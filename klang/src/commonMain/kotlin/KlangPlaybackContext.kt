package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_bridge.EngineDsl
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred

/**
 * Context object bundling all dependencies needed by playback implementations.
 * Owned and managed by [KlangPlayer].
 *
 * This reduces constructor parameter lists and makes it easier to add new dependencies
 * without changing all playback constructors.
 */
class KlangPlaybackContext internal constructor(
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

    /**
     * Completes when the backend emits [KlangCommLink.Feedback.BackendReady].
     * Playback should await this before scheduling the first voice so that
     * cold-start JIT/cache warmup is already done.
     */
    val backendReady: Deferred<Unit>,

    /**
     * Per-player cache + name allocator for inline [IgnitorDsl] sounds. Internal —
     * playbacks use [registerIgnitor] to interact with it.
     */
    internal val ignitors: IgnitorRegistry,

    /**
     * Per-player cache + name allocator for inline [EngineDsl] engines. Internal —
     * playbacks use [registerEngine] to interact with it.
     */
    internal val engines: EngineRegistry,
) {
    /**
     * Return a stable synthetic name for [dsl], registering it with the backend
     * on first sighting. Used at the playback → wire boundary to denormalize
     * inline `SoundValue.Osc(dsl)` references into a wire-level sound name.
     *
     * Structurally-equal DSL trees collapse to one name (and one BE registration).
     */
    fun registerIgnitor(dsl: IgnitorDsl): String = ignitors.registerOrLookup(dsl)

    /**
     * Return a stable synthetic name for [dsl], registering it with the backend on first
     * sighting. The engine-side mirror of [registerIgnitor] — denormalizes an inline
     * `EngineDsl` (from `.engine(engineDsl)`) into a wire-level engine name.
     */
    fun registerEngine(dsl: EngineDsl): String = engines.registerOrLookup(dsl)
}
