/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.getAttributeRecursiveOrNull
import io.peekandpoke.kraft.modals.ModalsManager
import kotlinx.browser.document

/**
 * Whether nothing is in front of the workbench's panels (KLANG-00334 items 39, 40): no modal open (a code tool's opens
 * no key layer, so a panel's key would answer behind it), and the focus not in a popup (the code tool's popover). A
 * popup that does not hold the focus (the editor's hover docs, open while the mouse rests on a word) blocks nothing.
 * The Esc of a panel and the Motor menu's Alt+M wait while something is in front. Read at the moment of the key; an
 * app without modals has none.
 *
 * Scaffolding: it goes when the key manager lets the newest registration on a key answer across actions and the code
 * tools declare their own Esc while open (the maintainer, 2026-10-26: stacking, declarative only; asked of
 * Klang-Main-Dev). Alt+M keeps no guard then.
 */
internal fun Component<*>.nothingInFront(): Boolean =
    getAttributeRecursiveOrNull(ModalsManager.key)?.invoke().isNullOrEmpty() &&
        document.activeElement?.closest(POPUP_STAGE) == null

/** Where Kraft's popups manager draws every popup. */
private const val POPUP_STAGE = ".popup-stage"
