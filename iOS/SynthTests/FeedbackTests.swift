//
//  FeedbackTests.swift
//  The feedback email goes to the right place with the details we ask for.
//

import Foundation
import Testing
@testable import Synth

@MainActor
struct FeedbackTests {

    @Test func mailtoGoesToTuesdayGamesWithSubjectAndDetails() throws {
        let url = try #require(Feedback.mailtoURL(fullVersion: true))
        let parts = try #require(URLComponents(url: url, resolvingAgainstBaseURL: false))
        #expect(parts.scheme == "mailto")
        #expect(parts.path == "info@tuesdaygames.com")
        let items = Dictionary(uniqueKeysWithValues: (parts.queryItems ?? []).map { ($0.name, $0.value ?? "") })
        #expect(items["subject"] == "808Synth feedback")
        let body = try #require(items["body"])
        #expect(body.contains("808Synth \(Feedback.appVersion)"))
        #expect(body.contains(Feedback.deviceModel))
        #expect(body.contains("Full version: yes"))
    }

    @Test func bodyLeavesRoomToWriteFirst() {
        let body = Feedback.body(fullVersion: false)
        #expect(body.hasPrefix("\n\n"))
        #expect(body.contains("Full version: no"))
    }

    @Test func deviceModelIsAHardwareName() {
        // e.g. "iPhone17,3" on a phone, or the simulated model in the simulator.
        #expect(Feedback.deviceModel.hasPrefix("iPhone") || Feedback.deviceModel.hasPrefix("iPad"))
    }
}
