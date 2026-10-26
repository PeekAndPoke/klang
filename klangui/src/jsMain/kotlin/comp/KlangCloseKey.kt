/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.ui.comp.KlangKeyHint.capsGroups
import io.peekandpoke.klang.workshop.keys.KeyHintMode
import io.peekandpoke.klang.workshop.keys.onKeyController
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.html.Tag
import kotlinx.html.span

@Suppress("FunctionName")
fun Tag.KlangCloseKey(
    onClose: () -> Unit,
) = comp(
    KlangCloseKey.Props(onClose = onClose)
) {
    KlangCloseKey(it)
}

/**
 * The close key of a panel that may close on Esc (KLANG-00334 item 39: a tool window flying in, the Motor's drawer):
 * while it is mounted it answers [KlangKeyBindings.PanelClose] through the key manager, and it shows its pill,
 * "Close: Esc", always, where the panel's header has room for it.
 *
 * A panel renders it only while it may close that way, so the newest open panel registered last and closes first.
 */
class KlangCloseKey(ctx: Ctx<Props>) : Component<KlangCloseKey.Props>(ctx) {

    data class Props(
        val onClose: () -> Unit,
    )

    private val close = onKeyController(
        action = KlangKeyBindings.PanelClose,
        hintMode = KeyHintMode.Always,
    ) {
        props.onClose()
    }

    override fun VDom.render() {
        val hint = close.hint

        span(classes = KlangControlStyles.keyPill.toString()) {
            // Nothing while the key does not answer here (a dialog on top, a page fading out)
            if (hint != null) {
                +"${hint.action.title}:"
                // Never greyed: the pill names the panel's key, and nothing redraws it when a modal comes and goes
                capsGroups(groups = hint.keyGroups())
            }
        }
    }
}
