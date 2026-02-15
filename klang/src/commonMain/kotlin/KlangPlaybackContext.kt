package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * Context object bundling all dependencies needed by playback implementations.
 * Owned and managed by [KlangPlayer].
 *
 * This reduces constructor parameter lists and makes it easier to add new dependencies
 * without changing all playback constructors.
 */
data class KlangPlaybackContext(
    /** Player configuration options */
    val playerOptions: KlangPlayer.Options,

    /** Centralized sample preloader (shared across all playbacks) */
    val samplePreloader: SamplePreloader,

    /** Function to send control commands to the audio backend */
    val sendControl: (KlangCommLink.Cmd) -> Unit,

    /** Coroutine scope for playback operations */
    val scope: CoroutineScope,

    /** Dispatcher for event fetching operations */
    val fetcherDispatcher: CoroutineDispatcher,

    /** Dispatcher for frontend callbacks and signals */
    val callbackDispatcher: CoroutineDispatcher,
)