package io.peekandpoke.klang.blockly

/**
 * String constants for Blockly event types, mirroring the values of `Blockly.Events.*`.
 *
 * Kept in a plain Kotlin file (not `@JsModule`) so they can be used anywhere without
 * being subject to the external-declarations-only restriction.
 */
object BlocklyEventTypes {
    /** A block's field value or comment changed. */
    const val BLOCK_CHANGE = "change"

    /** One or more blocks were created. */
    const val BLOCK_CREATE = "create"

    /** One or more blocks were deleted. */
    const val BLOCK_DELETE = "delete"

    /** A block was moved within the workspace. */
    const val BLOCK_MOVE = "move"

    /** Workspace finished loading from serialised state. */
    const val FINISHED_LOADING = "finished_loading"

    // ---- Pure UI events — do NOT indicate a structural program change ----

    const val UI = "ui"
    const val VIEWPORT_CHANGE = "viewport_change"
    const val SELECTED = "selected"
    const val CLICK = "click"
    const val BUBBLE_OPEN = "bubble_open"
    const val THEME_CHANGE = "theme_change"
    const val TOOLBOX_ITEM_SELECT = "toolbox_item_select"
    const val TRASHCAN_OPEN = "trashcan_open"

    /** Set of all event types that indicate a structural workspace change. */
    val STRUCTURAL: Set<String> = setOf(
        BLOCK_CREATE, BLOCK_DELETE, BLOCK_MOVE, BLOCK_CHANGE, FINISHED_LOADING
    )
}
