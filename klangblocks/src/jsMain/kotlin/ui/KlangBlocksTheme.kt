package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.kraft.addons.styling.StyleSheet
import kotlinx.css.*

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
        val newlineAction by rule {
            cursor = Cursor.pointer
            border = Border(1.px, BorderStyle.solid, Color(pillBorder))
            borderRadius = 16.px
            padding = Padding(top = 3.px, bottom = 3.px, left = 4.px, right = 0.px)
            backgroundColor = Color(pillBackground).withAlpha(0.9)
            color = Color(pillText)
            userSelect = UserSelect.none
        }
    }

    /** Returns the block background color for the given category (falls back to [blockDefaultColor]). */
    fun blockColor(category: String?): String =
        blockCategoryColors[category] ?: blockDefaultColor

    val styles = Styles()
}
