/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_engine.KlangRealtimeVoicePlayback
import io.peekandpoke.kraft.utils.launch
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource

/**
 * Non-UI controller owning the engine-startup → durable-playback → voice-forwarding lifecycle for
 * ONE realtime playback. The realtime sibling of [KlangCodePlaybackCtrl].
 *
 * It is deliberately a **pipe, not a manager**: an id goes in with a voice, the same id stops it.
 * It knows nothing about notes, MIDI, or what a voice sounds like — a note source (see
 * `MidiConnector`) owns per-note bookkeeping, and the page owns voice shape. That split is what
 * keeps this class reusable for a computer-keyboard fallback, UI sound effects or a pad grid.
 */
class RealtimePlaybackCtrl(
    /** Playback name; [io.peekandpoke.klang.audio_engine.KlangPlayer] mangles it to `"custom-$name"`. */
    private val playbackName: String,
) {
    /**
     * Scalar state, mirrored once from [Player.status] so consumers subscribe here only — the same
     * shape [KlangCodePlaybackCtrl] uses.
     *
     * It carries the [Player.Status] ENUM rather than a collapsed boolean: the song page can fold
     * "not started" into "ready" because its Play button doubles as the engine-start gesture, but
     * this page shows a separate "Start Engine" screen and needs the distinction — and both need
     * to tell FAILED apart from LOADING.
     */
    data class State(
        /** Mirrored from [Player.status] — global engine bootstrap state. */
        val playerStatus: Player.Status,
        /** True once THIS controller's durable playback exists and can take voices. */
        val isReady: Boolean,
        /** The exception message from a failed start, for display next to [Player.Status.FAILED]. */
        val error: String? = null,
    ) {
        val isLoading: Boolean get() = playerStatus == Player.Status.LOADING
        val isFailed: Boolean get() = playerStatus == Player.Status.FAILED
    }

    private val _state = StreamSource(
        State(playerStatus = Player.status(), isReady = false)
    )

    val state: Stream<State> = _state.readonly

    private var playback: KlangRealtimeVoicePlayback? = null

    /** Guards against a second [start] while the first is still in flight. */
    private var starting = false

    /**
     * Monotonic id source for voices. Owned here rather than on the playback so a note source can
     * mint an id before the playback exists (and without reaching into it).
     */
    private var liveIdCounter = 1

    init {
        Player.status.subscribeToStream { status ->
            _state { it.copy(playerStatus = status) }
        }
    }

    /**
     * Boots the engine and creates the durable playback. Idempotent and safe to call repeatedly.
     *
     * Call it from a USER GESTURE (the "Start Engine" button): browsers only allow an AudioContext
     * to start from one, so a page that boots the engine on mount alone comes up silently suspended.
     */
    fun start() {
        if (_state().isReady || starting) {
            return
        }

        starting = true

        launch {
            try {
                val player = Player.ensure().await()
                playback = player.createRealtimePlayback(playbackName)
                _state { it.copy(isReady = true, error = null) }
            } catch (t: Throwable) {
                console.error("[RealtimePlaybackCtrl] failed to start:", t)
                _state { it.copy(error = t.message ?: t.toString()) }
            } finally {
                starting = false
            }
        }
    }

    /** Mints the id for a voice about to be started. Unique per controller instance. */
    fun nextLiveId(): Int = liveIdCounter++

    /**
     * Fires a voice now. No-op until [start] has completed — a key pressed before the engine is up
     * is dropped rather than queued.
     *
     * @param gateDurSec null = held until [stopVoice].
     */
    fun startVoice(liveId: Int, data: VoiceData, gateDurSec: Double? = null) {
        playback?.startVoice(liveId = liveId, data = data, gateDurSec = gateDurSec)
    }

    /** Releases the voice(s) under [liveId] into their ADSR tail. Unknown id = no-op. */
    fun stopVoice(liveId: Int) {
        playback?.stopVoice(liveId)
    }

    /**
     * Announces an inline ignitor and returns the synthetic name to put in [VoiceData.sound];
     * null while the engine is not up. This is v1's seam: compile the editor's script to a DSL,
     * register it, play notes with the returned name.
     */
    fun registerIgnitor(dsl: IgnitorDsl): String? = playback?.registerIgnitor(dsl)

    /**
     * Stops the playback and drops it. The backend releases everything the playback still holds
     * (`Cmd.Cleanup` releases held realtime voices), so nothing can ring on after the page is gone.
     */
    fun tearDown() {
        playback?.stop()
        playback = null
        _state { it.copy(isReady = false) }
    }
}
