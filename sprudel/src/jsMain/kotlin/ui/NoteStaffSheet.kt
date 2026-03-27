package io.peekandpoke.klang.sprudel.ui

import io.peekandpoke.klang.sprudel.lang.editor.MnNodeOps
import io.peekandpoke.klang.sprudel.lang.editor.sourceRange
import io.peekandpoke.klang.sprudel.lang.parser.MnPattern
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.streams.Stream
import kotlinx.css.height
import kotlinx.css.px
import kotlinx.html.FlowContent
import kotlinx.html.div

/**
 * Renders a multi-line note staff sheet.
 *
 * Splits the source [text] on newlines and renders one [noteStaffSvg] per non-empty line,
 * passing the full [pattern] and the line's character range. This preserves original node IDs
 * so that insert targets always reference the real parsed tree.
 */
internal fun FlowContent.noteStaffSheet(
    pattern: MnPattern?,
    text: String,
    atomToPos: (String) -> Int?,
    posToValue: (Int) -> String,
    scaleName: String? = null,
    selection: MnSelection? = null,
    resolvedHighlightStream: Stream<List<ResolvedVoiceHighlight>>? = null,
    onAction: (NoteStaffEditor.Action) -> Unit = {},
) {
    if (pattern == null) return

    val allItems = MnNodeOps.collectStaffItems(pattern)
    val lines = text.split('\n')
    var lineStart = 0
    var staffRendered = false

    for (line in lines) {
        val lineEnd = lineStart + line.length

        if (line.isNotBlank()) {
            // Check if any visible atoms/rests fall within this line range
            val hasItems = allItems.any { node ->
                node.sourceRange?.first?.let { it in lineStart..lineEnd } == true
            }

            if (hasItems) {
                if (staffRendered) div { css { height = 8.px } }

                val lineRange = lineStart..lineEnd

                // Determine selection for this line
                val lineSelection = when (selection) {
                    is MnSelection.Atom -> {
                        val sr = selection.node.sourceRange?.first
                        if (sr != null && sr in lineRange) selection else null
                    }

                    is MnSelection.Rest -> {
                        val sr = selection.node.sourceRange?.first
                        if (sr != null && sr in lineRange) selection else null
                    }

                    null -> null
                }

                noteStaffSvg(
                    pattern = pattern,
                    lineRange = lineRange,
                    atomToPos = atomToPos,
                    posToValue = posToValue,
                    scaleName = scaleName,
                    selection = lineSelection,
                    resolvedHighlightStream = resolvedHighlightStream,
                    onAction = onAction,
                )

                staffRendered = true
            }
        }

        lineStart += line.length + 1
    }
}
