//
//  Theme.kt
//
//  Look of the instrument: dark panel, cream markings, orange highlights,
//  walnut side panels. The same colors as iOS's Theme.swift.
//

package com.tuesdaygames.synth.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Theme {
    val panel = Color(0xFF1C1B19)
    val module = Color(0xFF2A2725)
    val moduleEdge = Color.White.copy(alpha = 0.06f)
    val accent = Color(0xFFFF8A3D)
    val cream = Color(0xFFF0E6D4)
    val label = cream.copy(alpha = 0.75f)
    val track = Color.Black.copy(alpha = 0.45f)
    val button = Color(0xFF3B3836)
    val green = Color(0xFF34C759)

    val knobGradient = Brush.verticalGradient(listOf(Color(0xFF4D4D4D), Color(0xFF1F1F1F)))

    val wood = Brush.linearGradient(
        listOf(Color(0xFF5C3319), Color(0xFF784726), Color(0xFF54301A), Color(0xFF6E4021)),
    )

    /** The four step colors, one per beat. */
    val beatColors = listOf(Color(0xFFED4533), Color(0xFFFA8C33), Color(0xFFFAD14D), Color(0xFFF0E6D4))

    fun labelStyle(size: TextUnit = 10.sp) = TextStyle(fontSize = size, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif)
    fun titleStyle(size: TextUnit = 10.sp) = TextStyle(fontSize = size, fontWeight = FontWeight.Black, fontFamily = FontFamily.SansSerif)

    val material = darkColorScheme(
        primary = accent,
        onPrimary = Color.Black,
        secondary = accent,
        background = panel,
        surface = module,
        onSurface = cream,
        onBackground = cream,
    )
}

@Composable
fun SynthTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Theme.material, content = content)

/** Control sizes adapt to the screen; set once at the top of each panel. */
val LocalKnobSize = compositionLocalOf<Dp> { 44.dp }
val LocalFaderHeight = compositionLocalOf<Dp> { 100.dp }
