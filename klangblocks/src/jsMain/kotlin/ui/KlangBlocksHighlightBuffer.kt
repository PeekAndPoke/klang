package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.klang.blocks.model.CodeGenResult
import io.peekandpoke.klang.script.ast.SourceLocation
import kotlinx.browser.window

data class HighlightSignal(
    val blockId: String,
    val durationMs: Double,
)

/**
 * Emits [HighlightSignal]s onto a stream at the scheduled wall-clock time.
 *
 * Each block component subscribes to [highlights] and filters for its own ID,
 * so only the matching component re-renders on each event.
 */
class KlangBlocksHighlightBuffer {

    var codeGenResult: CodeGenResult? = null

    private val highlightSource = StreamSource<HighlightSignal?>(null)
    val highlights = highlightSource.readonly

    private val pendingTimeouts = mutableSetOf<Int>()

    /**
     * Schedule a highlight for the block at [location] to fire [startFromNowMs] ms from now
     * and remain visible for [durationMs] ms.
     */
    fun scheduleHighlight(location: SourceLocation, startFromNowMs: Double, durationMs: Double) {
        val blockId = codeGenResult?.findBlockAt(location.startLine, location.startColumn) ?: return

        var timeoutId = 0
        timeoutId = window.setTimeout({
            highlightSource(HighlightSignal(blockId, durationMs))
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
