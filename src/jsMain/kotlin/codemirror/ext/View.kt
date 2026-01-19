@file:JsModule("@codemirror/view")
@file:JsNonModule
@file:Suppress("unused")

package io.peekandpoke.klang.codemirror.ext

import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.MouseEvent

/**
 * Configuration for creating an EditorView
 */
external interface EditorViewConfig {
    var state: EditorState?
    var parent: Element?
    var root: Any? // Document or ShadowRoot
    var dispatch: ((tr: Transaction) -> Unit)?
}

/**
 * The editor view - represents the visible editor
 */
external class EditorView {
    constructor(config: EditorViewConfig)

    val state: EditorState
    val dom: HTMLElement
    val contentDOM: HTMLElement
    val scrollDOM: HTMLElement

    var dispatch: (tr: Transaction) -> Unit

    fun update(transactions: Array<Transaction>)
    fun setState(newState: EditorState)
    fun destroy()

    fun focus()
    fun hasFocus(): Boolean

    // Coordinate/position methods
    fun coordsAtPos(pos: Int, side: Int = definedExternally): Rect?
    fun posAtCoords(coords: Coords, precise: Boolean = definedExternally): Int?
    fun posAtDOM(node: Any, offset: Int = definedExternally): Int
    fun domAtPos(pos: Int): DOMPos

    // Viewport/scrolling
    val viewport: Viewport
    val viewportLineBlocks: Array<BlockInfo>
    fun scrollPosIntoView(pos: Int)

    // Measuring
    fun requestMeasure(request: MeasureRequest<*>)
    fun measureVisibleLineHeights(): Array<BlockInfo>
    fun lineBlockAt(pos: Int): BlockInfo
    fun lineBlockAtHeight(height: Double): BlockInfo

    // Plugin management
    fun plugin(plugin: ViewPlugin<*>): Any?

    companion object {
        val domEventHandlers: (handlers: dynamic) -> Extension
        val domEventObservers: (observers: dynamic) -> Extension
        val theme: (spec: dynamic, options: dynamic) -> Extension
        val baseTheme: (spec: dynamic) -> Extension
        val editorAttributes: Facet<dynamic>
        val contentAttributes: Facet<dynamic>
        val decorations: Facet<Any>
        val atomicRanges: Facet<Any>
        val scrollMargins: Facet<Any>
        val darkTheme: Facet<Boolean>
        val clickAddsSelectionRange: Facet<(event: MouseEvent) -> Boolean>
        val dragMovesSelection: Facet<(event: MouseEvent) -> Boolean>
        val perLineTextDirection: Facet<Boolean>
        val exceptionSink: Facet<(exception: Any) -> Unit>
        val updateListener: Facet<(update: ViewUpdate) -> Unit>
    }
}

/**
 * Rectangle coordinates
 */
external interface Rect {
    val left: Double
    val right: Double
    val top: Double
    val bottom: Double
}

/**
 * Coordinate pair
 */
external interface Coords {
    val x: Double
    val y: Double
}

/**
 * Position in the DOM
 */
external interface DOMPos {
    val node: Any // Node
    val offset: Int
}

/**
 * Information about the viewport
 */
external interface Viewport {
    val from: Int
    val to: Int
}

/**
 * Information about a line block
 */
external interface BlockInfo {
    val from: Int
    val to: Int
    val top: Double
    val bottom: Double
    val height: Double
}

/**
 * Request for measuring something in the editor
 */
external interface MeasureRequest<T> {
    val read: (view: EditorView) -> T
    val write: ((measure: T, view: EditorView) -> Unit)?
    val key: Any?
}

/**
 * View update - describes what changed in a view update
 */
external class ViewUpdate {
    val view: EditorView
    val state: EditorState
    val transactions: Array<Transaction>
    val startState: EditorState
    val changes: ChangeSet
    val viewportChanged: Boolean
    val heightChanged: Boolean
    val focusChanged: Boolean
    val docChanged: Boolean
    val selectionSet: Boolean
    val geometryChanged: Boolean
}

/**
 * View plugin - extends the editor view with additional functionality
 */
external interface ViewPlugin<T> {
    val extension: Extension
}

external interface PluginValue {
    fun update(update: ViewUpdate)
    fun destroy()
}

external interface PluginSpec<T : PluginValue> {
    val create: (view: EditorView) -> T
    val update: ((value: T, update: ViewUpdate) -> T)?
    val destroy: ((value: T) -> Unit)?
    val provide: ((plugin: ViewPlugin<T>) -> Extension)?
    val eventHandlers: dynamic
    val decorations: ((value: T) -> Any)?
}

/**
 * Decoration - visual styling for ranges in the editor
 */
external class Decoration {
    companion object {
        fun mark(spec: MarkDecorationSpec): Decoration
        fun widget(spec: WidgetDecorationSpec): Decoration
        fun replace(spec: ReplaceDecorationSpec): Decoration
        fun line(spec: LineDecorationSpec): Decoration

        val none: Any // DecorationSet
        fun set(decorations: Array<Range<Decoration>>, sort: Boolean = definedExternally): Any // DecorationSet
    }
}

/**
 * Decoration range
 */
external interface Range<T> {
    val from: Int
    val to: Int
    val value: T
}

/**
 * Mark decoration spec
 */
external interface MarkDecorationSpec {
    var inclusive: Boolean?
    var inclusiveStart: Boolean?
    var inclusiveEnd: Boolean?
    var attributes: dynamic
    var `class`: String?
    var tagName: String?
}

/**
 * Widget decoration spec
 */
external interface WidgetDecorationSpec {
    var side: Int?
    var block: Boolean?
    var widget: WidgetType?
    var inlineOrder: Boolean?
}

/**
 * Replace decoration spec
 */
external interface ReplaceDecorationSpec {
    var widget: WidgetType?
    var inclusive: Boolean?
    var inclusiveStart: Boolean?
    var inclusiveEnd: Boolean?
    var block: Boolean?
}

/**
 * Line decoration spec
 */
external interface LineDecorationSpec {
    var attributes: dynamic
    var `class`: String?
}

/**
 * Widget type - custom widget to be rendered in the editor
 */
external interface WidgetType {
    fun toDOM(view: EditorView): HTMLElement
    fun updateDOM(dom: HTMLElement, view: EditorView): Boolean
    fun eq(other: WidgetType): Boolean
    val estimatedHeight: Int
    val ignoreEvent: ((event: Event) -> Boolean)?
}

/**
 * Tooltip - hover/popup information
 */
external interface Tooltip {
    val pos: Int
    val end: Int?
    val create: (view: EditorView) -> TooltipView
    val above: Boolean?
    val arrow: Boolean?
}

external interface TooltipView {
    val dom: HTMLElement
    val mount: ((view: EditorView) -> Unit)?
    val update: ((update: ViewUpdate) -> Unit)?
    val destroy: (() -> Unit)?
}

/**
 * Panel - UI panel above or below the editor
 */
external interface Panel {
    val dom: HTMLElement
    val mount: (() -> Unit)?
    val update: ((update: ViewUpdate) -> Unit)?
    val destroy: (() -> Unit)?
    val top: Boolean?
}

/**
 * Key binding
 */
external interface KeyBinding {
    var key: String?
    var mac: String?
    var win: String?
    var linux: String?
    var run: ((view: EditorView, event: KeyboardEvent) -> Boolean)?
    var shift: ((view: EditorView, event: KeyboardEvent) -> Boolean)?
    var scope: String?
    var preventDefault: Boolean?
}

// Extension functions and constants
// Extension factory functions
external fun keymap(bindings: Array<KeyBinding>): Extension
external fun drawSelection(config: dynamic = definedExternally): Extension
external fun dropCursor(config: dynamic = definedExternally): Extension
external fun highlightSpecialChars(config: dynamic = definedExternally): Extension
external fun highlightActiveLine(): Extension
external fun highlightActiveLineGutter(): Extension
external fun lineNumbers(config: dynamic = definedExternally): Extension
external fun highlightWhitespace(config: dynamic = definedExternally): Extension
external fun rectangularSelection(config: dynamic = definedExternally): Extension
external fun crosshairCursor(config: dynamic = definedExternally): Extension
external fun tooltips(config: dynamic = definedExternally): Extension
external fun showPanel(panelConstructor: (view: EditorView) -> Panel): Extension
external fun panels(config: dynamic = definedExternally): Extension

external val showTooltip: Facet<Tooltip>
