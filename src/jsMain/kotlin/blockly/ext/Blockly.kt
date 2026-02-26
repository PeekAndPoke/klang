@file:JsModule("blockly")
@file:JsNonModule
@file:Suppress("unused")

package io.peekandpoke.klang.blockly.ext

import org.w3c.dom.Element

// ============================================================
// Top-level functions
// ============================================================

/**
 * Inject a Blockly workspace into the given container element.
 *
 * @param container The DOM element to inject the workspace into.
 * @param options   Configuration options (toolbox, trashcan, …).
 * @return The created [WorkspaceSvg].
 */
external fun inject(container: Element, options: BlocklyOptions): WorkspaceSvg

/**
 * Define one or more block types from a JSON array.
 * Calling this with the same type more than once simply replaces the definition.
 */
external fun defineBlocksWithJsonArray(blocksArray: Array<dynamic>)

// ============================================================
// Serialization namespace
//
// Usage:
//   serialization.workspaces.save(workspace)   → dynamic (state object)
//   serialization.workspaces.load(state, workspace)
// ============================================================

external val serialization: dynamic

// ============================================================
// Classes
// ============================================================

/**
 * An SVG Blockly workspace — the top-level container returned by [inject].
 */
external class WorkspaceSvg {
    /** Clear all blocks from the workspace (does NOT dispose the workspace). */
    fun clear()

    /** Dispose the workspace and remove its DOM elements. */
    fun dispose()

    /**
     * Return the top-level (un-parented) blocks in the workspace.
     *
     * @param ordered If true, returns blocks in a consistent left-to-right, top-to-bottom order.
     */
    fun getTopBlocks(ordered: Boolean = definedExternally): Array<Block>

    /**
     * Create a new headless [Block] of the given type.
     * For an SVG workspace you must call [BlockSvg.initSvg] and [BlockSvg.render]
     * afterwards to make it visible; use [newBlockAndRender] if you want that automatically.
     */
    fun newBlock(prototypeName: String): BlockSvg

    /** Add a listener that is called every time the workspace changes. */
    fun addChangeListener(fn: (event: dynamic) -> Unit)

    /** Remove a previously registered change listener. */
    fun removeChangeListener(fn: (event: dynamic) -> Unit)

    /** Force a re-render of the workspace. */
    fun render()
}

/**
 * A rendered (SVG) block.  Extends the headless [Block] concept with SVG management.
 */
external class BlockSvg {
    /** The block-type identifier (e.g. `"klang_sound"`). */
    val type: String

    /** The next block in the stack (null if this is the last). */
    val nextBlock: BlockSvg?

    /** The previous block in the stack (null if this is the first). */
    val previousBlock: BlockSvg?

    /** The outgoing connection at the bottom of this block (for stacks). */
    val nextConnection: Connection?

    /** The incoming connection at the top of this block (for stacks). */
    val previousConnection: Connection?

    /**
     * Return the current string value of the named field, or null if no such field exists.
     */
    fun getFieldValue(name: String): String?

    /**
     * Set the value of a named field.
     *
     * @param value The new value (always a string).
     * @param name  The field name as declared in the block definition.
     */
    fun setFieldValue(value: String, name: String)

    /**
     * Initialise the block's SVG representation.
     * Must be called on a newly created block before [render].
     */
    fun initSvg()

    /** Render the block to the SVG canvas. */
    fun render()

    /**
     * Dispose of this block, removing it from the workspace.
     *
     * @param healStack If true, reconnect the blocks above and below after removal.
     */
    fun dispose(healStack: Boolean = definedExternally)
}

/**
 * Convenience alias so callers that only need the shared API can use [Block].
 * At runtime every block on an [WorkspaceSvg] is actually a [BlockSvg].
 */
typealias Block = BlockSvg

/**
 * A connection between two blocks (e.g. nextConnection ↔ previousConnection).
 */
external class Connection {
    /** Connect this connection to [otherConnection]. */
    fun connect(otherConnection: Connection)

    /** Return true when this connection is currently connected to another. */
    fun isConnected(): Boolean

    /** Return the block that owns this connection. */
    fun getSourceBlock(): BlockSvg
}

// ============================================================
// Options / configuration
// ============================================================

/**
 * Options passed to [inject].
 */
external interface BlocklyOptions {
    /**
     * Toolbox definition — either a JSON object (created via `JSON.parse(…)`) or a plain-string
     * toolbox XML.  Prefer JSON.
     */
    var toolbox: dynamic

    /** If true, the workspace is read-only (no editing). Default: false. */
    var readOnly: Boolean?

    /** If true, show a trashcan widget. Default: true. */
    var trashcan: Boolean?

    /** If true, play sound effects. Default: true. */
    var sounds: Boolean?

    /** Grid configuration object (spacing, length, colour, snap). */
    var grid: dynamic

    /** Zoom configuration object (controls, wheel, startScale, …). */
    var zoom: dynamic

    /**
     * URL prefix for Blockly's media files (arrow icons, etc.).
     * Needed when serving from a custom base path.
     */
    var media: String?

    /** Renderer name ("geras", "zelos", "thrasos", "minimalist"). Default: "geras". */
    var renderer: String?

    /** Theme object or name. */
    var theme: dynamic

    /** Move configuration (scrollbars, drag, wheel). */
    var move: dynamic

    /**
     * Maximum number of blocks allowed in the workspace.
     * -1 means unlimited. Default: Infinity.
     */
    var maxBlocks: Int?

    /** Whether to collapse blocks. Default: true. */
    var collapse: Boolean?

    /** Whether to allow comments on blocks. Default: true. */
    var comments: Boolean?

    /** Whether to allow disabling blocks. Default: true. */
    var disable: Boolean?
}

// Note: BlocklyEventTypes lives in a separate non-@JsModule file: BlocklyEventTypes.kt
