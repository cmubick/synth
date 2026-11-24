//
//  PixelViews.swift
//  808Synth
//
//  SwiftUI pieces for 8-bit art drawn with PixelArt.swift: crisp pixel
//  pictures, Skate's font, and Skate's box shape for panels and buttons.
//

import SwiftUI

extension Color {
    init(_ pixel: PixelColor) {
        self.init(red: pixel.red, green: pixel.green, blue: pixel.blue)
    }
}

/// A pixel grid drawn as hard-edged squares, `pixel` points each.
struct PixelImage: View {
    let grid: PixelGrid
    var pixel: CGFloat = 3

    var body: some View {
        Canvas { context, _ in
            for y in 0..<grid.height {
                for x in 0..<grid.width {
                    guard let color = grid[x, y] else { continue }
                    let rect = CGRect(x: CGFloat(x) * pixel, y: CGFloat(y) * pixel, width: pixel, height: pixel)
                    context.fill(Path(rect), with: .color(Color(color)))
                }
            }
        }
        .frame(width: CGFloat(grid.width) * pixel, height: CGFloat(grid.height) * pixel)
        .accessibilityHidden(true)
    }
}

/// Text in Skate's 5x7 font with its drop shadow.
struct PixelText: View {
    let text: String
    var color: PixelColor = SynthPalette.cream
    var pixel: CGFloat = 2

    var body: some View {
        var grid = PixelGrid(width: PixelFont.width(of: text), height: PixelFont.glyphHeight + 1)
        grid.text(text, x: 0, y: 0, color: color)
        return PixelImage(grid: grid, pixel: pixel)
            .accessibilityElement()
            .accessibilityLabel(text.capitalized)
    }
}

/// Skate's box: square corners with the corner pixel cut off.
struct PixelBoxShape: Shape {
    var pixel: CGFloat = 3

    func path(in r: CGRect) -> Path {
        let p = pixel
        var path = Path()
        path.addLines([
            CGPoint(x: r.minX + p, y: r.minY), CGPoint(x: r.maxX - p, y: r.minY),
            CGPoint(x: r.maxX - p, y: r.minY + p), CGPoint(x: r.maxX, y: r.minY + p),
            CGPoint(x: r.maxX, y: r.maxY - p), CGPoint(x: r.maxX - p, y: r.maxY - p),
            CGPoint(x: r.maxX - p, y: r.maxY), CGPoint(x: r.minX + p, y: r.maxY),
            CGPoint(x: r.minX + p, y: r.maxY - p), CGPoint(x: r.minX, y: r.maxY - p),
            CGPoint(x: r.minX, y: r.minY + p), CGPoint(x: r.minX + p, y: r.minY + p),
        ])
        path.closeSubpath()
        return path
    }
}

/// A filled pixel box: black outline one pixel thick, the fill, and an optional
/// light line along the bottom, like Skate's buttons.
struct PixelBox: View {
    var fill: PixelColor
    var shine: PixelColor? = nil
    var pixel: CGFloat = 3

    var body: some View {
        ZStack(alignment: .bottom) {
            PixelBoxShape(pixel: pixel).fill(Color(SynthPalette.black))
            Rectangle().fill(Color(fill)).padding(pixel)
            if let shine {
                Rectangle().fill(Color(shine))
                    .frame(height: pixel)
                    .padding(.horizontal, pixel)
                    .padding(.bottom, pixel)
            }
        }
    }
}

/// A chunky 8-bit button: text in the pixel font on a pixel box, which
/// drops a pixel when pressed.
struct PixelButtonStyle: ButtonStyle {
    var fill: PixelColor = SynthPalette.accent
    var shine: PixelColor? = SynthPalette.stepYellow
    var pixel: CGFloat = 3

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .padding(.horizontal, pixel * 4)
            .padding(.vertical, pixel * 3)
            .background(PixelBox(fill: configuration.isPressed ? SynthPalette.accentDark : fill,
                                 shine: configuration.isPressed ? nil : shine, pixel: pixel))
            .offset(y: configuration.isPressed ? pixel : 0)
            .animation(nil, value: configuration.isPressed)
    }
}
