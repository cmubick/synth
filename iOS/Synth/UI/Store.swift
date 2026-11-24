//
//  Store.swift
//  808Synth
//
//  In-app purchases: a one-time unlock of the full version, and optional tips.
//  Apple handles the payment; the app only learns whether the unlock is owned.
//  Same design as the other Tuesday Games apps (Sevens, Skate, Mahjong).
//
//  What the full version unlocks is listed in `FullVersion.features`.
//

import Foundation
import Observation
import StoreKit
import UIKit

@MainActor
@Observable
final class Store {
    static let shared = Store()

    /// Product identifiers. These must match the products set up in App Store Connect.
    static let fullGameID = "synth.fullgame"
    static let tipIDs = ["synth.tip.small", "synth.tip.medium", "synth.tip.large"]

    /// When off, the full version is never loaded or sold and locked features say
    /// "coming soon" (as Skate does). On: song mode is ready to sell.
    static let fullGameOnSale = true

    enum Outcome: Equatable {
        case success
        /// Waiting on something outside the app, such as Ask to Buy approval.
        case pending
        case cancelled
        case failed
    }

    private static let unlockedKey = "fullGameUnlocked"

    @ObservationIgnored private let defaults: UserDefaults
    @ObservationIgnored private var updates: Task<Void, Never>?

    /// The one-time unlock, once the App Store has answered. Nil if the store is unavailable.
    private(set) var fullGame: Product?
    /// Tip products, cheapest first.
    private(set) var tips: [Product] = []
    /// True once the product list has been requested, whether or not anything came back.
    private(set) var hasLoaded = false
    /// Whether the App Store says the full version is owned. Cached so the app is
    /// right before the store answers.
    private var owned: Bool

    var isUnlocked: Bool {
        #if DEBUG
        if let debugOverride { return debugOverride }
        #endif
        return owned
    }

    #if DEBUG
    /// For screenshots and manual testing: pretend the full version is or is not owned.
    /// Launch with `-unlocked YES` or `-unlocked NO` to set it.
    var debugOverride: Bool?
    #endif

    /// Detail of the most recent failure, for the diagnostic line in sandbox builds.
    private(set) var lastProblem: String?
    @ObservationIgnored private var lastOwnershipCheck = "not checked"

    /// True for TestFlight and development builds, which use Apple's sandbox store.
    static var isSandbox: Bool {
        #if DEBUG
        return true
        #else
        return Bundle.main.appStoreReceiptURL?.lastPathComponent == "sandboxReceipt"
        #endif
    }

    /// One line describing what the store currently knows. Shown only in sandbox builds.
    var diagnostics: String {
        let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "?"
        var parts = [
            "build \(build)",
            "products \((fullGame == nil ? 0 : 1) + tips.count)/\(Store.productIDs.count)",
            "full \(owned ? "owned" : "not owned")",
            lastOwnershipCheck,
        ]
        if let lastProblem { parts.append(lastProblem) }
        return parts.joined(separator: " · ")
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        owned = defaults.bool(forKey: Store.unlockedKey)
        #if DEBUG
        if let index = CommandLine.arguments.firstIndex(of: "-unlocked"),
           index + 1 < CommandLine.arguments.count {
            debugOverride = ["YES", "1", "true"].contains(CommandLine.arguments[index + 1])
        }
        #endif
    }

    /// The products to ask the App Store for right now.
    static var productIDs: [String] { (fullGameOnSale ? [fullGameID] : []) + tipIDs }

    /// Starts watching for purchases made elsewhere, then loads products and ownership.
    func start() {
        guard updates == nil else { return }
        updates = Task { [weak self] in
            for await update in Transaction.updates {
                await self?.handle(update)
            }
        }
        Task { await refresh() }
    }

    func refresh() async {
        if let products = try? await Product.products(for: Store.productIDs) {
            fullGame = products.first { $0.id == Store.fullGameID }
            tips = products.filter { Store.tipIDs.contains($0.id) }.sorted { $0.price < $1.price }
        }
        hasLoaded = true
        await refreshOwnership()
    }

    /// Reads what the App Store says is owned. This is the source of truth for the unlock.
    private func refreshOwnership() async {
        var found = false
        var seen = 0
        for await entitlement in Transaction.currentEntitlements {
            seen += 1
            if owns(entitlement) { found = true }
        }
        // Belt and braces: also ask for the latest transaction for the unlock itself.
        if !found, let latest = await Transaction.latest(for: Store.fullGameID), owns(latest) {
            found = true
        }
        lastOwnershipCheck = "entitlements \(seen)"
        setUnlocked(found)
    }

    /// Whether this transaction is a live purchase of the full version.
    private func owns(_ result: VerificationResult<Transaction>) -> Bool {
        switch result {
        case .verified(let transaction):
            return transaction.productID == Store.fullGameID && transaction.revocationDate == nil
        case .unverified(let transaction, let error):
            if transaction.productID == Store.fullGameID {
                lastProblem = "unverified: \(error)"
            }
            return false
        }
    }

    /// Buys a product. Pass the window's scene so the App Store knows where to
    /// show its payment sheet; without it the sheet can fail to appear.
    func buy(_ product: Product, in scene: UIScene? = nil) async -> Outcome {
        lastProblem = nil
        do {
            let result: Product.PurchaseResult
            if let scene {
                result = try await product.purchase(confirmIn: scene)
            } else {
                result = try await product.purchase()
            }
            switch result {
            case .success(let verification):
                switch verification {
                case .verified(let transaction):
                    if transaction.productID == Store.fullGameID {
                        setUnlocked(true)
                    }
                    await transaction.finish()
                    return .success
                case .unverified(_, let error):
                    lastProblem = "unverified: \(error)"
                    return .failed
                }
            case .pending:
                lastProblem = "pending"
                return .pending
            case .userCancelled:
                lastProblem = "cancelled"
                return .cancelled
            @unknown default:
                lastProblem = "unknown purchase result"
                return .failed
            }
        } catch {
            lastProblem = "error: \(error)"
            return .failed
        }
    }

    /// Asks the App Store to re-send past purchases, for a new or restored device.
    func restore() async -> Bool {
        try? await AppStore.sync()
        await refreshOwnership()
        return isUnlocked
    }

    /// Shows Apple's sheet for typing in an offer code. A redeemed code arrives as
    /// an ordinary purchase, so the unlock follows from the usual ownership check.
    func redeemCode(in scene: UIWindowScene) async -> Bool {
        lastProblem = nil
        do {
            try await AppStore.presentOfferCodeRedeemSheet(in: scene)
        } catch {
            lastProblem = "redeem error: \(error)"
            return false
        }
        await refreshOwnership()
        return isUnlocked
    }

    private func handle(_ update: VerificationResult<Transaction>) async {
        guard case .verified(let transaction) = update else { return }
        if transaction.productID == Store.fullGameID {
            setUnlocked(transaction.revocationDate == nil)
        }
        await transaction.finish()
    }

    private func setUnlocked(_ value: Bool) {
        owned = value
        defaults.set(value, forKey: Store.unlockedKey)
    }

    /// The window scene to show Apple's payment and redeem sheets in.
    static var activeScene: UIWindowScene? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
    }
}

/// What the full version adds. Shown on the unlock screen and used to lock features.
///
/// Free: the whole synth, the whole drum kit, patterns A and B, three premade beats.
/// Features that aren't built yet are listed as coming (`available: false`); give
/// each its free limit (in the detail) and a lock where it lives when it arrives.
enum FullVersion {
    struct Feature {
        let title: String
        let detail: String
        let available: Bool
    }

    static let features: [Feature] = [
        Feature(title: "Song mode", detail: "Chain patterns into whole songs, in any order.", available: true),
        Feature(title: "Patterns C to H", detail: "Six more patterns for verses, choruses and fills. A and B are free.", available: true),
        Feature(title: "Every premade beat", detail: "All the premade beats and their songs, not just the free three.", available: true),
        Feature(title: "Export and share", detail: "Save your songs as audio and send them anywhere.", available: false),
        Feature(title: "Unlimited saves", detail: "Keep as many projects and sounds as you like. Three are free.", available: false),
        Feature(title: "The full sampler", detail: "Record your own sounds into every pad and key. One is free.", available: false),
        Feature(title: "MIDI", detail: "Play from a MIDI keyboard and sync the tempo with other gear.", available: false),
    ]

    /// Patterns A and B are free.
    static let freePatternCount = 2

    @MainActor static var isOwned: Bool { Store.shared.isUnlocked }

    @MainActor static func isLocked(pattern: Int) -> Bool {
        pattern >= freePatternCount && !isOwned
    }

    @MainActor static func isLocked(_ beat: Beat) -> Bool {
        !beat.isFree && !isOwned
    }
}
