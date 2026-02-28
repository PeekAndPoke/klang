package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.klang.blocks.model.CodeGenResult
import io.peekandpoke.klang.script.ast.SourceLocation
import kotlinx.browser.window

/**
 * Emits [HighlightSignal]s onto a stream at the scheduled wall-clock time.
 *
 * Each block component subscribes to [highlights] and filters for its own ID,
 * so only the matching component re-renders on each event.
 */
class KlangBlocksHighlightBuffer {

    data class HighlightSignal(
        val blockId: String,
        val durationMs: Double,
        /** Non-null when the hit was inside a specific string slot. */
        val slotIndex: Int? = null,
        /** 0-based start offset of the atom within the slot string content. */
        val atomStart: Int? = null,
        /** 0-based exclusive end offset of the atom within the slot string content. */
        val atomEnd: Int? = null,
    )

    var codeGenResult: CodeGenResult? = null

    private val highlightSource = StreamSource<HighlightSignal?>(null)
    val highlights = highlightSource.readonly

    private val pendingTimeouts = mutableSetOf<Int>()

    /**
     * Schedule a highlight for the block at [location] to fire [startFromNowMs] ms from now
     * and remain visible for [durationMs] ms.
     */
    fun scheduleHighlight(location: SourceLocation, startFromNowMs: Double, durationMs: Double) {
        val hit = codeGenResult?.findAt(location.startLine, location.startColumn) ?: return

        // Compute atom end offset within the slot content (single-line atoms only).
        val atomEnd = if (hit.slotIndex != null && hit.offsetInSlot != null &&
            location.startLine == location.endLine
        ) {
            hit.offsetInSlot + (location.endColumn - location.startColumn)
        } else null

        var timeoutId = 0
        timeoutId = window.setTimeout({
            highlightSource(
                HighlightSignal(
                    blockId = hit.blockId,
                    durationMs = durationMs,
                    slotIndex = hit.slotIndex,
                    atomStart = hit.offsetInSlot,
                    atomEnd = atomEnd,
                )
            )
            pendingTimeouts.remove(timeoutId)
        }, startFromNowMs.toInt())
        pendingTimeouts.add(timeoutId)
    }

    /** Cancel all pending highlights. Already-firing highlights fade out naturally. */
    fun cancelAll() {
        for (id in pendingTimeouts) window.clearTimeout(id)
        pendingTimeouts.clear()
    }
}
