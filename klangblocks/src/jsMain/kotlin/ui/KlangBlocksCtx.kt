package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.ultra.streams.Stream

data class KlangBlocksCtx(
    val editing: KBProgramEditingCtx,
    val dnd: DndCtrl,
    val highlights: Stream<HighlightSignal?>,
)
