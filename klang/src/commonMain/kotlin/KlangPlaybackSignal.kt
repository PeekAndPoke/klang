package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_bridge.VoiceData

/**
 * Signals emitted during playback lifecycle.
 * Subscribe via [KlangPlayback.signals].
 */
sealed class KlangPlaybackSignal {

    /**
     * Emitted when sample preloading begins.
     * Gives the frontend a chance to show a "loading samples..." indicator.
     */
    data class PreloadingSamples(
        /** Number of unique samples that need to be loaded */
        val count: Int,
        /** The sample identifiers being loaded */
        val samples: List<String>,
    ) : KlangPlaybackSignal()

    /**
     * Emitted when sample preloading completes (all samples sent to backend).
     */
    data class SamplesPreloaded(
        /** Number of samples that were loaded */
        val count: Int,
        /** How long preloading took in milliseconds */
        val durationMs: Long,
    ) : KlangPlaybackSignal()

    /**
     * Emitted when playback has actually started (after preloading, first voices scheduled).
     */
    data object PlaybackStarted : KlangPlaybackSignal()

    /**
     * Emitted when playback has stopped.
     */
    data object PlaybackStopped : KlangPlaybackSignal()

    /**
     * Emitted when voices are scheduled for a cycle chunk.
     * Contains all voices scheduled in this batch for efficient processing.
     *
     * Note: Uses absolute wall-clock times (seconds from KlangTime epoch)
     * for UI highlighting (compared with Date.now()).
     */
    data class VoicesScheduled(
        /** All voice events scheduled in this batch */
        val voices: List<VoiceEvent>,
    ) : KlangPlaybackSignal() {
        data class VoiceEvent(
            /** Absolute start time in seconds (wall-clock) */
            val startTime: Double,
            /** Absolute end time in seconds (wall-clock) */
            val endTime: Double,
            /** The voice data being played */
            val data: VoiceData,
            /** Source locations for code highlighting (module-specific type, e.g. SourceLocationChain) */
            val sourceLocations: Any?,
        ) {
            /** Start time in milliseconds */
            val startTimeMs get() = (startTime * 1000).toLong()

            /** End time in milliseconds */
            val endTimeMs get() = (endTime * 1000).toLong()
        }
    }

    /**
     * Generic custom signal for module-specific data.
     * Modules like Strudel can wrap their own signals in this.
     */
    data class Custom(
        val type: String,
        val data: Any,
    ) : KlangPlaybackSignal()
}
