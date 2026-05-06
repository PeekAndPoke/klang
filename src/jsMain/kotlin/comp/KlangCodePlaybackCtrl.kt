package io.peekandpoke.klang.comp

import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.audio_engine.KlangCyclicPlayback
import io.peekandpoke.klang.audio_engine.play
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.ui.codemirror.EditorError
import io.peekandpoke.kraft.utils.launch
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.ultra.streams.Unsubscribe
import io.peekandpoke.ultra.streams.ops.debounce
import io.peekandpoke.ultra.streams.ops.distinct
import io.peekandpoke.ultra.streams.ops.map

/**
 * Non-UI controller that owns the compile → play → signals → stop lifecycle for a single
 * KlangScript code buffer. It is intentionally framework-free — it exposes ultra streams and
 * action methods so both a Kraft component and a page-owned custom button bar can cooperate.
 *
 * Scalar UI state (code, rpm, title, isPlaying, ...) is bundled into [State] so consumers can
 * subscribe once via [state] instead of juggling a fistful of individual streams. Event-ish
 * streams ([signals], [errors], [playback]) stay separate because they have different semantics.
 */
class KlangCodePlaybackCtrl private constructor(private val config: Config) {

    companion object {
        fun builder() = Builder()
    }

    data class Config(
        val initialCode: String,
        val initialRpm: Double,
        val initialTitle: String?,
        val exclusive: Boolean,
    )

    class Builder {
        private var code: String = ""
        private var rpm: Double = 30.0
        private var title: String? = null
        private var exclusive: Boolean = false

        fun code(c: String) = apply { code = c }
        fun rpm(r: Double) = apply { rpm = r }
        fun title(t: String?) = apply { title = t }

        /** Opt-in: when calling [play], stop any other controller that has claimed the primary slot. */
        fun exclusive(v: Boolean = true) = apply { exclusive = v }

        fun build(): KlangCodePlaybackCtrl = KlangCodePlaybackCtrl(
            Config(initialCode = code, initialRpm = rpm, initialTitle = title, exclusive = exclusive)
        )
    }

    /**
     * The full scalar UI state of the controller. Consumers subscribe to [state] and destructure
     * whichever fields they care about.
     *
     * [isPlayerLoading] mirrors [Player.status] == [Player.Status.LOADING] — the controller
     * publishes it here as a convenience so a button bar only needs one subscription.
     */
    data class State(
        val code: String,
        val rpm: Double,
        val title: String?,
        val isPlaying: Boolean,
        val isCodeModified: Boolean,
        val currentCycle: Int,
        val isPlayerLoading: Boolean,
    )

    // ── Scalar UI state (combined) ───────────────────────────────────────────

    private val _state = StreamSource(
        State(
            code = config.initialCode,
            rpm = config.initialRpm,
            title = config.initialTitle,
            isPlaying = false,
            isCodeModified = false,
            currentCycle = 0,
            isPlayerLoading = Player.status() == Player.Status.LOADING,
        )
    )
    val state: Stream<State> = _state.readonly

    // ── Event-ish streams (separate on purpose) ──────────────────────────────

    private val _playback = StreamSource<KlangCyclicPlayback?>(null)
    val playback: Stream<KlangCyclicPlayback?> = _playback.readonly

    private val _signals = StreamSource<KlangPlaybackSignal?>(null)
    val signals: Stream<KlangPlaybackSignal?> = _signals.readonly

    private val _errors = StreamSource<List<EditorError>>(emptyList())
    val errors: Stream<List<EditorError>> = _errors.readonly

    // ── Internal ─────────────────────────────────────────────────────────────

    /** Baseline for [State.isCodeModified] — the exact string that was last submitted to a playback. */
    private var playingCode: String? = null

    /** Keeps the signal forwarding subscription so it can be cancelled when playback stops. */
    private var signalSub: Unsubscribe? = null

    init {
        // Backend tempo sync — debounced on rpm changes only, so typing code doesn't delay the sync.
        _state.map { it.rpm }.distinct().debounce(150).subscribeToStream { newRpm ->
            if (newRpm > 0.0) _playback()?.updateRpm(newRpm)
        }

        // Mirror the global player loading state into our combined state.
        Player.status.subscribeToStream { status ->
            _state { it.copy(isPlayerLoading = status == Player.Status.LOADING) }
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

    // ── Actions ──────────────────────────────────────────────────────────────

    /**
     * Compile and start playback if stopped, or compile and call [KlangCyclicPlayback.updatePattern]
     * if already playing. Non-suspending — the work runs in a coroutine via [launch].
     */
    fun play() {
        if (config.exclusive) {
            Player.requestExclusivePlayback(self = this) { other ->
                (other as? KlangCodePlaybackCtrl)?.stop()
            }
        }
        launch { runPlay() }
    }

    fun stop() {
        signalSub?.invoke()
        signalSub = null
        _playback()?.stop()
        _playback(null)
        _signals(null)
        playingCode = null
        _state {
            it.copy(
                isPlaying = false,
                currentCycle = 0,
                isCodeModified = false,
            )
        }
        Player.publishNowPlaying(handle = this, value = null)
    }

    fun reemitVoiceSignals() {
        _playback()?.reemitVoiceSignals()
    }

    // ── Impl ─────────────────────────────────────────────────────────────────

    private suspend fun runPlay() {
        _errors(emptyList())

        try {
            val codeToPlay = _state().code
            val player = Player.ensure().await()
            val engine = Player.createEngine(player = player)
            val pattern = SprudelPattern.compile(engine, codeToPlay)
                ?: error("Failed to compile Sprudel pattern from code")

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

                    signalSub = pb.signals.subscribeToStream { signal ->
                        _signals(signal)
                        if (signal is KlangPlaybackSignal.CycleCompleted) {
                            _state { it.copy(currentCycle = signal.cycleIndex + 1) }
                        }
                    }

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
            _errors(listOf(mapToEditorError(e)))
        }
    }

    private fun publishNowPlaying(pb: KlangCyclicPlayback) {
        val s = _state()
        Player.publishNowPlaying(
            handle = this,
            value = Player.NowPlaying(
                title = s.title,
                code = s.code,
                rpm = s.rpm,
                playback = pb,
            ),
        )
    }
}
