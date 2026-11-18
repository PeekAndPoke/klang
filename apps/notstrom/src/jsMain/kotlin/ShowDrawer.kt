/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.notstrom

import io.peekandpoke.klang.viz.liveact.Picture
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.html.onInput
import io.peekandpoke.ultra.html.onKeyDown
import io.peekandpoke.ultra.html.onKeyUp
import io.peekandpoke.ultra.html.onMouseEnter
import io.peekandpoke.ultra.html.onMouseLeave
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.InputType
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.input
import kotlinx.html.label
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import kotlin.math.roundToInt

@Suppress("FunctionName")
fun Tag.ShowDrawer(picture: Picture, release: String, onVolume: (Double) -> Unit) = comp(
    ShowDrawer.Props(picture = picture, release = release, onVolume = onVolume)
) {
    ShowDrawer(it)
}

/**
 * The drawer at the bottom of the window while the show plays: only a moving mouse brings it, and it goes again
 * [HIDE_MS] after the last move (while the pointer rests on it, it stays: a visitor reading it does not move). The
 * mouse pointer hides with it. It holds a button for fullscreen, whose label follows the page's real state (also when
 * fullscreen was entered by the choice before the show, or left with Esc); the live act's oversampling, its steps
 * with the one in use marked, following the real state (the test's choice, the key o, the live act's own step down),
 * where a choice is the visitor's and holds ([Props.picture]), and beside it the frames a second the live act counts
 * (read once a second while the drawer is shown, nothing while it is hidden); and the volume, 0 to 100 percent, full
 * at first and not kept ([Props.onVolume] is told a gain from 0 to 1). At its end, small and quiet, the [Props.release].
 *
 * The live act listens for clicks and keys on the whole window: what is clicked or typed in the drawer goes no
 * further while it is shown, so it reaches the show neither as a pause nor as a camera key. When it hides it gives the
 * keyboard's focus back, so no hidden control takes the keys (an arrow on the volume, Space on fullscreen) and the
 * live act's keys work again.
 *
 * No pause: a cyclic playback cannot hold its sound and its time and take both up again (KLANG-00465).
 */
class ShowDrawer(ctx: Ctx<Props>) : Component<ShowDrawer.Props>(ctx) {

    data class Props(val picture: Picture, val release: String, val onVolume: (Double) -> Unit)

    private var shown: Boolean by value(false)

    private var fullscreen: Boolean by value(isFullscreen())

    /** The volume, in percent. */
    private var volume: Int by value(100)

    /** The samples drawn for each pixel now, as the live act tells. */
    private val samples: Int by subscribingTo(props.picture.samples)

    /** The frames a second, as last read (while the drawer is shown). */
    private var fps: Int by value(0)

    /** Reads the frame rate once a second while the drawer is shown. */
    private var fpsTimer: Int? = null

    private var hovered = false
    private var hideTimer: Int? = null

    private val onMove: (Event) -> Unit = { show() }

    private val onFullscreenChange: (Event) -> Unit = { fullscreen = isFullscreen() }

    init {
        lifecycle {
            onMount {
                window.addEventListener("mousemove", onMove)
                document.addEventListener("fullscreenchange", onFullscreenChange)
                pointer()
            }

            onUnmount {
                window.removeEventListener("mousemove", onMove)
                document.removeEventListener("fullscreenchange", onFullscreenChange)
                hideTimer?.let { window.clearTimeout(it) }
                hideTimer = null
                fpsTimer?.let { window.clearInterval(it) }
                fpsTimer = null
                document.documentElement?.asDynamic()?.style?.cursor = ""
            }
        }
    }

    /** Shows the drawer, and hides it again after [HIDE_MS] unless the pointer rests on it then. */
    private fun show() {
        if (!shown) {
            shown = true
            pointer()
            readFps()
            fpsTimer = window.setInterval({ readFps() }, FPS_MS)
        }

        hideTimer?.let { window.clearTimeout(it) }
        hideTimer = window.setTimeout({
            hideTimer = null

            if (!hovered) {
                shown = false
                pointer()
                letGoOfFocus()
                fpsTimer?.let { window.clearInterval(it) }
                fpsTimer = null
            }
        }, HIDE_MS)
    }

    /** The keyboard's focus leaves the drawer, where one of its controls had it. */
    private fun letGoOfFocus() {
        val drawer = dom ?: return
        val focused = document.activeElement as? HTMLElement ?: return

        if (drawer.contains(focused)) {
            focused.blur()
        }
    }

    /** The frame rate the live act counted last, in whole frames. */
    private fun readFps() {
        val now = props.picture.fps()

        // NaN-guard: a rate that is no number shows as none
        fps = if (now == now) now.roundToInt() else 0
    }

    /** The mouse pointer is shown with the drawer, and hidden over the stage without it. */
    private fun pointer() {
        document.documentElement?.asDynamic()?.style?.cursor = if (shown) "" else "none"
    }

    override fun VDom.render() {
        div {
            css {
                put("position", "fixed")
                put("left", "0")
                put("right", "0")
                put("bottom", "0")
                put("z-index", "20")
                put("display", "flex")
                put("justify-content", "center")
                put("align-items", "center")
                put("gap", "24px")
                put("padding", "12px 16px")
                put("background", "rgba(2, 3, 8, 0.82)")
                put("border-top", "1px solid #1b2030")
                put("color", "#c8cfdc")
                put("font-size", "15px")
                put("opacity", if (shown) "1" else "0")
                put("transform", if (shown) "translateY(0)" else "translateY(100%)")
                // hidden, it catches no click meant for the stage
                put("pointer-events", if (shown) "auto" else "none")
                // hidden, its controls cannot be reached by Tab either (after the slide out)
                put("visibility", if (shown) "visible" else "hidden")
                put("transition", if (shown) "opacity 0.3s, transform 0.3s" else "opacity 0.3s, transform 0.3s, visibility 0s 0.3s")
            }

            // what happens in the drawer stays in it: the live act's window listeners hear none of it
            onClick { it.stopPropagation() }
            onKeyDown {
                if (shown) {
                    it.stopPropagation()
                }
            }
            onKeyUp {
                if (shown) {
                    it.stopPropagation()
                }
            }

            onMouseEnter { hovered = true }
            onMouseLeave {
                hovered = false
                show()
            }

            plainButton(text = if (fullscreen) "Leave fullscreen" else "Enter fullscreen", primary = false) {
                if (fullscreen) {
                    leaveFullscreen()
                } else {
                    enterFullscreen()
                }
            }

            div {
                css {
                    put("display", "flex")
                    put("align-items", "center")
                    put("gap", "8px")
                }

                +"Oversampling"

                for (step in Picture.STEPS) {
                    plainButton(text = "${step}x", primary = step == samples) { props.picture.choose(step) }
                }

                div {
                    css {
                        put("width", "4.5em")
                        put("color", "#8a93a8")
                        put("font-variant-numeric", "tabular-nums")
                        put("white-space", "nowrap")
                    }
                    +"$fps fps"
                }
            }

            label {
                css {
                    put("display", "flex")
                    put("align-items", "center")
                    put("gap", "10px")
                }

                +"Volume"

                input(type = InputType.range) {
                    attributes["min"] = "0"
                    attributes["max"] = "100"
                    attributes["step"] = "1"
                    value = "$volume"
                    css {
                        put("width", "160px")
                    }
                    onInput { event ->
                        val percent = (event.target as? HTMLInputElement)?.value?.toIntOrNull()?.coerceIn(0, 100)

                        if (percent != null) {
                            volume = percent
                            props.onVolume(percent / 100.0)
                        }
                    }
                }

                div {
                    css {
                        put("width", "3.5em")
                        put("white-space", "nowrap")
                        put("font-variant-numeric", "tabular-nums")
                    }
                    +"$volume %"
                }
            }

            div {
                css {
                    put("margin-left", "12px")
                    put("color", "#6b7385")
                    put("font-size", "13px")
                    put("white-space", "nowrap")
                }
                +props.release
            }
        }
    }

    private companion object {
        /** How long the drawer stays after the mouse last moved, in milliseconds. */
        const val HIDE_MS = 3000

        /** How often the frame rate is read while the drawer is shown, in milliseconds. */
        const val FPS_MS = 1000
    }
}
