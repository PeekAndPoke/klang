@file:JsModule("codemirror")
@file:JsNonModule
@file:Suppress("unused")

package io.peekandpoke.klang.codemirror.ext

/**
 * Basic setup configuration
 */
external interface BasicSetupOptions {
    var lineNumbers: Boolean?
    var highlightActiveLineGutter: Boolean?
    var highlightSpecialChars: Boolean?
    var history: Boolean?
    var foldGutter: Boolean?
    var drawSelection: Boolean?
    var dropCursor: Boolean?
    var allowMultipleSelections: Boolean?
    var indentOnInput: Boolean?
    var syntaxHighlighting: Boolean?
    var bracketMatching: Boolean?
    var closeBrackets: Boolean?
    var autocompletion: Boolean?
    var rectangularSelection: Boolean?
    var crosshairCursor: Boolean?
    var highlightActiveLine: Boolean?
    var highlightSelectionMatches: Boolean?
    var closeBracketsKeymap: Boolean?
    var defaultKeymap: Boolean?
    var searchKeymap: Boolean?
    var historyKeymap: Boolean?
    var foldKeymap: Boolean?
    var completionKeymap: Boolean?
    var lintKeymap: Boolean?
}

/**
 * Basic setup - a bundle of common extensions
 * Use this to quickly set up a functional editor with common features
 */
external val basicSetup: Extension

/**
 * Minimal setup - a smaller bundle with just essential features
 */
external val minimalSetup: Extension

/**
 * Create a basic setup with custom options
 */
external fun basicSetup(options: BasicSetupOptions = definedExternally): Extension
