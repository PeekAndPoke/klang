/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.PlaybackEngine.Phase
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * The backend host: routes inbound [KlangCommLink.Cmd]s and renders the final block.
 *
 * It owns the [AudioBackendContext] (shared services + the read-only clock), the **mutable**
 * [BackendClock] it advances each block, the final [MasterStage], and a
 * `Map<playbackId, PlaybackEngine>` — one fully-isolated engine per playback, created lazily and
 * disposed once told to stop ([cleanup]) and fully drained. Both platform backends shrink to a thin
 * pump: drain commands → [renderBlock] → convert/output → forward feedback. See
 * `docs/tasks-archive/2026-09/20260904-per-playback-engine.md`.
 *
 * An engine's end of life is its own [PlaybackEngine.phase] (the transition table is in its KDoc): the dispatcher
 * asks it whether a scheduled-again engine resumes (`Stopped`) or is detached (`Releasing`, `Released`), and whether
 * a stopped one may be disposed ([PlaybackEngine.isIdle]). It keeps no lifecycle of its own, only the two orders the
 * output depends on: the render order ([rendering]) and the disposal order ([ending]).
 */
class PlaybackEngineDispatcher(
    private val context: AudioBackendContext,
    private val clock: BackendClock,
) {
    // playbackId -> the engine filed under it (attached). LinkedHashMap: deterministic order, the creation order.
    private val engines = LinkedHashMap<String, PlaybackEngine>()

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
    private val master = MasterStage(sampleRate = context.sampleRate, blockFrames = context.blockFrames)

    // Aggregated diagnostics (D5) — emission moved here from per-engine VoiceScheduler so the gauges
    // still update when the engine map is empty (idle → zeros), and headroom times the WHOLE block.
    private var lastDiagnosticsTimeMs = 0.0
    private var avgHeadroom = 1.0

    // The counts of one emission ([emitDiagnostics]), summed over the engines by [countDiagnostics].
    private var diagnosticVoices = 0
    private var diagnosticDroppedVoices = 0
    private var diagnosticDeniedRents = 0

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
        engines[playbackId]?.let { scheduledAgain(it) }

        return engines[playbackId] ?: createEngine(playbackId)
    }

    /**
     * [engine]'s playback is scheduled again. A stopped one resumes, which cancels its pending disposal (e.g. a
     * resume after a pause); a releasing one is detached, and the id gets a fresh engine ([engineFor]).
     */
    private fun scheduledAgain(engine: PlaybackEngine): Unit = when (engine.phase) {
        Phase.Playing, Phase.Disposed -> Unit

        Phase.Stopped -> {
            engine.resume()
            ending.remove(engine)
            Unit
        }

        Phase.Releasing, Phase.Released -> {
            engines.remove(engine.playbackId)
            rendering.remove(engine)
            rendering.add(engine)
            ending.remove(engine)
            ending.add(0, engine)
        }
    }

    /** A fresh engine for [playbackId], rendering after the attached ones and before the detached ones. */
    private fun createEngine(playbackId: String): PlaybackEngine {
        val engine = PlaybackEngine.create(context = context, playbackId = playbackId)

        rendering.add(engines.size, engine)
        engines[playbackId] = engine

        return engine
    }

    /**
     * Route a single inbound command to the engine for its `playbackId` (creating it on demand).
     * Exhaustive `when` *expression*: a new [KlangCommLink.Cmd] subtype fails the build until handled.
     */
    fun handle(cmd: KlangCommLink.Cmd): Unit = when (cmd) {
        is KlangCommLink.Cmd.ScheduleVoice ->
            engineFor(cmd.playbackId).scheduler.scheduleVoice(cmd.voice)

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

        is KlangCommLink.Cmd.ClearScheduled ->
            clearScheduled(cmd.playbackId)

        is KlangCommLink.Cmd.Sample ->
            context.sampleStore.addSample(cmd)

        is KlangCommLink.Cmd.RegisterIgnitor ->
            engineFor(cmd.playbackId).scheduler.registerIgnitor(cmd.name, cmd.dsl)

        is KlangCommLink.Cmd.RegisterKatalyst ->
            engineFor(cmd.playbackId).registerKatalyst(cmd.name, cmd.dsl)
    }

    private fun scheduleVoices(playbackId: String, voices: List<ScheduledVoice>) {
        if (voices.isNotEmpty()) {
            engineFor(playbackId).scheduler.scheduleVoices(voices)
        }
    }

    private fun replaceVoices(playbackId: String, voices: List<ScheduledVoice>, afterTimeSec: Double?) {
        // Replace targets an EXISTING playback — never lazily create an engine here, so a
        // "replace with nothing" cannot materialize (and then leak) an empty engine.
        engines[playbackId]?.scheduler?.replaceVoices(voices = voices, afterTimeSec = afterTimeSec)
    }

    /** Stop scheduling for a playback and let it ring out; disposed once drained (see [renderBlock]). */
    private fun cleanup(playbackId: String) {
        val engine = engines[playbackId] ?: return

        engine.scheduler.cleanup()

        // A first stop joins the disposal order at its end; a second one keeps the place of the first.
        if (engine.phase == Phase.Playing) {
            ending.add(engine)
        }

        engine.stop()
    }

    private fun clearScheduled(playbackId: String) {
        engines[playbackId]?.scheduler?.clearScheduled()
    }

    /** Immediate disposal (warmup teardown) — does not let voices ring out. */
    fun cleanupHard(playbackId: String) {
        val engine = engines.remove(playbackId) ?: return

        rendering.remove(engine)
        ending.remove(engine)
        engine.scheduler.cleanupHard()
        engine.dispose()
    }

    /**
     * Render one block to [out] (`blockFrames` frames per channel): sum every engine into the shared
     * mix, then run the master stage, which writes the clipped floating-point output.
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
        // (#11: with one engine this is a straight render into the final mix.) Per-engine master gain
        // in D6 will need a scratch buffer here for the ≥2 case.
        for (i in 0 until rendering.size) {
            rendering[i].renderInto(mix, cursorFrame)
        }

        master.process(mix = mix, out = out)

        disposeDrainedEngines()
        // Deferred clearing of returned rings and networks, a bounded slice per block (round 3).
        context.warehouse.housekeep()
        emitDiagnostics(startMs)
    }

    /**
     * Emit one aggregate [KlangCommLink.Feedback.Diagnostics] per ~20 ms. Runs every block regardless
     * of engine count, so a fully-idle backend reports zero voices / zero cylinders (the gauges drop to
     * 0 on stop) and the headroom reflects the WHOLE block — all engines + master — not one scheduler.
     */
    private fun emitDiagnostics(startMs: Double) {
        val endMs = context.performanceTimeMs()
        val durationMs = endMs - startMs
        val blockDurationMs = (context.blockFrames.toDouble() / context.sampleRateDouble) * 1000.0
        avgHeadroom = (avgHeadroom * 9.0 + (1.0 - (durationMs / blockDurationMs))) / 10.0

        if (endMs - lastDiagnosticsTimeMs <= 20.0) {
            return
        }
        lastDiagnosticsTimeMs = endMs

        // The message carries its own list of cylinder states (it crosses to the frontend), so that list is new per
        // emission; the counts are fields, so the walk needs no closure.
        val cylinderStates = ArrayList<KlangCommLink.Feedback.Diagnostics.CylinderState>()

        diagnosticVoices = 0
        diagnosticDroppedVoices = 0
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
                backendNowMs = endMs,
                // The warehouse's own snapshot: rebuilt only when one of its parts changed.
                warehouse = context.warehouse.stats(
                    droppedVoices = diagnosticDroppedVoices,
                    deniedRents = diagnosticDeniedRents,
                ),
            )
        )
    }

    /** Adds [engine]'s voices, dropped voices and denied rents to the diagnostic counts, and its cylinders' states. */
    private fun countDiagnostics(
        engine: PlaybackEngine,
        cylinderStates: MutableList<KlangCommLink.Feedback.Diagnostics.CylinderState>,
    ) {
        // The gauge is "voices rendering audio": every listed voice renders (a culled one has left).
        diagnosticVoices += engine.scheduler.getActiveVoiceCount()
        diagnosticDroppedVoices += engine.scheduler.droppedVoiceCount()

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
                engines.remove(engine.playbackId)
            }

            engine.dispose()
        }
    }

    /** Reset the master post-chain (limiter envelope + DC blockers) after warmup. */
    fun resetPostChain() = master.reset()

    /** True when every idle ring and network on the warehouse's shelves is zeroed (see [WarmupRunner.tick]). */
    val isWarehouseClean: Boolean get() = context.warehouse.isClean

    /** The real block size — the warmup buckets on it, not on a constant. */
    val blockFrames: Int get() = context.blockFrames

    // ── Test / diagnostics inspection ────────────────────────────────────────────
    internal val activePlaybackIds: Set<String> get() = engines.keys

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
        /** Builds the backend host with its shared context + clock. Engines are created lazily per playback. */
        fun create(
            sampleRate: Int,
            blockFrames: Int,
            commLink: KlangCommLink.BackendEndpoint,
            performanceTimeMs: () -> Double,
        ): PlaybackEngineDispatcher {
            val clock = BackendClock(sampleRate)
            val context = AudioBackendContext.create(
                sampleRate = sampleRate,
                blockFrames = blockFrames,
                commLink = commLink,
                clock = clock,
                performanceTimeMs = performanceTimeMs,
            )
            return PlaybackEngineDispatcher(context = context, clock = clock)
        }
    }
}
