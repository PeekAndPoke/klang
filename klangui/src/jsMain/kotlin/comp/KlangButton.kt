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
import io.peekandpoke.ultra.html.onClick
import kotlinx.css.px
import kotlinx.html.BUTTON
import kotlinx.html.ButtonType
import kotlinx.html.Tag
import kotlinx.html.button

@Suppress("FunctionName")
fun Tag.KlangButton(
    onClick: () -> Unit,
    finish: KlangFinish = KlangFinish.Dark,
    surface: KlangButton.Surface = KlangButton.Surface.Flat,
    shape: KlangButton.Shape = KlangButton.Shape.Rect,
    pressed: Boolean? = null,
    current: Boolean = false,
    availability: KlangButton.Availability = KlangButton.Availability.Enabled,
    lamp: KlangLamp.State? = null,
    beacon: Boolean = false,
    ariaLabel: String? = null,
    content: BUTTON.() -> Unit,
) = comp(
    KlangButton.Props(
        onClick = onClick,
        finish = finish,
        surface = surface,
        shape = shape,
        pressed = pressed,
        current = current,
        availability = availability,
        lamp = lamp,
        beacon = beacon,
        ariaLabel = ariaLabel,
        content = content,
    )
) {
    KlangButton(it)
}

/**
 * The Klang button (KLANG-00337): a real `<button>` with a cap lit from above.
 *
 * [KlangFinish] is the cap's material, [Surface] its curve, [Shape] its outline. A toggle passes [Props.pressed] (true or
 * false, announced as `aria-pressed`); a plain button leaves it null; the current place passes [Props.current] (drawn
 * pressed, announced as `aria-current`). [Availability.Later] draws a dashed outline for a control that is not there
 * yet ("with the backend"): it cannot be pressed and says so (`aria-disabled`). [Props.beacon] is the first-visit
 * trail's "try me". Canvas cells: "Rack button", "Lamp button", "White or silver, flat or domed".
 */
class KlangButton(ctx: Ctx<Props>) : Component<KlangButton.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val onClick: () -> Unit,
        val finish: KlangFinish,
        val surface: Surface,
        val shape: Shape,
        /** Null for a plain button; true or false for a toggle, which is then announced as pressed or not. */
        val pressed: Boolean?,
        /** Where the user is (a drawer's current destination): drawn pressed, announced as `aria-current`. */
        val current: Boolean,
        val availability: Availability,
        /** A lamp before the content: the button's own state, as on a drum machine's step keys. */
        val lamp: KlangLamp.State?,
        val beacon: Boolean,
        /** Required in effect for a button without text (an icon only, [Shape.Round]). */
        val ariaLabel: String?,
        val content: BUTTON.() -> Unit,
    )

    /** The cap's curve: flat with a top highlight, or domed like the volume knob. */
    enum class Surface {
        Flat,
        Domed,
    }

    enum class Shape {
        Rect,
        Pill,
        Round,

        /** Tall, its content stacked: a drawer's entry, a swatch button. */
        Tile,
    }

    enum class Availability {
        Enabled,
        Disabled,

        /** Not there yet: drawn as a dashed outline, cannot be pressed. */
        Later,
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    /** Where this button cuts its grain from, so no two look alike; kept while it lives. */
    private val grain = KlangControlStyles.grainOffset()

    override fun VDom.render() {
        val enabled = props.availability == Availability.Enabled

        button(type = ButtonType.button, classes = classes().joinToString(" ")) {
            if (props.availability == Availability.Disabled) {
                disabled = true
            }

            if (props.availability == Availability.Later) {
                attributes["aria-disabled"] = "true"
            }

            props.pressed?.let { attributes["aria-pressed"] = it.toString() }

            if (props.current) {
                attributes["aria-current"] = "true"
            }

            props.ariaLabel?.let { attributes["aria-label"] = it }

            if (enabled) {
                onClick { props.onClick() }
            }

            // Its own cut of the grain; content that sets a style of its own replaces it (the grain then starts at 0)
            css { with(KlangControlStyles) { grainAt(grain) } }

            props.lamp?.let { KlangLamp(state = it, size = 7.px) }

            props.content(this)
        }
    }

    /** The classes, in the stylesheet's order of precedence. */
    private fun classes(): List<String> {
        val styles = KlangControlStyles

        val finish = when (props.finish) {
            KlangFinish.Dark -> when (props.surface) {
                Surface.Flat -> styles.darkFlat
                Surface.Domed -> styles.darkDomed
            }

            KlangFinish.Silver -> when (props.surface) {
                Surface.Flat -> styles.silverFlat
                Surface.Domed -> styles.silverDomed
            }
        }

        val shape = when (props.shape) {
            Shape.Rect -> null
            Shape.Pill -> styles.pill
            Shape.Round -> styles.round
            Shape.Tile -> styles.tile
        }

        val tileWithLamp = if (props.shape == Shape.Tile && props.lamp != null) styles.tileWithLamp else null

        val pressed = when {
            props.pressed != true && !props.current -> null
            props.finish == KlangFinish.Dark -> styles.pressedDark
            else -> styles.pressedSilver
        }

        val availability = when (props.availability) {
            Availability.Enabled -> null
            Availability.Disabled -> styles.disabled
            Availability.Later -> styles.later
        }

        val beacon = if (props.beacon && props.availability == Availability.Enabled) styles.beacon else null

        return listOfNotNull(styles.button, finish, shape, tileWithLamp, pressed, availability, beacon).map { it.toString() }
    }
}
