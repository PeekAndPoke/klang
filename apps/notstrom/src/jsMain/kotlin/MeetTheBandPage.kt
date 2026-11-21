/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.notstrom

import io.peekandpoke.klang.viz.liveact.Picture
import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.members.Members
import io.peekandpoke.klang.viz.liveact.meet.MeetLayout
import io.peekandpoke.klang.viz.liveact.meet.MeetTheBand
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.onClick
import kotlinx.browser.window
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.title
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

@Suppress("FunctionName")
fun Tag.MeetTheBandPage(release: String, models: String, samples: Double, onBack: () -> Unit, onFailed: () -> Unit) = comp(
    MeetTheBandPage.Props(release = release, models = models, samples = samples, onBack = onBack, onFailed = onFailed)
) {
    MeetTheBandPage(it)
}

/**
 * The page "Meet the band": the scene of the band round the fence ([MeetTheBand]) over the whole window, and over it
 * the app's own layer. No sound, no test, no fullscreen.
 *
 * What it shows of the member at the front: their name, large and widely spaced as the band's mark sets the band's
 * name, what they play in the band ([playsIn]) and how wild they are, 0 (elegant) to 10 (animal), as ten small marks.
 * The words fade out and in again when another member is chosen. On a wide window they stand at the bottom left, on
 * the dark floor beside the light; on a tall one (a phone) at the bottom, on the floor below the lamp: never over the
 * member, their shadow's head or the lamp.
 *
 * Turning the carousel: a large quiet arrow at the left and the right edge, the arrow keys, a swipe on a touch screen
 * (to the left: the next member, who comes in from the right), and six small marks for the six, each a button
 * straight to that member. The way back: a quiet "Back" at the top left, or the Escape key ([Props.onBack]).
 *
 * While the figures load, the app's loading view covers the scene; where they cannot be loaded, [Props.onFailed] is
 * told. The keys, the swipes and the window's size are listened to while the page is mounted, and no longer.
 */
class MeetTheBandPage(ctx: Ctx<Props>) : Component<MeetTheBandPage.Props>(ctx) {

    /**
     * [release]: the figures' release; [models]: where they lie; [samples]: the samples drawn for each pixel;
     * [onBack]: the way back was asked for; [onFailed]: the figures could not be loaded.
     */
    data class Props(
        val release: String,
        val models: String,
        val samples: Double,
        val onBack: () -> Unit,
        val onFailed: () -> Unit,
    )

    /** Who stands at the front, or is on the way there. */
    private var selected: Player by value(MeetLayout.ORDER.first())

    /** Whose words are shown, and whether they are seen (they fade out, change, and fade in again). */
    private var shown: Player by value(MeetLayout.ORDER.first())
    private var seen: Boolean by value(true)

    /** The timer that changes the words once they faded out, while it runs. */
    private var fadeTimer: Int? = null

    /** How far the figures have loaded; null before the first word of it. */
    private var figures: Picture.Figures? by value(null)

    /** Whether the window is wider than high: the words at the bottom left, else at the bottom. */
    private var wide: Boolean by value(isWide())

    /** Where a finger touched the screen, while it is on it. */
    private var touchX = Double.NaN
    private var touchY = Double.NaN

    private val onKey: (Event) -> Unit = { event ->
        val key = (event as? KeyboardEvent)?.takeUnless { it.ctrlKey || it.metaKey || it.altKey }?.key

        when (key) {
            "ArrowRight" -> choose(MeetLayout.next(selected))
            "ArrowLeft" -> choose(MeetLayout.previous(selected))
            "Escape" -> props.onBack()
            else -> Unit
        }
    }

    private val onResize: (Event) -> Unit = { wide = isWide() }

    private val onTouchStart: (Event) -> Unit = { event ->
        val touch = event.asDynamic().changedTouches?.item(0)

        touchX = (touch?.clientX as? Number)?.toDouble() ?: Double.NaN
        touchY = (touch?.clientY as? Number)?.toDouble() ?: Double.NaN
    }

    private val onTouchEnd: (Event) -> Unit = { event ->
        val touch = event.asDynamic().changedTouches?.item(0)
        val x = (touch?.clientX as? Number)?.toDouble() ?: Double.NaN
        val y = (touch?.clientY as? Number)?.toDouble() ?: Double.NaN

        when (swipeStep(dx = x - touchX, dy = y - touchY)) {
            1 -> choose(MeetLayout.next(selected))
            -1 -> choose(MeetLayout.previous(selected))
            else -> Unit
        }

        touchX = Double.NaN
        touchY = Double.NaN
    }

    init {
        lifecycle {
            onMount {
                window.addEventListener("keydown", onKey)
                window.addEventListener("resize", onResize)
                window.addEventListener("touchstart", onTouchStart)
                window.addEventListener("touchend", onTouchEnd)
            }

            onUnmount {
                window.removeEventListener("keydown", onKey)
                window.removeEventListener("resize", onResize)
                window.removeEventListener("touchstart", onTouchStart)
                window.removeEventListener("touchend", onTouchEnd)
                fadeTimer?.let { window.clearTimeout(it) }
                fadeTimer = null
            }
        }
    }

    /** [player] to the front: the carousel turns to them, and the words fade over to theirs. */
    private fun choose(player: Player) {
        if (player == selected) {
            return
        }

        selected = player
        seen = false
        fadeTimer?.let { window.clearTimeout(it) }
        fadeTimer = window.setTimeout({
            fadeTimer = null
            shown = selected
            seen = true
        }, FADE_MS)
    }

    override fun VDom.render() {
        val loading = figures?.takeUnless { it.done }

        div {
            css {
                put("position", "absolute")
                put("inset", "0")
                put("overflow", "hidden")
                put("font-family", PAGE_FONTS)
                // the page's own swipes: the browser does not scroll or zoom on them
                put("touch-action", "none")
            }

            MeetTheBand(
                models = props.models,
                version = props.release,
                selected = selected,
                samples = props.samples,
                onFigures = { count, of -> figures = Picture.Figures(arrived = count, of = of, failed = false) },
                onFailed = { props.onFailed() },
            )

            if (loading != null || figures == null) {
                notstromScreen {
                    figuresLoading(loading ?: Picture.Figures(arrived = 0, of = Player.entries.size, failed = false))
                }
            } else {
                member()
                arrows()
            }

            // the way back, over everything, also while the figures load
            div {
                css {
                    put("position", "absolute")
                    put("top", "16px")
                    put("left", "16px")
                    put("z-index", "20")
                }

                plainButton(text = "Back", primary = false) { props.onBack() }
            }
        }
    }

    /** The member at the front: name, instrument and wildness, and under them the six marks to choose by. */
    private fun FlowContent.member() {
        val sheet = Members.of(shown)

        div {
            css {
                put("position", "absolute")
                put("z-index", "15")
                put("display", "flex")
                put("flex-direction", "column")
                put("pointer-events", "none")

                if (wide) {
                    // low, on the dark floor beside the light: under the feet of the member who stands at the left
                    put("left", "max(88px, 7vw)")
                    put("bottom", "max(10px, 2.5vh)")
                    put("align-items", "flex-start")
                } else {
                    put("left", "0")
                    put("right", "0")
                    put("bottom", "max(4px, 1vh)")
                    put("align-items", "center")
                }
            }

            div {
                css {
                    put("display", "flex")
                    put("flex-direction", "column")
                    put("align-items", if (wide) "flex-start" else "center")
                    put("opacity", if (seen) "1" else "0")
                    put("transition", "opacity ${FADE_MS}ms ease")
                }

                div {
                    css {
                        put("font-size", if (wide) "clamp(30px, 3.4vw, 68px)" else "clamp(24px, 7.5vw, 40px)")
                        put("font-weight", "700")
                        put("letter-spacing", "0.18em")
                        // the last letter's spacing taken back, so the name's ink ends where it seems to
                        put("margin-right", "-0.18em")
                        put("line-height", "1")
                        put("color", "#e8ecf4")
                    }
                    +shown.name.uppercase()
                }

                div {
                    css {
                        put("margin-top", if (wide) "0.7em" else "0.45em")
                        put("font-size", if (wide) "clamp(15px, 1.1vw, 22px)" else "16px")
                        put("letter-spacing", "0.06em")
                        put("color", "#c8d0de")
                    }
                    +playsIn(shown)
                }

                wildness(sheet.character.wildness)
            }

            choices()
        }
    }

    /** How wild the member is, [of10] from 0 to 10: the word and ten small marks, so many of them lit. */
    private fun FlowContent.wildness(of10: Int) {
        div {
            css {
                put("margin-top", if (wide) "14px" else "10px")
                put("display", "flex")
                put("align-items", "center")
                put("gap", "12px")
                put("font-size", "13px")
                put("letter-spacing", "0.08em")
                put("color", "#8a93a8")
            }
            title = "$WILDNESS $of10 of 10: 0 is elegant, 10 an animal"

            div { +WILDNESS }

            div {
                css {
                    put("display", "flex")
                    put("gap", "4px")
                }

                for (i in 0 until 10) {
                    div {
                        css {
                            put("width", "9px")
                            put("height", "9px")
                            put("border-radius", "2px")
                            put("background", if (i < of10) "#d9a35e" else "#262c3b")
                        }
                    }
                }
            }
        }
    }

    /** The six marks for the six members, round the hexagon's order: the one at the front lit; each goes to its member. */
    private fun FlowContent.choices() {
        div {
            css {
                put("margin-top", if (wide) "14px" else "4px")
                // the buttons are as large as a finger, the dots in them small: their first dot under the words' edge
                put("margin-left", if (wide) "-17px" else "0")
                put("display", "flex")
                put("pointer-events", "auto")
            }

            for (player in MeetLayout.ORDER) {
                button {
                    css {
                        put("width", "44px")
                        put("height", "44px")
                        put("padding", "0")
                        put("border", "0")
                        put("background", "transparent")
                        put("cursor", "pointer")
                        put("display", "flex")
                        put("align-items", "center")
                        put("justify-content", "center")
                    }
                    title = player.name
                    attributes["aria-label"] = player.name
                    onClick { choose(player) }

                    div {
                        css {
                            put("width", "10px")
                            put("height", "10px")
                            put("border-radius", "50%")
                            put("background", if (player == selected) "#e8ecf4" else "transparent")
                            put("border", "1px solid ${if (player == selected) "#e8ecf4" else "#6b7385"}")
                            put("transition", "background ${FADE_MS}ms ease")
                        }
                    }
                }
            }
        }
    }

    /** The two large quiet arrows at the window's edges: the member before (left) and after (right). */
    private fun FlowContent.arrows() {
        arrow(left = true) { choose(MeetLayout.previous(selected)) }
        arrow(left = false) { choose(MeetLayout.next(selected)) }
    }

    private fun FlowContent.arrow(left: Boolean, onClick: () -> Unit) {
        button {
            css {
                put("position", "absolute")
                put("z-index", "15")
                put("top", "50%")
                put(if (left) "left" else "right", "max(12px, 2vw)")
                put("transform", "translateY(-50%)")
                put("width", "60px")
                put("height", "60px")
                put("padding", "0 0 4px 0")
                put("border-radius", "50%")
                put("border", "1px solid rgba(107, 115, 133, 0.6)")
                put("background", "rgba(2, 3, 8, 0.35)")
                put("color", "#c8d0de")
                put("font", "inherit")
                put("font-size", "34px")
                put("line-height", "1")
                put("cursor", "pointer")
            }
            title = if (left) "The member before" else "The next member"
            attributes["aria-label"] = if (left) "The member before" else "The next member"
            onClick { onClick() }

            +(if (left) "‹" else "›")
        }
    }

    private companion object {
        /** How long the words take to fade out, and in again, in milliseconds. */
        const val FADE_MS = 280

        /** Whether the window is wider than high. */
        fun isWide(): Boolean = window.innerWidth >= window.innerHeight
    }
}
