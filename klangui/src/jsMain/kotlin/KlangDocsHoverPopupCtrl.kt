/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui

import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.kraft.popups.PopupsManager
import io.peekandpoke.kraft.utils.Vector2D
import kotlinx.html.FlowContent
import org.w3c.dom.events.MouseEvent

fun HoverPopupCtrl.scheduleShow(
    doc: KlangSymbol,
    event: MouseEvent,
    positioning: PopupsManager.Positioning = PopupsManager.Positioning.TopLeft,
    content: FlowContent.(KlangSymbol) -> Unit,
) {
    scheduleShow(event = event, positioning = positioning) { _ -> content(doc) }
}

fun HoverPopupCtrl.scheduleShow(
    doc: KlangSymbol,
    anchor: Vector2D,
    positioning: PopupsManager.Positioning = PopupsManager.Positioning.TopLeft,
    content: FlowContent.(KlangSymbol) -> Unit,
) {
    scheduleShow(anchor = anchor, positioning = positioning) { _ -> content(doc) }
}
