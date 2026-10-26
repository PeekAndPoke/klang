/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.ui.comp.KlangIconToggle
import io.peekandpoke.klang.utils.FullscreenController
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.html.Tag

@Suppress("FunctionName")
fun Tag.FullscreenToggleButton(
    fs: FullscreenController,
) = comp(
    FullscreenToggleButton.Props(fs = fs)
) {
    FullscreenToggleButton(it)
}

/**
 * Fullscreen on and off, a key at the workbench strip's foot on every page (the maintainer, KLANG-00349: "should be
 * available on every page"). Entered with the browser's own key (F11), only Esc leaves it, and the label says so.
 */
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

        KlangIconToggle(
            icon = if (state.isFullscreen) ({ compress }) else ({ expand }),
            label = label,
            on = state.isFullscreen,
            onClick = { toggle() },
            large = true,
        )
    }
}
