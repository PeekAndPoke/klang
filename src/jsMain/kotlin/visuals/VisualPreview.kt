/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.visuals

import io.peekandpoke.ultra.html.css
import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.unsafe

/**
 * A small picture of a visual on its card in the Visuals tab (the maintainer, 2026-10-29: "Add a preview to the
 * cards", then "can you draw them as with pen and paper?", "the lines need to be a bit more straight"): a sketch in ink
 * on a sheet of ruled paper, its lines a touch shaky and drawn twice, as a steady hand draws them. The paper is paper in every look.
 */
fun FlowContent.VisualPreview(visual: VisualsScreen.Visual) {
    div {
        attributes["aria-hidden"] = "true"
        css {
            put("width", "100%")
            put("height", "96px")
            put("margin-bottom", "8px")
            put("overflow", "hidden")
            put("border-radius", "2px")
            // Ruled paper, a little worn at its edges
            put(
                "background",
                "radial-gradient(ellipse at center, transparent 55%, rgba(120, 90, 40, 0.18) 100%), " +
                    "repeating-linear-gradient(0deg, rgba(70, 110, 190, 0.16) 0 1px, transparent 1px 12px), " +
                    "#ebe4d2",
            )
            put("box-shadow", "inset 0 0 0 1px rgba(0, 0, 0, 0.25)")
        }

        unsafe {
            +sketch(
                id = visual.name,
                drawing = when (visual) {
                    VisualsScreen.Visual.Plate -> PLATE
                    VisualsScreen.Visual.Klangamp -> KLANGAMP
                    VisualsScreen.Visual.KlangampTiles -> KLANGAMP_TILES
                    VisualsScreen.Visual.KlangampRain -> KLANGAMP_RAIN
                },
            )
        }
    }
}

/** The ink. */
private const val INK = "#26324f"

/** The highlighter, for what the eye should find. */
private const val MARKER = "rgba(245, 205, 60, 0.55)"

/**
 * A drawing in ink, made shaky by a noise that bends its lines, and drawn a second time a hair off and fainter with
 * another noise: so each line looks drawn by hand, twice over.
 */
private fun sketch(id: String, drawing: String): String {
    val shaky = "kl-sketch-$id-a"
    val shakier = "kl-sketch-$id-b"

    return """
        <svg viewBox="0 0 240 96" width="100%" height="96" preserveAspectRatio="xMidYMid meet" style="display:block">
          <defs>
            <filter id="$shaky"><feTurbulence type="fractalNoise" baseFrequency="0.02" numOctaves="1" seed="3"/>
              <feDisplacementMap in="SourceGraphic" scale="1.1"/></filter>
            <filter id="$shakier"><feTurbulence type="fractalNoise" baseFrequency="0.025" numOctaves="1" seed="11"/>
              <feDisplacementMap in="SourceGraphic" scale="1.4"/></filter>
          </defs>
          <g fill="none" stroke="$INK" stroke-width="1.1" stroke-linecap="round" stroke-linejoin="round"
             font-family="'Segoe Print', 'Bradley Hand', 'Comic Sans MS', cursive" filter="url(#$shaky)">$drawing</g>
          <g fill="none" stroke="$INK" stroke-width="0.7" stroke-linecap="round" stroke-linejoin="round" opacity="0.4"
             transform="translate(0.5 0.4)" font-family="'Segoe Print', 'Bradley Hand', 'Comic Sans MS', cursive"
             filter="url(#$shakier)">$drawing</g>
        </svg>
    """.trimIndent()
}

/** The lamp over the plate, the title on its plate, the gauges in a row, the scope's hole and the spectrum's screen. */
private val PLATE = """
    <path d="M24 6 L24 14"/>
    <path d="M18 14 Q24 10 30 14 L28 22 Q24 25 20 22 Z"/>
    <path d="M14 26 L10 30 M24 28 L24 33 M34 26 L38 30" stroke-width="0.8"/>
    <text x="12" y="44" font-size="9" fill="$INK" stroke="none">lamp</text>
    <path d="M40 22 Q56 18 66 16" stroke-dasharray="2 3" stroke-width="0.8"/>
    <rect x="70" y="5" width="100" height="21" fill="$MARKER" stroke="none"/>
    <rect x="70" y="5" width="100" height="21"/>
    <text x="120" y="20" font-size="11" text-anchor="middle" letter-spacing="1.5" fill="$INK" stroke="none">KLANGMOTOR</text>
    <circle cx="88" cy="38" r="4"/><path d="M88 38 L86 35"/>
    <circle cx="104" cy="38" r="5.5"/><path d="M104 38 L101 35"/>
    <circle cx="120" cy="38" r="7"/><path d="M120 38 L122 33"/>
    <circle cx="136" cy="38" r="5.5"/><path d="M136 38 L134 34"/>
    <circle cx="152" cy="38" r="4"/><path d="M150 37 L154 37 M150 39 L154 39"/>
    <path d="M84 50 L156 50 L160 54 L160 62 L156 66 L84 66 L80 62 L80 54 Z"/>
    <path d="M86 58 Q92 52 98 58 T110 58 T122 58 T134 58 T146 58 T154 58"/>
    <path d="M84 70 L156 70 L160 74 L160 88 L156 92 L84 92 L80 88 L80 74 Z"/>
    <path d="M88 88 L88 80 M93 88 L93 76 M98 88 L98 78 M103 88 L103 74 M108 88 L108 79 M113 88 L113 77 M118 88 L118 75
             M123 88 L123 80 M128 88 L128 78 M133 88 L133 82 M138 88 L138 80 M143 88 L143 84 M148 88 L148 83 M153 88 L153 85"/>
    <path d="M196 58 Q184 60 164 58 M168 55 L164 58 L168 61" stroke-width="0.9"/>
    <text x="198" y="61" font-size="9" fill="$INK" stroke="none">scope</text>
    <path d="M196 82 Q184 84 164 82 M168 79 L164 82 L168 85" stroke-width="0.9"/>
    <text x="198" y="85" font-size="9" fill="$INK" stroke="none">spectrum</text>
""".trimIndent()

/**
 * The main window, its display, the scope across it and its keys, and the playlist under it, a song marked, all behind
 * glass on its tiles; a note says how it plays.
 */
private val KLANGAMP = """
    <path d="M52 0 L188 0 M52 96 L188 96" stroke-width="0.5" stroke-dasharray="3 3"/>
    <path d="M150 6 L136 22 M158 8 L146 22" stroke-width="0.6" opacity="0.7"/>
    <rect x="58" y="4" width="124" height="44"/>
    <path d="M61 9 L96 9 M61 11 L96 11 M144 9 L176 9 M144 11 L176 11" stroke-width="0.6"/>
    <text x="120" y="12" font-size="6.5" text-anchor="middle" letter-spacing="1" fill="$INK" stroke="none">KLANG AMP</text>
    <path d="M178 7 L181 10 M181 7 L178 10" stroke-width="0.7"/>
    <rect x="62" y="15" width="42" height="21"/>
    <text x="66" y="25" font-size="9" fill="$INK" stroke="none">1:23</text>
    <path d="M66 34 L66 31 M69 34 L69 29 M72 34 L72 30 M75 34 L75 28 M78 34 L78 31 M81 34 L81 29 M84 34 L84 32
             M87 34 L87 30 M90 34 L90 32 M93 34 L93 31 M96 34 L96 33 M99 34 L99 32"/>
    <rect x="108" y="15" width="70" height="7"/>
    <path d="M111 19 Q114 17 117 19 T123 19 M127 19 L135 19 M139 19 Q142 17 145 19 T151 19 M155 19 L166 19" stroke-width="0.7"/>
    <rect x="108" y="25" width="70" height="11"/>
    <text x="111" y="33" font-size="6" fill="$INK" stroke="none">RPM 33</text>
    <rect x="62" y="39" width="7" height="6"/><rect x="71" y="39" width="7" height="6"/>
    <rect x="80" y="39" width="7" height="6"/><rect x="89" y="39" width="7" height="6"/>
    <path d="M73 40 L76 42 L73 44 Z" fill="$INK"/>
    <circle cx="150" cy="42" r="3"/><circle cx="159" cy="42" r="3.6"/><circle cx="168" cy="42" r="3"/>
    <circle cx="177" cy="42" r="2.6"/>
    <rect x="58" y="52" width="124" height="41"/>
    <path d="M61 57 L90 57 M61 59 L90 59 M150 57 L176 57 M150 59 L176 59" stroke-width="0.6"/>
    <text x="120" y="60" font-size="6.5" text-anchor="middle" letter-spacing="1" fill="$INK" stroke="none">PLAYLIST</text>
    <rect x="62" y="69" width="116" height="7" fill="$MARKER" stroke="none"/>
    <text x="64" y="68" font-size="6" fill="$INK" stroke="none">1.</text><path d="M72 66 L118 66 M162 66 L174 66" stroke-width="0.7"/>
    <text x="64" y="75" font-size="6" fill="$INK" stroke="none">2.</text><path d="M72 73 L132 73 M162 73 L174 73" stroke-width="0.7"/>
    <text x="64" y="82" font-size="6" fill="$INK" stroke="none">3.</text><path d="M72 80 L110 80 M162 80 L174 80" stroke-width="0.7"/>
    <text x="64" y="89" font-size="6" fill="$INK" stroke="none">4.</text><path d="M72 87 L124 87 M162 87 L174 87" stroke-width="0.7"/>
    <path d="M214 62 Q206 70 186 72 M190 69 L186 72 L190 75" stroke-width="0.9"/>
    <text x="196" y="56" font-size="9" fill="$INK" stroke="none">click,</text>
    <text x="202" y="84" font-size="9" fill="$INK" stroke="none">it plays</text>
""".trimIndent()

/** KLANGAMP on its plate, then the gauges' plate, the scope and the spectrum side by side, the playlist; tiles all. */
private val KLANGAMP_TILES = """
    <rect x="70" y="3" width="100" height="16" fill="$MARKER" stroke="none"/>
    <rect x="70" y="3" width="100" height="16"/>
    <text x="120" y="15" font-size="10" text-anchor="middle" letter-spacing="1.5" fill="$INK" stroke="none">KLANG AMP</text>
    <rect x="70" y="21" width="100" height="16"/>
    <circle cx="80" cy="29" r="3"/><circle cx="100" cy="29" r="4.5"/><path d="M100 29 L98 26"/>
    <circle cx="120" cy="29" r="5.5"/><path d="M120 29 L122 25"/>
    <circle cx="140" cy="29" r="4.5"/><path d="M140 29 L138 26"/><circle cx="160" cy="29" r="3"/>
    <rect x="70" y="39" width="100" height="16"/>
    <path d="M75 42 L117 42 L117 52 L75 52 Z"/>
    <path d="M78 47 Q82 43 86 47 T94 47 T102 47 T110 47 T115 47"/>
    <path d="M123 42 L165 42 L165 52 L123 52 Z"/>
    <path d="M127 51 L127 47 M131 51 L131 45 M135 51 L135 46 M139 51 L139 44 M143 51 L143 47 M147 51 L147 45
             M151 51 L151 48 M155 51 L155 46 M159 51 L159 49 M163 51 L163 48"/>
    <rect x="70" y="57" width="100" height="36"/>
    <rect x="75" y="68" width="90" height="6" fill="$MARKER" stroke="none"/>
    <path d="M75 61 L165 61" stroke-width="0.6"/>
    <path d="M78 66 L120 66 M78 72 L130 72 M78 78 L112 78 M78 84 L124 84 M78 90 L104 90" stroke-width="0.7"/>
    <path d="M10 11 L60 11" stroke-width="0.6"/><text x="10" y="8" font-size="8" fill="$INK" stroke="none">header</text>
    <path d="M10 29 L64 29" stroke-width="0.6"/><text x="10" y="26" font-size="8" fill="$INK" stroke="none">gauges</text>
    <path d="M178 47 L230 47" stroke-width="0.6"/><text x="180" y="44" font-size="8" fill="$INK" stroke="none">scope+spectrum</text>
    <path d="M178 75 L230 75" stroke-width="0.6"/><text x="180" y="72" font-size="8" fill="$INK" stroke="none">playlist</text>
""".trimIndent()

/** Klang Amp's column of tiles on the left; on the right a tall display, the voices falling down it as letters. */
private val KLANGAMP_RAIN = """
    <rect x="22" y="3" width="95" height="16" fill="$MARKER" stroke="none"/>
    <rect x="22" y="3" width="95" height="16"/>
    <text x="69" y="15" font-size="9" text-anchor="middle" letter-spacing="1" fill="$INK" stroke="none">KLANG AMP</text>
    <rect x="22" y="21" width="95" height="16"/>
    <circle cx="32" cy="29" r="3"/><circle cx="50" cy="29" r="4"/><circle cx="69" cy="29" r="5"/>
    <circle cx="88" cy="29" r="4"/><circle cx="106" cy="29" r="3"/>
    <rect x="22" y="39" width="95" height="16"/>
    <path d="M26 42 L66 42 L66 52 L26 52 Z"/><path d="M28 47 Q32 43 36 47 T44 47 T52 47 T60 47 T64 47"/>
    <path d="M73 42 L113 42 L113 52 L73 52 Z"/>
    <path d="M77 51 L77 47 M81 51 L81 45 M85 51 L85 46 M89 51 L89 44 M93 51 L93 47 M97 51 L97 45 M101 51 L101 48 M105 51 L105 46 M109 51 L109 49"/>
    <rect x="22" y="57" width="95" height="36"/>
    <rect x="26" y="66" width="87" height="6" fill="$MARKER" stroke="none"/>
    <path d="M28 64 L70 64 M28 70 L80 70 M28 76 L64 76 M28 82 L74 82 M28 88 L58 88" stroke-width="0.7"/>
    <rect x="121" y="3" width="76" height="90"/>
    <text x="132" y="24" font-size="8" text-anchor="middle" fill="$INK" stroke="none" opacity="0.55">c</text>
    <text x="132" y="33" font-size="8" text-anchor="middle" fill="$INK" stroke="none" opacity="1">4</text>
    <path d="M132 8 L132 16" stroke-width="0.5" stroke-dasharray="1 2"/>
    <text x="146" y="40" font-size="8" text-anchor="middle" fill="$INK" stroke="none" opacity="0.55">b</text>
    <text x="146" y="49" font-size="8" text-anchor="middle" fill="$INK" stroke="none" opacity="1">d</text>
    <path d="M146 24 L146 32" stroke-width="0.5" stroke-dasharray="1 2"/>
    <text x="160" y="18" font-size="8" text-anchor="middle" fill="$INK" stroke="none" opacity="0.55">e</text>
    <text x="160" y="27" font-size="8" text-anchor="middle" fill="$INK" stroke="none" opacity="0.55">b</text>
    <text x="160" y="36" font-size="8" text-anchor="middle" fill="$INK" stroke="none" opacity="1">3</text>
    <path d="M160 6 L160 10" stroke-width="0.5" stroke-dasharray="1 2"/>
    <text x="174" y="50" font-size="8" text-anchor="middle" fill="$INK" stroke="none" opacity="0.55">s</text>
    <text x="174" y="59" font-size="8" text-anchor="middle" fill="$INK" stroke="none" opacity="0.55">a</text>
    <text x="174" y="68" font-size="8" text-anchor="middle" fill="$INK" stroke="none" opacity="1">w</text>
    <path d="M174 34 L174 42" stroke-width="0.5" stroke-dasharray="1 2"/>
    <text x="188" y="32" font-size="8" text-anchor="middle" fill="$INK" stroke="none" opacity="0.55">g</text>
    <text x="188" y="41" font-size="8" text-anchor="middle" fill="$INK" stroke="none" opacity="1">2</text>
    <path d="M188 16 L188 24" stroke-width="0.5" stroke-dasharray="1 2"/>
    <path d="M200 30 Q214 26 222 34 M200 30 L204 27 M200 30 L204 33" stroke-width="0.8"/>
    <text x="206" y="48" font-size="8" fill="$INK" stroke="none">voices</text>
    <text x="206" y="58" font-size="8" fill="$INK" stroke="none">falling</text>
""".trimIndent()
