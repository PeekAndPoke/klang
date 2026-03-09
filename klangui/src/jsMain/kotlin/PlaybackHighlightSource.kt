package io.peekandpoke.klang.ui

import io.peekandpoke.klang.script.ast.SourceLocationChain

/**
 * A single scheduled voice event for playback highlighting.
 *
 * Carries the raw timing and source-location data from the playback scheduler.
 * Consumers (editors) match voice events against their own atoms using the
 * [SourceLocationChain] and a base [io.peekandpoke.klang.script.ast.SourceLocation].
 */
data class PlaybackVoice(
    /** Absolute start time in seconds (wall-clock). */
    val startTime: Double,
    /** Absolute end time in seconds (wall-clock). */
    val endTime: Double,
    /** Source location chain for matching against source code elements. */
    val sourceLocations: SourceLocationChain?,
)
