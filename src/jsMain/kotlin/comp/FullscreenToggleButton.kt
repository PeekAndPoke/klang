/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.ui.comp.KlangButton
import io.peekandpoke.klang.utils.FullscreenController
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.semanticui.icon
import kotlinx.html.Tag
import kotlinx.html.title

@Suppress("FunctionName")
fun Tag.FullscreenToggleButton(
    fs: FullscreenController,
) = comp(
    FullscreenToggleButton.Props(fs = fs)
) {
    FullscreenToggleButton(it)
}

class FullscreenToggleButton(ctx: Ctx<Props>) : Component<FullscreenToggleButton.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val fs: FullscreenController,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val state by subscribingTo(props.fs)

    private fun toggle() {
        props.fs.toggle()
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        val label = if (state.canExitWithClick) "Toggle fullscreen" else "Press ESC to exit fullscreen"

        // A small round key, as the transport bar's others (KLANG-00349)
        KlangButton(onClick = { toggle() }, surface = KlangButton.Surface.Domed, shape = KlangButton.Shape.Round, ariaLabel = label) {
            title = label

            if (state.isFullscreen) {
                icon.compress()
            } else {
                icon.expand()
            }
        }
    }
}
