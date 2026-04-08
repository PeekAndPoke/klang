package io.peekandpoke.klang.ui.codemirror

import io.peekandpoke.klang.codemirror.ext.EditorView
import io.peekandpoke.klang.codemirror.ext.Extension
import io.peekandpoke.klang.codemirror.ext.HighlightStyle
import io.peekandpoke.klang.codemirror.ext.TagStyle
import io.peekandpoke.klang.codemirror.ext.syntaxHighlighting
import io.peekandpoke.klang.codemirror.ext.tags
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.utils.jsObject

class CodeMirrorTheme {

    // ── Palette ───────────────────────────────────────────────────────────────

    val chalky get() = KlangTheme.Hex.moderate
    val coral get() = KlangTheme.Hex.critical
    val cyan get() = KlangTheme.Hex.excellent
    val invalid = "#ffffff"
    val ivory get() = KlangTheme.Hex.textPrimary
    val stone get() = KlangTheme.Hex.textSecondary
    val malibu = "#61afef"
    val sage get() = KlangTheme.Hex.good
    val whiskey get() = KlangTheme.Hex.warning
    val violet = "#c678dd"
    val darkBackground get() = KlangTheme.Hex.panelBackground
    val highlightBackground get() = KlangTheme.Hex.cardBackground
    val background get() = KlangTheme.Hex.appBackground
    val tooltipBackground get() = KlangTheme.Hex.overlayBackground
    val selection get() = KlangTheme.Hex.gold
    val cursor get() = KlangTheme.Hex.accent

    // ── Editor chrome ─────────────────────────────────────────────────────────

    val editorTheme: Extension = EditorView.theme(
        jsObject {
            this["&"] = jsObject {
                color = ivory
                backgroundColor = background
            }
            this[".cm-content"] = jsObject {
                caretColor = cursor
                textShadow = "0px 0px 2px rgba(20,0,40,1.0)"
            }
            this[".cm-cursor, .cm-dropCursor"] = jsObject {
                borderLeftColor = "#ffffff"
                borderLeftWidth = "2px"
            }
            // Selection background — focused editor
            this["&.cm-focused > .cm-scroller > .cm-selectionLayer .cm-selectionBackground"] =
                jsObject {
                    backgroundColor = "${KlangTheme.Hex.accent}60"
                }
            // Selection background — unfocused editor (dimmer)
            this[".cm-selectionBackground"] = jsObject {
                backgroundColor = "${KlangTheme.Hex.accent}30"
            }
            // Native text selection
            this[".cm-content ::selection"] = jsObject {
                backgroundColor = "${KlangTheme.Hex.accent}60"
            }
            this[".cm-panels"] = jsObject {
                backgroundColor = darkBackground
                color = ivory
            }
            this[".cm-panels.cm-panels-top"] = jsObject {
                borderBottom = "2px solid black"
            }
            this[".cm-panels.cm-panels-bottom"] = jsObject {
                borderTop = "2px solid black"
            }
            this[".cm-searchMatch"] = jsObject {
                backgroundColor = "#72a1ff59"
                outline = "1px solid #457dff"
            }
            this[".cm-searchMatch.cm-searchMatch-selected"] = jsObject {
                backgroundColor = "#6199ff2f"
            }
            this[".cm-activeLine"] = jsObject {
                backgroundColor = "#6699ff0b"
            }
            this[".cm-selectionMatch"] = jsObject {
                backgroundColor = "${selection}40"
            }
            this["&.cm-focused .cm-matchingBracket, &.cm-focused .cm-nonmatchingBracket"] = jsObject {
                backgroundColor = "#bad0f847"
            }
            this[".cm-gutters"] = jsObject {
                backgroundColor = background
                color = KlangTheme.Hex.textTertiary
                borderRight = "1px solid ${KlangTheme.Hex.textTertiary}40"
            }
            this[".cm-activeLineGutter"] = jsObject {
                backgroundColor = highlightBackground
            }
            this[".cm-foldPlaceholder"] = jsObject {
                backgroundColor = "transparent"
                border = "none"
                color = "#ddd"
            }
            this[".cm-tooltip"] = jsObject {
                border = "1px solid ${KlangTheme.Hex.textTertiary}"
                borderRadius = "4px"
                backgroundColor = tooltipBackground
            }
            this[".cm-tooltip .cm-tooltip-arrow:before"] = jsObject {
                borderTopColor = "transparent"
                borderBottomColor = "transparent"
            }
            this[".cm-tooltip .cm-tooltip-arrow:after"] = jsObject {
                borderTopColor = tooltipBackground
                borderBottomColor = tooltipBackground
            }
            this[".cm-tooltip-autocomplete"] = jsObject {
                this["& > ul > li"] = jsObject {
                    color = stone
                }
                this["& > ul > li[aria-selected]"] = jsObject {
                    backgroundColor = highlightBackground
                    color = ivory
                }
            }
            this[".cm-completionInfo"] = jsObject {
                color = stone
                padding = "8px 12px"
            }

            // ── Lint / Diagnostics ──────────────────────────────────────
            this[".cm-lintRange-error"] = jsObject {
                backgroundImage = "none"
                textDecoration = "underline wavy $coral"
                textDecorationSkipInk = "none"
                textUnderlineOffset = "3px"
            }
            this[".cm-lintRange-warning"] = jsObject {
                backgroundImage = "none"
                textDecoration = "underline wavy $whiskey"
                textDecorationSkipInk = "none"
                textUnderlineOffset = "3px"
            }
            this[".cm-lint-marker-error"] = jsObject {
                content = "none"
                color = coral
            }
            this[".cm-lint-marker-warning"] = jsObject {
                content = "none"
                color = whiskey
            }
            this[".cm-panel.cm-panel-lint"] = jsObject {
                backgroundColor = darkBackground
                borderTop = "1px solid ${KlangTheme.Hex.textTertiary}"
                // Only highlight the selected item when the list actually has focus
                this["& ul:focus li.cm-diagnostic[aria-selected=true], " +
                        "& ul:focus-within li.cm-diagnostic[aria-selected=true]"] = jsObject {
                    backgroundColor = "${KlangTheme.Hex.accent}33"
                    color = ivory
                }
                // Override the default blue when list is NOT focused
                this["& ul:not(:focus):not(:focus-within) li.cm-diagnostic[aria-selected=true]"] = jsObject {
                    backgroundColor = "transparent"
                }
                this["& button"] = jsObject {
                    backgroundColor = "transparent"
                    color = stone
                    border = "1px solid ${KlangTheme.Hex.textTertiary}"
                    borderRadius = "3px"
                    this["cursor"] = "pointer"
                }
            }
            this[".cm-diagnostic-error"] = jsObject {
                borderLeftColor = coral
                color = stone
            }
            this[".cm-diagnostic-warning"] = jsObject {
                borderLeftColor = whiskey
                color = stone
            }
            this[".cm-lintPoint-error::after"] = jsObject {
                borderBottomColor = coral
            }
            this[".cm-lintPoint-warning::after"] = jsObject {
                borderBottomColor = whiskey
            }
        },
        jsObject {
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
