//
//  make_icon.swift
//  Draws 808Synth's 8-bit app icon: a 32 x 32 pixel picture, scaled up to
//  1024 x 1024. Uses the app's own PixelArt.swift (Skate's font, the synth's
//  colors), so the icon and the in-app pixel art always match.
//
//  Run from the repo root:   AppStore/make_icon.sh
//

import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

@main
struct MakeIcon {
    static func main() throws {
        let icon = drawIcon()
        let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
        try write(icon, scale: 32, to: root.appendingPathComponent("iOS/Synth/Assets.xcassets/AppIcon.appiconset/AppIcon.png"))
        try write(icon, scale: 32, to: root.appendingPathComponent("AppStore/icon/icon-1024.png"))
        // A small sharp copy for the support page (whole-pixel scale, no blur).
        try write(icon, scale: 6, to: root.appendingPathComponent("docs/icon.png"))
        try writeAndroid(icon, res: root.appendingPathComponent("android/app/src/main/res"))
        print("wrote the icon to the asset catalog, AppStore/icon/, docs/ and the Android launcher")
    }

    /// The Android launcher icon, done the way Skate's is (android/tools/icon there).
    /// Android crops icons to a circle, squircle or square of the phone's choosing,
    /// from a 108 dp layer of which the middle 72 dp shows. So the 32 x 32 picture is
    /// drawn 64 dp wide in the middle, in whole screen pixels so it stays sharp, and
    /// its edge pixels are stretched out to the sides: whatever shape the phone cuts,
    /// it cuts walnut and panel, not the 808.
    static func writeAndroid(_ grid: PixelGrid, res: URL) throws {
        for (folder, density) in [("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4)] as [(String, Double)] {
            let size = Int(108 * density)
            let square = Int(2 * density) // screen pixels per picture pixel: the picture is 64 dp
            let offset = (size - grid.width * square) / 2
            var big = PixelGrid(width: size, height: size)
            for y in 0..<size {
                for x in 0..<size {
                    let column = min(grid.width - 1, max(0, x - offset < 0 ? 0 : (x - offset) / square))
                    let row = min(grid.height - 1, max(0, y - offset < 0 ? 0 : (y - offset) / square))
                    big[x, y] = grid[column, row]
                }
            }
            try write(big, scale: 1, to: res.appendingPathComponent("mipmap-\(folder)/ic_launcher_background.png"))
        }
    }

    /// The picture, one character per pixel decision.
    static func drawIcon() -> PixelGrid {
        let P = SynthPalette.self
        var g = PixelGrid(width: 32, height: 32, fill: P.panel)

        // Walnut side cheeks, like the app's side panels, with a little grain.
        for x in [0, 1, 2, 29, 30, 31] {
            for y in 0..<32 {
                let grain = (x * 7 + y * 3) % 11 == 0 || (y + x * 5) % 13 == 0
                g[x, y] = grain ? P.woodDark : (x == 2 || x == 29 ? P.wood : P.woodLight)
            }
        }
        // The panel's top lip.
        g.fill(x: 3, y: 2, width: 26, height: 1, P.edge)

        // "808" in Skate's font, the app's orange, with the black drop shadow.
        let word = "808"
        let wordX = 3 + (26 - PixelFont.width(of: word)) / 2
        g.text(word, x: wordX, y: 5, color: P.accent)

        // Step lights over the pads: a beat on 1 and 3.
        let padX = [3, 10, 17, 24]
        for (i, x) in padX.enumerated() {
            g.fill(x: x + 1, y: 15, width: 3, height: 1, i % 2 == 0 ? P.stepRed : P.ledOff)
        }

        // Two rows of step buttons in the four beat colors, drawn like the app's:
        // a lit step is solid color with a shaded edge and a highlight pixel; an
        // unlit one is dark with its color as a strip along the bottom.
        let colors = [P.stepRed, P.stepOrange, P.stepYellow, P.stepCream]
        for (row, y) in [17, 23].enumerated() {
            for (i, x) in padX.enumerated() {
                let lit = (row + i) % 2 == 0
                if lit {
                    g.fill(x: x, y: y, width: 5, height: 5, colors[i])
                    g.fill(x: x, y: y + 4, width: 5, height: 1, shade(colors[i], 0.7))
                    g[x, y] = P.cream
                } else {
                    g.fill(x: x, y: y, width: 5, height: 4, P.button)
                    g.fill(x: x, y: y + 4, width: 5, height: 1, colors[i])
                }
            }
        }
        return g
    }

    static func shade(_ c: PixelColor, _ factor: Double) -> PixelColor {
        let r = UInt32((c.red * factor * 255).rounded())
        let gr = UInt32((c.green * factor * 255).rounded())
        let b = UInt32((c.blue * factor * 255).rounded())
        return PixelColor((r << 16) | (gr << 8) | b)
    }

    static func write(_ grid: PixelGrid, scale: Int, to url: URL) throws {
        let size = grid.width * scale
        let space = CGColorSpace(name: CGColorSpace.sRGB)!
        // App icons must be opaque (no alpha channel).
        guard let ctx = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0,
                                  space: space, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else {
            throw NSError(domain: "icon", code: 1)
        }
        for y in 0..<grid.height {
            for x in 0..<grid.width {
                let c = grid[x, y] ?? SynthPalette.panel
                ctx.setFillColor(red: c.red, green: c.green, blue: c.blue, alpha: 1)
                // CoreGraphics counts y from the bottom.
                ctx.fill(CGRect(x: x * scale, y: (grid.height - 1 - y) * scale, width: scale, height: scale))
            }
        }
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        guard let image = ctx.makeImage(),
              let dest = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil) else {
            throw NSError(domain: "icon", code: 2)
        }
        CGImageDestinationAddImage(dest, image, nil)
        guard CGImageDestinationFinalize(dest) else { throw NSError(domain: "icon", code: 3) }
    }
}
