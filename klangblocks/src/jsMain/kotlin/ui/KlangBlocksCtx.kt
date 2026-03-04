package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.ultra.streams.Stream
import io.peekandpoke.klang.blocks.model.KBProgramEditingCtx

data class KlangBlocksCtx(
    val editing: KBProgramEditingCtx,
    val dnd: DndCtrl,
    val highlights: Stream<KlangBlocksHighlightBuffer.HighlightSignal?>,
    val theme: KlangBlocksTheme = KlangBlocksTheme.Default,
)
