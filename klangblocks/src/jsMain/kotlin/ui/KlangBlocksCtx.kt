/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.blocks.ui

import io.peekandpoke.klang.blocks.model.KBProgramEditingCtx
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.ui.HoverPopupCtrl
import io.peekandpoke.ultra.streams.Stream
import kotlinx.html.FlowContent

data class KlangBlocksCtx(
    val editing: KBProgramEditingCtx,
    val dnd: DndCtrl,
    val highlights: Stream<KlangBlocksHighlightBuffer.HighlightSignal?>,
    val theme: KlangBlocksTheme = KlangBlocksTheme.Default,
    val hoverPopup: HoverPopupCtrl? = null,
    val hoverContent: (FlowContent.(KlangSymbol) -> Unit)? = null,
)
