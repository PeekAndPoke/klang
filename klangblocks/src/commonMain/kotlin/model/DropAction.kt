package io.peekandpoke.klang.blocks.model

/** A complete drop operation: payload + destination. Executed by [KBProgramEditingCtx.execute]. */
sealed interface DropAction {
    /** Create a new single-block chain from the palette and place it at [destination]. */
    data class CreateBlock(
        val funcName: String,
        val destination: DropDestination,
    ) : DropAction

    /**
     * Move one or more blocks (removing them from wherever they live in the tree)
     * and place them at [destination].
     */
    data class MoveBlocks(
        val blocks: List<KBCallBlock>,
        val destination: DropDestination,
    ) : DropAction

    /** Reorder an entire top-level chain row to a different row position. */
    data class MoveRow(
        val sourceStmtId: String,
        val targetIndex: Int,
    ) : DropAction

    /** Execute multiple actions in sequence, each on the result of the previous. */
    data class Compound(val actions: List<DropAction>) : DropAction
}
