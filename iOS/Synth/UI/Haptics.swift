//
//  Haptics.swift
//  808Synth
//
//  Light taps under your finger, like Sevens'. Can be turned off in More.
//

import UIKit

enum Haptics {
    /// Settings key; on unless the player turns it off.
    static let settingKey = "haptics"

    private static let selection = UISelectionFeedbackGenerator()
    private static let light = UIImpactFeedbackGenerator(style: .light)
    private static let medium = UIImpactFeedbackGenerator(style: .medium)

    static var isOn: Bool {
        UserDefaults.standard.object(forKey: settingKey) as? Bool ?? true
    }

    /// The faintest tick: a step or a pattern button.
    static func tick() {
        guard isOn else { return }
        selection.selectionChanged()
    }

    /// A light tap: hitting a pad.
    static func tap() {
        guard isOn else { return }
        light.impactOccurred()
    }

    /// A firmer tap: play / stop.
    static func thump() {
        guard isOn else { return }
        medium.impactOccurred()
    }
}
