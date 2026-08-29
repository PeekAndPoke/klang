/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.audio_bridge.RealtimeVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource

/**
 * An always-on playback that plays voices "now" — the realtime counterpart of
 * [KlangCyclicPlayback] for sources without a timeline (MIDI keyboard, UI-triggered sound
 * effects; see docs/tasks/midi-keyboard-playground.md).
 *
 * There is nothing to start and nothing schedules cleanup: the backend engine materializes on
 * the first command and lives until [stop] sends the explicit `Cleanup`. Create via
 * [KlangPlayer.createRealtimePlayback] — never directly — so playback ids stay collision-free.
 */
class KlangRealtimeVoicePlayback internal constructor(
    private val player: KlangPlayer,
    override val playbackId: String,
) : KlangPlayback {

    private val _signals = StreamSource<KlangPlaybackSignal>(KlangPlaybackSignal.Idle)
    override val signals: Stream<KlangPlaybackSignal> = _signals.readonly

    private var nextLiveId = 1

    /**
     * Starts a voice immediately.
     *
     * @param data The synthesis payload.
     * @param gateDurSec Gate length in seconds; null = held until a later stop command (v2).
     * @return The liveId identifying this voice while it is alive.
     */
    fun startVoice(data: VoiceData, gateDurSec: Double? = null): Int {
        val liveId = nextLiveId++

        player.sendControl(
            KlangCommLink.Cmd.StartRealtimeVoice(
                playbackId = playbackId,
                voice = RealtimeVoice(liveId = liveId, data = data, gateDurSec = gateDurSec),
            )
        )

        return liveId
    }

    /**
     * Releases the voice(s) started under [liveId] — they enter their ADSR release from the
     * current level (a note-off, not a cut). Releasing an already-ended liveId is a no-op.
     */
    fun stopVoice(liveId: Int) {
        player.sendControl(
            KlangCommLink.Cmd.StopRealtimeVoice(playbackId = playbackId, liveId = liveId)
        )
    }

    override fun handleFeedback(feedback: KlangCommLink.Feedback) {
        // No feedback consumed yet — diagnostics are handled at player level.
    }

    override fun start() {
        // Nothing to do — the backend engine materializes on the first command.
    }

    override fun stop() {
        player.sendControl(KlangCommLink.Cmd.Cleanup(playbackId))
        player.unregisterPlayback(this)
    }
}
