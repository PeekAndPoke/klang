package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.kraft.addons.styling.StyleSheet
import kotlinx.css.*
import kotlinx.css.properties.LineHeight

data class KlangBlocksTheme(

    // ── Structural backgrounds ──────────────────────────────────────────────
    val canvasBackground: String = "#1e1e2e",
    val paletteBackground: String = "#252535",

    // ── Dividers / chrome ───────────────────────────────────────────────────
    val dividerColor: String = "#333",

    // ── Text & icon hierarchy ───────────────────────────────────────────────
    val textPrimary: String = "#ffffff",
    val textMuted: String = "#aaa",        // secondary labels, stmt-pill text
    val textFaint: String = "#888",        // faint icons, connector hints
    val textSubdued: String = "#666",      // row numbers, remove-btn rest
    val textDisabled: String = "#555",     // empty-state copy

    // ── Block category colors ───────────────────────────────────────────────
    val blockDefaultColor: String = "#555",
    val blockCategoryColors: Map<String, String> = mapOf(
        "synthesis" to "#4a6fa5",
        "sample" to "#3a8a4a",
        "effects" to "#3a7a3a",
        "tempo" to "#8a7a20",
        "structural" to "#7a3a8a",
        "random" to "#8a3a20",
        "tonal" to "#4a3a8a",
        "continuous" to "#2a7a7a",
        "filters" to "#2a6a3a",
    ),

    // ── Chain connectors ────────────────────────────────────────────────────
    val connectorColor: String = "#888",

    // ── Inline items (string literals, identifier heads) ────────────────────
    val inlineItemBackground: String = "rgba(0,0,0,0.25)",
    val inlineItemBorder: String = "rgba(255,255,255,0.2)",
    val inlineItemText: String = "rgba(255,255,255,0.85)",
    val inlineItemHoverBackground: String = "rgba(255,255,255,0.15)",

    // ── Argument slots (within blocks) ──────────────────────────────────────
    val slotBackground: String = "rgba(0,0,0,0.2)",
    val slotHoverBackground: String = "rgba(255,255,255,0.15)",
    val slotDropBackground: String = "rgba(255,255,255,0.08)",
    val slotDropBorder: String = "rgba(255,255,255,0.5)",
    val slotDropHoverBackground: String = "rgba(255,255,255,0.45)",

    // ── Inline text edit inputs ─────────────────────────────────────────────
    val inputBackground: String = "rgba(0,0,0,0.4)",
    val inputBackgroundIdle: String = "rgba(0,0,0,0.2)",
    val inputBorder: String = "rgba(255,255,255,0.4)",

    // ── Block drop-target outlines ──────────────────────────────────────────
    val blockDropHoverOutline: String = "rgba(255,255,255,0.85)",
    val blockDropIdleOutline: String = "rgba(255,255,255,0.5)",

    // ── Inline drop zones (insert indicators between/around blocks) ──────────
    val dropZoneBackground: String = "#666",
    val dropZoneBackgroundHover: String = "#888",
    val dropZoneBorder: String = "#BBB",
    val dropZoneIcon: String = "#DDD",

    // ── Overlay pills (toggle-newline button) ───────────────────────────────
    val pillBackground: String = "#666",
    val pillBorder: String = "#BBB",
    val pillText: String = "#BBB",

    // ── Row-level controls ──────────────────────────────────────────────────
    val rowNumberColor: String = "#666",
    val rowNumberHoverColor: String = "#aaa",
    val rowInsertColor: String = "#888",
    val rowDropLineIdle: String = "rgba(106,159,216,0.35)",
    val rowDropLineHover: String = "#6a9fd8",

    // ── Statement pills (import / let / const in canvas) ────────────────────
    val stmtPillBackground: String = "#333",
    val stmtPillText: String = "#aaa",
    val stmtPillRemoveText: String = "#555",
    val stmtPillRemoveHoverBackground: String = "rgba(255,255,255,0.08)",
    val stmtPillRemoveHoverText: String = "#ccc",

    // ── Inline action buttons (e.g. × inside let/const blocks) ─────────────
    val blockActionText: String = "rgba(255,255,255,0.55)",
    val blockActionHoverBackground: String = "rgba(255,255,255,0.18)",

    // ── Highlight pulse ─────────────────────────────────────────────────────
    val highlightShadow: String = "rgba(255,255,255,0.65)",

    // ── Drag ghost ──────────────────────────────────────────────────────────
    val dragGhostShadow: String = "rgba(0,0,0,0.4)",

    ) {
    companion object {
        val Default = KlangBlocksTheme()
    }

    inner class Styles : StyleSheet("klang-blocks-theme") {

        // ── Newline-toggle pill ──────────────────────────────────────────────
        val newlineAction by rule {
            cursor = Cursor.pointer
            border = Border(1.px, BorderStyle.solid, Color(pillBorder))
            borderRadius = 16.px
            padding = Padding(top = 3.px, bottom = 3.px, left = 4.px, right = 0.px)
            backgroundColor = Color(pillBackground).withAlpha(0.9)
            color = Color(pillText)
            userSelect = UserSelect.none
        }

        // ── Statement pills (import / let / const / assign in canvas) ────────
        val stmtPill by rule {
            backgroundColor = Color(stmtPillBackground)
            color = Color(stmtPillText)
            padding = Padding(vertical = 4.px, horizontal = 8.px)
            borderRadius = 4.px
            fontFamily = "monospace"
            fontSize = 12.px
        }

        // ── × remove button next to stmt pills ───────────────────────────────
        val stmtPillRemoveBtn by rule {
            fontSize = 13.px
            color = Color(stmtPillRemoveText)
            cursor = Cursor.pointer
            borderRadius = 3.px
            padding = Padding(horizontal = 4.px, vertical = 2.px)
            hover {
                backgroundColor = Color(stmtPillRemoveHoverBackground)
                color = Color(stmtPillRemoveHoverText)
            }
        }

        // ── Row number gutter ────────────────────────────────────────────────
        val rowNumber by rule {
            color = Color(rowNumberColor)
            fontSize = 11.px
            fontFamily = "monospace"
            width = 24.px
            flexShrink = 0.0
            textAlign = TextAlign.right
        }

        val rowNumberDraggable by rule {
            cursor = Cursor.grab
            hover {
                color = Color(rowNumberHoverColor)
            }
        }

        // ── Inline items (identifier head, string literal display) ───────────
        val inlineItem by rule {
            borderRadius = 3.px
            padding = Padding(horizontal = 4.px, vertical = 1.px)
            fontSize = 11.px
            backgroundColor = Color(inlineItemBackground)
            border = Border(1.px, BorderStyle.solid, Color(inlineItemBorder))
            color = Color(inlineItemText)
            fontFamily = "monospace"
            whiteSpace = WhiteSpace.nowrap
            cursor = Cursor.text
            hover {
                backgroundColor = Color(inlineItemHoverBackground)
            }
        }

        // ── Arg value slot — normal (not editing, not drop target) ───────────
        val valueSlot by rule {
            backgroundColor = Color(slotBackground)
            border = Border(1.px, BorderStyle.solid, Color.transparent)
            cursor = Cursor.text
            hover {
                backgroundColor = Color(slotHoverBackground)
            }
        }

        // ── Arg value slot — drop target ──────────────────────────────────────
        val valueSlotDrop by rule {
            backgroundColor = Color(slotDropBackground)
            border = Border(1.px, BorderStyle.dashed, Color(slotDropBorder))
            cursor = Cursor.copy
            hover {
                backgroundColor = Color(slotDropHoverBackground)
                border = Border(1.px, BorderStyle.solid, Color(textPrimary))
            }
        }

        // ── Nested chain slot — drop target ───────────────────────────────────
        val nestedSlotDrop by rule {
            border = Border(1.px, BorderStyle.dashed, Color(slotDropBorder))
            cursor = Cursor.copy
            hover {
                backgroundColor = Color(slotDropHoverBackground)
            }
        }

        // ── Block hover-action buttons (layout toggle, remove) ────────────────
        val blockHoverActionBtn by rule {
            lineHeight = LineHeight("1")
            color = Color(textPrimary)
            cursor = Cursor.pointer
            borderRadius = 3.px
        }

        // ── Let/const inline action button (× remove) ────────────────────────
        val blockActionBtn by rule {
            lineHeight = LineHeight("1")
            color = Color(blockActionText)
            cursor = Cursor.pointer
            borderRadius = 3.px
            padding = Padding(horizontal = 3.px, vertical = 1.px)
            hover {
                backgroundColor = Color(blockActionHoverBackground)
                color = Color(textPrimary)
            }
        }

        // ── Chain connector primitives ────────────────────────────────────────
        val connectorDot by rule {
            width = 6.px
            height = 6.px
            borderRadius = 50.pct
            backgroundColor = Color(connectorColor)
            flexShrink = 0.0
        }

        val connectorSolidLine by rule {
            flexGrow = 1.0
            height = 2.px
            backgroundColor = Color(connectorColor)
            flexShrink = 0.0
        }

        val connectorDashedLine by rule {
            flexGrow = 1.0
            height = 0.px
            borderTop = Border(2.px, BorderStyle.dotted, Color(connectorColor))
        }

        // ── Editor shell ──────────────────────────────────────────────────────
        val editorRoot by rule {
            display = Display.flex
            flexDirection = FlexDirection.row
            width = 100.pct
            height = 100.pct
            position = Position.relative
            overflow = Overflow.hidden
            backgroundColor = Color(canvasBackground)
        }

        val dragGhostFixed by rule {
            position = Position.fixed
            pointerEvents = PointerEvents.none
            put("z-index", "9999")
            opacity = 0.85
            put("transform", "translate(-50%, -50%)")
        }

        val dragGhostLabel by rule {
            display = Display.inlineBlock
            color = Color(textPrimary)
            borderRadius = 8.px
            padding = Padding(vertical = 5.px, horizontal = 10.px)
            fontSize = 13.px
            fontFamily = "monospace"
            fontWeight = FontWeight.bold
            whiteSpace = WhiteSpace.nowrap
            put("box-shadow", "0 4px 12px $dragGhostShadow")
        }

        // ── Canvas ────────────────────────────────────────────────────────────
        val canvasContainer by rule {
            flex = Flex(1.0, 1.0, FlexBasis.auto)
            overflowY = Overflow.auto
            overflowX = Overflow.auto
            padding = Padding(16.px)
            backgroundColor = Color(canvasBackground)
            minHeight = 400.px
        }

        val canvasEmptyState by rule {
            display = Display.flex
            alignItems = Align.center
            justifyContent = JustifyContent.center
            height = 200.px
            color = Color(textDisabled)
            fontSize = 16.px
        }

        val canvasRow by rule {
            display = Display.flex
            flexDirection = FlexDirection.row
            alignItems = Align.flexStart
            gap = 8.px
        }

        val chainSegmentColumn by rule {
            display = Display.flex
            flexDirection = FlexDirection.column
            gap = 4.px
        }

        val blankLine by rule {
            display = Display.inlineBlock
            height = 16.px
        }

        // ── Palette ───────────────────────────────────────────────────────────
        val paletteContainer by rule {
            width = 200.px
            flexShrink = 0.0
            backgroundColor = Color(paletteBackground)
            put("border-right", "1px solid $dividerColor")
            display = Display.flex
            flexDirection = FlexDirection.column
        }

        val paletteToolbar by rule {
            display = Display.flex
            flexDirection = FlexDirection.row
            alignItems = Align.center
            gap = 4.px
            padding = Padding(6.px)
            put("border-bottom", "1px solid $dividerColor")
        }

        val paletteToolbarBtn by rule {
            padding = Padding(4.px)
            borderRadius = 4.px
        }

        val paletteSection by rule {
            padding = Padding(8.px)
            flexShrink = 0.0
            put("border-bottom", "1px solid $dividerColor")
        }

        val paletteImportLabel by rule {
            color = Color(textFaint)
            fontSize = 10.px
            fontWeight = FontWeight.bold
            textTransform = TextTransform.uppercase
            letterSpacing = LinearDimension("0.08em")
            marginBottom = 6.px
        }

        val paletteImportBtn by rule {
            display = Display.inlineFlex
            alignItems = Align.center
            gap = 4.px
            backgroundColor = Color(stmtPillBackground)
            color = Color(stmtPillText)
            borderRadius = 4.px
            padding = Padding(vertical = 3.px, horizontal = 8.px)
            marginBottom = 3.px
            fontSize = 12.px
            fontFamily = "monospace"
            cursor = Cursor.pointer
            userSelect = UserSelect.none
        }

        val paletteSearchInput by rule {
            width = 100.pct
            backgroundColor = Color(canvasBackground)
            color = Color(textMuted)
            border = Border(1.px, BorderStyle.solid, Color(dividerColor))
            borderRadius = 4.px
            padding = Padding(vertical = 4.px, horizontal = 6.px)
            fontSize = 12.px
            outline = Outline.none
            put("box-sizing", "border-box")
        }

        val paletteBlockList by rule {
            flex = Flex(1.0, 1.0, FlexBasis.auto)
            overflowY = Overflow.auto
            padding = Padding(8.px)
        }

        val paletteCategoryHeader by rule {
            fontSize = 10.px
            fontWeight = FontWeight.bold
            textTransform = TextTransform.uppercase
            letterSpacing = LinearDimension("0.08em")
            padding = Padding(vertical = 6.px, horizontal = 4.px)
            marginTop = 4.px
        }

        val paletteBlockItem by rule {
            display = Display.block
            color = Color(textPrimary)
            borderRadius = 6.px
            padding = Padding(vertical = 4.px, horizontal = 8.px)
            marginBottom = 3.px
            fontSize = 12.px
            fontFamily = "monospace"
            cursor = Cursor.grab
            userSelect = UserSelect.none
            whiteSpace = WhiteSpace.nowrap
            overflow = Overflow.hidden
            textOverflow = TextOverflow.ellipsis
        }

        // ── Block container base (static properties shared by all block variants) ──
        val blockBase by rule {
            display = Display.inlineFlex
            color = Color(textPrimary)
            fontFamily = "monospace"
            whiteSpace = WhiteSpace.nowrap
            userSelect = UserSelect.none
            flexShrink = 0.0
            position = Position.relative
            put("transition", "filter 0.15s ease")
        }

        // ── Identifier arg "$" prefix ─────────────────────────────────────────
        val identifierDollarSign by rule {
            opacity = 0.85
        }

        // ── Block elements ────────────────────────────────────────────────────
        val blockFuncName by rule {
            minWidth = 30.px
            fontWeight = FontWeight.bold
        }

        val blockHoverActions by rule {
            display = Display.inlineFlex
            alignItems = Align.center
            gap = 2.px
            position = Position.absolute
            border = Border(1.px, BorderStyle.dotted, Color(blockDropIdleOutline))
            borderRadius = 6.px
            padding = Padding(2.px, 4.px, 2.px, 8.px)
            right = 0.px
        }

        val highlightAtom by rule {
            borderRadius = 2.px
            put("box-shadow", "0 0 0 2px $highlightShadow")
        }

        // ── Let/const statement block ─────────────────────────────────────────
        val letStmtBlock by rule {
            display = Display.inlineFlex
            flexDirection = FlexDirection.row
            alignItems = Align.center
            backgroundColor = Color(blockColor("structural"))
            color = Color(textPrimary)
            fontFamily = "monospace"
            whiteSpace = WhiteSpace.nowrap
            userSelect = UserSelect.none
            flexShrink = 0.0
            position = Position.relative
            cursor = Cursor.grab
            put("transition", "filter 0.15s ease")
        }

        val letStmtHoverRemove by rule {
            display = Display.inlineFlex
            alignItems = Align.center
            position = Position.absolute
            top = (-8).px
            right = 0.px
            backgroundColor = Color(blockColor("structural"))
            borderTopRightRadius = 8.px
            borderTopLeftRadius = 8.px
            borderBottomLeftRadius = 6.px
            padding = Padding(2.px, 4.px)
        }

        // ── Row gap ───────────────────────────────────────────────────────────
        val rowGapContainer by rule {
            height = 10.px
            position = Position.relative
        }

        val rowInsertIcon by rule {
            position = Position.absolute
            top = (-13).px
            left = 4.px
            width = 28.px
            height = 28.px
            display = Display.flex
            alignItems = Align.center
            cursor = Cursor.pointer
            color = Color(rowInsertColor)
        }

        val rowGapDropZone by rule {
            position = Position.absolute
            top = (-10).px
            bottom = (-10).px
            left = 28.px
            right = 0.px
            display = Display.flex
            alignItems = Align.center
            cursor = Cursor.copy
            put("z-index", "10")
        }

        val rowDropLine by rule {
            width = 100.pct
            height = 3.px
            borderRadius = 2.px
            put("transition", "background-color 0.1s ease")
        }

        // ── Drop zone ─────────────────────────────────────────────────────────
        val appendConnectorContainer by rule {
            display = Display.inlineFlex
            alignItems = Align.center
            justifyContent = JustifyContent.center
            flexShrink = 0.0
            width = 24.px
            height = 24.px
            marginLeft = 6.px
            alignSelf = Align.center
            position = Position.relative
        }

        val hoverActionsOverlay by rule {
            position = Position.absolute
            alignSelf = Align.center
        }

        val dropZoneIconCls by rule {
            color = Color(dropZoneIcon)
            margin = Margin(0.px)
            put("transition", "color 0.1s ease")
        }

        val appendDropIndicatorPos by rule {
            position = Position.absolute
            left = 50.pct
            top = 50.pct
            pointerEvents = PointerEvents.none
            put("transform", "translate(-50%, -50%)")
        }

        // ── Chain segment rendering ───────────────────────────────────────────
        val segmentBreakConnector by rule {
            display = Display.inlineFlex
            alignItems = Align.center
            width = 36.px
            flexShrink = 0.0
            cursor = Cursor.pointer
            marginLeft = (-10).px
            position = Position.relative
            put("z-index", "1")
        }

        // ── String literal inline widget (textarea) ──────────────────────────
        /** Shared structural rule for both display and edit modes of the string literal widget. */
        val stringLiteralInline by rule {
            display = Display.inlineBlock
            borderRadius = 3.px
            padding = Padding(horizontal = 4.px, vertical = 1.px)
            fontSize = 12.px
            lineHeight = LineHeight("1.8")
            whiteSpace = WhiteSpace.preWrap
            minWidth = 30.px
            outline = Outline.none
            verticalAlign = VerticalAlign.middle
            appearance = Appearance.none
            overflow = Overflow.hidden
            put("-webkit-appearance", "none")
            put("field-sizing", "content")
        }
    }

    /** Returns the block background color for the given category (falls back to [blockDefaultColor]). */
    fun blockColor(category: String?): String =
        blockCategoryColors[category] ?: blockDefaultColor

    val styles = Styles()
}
