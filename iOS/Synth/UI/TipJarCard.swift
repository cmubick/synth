//
//  TipJarCard.swift
//  808Synth
//
//  The tip jar in the More sheet, 8-bit like Skate's (its font, its boxes and
//  wording) in 808Synth's colors. The jar fills with coins as tips come in.
//

import StoreKit
import SwiftUI

struct TipJarCard: View {
    /// How many tips this device has given; only fills the jar picture.
    @AppStorage("tipsGiven") private var tipsGiven = 0
    @State private var message = ""
    @State private var busy = false

    private var store: Store { Store.shared }

    /// Skate's names and prices, shown before the App Store answers.
    static let tipLabels: [(name: String, price: String)] = [("SMALL", "$0.99"), ("GENEROUS", "$2.99"), ("BIG", "$4.99")]

    var body: some View {
        VStack(spacing: 14) {
            HStack(alignment: .center, spacing: 16) {
                PixelImage(grid: TipJarArt.jar(level: tipsGiven), pixel: 5)
                VStack(alignment: .leading, spacing: 8) {
                    PixelText(text: "TIP JAR", color: SynthPalette.accent, pixel: 3)
                    PixelText(text: "TIPS ARE OPTIONAL AND", color: SynthPalette.creamDark, pixel: 2)
                    PixelText(text: "DON'T UNLOCK ANYTHING.", color: SynthPalette.creamDark, pixel: 2)
                }
                Spacer(minLength: 0)
            }

            HStack(alignment: .top, spacing: 12) {
                ForEach(Self.tipLabels.indices, id: \.self) { index in
                    let tip = Self.tipLabels[index]
                    let product = index < store.tips.count ? store.tips[index] : nil
                    VStack(spacing: 6) {
                        Button {
                            Task { await give(product) }
                        } label: {
                            PixelText(text: product?.displayPrice ?? tip.price, color: PixelColor(0xFCFCFC), pixel: 3)
                                .frame(minWidth: 84)
                        }
                        .buttonStyle(PixelButtonStyle())
                        .disabled(busy)
                        .accessibilityLabel("\(tip.name.capitalized) tip, \(product?.displayPrice ?? tip.price)")
                        PixelText(text: tip.name, color: SynthPalette.grey, pixel: 2)
                    }
                }
            }

            if !message.isEmpty {
                PixelText(text: message, color: SynthPalette.stepYellow, pixel: 3)
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity)
        .background(PixelBox(fill: SynthPalette.module, shine: SynthPalette.edge, pixel: 3))
    }

    private func give(_ product: Product?) async {
        guard let product else {
            message = store.hasLoaded ? "TIPS AREN'T SET UP YET" : "ONE MOMENT..."
            if !store.hasLoaded { await store.refresh() }
            return
        }
        busy = true
        defer { busy = false }
        switch await store.buy(product, in: Store.activeScene) {
        case .success:
            tipsGiven += 1
            message = "THANK YOU!"
            Haptics.thump()
        case .pending: message = "WAITING FOR APPROVAL"
        case .cancelled: message = ""
        case .failed: message = "THAT DIDN'T WORK. TRY AGAIN?"
        }
    }
}
