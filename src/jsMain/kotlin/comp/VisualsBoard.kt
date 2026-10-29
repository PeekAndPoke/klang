/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_bridge.analyzer.AnalyzerBufferHistory
import io.peekandpoke.klang.ui.comp.KlangGauge
import io.peekandpoke.klang.ui.comp.KlangMenuButton
import io.peekandpoke.klang.ui.feel.KlangRole
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.streams.ops.map
import io.peekandpoke.ultra.streams.ops.smooth
import io.peekandpoke.ultra.streams.ops.switchMapNotNull
import kotlinx.browser.window
import kotlinx.css.Color
import kotlinx.css.px
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import org.khronos.webgl.get
import org.w3c.dom.HTMLElement
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Suppress("FunctionName")
fun Tag.VisualsBoard(onLeave: () -> Unit, lamp: () -> MotorBackground.Lamp?) = comp(
    VisualsBoard.Props(onLeave = onLeave, lamp = lamp)
) {
    VisualsBoard(it)
}

/**
 * The Motor laid out on the visual's plate (the maintainer, 2026-10-29: "put the gauges on the five tiles below the motor.
 * create a "hole" where the osci is placed. The stronger the osci signal the more it lights up the inner of the hole. The
 * spectrumeter will be shown in a "screen" display"): under the KLANGMOTOR title the volume knob, the three gauges and
 * the menu button, each in the middle of its tile, as the start page sets its gauges; under them the scope in a hole cut
 * into the plate, its inside lit by the signal; under it the spectrum in a well; the hole and the well three tiles wide,
 * a tile row each, their corners cut. The menu button leaves the visual.
 */
class VisualsBoard(ctx: Ctx<Props>) : Component<VisualsBoard.Props>(ctx) {

    data class Props(
        val onLeave: () -> Unit,
        /** The plate's lamp, which the glass domes reflect; null until the plate is mounted. */
        val lamp: () -> MotorBackground.Lamp?,
    )

    companion object {
        /** The gauges' and the knobs' sizes, as shares of a tile's width. */
        private const val MIDDLE_GAUGE = 0.62
        private const val SIDE_GAUGE = 0.5
        private const val KNOB = 0.36

        /** The hole's and the screen's heights, as shares of a tile; they are as wide as the board's plate. */
        private const val HOLE_HEIGHT = 0.42
        private const val SCREEN_HEIGHT = 0.8

        /**
         * The margin left and right of the hole and the screen inside the board's plate, as a share of a tile (the
         * maintainer, 2026-10-29: "Keep a left and right margin").
         */
        const val SIDE_MARGIN = 0.15

        /** How far the hole's and the screen's corners are cut, as a large screen's (KlangScreen.Size.Large). */
        private const val CUT_PX = 14

        /** A box with its four corners cut off at 45 degrees, [cut] px each way. */
        private fun cutCorners(cut: Int): String =
            "polygon(${cut}px 0, calc(100% - ${cut}px) 0, 100% ${cut}px, 100% calc(100% - ${cut}px), " +
                "calc(100% - ${cut}px) 100%, ${cut}px 100%, 0 calc(100% - ${cut}px), 0 ${cut}px)"

        /** How the hole's glow follows the signal: the share of the way kept per 10 ms. */
        private const val GLOW_SMOOTHING = 0.85
    }

    private val look by subscribingTo(KlangTheme.look)

    /** The frame that follows the lamp, for the glass; none once unmounted. */
    private var lightFrame: Int? = null

    init {
        lifecycle {
            onMount { followLight() }
            onUnmount { lightFrame?.let { window.cancelAnimationFrame(it) } }
        }
    }

    /**
     * The lamp's place and brightness into the board's CSS, every frame, for the glass domes' reflection: two variables
     * on the board's element, no redraw.
     */
    private fun followLight() {
        val lamp = props.lamp()
        val style = (dom as? HTMLElement)?.style

        if (lamp != null && style != null) {
            style.setProperty("--kl-glass-x", lamp.x.toString())
            style.setProperty("--kl-glass-y", lamp.y.toString())
            style.setProperty("--kl-glass-light", lamp.level.coerceIn(0.0, 2.0).toString())
        }

        lightFrame = window.requestAnimationFrame { followLight() }
    }

    /** The current player's latest diagnostics; null while there is none. */
    private val online = Player.diagnostics.stream

    private val cylinders by subscribingTo(
        online.map { d -> d?.cylinders?.count { it.active }?.toDouble() }
            .smooth(sharePer10Ms = PlayerMiniStats.NEEDLE_SMOOTHING, near = 32 * PlayerMiniStats.NEEDLE_NEAR)
    )

    private val heat by subscribingTo(
        online.map { d -> d?.renderHeadroom?.let { 1.0 - it } }
            .smooth(sharePer10Ms = PlayerMiniStats.NEEDLE_SMOOTHING, near = PlayerMiniStats.NEEDLE_NEAR)
    )

    private val voices by subscribingTo(
        online.map { d -> d?.activeVoiceCount?.toDouble() }
            .smooth(sharePer10Ms = PlayerMiniStats.NEEDLE_SMOOTHING, near = 100 * PlayerMiniStats.NEEDLE_NEAR)
    )

    /** The scope's signal now, 0 (silence) to 1 (full scale): its loudest sample in the newest frame, smoothed. */
    private val signal by subscribingTo(
        Player.player.map { it?.getAnalyzer() }
            .switchMapNotNull { it.waveform }
            .map<AnalyzerBufferHistory?, Double?> { history ->
                if (history == null || history.size == 0) {
                    0.0
                } else {
                    val frame = history[0]
                    var peak = 0.0

                    for (i in 0 until history.bufferSize) {
                        peak = max(peak, abs(frame[i].toDouble()))
                    }

                    peak.coerceIn(0.0, 1.0)
                }
            }
            .smooth(sharePer10Ms = GLOW_SMOOTHING, near = 0.001)
    )

    override fun VDom.render() {
        val height = window.innerHeight.toDouble()
        val tile = MotorBackground.tileWidthPx(height)
        // The plate the texture draws under the scope and the spectrum; the gauges' row runs above it
        val plate = MotorBackground.boardPlateOnScreen(screenWidth = window.innerWidth.toDouble(), screenHeight = height)
        val top = plate.top - tile

        div {
            key = "visuals-board"
            css {
                put("position", "absolute")
                put("left", "0")
                put("right", "0")
                put("top", "${top}px")
                put("display", "flex")
                put("flex-direction", "column")
                put("align-items", "center")
                // The gauges glow in the background lamp's colour, the look's metal, as on the start page
                put("--kl-gauge-glow", "color-mix(in srgb, var(--kl-metal-mid) 50%, transparent)")
                // In after the plate's light has come up, about two and a half seconds, hidden until then (the
                // maintainer, 2026-10-29: "Fade in the ui after the background")
                put("animation", "kl-visuals-fade-in 1.5s ease-in 2.5s both")
            }

            // A tile row each, the content in its middle, the hole and the screen three tiles wide on the middle three
            // (the maintainer, 2026-10-29: "align the osci and spectrum to the tiles")
            renderGauges(tile)
            tileRow(tile, "hole-row") { renderHole(tile = tile, width = plate.width) }
            tileRow(tile, "screen-row") { renderScreen(tile = tile, width = plate.width) }
        }
    }

    /** A row one tile high, its content in the middle both ways. */
    private fun FlowContent.tileRow(tile: Double, key: String, content: FlowContent.() -> Unit) {
        div {
            this.key = key
            css {
                put("display", "flex")
                put("align-items", "center")
                put("justify-content", "center")
                put("height", "${tile}px")
            }

            content()
        }
    }

    /** The five, each in the middle of its tile: the tiles are centred on the screen, so are the columns. */
    private fun FlowContent.renderGauges(tile: Double) {
        div {
            key = "gauges"
            css {
                put("display", "grid")
                put("grid-template-columns", "repeat(5, ${tile}px)")
                put("height", "${tile}px")
                put("justify-items", "center")
                put("align-items", "center")
            }

            div { VolumeKnob(size = (tile * KNOB).px) }

            KlangGauge(
                name = "Active cylinders",
                value = cylinders,
                range = 0.0..32.0,
                size = (tile * SIDE_GAUGE).px,
                icon = { layer_group },
                display = { it.roundToInt().toString() },
            )

            KlangGauge(
                name = "Motor heat",
                value = heat,
                size = (tile * MIDDLE_GAUGE).px,
                icon = { microchip },
                display = { "${(it * 100).roundToInt()}%" },
                hotFrom = PlayerMiniStats.HEAT_HOT_FROM,
            )

            KlangGauge(
                name = "Active voices",
                value = voices,
                range = 0.0..100.0,
                size = (tile * SIDE_GAUGE).px,
                icon = { music },
                display = { it.roundToInt().toString() },
            )

            KlangMenuButton(open = false, onClick = { props.onLeave() }, size = (tile * KNOB).px)
        }
    }

    /**
     * The scope in a hole cut into the plate: a dark recess, its rim catching the light, its inside lit by the signal
     * in the scope's own colour, brighter the louder.
     */
    private fun FlowContent.renderHole(tile: Double, width: Double) {
        val glow = (signal ?: 0.0) * 100

        div {
            key = "hole"
            css {
                put("position", "relative")
                put("width", "${width - 2 * SIDE_MARGIN * tile}px")
                put("height", "${tile * HOLE_HEIGHT}px")
                // Cut corners, as the screens have, not round ones (the maintainer, 2026-10-29: "do not use round
                // corners but the triangle again")
                put("clip-path", cutCorners(CUT_PX))
                put(
                    "background",
                    "radial-gradient(ellipse at center, " +
                        "color-mix(in srgb, var(--kl-scope-trace) ${(glow * 0.55).roundToInt()}%, transparent) 0%, " +
                        "transparent 75%), #050505",
                )
                put(
                    "box-shadow",
                    "inset 0 6px 14px rgba(0, 0, 0, 0.9), inset 0 -1px 0 rgba(255, 255, 255, 0.08), " +
                        "0 1px 0 rgba(255, 255, 255, 0.12)",
                )
                put("overflow", "hidden")
            }

            div {
                css {
                    put("position", "absolute")
                    put("inset", "0 ${tile * 0.15}px")
                }

                Oscilloscope(player = Player.player, strokeColor = Color(look[KlangRole.ScopeTrace]))
            }

            glassDome()
        }
    }

    /**
     * A clear glass dome over what lies under it (the maintainer, 2026-10-29: "Both need transparent glass domes that
     * react to the light"): its curve, a brighter band at its top and a darker rim at its foot; and the lamp mirrored in
     * it, a soft highlight that moves with the lamp's sway (`--kl-glass-x`, `--kl-glass-y`, [followLight]) and grows with
     * its light (`--kl-glass-light`). The middle stays clear, so the scope and the spectrum read through it.
     */
    private fun FlowContent.glassDome() {
        div(classes = "kl-glass-dome") {
            attributes["aria-hidden"] = "true"
            css {
                put("position", "absolute")
                put("inset", "0")
                put("pointer-events", "none")
                put(
                    "background",
                    listOf(
                        // The lamp mirrored, a wide soft glow (the maintainer, 2026-10-29: a small sharp one was "the wrong
                        // direction", "even wider than before"): where the lamp is, toward it from the middle; up is up
                        "radial-gradient(ellipse 60% 85% at calc(50% + var(--kl-glass-x, 0) * 22%) " +
                            "calc(32% - var(--kl-glass-y, 0) * 22%), " +
                            "rgba(255, 255, 255, calc(var(--kl-glass-light, 1) * 0.06)) 0%, transparent 75%)",
                        // The dome round all the way (the maintainer, 2026-10-29: "or maybe even more round ... more
                        // domed", after a flatter try): an arc of light along its top, a faint glow at its foot where
                        // the light comes through, and its edges darker all round, the middle clear; the reflections
                        // quiet ("still too much reflection": the lamp's 0.14 and the arc's 0.14 before)
                        "radial-gradient(ellipse 75% 45% at 50% 0%, rgba(255, 255, 255, 0.08) 0%, transparent 70%)",
                        "radial-gradient(ellipse 60% 30% at 50% 100%, rgba(255, 255, 255, 0.05) 0%, transparent 70%)",
                        "radial-gradient(ellipse 70% 75% at 50% 50%, transparent 45%, rgba(0, 0, 0, 0.5) 100%)",
                    ).joinToString(", "),
                )
                // The shoulders: the glass rolling away along every edge (the maintainer, 2026-10-29: "more domed toward
                // the edges"), the fine bright line on its upper edge
                put("box-shadow", "inset 0 0 18px rgba(0, 0, 0, 0.45), inset 0 1px 0 rgba(255, 255, 255, 0.18)")
            }
        }
    }

    /** The spectrum on a screen of its own, the editor's kind: a well with cut corners. */
    private fun FlowContent.renderScreen(tile: Double, width: Double) {
        div {
            key = "screen"
            css {
                put("display", "flex")
                put("width", "${width - 2 * SIDE_MARGIN * tile}px")
                put("height", "${tile * SCREEN_HEIGHT}px")
            }

            // A well with cut corners, cut out of the plate: KlangScreen paints its corners in a ground colour, which
            // over the lit plate would show as patches
            div {
                css {
                    put("flex", "1")
                    // As wide as the screen and no wider: the spectrum's canvas would push it out on a small window
                    put("min-width", "0")
                    put("min-height", "0")
                    put("position", "relative")
                    // Room under the spectrum's floor too, as at its sides (the maintainer, 2026-10-29)
                    put("padding", "10px 12px 12px")
                    put("background", "#000")
                    put("clip-path", cutCorners(CUT_PX))
                    put("box-shadow", "inset 0 6px 14px rgba(0, 0, 0, 0.9), inset 0 0 0 1px rgba(255, 255, 255, 0.06)")
                }

                Spectrumeter(
                    colors = Motor.SPECTRUM_ROLES.map { Color(look[it]) },
                    numBoxesInStack = Motor.SPECTRUM_BOXES,
                    colorStep = Motor.SPECTRUM_COLOR_STEP,
                    numBuckets = Motor.SPECTRUM_COLUMNS,
                    // The light dancing through the columns, stronger than on the code page (the maintainer, 2026-10-29)
                    lighting = Spectrumeter.Lighting.Dancing,
                    // The hovered column's frequencies at the top of the screen (the maintainer, 2026-10-29)
                    labelAtTop = true,
                ) { Player.get() }

                glassDome()
            }
        }
    }
}
