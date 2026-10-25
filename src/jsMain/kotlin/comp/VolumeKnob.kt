/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.MasterVolumeScale
import io.peekandpoke.klang.Player
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.onPointerCancel
import io.peekandpoke.ultra.html.onPointerDown
import io.peekandpoke.ultra.html.onPointerMove
import io.peekandpoke.ultra.html.onPointerUp
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.BoxSizing
import kotlinx.css.Color
import kotlinx.css.Cursor
import kotlinx.css.Display
import kotlinx.css.LinearDimension
import kotlinx.css.Margin
import kotlinx.css.PointerEvents
import kotlinx.css.Position
import kotlinx.css.UserSelect
import kotlinx.css.backgroundColor
import kotlinx.css.borderRadius
import kotlinx.css.borderWidth
import kotlinx.css.boxSizing
import kotlinx.css.cursor
import kotlinx.css.display
import kotlinx.css.height
import kotlinx.css.left
import kotlinx.css.margin
import kotlinx.css.pct
import kotlinx.css.pointerEvents
import kotlinx.css.position
import kotlinx.css.px
import kotlinx.css.top
import kotlinx.css.userSelect
import kotlinx.css.width
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.title
import org.w3c.dom.Element
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.math.roundToInt

@Suppress("FunctionName")
fun Tag.VolumeKnob(
    size: LinearDimension,
    motorOnline: Boolean,
    glowColor: Color? = null,
    glowIntensity: Double = 0.5,
) = comp(
    VolumeKnob.Props(size = size, motorOnline = motorOnline, glowColor = glowColor, glowIntensity = glowIntensity)
) {
    VolumeKnob(it)
}

/**
 * The master volume as a turning knob (KLANG-00330): drag up to turn it up, down to turn it down, with a mouse, a
 * finger or a pen (pointer events, captured, so the drag ends cleanly also outside the window). A small LED on its
 * rim shows where it points, over the same 270 degree sweep as the gauges beside it; at the top end it points to
 * full, the song's own level, and it never goes above.
 *
 * The volume lives in [Player.outputVolume], as a gain; the knob derives its position from it on the loudness scale
 * ([MasterVolumeScale]) and turns it from there.
 */
class VolumeKnob(ctx: Ctx<Props>) : Component<VolumeKnob.Props>(ctx) {

    companion object {
        /** The drag, in pixels, that turns the knob from silence to full. */
        const val DRAG_PIXELS_FOR_FULL_TURN = 75.0

        /** The ring's glow against the gauges' glow at the same intensity: a little softer (maintainer, 2026-10-25). */
        const val GLOW_SHARE = 0.75

        /** The LED's brightness at silence; it grows evenly with the knob to full at full (maintainer, 2026-10-25). */
        const val LED_BRIGHTNESS_AT_SILENCE = 0.5

        /** Where the knob points at silence, in degrees from straight up, clockwise. */
        const val ANGLE_SILENCE = -135.0

        /** Where the knob points at full, the song's own level, in degrees from straight up, clockwise. */
        const val ANGLE_FULL = 135.0
    }

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val size: LinearDimension,
        /** The player is ready; before that the LED stays dark, as the gauges do (maintainer, 2026-10-25). */
        val motorOnline: Boolean,
        /** Tint of the ring's glow, as the gauges beside it; null for none. */
        val glowColor: Color?,
        /** Strength of the glow, 0.0..1.0. */
        val glowIntensity: Double,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val outputVolume by subscribingTo(Player.outputVolume)

    /** Where the knob points, derived from the volume. */
    private val knobPosition: Double get() = MasterVolumeScale.positionOf(outputVolume)

    /** Where a drag started: which pointer, its height on the screen and the knob's position then. */
    private data class DragStart(val pointerId: Int, val pointerY: Double, val knobPosition: Double)

    /** The drag that is on, null when none is. */
    private var dragStart: DragStart? = null

    init {
        lifecycle {
            onUnmount {
                // Gone mid-drag: the turn so far is kept and remembered
                stopDrag()
            }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    private fun startDrag(e: PointerEvent) {
        // The main button or one finger only: a right-click opens the context menu, which takes the release, and the
        // drag would then go on under a hovering pointer
        if (!e.isPrimary || e.button.toInt() != 0) {
            return
        }

        dragStart = DragStart(pointerId = e.pointerId, pointerY = e.clientY.toDouble(), knobPosition = knobPosition)
        // Captured: the moves and the release reach the knob also when the pointer leaves it, or the window
        (e.currentTarget as? Element)?.setPointerCapture(e.pointerId)
    }

    private fun drag(e: PointerEvent) {
        val start = dragStart ?: return

        if (e.pointerId != start.pointerId) {
            return
        }

        // No button held any more, and the release never came (the window lost focus mid-drag): the drag is over
        if (e.buttons.toInt() == 0) {
            stopDrag()

            return
        }

        // Up is louder: the screen's y grows downwards
        val turnedTo = start.knobPosition + (start.pointerY - e.clientY) / DRAG_PIXELS_FOR_FULL_TURN

        Player.setOutputVolume(MasterVolumeScale.gainOf(turnedTo))
    }

    private fun stopDrag() {
        if (dragStart == null) {
            return
        }

        dragStart = null
        Player.rememberOutputVolume()
    }

    /** Ends the drag when [e] comes from the pointer that started it. */
    private fun stopDragOf(e: PointerEvent) {
        if (dragStart?.pointerId == e.pointerId) {
            stopDrag()
        }
    }

    private fun tooltip(): String {
        val level = when {
            knobPosition <= 0.0 -> "off"
            else -> "${MasterVolumeScale.dbOf(knobPosition).roundToInt()} dB"
        }

        return "Volume: $level\ndrag up or down to turn"
    }

    override fun VDom.render() {
        val angle = ANGLE_SILENCE + (ANGLE_FULL - ANGLE_SILENCE) * knobPosition
        // Lit once the Motor is online, also at silence, half as bright there as at full
        val lit = props.motorOnline
        val ledBrightness = LED_BRIGHTNESS_AT_SILENCE + (1.0 - LED_BRIGHTNESS_AT_SILENCE) * knobPosition
        // The palette's cyan, as the gauges' icons glow at rest (maintainer, 2026-10-25: the accent blue read too dark)
        val led = Color(KlangTheme.Hex.excellent)

        div {
            css {
                position = Position.relative
                display = Display.inlineBlock
                width = props.size
                height = props.size
                cursor = Cursor.nsResize
                userSelect = UserSelect.none
                // A finger's drag turns the knob instead of scrolling the page
                put("touch-action", "none")
            }

            title = tooltip()

            onPointerDown { e ->
                e.preventDefault()
                startDrag(e)
            }
            onPointerMove { e -> drag(e) }
            onPointerUp { e -> stopDragOf(e) }
            onPointerCancel { e -> stopDragOf(e) }
            // The capture lost without a release reaching the knob; ultra.html has no helper for this one
            consumer.onTagEvent(this, "onlostpointercapture", { e: PointerEvent -> stopDragOf(e) }.asDynamic())

            ui.basic.inverted.white.circular.icon.label.with("gauge-ring") {
                css {
                    boxSizing = BoxSizing.borderBox
                    borderWidth = 1.px
                    props.glowColor?.takeIf { props.glowIntensity > 0.0 }?.let { glow ->
                        val alpha = props.glowIntensity.coerceIn(0.0, 1.0) * GLOW_SHARE
                        put("box-shadow", "0 0 ${props.size * KlangTheme.gaugeGlowBlur} ${glow.withAlpha(alpha)}")
                    }
                    width = props.size
                    height = props.size
                    position = Position.relative
                    margin = Margin(0.px)
                }

                // The turning part: a layer the size of the knob, rotated, with the LED at its top
                div {
                    css {
                        position = Position.absolute
                        top = 0.px
                        left = 0.px
                        width = 100.pct
                        height = 100.pct
                        pointerEvents = PointerEvents.none
                        put("transform", "rotate(${angle}deg)")
                    }

                    div {
                        css {
                            position = Position.absolute
                            left = 50.pct
                            top = 16.pct
                            width = props.size * 0.19
                            height = props.size * 0.19
                            borderRadius = 50.pct
                            put("transform", "translateX(-50%)")
                            backgroundColor = if (lit) led.withAlpha(ledBrightness) else Color.dimGray

                            if (lit) {
                                put("box-shadow", "0 0 ${props.size * 0.15} ${led.withAlpha(ledBrightness)}")
                            }
                        }
                    }
                }
            }
        }
    }
}
