/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.MasterVolumeScale
import io.peekandpoke.klang.Player
import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.ui.comp.KlangKnob
import io.peekandpoke.klang.workshop.keys.OnKey
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import kotlinx.css.Display
import kotlinx.css.LinearDimension
import kotlinx.css.display
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.title
import org.w3c.dom.HTMLElement
import kotlin.math.roundToInt

@Suppress("FunctionName")
fun Tag.VolumeKnob(
    size: LinearDimension,
) = comp(
    VolumeKnob.Props(size = size)
) {
    VolumeKnob(it)
}

/**
 * The master volume as a knob (KLANG-00330, on `KlangKnob` since KLANG-00344): a drag up or down turns it, the arrow
 * keys too once it has the focus (Alt+V gives it the focus); at the top end it is full, the song's own level, and it
 * never goes above.
 *
 * The volume lives in [Player.outputVolume], as a gain; the knob derives its position from it on the loudness scale
 * ([MasterVolumeScale]), turns it from there, and has it remembered when a turn is over.
 */
class VolumeKnob(ctx: Ctx<Props>) : Component<VolumeKnob.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val size: LinearDimension,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val outputVolume by subscribingTo(Player.outputVolume)

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        val position = MasterVolumeScale.positionOf(outputVolume)
        val level = if (position <= 0.0) "off" else "${MasterVolumeScale.dbOf(position).roundToInt()} dB"

        // The level as a tooltip: at this size there is no room to print it beside the knob
        div {
            css { display = Display.inlineBlock }
            title = "Volume: $level\ndrag up or down, or the arrow keys"

            // Alt+V puts the focus on the knob, the arrows then turn it (the maintainer, 2026-10-26)
            OnKey(KlangKeyBindings.VolumeFocus) { (dom?.querySelector("[role=slider]") as? HTMLElement)?.focus() }

            KlangKnob(
                name = "Volume",
                value = position,
                onChange = { Player.setOutputVolume(MasterVolumeScale.gainOf(it)) },
                onChangeEnd = { Player.rememberOutputVolume() },
                size = props.size,
            )
        }
    }
}
