/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KlangPatternEvent
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.audio_bridge.RealtimeVoice
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import kotlinx.coroutines.Deferred

/**
 * An always-on playback that plays voices "now" — the realtime counterpart of
 * [KlangCyclicPlayback] for sources without a timeline (MIDI keyboard, UI-triggered sound
 * effects; see KLANG-00228 (midi keyboard playground)).
 *
 * There is nothing to start and nothing schedules cleanup: the backend engine materializes on
 * the first command and lives until [stop] sends the explicit `Cleanup`. Create via
 * [KlangPlayer.createRealtimePlayback] — never directly — so playback ids stay collision-free.
 */
class KlangRealtimeVoicePlayback internal constructor(
    private val player: KlangPlayer,
    override val playbackId: String,
    /** Inline-DSL bookkeeping — the same object the cyclic path uses (see [InlineDslRegistrar]). */
    private val registrar: InlineDslRegistrar,
) : KlangPlayback {

    private val _signals = StreamSource<KlangPlaybackSignal>(KlangPlaybackSignal.Idle)
    override val signals: Stream<KlangPlaybackSignal> = _signals.readonly

    private var liveIdCounter = 1

    /**
     * Starts a voice immediately under a caller-supplied [liveId].
     *
     * The explicit-id form exists because a note source generally has to know the id BEFORE the
     * voice exists: the MIDI playground's note voicing maps (source, channel, note) to the id it will
     * later stop, and that mapping has to be in place before the note-on is even sent.
     *
     * @param data The synthesis payload.
     * @param gateDurSec Gate length in seconds; null = held until [stopVoice].
     */
    fun startVoice(liveId: Int, data: VoiceData, gateDurSec: Double? = null) {
        player.sendControl(
            KlangCommLink.Cmd.StartRealtimeVoice(
                playbackId = playbackId,
                voice = RealtimeVoice(liveId = liveId, data = data, gateDurSec = gateDurSec),
            )
        )
    }

    /**
     * Starts a voice immediately, minting the id here.
     *
     * @return The liveId identifying this voice while it is alive.
     */
    fun startVoice(data: VoiceData, gateDurSec: Double? = null): Int =
        (liveIdCounter++).also { startVoice(liveId = it, data = data, gateDurSec = gateDurSec) }

    /**
     * Starts one voice per pattern event, all under the same [liveId], so a single [stopVoice]
     * releases them together (a mapper that turns one key into a chord).
     *
     * This is how a pattern language reaches the realtime path: the caller queries its pattern
     * and hands over the events; their timing is ignored, every voice starts now. Inline DSLs the
     * events reference are announced first, exactly as the cyclic scheduler does before it
     * schedules (see [InlineDslRegistrar.announceAll]).
     *
     * @param gateDurSec Gate length in seconds, scaled by each event's own [KlangPatternEvent.gateSeconds] (sprudel's
     *   `legato`); null = held until [stopVoice], whatever the event's legato.
     */
    fun startEvents(liveId: Int, events: List<KlangPatternEvent>, gateDurSec: Double? = null) {
        registrar.announceAll(events)

        events.forEach { event ->
            // A fixed gate takes the event's own (sprudel's `legato`); a held one is released by [stopVoice]
            startVoice(liveId = liveId, data = event.toVoiceData(), gateDurSec = gateDurSec?.let { event.gateSeconds(it) })
        }
    }

    /**
     * Loads the samples [requests] name and sends them to the backend: the realtime path loads nothing on its own (it
     * has no timeline to look ahead on), so a source with a known set of sounds, such as the MIDI rig's drum kit, loads
     * them here before it plays. Completes once the backend has them all (KLANG-00402, D2).
     */
    fun preload(requests: Set<SampleRequest>): Deferred<Unit> = player.samplePreloader.ensureLoadedDeferred(requests = requests)

    /**
     * Announces an inline [IgnitorDsl] to this playback's backend engine and returns the
     * synthetic name to put in [VoiceData.sound].
     *
     * This is how a user-authored instrument reaches the realtime path (the MIDI playground's
     * ignitor editor): compile to a DSL, register it, play notes with the returned name.
     * Announce-once and naming are shared with the cyclic path — the same DSL tree yields the
     * same name and is sent to the backend only the first time this playback sees it.
     */
    fun registerIgnitor(dsl: IgnitorDsl): String = registrar.ignitors.registerOrLookup(dsl)

    /**
     * Releases the voice(s) started under [liveId] — they enter their ADSR release from the
     * current level (a note-off, not a cut). Releasing an already-ended liveId is a no-op.
     */
    fun stopVoice(liveId: Int) {
        player.sendControl(
            KlangCommLink.Cmd.StopRealtimeVoice(playbackId = playbackId, liveId = liveId)
        )
    }

    /**
     * The backend asks for a sample a voice needs and does not have: it is loaded and sent, as the cyclic path does. A
     * fallback, not a guarantee: the voice has started already, so the first hit of a sample not loaded plays silent. A
     * source that knows its sounds ahead (the MIDI rig's drum kit) loads them with [preload] and waits for it.
     */
    override fun handleFeedback(feedback: KlangCommLink.Feedback) {
        when (feedback) {
            is KlangCommLink.Feedback.RequestSample -> {
                player.samplePreloader.ensureLoadedSilently(requests = setOf(feedback.req))
            }

            // The player's: these never need a realtime playback's answer
            is KlangCommLink.Feedback.PlaybackAnchored,
            is KlangCommLink.Feedback.Diagnostics,
            is KlangCommLink.Feedback.SampleReceived,
            is KlangCommLink.Feedback.BackendReady,
            is KlangCommLink.Feedback.PlaybackFailed,
            -> Unit
        }
    }

    override fun start() {
        // Nothing to do — the backend engine materializes on the first command.
    }

    override fun stop() {
        player.sendControl(KlangCommLink.Cmd.Cleanup(playbackId))
        player.unregisterPlayback(this)
    }
}
