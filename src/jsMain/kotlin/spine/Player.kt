/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_engine.KlangDiagnostics
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.audio_engine.klangPlayer
import io.peekandpoke.klang.audio_fe.create
import io.peekandpoke.klang.audio_fe.samples.SampleCatalogue
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.common.logging.LogArea
import io.peekandpoke.klang.common.logging.Logger
import io.peekandpoke.klang.registerBuiltInSongsAsModules
import io.peekandpoke.klang.script.KlangScriptEngine
import io.peekandpoke.klang.script.stdlib.ConsoleLevel
import io.peekandpoke.klang.script.stdlib.KlangStdLib
import io.peekandpoke.klang.script.stdlibLib
import io.peekandpoke.klang.spine.storage.OutputVolumeStorage
import io.peekandpoke.klang.sprudel.lang.sprudelLib
import io.peekandpoke.kraft.utils.async
import io.peekandpoke.kraft.utils.launch
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.ultra.streams.ops.combinedWith
import io.peekandpoke.ultra.streams.ops.distinct
import io.peekandpoke.ultra.streams.ops.map
import io.peekandpoke.ultra.streams.ops.switchMapNotNull
import kotlinx.browser.window
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

private val log = Logger(area = LogArea.Engine, source = "Player")

class Player : PlaybackEngine {

    companion object {
        /**
         * Creates a KlangScriptEngine with sprudel and stdlib registered.
         *
         * Inline ignitors are denormalized at the playback boundary by the player's ignitor
         * registry; no engine-level wiring is required.
         * If [outputHandler] is provided, console/print output goes there (e.g. REPL capture).
         */
        fun createEngine(
            outputHandler: ((ConsoleLevel, List<String>) -> Unit)? = null,
        ): KlangScriptEngine {
            val stdlib = if (outputHandler != null) {
                KlangStdLib.create(outputHandler = outputHandler)
            } else {
                stdlibLib
            }

            val engineBuilder = KlangScriptEngine.Builder()

            engineBuilder.registerLibrary(stdlib)
            engineBuilder.registerLibrary(sprudelLib)
            engineBuilder.registerBuiltInSongsAsModules()

            return engineBuilder.build()
        }
    }

    enum class Status {
        /** Initial state */
        NOT_LOADED,
        /** Player loading */
        LOADING,
        /** Player ready */
        READY,
        /** Engine startup threw. [ensure] drops its memoized deferred on this path, so calling it again RETRIES */
        FAILED,
    }

    private val _status = StreamSource(Status.NOT_LOADED)
    override val status: Stream<Status> = _status.readonly

    private val _player = StreamSource<KlangPlayer?>(null)
    val player: Stream<KlangPlayer?> = _player.readonly

    /** The current player's Motor diagnostics, as [KlangPlayer.diagnostics] has them, null while there is no player. */
    class Diagnostics(player: Stream<KlangPlayer?>) {
        /** The latest diagnostics; null while there is no player, so the gauges go offline. */
        val stream: Stream<KlangCommLink.Feedback.Diagnostics?> = player.switchMapNotNull { it.diagnostics.stream }
            .combinedWith(player) { d, p -> d?.takeIf { p != null } }

        /** The Motor's last two minutes; null while there is no player. */
        val history: Stream<KlangDiagnostics.History?> = player.switchMapNotNull { it.diagnostics.history }
            .combinedWith(player) { h, p -> h?.takeIf { p != null } }
    }

    /**
     * The current player's Motor diagnostics: subscribe to [Diagnostics.stream] for the latest, to
     * [Diagnostics.history] for the last two minutes (the maintainer, 2026-10-28).
     */
    val diagnostics = Diagnostics(player)

    /**
     * Whether something plays on the current player ([KlangPlayer.playing]); false while there is none. The screen
     * stays awake on it ([io.peekandpoke.klang.comp.ScreenAwake]).
     */
    val playing: Stream<Boolean> = player.switchMapNotNull { it.playing }.map { it == true }.distinct()

    private val _samples = StreamSource<Samples?>(null)
    val samples: Stream<Samples?> = _samples.readonly

    private val _outputVolume = StreamSource(OutputVolumeStorage.load())

    /**
     * The master volume, a linear gain from 0.0 (silence) to 1.0 (the song's own level) on the browser's output
     * (KLANG-00330). It lives here from page load, before the Motor; the player gets it when it starts, and every
     * change after. The volume knob derives its position from it ([MasterVolumeScale]).
     */
    val outputVolume: Stream<Double> = _outputVolume.readonly

    /** Sets the master volume to [gain] (kept in 0.0 to 1.0) and hands it to the player, when there is one. */
    fun setOutputVolume(gain: Double) {
        // NaN-guard: an unreadable gain leaves the volume where it is
        if (gain != gain) {
            return
        }

        val kept = gain.coerceIn(0.0, 1.0)

        _outputVolume(kept)
        get()?.setOutputVolume(kept)
    }

    /** Stores the master volume for the next visit; once per turn of the knob, not on every move. */
    fun rememberOutputVolume() {
        OutputVolumeStorage.save(_outputVolume())
    }

    private var deferred: CompletableDeferred<KlangPlayer>? = null

    // Downloaded on first need: [preloadSamples] or [ensure]
    private val samplesLazy = lazy {
        async {
            // Runtime override for the sample host (same idiom as window.__APP_VERSION__, see VersionController):
            // set `window.__KLANG_SAMPLE_BASE__ = "http://localhost:8000"` to load from a local mirror.
            val override = window.asDynamic().__KLANG_SAMPLE_BASE__ as? String

            val catalogue = when {
                override.isNullOrBlank() -> SampleCatalogue.default
                else -> SampleCatalogue.mirrored(override)
            }

            Samples.create(catalogue = catalogue)
        }.also { it.start() }
    }

    private val samplesDeferred: Deferred<Samples> by samplesLazy

    /** Whether the samples' download was asked for yet: by [preloadSamples] or by the engine's start ([ensure]). */
    internal val samplesRequested: Boolean get() = samplesLazy.isInitialized()

    private var preloading = false

    /**
     * Starts the samples' download, once: the app does at its start, so they are there when the first song plays. A
     * player nobody asks (a spec's) downloads nothing until it is started ([ensure]).
     */
    fun preloadSamples() {
        if (preloading) {
            return
        }

        preloading = true

        launch {
            val samples = samplesDeferred.await()
            _samples(samples)
        }
    }

    fun get(): KlangPlayer? = player()

    /** The player, started on the first call (inside a user gesture, for the browser's audio); see [ensure]. */
    override suspend fun readyPlayer(): KlangPlayer = ensure().await()

    /** A script engine for a playback's code: [createEngine] with no console of its own. */
    override fun scriptEngine(): KlangScriptEngine = createEngine()

    fun ensure(): Deferred<KlangPlayer> {
        deferred?.let { return it }

        val def = CompletableDeferred<KlangPlayer>().also { deferred = it }

        launch {
            _status(Status.LOADING)

            try {
                val samples = samplesDeferred.await()

                // The master volume (KLANG-00330) goes in before the audio graph exists
                val playerOptions = KlangPlayer.Options(samples = samples, sampleRate = 48000, outputVolume = _outputVolume())
                val playerInstance = klangPlayer(playerOptions)

                // And once more after: a turn of the knob while the engine started
                playerInstance.setOutputVolume(_outputVolume())

                log.info("KlangPlayer ready")

                _samples(samples)
                _player(playerInstance)
                _status(Status.READY)

                def.complete(playerInstance)
            } catch (t: Throwable) {
                // Without this the deferred never completes: status would sit at LOADING forever
                // and every awaiter would hang: a Play button spinning with no explanation.
                log.error("KlangPlayer failed to start", t)

                _status(Status.FAILED)

                // Drop the memo so a later ensure() actually retries instead of handing out the
                // same dead deferred.
                deferred = null
                def.completeExceptionally(t)
            }
        }

        return def
    }
}
