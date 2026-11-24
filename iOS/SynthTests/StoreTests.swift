//
//  StoreTests.swift
//  Runs the store against the local test configuration in Synth.storekit,
//  the same checks Sevens runs on its store.
//

import Foundation
import StoreKit
import StoreKitTest
import Testing
@testable import Synth

@MainActor
@Suite(.serialized)
struct StoreTests {

    private func makeSession() throws -> SKTestSession {
        let session = try SKTestSession(configurationFileNamed: "Synth")
        session.disableDialogs = true
        session.clearTransactions()
        return session
    }

    private func makeStore() -> Store {
        Store(defaults: UserDefaults(suiteName: "store-tests-\(UUID().uuidString)")!)
    }

    @Test func loadsTheUnlockAndThreeTips() async throws {
        _ = try makeSession()
        let store = makeStore()
        await store.refresh()
        #expect(store.hasLoaded)
        #expect(store.fullGame?.id == Store.fullGameID)
        #expect(store.tips.map(\.id) == Store.tipIDs)
        #expect(!store.isUnlocked)
    }

    @Test func buyingTheFullVersionUnlocksItAndIsRemembered() async throws {
        _ = try makeSession()
        let defaults = UserDefaults(suiteName: "store-tests-\(UUID().uuidString)")!
        let store = Store(defaults: defaults)
        await store.refresh()
        let product = try #require(store.fullGame)
        #expect(await store.buy(product) == .success)
        #expect(store.isUnlocked)

        // A fresh launch reads the cached answer, then the store confirms it.
        let relaunched = Store(defaults: defaults)
        #expect(relaunched.isUnlocked)
        await relaunched.refresh()
        #expect(relaunched.isUnlocked)
    }

    @Test func tippingDoesNotUnlockAnything() async throws {
        _ = try makeSession()
        let store = makeStore()
        await store.refresh()
        let tip = try #require(store.tips.first)
        #expect(await store.buy(tip) == .success)
        #expect(!store.isUnlocked)
        // Tips can be given more than once.
        #expect(await store.buy(tip) == .success)
    }

    @Test func restoreFindsAnEarlierPurchase() async throws {
        _ = try makeSession()
        // Bought on one "device"...
        let first = makeStore()
        await first.refresh()
        #expect(await first.buy(try #require(first.fullGame)) == .success)
        // ...then restored on another that has no cached answer.
        let store = makeStore()
        #expect(!store.isUnlocked)
        #expect(await store.restore())
        #expect(store.isUnlocked)
    }

    @Test func aRefundLocksItAgain() async throws {
        let session = try makeSession()
        let store = makeStore()
        await store.refresh()
        #expect(await store.buy(try #require(store.fullGame)) == .success)
        let transaction = try #require(session.allTransactions().first { $0.productIdentifier == Store.fullGameID })
        try session.refundTransaction(identifier: transaction.identifier)
        // The test store applies the refund asynchronously; give it up to 2 seconds.
        for _ in 0..<20 where store.isUnlocked {
            try await Task.sleep(for: .milliseconds(100))
            await store.refresh()
        }
        #expect(!store.isUnlocked)
    }
}
