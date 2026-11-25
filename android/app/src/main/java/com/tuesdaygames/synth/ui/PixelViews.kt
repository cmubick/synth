//
//  PixelViews.kt
//
//  Compose pieces for 8-bit art drawn with PixelArt.kt: crisp pixel pictures,
//  Skate's font, and Skate's box shape for panels and buttons. Ported from
//  iOS's PixelViews.swift.
//

package com.tuesdaygames.synth.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun PixelColor.toColor() = Color(red, green, blue)

/** A pixel grid drawn as hard-edged squares, `pixel` dp each. */
@Composable
fun PixelImage(grid: PixelGrid, pixel: Dp = 3.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(pixel * grid.width, pixel * grid.height)) {
        val p = pixel.toPx()
        for (y in 0 until grid.height) for (x in 0 until grid.width) {
            val c = grid[x, y] ?: continue
            // A hair of overlap so no seams show between squares.
            drawRect(c.toColor(), Offset(x * p, y * p), Size(p + 0.5f, p + 0.5f))
        }
    }
}

/** Text in Skate's 5x7 font with its drop shadow. */
@Composable
fun PixelText(text: String, color: PixelColor = SynthPalette.cream, pixel: Dp = 2.dp, modifier: Modifier = Modifier) {
    val grid = PixelGrid(PixelFont.width(text), PixelFont.GLYPH_HEIGHT + 1)
    grid.text(text, 0, 0, color)
    PixelImage(grid, pixel, modifier.semantics { contentDescription = text.lowercase().replaceFirstChar { it.uppercase() } })
}

/** Skate's box: square corners with the corner pixel cut off, a black outline one
 *  pixel thick, the fill, and an optional light line along the bottom. */
fun DrawScope.pixelBox(fill: Color, shine: Color?, pixel: Float) {
    val w = size.width
    val h = size.height
    val p = pixel
    val outline = Path().apply {
        moveTo(p, 0f); lineTo(w - p, 0f); lineTo(w - p, p); lineTo(w, p); lineTo(w, h - p); lineTo(w - p, h - p)
        lineTo(w - p, h); lineTo(p, h); lineTo(p, h - p); lineTo(0f, h - p); lineTo(0f, p); lineTo(p, p); close()
    }
    drawPath(outline, SynthPalette.black.toColor())
    drawRect(fill, Offset(p, p), Size(w - 2 * p, h - 2 * p))
    if (shine != null) drawRect(shine, Offset(p, h - 2 * p), Size(w - 2 * p, p))
}

/** A panel drawn as a pixel box behind its content. */
@Composable
fun PixelPanel(
    fill: PixelColor,
    shine: PixelColor? = null,
    pixel: Dp = 3.dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.drawBehind { pixelBox(fill.toColor(), shine?.toColor(), pixel.toPx()) },
        content = { Box(Modifier.padding(pixel), content = content) },
    )
}
