/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.PlaybackEngine.Phase
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.common.infra.KlangMap

/**
 * The backend host: routes inbound [KlangCommLink.Cmd]s and renders the final block.
 *
 * It owns the [AudioBackendContext] (shared services + the read-only clock), the **mutable**
 * [BackendClock] it advances each block, the final [HouseStage], and a
 * `Map<playbackId, PlaybackEngine>` — one fully-isolated engine per playback, created lazily and
 * disposed once told to stop ([cleanup]) and fully drained. Both platform backends shrink to a thin
 * pump: drain commands → [renderBlock] → convert/output → forward feedback. See
 * KLANG-00176 (per playback engine).
 *
 * An engine's end of life is its own [PlaybackEngine.phase] (the transition table is in its KDoc): the dispatcher
 * asks it whether a scheduled-again engine resumes (`Stopped`) or is detached (`Releasing`, `Released`), and whether
 * a stopped one may be disposed ([PlaybackEngine.isIdle]). It keeps no lifecycle of its own, only the two orders the
 * output depends on: the render order ([rendering]) and the disposal order ([ending]). The disposal order costs one
 * extra list and decides which pooled reverb or delay unit a later rent gets when several engines end in the same
 * block; it stays as it is (maintainer, 2026-10-13, Q19): that case almost never happens and is not worth simplifying.
 *
 * **The net** (audit L8-03, L9-01): a throw from ONE playback's engine, in its block ([renderBlock]) or in a command
 * routed to it ([handle]), is caught here and costs that playback only ([fail]): the engine leaves the render and is
 * disposed (its phase is `Disposed`), the frontend is told once ([KlangCommLink.Feedback.PlaybackFailed]), and every
 * other playback plays on. The failed engine stays filed under its id, so the rest of that playback's commands find
 * it and are ignored instead of starting a fresh engine that would replay half a song; the playback's `Cleanup`
 * removes it, and the id may then start afresh. An attached engine is `Disposed` only this way (a stopped engine
 * leaves [engines] before it is disposed). The normal path pays one `try` per engine per block and one per command,
 * and nothing allocates unless something was thrown. Each host keeps a last catch of its own for a throw from
 * outside an engine (the house stage), so the audio node itself never dies.
 */
class PlaybackEngineDispatcher(
    private val context: AudioBackendContext,
    private val clock: BackendClock,
) {
    // playbackId -> the engine filed under it (attached). A KlangMap: deterministic order, the creation order, and a
    // lookup that allocates nothing on Kotlin/JS (every scheduled voice looks its playback up; engine follow-up 2).
    // A HashMap here costs about 40 B per scheduled voice on V8, at the edge of E-C-n1's line: this comment guards it.
    private val engines = KlangMap<PlaybackEngine>()

    // Every engine that renders, in render order (the order they sum into the mix in, so it is kept exactly): the
    // attached ones in [engines] order, then the DETACHED ones in the order they were detached. A detached engine
    // was releasing when its playbackId was scheduled again: a release has no way back (it would step up), so the
    // engine leaves [engines], finishes its release here, and the id gets a fresh engine. An index loop over a list
    // allocates nothing per block; a map iterator does on Kotlin/JS.
    private val rendering = ArrayList<PlaybackEngine>()

    // The engines past `Playing` and not yet disposed, in the order the sweep ([disposeDrainedEngines]) disposes them
    // when several go idle in one block: the detached ones newest first, then the stopped attached ones in the order
    // they were stopped. Disposal returns their units to the warehouse's shelves, which later rents read in order, so
    // this order is kept exactly. It has no reason of its own: it is the order the sweep had before tidy-up step 9,
    // pinned so the step stays bit-identical; it may change only in a step that accepts new bits. Order only: what each engine is, its phase says. An engine joins at its stop
    // ([cleanup]), moves to the front when detached, and leaves when resumed, disposed or hard-cleaned.
    private val ending = ArrayList<PlaybackEngine>()

    private val mix = StereoBuffer(context.blockFrames)
    private val house = HouseStage(sampleRate = context.sampleRate, blockFrames = context.blockFrames)

    // Aggregated diagnostics (D5) — emission moved here from per-engine VoiceScheduler so the gauges
    // still update when the engine map is empty (idle → zeros), and headroom times the WHOLE block.
    // Counted in frames rendered, not wall time (KLANG-00399): the frontend's clock runs on these reports, so a
    // backend that renders less reports less, and a wall clock stepping back cannot silence them.
    private var framesSinceDiagnostics = 0
    private val diagnosticsEveryFrames = (context.sampleRateDouble * DIAGNOSTICS_EVERY_SEC).toInt()
    private var avgHeadroom = 1.0

    // The counts of one emission ([emitDiagnostics]), summed over the engines by [countDiagnostics].
    private var diagnosticVoices = 0
    private var diagnosticDroppedVoices = 0
    private var diagnosticSoundlessVoices = 0
    private var diagnosticDeniedRents = 0

    /**
     * The milliseconds from the timeline to the speaker that only the host knows, carried in every report
     * ([KlangCommLink.Feedback.Diagnostics.outputLatencyMs]): the JVM host sets its line's buffer plus the house
     * limiter's lookahead (audit L8-08: it reported none, and the UI's time ran a quarter second ahead of the sound).
     * The browser leaves it at 0 in the worklet and fills the field on the main thread, where the context's figures
     * are (`JsAudioBackend`). Written by the host once, before it renders.
     */
    var outputLatencyMs: Double = 0.0

    val ignitorRegistry: IgnitorRegistry get() = context.ignitorRegistry
    val sampleStore: SampleStore get() = context.sampleStore

    /**
     * Sets the backend epoch (the one audio timeline). Call **once at startup**, before any block is
     * rendered — the cursor advances from here.
     */
    fun setBackendStartTime(startTimeSec: Double) {
        clock.startTimeSec = startTimeSec
    }

    private fun engineFor(playbackId: String): PlaybackEngine {
        // One lookup per call (engine follow-up 2, step 3, when `engines` was a HashMap at 40 bytes per lookup on
        // Kotlin/JS). Since step 5 it is a KlangMap and a lookup allocates nothing; the field's comment guards that.
        val attached = engines[playbackId] ?: return createEngine(playbackId)
        val staysAttached = scheduledAgain(attached)

        if (staysAttached) {
            return attached
        }

        return createEngine(playbackId)
    }

    /**
     * The engine filed under [playbackId], created if there is none, as a schedule finds it ([engineFor]): for a host
     * in this module that schedules into the engine's scheduler directly instead of over the wire
     * ([KlangAudioRenderer], the benchmarks' single-engine render), so its blocks are this dispatcher's.
     */
    internal fun openEngine(playbackId: String): PlaybackEngine = engineFor(playbackId)

    /**
     * [engine]'s playback is scheduled again. A stopped one resumes, which cancels its pending disposal (e.g. a
     * resume after a pause); a releasing one is detached, and the id gets a fresh engine ([engineFor]). True when
     * [engine] stays filed under its id.
     */
    private fun scheduledAgain(engine: PlaybackEngine): Boolean = when (engine.phase) {
        Phase.Playing, Phase.Disposed -> true

        Phase.Stopped -> {
            engine.resume()
            ending.remove(engine)
            true
        }

        Phase.Releasing, Phase.Released -> {
            detach(engine)
            false
        }
    }

    /** Takes the releasing [engine] off its id: it finishes its release among the detached ones, then is disposed. */
    private fun detach(engine: PlaybackEngine) {
        engines.delete(engine.playbackId)
        rendering.remove(engine)
        rendering.add(engine)
        ending.remove(engine)
        ending.add(0, engine)
    }

    /** A fresh engine for [playbackId], rendering after the attached ones and before the detached ones. */
    private fun createEngine(playbackId: String): PlaybackEngine {
        val engine = PlaybackEngine.create(context = context, playbackId = playbackId)

        rendering.add(engines.size, engine)
        engines[playbackId] = engine

        return engine
    }

    /**
     * Route a single inbound command to the engine for its `playbackId` (creating it on demand), inside the net (the
     * class KDoc): a throw fails that playback, and a failed playback ignores its commands until its `Cleanup`.
     */
    fun handle(cmd: KlangCommLink.Cmd) {
        val attached = engines[cmd.playbackId]

        // A failed playback (see the class KDoc, "The net") ignores everything until its stop, which forgets it.
        if (attached != null && attached.phase == Phase.Disposed) {
            if (cmd is KlangCommLink.Cmd.Cleanup) {
                engines.delete(cmd.playbackId)
            }

            return
        }

        try {
            route(cmd)
        } catch (e: Throwable) {
            // The engine the command reached is the one filed under its id now (a command never reaches a detached one).
            val engine = engines[cmd.playbackId]
            val reason = "command ${cmd::class.simpleName}: $e"

            if (engine != null) {
                fail(engine = engine, reason = reason)
            } else {
                report(playbackId = cmd.playbackId, reason = reason)
            }
        }
    }

    /**
     * Routes [cmd] to its engine; [handle] is the net around it. Exhaustive `when` *expression*: a new
     * [KlangCommLink.Cmd] subtype fails the build until handled.
     */
    private fun route(cmd: KlangCommLink.Cmd): Unit = when (cmd) {
        is KlangCommLink.Cmd.ScheduleVoices ->
            scheduleVoices(cmd.playbackId, cmd.voices)

        is KlangCommLink.Cmd.StartRealtimeVoice ->
            engineFor(cmd.playbackId).scheduler.startRealtimeVoice(cmd.playbackId, cmd.voice)

        // Targets an EXISTING playback — a stop must never create (or revive) an engine.
        is KlangCommLink.Cmd.StopRealtimeVoice ->
            engines[cmd.playbackId]?.scheduler?.stopRealtimeVoice(cmd.liveId) ?: Unit

        is KlangCommLink.Cmd.ReplaceVoices ->
            replaceVoices(cmd.playbackId, cmd.voices, cmd.afterTimeSec)

        is KlangCommLink.Cmd.Cleanup ->
            cleanup(cmd.playbackId)

        is KlangCommLink.Cmd.Sample ->
            context.sampleStore.addSample(cmd)

        is KlangCommLink.Cmd.RegisterIgnitor ->
            engineFor(cmd.playbackId).scheduler.registerIgnitor(cmd.name, cmd.dsl)

        is KlangCommLink.Cmd.RegisterKatalyst ->
            engineFor(cmd.playbackId).registerKatalyst(cmd.name, cmd.dsl)

        is KlangCommLink.Cmd.SetGain ->
            setGain(playbackId = cmd.playbackId, gain = cmd.gain, rampSec = cmd.rampSec)
    }

    /**
     * Never through [engineFor], which resumes a stopped engine: a fade sent to a song that just stopped must not
     * revive it. The engine is created when none is filed under the id, because a start sends its gain BEFORE its
     * first voices; the frontend's playback is the gate that sends it only while started (`Cmd.SetGain`'s KDoc).
     *
     * An engine still releasing a stopped playback is detached, as a schedule does ([scheduledAgain]), and the gain goes
     * to a fresh engine for the start it comes before: the release fades on undisturbed, the new start has its gain.
     */
    private fun setGain(playbackId: String, gain: Double, rampSec: Double) {
        val attached = engines[playbackId]

        val engine = if (attached == null) {
            createEngine(playbackId)
        } else {
            when (attached.phase) {
                Phase.Playing, Phase.Stopped, Phase.Disposed -> attached

                Phase.Releasing, Phase.Released -> {
                    detach(attached)
                    createEngine(playbackId)
                }
            }
        }

        engine.setGain(gain = gain, rampSec = rampSec)
    }

    private fun scheduleVoices(playbackId: String, voices: List<ScheduledVoice>) {
        if (voices.isNotEmpty()) {
            engineFor(playbackId).scheduler.scheduleVoices(voices)
        }
    }

    private fun replaceVoices(playbackId: String, voices: List<ScheduledVoice>, afterTimeSec: Double?) {
        // Replace targets an EXISTING playback — never lazily create an engine here, so a
        // "replace with nothing" cannot materialize (and then leak) an empty engine.
        val engine = engines[playbackId] ?: return

        when (engine.phase) {
            Phase.Playing -> engine.scheduler.replaceVoices(voices = voices, afterTimeSec = afterTimeSec)

            // A stopped playback stays stopped: a live edit that arrives after the stop is ignored, never scheduled
            // into the stopped engine nor a resume (maintainer, 2026-11-05, KLANG-00290 (engine follow-ups) item 15).
            // A start schedules through [engineFor], which resumes.
            Phase.Stopped, Phase.Releasing, Phase.Released, Phase.Disposed -> Unit
        }
    }

    /**
     * Stop scheduling for a playback: its open gates get their note-off, and it rings out; disposed once drained (see
     * [renderBlock], `VoiceScheduler.cleanup`).
     */
    private fun cleanup(playbackId: String) {
        val engine = engines[playbackId] ?: return

        engine.scheduler.cleanup()

        // A first stop joins the disposal order at its end; a second one keeps the place of the first.
        if (engine.phase == Phase.Playing) {
            ending.add(engine)
        }

        engine.stop()
    }

    /** Immediate disposal (warmup teardown) — does not let voices ring out. */
    fun cleanupHard(playbackId: String) {
        val engine = engines.delete(playbackId) ?: return

        // A failed engine ([fail]) is disposed already: forgetting it is all that is left.
        if (engine.phase == Phase.Disposed) {
            return
        }

        rendering.remove(engine)
        ending.remove(engine)
        engine.scheduler.cleanupHard()
        engine.dispose()
    }

    /**
     * Render one block to [out] (`blockFrames` frames per channel): sum every engine into the shared
     * mix, then run the house stage, which writes the clipped floating-point output.
     */
    // NB `cursorFrame` is Double, not Int: it is an ABSOLUTE frame on the backend timeline, which
    // grows for the life of the backend and overflows Int after ~12.4 h. Exact below 2^53
    // (~5,950 years at 48 kHz). See RenderClock.cursorFrame. Per-sample offsets stay Int.
    fun renderBlock(cursorFrame: Double, out: StereoBuffer) {
        val startMs = context.performanceTimeMs()
        clock.cursorFrame = cursorFrame
        mix.clear()

        try {
            renderBlockAt(cursorFrame = cursorFrame, out = out, startMs = startMs)
        } finally {
            // The clock convention (see RenderClock.cursorFrame): between renders it is the NEXT
            // block. Advanced in `finally` so an exception mid-block cannot leave "now" in the past.
            clock.cursorFrame = cursorFrame + context.blockFrames
        }
    }

    private fun renderBlockAt(cursorFrame: Double, out: StereoBuffer, startMs: Double) {

        // processAndMix accumulates additively, so engines simply render into the same mix in turn.
        // (#11: with one engine this is a straight render into the final mix.) An engine with a master chain or a
        // gain that is not 1 renders into its own bus first and adds that (`PlaybackEngine.renderInto`).
        var i = 0

        while (i < rendering.size) {
            // A failed engine leaves the list, so the next one moves into its index.
            if (renderEngine(engine = rendering[i], cursorFrame = cursorFrame)) {
                i++
            }
        }

        house.process(mix = mix, out = out)

        disposeDrainedEngines()
        // Deferred clearing of returned rings and networks, a bounded slice per block (round 3).
        context.warehouse.housekeep()
        emitDiagnostics(startMs = startMs, blockEndFrame = cursorFrame + context.blockFrames)
    }

    /**
     * Renders [engine] into the shared mix; false when it threw and was taken out of the render ([fail]). What it
     * added to the mix before the throw stays in this block.
     */
    private fun renderEngine(engine: PlaybackEngine, cursorFrame: Double): Boolean {
        try {
            engine.renderInto(mix, cursorFrame)

            return true
        } catch (e: Throwable) { // hot-path-lint: allow exception the net: one engine's throw costs that playback only
            // hot-path-lint: allow alloc-string only once a throw happened, which ends this engine's render for good
            fail(engine = engine, reason = "render: $e")

            return false
        }
    }

    /**
     * The net's one action (the class KDoc, "The net"): [engine] threw. It leaves the render and the disposal order,
     * the frontend is told, and it is disposed: its voices are killed and its units go back to the warehouse. An
     * attached engine stays filed under its id, `Disposed`, until the playback's `Cleanup`; a detached one is gone.
     * Its state after the throw is not trusted: nothing of it renders again.
     */
    private fun fail(engine: PlaybackEngine, reason: String) {
        rendering.remove(engine)
        ending.remove(engine)
        report(playbackId = engine.playbackId, reason = reason)
        engine.scheduler.cleanupHard()
        engine.dispose()
    }

    /** Tells the frontend that [playbackId] failed ([KlangCommLink.Feedback.PlaybackFailed]). */
    // hot-path-lint: cold runs once per failure, never on the path that does not throw
    private fun report(playbackId: String, reason: String) {
        context.commLink.feedback.send(KlangCommLink.Feedback.PlaybackFailed(playbackId = playbackId, reason = reason))
    }

    /**
     * Emit one aggregate [KlangCommLink.Feedback.Diagnostics] per [DIAGNOSTICS_EVERY_SEC] of audio rendered. Runs
     * every block regardless of engine count, so a fully-idle backend reports zero voices / zero cylinders (the
     * gauges drop to 0 on stop) and the headroom reflects the WHOLE block (all engines + the house stage), not one scheduler.
     * It carries the timeline at [blockEndFrame], the end of the block just rendered.
     *
     * **A periodic allocation on the audio thread, accepted** (it departs from "no allocation in audio hot paths"): one
     * list, one `CylinderState` per rented cylinder and one message per report, about 47 a second, idle or not, and in
     * the worklet one encode and one structured clone of them. Measured on the JVM (audit L1-06, L8-05, L9-08,
     * `AuditRealtimeRepro`): 104 B and under 0.1 us per report idle, 352 B and 0.6 to 1.25 us with 8 cylinders, nothing
     * in the blocks between. The report is the frontend's clock, so it cannot be sent less often; sending the gauges
     * apart from the clock would change what the frontend reads (KLANG-00431, Decide).
     */
    private fun emitDiagnostics(startMs: Double, blockEndFrame: Double) {
        val endMs = context.performanceTimeMs()
        val durationMs = endMs - startMs
        val blockDurationMs = (context.blockFrames.toDouble() / context.sampleRateDouble) * 1000.0
        avgHeadroom = (avgHeadroom * 9.0 + (1.0 - (durationMs / blockDurationMs))) / 10.0

        framesSinceDiagnostics += context.blockFrames

        if (framesSinceDiagnostics < diagnosticsEveryFrames) {
            return
        }

        framesSinceDiagnostics = 0

        // The message carries its own list of cylinder states (it crosses to the frontend), so that list is new per
        // emission; the counts are fields, so the walk needs no closure.
        val cylinderStates = ArrayList<KlangCommLink.Feedback.Diagnostics.CylinderState>()

        diagnosticVoices = 0
        diagnosticDroppedVoices = 0
        diagnosticSoundlessVoices = 0
        diagnosticDeniedRents = 0

        // Every engine that renders counts, a detached one still releasing included, in render order.
        for (i in 0 until rendering.size) {
            countDiagnostics(engine = rendering[i], cylinderStates = cylinderStates)
        }

        context.commLink.feedback.send(
            KlangCommLink.Feedback.Diagnostics(
                playbackId = KlangCommLink.SYSTEM_PLAYBACK_ID,
                sampleRate = context.sampleRate,
                renderHeadroom = avgHeadroom,
                activeVoiceCount = diagnosticVoices,
                cylinders = cylinderStates,
                backendNowMs = context.clock.secAt(blockEndFrame) * 1000.0,
                outputLatencyMs = outputLatencyMs,
                // The warehouse's own snapshot: rebuilt only when one of its parts changed.
                warehouse = context.warehouse.stats(
                    droppedVoices = diagnosticDroppedVoices,
                    deniedRents = diagnosticDeniedRents,
                ),
                soundlessVoices = diagnosticSoundlessVoices,
                nonFiniteSamples = house.nonFiniteSamples,
            )
        )
    }

    /**
     * Adds [engine]'s voices, dropped and soundless voices and denied rents to the diagnostic counts, and its cylinders'
     * states.
     */
    private fun countDiagnostics(
        engine: PlaybackEngine,
        cylinderStates: MutableList<KlangCommLink.Feedback.Diagnostics.CylinderState>,
    ) {
        // The gauge is "voices rendering audio": every listed voice renders (a culled one has left).
        diagnosticVoices += engine.scheduler.getActiveVoiceCount()
        diagnosticDroppedVoices += engine.scheduler.droppedVoiceCount()
        diagnosticSoundlessVoices += engine.scheduler.soundlessVoiceCount()

        val cylinders = engine.cylinders.cylinders

        for (i in 0 until cylinders.size) {
            val cylinder = cylinders[i]

            cylinderStates.add(
                KlangCommLink.Feedback.Diagnostics.CylinderState(id = cylinder.id, active = cylinder.isActive)
            )
            diagnosticDeniedRents += cylinder.deniedRents
        }
    }

    /**
     * Dispose engines that were told to stop and have now fully gone quiet, in the [ending] order. No auto-GC of
     * live engines: a `Playing` engine is never among them.
     */
    private fun disposeDrainedEngines() {
        var i = 0

        while (i < ending.size) {
            val engine = ending[i]

            if (!engine.isIdle()) {
                i++
                continue
            }

            ending.removeAt(i)
            rendering.remove(engine)

            // A detached engine's id may already belong to a fresh engine, which stays.
            if (engines[engine.playbackId] === engine) {
                engines.delete(engine.playbackId)
            }

            engine.dispose()
        }
    }

    /** Reset the house stage (limiter envelope + DC blockers) after warmup. */
    fun resetPostChain() = house.reset()

    /** True when every idle ring and network on the warehouse's shelves is zeroed (see [WarmupRunner.tick]). */
    val isWarehouseClean: Boolean get() = context.warehouse.isClean

    /** Puts [count] delay rings of [seconds] on the warehouse's shelf, for the warmup ([ResourceWarehouse.stockDelayRings]). */
    fun stockDelayRings(seconds: Double, count: Int) = context.warehouse.stockDelayRings(seconds = seconds, count = count)

    /**
     * Frames of latency the output adds: the house stage's (the house limiter's lookahead) plus the longest master
     * chain an engine has in service. A render that stops must run this many frames past its musical end, or the last
     * samples are still in a delay ring; read it per block, as a master event swaps a chain mid-song. The offline
     * renderer reads it (KLANG-00399, step 4), as it read `KlangAudioRenderer.latencyFrames` before.
     */
    val latencyFrames: Int
        get() {
            var longest = 0

            for (i in 0 until rendering.size) {
                longest = maxOf(longest, rendering[i].masterLatencyFrames)
            }

            return house.latencyFrames + longest
        }

    /** The real block size — the warmup buckets on it, not on a constant. */
    val blockFrames: Int get() = context.blockFrames

    // ── Test / diagnostics inspection ────────────────────────────────────────────
    internal val activePlaybackIds: Set<String> get() = buildSet { engines.forEachEntry { id, _ -> add(id) } }

    /** Stopped engines detached from their id mid-release (see [rendering]), still rendering. */
    internal val detachedCountForTest: Int get() = rendering.size - engines.size

    /** Every engine the next block renders, attached and detached: a disposed one must not stay among them. */
    internal val renderedEngineCountForTest: Int get() = rendering.size

    /** The engines waiting to be disposed, in the order the sweep disposes them (see [ending]). */
    internal val endingForTest: List<PlaybackEngine> get() = ending

    /** The render clock, for specs that must observe the between-renders convention (block-framing B1). */
    internal val clockForTest: RenderClock get() = clock
    internal fun engine(playbackId: String): PlaybackEngine? = engines[playbackId]

    companion object {
        /** How much audio is rendered between two diagnostics: about fifty a second. */
        const val DIAGNOSTICS_EVERY_SEC = 0.02

        /** Builds the backend host with its shared context + clock. Engines are created lazily per playback. */
        fun create(
            sampleRate: Int,
            blockFrames: Int,
            commLink: KlangCommLink.BackendEndpoint,
            performanceTimeMs: () -> Double,
            voiceWatch: VoiceWatch? = null,
            phasePoolSeed: Int? = null,
        ): PlaybackEngineDispatcher {
            val clock = BackendClock(sampleRate)
            val context = AudioBackendContext.create(
                sampleRate = sampleRate,
                blockFrames = blockFrames,
                commLink = commLink,
                clock = clock,
                performanceTimeMs = performanceTimeMs,
                voiceWatch = voiceWatch,
                phasePoolSeed = phasePoolSeed,
            )
            return PlaybackEngineDispatcher(context = context, clock = clock)
        }
    }
}
