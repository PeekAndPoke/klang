/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.SampleStore
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.master.MasterBus
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.PipelineDsl
import io.peekandpoke.klang.audio_bridge.RealtimeVoice
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.common.infra.KlangMinHeap
import io.peekandpoke.klang.common.math.ValueRamp
import io.peekandpoke.ultra.maths.Ease
import kotlin.random.Random

class VoiceScheduler(
    val options: Options,
) {
    companion object {
        /**
         * Upper bound of the held-voice gate horizon ([RealtimeVoice.gateDurSec] == null) — 10 h,
         * far longer than any session. The EFFECTIVE horizon is [heldGateHorizonSec], which caps
         * this by sample rate so Int frame counts cannot overflow.
         */
        const val REALTIME_HELD_GATE_SEC: Double = 36_000.0
    }

    /** A scheduler is per-playback now; the shared backend state arrives via [context]. */
    class Options(
        val context: AudioBackendContext,
        val cylinders: Cylinders,
        /** Sink for `master(…)` control events consumed during promotion. */
        val masterBus: MasterBus,
    )

    private val context = options.context
    private val masterBus = options.masterBus

    // Per-engine registry forks — custom oscs/engines for THIS playback live here and die with the
    // engine; the shared parent ([context]) keeps only the built-ins. See per-playback-engine.md (#2).
    private val ignitorFork = context.ignitorRegistry.fork()
    private val pipelineFork = context.pipelineRegistry.fork()

    // Heap with scheduled voices
    private val scheduled = KlangMinHeap<ScheduledVoice> { a, b -> a.startTime < b.startTime }

    /**
     * Where an active voice came from — a voice is EITHER a timeline voice or a realtime voice,
     * never both, and each variant carries exactly the identity its path needs.
     */
    private sealed interface VoiceOrigin {
        /**
         * Promoted from the scheduled timeline. [source] is the original (relative) event, kept so
         * the replace path can dedup a resent duplicate of a voice that has already been promoted
         * (see dedupAgainstActive).
         */
        data class Timeline(val source: ScheduledVoice) : VoiceOrigin

        /**
         * Started by [startRealtimeVoice]. [liveId] ([RealtimeVoice.liveId]) is the handle a stop
         * command releases. Realtime voices have no resendable source event — the replace-dedup
         * can never (and must never) match them.
         *
         * [held] = the voice was started with `gateDurSec == null` (gate open until a stop).
         * Fixed-gate realtime voices ([held] = false) end on their own and are NOT released by
         * [cleanup] — only an explicit [stopRealtimeVoice] may cut them short.
         */
        data class Realtime(val liveId: Int, val held: Boolean) : VoiceOrigin
    }

    // Wrapper to track playbackId and solo state alongside Voice
    private data class ActiveVoice(
        val voice: Voice,
        val playbackId: String,
        val soloAmount: Double,
        val sourceId: String?,
        val origin: VoiceOrigin,
    )

    // State with active voices
    private val active = ArrayList<ActiveVoice>(64)

    // Smooth gain transition for solo/mute
    private val soloMuteRamp = ValueRamp(initialValue = 1.0, duration = 1.5, ease = Ease.InOut.cubic)

    /**
     * Tracks solo state for source IDs with delayed cleanup.
     */
    private class SoloSourceTracker(val rampDurationSec: Double, val sampleRate: Int) {
        private data class SourceState(
            val sourceId: String,
            // Absolute backend frame — Double, see RenderClock.cursorFrame.
            val cleanupFrame: Double?,
        )

        private val sources = mutableMapOf<String, SourceState>()

        // Pre-allocated list for deferred mutations — avoids toList() allocation on the audio thread.
        // Entries are (sourceId, newState) pairs to apply after the read-only iteration.
        private val pendingUpdates = mutableListOf<Pair<String, SourceState>>()

        fun update(activeSoloSourceIds: Set<String>, currentFrame: Double): Set<String> {
            // Pass 1: find sources that need a cleanup frame — collect updates without mutating
            pendingUpdates.clear()
            for ((sourceId, state) in sources) {
                if (sourceId !in activeSoloSourceIds && state.cleanupFrame == null) {
                    val cleanupFrame = currentFrame + rampDurationSec * sampleRate
                    pendingUpdates.add(sourceId to state.copy(cleanupFrame = cleanupFrame))
                }
            }
            // Apply deferred mutations
            for ((sourceId, newState) in pendingUpdates) {
                sources[sourceId] = newState
            }

            for (sourceId in activeSoloSourceIds) {
                sources[sourceId] = SourceState(sourceId, cleanupFrame = null)
            }

            val iterator = sources.entries.iterator()
            while (iterator.hasNext()) {
                val (_, state) = iterator.next()
                if (state.cleanupFrame != null && currentFrame >= state.cleanupFrame) {
                    iterator.remove()
                }
            }

            return sources.keys
        }
    }

    private val soloSourceTracker = SoloSourceTracker(
        rampDurationSec = 2.0,
        sampleRate = context.sampleRate,
    )

    // Map playbackId -> per-playback context (registry, epoch, ...)
    private val playbackContexts = mutableMapOf<String, PlaybackCtx>()

    // Scratch buffers — pre-allocated to avoid per-block heap allocation on the audio thread
    private val voiceBuffer = AudioBuffer(context.blockFrames)
    private val freqModBuffer = DoubleArray(context.blockFrames)
    private val scratchBuffers = ScratchBuffers(context.blockFrames)
    private val activeSoloSourceIds = mutableSetOf<String>()

    // Context reused per block
    private val ctx = Voice.RenderContext(
        cylinders = options.cylinders,
        sampleRate = context.sampleRate,
        blockFrames = context.blockFrames,
        voiceBuffer = voiceBuffer,
        freqModBuffer = freqModBuffer,
        scratchBuffers = scratchBuffers,
    )

    // Voice factory — creates Voice instances from VoiceData
    private val voiceFactory = VoiceFactory(
        sampleRate = context.sampleRate,
        sampleRateDouble = context.sampleRateDouble,
        blockFrames = context.blockFrames,
        ignitorRegistry = ignitorFork,
        pipelineRegistry = pipelineFork,
        cylinders = options.cylinders,
        voiceBuffer = voiceBuffer,
        freqModBuffer = freqModBuffer,
        scratchBuffers = scratchBuffers,
    )

    fun clear() {
        scheduled.clear()
        active.clear()
        playbackContexts.clear()
    }

    fun addSample(msg: KlangCommLink.Cmd.Sample) = context.sampleStore.addSample(msg)

    fun getCompleteSample(req: SampleRequest): SampleStore.SampleEntry.Complete? =
        context.sampleStore.getComplete(req)

    fun getActiveVoiceCount(): Int = active.size

    /** Register a custom oscillator for THIS playback — lands on the per-engine fork, not the shared parent. */
    fun registerIgnitor(name: String, dsl: IgnitorDsl) = ignitorFork.register(name, dsl)

    /** Register a custom engine for THIS playback — per-engine fork (mirror of [registerIgnitor]). */
    fun registerPipeline(name: String, dsl: PipelineDsl) = pipelineFork.register(name, dsl)

    internal fun containsIgnitor(name: String): Boolean = ignitorFork.contains(name)

    internal fun resolvePipeline(name: String?): PipelineDsl = pipelineFork.get(name)

    fun cleanup(playbackId: String) {
        // HELD realtime voices would otherwise ring at full sustain to the held-gate horizon —
        // hours — and keep the engine un-drainable forever. Release them into their tails.
        // Fixed-gate realtime voices and timeline voices keep their natural ring-out, as before.
        for (activeVoice in active) {
            val origin = activeVoice.origin

            if (activeVoice.playbackId == playbackId && origin is VoiceOrigin.Realtime && origin.held) {
                releaseRealtimeVoice(activeVoice)
            }
        }

        playbackContexts.remove(playbackId)
        clearScheduled(playbackId)
    }

    /**
     * Hard-removes every trace of [playbackId]: scheduled, pending-sample, active, context.
     * Unlike [cleanup] this does not let currently-playing voices ring out — use it when the
     * caller needs a clean slate (e.g. the end of the warmup handshake).
     */
    fun cleanupHard(playbackId: String) {
        cleanup(playbackId)
        active.removeAll { it.playbackId == playbackId }
    }

    fun clearScheduled(playbackId: String) {
        scheduled.removeWhen { it.playbackId == playbackId }
    }

    fun replaceVoices(playbackId: String, voices: List<ScheduledVoice>, afterTimeSec: Double? = null) {
        if (afterTimeSec != null) {
            val epoch = playbackContexts[playbackId]?.epoch
            if (epoch != null) {
                val cutoffSec = epoch + afterTimeSec
                scheduled.removeWhen { voice ->
                    voice.playbackId == playbackId &&
                            (playbackContexts[voice.playbackId]?.epoch?.let { it + voice.startTime } ?: 0.0) >= cutoffSec
                }
            }
        } else {
            clearScheduled(playbackId)
        }

        scheduleVoices(dedupAgainstActive(voices))
    }

    /**
     * Drops incoming replacement voices that a still-playing voice already covers. The removal in
     * [replaceVoices] only clears the `scheduled` heap; a voice at/after the cutoff that was already
     * promoted to `active` (FE/BE clock skew + command latency) would otherwise be doubled by its
     * resend. We keep the playing voice and drop the duplicate — no click, no teardown.
     *
     * Matched by full identity ([ScheduledVoice.isDuplicate]: same `startTime`, structurally-equal
     * `data`) — so it is chord/`superimpose`-safe: legitimately simultaneous voices differ in `data`
     * and are never collapsed. 1-to-1 (each active voice absorbs at most one incoming) preserves
     * multiplicity. Only unchanged, re-derived-identical events dedup; a genuinely changed voice does
     * not match and is scheduled normally.
     */
    private fun dedupAgainstActive(voices: List<ScheduledVoice>): List<ScheduledVoice> {
        if (voices.isEmpty() || active.isEmpty()) return voices
        val claimed = BooleanArray(active.size)
        return voices.filter { incoming ->
            val match = active.indices.firstOrNull { i ->
                !claimed[i] && (active[i].origin as? VoiceOrigin.Timeline)?.source?.isDuplicate(incoming) == true
            }
            if (match != null) {
                claimed[match] = true
                false // already playing → drop the duplicate
            } else {
                true // schedule it
            }
        }
    }

    fun scheduleVoice(voice: ScheduledVoice) {
        ensureEpoch(voice)
        scheduled.push(voice)
        val cursor = context.clock.cursorFrame
        promoteScheduled(cursor, cursor + context.blockFrames)
        prefetchSampleSound(voice)
    }

    /**
     * Starts a [RealtimeVoice] immediately — promoted straight to active, never entering the
     * scheduled heap or the epoch machinery. The backend stamps "now" as the start time; a null
     * gate duration means "held" (released by a later stop command; until then the gate ends at
     * a far-but-finite horizon, see [REALTIME_HELD_GATE_SEC]).
     */
    fun startRealtimeVoice(playbackId: String, voice: RealtimeVoice) {
        // Invariant: control-only events never reach voice creation (same rule as the timeline path).
        if (voice.data.control == true) return

        // The cursor still points at the block ALREADY rendered — commands drain BETWEEN blocks
        // (see the promotion convention comment in VoiceFactory). The first frame that can
        // actually be rendered is one block later; stamping "now" there lets the attack enter
        // its curve at position 0 instead of silently losing its first block (audit R1).
        val nowFrame = context.clock.cursorFrame + context.blockFrames
        val nowSec = context.clock.secAt(nowFrame)
        val pCtx = ensureRealtimeCtx(playbackId, nowSec)

        val absolute = ScheduledVoice(
            playbackId = playbackId,
            data = voice.data,
            startTime = nowSec,
            gateEndTime = nowSec + (voice.gateDurSec ?: heldGateHorizonSec()),
            playbackStartTime = nowSec,
        )

        prefetchSampleSound(absolute)
        activateVoice(
            absoluteVoice = absolute,
            origin = VoiceOrigin.Realtime(liveId = voice.liveId, held = voice.gateDurSec == null),
            nowFrame = nowFrame,
            pCtx = pCtx,
        )
    }

    /**
     * Releases the gate of EVERY active realtime voice of [playbackId] carrying [liveId] (not
     * just the first — future layered instruments may start several voices per key). The voice
     * enters its ADSR release from the current level and dies right after the tail. Unknown
     * liveId is a no-op.
     */
    fun stopRealtimeVoice(playbackId: String, liveId: Int) {
        for (activeVoice in active) {
            val origin = activeVoice.origin

            if (activeVoice.playbackId == playbackId && origin is VoiceOrigin.Realtime && origin.liveId == liveId) {
                releaseRealtimeVoice(activeVoice)
            }
        }
    }

    /**
     * Releases one realtime voice with the two stamping rules of the realtime path:
     *
     * - Same one-block-stale cursor as the start path (audit R1): the release begins on the
     *   first frame that will actually render, so the tail enters its curve at position 0.
     *   Block-quantised by decision: live-input jitter dwarfs one block (<= 3 ms). See the
     *   "Decided semantics" in docs/tasks-archive/2026-08/20260829-realtime-note-off-gate-release.md.
     * - Floored one block after the voice's start: a note-on and note-off arriving in the SAME
     *   command drain stamp identical frames, and a gate exactly on startFrame would make the
     *   first rendered sample enter release from level 0 — the tap would be pure silence. With
     *   the floor, a zero-length tap renders one block of attack, then releases from there.
     */
    private fun releaseRealtimeVoice(activeVoice: ActiveVoice) {
        val releaseFrame = context.clock.cursorFrame + context.blockFrames
        val floor = activeVoice.voice.startFrame + context.blockFrames

        activeVoice.voice.releaseGate(maxOf(releaseFrame, floor))
    }

    /**
     * Gate horizon for held realtime voices, derived from the sample rate: far enough to outlast
     * any session, small enough that every voice-relative Int frame count
     * (`voiceDurationFrames`, `IgniteContext.gateEndFrame`) stays well inside Int at ANY sample
     * rate — 36 000 s is fine at 48 kHz but overflows at 96 kHz. Sample rate is a platform
     * variable, like block size.
     */
    private fun heldGateHorizonSec(): Double =
        minOf(REALTIME_HELD_GATE_SEC, (Int.MAX_VALUE / 2).toDouble() / context.sampleRate)

    /**
     * Batched schedule. All voices share a single nowSec snapshot for [ensureEpoch] / [promoteScheduled],
     * which prevents later voices in a tight cluster from sliding into the past while earlier voices
     * are being delivered (the per-voice send path would otherwise interleave with audio blocks).
     */
    fun scheduleVoices(voices: List<ScheduledVoice>) {
        if (voices.isEmpty()) return
        for (voice in voices) {
            ensureEpoch(voice)
            scheduled.push(voice)
            prefetchSampleSound(voice)
        }
        val cursor = context.clock.cursorFrame
        promoteScheduled(cursor, cursor + context.blockFrames)
    }

    // NB `cursorFrame` is Double, not Int: it is an ABSOLUTE frame on the backend timeline, which
    // grows for the life of the backend and overflows Int after ~12.4 h. Exact below 2^53
    // (~5,950 years at 48 kHz). See RenderClock.cursorFrame. Per-sample offsets stay Int.
    fun process(cursorFrame: Double) {
        val blockEnd = cursorFrame + context.blockFrames

        // 1. Promote scheduled to active
        promoteScheduled(cursorFrame, blockEnd)

        // 2. Prepare Context
        ctx.blockStart = cursorFrame

        // 2.5. Calculate solo/mute gain multipliers
        activeSoloSourceIds.clear()
        var maxSoloAmount = 0.0
        for (voice in active) {
            if (voice.soloAmount > 0.0 && voice.sourceId != null) {
                activeSoloSourceIds.add(voice.sourceId)
                maxSoloAmount = maxOf(maxSoloAmount, voice.soloAmount)
            }
        }

        val soloSourceIds = soloSourceTracker.update(activeSoloSourceIds, cursorFrame)
        val hasSoloSources = soloSourceIds.isNotEmpty()

        val targetGain = if (hasSoloSources) 1.0 - (maxSoloAmount * 0.95) else 1.0
        val blockDurationSec = context.blockFrames.toDouble() / context.sampleRateDouble
        val currentBackgroundGain = soloMuteRamp.step(targetGain, blockDurationSec)

        // 3. Render Loop
        var i = 0

        while (i < active.size) {
            val activeVoice = active[i]

            val isFromSoloSource = activeVoice.sourceId != null && activeVoice.sourceId in soloSourceIds
            if (isFromSoloSource) {
                activeVoice.voice.setGainMultiplier(1.0)
            } else {
                activeVoice.voice.setGainMultiplier(currentBackgroundGain)
            }

            val isAlive = activeVoice.voice.render(ctx)

            if (isAlive) {
                i++
            } else {
                if (i < active.size - 1) {
                    active[i] = active.last()
                }
                active.removeLast()
            }
        }
        // Diagnostics emission lives on the dispatcher now (D5): it always runs renderBlock — even
        // with zero engines — so the gauges can report idle/zero, and it times the WHOLE block.
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Private helpers
    // ═════════════════════════════════════════════════════════════════════════════

    private fun ensureEpoch(voice: ScheduledVoice) {
        val pid = voice.playbackId

        if (pid !in playbackContexts) {
            // Read the SHARED clock — i.e. the real current backend time — so the epoch snaps to "now"
            // and the first voice is not judged in the past. (A fresh engine has no per-scheduler cursor.)
            val nowSec = context.clock.nowSec()
            val latency = maxOf(0.0, nowSec - voice.playbackStartTime)
            playbackContexts[pid] = createPlaybackCtx(pid, nowSec, epoch = voice.playbackStartTime + latency)
        }
    }

    /**
     * PlaybackCtx for a realtime playback — there is no FE timeline to anchor against, so the
     * epoch is simply "now". Idempotent per playbackId (mirror of [ensureEpoch]).
     */
    private fun ensureRealtimeCtx(playbackId: String, nowSec: Double): PlaybackCtx =
        playbackContexts.getOrPut(playbackId) { createPlaybackCtx(playbackId, nowSec, epoch = nowSec) }

    private fun createPlaybackCtx(pid: String, nowSec: Double, epoch: Double): PlaybackCtx = PlaybackCtx(
        playbackId = pid,
        ignitorRegistry = ignitorFork,
        // The pool gets its OWN rng stream (deliberately voice-SHARED vocabulary —
        // distinct from the per-voice streams dealt from PlaybackCtx.coreRandom, the
        // seeded-voice-rng derivation tree), and creating it must not consume from
        // coreRandom (a draw here would shift every voice's seed). Live seeds from the
        // clock ("takes vary"); offline passes a fixed seed so pool vocabularies
        // reproduce.
        // ⚠️ nowSec is UNIX-EPOCH-scale live (~1.8e9): a naive `* 1e6 → toInt()` SATURATES
        // to Int.MAX_VALUE — a constant seed for every playback. Fold to sub-Int range and
        // mix in the playback id (two playbacks can share a render block's nowSec).
        phasePools = PhasePools(
            Random(
                context.phasePoolSeed
                    ?: (((nowSec % 4096.0) * 1e5).toInt() xor pid.hashCode()),
            ),
        ),
        epoch = epoch,
    )

    private fun prefetchSampleSound(voice: ScheduledVoice) {
        if (!ignitorFork.contains(voice.data.sound)) {
            context.sampleStore.requestIfMissing(voice.data.asSampleRequest(), voice.playbackId)
        }
    }

    private fun promoteScheduled(nowFrame: Double, blockEnd: Double) {
        val clock = context.clock
        val blockEndSec = clock.secAt(blockEnd)
        val nowSec = clock.secAt(nowFrame)
        val blockSizeSec = context.blockFrames.toDouble() / context.sampleRate.toDouble()
        val oldestAllowedSec = nowSec - (5 * blockSizeSec)

        while (true) {
            val head = scheduled.peek() ?: break

            val pCtx = playbackContexts[head.playbackId]
            if (pCtx == null) {
                scheduled.pop()
                continue
            }
            val epoch = pCtx.epoch

            val absoluteStartSec = epoch + head.startTime

            if (absoluteStartSec >= blockEndSec) break

            scheduled.pop()

            // Engine-level control data rides the voice stream (see docs/tasks/master-dsl.md):
            // a `master(…)` reference swaps this playback's master chain from here on. It applies
            // whether or not the event also sounds, so `note("c3").master(…)` does both.
            //
            // Applied BEFORE the guards below: a late *sound* is dropped because playing it now
            // would be wrong, but late *state* must still take effect — otherwise a worklet stall
            // silently loses a section's master for the rest of the playback.
            head.data.master?.let { masterBus.requestSwap(it) }

            // A control-only event has now been consumed — it must never reach voice creation.
            // The flag is explicit because a null `sound` is NOT silent: the ignitor registry
            // resolves it to the default oscillator.
            if (head.data.control == true) {
                continue
            }

            if (absoluteStartSec < oldestAllowedSec) {
                continue
            }

            val absoluteVoice = head.copy(
                startTime = absoluteStartSec,
                gateEndTime = epoch + head.gateEndTime,
            )

            activateVoice(absoluteVoice, origin = VoiceOrigin.Timeline(head), nowFrame = nowFrame, pCtx = pCtx)
        }
    }

    /**
     * Promotes ONE voice with ABSOLUTE times into the active list: applies cut/choke groups,
     * builds the Voice, and registers it. The shared tail of the timeline path
     * ([promoteScheduled]) and the realtime path ([startRealtimeVoice]).
     */
    private fun activateVoice(
        absoluteVoice: ScheduledVoice,
        origin: VoiceOrigin,
        nowFrame: Double,
        pCtx: PlaybackCtx,
    ) {
        // Handle Cut / Choke Groups before creating the new voice
        val cut = absoluteVoice.data.cut
        if (cut != null) {
            val iterator = active.iterator()
            while (iterator.hasNext()) {
                val activeVoice = iterator.next()
                if (activeVoice.voice.cut == cut) {
                    // TODO: Use a fade out / release phase instead of hard cut?
                    iterator.remove()
                }
            }
        }

        voiceFactory.makeVoice(
            scheduled = absoluteVoice,
            nowFrame = nowFrame,
            backendStartTimeSec = context.clock.startTimeSec,
            playbackCtx = pCtx,
            getSample = ::getCompleteSample,
        )?.let { voice ->
            val soloAmount = absoluteVoice.data.solo ?: 0.0
            val sourceId = absoluteVoice.data.sourceId
            active.add(ActiveVoice(voice, absoluteVoice.playbackId, soloAmount, sourceId, origin = origin))
        }
    }
}
