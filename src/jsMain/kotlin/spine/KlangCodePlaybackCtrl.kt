/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.audio_engine.KlangCyclicPlayback
import io.peekandpoke.klang.audio_engine.play
import io.peekandpoke.klang.comp.mapToEditorError
import io.peekandpoke.klang.sprudel.SprudelDiagnostic
import io.peekandpoke.klang.sprudel.SprudelDiagnostics
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.ui.codemirror.EditorError
import io.peekandpoke.kraft.utils.launch
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.ultra.streams.ops.debounce
import io.peekandpoke.ultra.streams.ops.distinct
import io.peekandpoke.ultra.streams.ops.map
import io.peekandpoke.ultra.streams.ops.switchMapNotNull
import kotlin.js.Date
import kotlinx.browser.window

/**
 * Non-UI controller that owns the compile → play → signals → stop lifecycle for a single
 * KlangScript code buffer. It is intentionally framework-free: it exposes ultra streams and
 * action methods so both a Kraft component and a page-owned custom button bar can cooperate.
 * Its owner makes it and [release]s it when done (KLANG-00389): it follows the [PlaybackEngine]'s status till then.
 *
 * Scalar UI state (code, rpm, title, isPlaying, ...) is bundled into [State] so consumers can
 * subscribe once via [state] instead of juggling a fistful of individual streams. Event-ish
 * streams ([signals], [errors], [playback]) stay separate because they have different semantics.
 */
class KlangCodePlaybackCtrl private constructor(private val config: Config) : PlaybackOwner {

    companion object {
        /**
         * The RPM knob's range. Up to 999 since the tempo map took the jumps out of the knob (the maintainer, 2026-10-31,
         * KLANG-00399: "the RPM dial now works perfectly. Please set the RPM max to 999").
         */
        val RPM_RANGE: ClosedFloatingPointRange<Double> = 10.0..200.0

        fun builder() = Builder()
    }

    data class Config(
        val initialCode: String,
        val initialRpm: Double,
        val initialTitle: String?,
        /** The lane a preview plays on ([Builder.preview]); null for a controller that is no preview. */
        val previews: (() -> Previews)?,
        val engine: PlaybackEngine,
    )

    class Builder {
        private var code: String = ""
        private var rpm: Double = 30.0
        private var title: String? = null
        private var previews: (() -> Previews)? = null
        private var engine: PlaybackEngine? = null

        fun code(c: String) = apply { code = c }
        fun rpm(r: Double) = apply { rpm = r }
        fun title(t: String?) = apply { title = t }

        /**
         * A preview, a code example (KLANG-00406, D1): its [play] stops another preview and whatever else holds the
         * stage, and fades the song on the deck out instead; its stop fades the song in again ([Previews]). On [lane],
         * the app's [Playbacks.previews] when none is given.
         */
        fun preview(lane: Previews? = null) = apply { previews = { lane ?: Playbacks.previews } }

        /** What it plays on: the app's [Player] when none is given (so a spec's builder never starts it). */
        fun engine(e: PlaybackEngine) = apply { engine = e }

        fun build(): KlangCodePlaybackCtrl = KlangCodePlaybackCtrl(
            Config(initialCode = code, initialRpm = rpm, initialTitle = title, previews = previews, engine = engine ?: Player)
        )
    }

    /**
     * The full scalar UI state of the controller. Consumers subscribe to [state] and destructure
     * whichever fields they care about.
     *
     * [playerStatus] mirrors [PlaybackEngine.status]: the controller publishes it here as a convenience
     * so a button bar only needs one subscription. It carries the ENUM, not a
     * `isPlayerLoading` boolean: collapsing it loses FAILED, and a Play button that cannot say
     * "the engine did not start" just spins.
     */
    data class State(
        val code: String,
        val rpm: Double,
        val title: String?,
        val isPlaying: Boolean,
        val isCodeModified: Boolean,
        val currentCycle: Int,
        val playerStatus: Player.Status,
    ) {
        val isPlayerLoading: Boolean get() = playerStatus == Player.Status.LOADING
        val isPlayerFailed: Boolean get() = playerStatus == Player.Status.FAILED
    }

    // ── Scalar UI state (combined) ───────────────────────────────────────────

    private val _state = StreamSource(
        State(
            code = config.initialCode,
            rpm = config.initialRpm,
            title = config.initialTitle,
            isPlaying = false,
            isCodeModified = false,
            currentCycle = 0,
            playerStatus = config.engine.status(),
        )
    )
    val state: Stream<State> = _state.readonly

    // ── Event-ish streams (separate on purpose) ──────────────────────────────

    private val _playback = StreamSource<KlangCyclicPlayback?>(null)
    val playback: Stream<KlangCyclicPlayback?> = _playback.readonly

    /** The current playback's signals; null while nothing plays. */
    val signals: Stream<KlangPlaybackSignal?> = _playback.switchMapNotNull { it.signals }

    private val _errors = StreamSource<List<EditorError>>(emptyList())
    val errors: Stream<List<EditorError>> = _errors.readonly

    // ── Internal ─────────────────────────────────────────────────────────────

    /** Baseline for [State.isCodeModified]: the exact string that was last submitted to a playback. */
    private var playingCode: String? = null

    /** Counts the stops: a start that waited for the engine gives up when a stop came meanwhile (KLANG-00398). */
    private var stops = 0

    /**
     * The output gain asked for ([setGain]): the playback has it, and a playback still to start gets it before its
     * first sound (a song still starting when a preview begins starts faded).
     */
    var gain: Double = 1.0
        private set

    /** The subscriptions this controller holds till it is [release]d. */
    private val subscriptions: List<() -> Unit> = listOf(
        // Backend tempo sync: debounced on rpm changes only, so typing code doesn't delay the sync.
        _state.map { it.rpm }.distinct().debounce(150).subscribeToStream { newRpm ->
            if (newRpm > 0.0) _playback()?.updateRpm(newRpm)
        },

        // The cycle counter follows the playing playback's completed cycles, when their end is heard (KLANG-00399): a
        // cycle completes once the backend has rendered it, and is heard the output latency later
        signals.subscribeToStream { signal ->
            if (signal is KlangPlaybackSignal.CycleCompleted) {
                countWhenHeard(signal)
            }
        },
        // Mirror the engine's loading state into our combined state.
        config.engine.status.subscribeToStream { status ->
            _state { it.copy(playerStatus = status) }
        },
    )

    /** Counts [completed] at its [KlangPlaybackSignal.CycleCompleted.atTimeSec], if the same playback still plays. */
    private fun countWhenHeard(completed: KlangPlaybackSignal.CycleCompleted) {
        val playback = _playback()
        val count = {
            if (_playback() === playback) {
                _state { it.copy(currentCycle = completed.cycleIndex + 1) }
            }
        }
        val inMs = completed.atTimeSec * 1000.0 - Date.now()

        if (inMs <= 0.0) {
            count()
        } else {
            window.setTimeout(count, inMs.toInt())
        }
    }

    // ── Mutators ─────────────────────────────────────────────────────────────

    fun setCode(newCode: String) {
        val s = _state()
        if (s.code == newCode) return
        _state(
            s.copy(
                code = newCode,
                isCodeModified = playingCode != null && newCode != playingCode,
            )
        )
        _errors(emptyList())
    }

    fun setRpm(newRpm: Double) {
        _state { it.copy(rpm = newRpm) }
    }

    fun setTitle(newTitle: String?) {
        val s = _state()
        if (s.title == newTitle) return
        _state(s.copy(title = newTitle))
        _playback()?.let { publishNowPlaying(it) }
    }

    fun clearErrors() {
        _errors(emptyList())
    }

    /** Moves the output gain to [gain] over [rampSec] seconds: now, on what plays, and kept for what starts later. */
    fun setGain(gain: Double, rampSec: Double) {
        this.gain = gain
        _playback()?.setGain(gain = gain, rampSec = rampSec)
    }

    // ── Actions ──────────────────────────────────────────────────────────────

    /**
     * Compile and start playback if stopped, or compile and call [KlangCyclicPlayback.updatePattern]
     * if already playing. Non-suspending: the work runs in a coroutine via [launch].
     */
    fun play() {
        config.previews?.invoke()?.play(preview = this)

        val stopsAtPlay = stops

        launch { runPlay(stopsAtPlay = stopsAtPlay) }
    }

    override fun stop() {
        stops++
        val stopping = _playback()
        // Off the signals first: what the playback sends while it stops does not reach the editor.
        _playback(null)
        stopping?.stop()
        playingCode = null
        _state {
            it.copy(
                isPlaying = false,
                currentCycle = 0,
                isCodeModified = false,
            )
        }
        Playbacks.publishNowPlaying(owner = this, value = null)
        config.previews?.invoke()?.stopped(preview = this)
    }

    /** Takes the stage again with what plays, after a preview held it: what the stage's displays show is this again. */
    fun showOnStage() {
        _playback()?.let { publishNowPlaying(it) }
    }

    /** Stops what plays and ends the subscriptions: the owner is done with this controller (it leaves, or unmounts). */
    fun release() {
        stop()
        subscriptions.forEach { it() }
    }

    fun reemitVoiceSignals() {
        _playback()?.reemitVoiceSignals()
    }

    // ── Impl ─────────────────────────────────────────────────────────────────

    /** Compiles and plays; gives up after the engine's start when a stop came since the Play ([stopsAtPlay]). */
    private suspend fun runPlay(stopsAtPlay: Int) {
        _errors(emptyList())

        // Pattern building swallows errors on purpose, so a bad edit mid-performance cannot kill
        // the audio (see SprudelDiagnostics). Collect what it swallowed and surface it in the
        // editor: without this a single typo silently discards a whole shape function and the
        // only trace is a console stack trace nobody is watching while mixing.
        //
        // Declared OUTSIDE the try, and published in a `finally`, because installing a collector
        // stops SprudelDiagnostics from printing. If the build then threw (or returned null),
        // publishing from inside the try would lose the diagnostic from the editor AND from the
        // console: strictly less than before this change existed.
        val swallowed = mutableListOf<SprudelDiagnostic>()

        try {
            val codeToPlay = _state().code
            val player = config.engine.readyPlayer()

            // Stopped since the Play, while the engine started (its first start of a visit): nothing plays
            if (stops != stopsAtPlay) {
                return
            }

            val engine = config.engine.scriptEngine()
            val pattern = SprudelDiagnostics.collectingInto(swallowed) {
                SprudelPattern.compile(engine, codeToPlay)
            } ?: error("Failed to compile Sprudel pattern from code")

            when (val current = _playback()) {
                null -> {
                    val pb = player.play(pattern)
                    _playback(pb)
                    playingCode = codeToPlay
                    _state {
                        it.copy(
                            isPlaying = true,
                            isCodeModified = false,
                            currentCycle = 0,
                        )
                    }

                    // The gain asked for meanwhile, before the first sound: kept by the playback, sent at its start
                    pb.setGain(gain = gain, rampSec = 0.0)
                    pb.start(KlangCyclicPlayback.Options(rpm = _state().rpm))
                    publishNowPlaying(pb)
                }

                else -> {
                    current.updatePattern(pattern)
                    playingCode = codeToPlay
                    _state { it.copy(isCodeModified = false) }
                    publishNowPlaying(current)
                }
            }
        } catch (e: Throwable) {
            console.error("KlangCodePlaybackCtrl: play failed", e)
            // APPEND: a failure here must not discard markers published for swallowed errors.
            _errors(_errors() + mapToEditorError(e))

            // A preview that failed to start plays nothing: the song it faded out comes back
            if (_playback() == null) {
                config.previews?.invoke()?.stopped(preview = this)
            }
        } finally {
            // Runs on every path (success, throw, and the null-compile elvis), so a swallowed
            // error is never silently dropped. Keeps playing either way: a marker is
            // information, not a reason to stop the music.
            if (swallowed.isNotEmpty()) {
                swallowed.forEach {
                    // Keep the console trace too. With a collector installed SprudelDiagnostics
                    // no longer prints, and for anything but a KlangScriptTypeError the marker
                    // carries only `message`.
                    console.warn("Swallowed in ${it.context}:", it.error)
                }
                _errors(_errors() + swallowed.map { mapToEditorError(it.error) })
            }
        }
    }

    private fun publishNowPlaying(pb: KlangCyclicPlayback) {
        val s = _state()
        Playbacks.publishNowPlaying(
            owner = this,
            value = Playbacks.NowPlaying(
                title = s.title,
                code = s.code,
                rpm = s.rpm,
                playback = pb,
            ),
        )
    }
}
