//
//  Beat.kt
//
//  A premade beat: a few patterns, a song that strings them together, a tempo
//  and swing, and small tweaks to the kit. Generated from iOS's Beat.swift so
//  both apps ship exactly the same beats.
//
//  Patterns are written as text, one row per drum, 16 characters each:
//    x = hit, X = accented hit, . = rest (spaces are ignored).
//  Drums are named by their short names: BD SD CP LT HT RS CB CH OH CY.
//

package com.tuesdaygames.synth.model

data class Beat(
    val name: String,
    val tempo: Float,
    val swing: Float = 0f,
    val patterns: List<Map<String, String>>,
    val song: List<Int>,
    /** Per drum: param index (0 level, 1 tune, 2 decay, 3 tone) to value. */
    val kit: Map<String, Map<Int, Float>> = emptyMap(),
    /** Free beats load for everyone; the rest are part of the full version. */
    val isFree: Boolean = false,
) {
    companion object {
        fun parse(row: String): List<Int> = row.mapNotNull {
            when (it) {
                'x' -> 1
                'X' -> 2
                '.' -> 0
                else -> null
            }
        }

        val boomBap = Beat(
            name = "Boom Bap",
            tempo = 90f,
            swing = 0.45f,
            patterns = listOf(
                mapOf(  // A: main groove
                    "BD" to "X... ...x ..x. ....",
                    "SD" to ".... X... .... X...",
                    "CH" to "x.x. x.x. x.x. x.x.",
                ),
                mapOf(  // B: groove with a pickup
                    "BD" to "X... ...x ..x. ..x.",
                    "SD" to ".... X... .... X..x",
                    "CH" to "x.x. x.x. x.x. x...",
                    "OH" to ".... .... .... ..x.",
                ),
                mapOf(  // C: tom fill
                    "BD" to "X... ...x .... ....",
                    "SD" to ".... X... .... ....",
                    "HT" to ".... .... x.x. ....",
                    "LT" to ".... .... .... x.x.",
                    "CH" to "x.x. x.x. .... ....",
                ),
            ),
            song = listOf(0, 0, 0, 1, 0, 0, 0, 2),
            kit = mapOf("BD" to mapOf(1 to 0.45f, 2 to 0.6f), "SD" to mapOf(3 to 0.7f)),
            isFree = true,
        )

        val house = Beat(
            name = "Four on the Floor",
            tempo = 124f,
            swing = 0.15f,
            patterns = listOf(
                mapOf(
                    "BD" to "X... X... X... X...",
                    "CP" to ".... x... .... x...",
                    "CH" to "x... x... x... x...",
                    "OH" to "..x. ..x. ..x. ..x.",
                ),
                mapOf(
                    "BD" to "X... X... X... X...",
                    "CP" to ".... x... .... x...",
                    "OH" to "..x. ..x. ..x. ..x.",
                    "RS" to "...x .... .x.. ...x",
                    "CB" to ".... ..x. .... x...",
                ),
                mapOf(  // build-up
                    "BD" to "X... X... X... X...",
                    "CP" to ".... x... x.x. xxXX",
                    "OH" to "..x. ..x. ..x. ..x.",
                    "CY" to ".... .... .... ...x",
                ),
            ),
            song = listOf(0, 0, 1, 1, 0, 0, 1, 2),
            kit = mapOf("BD" to mapOf(2 to 0.4f), "OH" to mapOf(2 to 0.35f)),
            isFree = true,
        )

        val electro = Beat(
            name = "Electro Funk",
            tempo = 112f,
            swing = 0f,
            patterns = listOf(
                mapOf(
                    "BD" to "X... ..x. ..x. ....",
                    "SD" to ".... X... .... X...",
                    "CP" to ".... x... .... x...",
                    "CH" to "Xxxx Xxxx Xxxx Xxxx",
                    "CB" to "x..x ..x. ..x. x...",
                ),
                mapOf(
                    "BD" to "X... ..x. ..x. .x..",
                    "SD" to ".... X... .... X...",
                    "CP" to ".... x... .... x...",
                    "CH" to "Xxxx Xxxx Xxxx Xx..",
                    "OH" to ".... .... .... ..x.",
                    "LT" to ".... .... ..x. .x..",
                ),
            ),
            song = listOf(0, 0, 0, 1),
            kit = mapOf("BD" to mapOf(2 to 0.75f), "CB" to mapOf(0 to 0.45f)),
            isFree = false,
        )

        val halfTime = Beat(
            name = "Half-Time Boom",
            tempo = 140f,
            swing = 0f,
            patterns = listOf(
                mapOf(
                    "BD" to "X... .... ..x. ....",
                    "CP" to ".... .... X... ....",
                    "SD" to ".... .... x... ....",
                    "CH" to "x.x. x.x. x.xx x.x.",
                ),
                mapOf(
                    "BD" to "X... ...x ..x. ....",
                    "CP" to ".... .... X... ....",
                    "SD" to ".... .... x... ....",
                    "CH" to "x.x. xxxx x.x. x.xx",
                    "OH" to ".... .... .... ..x.",
                ),
            ),
            song = listOf(0, 1),
            kit = mapOf("BD" to mapOf(1 to 0.4f, 2 to 0.95f, 3 to 0.35f)),
            isFree = false,
        )

        val rock = Beat(
            name = "Garage Rock",
            tempo = 116f,
            swing = 0f,
            patterns = listOf(
                mapOf(
                    "BD" to "X... ..x. X... ....",
                    "SD" to ".... X... .... X...",
                    "CH" to "x.x. x.x. x.x. x.x.",
                ),
                mapOf(
                    "BD" to "X... ..x. X.x. ....",
                    "SD" to ".... X... .... X.xx",
                    "CH" to "x.x. x.x. x.x. x...",
                    "CY" to "x... .... .... ....",
                ),
            ),
            song = listOf(0, 0, 0, 1),
            kit = mapOf("SD" to mapOf(1 to 0.55f, 3 to 0.8f)),
            isFree = false,
        )

        val blank = Beat(name = "Blank", tempo = 120f, patterns = emptyList(), song = emptyList(), isFree = true)

        val factory: List<Beat> = listOf(boomBap, house, electro, halfTime, rock, blank)
    }
}
