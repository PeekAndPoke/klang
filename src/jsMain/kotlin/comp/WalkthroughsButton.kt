/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.KlangGuiDance
import io.peekandpoke.klang.ui.comp.KlangIconToggle
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.modals.ModalsManager.Companion.modals
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.html.Tag

@Suppress("FunctionName")
fun Tag.WalkthroughsButton() = comp {
    WalkthroughsButton(it)
}

/**
 * The walkthroughs key at the workbench strip's foot, above fullscreen (KLANG-00350; the maintainer: "a little
 * tutorial icon on top of the fullscreen icon", the "shoes" icon, "walkthrough", not "tour"): it opens the
 * [WalkthroughsWindow], and it is itself the last step of the fullscreen walkthrough.
 */
class WalkthroughsButton(ctx: NoProps) : PureComponent(ctx) {

    /** The Gui-dance's lit step: the key wears its beacon when it is the key's. */
    private val guiDanceLit by subscribingTo(KlangGuiDance.dance.lit)

    private fun open() {
        KlangGuiDance.dance.used(KlangGuiDance.WALKTHROUGHS)

        modals.show { handle -> WalkthroughsWindow(handle = handle) }
    }

    override fun VDom.render() {
        KlangIconToggle(
            icon = { shoe_prints },
            label = "Walkthroughs",
            on = false,
            onClick = { open() },
            large = true,
            beacon = guiDanceLit == KlangGuiDance.WALKTHROUGHS,
        )
    }
}
