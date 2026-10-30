/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.visuals

import io.peekandpoke.klang.Player
import io.peekandpoke.klang.comp.Motor
import io.peekandpoke.klang.comp.MotorBackground
import io.peekandpoke.klang.comp.MotorReadings
import io.peekandpoke.klang.comp.Oscilloscope
import io.peekandpoke.klang.comp.PlateComposition
import io.peekandpoke.klang.comp.PlayerMiniStats
import io.peekandpoke.klang.comp.ScreenRect
import io.peekandpoke.klang.comp.Spectrumeter
import io.peekandpoke.klang.comp.VolumeKnob
import io.peekandpoke.klang.spine.SwingingLamp
import io.peekandpoke.klang.spine.VisualState
import io.peekandpoke.klang.ui.comp.KlangGauge
import io.peekandpoke.klang.ui.comp.KlangKnob
import io.peekandpoke.klang.ui.comp.KlangMenuButton
import io.peekandpoke.klang.ui.feel.KlangRole
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.klang.visuals.VisualsParts.glassDome
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.Unsubscribe
import kotlinx.browser.window
import kotlinx.css.Color
import kotlinx.css.px
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.title
import org.w3c.dom.HTMLElement
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

@Suppress("FunctionName")
fun Tag.VisualsBoard(
    composition: PlateComposition,
    onLeave: () -> Unit,
    light: Stream<SwingingLamp.Light> = VisualState.light,
    onMotorButton: () -> Unit = onLeave,
    rainSpeed: Double = 1.0,
    onRainSpeed: (Double) -> Unit = {},
) = comp(
    VisualsBoard.Props(
        composition = composition,
        onLeave = onLeave,
        light = light,
        onMotorButton = onMotorButton,
        rainSpeed = rainSpeed,
        onRainSpeed = onRainSpeed,
    )
) {
    VisualsBoard(it)
}

/**
 * The parts of a visual on their tiles (the maintainer, 2026-10-29: "implement the tiles individually and compose the
 * screen as needed"): each tile of the [Props.composition] gets its content on the plate the background draws for it,
 * both placed by the same geometry ([MotorBackground.tilesOnScreen]):
 *
 * - [PlateComposition.GAUGES]: the volume knob, the three gauges and the menu button, each in the middle of its tile;
 *   the menu button leaves the visual, or does what the visual gives it ([Props.onMotorButton]).
 * - [VisualsCompositions.BOARD]: the scope in a hole cut into the plate, its inside lit by the signal, and under it the
 *   spectrum in a well, a tile row each (the maintainer, 2026-10-29).
 * - [VisualsCompositions.SCOPE_SPECTRUM]: the hole and the well side by side, one tile row.
 * - [VisualsCompositions.PLAYLIST]: the built-in songs in a well, what plays above them ([KlangampPlaylist]).
 * - [VisualsCompositions.AMP]: the desktop player ([KlangampPlayer]) behind glass, scaled to its plate.
 * - [VisualsCompositions.RAIN]: the voices played falling down a display, matrix style ([VoiceRain]).
 *
 * The displays wear glass domes that mirror the plate's lamp.
 */
class VisualsBoard(ctx: Ctx<Props>) : Component<VisualsBoard.Props>(ctx) {

    data class Props(
        val composition: PlateComposition,
        val onLeave: () -> Unit,
        /** The lamp's light, which the glass domes reflect ([VisualState.light]). */
        val light: Stream<SwingingLamp.Light>,
        /** What the menu button in the gauges' row does: leave, or switch the tiles (Klang Amp tiles). */
        val onMotorButton: () -> Unit,
        /** How fast the voices fall on the rain's display ([VisualsCompositions.RAIN]), 1 as made. */
        val rainSpeed: Double,
        /** The rain's speed knob turned: in the gauges' row where the menu button stands elsewhere. */
        val onRainSpeed: (Double) -> Unit,
    )

    companion object {
        /** The gauges' and the knobs' sizes, as shares of a tile's width. */
        private const val MIDDLE_GAUGE = 0.62
        private const val SIDE_GAUGE = 0.5
        private const val KNOB = 0.36

        /** The hole's and the screen's heights on the board's plate, as shares of a tile. */
        private const val HOLE_HEIGHT = 0.42
        private const val SCREEN_HEIGHT = 0.8

        /** The hole's and the screen's height side by side on one tile row, as a share of a tile. */
        private const val SIDE_BY_SIDE_HEIGHT = 0.66

        /**
         * How far a full-scale signal swings in every hole, as a share of a tile: the Motor's plate's, half its hole
         * (the maintainer, 2026-10-30: "the distance from the center must not depend on the height of the element ...
         * the 3x1 tiles version has the correct setting"; side by side, the taller hole swung 1.6 times as far,
         * "hectic"; KLANG-00391).
         */
        private const val SCOPE_SWING = HOLE_HEIGHT / 2

        /** How far the scope's smoke may rise out of its hole, and go to its sides and below it, as shares of a tile. */
        private const val SMOKE_UP = 0.6
        private const val SMOKE_SIDES = 0.3
        private const val SMOKE_DOWN = 0.2

        /**
         * The margin round a display inside its plate, as a share of a tile (the maintainer, 2026-10-29: "Keep a left
         * and right margin").
         */
        const val SIDE_MARGIN = 0.15

        /** How far the displays' corners are cut, as a large screen's (KlangScreen.Size.Large). */
        private const val CUT_PX = 14

        /** The rain's speed knob: from a quarter of the speed to three times, evenly on a log scale, 1 a little past the middle. */
        private const val RAIN_SLOWEST = 0.25
        private const val RAIN_FASTEST = 3.0

        /** The rain's speed at the knob's [position], 0 to 1. */
        fun rainSpeedOf(position: Double): Double =
            RAIN_SLOWEST * (RAIN_FASTEST / RAIN_SLOWEST).pow(position.coerceIn(0.0, 1.0))

        /** The knob's position for the rain's [speed]. */
        fun rainKnobOf(speed: Double): Double =
            (ln(speed.coerceIn(RAIN_SLOWEST, RAIN_FASTEST) / RAIN_SLOWEST) / ln(RAIN_FASTEST / RAIN_SLOWEST)).coerceIn(0.0, 1.0)
    }

    private val look by subscribingTo(KlangTheme.look)

    /** The lamp followed for the glass; none once unmounted. */
    private var lightFollow: Unsubscribe? = null

    init {
        lifecycle {
            onMount {
                findHighlights()
                lightFollow = props.light.subscribeToStream { reflect(it) }
            }
            // A redraw may bring other domes (the Motor button's tile states, a resize)
            onUpdate { findHighlights() }
            onUnmount {
                lightFollow?.invoke()
                lightFollow = null
                // A song Klangamp started stops when the visual is left
                KlangampDeck.leave()
            }
        }
    }

    /**
     * The lamp mirrored in the glass domes: each dome's highlight moved toward the lamp and brightened with its light,
     * by its transform and its opacity alone, which the compositor does without painting anything again (the
     * maintainer's trace, 2026-10-29: the domes' gradients, which read the lamp from variables on the board, were
     * repainted every frame, and the page layer under them). Written onto the highlights, not drawn by Kraft: a frame
     * redraws nothing.
     */
    private fun reflect(light: SwingingLamp.Light) {
        val transform = VisualsParts.highlightTransform(swayX = light.swayX, swayY = light.swayY)
        val opacity = VisualsParts.highlightOpacity(level = light.level).toString()

        for (highlight in highlights) {
            highlight.style.transform = transform
            highlight.style.opacity = opacity
        }
    }

    /** The domes' highlights, looked up when the board is drawn, not on every light. */
    private var highlights: List<HTMLElement> = emptyList()

    private fun findHighlights() {
        val found = (dom as? HTMLElement)?.querySelectorAll(".${VisualsParts.HIGHLIGHT}")

        highlights = found?.let { list -> (0 until list.length).mapNotNull { list.item(it) as? HTMLElement } } ?: emptyList()
    }

    /** What plays now, from here or from any page: the Motor's plate's play/stop shows it. */
    private val nowPlaying by subscribingTo(Player.nowPlaying)

    private val cylinders by subscribingTo(MotorReadings.cylinders())

    private val heat by subscribingTo(MotorReadings.heat())

    private val voices by subscribingTo(MotorReadings.voices())

    override fun VDom.render() {
        val width = window.innerWidth.toDouble()
        val height = window.innerHeight.toDouble()
        val tile = MotorBackground.tileWidthPx(height)
        val spots = MotorBackground.tilesOnScreen(screenWidth = width, screenHeight = height, composition = props.composition)
        val margin = SIDE_MARGIN * tile

        div {
            key = "visuals-board"
            css {
                put("position", "absolute")
                put("inset", "0")
                // The gauges glow in the background lamp's colour, the look's metal, as on the start page
                put("--kl-gauge-glow", "color-mix(in srgb, var(--kl-metal-mid) 50%, transparent)")
                // In after the plate's light has come up, about two and a half seconds, hidden until then (the
                // maintainer, 2026-10-29: "Fade in the ui after the background")
                // (the maintainer, 2026-10-29: "the contents must fade in earlier and a bit faster"; 1.5 s after 2.5 s
                // before)
                put("animation", "kl-visuals-fade-in 1s ease-in 1.2s both")
            }

            for (placed in props.composition.tiles) {
                val rect = spots.getValue(placed.id)

                when (placed.id) {
                    PlateComposition.GAUGES -> spot(key = placed.id, rect = rect) { renderGauges(tile) }

                    // A tile row each (the maintainer, 2026-10-29: "align the osci and spectrum to the tiles")
                    VisualsCompositions.BOARD -> {
                        val holeRow = rect.copy(bottom = rect.top + tile)
                        val screenRow = rect.copy(top = rect.top + tile)

                        spot(key = "hole-row", rect = holeRow) {
                            renderHole(width = rect.width - 2 * margin, height = tile * HOLE_HEIGHT, tile = tile)
                        }
                        spot(key = "screen-row", rect = screenRow) {
                            renderScreen(width = rect.width - 2 * margin, height = tile * SCREEN_HEIGHT)
                        }
                    }

                    VisualsCompositions.SCOPE_SPECTRUM -> spot(key = placed.id, rect = rect, gap = margin) {
                        val each = (rect.width - 3 * margin) / 2

                        renderHole(width = each, height = tile * SIDE_BY_SIDE_HEIGHT, tile = tile)
                        renderScreen(width = each, height = tile * SIDE_BY_SIDE_HEIGHT)
                    }

                    VisualsCompositions.PLAYLIST -> spot(key = placed.id, rect = rect) {
                        renderPlaylist(width = rect.width - 2 * margin, height = rect.height - 2 * margin)
                    }

                    VisualsCompositions.AMP -> spot(key = placed.id, rect = rect) {
                        renderAmp(size = rect.width - 2 * margin)
                    }

                    VisualsCompositions.RAIN -> spot(key = placed.id, rect = rect) {
                        well(
                            key = "rain",
                            width = rect.width - 2 * margin,
                            height = rect.height - 2 * margin,
                            padding = "8px",
                        ) {
                            VoiceRain(speed = props.rainSpeed)
                        }
                    }

                    // The title is cut into the plate; a tile this board does not know stays a plain plate
                    else -> Unit
                }
            }
        }
    }

    /** A tile's content, in the middle of its [rect] both ways. */
    private fun FlowContent.spot(key: String, rect: ScreenRect, gap: Double = 0.0, content: FlowContent.() -> Unit) {
        div {
            this.key = key
            css {
                put("position", "absolute")
                put("left", "${rect.left}px")
                put("top", "${rect.top}px")
                put("width", "${rect.width}px")
                put("height", "${rect.height}px")
                put("display", "flex")
                put("align-items", "center")
                put("justify-content", "center")
                put("gap", "${gap}px")
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

            // With the rain's display beside them, a knob for how fast the voices fall in the menu button's place (the
            // maintainer, 2026-10-29: "Instead of the Motor button we could have another knob changing the speed")
            if (props.composition.tiles.any { it.id == VisualsCompositions.RAIN }) {
                div {
                    title = "Falling speed: ${(props.rainSpeed * 100).roundToInt()} %\ndrag, or the arrow keys"

                    KlangKnob(
                        name = "Falling speed",
                        value = rainKnobOf(props.rainSpeed),
                        onChange = { props.onRainSpeed(rainSpeedOf(it)) },
                        size = (tile * KNOB).px,
                        dragPixels = 200.0,
                    )
                }
            } else if (props.composition.tiles.any { it.id == VisualsCompositions.BOARD }) {
                // On the Motor's plate the menu button's dress plays and stops (the maintainer, 2026-10-29: "on the first
                // visual replace the motor button with a play / stop button ... same button style other icons and
                // functionality"): whatever plays stops; nothing playing, the song open on the page under it plays, as it
                // stands in its editor ("the first visual has to play the song the user had open"), else Klang Amp's
                // last song, at first the playlist's first
                val playing = nowPlaying != null

                KlangMenuButton(
                    open = playing,
                    onClick = {
                        when {
                            playing -> KlangampDeck.stop()
                            else -> VisualState.openSong()?.play?.invoke() ?: KlangampDeck.playAgain()
                        }
                    },
                    size = (tile * KNOB).px,
                    label = if (playing) "Stop" else "Play",
                    icon = if (playing) ({ stop }) else ({ play }),
                )
            } else {
                KlangMenuButton(open = false, onClick = { props.onMotorButton() }, size = (tile * KNOB).px)
            }
        }
    }

    /**
     * The scope in a hole cut into the plate: a dark recess, its rim catching the light, the trace radiating in it. Its
     * swing is the same in every hole, [SCOPE_SWING] of a [tile].
     */
    private fun FlowContent.renderHole(width: Double, height: Double, tile: Double) {
        // The hole, the scope and the glass as layers of their own, so only the hole and its glass are cut to its
        // shape and the scope's smoke can rise out of it (the maintainer, 2026-10-30: "if the smoke could come "out of
        // the hole"")
        div {
            key = "hole"
            css {
                put("position", "relative")
                put("flex", "none")
                put("width", "${width}px")
                put("height", "${height}px")
            }

            div {
                css {
                    put("position", "absolute")
                    put("inset", "0")
                    // Cut corners, as the screens have, not round ones (the maintainer, 2026-10-29: "do not use round
                    // corners but the triangle again")
                    put("clip-path", VisualsParts.cutCorners(CUT_PX))
                    put("background", "#050505")
                    put(
                        "box-shadow",
                        "inset 0 6px 14px rgba(0, 0, 0, 0.9), inset 0 -1px 0 rgba(255, 255, 255, 0.08), " +
                            "0 1px 0 rgba(255, 255, 255, 0.12)",
                    )
                }
            }

            div {
                css {
                    put("position", "absolute")
                    put("inset", "0 ${height * 0.36}px")
                }

                Oscilloscope(
                    player = Player.player,
                    strokeColor = Color(look[KlangRole.ScopeTrace]),
                    swingPx = tile * SCOPE_SWING,
                    smokeRoom = Oscilloscope.SmokeRoom(
                        top = tile * SMOKE_UP,
                        sides = tile * SMOKE_SIDES,
                        bottom = tile * SMOKE_DOWN,
                    ),
                )
            }

            div {
                css {
                    put("position", "absolute")
                    put("inset", "0")
                    put("clip-path", VisualsParts.cutCorners(CUT_PX))
                    put("pointer-events", "none")
                }

                glassDome()
            }
        }
    }

    /** The spectrum on a screen of its own, the editor's kind: a well with cut corners. */
    private fun FlowContent.renderScreen(width: Double, height: Double) {
        well(key = "screen", width = width, height = height, padding = "10px 12px 12px") {
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
        }
    }

    /** The playlist on a screen of its own, what plays above the songs. */
    private fun FlowContent.renderPlaylist(width: Double, height: Double) {
        well(key = "playlist", width = width, height = height, padding = "10px 6px") {
            KlangampPlaylist(nowLine = true)
        }
    }

    /**
     * The desktop player behind glass, laid on its plate as a part of its own (the maintainer, 2026-10-29: "Hide this
     * winamp behind glass and aligned to the tiles as if it was its own element that can be placed in the board of
     * tiles"): drawn at its own size and scaled to the plate, a sunk frame round it, the glass over it.
     */
    private fun FlowContent.renderAmp(size: Double) {
        div {
            key = "amp"
            css {
                put("position", "relative")
                put("flex", "none")
                put("width", "${size}px")
                put("height", "${size}px")
                put("clip-path", VisualsParts.cutCorners(CUT_PX))
                put("background", "#000")
                put("box-shadow", "inset 0 6px 14px rgba(0, 0, 0, 0.9), inset 0 0 0 1px rgba(255, 255, 255, 0.06)")
                put("overflow", "hidden")
            }

            val inset = CUT_PX.toDouble()

            div {
                css {
                    put("position", "absolute")
                    put("left", "${inset}px")
                    put("top", "${inset}px")
                }

                KlangampPlayer(scale = (size - 2 * inset) / KlangampPlayer.SIZE_PX, onLeave = { props.onLeave() })
            }

            glassDome()
        }
    }

    /** A well with cut corners, cut out of the plate, a glass dome over it. */
    private fun FlowContent.well(
        key: String,
        width: Double,
        height: Double,
        padding: String,
        content: FlowContent.() -> Unit,
    ) {
        div {
            this.key = key
            css {
                put("display", "flex")
                put("flex", "none")
                put("width", "${width}px")
                put("height", "${height}px")
            }

            // KlangScreen paints its corners in a ground colour, which over the lit plate would show as patches
            div {
                css {
                    put("flex", "1")
                    // As wide as the screen and no wider: the spectrum's canvas would push it out on a small window
                    put("min-width", "0")
                    put("min-height", "0")
                    put("position", "relative")
                    // Room under the spectrum's floor too, as at its sides (the maintainer, 2026-10-29)
                    put("padding", padding)
                    put("background", "#000")
                    put("clip-path", VisualsParts.cutCorners(CUT_PX))
                    put("box-shadow", "inset 0 6px 14px rgba(0, 0, 0, 0.9), inset 0 0 0 1px rgba(255, 255, 255, 0.06)")
                }

                content()

                glassDome()
            }
        }
    }
}
