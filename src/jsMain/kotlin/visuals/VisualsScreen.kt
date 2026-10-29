/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.visuals

import io.peekandpoke.klang.comp.Motor
import io.peekandpoke.klang.comp.MotorBackground
import io.peekandpoke.klang.comp.PlateComposition
import io.peekandpoke.klang.storage.LookStorage
import io.peekandpoke.klang.ui.comp.KlangCloseKey
import io.peekandpoke.klang.ui.comp.KlangSwatchButton
import io.peekandpoke.klang.ui.feel.KlangLookShelf
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.klang.workshop.keys.openKeyLayer
import io.peekandpoke.kraft.components.ComponentRef
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.modals.ModalsManager
import io.peekandpoke.kraft.modals.ModalsManager.Companion.modals
import io.peekandpoke.kraft.semanticui.modals.FadingModal
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.html.onMouseMove
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.ultra.streams.ops.distinct
import io.peekandpoke.ultra.streams.ops.map
import kotlinx.browser.window
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.button
import kotlinx.html.div

@Suppress("FunctionName")
fun Tag.VisualsScreen() = comp {
    VisualsScreen(it)
}

/**
 * The visuals over the whole window (the maintainer, 2026-10-29: "Add a tab in the bottom toolbar called "visuals" and
 * add one link card. This one will open a fullscreen overlay with the background of the startpage"), each a
 * composition of tiles on the start page's plate under its swaying lamp ([PlateComposition], [VisualsBoard]).
 *
 * Mounted once at the app's root; [shown] opens one, as a modal (the maintainer, 2026-10-29: "make the visual a Modal
 * ... then they react to back-navigation and close"): Esc, the close key, the menu button and the browser's back all
 * leave it.
 */
class VisualsScreen(ctx: NoProps) : PureComponent(ctx) {

    /** The visuals there are, a card each in the Visuals tab. */
    enum class Visual(val title: String, val description: String, val composition: () -> PlateComposition) {
        /** The start page's lamp over its plate, the Motor laid out under the title. */
        Plate(
            title = "The Motor's plate",
            description = "The start page's lamp over its plate, the whole window",
            composition = { VisualsCompositions.motorBoard },
        ),

        /** The Motor's elements as an old desktop player behind glass, a part of its own on the tiles. */
        Klangamp(
            title = "Klang Amp",
            description = "The Motor as a desktop player behind glass, the built-in songs on its playlist",
            composition = { VisualsCompositions.klangampGlass },
        ),

        /** Klangamp made of the plate's own tiles. */
        KlangampTiles(
            title = "Klang Amp tiles",
            description = "A player built from the plate's tiles: gauges, scope and spectrum, playlist",
            composition = { VisualsCompositions.klangampTiles },
        ),
    }

    companion object {
        /** The visual shown; null while none is. */
        val shown = StreamSource<Visual?>(null)

        /** Whether a visual is shown. */
        val open: Stream<Boolean> = shown.map { it != null }.distinct()

        /** What Klang Amp tiles shows under its parts; its Motor button steps through them, kept while the app runs. */
        val klangampTiles = StreamSource(TilesState.Tiles)
    }

    /**
     * What Klang Amp tiles shows under its parts (the maintainer, 2026-10-29: "without the tiles was also nice... can we
     * have a switch somewhere? Maybe the Motorbutton", "And a third state with no tiles at all").
     */
    enum class TilesState {
        /** Its tiles, the window round them black. */
        Tiles,

        /** No tiles: the parts on black, the title cut into the dark. */
        TitleOnly,

        /** Nothing: the parts alone on black. */
        Bare;

        /** The state the Motor button steps on to, round. */
        val next: TilesState get() = entries[(ordinal + 1) % entries.size]

        /** The composition as this state shows it. */
        fun apply(composition: PlateComposition): PlateComposition = when (this) {
            Tiles -> composition
            TitleOnly -> composition.copy(platesShown = false)
            Bare -> composition.copy(platesShown = false, tiles = composition.tiles.map { it.copy(engraving = null) })
        }
    }

    /** The modal showing [shown]'s visual; null while none is open. */
    private var handle: ModalsManager.Handle<Boolean>? = null

    /** The visual the open modal shows. */
    private var opened: Visual? = null

    @Suppress("unused")
    private val visual by subscribingTo(shown) { visual ->
        val open = handle

        // Asked for the one already open: nothing to do
        if (open != null && visual != null && opened == visual) {
            return@subscribingTo
        }

        handle = null
        open?.close()

        if (visual != null) {
            val next = modals.show { h -> VisualsModal(handle = h, visual = visual) }

            handle = next
            opened = visual
            next.onClose {
                if (handle === next) {
                    handle = null
                    shown(null)
                }
            }
        }
    }

    override fun VDom.render() {
        // Nothing of its own: the visual shows in the modals' stage
    }
}

@Suppress("FunctionName")
private fun Tag.VisualsModal(handle: ModalsManager.Handle<Boolean>, visual: VisualsScreen.Visual) = comp(
    VisualsModal.Props(handle = handle, visual = visual)
) {
    VisualsModal(it)
}

/**
 * A visual in its modal: the plate under its lamp, the tiles' parts on it, a close key top right after the mouse moved
 * ("Close: Esc"), and the looks at the bottom edge, to wear one right there (the maintainer, 2026-10-29: "going with the
 * mouse to the bottom of the overlay will show a menu with the themes"). It fades in (the maintainer, 2026-10-29: "the
 * overlay should fade in as well ... it appears to sudden").
 */
private class VisualsModal(ctx: Ctx<Props>) : FadingModal<VisualsModal.Props, Boolean>(ctx) {

    data class Props(
        override val handle: ModalsManager.Handle<Boolean>,
        val visual: VisualsScreen.Visual,
    ) : FadingModal.Props<Boolean>() {
        override val transition: Transition = Transition(fadeInTimeMs = 700, fadeOutTimeMs = 400)
    }

    private companion object {
        /** How long the close key stays after the mouse last moved. */
        const val CLOSE_KEY_MS = 2000

        /** The mouse this close to the bottom edge brings up the looks; further up than [THEMES_LEAVE_PX] they go. */
        const val THEMES_EDGE_PX = 80.0
        const val THEMES_LEAVE_PX = 170.0
    }

    /** Whether the close key shows: the mouse moved a moment ago. */
    private var closeKey: Boolean by value(false)

    /** Whether the looks show at the bottom: the mouse came to the bottom edge. */
    private var themes: Boolean by value(false)

    private val shelf by subscribingTo(KlangLookShelf)

    /** Redraws the looks' row when a look is worn. */
    @Suppress("unused")
    private val look by subscribingTo(KlangTheme.look)

    private val tilesState by subscribingTo(VisualsScreen.klangampTiles)

    private var hideTimer: Int? = null

    /** The visual's own background, not the start page's shared one. */
    private val background = ComponentRef.Tracker<MotorBackground>()

    init {
        // Its keys over the page's: Esc closes the visual, not what lies under it
        openKeyLayer()

        lifecycle {
            onMount { lightUp() }
            onUnmount { hideTimer?.let { window.clearTimeout(it) } }
        }
    }

    /** The light on once the background is mounted: as on the start page after the dyno, settled. */
    private fun lightUp(attempt: Int = 0) {
        val bg = background()

        if (bg == null) {
            if (attempt < 50) {
                window.setTimeout({ lightUp(attempt + 1) }, 20)
            }

            return
        }

        bg.powerOn()
        bg.settle()
    }

    private fun mouseMoved(clientY: Double) {
        val fromBottom = window.innerHeight - clientY

        if (fromBottom <= THEMES_EDGE_PX) {
            themes = true
        } else if (fromBottom > THEMES_LEAVE_PX) {
            themes = false
        }

        closeKey = true
        hideTimer?.let { window.clearTimeout(it) }
        hideTimer = window.setTimeout({ closeKey = false }, CLOSE_KEY_MS)
    }

    override fun FlowContent.renderContent() {
        val composition = when (props.visual) {
            VisualsScreen.Visual.KlangampTiles -> tilesState.apply(props.visual.composition())
            else -> props.visual.composition()
        }

        div {
            key = "visuals-screen"
            css {
                // Over the whole window, the background's canvas inside it; the dimmer's centred, unselectable text undone
                put("position", "fixed")
                put("inset", "0")
                put("background", "#000")
                put("text-align", "left")
                put("user-select", "auto")
                put("cursor", if (closeKey) "default" else "none")
            }

            onMouseMove { e -> mouseMoved(clientY = (e.asDynamic().clientY as Number).toDouble()) }

            MotorBackground(ref = background, composition = composition)

            VisualsBoard(
                composition = composition,
                onLeave = { close() },
                lamp = { background()?.lamp },
                // Klang Amp tiles: the Motor button steps through its tiles, its title alone, nothing (the maintainer,
                // 2026-10-29)
                onMotorButton = when (props.visual) {
                    VisualsScreen.Visual.KlangampTiles -> { -> VisualsScreen.klangampTiles(tilesState.next) }
                    else -> { -> close() }
                },
            )

            // The close key: Esc always (it answers while mounted), the pill only after the mouse moved
            button(type = ButtonType.button) {
                attributes["aria-label"] = "Close the visual"
                onClick { close() }

                css {
                    put("position", "absolute")
                    put("top", "16px")
                    put("right", "16px")
                    put("padding", "0")
                    put("border", "none")
                    put("background", "transparent")
                    put("cursor", "pointer")
                    put("opacity", if (closeKey) "1" else "0")
                    put("transition", "opacity 300ms ease")
                }

                KlangCloseKey(onClose = { close() })
            }

            renderThemes()
        }
    }

    /** The equipped looks in a row at the bottom edge; a press wears one, at once, behind it. */
    private fun FlowContent.renderThemes() {
        val chosen = KlangTheme.chosen

        div {
            key = "visuals-themes"
            css {
                put("position", "absolute")
                put("left", "50%")
                put("bottom", "0")
                put("display", "flex")
                put("gap", "8px")
                put("padding", "12px 16px 16px")
                put("background", "color-mix(in srgb, var(--kl-ground-panel) 88%, transparent)")
                put("border", "1px solid var(--kl-edge)")
                put("border-bottom", "none")
                put("border-radius", "6px 6px 0 0")
                put("box-shadow", "0 -8px 24px rgba(0, 0, 0, 0.5)")
                put("transform", if (themes) "translate(-50%, 0)" else "translate(-50%, 110%)")
                put("transition", "transform 220ms ease")
            }

            for (preset in shelf.equipped.filterNotNull()) {
                KlangSwatchButton(
                    name = preset.name,
                    swatch = preset.swatch,
                    pressed = preset == chosen,
                    onClick = {
                        KlangTheme.choose(preset)
                        LookStorage.save(preset)
                    },
                )
            }
        }
    }
}
