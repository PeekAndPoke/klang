package io.peekandpoke.klang.codemirror

import de.peekandpoke.kraft.utils.jsObject
import io.peekandpoke.klang.codemirror.ext.*
import io.peekandpoke.klang.feel.KlangColors

class CodeMirrorTheme {

    // ── Palette ───────────────────────────────────────────────────────────────

    val chalky get() = KlangColors.Hex.moderate
    val coral get() = KlangColors.Hex.critical
    val cyan get() = KlangColors.Hex.excellent
    val invalid = "#ffffff"
    val ivory get() = KlangColors.Hex.textPrimary
    val stone get() = KlangColors.Hex.textSecondary
    val malibu = "#61afef"
    val sage get() = KlangColors.Hex.good
    val whiskey get() = KlangColors.Hex.warning
    val violet = "#c678dd"
    val darkBackground get() = KlangColors.Hex.panelBackground
    val highlightBackground get() = KlangColors.Hex.cardBackground
    val background get() = KlangColors.Hex.appBackground
    val tooltipBackground get() = KlangColors.Hex.overlayBackground
    val selection = "#3E4451"
    val cursor get() = KlangColors.Hex.accent

    // ── Editor chrome ─────────────────────────────────────────────────────────

    val editorTheme: Extension = EditorView.theme(
        jsObject<dynamic> {
            this["&"] = jsObject<dynamic> {
                color = ivory
                backgroundColor = background
            }
            this[".cm-content"] = jsObject<dynamic> {
                caretColor = cursor
            }
            this[".cm-cursor, .cm-dropCursor"] = jsObject<dynamic> {
                borderLeftColor = cursor
            }
            this["&.cm-focused > .cm-scroller > .cm-selectionLayer .cm-selectionBackground, .cm-selectionBackground, .cm-content ::selection"] =
                jsObject<dynamic> {
                    backgroundColor = selection
                }
            this[".cm-panels"] = jsObject<dynamic> {
                backgroundColor = darkBackground
                color = ivory
            }
            this[".cm-panels.cm-panels-top"] = jsObject<dynamic> {
                borderBottom = "2px solid black"
            }
            this[".cm-panels.cm-panels-bottom"] = jsObject<dynamic> {
                borderTop = "2px solid black"
            }
            this[".cm-searchMatch"] = jsObject<dynamic> {
                backgroundColor = "#72a1ff59"
                outline = "1px solid #457dff"
            }
            this[".cm-searchMatch.cm-searchMatch-selected"] = jsObject<dynamic> {
                backgroundColor = "#6199ff2f"
            }
            this[".cm-activeLine"] = jsObject<dynamic> {
                backgroundColor = "#6699ff0b"
            }
            this[".cm-selectionMatch"] = jsObject<dynamic> {
                backgroundColor = "#aafe661a"
            }
            this["&.cm-focused .cm-matchingBracket, &.cm-focused .cm-nonmatchingBracket"] = jsObject<dynamic> {
                backgroundColor = "#bad0f847"
            }
            this[".cm-gutters"] = jsObject<dynamic> {
                backgroundColor = background
                color = KlangColors.Hex.textTertiary
                border = "none"
            }
            this[".cm-activeLineGutter"] = jsObject<dynamic> {
                backgroundColor = highlightBackground
            }
            this[".cm-foldPlaceholder"] = jsObject<dynamic> {
                backgroundColor = "transparent"
                border = "none"
                color = "#ddd"
            }
            this[".cm-tooltip"] = jsObject<dynamic> {
                border = "none"
                backgroundColor = tooltipBackground
            }
            this[".cm-tooltip .cm-tooltip-arrow:before"] = jsObject<dynamic> {
                borderTopColor = "transparent"
                borderBottomColor = "transparent"
            }
            this[".cm-tooltip .cm-tooltip-arrow:after"] = jsObject<dynamic> {
                borderTopColor = tooltipBackground
                borderBottomColor = tooltipBackground
            }
            this[".cm-tooltip-autocomplete"] = jsObject<dynamic> {
                this["& > ul > li[aria-selected]"] = jsObject<dynamic> {
                    backgroundColor = highlightBackground
                    color = ivory
                }
            }
        },
        jsObject<dynamic> {
            dark = true
        },
    )

    // ── Syntax highlighting ───────────────────────────────────────────────────

    val highlightStyle: HighlightStyle = HighlightStyle.define(
        arrayOf(
            tagStyle(tags.keyword, color = violet),
            tagStyle(tags.name, tags.deleted, tags.character, tags.propertyName, tags.macroName, color = coral),
            tagStyle(tags.function(tags.variableName), tags.labelName, color = malibu),
            tagStyle(tags.color, tags.constant(tags.name), tags.standard(tags.name), color = whiskey),
            tagStyle(tags.definition(tags.name), tags.separator, color = ivory),
            tagStyle(
                tags.typeName,
                tags.className,
                tags.number,
                tags.changed,
                tags.annotation,
                tags.modifier,
                tags.self,
                tags.namespace,
                color = chalky
            ),
            tagStyle(
                tags.operator,
                tags.operatorKeyword,
                tags.url,
                tags.escape,
                tags.regexp,
                tags.link,
                tags.special(tags.string),
                color = cyan
            ),
            tagStyle(tags.meta, tags.comment, color = stone),
            tagStyle(tags.strong, fontWeight = "bold"),
            tagStyle(tags.emphasis, fontStyle = "italic"),
            tagStyle(tags.strikethrough, textDecoration = "line-through"),
            tagStyle(tags.link, color = stone, textDecoration = "underline"),
            tagStyle(tags.heading, fontWeight = "bold", color = coral),
            tagStyle(tags.atom, tags.bool, tags.special(tags.variableName), color = whiskey),
            tagStyle(tags.processingInstruction, tags.string, tags.inserted, color = sage),
            tagStyle(tags.invalid, color = invalid),
        )
    )

    // ── Combined extension ────────────────────────────────────────────────────

    val extension: Extension = arrayOf(
        editorTheme,
        syntaxHighlighting(highlightStyle),
    ).unsafeCast<Extension>()

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun tagStyle(
        vararg tag: dynamic,
        color: String? = null,
        fontWeight: String? = null,
        fontStyle: String? = null,
        textDecoration: String? = null,
    ): TagStyle = jsObject {
        this.tag = if (tag.size == 1) tag[0] else tag
        color?.let { this.color = it }
        fontWeight?.let { this.fontWeight = it }
        fontStyle?.let { this.fontStyle = it }
        textDecoration?.let { this.textDecoration = it }
    }
}
