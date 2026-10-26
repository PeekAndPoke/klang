/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import kotlinx.browser.window
import kotlinx.css.Align
import kotlinx.css.Display
import kotlinx.css.FontWeight
import kotlinx.css.JustifyContent
import kotlinx.css.LinearDimension
import kotlinx.css.Overflow
import kotlinx.css.Position
import kotlinx.css.alignItems
import kotlinx.css.color
import kotlinx.css.display
import kotlinx.css.fontSize
import kotlinx.css.fontWeight
import kotlinx.css.height
import kotlinx.css.justifyContent
import kotlinx.css.overflow
import kotlinx.css.position
import kotlinx.css.px
import kotlinx.css.width
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.span

@Suppress("FunctionName")
fun Tag.LcdDisplay(
    value: Int,
    digits: Int = 4,
    // 20px keeps the 0.9em digit cells at exactly 18px — integer pixel grid,
    // so the rolling strip never sits on half-pixels (which renders blurry).
    size: LinearDimension = 20.px,
    dim: Boolean = false,
) = comp(
    LcdDisplay.Props(
        value = value,
        digits = digits,
        size = size,
        dim = dim,
    )
) {
    LcdDisplay(it)
}

class LcdDisplay(ctx: Ctx<Props>) : Component<LcdDisplay.Props>(ctx) {

    data class Props(
        val value: Int,
        val digits: Int,
        val size: LinearDimension,
        val dim: Boolean,
    )

    //  Strip layout: 0-9 repeated 3 times (30 entries).
    //  Home is the middle set (positions 10-19).
    //  Rolling up past 9 goes into the third set, rolling down past 0 into the first.
    //  After animation, snap back to the middle set.

    private var positions = IntArray(props.digits) { toDigits(props.value, props.digits)[it] + MIDDLE }
    private var noTransition = BooleanArray(props.digits)
    private var prevDigits = toDigits(props.value, props.digits).toIntArray()
    private val snapTimers = IntArray(props.digits)

    // Incrementing forces a re-render for snap-backs (which don't change props)
    @Suppress("unused")
    private var renderTick by value(0)

    init {
        lifecycle {
            onUnmount { cancelAllTimers() }
        }
    }

    override fun VDom.render() {
        val curDigits = toDigits(props.value, props.digits)

        // Handle digit count change
        if (curDigits.size != prevDigits.size) {
            cancelAllTimers()
            positions = IntArray(curDigits.size) { MIDDLE + curDigits[it] }
            noTransition = BooleanArray(curDigits.size)
            prevDigits = curDigits.toIntArray()
        }

        // Detect per-digit changes and update positions
        for (i in curDigits.indices) {
            if (curDigits[i] != prevDigits[i]) {
                noTransition[i] = false

                if (snapTimers[i] != 0) {
                    window.clearTimeout(snapTimers[i])
                    snapTimers[i] = 0
                }

                val curDigit = positions[i] % 10
                val newDigit = curDigits[i]

                // Shortest path around the 0-9 wheel
                val forwardDist = (newDigit - curDigit + 10) % 10
                val backwardDist = (curDigit - newDigit + 10) % 10

                positions[i] = if (forwardDist <= backwardDist) {
                    positions[i] + forwardDist   // rotate up (e.g. 3→5, 9→0)
                } else {
                    positions[i] - backwardDist  // rotate down (e.g. 5→3, 0→9)
                }

                // Safety clamp to strip bounds
                if (positions[i] >= STRIP_SIZE || positions[i] < 0) {
                    positions[i] = MIDDLE + newDigit
                }

                if (positions[i] < MIDDLE || positions[i] >= MIDDLE + 10) {
                    scheduleSnapBack(i)
                }

                prevDigits[i] = curDigits[i]
            }
        }

        // Render: in the look's LCD (KLANG-00349), as the value knob's LCD beside it: its digits in the LCD's ink, a
        // sunken well in the look's ground, a ghost "8" behind each digit
        val ink = "var(--kl-lcd-ink)"
        val ghost = "color-mix(in srgb, var(--kl-lcd-ink) 13%, transparent)"
        // The value knob's height, so the two LCDs stand level in the transport bar (border included via borderBox)
        val compHeight = 34.px

        div {
            css {
                // Block-level flex — inline-flex sat on the line-box baseline,
                // which shifted the LCD down a few px next to the buttons.
                display = Display.flex
                alignItems = Align.center
                put("background", "var(--kl-ground-sunken)")
                put("box-sizing", "border-box")
                put("border", "1px solid var(--kl-edge)")
                put("border-radius", "var(--kl-radius)")
                put("box-shadow", "inset 0 2px 4px rgba(0,0,0,0.6), 0 1px 0 rgba(255,255,255,0.06)")
                height = compHeight
                put("padding", "0 6px")
                put("gap", "1px")
                put("font-family", "'Courier New', 'Consolas', monospace")
                fontWeight = FontWeight.normal
                fontSize = props.size
                if (props.dim) {
                    put("filter", "brightness(0.7)")
                }
            }

            for (i in 0 until props.digits) {
                val pos = positions[i]
                val skipTrans = noTransition[i]

                div {
                    css {
                        this.position = Position.relative
                        width = LinearDimension("0.7em")
                        height = LinearDimension("${ENTRY_EM}em")
                        overflow = Overflow.hidden
                    }

                    // Ghost "8"
                    span {
                        css {
                            this.position = Position.absolute
                            display = Display.flex
                            width = LinearDimension("100%")
                            height = LinearDimension("100%")
                            alignItems = Align.center
                            justifyContent = JustifyContent.center
                            put("color", ghost)
                            // Optical centering — Courier digits sit high otherwise (2px by eye)
                            put("transform", "translateY(2px)")
                        }
                        +"8"
                    }

                    // Rolling digit strip: 0-9 × 3 sets, em-sized entries
                    div {
                        css {
                            this.position = Position.absolute
                            width = LinearDimension("100%")
                            height = LinearDimension("${ENTRY_EM * STRIP_SIZE}em")
                            put("top", "50%")
                            if (!skipTrans) {
                                put("transition", "transform 0.4s ease-out")
                            } else {
                                put("transition", "none")
                            }
                            // The +2px matches the ghost "8" optical-centering nudge
                            put("transform", "translateY(calc(-${(pos + 0.5) * ENTRY_EM}em + 2px))")
                        }

                        for (d in 0 until STRIP_SIZE) {
                            div {
                                css {
                                    height = LinearDimension("${ENTRY_EM}em")
                                    display = Display.flex
                                    alignItems = Align.center
                                    justifyContent = JustifyContent.center
                                    put("color", ink)
                                    put("text-shadow", "0 0 3px color-mix(in srgb, var(--kl-lcd-ink) 40%, transparent)")
                                }
                                +"${d % 10}"
                            }
                        }
                    }
                }
            }
        }
    }

    private fun scheduleSnapBack(index: Int) {
        snapTimers[index] = window.setTimeout({
            val homePos = MIDDLE + positions[index] % 10
            if (positions[index] != homePos) {
                noTransition[index] = true
                positions[index] = homePos
                renderTick++
            }
            snapTimers[index] = 0
        }, 500)
    }

    private fun cancelAllTimers() {
        for (i in snapTimers.indices) {
            if (snapTimers[i] != 0) {
                window.clearTimeout(snapTimers[i])
                snapTimers[i] = 0
            }
        }
    }

    companion object {
        private const val STRIP_SIZE = 30
        private const val MIDDLE = 10
        private const val ENTRY_EM = 0.9

        private fun toDigits(value: Int, count: Int): List<Int> =
            value.toString().takeLast(count).padStart(count, '0').map { it.digitToInt() }
    }
}
