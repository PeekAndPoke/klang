/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.KlangGuiDance
import io.peekandpoke.klang.ui.comp.KlangIconToggle
import io.peekandpoke.klang.ui.comp.KlangNoticeModal
import io.peekandpoke.klang.utils.FullscreenController
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.modals.ModalsManager.Companion.modals
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
 * available on every page"). Entered with the browser's own key (F11), only that key leaves it, and the label says so.
 */
class FullscreenToggleButton(ctx: Ctx<Props>) : Component<FullscreenToggleButton.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val fs: FullscreenController,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val state by subscribingTo(props.fs)

    /** The Gui-dance's lit step: the key wears its beacon when it is the key's (KLANG-00350). */
    private val guiDanceLit by subscribingTo(KlangGuiDance.dance.lit)

    private fun toggle() {
        KlangGuiDance.dance.used(KlangGuiDance.FULLSCREEN)

        // Fullscreen the browser started (F11, or a Mac's own) only the browser can end: say so, as a click does nothing
        // (the maintainer, 2026-10-26: "i cannot leave fullscreen when clicking the icon")
        if (state.isFullscreen && !state.canExitWithClick) {
            explainBrowserFullscreen()
            return
        }

        props.fs.toggle()
    }

    private fun explainBrowserFullscreen() {
        modals.show<Boolean> { handle ->
            KlangNoticeModal(handle = handle, headline = "Leave fullscreen with your browser's key", okText = "Got it") {
                +"This fullscreen was started by the browser (with F11), so only the browser can end it: press F11 again. "
                +"On a Mac, press Ctrl+Cmd+F."
            }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        val label = if (state.canExitWithClick) "Toggle fullscreen" else "Press F11 to exit fullscreen"

        KlangIconToggle(
            icon = if (state.isFullscreen) ({ compress }) else ({ expand }),
            label = label,
            on = state.isFullscreen,
            onClick = { toggle() },
            large = true,
            beacon = guiDanceLit == KlangGuiDance.FULLSCREEN,
        )
    }
}
