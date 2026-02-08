package io.peekandpoke.klang.codemirror

import de.peekandpoke.kraft.components.ComponentRef
import io.peekandpoke.klang.audio_engine.KlangPlaybackSignal
import io.peekandpoke.klang.script.ast.SourceLocation
import io.peekandpoke.klang.script.ast.SourceLocationChain
import kotlinx.browser.window
import kotlin.js.Date

/**
 * Buffers and manages code highlights with deduplication, rate-limiting, and concurrency control.
 *
 * Features:
 * - Deduplication: Removes existing highlight for same location before adding new one
 * - Rate limiting: Max [maxRefreshRatePerLocation] updates per second per location
 * - Concurrency cap: Max [maxSimultaneousHighlights] active highlights
 * - Dynamic duration: Highlight animation matches voice duration
 * - Clean cancellation: All pending timeouts can be cleared
 */
class CodeHighlightBuffer(
    private val editorRef: ComponentRef.Tracker<CodeMirrorComp>,
    private val maxRefreshRatePerLocation: Int = 16,
    private val maxSimultaneousHighlights: Int = 100,
) {
    /**
     * Active highlight information for tracking and cancellation.
     */
    private data class ActiveHighlight(
        val locationKey: String,
        val highlightId: String,
        val startTimeoutId: Int?,
        val removeTimeoutId: Int?,
    )

    // Location key → currently active highlight
    private val activeHighlights = mutableMapOf<String, ActiveHighlight>()

    // All setTimeout IDs for bulk cancellation
    private val pendingTimeouts = mutableSetOf<Int>()

    // Location key → last time (Date.now()) a highlight was applied
    private val lastHighlightTime = mutableMapOf<String, Double>()

    // Computed minimum interval between highlights for the same location
    private val minIntervalMs: Double get() = 1000.0 / maxRefreshRatePerLocation

    /**
     * Schedule highlights for a voice event.
     * Automatically handles all source locations in the event's location chain.
     */
    fun scheduleHighlight(event: KlangPlaybackSignal.VoicesScheduled.VoiceEvent) {
        // Cast to SourceLocationChain - if not, nothing to highlight
        val chain = event.sourceLocations as? SourceLocationChain ?: return

        // Schedule each location in the chain
        chain.locations.forEach { location ->
            scheduleForLocation(location, event)
        }
    }

    /**
     * Schedule a highlight for a specific source location.
     */
    private fun scheduleForLocation(
        location: SourceLocation,
        event: KlangPlaybackSignal.VoicesScheduled.VoiceEvent,
    ) {
        val key = location.toKey()
        val now = Date.now()

        // Compute timing (convert times from seconds to milliseconds)
        val startTimeMs = event.startTime * 1000.0
        val endTimeMs = event.endTime * 1000.0
        val startFromNowMs = maxOf(1.0, (startTimeMs - now) - 0.0)
        val durationMs = maxOf(200.0, minOf(10000.0, endTimeMs - startTimeMs))

        // Rate-limit check: Use projected time (when highlight will actually fire)
        val projectedTime = now + startFromNowMs
        val lastTime = lastHighlightTime[key]
        if (lastTime != null && (projectedTime - lastTime) < minIntervalMs) {
            return // Skip - too soon after last highlight for this location
        }

        // Max simultaneous check
        if (activeHighlights.size >= maxSimultaneousHighlights) {
            return // Drop - too many active highlights
        }

        // Schedule the start
        var startTimeoutId = 0

        startTimeoutId = window.setTimeout({
            // Deduplication: Remove existing highlight for this location
            activeHighlights[key]?.let { existing ->
                // Cancel removal timeout
                existing.removeTimeoutId?.let { id ->
                    window.clearTimeout(id)
                    pendingTimeouts.remove(id)
                }
                // Remove from editor
                editorRef { it.removeHighlight(existing.highlightId) }
                // Remove from tracking
                activeHighlights.remove(key)
            }

            // Add new highlight
            var highlightId = ""
            editorRef { editor ->
                highlightId = editor.addHighlight(
                    line = location.startLine,
                    column = location.startColumn,
                    length = if (location.startLine == location.endLine) {
                        location.endColumn - location.startColumn
                    } else {
                        2 // multiline fallback
                    },
                    durationMs = durationMs,
                )
            }

            // If editor didn't create highlight (empty ID), cleanup and return
            if (highlightId.isEmpty()) {
                pendingTimeouts.remove(startTimeoutId)
                return@setTimeout
            }

            // Record last highlight time
            lastHighlightTime[key] = Date.now()

            // Schedule removal (durationMs + 50ms buffer for animation to finish)
            var removeTimeoutId = 0

            removeTimeoutId = window.setTimeout({
                editorRef { it.removeHighlight(highlightId) }
                activeHighlights.remove(key)
                pendingTimeouts.remove(removeTimeoutId)
            }, (durationMs + 50.0).toInt())

            pendingTimeouts.add(removeTimeoutId)

            // Store active highlight
            activeHighlights[key] = ActiveHighlight(
                locationKey = key,
                highlightId = highlightId,
                startTimeoutId = startTimeoutId,
                removeTimeoutId = removeTimeoutId,
            )
        }, startFromNowMs.toInt())

        pendingTimeouts.add(startTimeoutId)
    }

    /**
     * Cancel all pending highlights and clear all active highlights from the editor.
     * Call this when playback stops.
     */
    fun cancelAll() {
        // 1. Cancel all pending timeouts
        for (id in pendingTimeouts) {
            window.clearTimeout(id)
        }
        pendingTimeouts.clear()

        // 2. Clear all active highlights from editor
        editorRef { it.clearHighlights() }

        // 3. Reset state
        activeHighlights.clear()
        lastHighlightTime.clear()
    }

    /**
     * Convert a SourceLocation to a unique key for deduplication.
     */
    private fun SourceLocation.toKey(): String = "$startLine:$startColumn:$endLine:$endColumn"
}
