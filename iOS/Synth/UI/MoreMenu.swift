//
//  MoreMenu.swift
//  808Synth
//
//  The More sheet: unlock the full version, the tip jar, a few settings and
//  the about line. Opened from the top bar, or from a locked feature (which
//  jumps straight to the unlock).
//

import StoreKit
import SwiftUI

enum MoreFocus: Hashable {
    case fullVersion
    case tips
}

/// Settings keys shared by the More sheet and the screens that use them.
enum Settings {
    static let keepAwakeKey = "keepAwakeWhilePlaying"
}

/// `@Environment(\.openMore) var openMore` then `openMore(.fullVersion)`.
struct OpenMoreAction {
    var action: (MoreFocus?) -> Void = { _ in }
    func callAsFunction(_ focus: MoreFocus? = nil) { action(focus) }
}

private struct OpenMoreKey: EnvironmentKey {
    static let defaultValue = OpenMoreAction()
}

extension EnvironmentValues {
    var openMore: OpenMoreAction {
        get { self[OpenMoreKey.self] }
        set { self[OpenMoreKey.self] = newValue }
    }
}

struct MoreButton: View {
    @Environment(\.openMore) private var openMore

    var body: some View {
        Button {
            openMore()
        } label: {
            Image(systemName: "line.3.horizontal")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Theme.cream)
                .frame(width: 34, height: 34)
                .background(RoundedRectangle(cornerRadius: 7).fill(Theme.button))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("More")
        .accessibilityHint("Tip jar, full version and settings")
    }
}

struct MoreSheet: View {
    var focus: MoreFocus?

    @Environment(\.dismiss) private var dismiss
    @AppStorage(Haptics.settingKey) private var haptics = true
    @AppStorage(Settings.keepAwakeKey) private var keepAwake = true
    @State private var unlockMessage = ""
    @State private var busy = false

    private var store: Store { Store.shared }

    var body: some View {
        NavigationStack {
            ScrollViewReader { proxy in
                Form {
                    fullVersionSection.id(MoreFocus.fullVersion)
                    tipSection.id(MoreFocus.tips)
                    settingsSection
                    aboutSection
                }
                .scrollContentBackground(.hidden)
                .background(Theme.panel)
                .onAppear {
                    if let focus { proxy.scrollTo(focus, anchor: .top) }
                }
            }
            .navigationTitle("More")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .tint(Theme.accent)
        .task {
            if !store.hasLoaded || store.tips.isEmpty { await store.refresh() }
        }
    }

    // MARK: Full version

    @ViewBuilder private var fullVersionSection: some View {
        Section {
            if store.isUnlocked {
                Text("You own the full version.\nThank you for supporting 808Synth!")
                    .foregroundStyle(Theme.cream)
            } else {
                ForEach(FullVersion.features, id: \.title) { feature in
                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 8) {
                            Label(feature.title, systemImage: feature.available ? "lock.open.fill" : "clock")
                                .font(.headline)
                                .foregroundStyle(feature.available ? Theme.cream : Theme.label)
                            if !feature.available {
                                Text("COMING SOON")
                                    .font(Theme.titleFont(9))
                                    .tracking(1)
                                    .foregroundStyle(Theme.accent)
                            }
                        }
                        Text(feature.detail)
                            .font(.subheadline)
                            .foregroundStyle(Theme.label)
                    }
                }
                if Store.fullGameOnSale {
                    Text("One purchase. Yours to keep. No ads, ever.")
                        .font(.footnote)
                        .foregroundStyle(Theme.label)
                    Button {
                        Task { await unlock() }
                    } label: {
                        Text(unlockTitle)
                            .font(.headline)
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(store.fullGame == nil || busy)
                    Button("Restore purchases") { Task { await restore() } }
                        .disabled(busy)
                    Button("Redeem code") { Task { await redeem() } }
                        .disabled(busy)
                } else {
                    Text("Coming soon.")
                        .foregroundStyle(Theme.label)
                }
            }
            if !unlockMessage.isEmpty {
                Text(unlockMessage).foregroundStyle(Theme.accent)
            }
        } header: {
            Text("808Synth: Full Version")
        }
        .listRowBackground(Theme.module)
    }

    private var unlockTitle: String {
        if let product = store.fullGame { return "Unlock for \(product.displayPrice)" }
        return store.hasLoaded ? "Unavailable right now" : "Loading…"
    }

    private func unlock() async {
        guard let product = store.fullGame else { return }
        busy = true
        defer { busy = false }
        switch await store.buy(product, in: Store.activeScene) {
        case .success: unlockMessage = "Unlocked. Enjoy the full version!"
        case .pending: unlockMessage = "Waiting for approval."
        case .cancelled: unlockMessage = "Not purchased. Tap again to try."
        case .failed: unlockMessage = "The purchase didn't go through."
        }
    }

    private func restore() async {
        busy = true
        defer { busy = false }
        unlockMessage = "Checking your purchases…"
        let found = await store.restore()
        unlockMessage = found ? "Full version restored." : "No earlier purchase found."
    }

    private func redeem() async {
        guard let scene = Store.activeScene else { return }
        busy = true
        defer { busy = false }
        if await store.redeemCode(in: scene) {
            unlockMessage = "Unlocked. Enjoy the full version!"
        }
    }

    // MARK: Tip jar

    private var tipSection: some View {
        Section {
            // Inset so the list's rounded row doesn't clip the card's pixel corners.
            TipJarCard()
                .listRowInsets(EdgeInsets(top: 10, leading: 10, bottom: 10, trailing: 10))
                .listRowBackground(Color.clear)
        }
    }

    // MARK: Settings & about

    private var settingsSection: some View {
        Section("Settings") {
            Toggle("Haptics", isOn: $haptics)
            Toggle("Keep the screen on while the beat plays", isOn: $keepAwake)
        }
        .foregroundStyle(Theme.cream)
        .listRowBackground(Theme.module)
    }

    private var aboutSection: some View {
        Section {
            LabeledContent("Version", value: Self.version)
            Text("A Tuesday Games app. No ads, ever.")
                .foregroundStyle(Theme.label)
            if Store.isSandbox {
                Text(store.diagnostics)
                    .font(.caption2.monospaced())
                    .foregroundStyle(Theme.label.opacity(0.7))
            }
        }
        .foregroundStyle(Theme.cream)
        .listRowBackground(Theme.module)
    }

    private static var version: String {
        let info = Bundle.main.infoDictionary
        let short = info?["CFBundleShortVersionString"] as? String ?? "?"
        let build = info?["CFBundleVersion"] as? String ?? "?"
        return "\(short) (\(build))"
    }
}
