/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.peekandpoke.klang.ui.svgRoot
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.kraft.vdom.custom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.onKeyDown
import io.peekandpoke.ultra.html.onPointerCancel
import io.peekandpoke.ultra.html.onPointerDown
import io.peekandpoke.ultra.html.onPointerMove
import io.peekandpoke.ultra.html.onPointerUp
import io.peekandpoke.ultra.semanticui.SemanticIconFn
import io.peekandpoke.ultra.semanticui.icon
import kotlinx.css.LinearDimension
import kotlinx.css.fontSize
import kotlinx.css.height
import kotlinx.css.px
import kotlinx.css.width
import kotlinx.html.DIV
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.span
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.math.roundToInt

@Suppress("FunctionName")
fun Tag.KlangKnob(
    name: String,
    value: Double,
    onChange: (Double) -> Unit,
    onChangeEnd: (() -> Unit)? = null,
    size: LinearDimension = 56.px,
    finish: KlangFinish = KlangFinish.Dark,
    label: String? = null,
    labelPosition: KlangKnob.LabelPosition = KlangKnob.LabelPosition.Below,
    valueText: String? = null,
    spokenValue: String? = null,
    icon: SemanticIconFn? = null,
    ring: Boolean = true,
    step: Double = 0.05,
    dragPixels: Double = KlangKnob.DRAG_PIXELS_FOR_FULL_TURN,
) = comp(
    KlangKnob.Props(
        name = name,
        value = value,
        onChange = onChange,
        onChangeEnd = onChangeEnd,
        size = size,
        finish = finish,
        label = label,
        labelPosition = labelPosition,
        valueText = valueText,
        spokenValue = spokenValue,
        icon = icon,
        ring = ring,
        step = step,
        dragPixels = dragPixels,
    )
) {
    KlangKnob(it)
}

/**
 * A turning knob (KLANG-00338): a domed cap, dark or silver, with a pointer, or an icon on the cap and the pointer as
 * a dot at its rim; around it an optional ring that shows how far it is turned; a label on top or below, and the value
 * below the dial (right under the label when that is below too).
 *
 * It turns a position from 0 to 1 and knows no units: the caller maps the position to its value and says it in
 * [Props.valueText]. Dragging up turns it up, with a mouse, a finger or a pen; a focused knob takes the arrow keys,
 * Home and End, and is announced as a `slider`. Today's `VolumeKnob` drags the same way (it becomes a preset of this
 * knob when the Motor switches over). Canvas section "Knobs".
 */
class KlangKnob(ctx: Ctx<Props>) : Component<KlangKnob.Props>(ctx) {

    companion object {
        /** The drag, in pixels, from 0 to 1 (as the volume knob's). */
        const val DRAG_PIXELS_FOR_FULL_TURN = 75.0

        /** How far the cap sits inside the dial when the ring is shown. */
        const val CAP_INSET_WITH_RING_PCT = 14
    }

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        /** What the knob turns, for screen readers ("Cutoff"). */
        val name: String,
        /** The position, 0 to 1. */
        val value: Double,
        /** A new position, 0 to 1, on every step of a drag or a key. */
        val onChange: (Double) -> Unit,
        /** A drag or a key press is over: the place to remember the value. */
        val onChangeEnd: (() -> Unit)?,
        val size: LinearDimension,
        val finish: KlangFinish,
        val label: String?,
        val labelPosition: LabelPosition,
        val valueText: String?,
        /** The value as screen readers say it, when no [valueText] is printed ("37.20 RPM"); else they hear a percent. */
        val spokenValue: String?,
        val icon: SemanticIconFn?,
        val ring: Boolean,
        /** How far one arrow key turns it. */
        val step: Double,
        /**
         * How far a drag goes for the whole way, in pixels: more is finer (the maintainer, 2026-10-26: the RPM knob's
         * sensitivity "should be pretty low ... you have to move the mouse very far to change the values").
         */
        val dragPixels: Double,
    )

    enum class LabelPosition {
        Above,
        Below,
    }

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    /** Where a drag started: which pointer and where on the screen. */
    private data class DragStart(val pointerId: Int, val pointerX: Double, val pointerY: Double)

    /** Where this knob's cap cuts its grain from, so no two look alike; kept while it lives. */
    private val grain = KlangControlStyles.grainOffset()

    private var dragStart: DragStart? = null

    /**
     * The position a drag turns from, read at its first move, not at the press: a press that ends something else
     * first (a typed value taken as its field loses the focus) has the new value drawn by then.
     */
    private var dragBase: Double? = null

    init {
        lifecycle {
            onUnmount {
                stopDrag()
            }
        }
    }

    //  INPUT  //////////////////////////////////////////////////////////////////////////////////////////////////

    private fun startDrag(e: PointerEvent) {
        // The main button or one finger only
        if (!e.isPrimary || e.button.toInt() != 0) {
            return
        }

        e.preventDefault()
        (e.currentTarget as? HTMLElement)?.focus()

        dragStart = DragStart(
            pointerId = e.pointerId,
            pointerX = e.clientX.toDouble(),
            pointerY = e.clientY.toDouble(),
        )
        dragBase = null

        // The moves and the release reach the knob also outside it; a pointer that is already gone cannot be captured
        runCatching { (e.currentTarget as? Element)?.setPointerCapture(e.pointerId) }
    }

    private fun drag(e: PointerEvent) {
        val start = dragStart ?: return

        if (e.pointerId != start.pointerId) {
            return
        }

        // No button held any more, and the release never came: the drag is over
        if (e.buttons.toInt() == 0) {
            stopDrag()

            return
        }

        // Up or right turns it up (the screen's y grows downwards); sideways too, for a knob at the screen's top edge,
        // where there is no room above it (the maintainer, 2026-10-26)
        val moved = (start.pointerY - e.clientY) + (e.clientX - start.pointerX)

        // NaN-guard: a distance that is not a positive number counts as the default
        val pixels = props.dragPixels.takeIf { it > 0.0 } ?: DRAG_PIXELS_FOR_FULL_TURN

        val base = dragBase ?: Dial.clamp(props.value).also { dragBase = it }

        props.onChange(Dial.clamp(base + moved / pixels))
    }

    private fun stopDrag() {
        if (dragStart == null) {
            return
        }

        dragStart = null
        props.onChangeEnd?.invoke()
    }

    private fun stopDragOf(e: PointerEvent) {
        if (dragStart?.pointerId == e.pointerId) {
            stopDrag()
        }
    }

    private fun key(e: KeyboardEvent) {
        val position = Dial.clamp(props.value)

        val next = when (e.key) {
            "ArrowUp", "ArrowRight" -> position + props.step
            "ArrowDown", "ArrowLeft" -> position - props.step
            "Home" -> 0.0
            "End" -> 1.0
            else -> return
        }

        e.preventDefault()
        props.onChange(Dial.clamp(next))
        props.onChangeEnd?.invoke()
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        val styles = KlangControlStyles
        val label = props.label

        val labelBelow = label != null && props.labelPosition == LabelPosition.Below
        val valueText = props.valueText

        div(classes = styles.knob.toString()) {
            if (label != null && props.labelPosition == LabelPosition.Above) {
                span(classes = styles.knobLabel.toString()) { +label }
            }

            renderDial()

            // Under the dial: the label when it goes below, then the value, as one tight caption
            if (labelBelow || valueText != null) {
                div(classes = styles.knobCaption.toString()) {
                    if (labelBelow && label != null) {
                        span(classes = styles.knobLabel.toString()) { +label }
                    }

                    if (valueText != null) {
                        span(classes = styles.knobValue.toString()) { +valueText }
                    }
                }
            }
        }
    }

    private fun DIV.renderDial() {
        val styles = KlangControlStyles
        val position = Dial.clamp(props.value)
        val silver = props.finish == KlangFinish.Silver

        div(classes = styles.knobDial.toString()) {
            attributes["role"] = "slider"
            attributes["tabindex"] = "0"
            attributes["aria-label"] = props.name
            attributes["aria-valuemin"] = "0"
            attributes["aria-valuemax"] = "100"
            attributes["aria-valuenow"] = (position * 100).roundToInt().toString()
            (props.valueText ?: props.spokenValue)?.let { attributes["aria-valuetext"] = it }

            css {
                width = props.size
                height = props.size
            }

            onPointerDown { e -> startDrag(e) }
            onPointerMove { e -> drag(e) }
            onPointerUp { e -> stopDragOf(e) }
            onPointerCancel { e -> stopDragOf(e) }
            // The capture lost without a release reaching the knob; ultra.html has no helper for this one
            consumer.onTagEvent(this, "onlostpointercapture", { e: PointerEvent -> stopDragOf(e) }.asDynamic())
            onKeyDown { e -> key(e) }

            if (props.ring) {
                svgRoot(viewBox = "0 0 100 100", style = "display:block;height:100%;pointer-events:none;") {
                    custom("path") {
                        attributes["class"] = styles.knobTrack.toString()
                        attributes["d"] = Dial.arc(fraction = 1.0, radius = 45.0)
                        attributes["stroke-width"] = "5"
                    }
                    custom("path") {
                        attributes["class"] = styles.knobArc.toString()
                        attributes["data-knob-arc"] = "true"
                        attributes["d"] = Dial.arc(fraction = position, radius = 45.0)
                        attributes["stroke-width"] = "5"
                    }
                }
            }

            val capClasses = listOf(styles.knobCap, if (silver) styles.knobCapSilver else styles.knobCapDark)

            div(classes = capClasses.joinToString(" ")) {
                val inset = if (props.ring) CAP_INSET_WITH_RING_PCT else 0
                css {
                    put("inset", "$inset%")
                    // Its own cut of the grain, so no two caps look alike
                    with(styles) { grainAt(grain) }
                }

                div(classes = styles.knobTurn.toString()) {
                    attributes["data-knob-turn"] = "true"
                    css { put("transform", "rotate(${Dial.capDegrees(position)}deg)") }

                    val mark = if (props.icon == null) styles.knobPointer else styles.knobDot
                    val markClasses = listOfNotNull(mark, if (silver) styles.knobMarkSilver else null)

                    div(classes = markClasses.joinToString(" ")) {}
                }

                props.icon?.let { iconFn ->
                    val iconClasses = listOfNotNull(styles.knobIcon, if (silver) styles.knobIconSilver else null)

                    icon.iconFn().with(iconClasses.joinToString(" ")).render {
                        attributes["aria-hidden"] = "true"
                        // Small enough to leave the dot at the rim its room (maintainer, 2026-10-25)
                        css { fontSize = props.size * 0.22 }
                    }
                }
            }
        }
    }
}
