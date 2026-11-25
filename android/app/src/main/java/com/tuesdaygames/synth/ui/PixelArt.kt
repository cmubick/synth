//
//  PixelArt.kt
//
//  8-bit drawing, the way Skate and the Tuesday Games site do it: a grid of
//  pixels, sprites written as text, and Skate's 5x7 font with its drop shadow.
//  Ported from iOS's PixelArt.swift (font and colors generated from it, so they
//  match exactly). Plain Kotlin, no Android, so tests can check it.
//

package com.tuesdaygames.synth.ui

/** A 24-bit color, written like the hex in a paint program: PixelColor(0xFF8A3D). */
@JvmInline
value class PixelColor(val rgb: Int) {
    val red: Float get() = ((rgb shr 16) and 0xFF) / 255f
    val green: Float get() = ((rgb shr 8) and 0xFF) / 255f
    val blue: Float get() = (rgb and 0xFF) / 255f
}

/** 808Synth's colors, matching Theme in the app and SynthPalette on iOS. */
object SynthPalette {
    val black = PixelColor(0x101010)
    val panel = PixelColor(0x1C1B19)
    val module = PixelColor(0x2A2725)
    val button = PixelColor(0x3B3836)
    val edge = PixelColor(0x45413D)
    val accent = PixelColor(0xFF8A3D)
    val accentDark = PixelColor(0xB85A1F)
    val cream = PixelColor(0xF0E6D4)
    val creamDark = PixelColor(0xB8AE9C)
    val grey = PixelColor(0x8C857C)
    val woodLight = PixelColor(0x784726)
    val wood = PixelColor(0x5C3319)
    val woodDark = PixelColor(0x40220F)
    val stepRed = PixelColor(0xED4533)
    val stepOrange = PixelColor(0xFA8C33)
    val stepYellow = PixelColor(0xFAD14D)
    val stepCream = PixelColor(0xF0E6D4)
    val ledOff = PixelColor(0x4A2A22)
}

/** A rectangle of pixels; null means see-through. */
class PixelGrid(val width: Int, val height: Int, fill: PixelColor? = null) {
    private val pixels = arrayOfNulls<PixelColor>(width * height).also { if (fill != null) it.fill(fill) }

    operator fun get(x: Int, y: Int): PixelColor? =
        if (x in 0 until width && y in 0 until height) pixels[y * width + x] else null

    operator fun set(x: Int, y: Int, color: PixelColor?) {
        if (x in 0 until width && y in 0 until height && color != null) pixels[y * width + x] = color
    }

    fun fill(x: Int, y: Int, w: Int, h: Int, color: PixelColor) {
        for (j in 0 until h) for (i in 0 until w) this[x + i, y + j] = color
    }

    /** Draws a sprite written as rows of characters; characters not in `colors` stay clear. */
    fun draw(rows: List<String>, colors: Map<Char, PixelColor>, x: Int, y: Int) {
        rows.forEachIndexed { j, row -> row.forEachIndexed { i, ch -> colors[ch]?.let { this[x + i, y + j] = it } } }
    }

    /** Text in Skate's font, with its drop shadow one pixel straight below. */
    fun text(string: String, x: Int, y: Int, color: PixelColor, shadow: PixelColor? = SynthPalette.black) {
        val passes = if (shadow != null) listOf(1 to shadow, 0 to color) else listOf(0 to color)
        for ((dy, ink) in passes) {
            var cursor = x
            for (ch in string.uppercase()) {
                val rows = PixelFont.glyph(ch)
                draw(rows, mapOf('#' to ink), cursor, y + dy)
                cursor += rows[0].length + PixelFont.GAP
            }
        }
    }
}

/** Skate's 7-pixel-high font: capitals, digits and a little punctuation. */
object PixelFont {
    const val GLYPH_HEIGHT = 7
    const val GAP = 1

    fun glyph(ch: Char): List<String> = glyphs[ch] ?: glyphs.getValue('?')

    fun width(text: String): Int = maxOf(1, text.uppercase().sumOf { glyph(it)[0].length + GAP } - GAP)

    private val glyphs: Map<Char, List<String>> = mapOf(
        'A' to listOf(".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
        'B' to listOf("####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."),
        'C' to listOf(".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."),
        'D' to listOf("####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."),
        'E' to listOf("#####", "#....", "#....", "####.", "#....", "#....", "#####"),
        'F' to listOf("#####", "#....", "#....", "####.", "#....", "#....", "#...."),
        'G' to listOf(".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".####"),
        'H' to listOf("#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
        'I' to listOf("###", ".#.", ".#.", ".#.", ".#.", ".#.", "###"),
        'J' to listOf("..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##.."),
        'K' to listOf("#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"),
        'L' to listOf("#....", "#....", "#....", "#....", "#....", "#....", "#####"),
        'M' to listOf("#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"),
        'N' to listOf("#...#", "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#"),
        'O' to listOf(".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
        'P' to listOf("####.", "#...#", "#...#", "####.", "#....", "#....", "#...."),
        'Q' to listOf(".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"),
        'R' to listOf("####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"),
        'S' to listOf(".####", "#....", "#....", ".###.", "....#", "....#", "####."),
        'T' to listOf("#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."),
        'U' to listOf("#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
        'V' to listOf("#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."),
        'W' to listOf("#...#", "#...#", "#...#", "#.#.#", "#.#.#", "#.#.#", ".#.#."),
        'X' to listOf("#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"),
        'Y' to listOf("#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."),
        'Z' to listOf("#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"),
        '0' to listOf(".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."),
        '1' to listOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
        '2' to listOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
        '3' to listOf("#####", "...#.", "..#..", "...#.", "....#", "#...#", ".###."),
        '4' to listOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
        '5' to listOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
        '6' to listOf("..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."),
        '7' to listOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
        '8' to listOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
        '9' to listOf(".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."),
        '+' to listOf(".....", "..#..", "..#..", "#####", "..#..", "..#..", "....."),
        '-' to listOf("...", "...", "...", "###", "...", "...", "..."),
        '!' to listOf("#", "#", "#", "#", "#", ".", "#"),
        '\'' to listOf("#", "#", ".", ".", ".", ".", "."),
        ',' to listOf("..", "..", "..", "..", "..", ".#", "#."),
        '$' to listOf("..#..", ".####", "#.#..", ".###.", "..#.#", "####.", "..#.."),
        ':' to listOf(".", ".", "#", ".", ".", "#", "."),
        '.' to listOf(".", ".", ".", ".", ".", ".", "#"),
        '/' to listOf("....#", "....#", "...#.", "..#..", ".#...", "#....", "#...."),
        '?' to listOf(".###.", "#...#", "....#", "...#.", "..#..", ".....", "..#.."),
        ' ' to listOf("...", "...", "...", "...", "...", "...", "..."),
    )
}

/** The tip jar: a glass jar with a walnut lid, filling with coins in the step colors. */
object TipJarArt {
    fun jar(level: Int): PixelGrid {
        val rows = listOf(
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
        )
        val grid = PixelGrid(14, 13)
        grid.draw(rows, mapOf('l' to SynthPalette.woodLight, 'L' to SynthPalette.woodDark, 'g' to SynthPalette.creamDark), 0, 0)
        // Coins stack up from the bottom, a row per level; the jar's glass glints.
        val coins = listOf(SynthPalette.stepYellow, SynthPalette.stepOrange, SynthPalette.stepYellow, SynthPalette.stepRed, SynthPalette.stepYellow)
        val fullRows = 3 + level.coerceIn(0, 3) * 2
        for (r in 0 until fullRows) {
            val y = 11 - r
            for (x in 2..11) grid[x, y] = coins[(x + r) % coins.size]
        }
        for (y in 3..5) grid[3, y] = SynthPalette.cream
        return grid
    }
}
