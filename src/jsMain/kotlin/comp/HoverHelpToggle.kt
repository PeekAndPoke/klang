/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.storage.EditorSettings
import io.peekandpoke.klang.ui.comp.KlangCheckbox
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.html.Tag

@Suppress("FunctionName")
fun Tag.HoverHelpToggle() = comp {
    HoverHelpToggle(it)
}

/**
 * The switch for a name's help on hover (KLANG-00349; the maintainer, 2026-10-27: on by default, the option to turn it
 * off "into the header of the tool", and a button there "too brutal"): a checkbox in the Quick help panel's header, the
 * same setting as in the Settings window.
 */
class HoverHelpToggle(ctx: NoProps) : PureComponent(ctx) {

    private val hoverHelp by subscribingTo(EditorSettings.hoverHelp)

    override fun VDom.render() {
        KlangCheckbox(label = "Show on hover", checked = hoverHelp, onChange = { EditorSettings.setHoverHelp(it) })
    }
}
