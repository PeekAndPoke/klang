/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.storage.LookStorage
import io.peekandpoke.klang.ui.comp.KlangCloseKey
import io.peekandpoke.klang.ui.comp.KlangSwatchButton
import io.peekandpoke.klang.ui.feel.KlangLookShelf
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.ComponentRef
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.html.onMouseMove
import io.peekandpoke.ultra.streams.StreamSource
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
 * A visual over the whole window (the maintainer, 2026-10-29: "Add a tab in the bottom toolbar called "visuals" and add
 * one link card. This one will open a fullscreen overlay with the background of the startpage, no gauges, no popup. Esc
 * leaves the screen. Moving the mouse shows a close button top right with text "Close: Esc""): the start page's plate
 * and its swaying lamp, lit, and the Motor laid out under the title ([VisualsBoard]), whose menu button leaves. Mounted once at the app's root;
 * [open] shows it. The mouse at the bottom
 * edge brings up the looks, to wear one right there (the maintainer, 2026-10-29: "going with the mouse to the bottom of
 * the overlay will show a menu with the themes").
 */
class VisualsScreen(ctx: NoProps) : PureComponent(ctx) {

    companion object {
        /** Whether the visual is shown. */
        val open = StreamSource(false)

        /** How long the close key stays after the mouse last moved. */
        private const val CLOSE_KEY_MS = 2000

        /** The mouse this close to the bottom edge brings up the looks; further up than [THEMES_LEAVE_PX] they go. */
        private const val THEMES_EDGE_PX = 80.0
        private const val THEMES_LEAVE_PX = 170.0
    }

    private val shown by subscribingTo(open) { if (it) lightUp() }

    /** Whether the close key shows: the mouse moved a moment ago. */
    private var closeKey: Boolean by value(false)

    /** Whether the looks show at the bottom: the mouse came to the bottom edge. */
    private var themes: Boolean by value(false)

    private val shelf by subscribingTo(KlangLookShelf)

    /** Redraws the looks' row when a look is worn. */
    @Suppress("unused")
    private val look by subscribingTo(KlangTheme.look)

    private var hideTimer: Int? = null

    /** The visual's own background, not the start page's shared one. */
    private val background = ComponentRef.Tracker<MotorBackground>()

    init {
        lifecycle {
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

    private fun close() {
        open(false)
        closeKey = false
        themes = false
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

    override fun VDom.render() {
        if (!shown) {
            return
        }

        div {
            key = "visuals-screen"
            css {
                // Over everything: its own layer, the background's canvas inside it
                put("position", "fixed")
                put("inset", "0")
                put("z-index", "2000")
                put("background", "#000")
                put("cursor", if (closeKey) "default" else "none")
            }

            onMouseMove { e -> mouseMoved(clientY = (e.asDynamic().clientY as Number).toDouble()) }

            MotorBackground(ref = background, boardPlate = true)

            // The Motor laid out on the plate: the gauges on the tiles, the scope in a hole, the spectrum on a screen
            // (the maintainer, 2026-10-29); its menu button leaves the visual
            VisualsBoard(onLeave = { close() }, background = { background() })

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
