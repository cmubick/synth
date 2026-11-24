//
//  PixelArt.swift
//  808Synth
//
//  8-bit drawing, the way Skate and the Tuesday Games site do it: a grid of
//  pixels, sprites written as text, and Skate's 5x7 font with its drop shadow.
//  Plain Swift on purpose (no UIKit/SwiftUI), so the app icon script in
//  AppStore/make_icon.swift draws with exactly the same code.
//

import Foundation

/// A 24-bit color, written like the hex in a paint program: PixelColor(0xFF8A3D).
struct PixelColor: Hashable {
    let rgb: UInt32

    init(_ rgb: UInt32) { self.rgb = rgb }

    var red: Double { Double((rgb >> 16) & 0xFF) / 255 }
    var green: Double { Double((rgb >> 8) & 0xFF) / 255 }
    var blue: Double { Double(rgb & 0xFF) / 255 }
}

/// 808Synth's colors, matching Theme in the app: dark panel, cream markings,
/// orange accent, walnut sides, and the four step colors.
enum SynthPalette {
    static let black = PixelColor(0x101010)
    static let panel = PixelColor(0x1C1B19)
    static let module = PixelColor(0x2A2725)
    static let button = PixelColor(0x3B3836)
    static let edge = PixelColor(0x45413D)
    static let accent = PixelColor(0xFF8A3D)
    static let accentDark = PixelColor(0xB85A1F)
    static let cream = PixelColor(0xF0E6D4)
    static let creamDark = PixelColor(0xB8AE9C)
    static let grey = PixelColor(0x8C857C)
    static let woodLight = PixelColor(0x784726)
    static let wood = PixelColor(0x5C3319)
    static let woodDark = PixelColor(0x40220F)
    static let stepRed = PixelColor(0xED4533)
    static let stepOrange = PixelColor(0xFA8C33)
    static let stepYellow = PixelColor(0xFAD14D)
    static let stepCream = PixelColor(0xF0E6D4)
    static let ledOff = PixelColor(0x4A2A22)
}

/// A rectangle of pixels; nil means see-through.
struct PixelGrid {
    let width: Int
    let height: Int
    private(set) var pixels: [PixelColor?]

    init(width: Int, height: Int, fill: PixelColor? = nil) {
        self.width = width
        self.height = height
        pixels = Array(repeating: fill, count: width * height)
    }

    subscript(x: Int, y: Int) -> PixelColor? {
        get { (0..<width).contains(x) && (0..<height).contains(y) ? pixels[y * width + x] : nil }
        set {
            guard (0..<width).contains(x), (0..<height).contains(y), let newValue else { return }
            pixels[y * width + x] = newValue
        }
    }

    mutating func fill(x: Int, y: Int, width w: Int, height h: Int, _ color: PixelColor) {
        for j in 0..<max(0, h) {
            for i in 0..<max(0, w) { self[x + i, y + j] = color }
        }
    }

    /// Draws a sprite written as rows of characters; characters not in `colors` stay clear.
    mutating func draw(_ rows: [String], colors: [Character: PixelColor], x: Int, y: Int) {
        for (j, row) in rows.enumerated() {
            for (i, ch) in row.enumerated() {
                if let color = colors[ch] { self[x + i, y + j] = color }
            }
        }
    }

    /// Text in Skate's font, with its drop shadow one pixel straight below.
    mutating func text(_ string: String, x: Int, y: Int, color: PixelColor, shadow: PixelColor? = SynthPalette.black) {
        let passes: [(Int, PixelColor)] = shadow.map { [(1, $0), (0, color)] } ?? [(0, color)]
        for (dy, ink) in passes {
            var cursor = x
            for character in string.uppercased() {
                let rows = PixelFont.glyph(character)
                draw(rows, colors: ["#": ink], x: cursor, y: y + dy)
                cursor += rows[0].count + PixelFont.gap
            }
        }
    }

    /// Skate's button and panel shape: a black outline with the corner pixels
    /// cut off, a fill, and a light line along the bottom edge.
    mutating func box(x: Int, y: Int, width w: Int, height h: Int, fill color: PixelColor, shine: PixelColor? = nil) {
        fill(x: x + 1, y: y, width: w - 2, height: h, SynthPalette.black)
        fill(x: x, y: y + 1, width: w, height: h - 2, SynthPalette.black)
        fill(x: x + 1, y: y + 1, width: w - 2, height: h - 2, color)
        if let shine { fill(x: x + 1, y: y + h - 2, width: w - 2, height: 1, shine) }
    }
}

/// Skate's 7-pixel-high font: capitals, digits and a little punctuation.
enum PixelFont {
    static let glyphHeight = 7
    static let gap = 1

    static func glyph(_ character: Character) -> [String] {
        glyphs[character] ?? glyphs["?"]!
    }

    static func width(of text: String) -> Int {
        max(1, text.uppercased().reduce(0) { $0 + glyph($1)[0].count + gap } - gap)
    }

    private static let glyphs: [Character: [String]] = [
        "A": [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
        "B": ["####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."],
        "C": [".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."],
        "D": ["####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."],
        "E": ["#####", "#....", "#....", "####.", "#....", "#....", "#####"],
        "F": ["#####", "#....", "#....", "####.", "#....", "#....", "#...."],
        "G": [".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".####"],
        "H": ["#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
        "I": ["###", ".#.", ".#.", ".#.", ".#.", ".#.", "###"],
        "J": ["..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##.."],
        "K": ["#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"],
        "L": ["#....", "#....", "#....", "#....", "#....", "#....", "#####"],
        "M": ["#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"],
        "N": ["#...#", "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#"],
        "O": [".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
        "P": ["####.", "#...#", "#...#", "####.", "#....", "#....", "#...."],
        "Q": [".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"],
        "R": ["####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"],
        "S": [".####", "#....", "#....", ".###.", "....#", "....#", "####."],
        "T": ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."],
        "U": ["#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
        "V": ["#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."],
        "W": ["#...#", "#...#", "#...#", "#.#.#", "#.#.#", "#.#.#", ".#.#."],
        "X": ["#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"],
        "Y": ["#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."],
        "Z": ["#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"],
        "0": [".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."],
        "1": ["..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."],
        "2": [".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"],
        "3": ["#####", "...#.", "..#..", "...#.", "....#", "#...#", ".###."],
        "4": ["...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."],
        "5": ["#####", "#....", "####.", "....#", "....#", "#...#", ".###."],
        "6": ["..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."],
        "7": ["#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."],
        "8": [".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."],
        "9": [".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."],
        "+": [".....", "..#..", "..#..", "#####", "..#..", "..#..", "....."],
        "-": ["...", "...", "...", "###", "...", "...", "..."],
        "!": ["#", "#", "#", "#", "#", ".", "#"],
        "'": ["#", "#", ".", ".", ".", ".", "."],
        ",": ["..", "..", "..", "..", "..", ".#", "#."],
        "$": ["..#..", ".####", "#.#..", ".###.", "..#.#", "####.", "..#.."],
        ":": [".", ".", "#", ".", ".", "#", "."],
        ".": [".", ".", ".", ".", ".", ".", "#"],
        "/": ["....#", "....#", "...#.", "..#..", ".#...", "#....", "#...."],
        "?": [".###.", "#...#", "....#", "...#.", "..#..", ".....", "..#.."],
        " ": ["...", "...", "...", "...", "...", "...", "..."],
    ]
}

/// The tip jar: a glass jar with a walnut lid, filling up with coins in the
/// four step colors. `level` 0...3 is how full it is (more after each tip).
enum TipJarArt {
    static func jar(level: Int) -> PixelGrid {
        let rows = [
            "..llllllllll..",
            "..LLLLLLLLLL..",
            "...g......g...",
            "..g........g..",
            ".g..........g.",
            ".g..........g.",
            ".g..........g.",
            ".g..........g.",
            ".g..........g.",
            ".g..........g.",
            ".g..........g.",
            ".g..........g.",
            "..gggggggggg..",
        ]
        var grid = PixelGrid(width: 14, height: 13)
        grid.draw(rows, colors: [
            "l": SynthPalette.woodLight, "L": SynthPalette.woodDark, "g": SynthPalette.creamDark,
        ], x: 0, y: 0)
        // Coins stack up from the bottom, a row per level; the jar's glass glints.
        let coins = [SynthPalette.stepYellow, SynthPalette.stepOrange, SynthPalette.stepYellow,
                     SynthPalette.stepRed, SynthPalette.stepYellow]
        let fullRows = 3 + min(max(level, 0), 3) * 2
        for r in 0..<fullRows {
            let y = 11 - r
            for x in 2...11 {
                grid[x, y] = coins[(x + r) % coins.count]
            }
        }
        for y in 3...5 { grid[3, y] = SynthPalette.cream }
        return grid
    }
}
