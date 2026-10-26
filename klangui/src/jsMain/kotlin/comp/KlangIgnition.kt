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
import kotlinx.css.LinearDimension
import kotlinx.css.fontSize
import kotlinx.css.height
import kotlinx.css.px
import kotlinx.css.width
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.span
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Suppress("FunctionName")
fun Tag.KlangIgnition(
    position: KlangIgnition.Position,
    glowing: Boolean,
    onTurn: () -> Unit,
    size: LinearDimension = 200.px,
    name: String = "Start the Motor",
) = comp(
    KlangIgnition.Props(position = position, glowing = glowing, onTurn = onTurn, size = size, name = name)
) {
    KlangIgnition(it)
}

/**
 * The ignition key (KLANG-00347, the epic's board G): a silver cap with OFF, ON and START printed above it, and the
 * glow lamp beside ON.
 *
 * At [Position.Off] it waits with the gold "try me" beacon, and one click (Enter or Space too, it is a real button)
 * asks to turn it: [Props.onTurn]. The caller owns the position: it moves the key to ON, lights the glow lamp
 * ([Props.glowing], amber, a diesel's glow plug) while the Motor loads, and to START when it runs. Past OFF the key
 * takes no more turns. The key turns with a short motion, none under "reduce motion".
 */
class KlangIgnition(ctx: Ctx<Props>) : Component<KlangIgnition.Props>(ctx) {

    companion object {
        /** How far the key turns from one position to the next, in degrees. */
        const val STEP_DEGREES = 45.0

        /** Where the positions are printed, from the cap's centre, as a share of the cap's size. */
        private const val LABEL_RADIUS = 0.8

        /** The box around the cap, which leaves room for the positions above it, as a share of the cap's size. */
        private const val BOX_WIDTH = 1.6
        private const val BOX_HEIGHT = 1.4

        /** Where the cap's centre sits below the key's top, as a share of the cap's size, for placing the cap. */
        const val CAP_CENTRE_FROM_TOP = BOX_HEIGHT - 0.5
    }

    enum class Position(val label: String) {
        Off("OFF"),
        On("ON"),
        Start("START");

        /** Where the key points: OFF to the upper left, ON straight up, START to the upper right. */
        val degrees: Double get() = (ordinal - 1) * STEP_DEGREES
    }

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val position: Position,
        /** The glow lamp beside ON: lit while the Motor gets ready. */
        val glowing: Boolean,
        /** The key was turned at [Position.Off]; it is not called past it. */
        val onTurn: () -> Unit,
        /** The cap's size; the positions take room above it. */
        val size: LinearDimension,
        /** What the key does, for screen readers. */
        val name: String,
    )

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        val styles = KlangControlStyles
        val waiting = props.position == Position.Off

        div(classes = styles.ignition.toString()) {
            css {
                width = props.size * BOX_WIDTH
                height = props.size * BOX_HEIGHT
            }

            for (position in Position.entries) {
                renderLabel(position)
            }

            val capClasses = listOfNotNull(
                styles.knobCap,
                styles.knobCapSilver,
                styles.ignitionCap,
                if (waiting) styles.beacon else null,
            )

            button(type = ButtonType.button, classes = capClasses.joinToString(" ")) {
                // The positions above the cap are hidden from screen readers: the name says where the key is
                attributes["aria-label"] = "${props.name}: ${spoken()}"
                // Not disabled: the focus stays on the key while it turns
                attributes["aria-disabled"] = (!waiting).toString()
                onClick {
                    if (props.position == Position.Off) {
                        props.onTurn()
                    }
                }

                css {
                    width = props.size
                    height = props.size
                    put("left", "calc(50% - ${props.size * 0.5})")
                    put("bottom", "0")
                }

                div(classes = "${styles.knobTurn} ${styles.ignitionTurn}") {
                    attributes["data-key-turn"] = "true"
                    css { put("transform", "rotate(${props.position.degrees}deg)") }

                    div(classes = "${styles.knobPointer} ${styles.knobMarkSilver}") {}
                }
            }
        }
    }

    /** Where the key is, in words, for screen readers. */
    private fun spoken(): String = when (props.position) {
        Position.Off -> "off"
        Position.On -> if (props.glowing) "on, warming up" else "on"
        Position.Start -> "running"
    }

    /** A position's name on its spot above the cap; the glow lamp after ON. */
    private fun FlowContent.renderLabel(position: Position) {
        val styles = KlangControlStyles
        val current = position == props.position
        val radians = position.degrees * PI / 180.0
        // From the box's top left, in shares of the cap's size: the cap's centre sits half a cap above the box's foot
        val x = BOX_WIDTH / 2.0 + LABEL_RADIUS * sin(radians)
        val y = CAP_CENTRE_FROM_TOP - LABEL_RADIUS * cos(radians)

        span(classes = listOfNotNull(styles.ignitionLabel, if (current) styles.ignitionLabelCurrent else null).joinToString(" ")) {
            attributes["aria-hidden"] = "true"
            attributes["data-ignition-label"] = position.name

            css {
                put("left", "${x / BOX_WIDTH * 100}%")
                put("top", "${y / BOX_HEIGHT * 100}%")
                fontSize = props.size * 0.09
            }

            +position.label

            if (position == Position.On) {
                KlangLamp(
                    state = if (props.glowing) KlangLamp.State.Warning else KlangLamp.State.Off,
                    size = props.size * 0.05,
                )
            }
        }
    }
}
