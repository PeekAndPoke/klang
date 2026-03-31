@file:JsModule("@lezer/highlight")
@file:JsNonModule
@file:Suppress("unused")

package io.peekandpoke.klang.codemirror.ext

/**
 * Highlighting tags from @lezer/highlight.
 * Used with HighlightStyle.define() to map syntax tokens to colors.
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

    // Tag modifier functions — wrap a base tag to produce a more specific one
    fun function(tag: dynamic): dynamic
    fun constant(tag: dynamic): dynamic
    fun standard(tag: dynamic): dynamic
    fun definition(tag: dynamic): dynamic
    fun special(tag: dynamic): dynamic
}
