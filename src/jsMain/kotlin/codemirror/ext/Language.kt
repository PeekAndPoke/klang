@file:JsModule("@codemirror/language")
@file:JsNonModule
@file:Suppress("unused")

package io.peekandpoke.klang.codemirror.ext

/**
 * Language configuration
 */
external class Language {
    val name: String
    val parser: Parser
    val extension: Extension

    companion object {
        fun define(spec: LanguageSpec): Language
    }
}

/**
 * Language specification
 */
external interface LanguageSpec {
    var name: String?
    var parser: Parser
    var languageData: dynamic
}

/**
 * Parser - parses the document into a syntax tree
 */
external interface Parser {
    fun parse(
        input: String,
        fragments: Array<TreeFragment> = definedExternally,
        ranges: Array<dynamic> = definedExternally,
    ): Tree

    fun createParse(
        input: String,
        fragments: Array<TreeFragment> = definedExternally,
        ranges: Array<dynamic> = definedExternally,
    ): PartialParse
}

/**
 * Syntax tree
 */
external class Tree {
    val length: Int
    val type: NodeType

    fun cursor(mode: Int = definedExternally): TreeCursor
    fun topNode(): SyntaxNode
    fun resolve(pos: Int, side: Int = definedExternally): SyntaxNode
}

/**
 * Node type in the syntax tree
 */
external class NodeType {
    val name: String
    val id: Int
    val isTop: Boolean
    val isError: Boolean
}

/**
 * Syntax node
 */
external interface SyntaxNode {
    val type: NodeType
    val from: Int
    val to: Int
    val name: String
    val tree: Tree

    fun resolve(pos: Int, side: Int = definedExternally): SyntaxNode
    fun cursor(mode: Int = definedExternally): TreeCursor
    fun firstChild(): SyntaxNode?
    fun lastChild(): SyntaxNode?
    fun childAfter(pos: Int): SyntaxNode?
    fun childBefore(pos: Int): SyntaxNode?
    fun parent(): SyntaxNode?
    fun nextSibling(): SyntaxNode?
    fun prevSibling(): SyntaxNode?
}

/**
 * Tree cursor for traversing the syntax tree
 */
external interface TreeCursor {
    val type: NodeType
    val from: Int
    val to: Int
    val name: String

    fun next(enter: Boolean = definedExternally): Boolean
    fun prev(enter: Boolean = definedExternally): Boolean
    fun parent(): Boolean
    fun firstChild(): Boolean
    fun lastChild(): Boolean
    fun nextSibling(): Boolean
    fun prevSibling(): Boolean
}

/**
 * Tree fragment - reusable piece of a syntax tree
 */
external interface TreeFragment {
    val from: Int
    val to: Int
    val tree: Tree
    val offset: Int
}

/**
 * Partial parse - incremental parsing
 */
external interface PartialParse {
    val parsedPos: Int
    fun advance(): Tree?
    fun stopAt(pos: Int)
}

/**
 * Language data - metadata about a language
 */
external interface LanguageData {
    var commentTokens: CommentTokens?
    var autocomplete: ((context: CompletionContext) -> CompletionResult?)?
    var wordChars: String?
}

/**
 * Comment tokens for a language
 */
external interface CommentTokens {
    var line: String?
    var block: BlockCommentTokens?
}

external interface BlockCommentTokens {
    var open: String
    var close: String
}

/**
 * Completion context
 */
external interface CompletionContext {
    val state: EditorState
    val pos: Int
    val explicit: Boolean
    val aborted: Boolean

    fun matchBefore(regexp: dynamic): dynamic
    val abortListeners: Array<() -> Unit>
}

/**
 * Completion result
 */
external interface CompletionResult {
    var from: Int
    var to: Int?
    var options: Array<Completion>
    var span: dynamic
    var filter: Boolean?
    var validFor: dynamic
}

/**
 * Single completion
 */
external interface Completion {
    var label: String
    var detail: String?
    var info: Any? // string or function
    var apply: Any? // string or function
    var type: String?
    var boost: Int?
}

/**
 * Syntax highlighting
 */
external class HighlightStyle {
    val extension: Extension

    companion object {
        fun define(specs: Array<TagStyle>, options: dynamic = definedExternally): HighlightStyle
    }
}

/**
 * Tag style for highlighting
 */
external interface TagStyle {
    var tag: Any // Tag or Array<Tag>
    var `class`: String?
    var color: String?
    var fontWeight: String?
    var fontStyle: String?
    var textDecoration: String?
}

/**
 * Highlighting tags
 */
external val tags: Tags

external interface Tags {
    val comment: dynamic
    val lineComment: dynamic
    val blockComment: dynamic
    val docComment: dynamic
    val name: dynamic
    val variableName: dynamic
    val typeName: dynamic
    val tagName: dynamic
    val propertyName: dynamic
    val attributeName: dynamic
    val className: dynamic
    val labelName: dynamic
    val namespace: dynamic
    val macroName: dynamic
    val literal: dynamic
    val string: dynamic
    val docString: dynamic
    val character: dynamic
    val attributeValue: dynamic
    val number: dynamic
    val integer: dynamic
    val float: dynamic
    val bool: dynamic
    val regexp: dynamic
    val escape: dynamic
    val color: dynamic
    val url: dynamic
    val keyword: dynamic
    val self: dynamic
    val `null`: dynamic
    val atom: dynamic
    val unit: dynamic
    val modifier: dynamic
    val operatorKeyword: dynamic
    val controlKeyword: dynamic
    val definitionKeyword: dynamic
    val moduleKeyword: dynamic
    val operator: dynamic
    val derefOperator: dynamic
    val arithmeticOperator: dynamic
    val logicOperator: dynamic
    val bitwiseOperator: dynamic
    val compareOperator: dynamic
    val updateOperator: dynamic
    val definitionOperator: dynamic
    val typeOperator: dynamic
    val controlOperator: dynamic
    val punctuation: dynamic
    val separator: dynamic
    val bracket: dynamic
    val angleBracket: dynamic
    val squareBracket: dynamic
    val paren: dynamic
    val brace: dynamic
    val content: dynamic
    val heading: dynamic
    val heading1: dynamic
    val heading2: dynamic
    val heading3: dynamic
    val heading4: dynamic
    val heading5: dynamic
    val heading6: dynamic
    val contentSeparator: dynamic
    val list: dynamic
    val quote: dynamic
    val emphasis: dynamic
    val strong: dynamic
    val link: dynamic
    val monospace: dynamic
    val strikethrough: dynamic
    val inserted: dynamic
    val deleted: dynamic
    val changed: dynamic
    val invalid: dynamic
    val meta: dynamic
    val documentMeta: dynamic
    val annotation: dynamic
    val processingInstruction: dynamic
}

// Language support extensions
external fun syntaxHighlighting(highlightStyle: HighlightStyle, options: dynamic = definedExternally): Extension
external val defaultHighlightStyle: HighlightStyle
external fun syntaxTree(state: EditorState): Tree
external fun ensureSyntaxTree(state: EditorState, upto: Int, timeout: Int = definedExternally): Tree?

// Language description
external class LanguageDescription {
    val name: String
    val alias: Array<String>
    val extensions: Array<String>
    val filename: dynamic // RegExp
    val load: () -> dynamic // Promise<LanguageSupport>

    companion object {
        fun of(spec: LanguageDescriptionSpec): LanguageDescription
        fun matchFilename(descs: Array<LanguageDescription>, filename: String): LanguageDescription?
        fun matchLanguageName(
            descs: Array<LanguageDescription>,
            name: String,
            fuzzy: Boolean = definedExternally,
        ): LanguageDescription?
    }
}

external interface LanguageDescriptionSpec {
    var name: String
    var alias: Array<String>?
    var extensions: Array<String>?
    var filename: dynamic // RegExp
    var load: (() -> dynamic)? // () => Promise<LanguageSupport>
    var support: LanguageSupport?
}

/**
 * Language support - combines language with extensions
 */
external class LanguageSupport {
    constructor(language: Language, support: Array<Extension> = definedExternally)

    val extension: Extension
    val language: Language
}

// Folding
external val foldable: Facet<Any>
external val foldService: Facet<Any>
external fun foldGutter(config: dynamic = definedExternally): Extension
external fun codeFolding(config: dynamic = definedExternally): Extension

// Indentation
external val indentUnit: Facet<String>
external fun indentOnInput(): Extension
external fun indentString(state: EditorState, cols: Int): String
external fun getIndentUnit(state: EditorState): Int
external fun indentRange(state: EditorState, from: Int, to: Int): Array<dynamic>

// Bracket matching
external fun bracketMatching(config: dynamic = definedExternally): Extension
