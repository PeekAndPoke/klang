@file:JsModule("@codemirror/lang-javascript")
@file:JsNonModule
@file:Suppress("unused")

package io.peekandpoke.klang.codemirror.ext

/**
 * JavaScript language configuration
 */
external interface JavascriptConfig {
    var jsx: Boolean?
    var typescript: Boolean?
}

/**
 * JavaScript language support
 * Returns a LanguageSupport instance for JavaScript
 */
external fun javascript(config: JavascriptConfig = definedExternally): LanguageSupport

/**
 * TypeScript language support
 * Convenience function - same as javascript({ typescript: true })
 */
external fun typescript(config: JavascriptConfig = definedExternally): LanguageSupport

/**
 * JSX language support
 * Convenience function - same as javascript({ jsx: true })
 */
external fun jsx(config: JavascriptConfig = definedExternally): LanguageSupport

/**
 * TSX language support
 * Convenience function - same as javascript({ typescript: true, jsx: true })
 */
external fun tsx(config: JavascriptConfig = definedExternally): LanguageSupport

/**
 * JavaScript language (without support extensions)
 */
external val javascriptLanguage: Language

/**
 * TypeScript language (without support extensions)
 */
external val typescriptLanguage: Language

/**
 * JSX snippets - common JSX code snippets for autocompletion
 */
external val jsxSnippets: Array<dynamic>

/**
 * TypeScript snippets - common TypeScript code snippets for autocompletion
 */
external val typescriptSnippets: Array<dynamic>

/**
 * Scope completion source - provides completions based on scope
 */
external val scopeCompletionSource: (context: dynamic) -> dynamic

/**
 * Local completion source - provides completions from local scope
 */
external val localCompletionSource: (context: dynamic) -> dynamic

/**
 * Snippet completion - enables snippet-style completions
 */
external val snippetCompletion: (template: String, completion: dynamic) -> dynamic

/**
 * Auto-close tags for JSX
 */
external val autoCloseTags: Extension

/**
 * ES Lint integration
 */
external interface ESLintConfig {
    var parserOptions: dynamic
    var env: dynamic
    var rules: dynamic
}

external fun esLint(eslintInstance: dynamic, config: ESLintConfig = definedExternally): Extension
