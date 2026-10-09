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
import io.peekandpoke.klang.audio_be.utils.retainInOrder
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.RealtimeVoice
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.common.infra.KlangMinHeap
import kotlin.random.Random

/**
 * The voice scheduler of ONE playback: its engine's scheduled heap, active voices, solo state and the context its
 * voices are built with ([PlaybackCtx]). Each `PlaybackEngine` owns one, and the
 * dispatcher routes every command by `playbackId` to its engine, so every voice a scheduler is handed belongs to the
 * one playback (the offline renderer has one engine, and its caller one playback id). Nothing here filters by
 * `playbackId` (audit item B3.3, tidy-up step 8).
 *
 * The playback's context is created with its first voice ([ensureEpoch], [startRealtimeVoice]), from that voice's
 * `playbackId`, which seeds the voices' random streams; [cleanup] drops it, and the next voice, a resume of the
 * stopped playback, creates a fresh one with a fresh epoch.
 */
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

        /** The background's solo ramp, in and out (cubic). Today's value, kept by default (maintainer to confirm). */
        const val SOLO_RAMP_SEC: Double = 1.5

        /**
         * How long a soloed source keeps its voices at full level after its last solo event ends. At least
         * [SOLO_RAMP_SEC], so its tail never dips while the others come back.
         */
        const val SOLO_HOLD_SEC: Double = 2.0

        /** Blocks a solo entry stays live past its end, so two back-to-back events never leave a hole at a seam. */
        const val SOLO_GRACE_BLOCKS: Int = 4
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
    private val cylinders = options.cylinders

    // Per-engine registry fork: custom Ignitors for THIS playback live here and die with the
    // engine; the shared parent ([context]) keeps only the built-ins. See docs/tasks-archive/2026-09/20260904-per-playback-engine.md (#2).
    private val ignitorFork = context.ignitorRegistry.fork()

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
         *
         * [solo] = the voice's solo amount ([VoiceData.solo]). The realtime path has no control events, so [process]
         * records it for the voice's source in every block in which the voice's gate is open.
         */
        data class Realtime(val liveId: Int, val held: Boolean, val solo: Double?) : VoiceOrigin
    }

    // Wrapper to track the source id (what solo protects) and the origin alongside Voice
    private data class ActiveVoice(
        val voice: Voice,
        val sourceId: String?,
        val origin: VoiceOrigin,
    )

    // State with active voices
    private val active = ArrayList<ActiveVoice>(64)

    // Smooth gain transition for solo/mute: the level of every voice whose source is not soloed
    private val soloMuteRamp = SoloRamp(initialValue = 1.0, durationSec = SOLO_RAMP_SEC)

    // Who is soloed, at which amount, until when (per playback: this scheduler is the playback's)
    private val soloTracker = SoloTracker(
        holdSec = SOLO_HOLD_SEC,
        graceSec = SOLO_GRACE_BLOCKS * context.blockFrames / context.sampleRateDouble,
    )

    // The playback's context (registry, epoch, random streams, ...): null until its first voice, and again after
    // [cleanup] until the next one.
    private var playback: PlaybackCtx? = null

    // Scratch buffers — pre-allocated to avoid per-block heap allocation on the audio thread
    private val voiceBuffer = AudioBuffer(context.blockFrames)
    private val freqModBuffer = DoubleArray(context.blockFrames)
    // The ONE shared scratch pool (resource warehouse, step 2a). Engines render sequentially within a
    // block, so one pool serves every playback, and its depth — reached once, kept forever — is
    // never paid again by the next playback or the one after warmup.
    private val scratchBuffers = context.warehouse.scratch

    /** The pool this scheduler renders with, for the spec that proves it is the shared one. */
    internal val scratchBuffersForTest: ScratchBuffers get() = scratchBuffers

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
        blockFrames = context.blockFrames,
        voiceBuffer = voiceBuffer,
        freqModBuffer = freqModBuffer,
        scratchBuffers = scratchBuffers,
    )

    fun addSample(msg: KlangCommLink.Cmd.Sample) = context.sampleStore.addSample(msg)

    fun getCompleteSample(req: SampleRequest): SampleStore.SampleEntry.Complete? =
        context.sampleStore.getComplete(req)

    fun getActiveVoiceCount(): Int = active.size

    // Voices whose release stayed silent through the cull window (Voice.culled): they end at that
    // block, long before their scheduled end, and leave [active] like any finished voice.
    private var culledVoices: Int = 0

    /**
     * Voices culled since this scheduler was created, counted at the block the cull ended them.
     * Read by the song benchmark (its `culled` column); not yet on the diagnostics feed,
     * which reports dropped voices only.
     */
    fun culledVoicesTotal(): Int = culledVoices

    /**
     * The voice data of the active timeline voices, in list order, one entry per voice (every listed
     * voice renders: a culled voice has left the list). A diagnostic for the song benchmark's work columns: it allocates, so it is
     * never called on the render path. A live (non-timeline) voice has no scheduled data and is
     * left out.
     */
    fun renderingVoiceData(): List<VoiceData> {
        val out = ArrayList<VoiceData>(active.size)

        for (i in 0 until active.size) {
            val origin = active[i].origin

            if (origin is VoiceOrigin.Timeline) {
                out.add(origin.source.data)
            }
        }

        return out
    }

    /**
     * Voices of the playback dropped at admission because their start had already been rendered
     * past (block-framing B2), counted since its context was created (0 without one). Zero on a healthy
     * link; a rising count is the only visible trace of a frontend stall now that late voices are
     * dropped rather than smeared. Read by the stats feed.
     */
    fun droppedVoiceCount(): Int = playback?.droppedVoices ?: 0

    /** Register a custom oscillator for THIS playback — lands on the per-engine fork, not the shared parent. */
    fun registerIgnitor(name: String, dsl: IgnitorDsl) = ignitorFork.register(name, dsl)

    internal fun containsIgnitor(name: String): Boolean = ignitorFork.contains(name)

    /**
     * The playback stops: its held realtime voices are released, its context and its scheduled voices are dropped,
     * and the voices already playing ring out.
     */
    fun cleanup() {
        // HELD realtime voices would otherwise ring at full sustain to the held-gate horizon —
        // hours — and keep the engine un-drainable forever. Release them into their tails.
        // Fixed-gate realtime voices and timeline voices keep their natural ring-out, as before.
        for (i in 0 until active.size) {
            val activeVoice = active[i]
            val origin = activeVoice.origin

            if (origin is VoiceOrigin.Realtime && origin.held) {
                releaseRealtimeVoice(activeVoice)
            }
        }

        playback = null
        clearScheduled()
    }

    /**
     * Hard-removes every trace of the playback: scheduled, active, context.
     * Unlike [cleanup] this does not let currently-playing voices ring out — use it when the
     * caller needs a clean slate (e.g. the end of the warmup handshake).
     */
    fun cleanupHard() {
        cleanup()

        // The hard kill is an event on the voice; the removal takes only what is Done.
        for (i in 0 until active.size) {
            active[i].voice.kill()
        }

        removeDoneVoices()
    }

    /**
     * Removes the `Done` voices between blocks. The scheduler removes only `Done` voices, and always keeping the
     * survivors' order (one law, lifecycle step 5): here, for voices sent to `Done` between blocks (a hard kill,
     * [Voice.kill], must leave at once because the engine is disposed right after [cleanupHard]; a voice a cut
     * finds `Pending`), and in the render loop of [process], for a voice that ends while it renders. Both use
     * [retainInOrder], one pass and no allocation. The list therefore stays in activation order. Between blocks
     * no other voice is `Done`.
     */
    private fun removeDoneVoices() {
        active.retainInOrder { it.voice.state !is Voice.State.Done }
    }

    /** Drops every voice not yet promoted. */
    fun clearScheduled() {
        scheduled.clear()
    }

    fun replaceVoices(voices: List<ScheduledVoice>, afterTimeSec: Double? = null) {
        if (afterTimeSec != null) {
            val epoch = playback?.epoch
            if (epoch != null) {
                val cutoffSec = epoch + afterTimeSec
                scheduled.removeWhen { voice -> epoch + voice.startTime >= cutoffSec }
            }
        } else {
            clearScheduled()
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
        promoteScheduled(nowFrame = cursor, blockEnd = cursor + context.blockFrames)
        prefetchSampleSound(voice)
    }

    /**
     * Starts a [RealtimeVoice] immediately — promoted straight to active, never entering the
     * scheduled heap or the epoch machinery. The backend stamps "now" as the start time; a null
     * gate duration means "held" (released by a later stop command; until then the gate ends at
     * a far-but-finite horizon, see [REALTIME_HELD_GATE_SEC]). [playbackId] is the playback's id (a realtime voice
     * carries none of its own): the context is made from it when this is the playback's first voice.
     */
    fun startRealtimeVoice(playbackId: String, voice: RealtimeVoice) {
        // Invariant: control-only events never reach voice creation (same rule as the timeline path).
        if (voice.data.control == true) return

        // The cursor still points at the block ALREADY rendered — commands drain BETWEEN blocks
        // (see the promotion convention comment in VoiceFactory). Between renders the clock IS
        // the first frame that can still be rendered (RenderClock.cursorFrame, block-framing B1),
        // so stamping "now" there lets the attack enter its curve at position 0 instead of
        // silently losing its first block (audit R1). This used to add a block to compensate for
        // a clock that lagged one behind; the lag is gone, and so is the compensation.
        val nowFrame = context.clock.cursorFrame
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
            origin = VoiceOrigin.Realtime(liveId = voice.liveId, held = voice.gateDurSec == null, solo = voice.data.solo),
            pCtx = pCtx,
        )
    }

    /**
     * Releases the gate of EVERY active realtime voice carrying [liveId] (not
     * just the first — future layered instruments may start several voices per key). The voice
     * enters its ADSR release from the current level and dies right after the tail. Unknown
     * liveId is a no-op.
     */
    fun stopRealtimeVoice(liveId: Int) {
        for (i in 0 until active.size) {
            val activeVoice = active[i]
            val origin = activeVoice.origin

            if (origin is VoiceOrigin.Realtime && origin.liveId == liveId) {
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
        // Same convention as [startRealtimeVoice]: the clock is already the next renderable frame.
        val releaseFrame = context.clock.cursorFrame
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
        for (i in 0 until voices.size) {
            val voice = voices[i]

            ensureEpoch(voice)
            scheduled.push(voice)
            prefetchSampleSound(voice)
        }
        val cursor = context.clock.cursorFrame
        promoteScheduled(nowFrame = cursor, blockEnd = cursor + context.blockFrames)
    }

    // NB `cursorFrame` is Double, not Int: it is an ABSOLUTE frame on the backend timeline, which
    // grows for the life of the backend and overflows Int after ~12.4 h. Exact below 2^53
    // (~5,950 years at 48 kHz). See RenderClock.cursorFrame. Per-sample offsets stay Int.
    fun process(cursorFrame: Double) {
        val blockEnd = cursorFrame + context.blockFrames

        // 1. Promote scheduled to active
        promoteScheduled(nowFrame = cursorFrame, blockEnd = blockEnd)

        // 2. Prepare Context
        ctx.blockStart = cursorFrame

        // 2.5. Solo: the tracker (recorded at promotion, and here for the held realtime voices) says who is
        // protected and what the others play at, `1 - amount` of the strongest live solo, reached on the ramp.
        recordRealtimeSolo(cursorFrame = cursorFrame, blockEnd = blockEnd)
        soloTracker.advance(nowSec = context.clock.secAt(cursorFrame))

        val blockDurationSec = context.blockFrames.toDouble() / context.sampleRateDouble
        val currentBackgroundGain = soloMuteRamp.step(target = soloTracker.targetGain(), dt = blockDurationSec)

        // 3. Render Loop. A voice that ends here (render returns false: `Done`) leaves the list in the same pass
        // ([retainInOrder], as [removeDoneVoices]): the survivors keep their order.
        active.retainInOrder { activeVoice ->
            if (soloTracker.isProtected(activeVoice.sourceId)) {
                activeVoice.voice.setGainMultiplier(1.0)
            } else {
                activeVoice.voice.setGainMultiplier(currentBackgroundGain)
            }

            val isAlive = activeVoice.voice.render(ctx)

            if (!isAlive && activeVoice.voice.culled) {
                culledVoices++
            }

            isAlive
        }

        // Diagnostics emission lives on the dispatcher now (D5): it always runs renderBlock — even
        // with zero engines — so the gauges can report idle/zero, and it times the WHOLE block.
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Private helpers
    // ═════════════════════════════════════════════════════════════════════════════

    private fun ensureEpoch(voice: ScheduledVoice) {
        if (playback == null) {
            // Read the SHARED clock so the epoch snaps to "now" and the first voice is not judged in
            // the past. "Now" is the NEXT block to be rendered (RenderClock.cursorFrame) — which is
            // what makes this safe under no-late-voices: before B1 the clock lagged one block and
            // every playback's first note was exactly one block late.
            val nowSec = context.clock.nowSec()
            val latency = maxOf(0.0, nowSec - voice.playbackStartTime)
            playback = createPlaybackCtx(pid = voice.playbackId, nowSec = nowSec, epoch = voice.playbackStartTime + latency)
        }
    }

    /**
     * PlaybackCtx for a realtime playback — there is no FE timeline to anchor against, so the
     * epoch is simply "now". Idempotent (mirror of [ensureEpoch]): a context the timeline made is kept.
     */
    private fun ensureRealtimeCtx(playbackId: String, nowSec: Double): PlaybackCtx =
        playback ?: createPlaybackCtx(pid = playbackId, nowSec = nowSec, epoch = nowSec).also { playback = it }

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

        while (true) {
            val head = scheduled.peek() ?: break

            val pCtx = playback
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
            // The orbit twin (Katalyst step 3a): a `katalyst(…)` reference selects the chain the
            // event's ORBIT runs from here on. Same rule as the master's above, one line down
            // because it is the same kind of engine-level state, and the default orbit is the one
            // `VoiceFactory` resolves for a voice that names none.
            head.data.katalyst?.let { cylinders.requestChain(orbit = head.data.cylinder ?: 0, name = it) }
            // Solo state, the same kind of engine state and the same rule: "this source is soloed at this amount
            // until this event ends", from a control event (what `solo(...)` puts over a rest) or a sounding note.
            recordSolo(head.data, untilSec = epoch + head.gateEndTime)

            // A control-only event has now been consumed — it must never reach voice creation.
            // The flag is explicit because a null `sound` is NOT silent: the ignitor registry
            // resolves it to the default oscillator.
            if (head.data.control == true) {
                continue
            }

            // NO LATE VOICES, EVER (block-framing B2, 2026-09-03). The DSP is written against the
            // contract that a voice's first generate() has voiceElapsedFrames == 0; the scheduler
            // guarantees it here. A voice whose start has already been rendered past is dropped and
            // counted — observability, not a clamp. This replaces a 5-block tolerance window that
            // admitted such voices LATE: oscillator phase fresh, envelope already blocks in, neither
            // on time nor shifted, and two silent-note bugs reachable only in that state.
            if (!(absoluteStartSec >= nowSec)) { // NaN-guard: a non-finite start is dropped like a late one
                pCtx.droppedVoices++
                continue
            }

            val absoluteVoice = head.copy(
                startTime = absoluteStartSec,
                gateEndTime = epoch + head.gateEndTime,
            )

            activateVoice(absoluteVoice, origin = VoiceOrigin.Timeline(head), pCtx = pCtx)
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
        pCtx: PlaybackCtx,
    ) {
        // Cut / choke groups, before the new voice exists (so it never cuts itself): the scheduler decides WHO
        // (every active voice of the group), the voice decides WHAT by its state (`Voice.cutOff`: a silent one is
        // Done at once, a sounding one fades from the cutting voice's onset). The Done ones leave here,
        // order-preserving (F3); a fading one leaves through the render loop once its fade has ended.
        val cut = absoluteVoice.data.cut

        if (cut != null) {
            val fadeStartFrame = voiceFactory.onsetFrame(absoluteVoice, context.clock.startTimeSec)

            for (i in 0 until active.size) {
                val voice = active[i].voice

                if (voice.cut == cut) {
                    voice.cutOff(fadeStartFrame)
                }
            }

            removeDoneVoices()
        }

        voiceFactory.makeVoice(
            scheduled = absoluteVoice,
            backendStartTimeSec = context.clock.startTimeSec,
            playbackCtx = pCtx,
            getSample = ::getCompleteSample,
        )?.let { voice ->
            active.add(
                ActiveVoice(
                    voice = voice,
                    sourceId = absoluteVoice.data.sourceId,
                    origin = origin,
                )
            )
        }
    }

    /**
     * The realtime path's solo: every realtime voice whose gate is open at the block start records its source until
     * the block's end, so the solo lasts while ANY voice of the source is held and ends the grace after the last gate
     * closes (one block more only for a fixed gate closing inside a block; a note-off lands on a block boundary): a note-off, a cut, a voice that ended on its own. A note that made no voice records
     * nothing. Timeline voices are not read here: their solo comes from the events, control events included.
     */
    private fun recordRealtimeSolo(cursorFrame: Double, blockEnd: Double) {
        val untilSec = context.clock.secAt(blockEnd)

        for (i in 0 until active.size) {
            val activeVoice = active[i]
            val origin = activeVoice.origin
            val sourceId = activeVoice.sourceId

            if (origin is VoiceOrigin.Realtime && origin.solo != null && sourceId != null &&
                activeVoice.voice.gateOpenAt(cursorFrame)
            ) {
                soloTracker.record(sourceId = sourceId, amount = origin.solo, untilSec = untilSec)
            }
        }
    }

    /** Records [data]'s solo amount for its source until [untilSec]; nothing without both (see [SoloTracker.record]). */
    private fun recordSolo(data: VoiceData, untilSec: Double) {
        val amount = data.solo ?: return
        val sourceId = data.sourceId ?: return

        soloTracker.record(sourceId = sourceId, amount = amount, untilSec = untilSec)
    }
}
