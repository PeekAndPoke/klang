/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.onBlur
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.html.onKeyDown
import kotlinx.css.LinearDimension
import kotlinx.css.px
import kotlinx.html.InputType
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.input
import kotlinx.html.span
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import kotlin.math.floor
import kotlin.math.pow

@Suppress("FunctionName")
fun Tag.KlangValueKnob(
    name: String,
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    step: Double,
    onChange: (Double) -> Unit,
    unit: String,
    decimals: Int = 1,
    perPixel: Double? = null,
    size: LinearDimension = 34.px,
) = comp(
    KlangValueKnob.Props(
        name = name,
        value = value,
        range = range,
        step = step,
        onChange = onChange,
        unit = unit,
        decimals = decimals,
        perPixel = perPixel,
        size = size,
    )
) {
    KlangValueKnob(it)
}

/**
 * A value on a knob with its reading on an LCD beside it (KLANG-00349, the song's RPM; the maintainer, 2026-10-26: no
 * browser steppers, "this looks weird in different browsers and os-es").
 *
 * The knob turns the value over [Props.range] by a drag, or by the arrow keys a [Props.step] at a time. A click on
 * the LCD (or Enter on it) lets one type the value: Enter or leaving the field takes it, coerced into the range; Esc,
 * or what is not a number, gives it up. Every value is held to [Props.decimals] (the maintainer, 2026-10-26: "The
 * input knob needs configurable precission ... the RPM might really need 2 decimal points"), and the LCD shows
 * exactly that many, as an LCD's fixed digits do.
 */
class KlangValueKnob(ctx: Ctx<Props>) : Component<KlangValueKnob.Props>(ctx) {

    companion object {
        /** [value] with exactly [decimals] decimals: 37.20 for 2, 37 for 0. */
        fun format(value: Double, decimals: Int): String = value.asDynamic().toFixed(decimals.coerceIn(0, 6)) as String
    }

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        /** What the value is, for screen readers ("Revolutions per minute"). */
        val name: String,
        val value: Double,
        val range: ClosedFloatingPointRange<Double>,
        /** An arrow key's step. */
        val step: Double,
        val onChange: (Double) -> Unit,
        /** Printed on the LCD after the value ("RPM"). */
        val unit: String,
        /** The precision every value is held to, and the decimals the LCD shows. */
        val decimals: Int,
        /** How much one pixel of a drag changes the value; null turns the whole range in the knob's usual drag. */
        val perPixel: Double?,
        /** The knob's size; the LCD takes the knob's height. */
        val size: LinearDimension,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    /** The LCD is a field while the user types into it. */
    private var editing: Boolean by value(false)

    /** The field just opened: the next update gives it the value, the focus and a selection, once. */
    private var opening = false

    /** The field closed by a key: the next update gives the focus back to the reading. */
    private var refocus = false

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    private val span: Double get() = props.range.endInclusive - props.range.start

    private fun positionOf(v: Double): Double = if (span > 0.0) ((v - props.range.start) / span).coerceIn(0.0, 1.0) else 0.0

    /** [v] into the range, held to the precision; a NaN keeps the value as it is. */
    private fun settle(v: Double): Double {
        // NaN-guard: not a number changes nothing
        if (v != v) {
            return props.value
        }

        val scale = 10.0.pow(props.decimals.coerceIn(0, 6))

        // Ties round up, as the LCD's toFixed shows a positive value (37.125 is 37.13)
        return (floor(v * scale + 0.5) / scale).coerceIn(props.range.start, props.range.endInclusive)
    }

    private fun shown(v: Double): String = format(v, props.decimals)

    /** The widest reading the range allows, in characters: the field and the reading take it, so neither cuts a digit. */
    private val digits: Int get() = maxOf(shown(props.range.start).length, shown(props.range.endInclusive).length)

    private fun open() {
        editing = true
        opening = true
    }

    private fun take(text: String) {
        editing = false

        val typed = text.trim().replace(',', '.').toDoubleOrNull() ?: return
        val next = settle(typed)

        if (next != props.value) {
            props.onChange(next)
        }
    }

    override fun VDom.render() {
        val styles = KlangControlStyles

        div(classes = styles.valueKnob.toString()) {
            KlangKnob(
                name = props.name,
                value = positionOf(props.value),
                onChange = { position ->
                    val next = settle(props.range.start + position * span)

                    if (next != props.value) {
                        props.onChange(next)
                    }
                },
                size = props.size,
                spokenValue = "${shown(props.value)} ${props.unit}",
                step = if (span > 0.0) props.step / span else 0.05,
                dragPixels = props.perPixel?.takeIf { it > 0.0 && span > 0.0 }?.let { span / it }
                    ?: KlangKnob.DRAG_PIXELS_FOR_FULL_TURN,
            )

            div(classes = styles.lcd.toString()) {
                css { put("height", props.size.toString()) }

                if (editing) {
                    input(type = InputType.text, classes = styles.lcdInput.toString()) {
                        css { put("width", "${digits}ch") }
                        attributes["aria-label"] = props.name
                        attributes["inputmode"] = "decimal"
                        // No value bound here: a redraw while typing (the song page redraws on every cycle) would put
                        // the value back over what is typed; the field gets it once when it opens (see onUpdate)

                        onKeyDown { e ->
                            val field = e.target as? HTMLInputElement

                            // An input method that is composing owns Enter and Esc (it confirms or cancels its text)
                            if (e.isComposing) {
                                return@onKeyDown
                            }

                            when (e.key) {
                                "Enter" -> {
                                    e.preventDefault()
                                    refocus = true
                                    field?.let { take(it.value) }
                                }

                                // The field uses its Esc: the key manager leaves a prevented Esc to it
                                "Escape" -> {
                                    e.preventDefault()
                                    refocus = true
                                    editing = false
                                }
                            }
                        }

                        onBlur { e ->
                            if (editing) {
                                (e.target as? HTMLInputElement)?.let { take(it.value) }
                            }
                        }
                    }
                } else {
                    span(classes = styles.lcdValue.toString()) {
                        css { put("min-width", "${digits}ch") }
                        attributes["role"] = "button"
                        attributes["tabindex"] = "0"
                        attributes["aria-label"] = "${props.name}: ${shown(props.value)} ${props.unit}, type a value"

                        onClick { open() }
                        // Enter or Space, as a button; a held key's repeat does not open it again
                        onKeyDown { e ->
                            if ((e.key == "Enter" || e.key == " ") && !e.repeat) {
                                e.preventDefault()
                                open()
                            }
                        }

                        +shown(props.value)
                    }
                }

                span(classes = styles.lcdUnit.toString()) {
                    attributes["aria-hidden"] = "true"
                    +props.unit
                }
            }
        }
    }

    init {
        lifecycle {
            onUpdate {
                // The field just opened: the value, the focus, its text selected, ready for a new number
                if (opening) {
                    opening = false

                    (dom?.querySelector("input") as? HTMLInputElement)?.let { field ->
                        field.value = shown(props.value)
                        field.focus()
                        field.select()
                    }
                }

                // Closed by Enter or Esc: the focus back on the reading, not lost to the page
                if (refocus && !editing) {
                    refocus = false

                    (dom?.querySelector("[role=button]") as? HTMLElement)?.focus()
                }
            }
        }
    }
}
