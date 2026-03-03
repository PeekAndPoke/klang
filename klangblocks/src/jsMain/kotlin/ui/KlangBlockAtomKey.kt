package io.peekandpoke.klang.blocks.ui

/** Identifies a unique active atom by its slot and char range. */
internal data class KlangBlockAtomKey(val slotIndex: Int?, val atomStart: Int?, val atomEnd: Int?)
