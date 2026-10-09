/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.SampleStore
import io.peekandpoke.klang.audio_be.ignitor.BuiltIgnitor
import io.peekandpoke.klang.audio_be.ignitor.buildExciter
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.SampleIgnitor
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import io.peekandpoke.klang.audio_be.voices.strip.ignite.IgniteRenderer
import io.peekandpoke.klang.audio_be.voices.strip.pitch.buildPitchPipeline
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.constants.FM_RATIO
import io.peekandpoke.klang.audio_bridge.constants.MOD_ENV_CURVE
import io.peekandpoke.klang.audio_bridge.constants.VOICE_ADSR_RELEASE_SEC
import io.peekandpoke.klang.audio_bridge.constants.VOICE_CULL_NEVER
import io.peekandpoke.klang.audio_bridge.VoiceData
import kotlin.random.Random

/**
 * Creates [Voice] instances from [ScheduledVoice] data.
 *
 * Extracted from [VoiceScheduler] to separate voice construction (parameter mapping, the tree build)
 * from scheduling concerns (timing, promotion, lifecycle).
 *
 * Every voice is ONE Ignitor tree (phase 3 step 9 retired the voice strip): a registered instrument's tree
 * (a built-in, an authored instrument, an inline one) or the sample instrument over the voice's PCM. Around
 * it the voice runs only its pitch pipeline (in front), the teardown fade when the tree's root is not a built
 * envelope, and the channel (gain, pan, the orbit's send). An authored instrument that does not end in
 * `classic()` is played as its bare tree: no voice envelope, no doors.
 */
class VoiceFactory(
    private val sampleRate: Int,
    private val blockFrames: Int,
    private val voiceBuffer: AudioBuffer,
    private val freqModBuffer: DoubleArray,
    private val scratchBuffers: ScratchBuffers,
) {

    private companion object {
        /** The stages after the tree of a voice whose tree does not end in its own envelope: the teardown fade alone. */
        val TEARDOWN_FADE_ONLY: List<BlockRenderer> = listOf(TeardownFadeRenderer)
    }

    /**
     * The onset frame a voice built from [scheduled] gets (absolute backend frame, floored), the one formula
     * [makeVoice] and the scheduler's cut sweep share: the cut fades its victims from the cutting voice's onset,
     * also when that voice cannot be built.
     */
    fun onsetFrame(scheduled: ScheduledVoice, backendStartTimeSec: Double): Double {
        // Convert absolute time to backend-relative time, then to frames.
        val relativeStartTime = scheduled.startTime - backendStartTimeSec

        return kotlin.math.floor(relativeStartTime * sampleRate)
    }

    /**
     * Creates a voice from a scheduled voice with absolute timing and resolved sample data.
     *
     * There is no "now" here: since block-framing B2 the scheduler drops any voice whose start is
     * behind the block it is promoted for, so `startFrame` is always renderable as scheduled (see
     * `sampleStartFrame` below for the floor that used to need it).
     *
     * Returns null if the voice cannot be created (unknown sound, missing sample, etc.).
     */
    fun makeVoice(
        scheduled: ScheduledVoice,
        backendStartTimeSec: Double,
        playbackCtx: PlaybackCtx,
        getSample: (SampleRequest) -> SampleStore.SampleEntry.Complete?,
    ): Voice? {
        val data = scheduled.data

        // Convert absolute time to backend-relative time, then to frames
        val relativeGateEndTime = scheduled.gateEndTime - backendStartTimeSec

        // Absolute backend frames — Double (see RenderClock.cursorFrame). `.toInt()` here would
        // overflow after ~12.4 h of backend uptime, silently placing every new voice at a nonsense
        // frame. Durations derived below are relative and stay Int.
        val startFrame = onsetFrame(scheduled, backendStartTimeSec)
        val gateEndFrameFromTime = kotlin.math.floor(relativeGateEndTime * sampleRate)

        // Handle legato (clip) logic
        val clip = data.legato
        val originalGateDuration = (gateEndFrameFromTime - startFrame).toInt()
        val effectiveGateDuration = if (clip != null) (originalGateDuration * clip).toInt() else originalGateDuration
        val gateEndFrame = startFrame + effectiveGateDuration

        // THE one place the factory reads `analog` off the bag, guarded here rather than at each
        // use: the sample playhead's drift lane (`SampleIgnitor`) takes it below; every tree reads its own
        // `Slots.analog`. A non-finite override reads as UNSET, the same rule the `Param` leaf applies to every
        // slot (`IgnitorDslRuntime`, `/dsl-design` section 4) and the same rule `gain` follows below.
        // It matters because the readers disagree on which test a non-finite value fails: `AnalogDrift`
        // tests `analog > 0.0` (a NaN fails it, an `+Infinity` passes). What each non-finite value used to
        // render is written out once, in `VoiceBagGuardSpec`, which is the guard.
        val analog = data.ignitorParams?.get("analog")?.takeIf { it.isFinite() } ?: 0.0 // NaN-guard: non-finite reads as unset

        // Seeded-voice-rng: deal THIS voice's stream from the playback's coreRandom at the
        // TOP of creation, before any branch can bail (a `?: return null` after the
        // deal would make the core draw count depend on e.g. async sample-load timing,
        // shifting every later voice's seed). One instance feeds EVERYTHING this voice owns:
        // the exciter build (IgnitorBuildCache.random, the tree filters' humanization draws among it),
        // the sample playhead, and the render context (IgniteContext.random).
        val voiceRandom = Random(playbackCtx.coreRandom.nextInt())

        // Decision: oscillator vs sample. A name that is not a registered instrument is a sample. `isOsci` and the
        // build ask the same registry, the playback's (in production the scheduler's fork, `VoiceScheduler`).
        val freqHz = data.freqHz
        val sound = data.sound
        val isOsci = playbackCtx.ignitorRegistry.contains(sound)
        val isSample = !isOsci && sound != null

        // Routing
        val cylinder = data.cylinder ?: 0

        // Pitch / Glissando
        val accelerate = Voice.Accelerate(semitones = data.accelerate ?: 0.0)

        // Silence culling: the author's `cull(...)`; a tremolo inside the tree adds its own cull-never rule at the
        // build (`treeCull`).
        val cull = data.cull

        // Dynamics: `gain` is the channel fader, the one level word on the wire. A frontend's
        // articulation shorthand (sprudel's `velocity`, a MIDI key velocity) is already folded
        // into it before it crosses (signal-flow plan section 6).
        //
        // A non-finite gain reads as UNSET, like every other wire number (/dsl-design section 4).
        // Two downstream readers depend on it, and both used to be wrong for a NaN: `Voice.heard`
        // starts latched on a gain of exactly 0, and `NaN == 0.0` is false, so a NaN voice started
        // unlatched; and `SendRenderer.measurePeak` scales the block peak by `abs(gain)`, so a NaN
        // gain made the measured peak NaN, which fails every compare against the cull floor and
        // reads as audible forever. The guard hands both readers a finite number, and a NaN can no
        // longer reach the orbit mix, which the orbit's reverb and delay are fed from and would
        // latch it for the rest of the playback.
        val gain = data.gain?.takeIf { it.isFinite() } ?: 1.0 // NaN-guard: non-finite reads as unset

        // FM Synthesis
        val fm = if (data.fmh != null || (data.fmEnv ?: 0.0) != 0.0) {
            val ratio = data.fmh ?: FM_RATIO
            val depth = data.fmEnv ?: 0.0
            // The modulation envelopes' curve, the Ignitor FM node's (decision D3).
            val fmEnv = Voice.Envelope(
                attackFrames = (data.fmAttack ?: 0.0) * sampleRate,
                decayFrames = (data.fmDecay ?: 0.0) * sampleRate,
                sustainLevel = data.fmSustain ?: 1.0,
                releaseFrames = 0.0,
                attackCurve = MOD_ENV_CURVE,
                decayCurve = MOD_ENV_CURVE,
                releaseCurve = MOD_ENV_CURVE,
            )
            Voice.Fm(ratio = ratio, depth = depth, envelope = fmEnv)
        } else {
            null
        }

        return when {
            isOsci -> {
                val voiceDurationFrames = (gateEndFrame - startFrame).toInt()
                // Build FIRST: the voice's lifetime is a finding of the build (the tree's release tail), not a
                // separate analysis of the DSL tree.
                val built = playbackCtx.ignitorRegistry.createExciter(
                    sound, data, freqHz ?: 0.0,
                    phasePools = playbackCtx.phasePools,
                    random = voiceRandom,
                    sampleRate = sampleRate,
                    blockFrames = blockFrames,
                ) ?: return null

                buildVoice(
                    data = data, releaseSec = treeLifetime(built), startFrame = startFrame, gateEndFrame = gateEndFrame, voiceDurationFrames = voiceDurationFrames, cylinder = cylinder,
                    gain = gain, accelerate = accelerate,
                    fm = fm, signal = built.ignitor, freqHz = freqHz ?: 0.0, voiceRandom = voiceRandom,
                    cut = data.cut,
                    cull = treeCull(cull, built),
                    treeStages = treeStages(built),
                )
            }

            isSample -> {
                val sampleRequest = data.asSampleRequest()
                val entry = getSample(sampleRequest) ?: return null
                val sample = entry.sample
                if (sample.pcm.size <= 1) return null

                // The sample's playback slots (`IgnitorDsl.Slots.sample`, phase 3 step 8): read off the voice's slot bag
                // where the playhead is built, before any tree. A non-finite value reads as UNSET, the rule of every
                // slot, and UNSET matters here beyond its default: an unset `begin` and `end` let the sample's own
                // loop apply, and only a set `begin` moves the start.
                val sampleBag = data.ignitorParams
                val sampleBegin = sampleBag.finiteSlot(IgnitorDsl.Slots.sample.begin)
                val sampleEnd = sampleBag.finiteSlot(IgnitorDsl.Slots.sample.end)
                val sampleSpeed = sampleBag.finiteSlotOrDefault(IgnitorDsl.Slots.sample.speed)
                val sampleLoop = sampleBag.finiteSlotOrDefault(IgnitorDsl.Slots.sample.loop)

                val baseSamplePitchHz = entry.pitchHz
                val targetPitchHz = data.freqHz ?: baseSamplePitchHz
                val pitchRatio = (targetPitchHz / baseSamplePitchHz).coerceIn(1.0 / 32.0, 32.0)
                val loopSpeed = sampleSpeed
                val rate = (sample.sampleRate.toDouble() / sampleRate.toDouble()) * pitchRatio * loopSpeed
                val pcmSize = sample.pcm.size.toDouble()

                val loopBeginRatio = sampleBegin ?: 0.0
                val startSample = loopBeginRatio * pcmSize
                val loopEndRatio = sampleEnd ?: 1.0
                val endSample = loopEndRatio * pcmSize

                // A flag: any finite value but 0.0 is on (the house flag rule, as the envelope's `on`).
                val explicitLoop = sampleLoop != 0.0
                val useMetaLoop = !explicitLoop && sampleBegin == null && sampleEnd == null
                val sampleMetaLoop = sample.meta.loop

                val loopStart: Double
                val loopEnd: Double
                val isLooping: Boolean

                if (explicitLoop) {
                    loopStart = startSample
                    loopEnd = endSample
                    isLooping = loopStart >= 0.0 && loopEnd > loopStart
                } else if (useMetaLoop && sampleMetaLoop != null) {
                    loopStart = sampleMetaLoop.startSec * sample.sampleRate
                    loopEnd = sampleMetaLoop.endSec * sample.sampleRate
                    isLooping = loopStart >= 0.0 && loopEnd > loopStart
                } else {
                    loopStart = -1.0
                    loopEnd = -1.0
                    isLooping = false
                }

                // Play from the START unless the user set `begin`. Both SoundFont 2 and WebAudioFont
                // start at the sample's first frame, play THROUGH the attack, and loop
                // `[loopStart, loopEnd)` only once the playhead arrives there.
                //
                // This used to start a looped sample AT `loopStart` — skipping the attack entirely
                // (the FluidR3 violin lost 1.27 s of bow onset and looped a 180 ms slice of steady
                // state) — and a non-looped one at `meta.anchor`, which is not a start offset at all:
                // measured against the decoded audio, `anchor` is the position of the loudest sample
                // (argmax |x|), a normalisation artefact of the converter. For the nylon guitar that
                // skipped the pluck. See docs/tasks-archive/2026-09/20260903-soundfont-looping-investigation.md.
                val playhead0 = if (sampleBegin != null) startSample else 0.0

                // Sample-accurate onset, same as the oscillator branch: `Voice.render` clips the
                // voice into the block itself (offset = startFrame - blockStart), so a sample that
                // starts mid-block starts mid-block. Rounding down to the block start (which this
                // used to do unconditionally) fires every hit EARLY by 0..blockFrames-1 frames —
                // not a constant offset but per-hit jitter, which is what wrecks the groove.
                //
                // This used to be `maxOf(startFrame, nowFrame)`: a floor for the LATE case, so a
                // voice whose start was already behind the current block would not run its ADSR
                // from a past frame while the sample playhead started at the top of the PCM. Since
                // block-framing B2 (2026-09-03) the scheduler drops late voices at admission — an
                // admitted voice always has startFrame >= the block it is promoted for — so the
                // floor was an identity and is gone. The desync class it bounded is unreachable.
                val sampleStartFrame = startFrame
                val voiceDurationFrames = (gateEndFrame - sampleStartFrame).toInt()

                val signal = SampleIgnitor(
                    rng = voiceRandom,
                    pcm = sample.pcm,
                    rate = rate,
                    playhead = playhead0,
                    loopStart = loopStart,
                    loopEnd = loopEnd,
                    isLooping = isLooping,
                    stopFrame = endSample,
                    // The guarded read from the top of this function, not a second lookup off the
                    // bag: `SampleIgnitor` hands this straight to its `AnalogDrift`, whose lane
                    // tests `analog > 0.0`, so an `+Infinity` used to take the playhead non-finite
                    // on the first increment. Guard: `VoiceBagGuardSpec`.
                    analog = analog,
                    sampleRate = sampleRate,
                    blockFrames = blockFrames,
                )

                // The SAMPLE INSTRUMENT (phase 3 step 7): the built-in shape over this playhead.
                // The playhead is built FIRST, above, so its drift lane draws before the tree's filters do. The
                // voice's slots reach the `classic()` stages as on a built-in; the sample's own
                // meta envelope fills the `adsr.*` slots the pattern left unset. The instrument has no variants
                // (`n` already chose the sample), so the build takes no sound index.
                val built = IgnitorRegistry.SAMPLE_INSTRUMENT.buildExciter(
                    ignitorParams = withSampleEnvelopeDefaults(sampleBag, sample.meta.adsr),
                    phasePools = playbackCtx.phasePools,
                    orbit = cylinder,
                    random = voiceRandom,
                    freqHz = baseSamplePitchHz,
                    sampleRate = sampleRate,
                    blockFrames = blockFrames,
                    sampleSource = signal,
                )

                buildVoice(
                    data = data, releaseSec = treeLifetime(built), startFrame = sampleStartFrame, gateEndFrame = gateEndFrame, voiceDurationFrames = voiceDurationFrames, cylinder = cylinder,
                    gain = gain, accelerate = accelerate,
                    fm = fm, signal = built.ignitor, freqHz = baseSamplePitchHz,
                    voiceRandom = voiceRandom,
                    cut = data.cut,
                    cull = treeCull(cull, built),
                    treeStages = treeStages(built),
                )
            }

            else -> null
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Private helpers
    // ═════════════════════════════════════════════════════════════════════════════

    /**
     * The voice's lifetime past its gate, in seconds: the tree's own release tail, which a switched-off envelope
     * still reports, so a release written only as a slot lives exactly that long. `null` (no static answer: no
     * envelope on the spine, or a modulated release) takes the voice envelope's `VOICE_ADSR_RELEASE_SEC`. A
     * sample never reaches that fallback: the sample instrument ends in `classic()`, whose release is a slot, a
     * leaf with a finite default, so its tail is always static (its meta release reaches it as the slot's fill,
     * `withSampleEnvelopeDefaults`). The LIFETIME (not the envelope) never ends before
     * the gate: a raw negative release is a zero-length release stage, and the voice plays to its gate. Not a
     * clamp on an audio parameter: the envelope still reads it raw.
     */
    private fun treeLifetime(built: BuiltIgnitor): Double =
        maxOf(built.releaseTailSec ?: VOICE_ADSR_RELEASE_SEC, 0.0)

    /**
     * The cull rule for a tremolo INSIDE the tree, which only the build can see (`BuiltIgnitor.gatesOutput`,
     * phase 3 step 3b), on top of the author's [cull], which still wins.
     */
    private fun treeCull(cull: Double?, built: BuiltIgnitor): Double? =
        cull ?: if (built.gatesOutput) VOICE_CULL_NEVER else null

    /**
     * The voice-level stages after the tree: none when the tree ends in its own envelope (a built root `Adsr`
     * with a static release, `BuiltIgnitor.endsInEnvelope`), else the teardown fade, which takes the voice's
     * last frames to exactly zero.
     */
    private fun treeStages(built: BuiltIgnitor): List<BlockRenderer> =
        if (built.endsInEnvelope) emptyList() else TEARDOWN_FADE_ONLY

    private fun buildVoice(
        data: VoiceData,
        /** The voice's lifetime past its gate, in seconds (`treeLifetime`). */
        releaseSec: Double,
        // Absolute backend frames are Double (RenderClock.cursorFrame); the DURATION is relative
        // to the voice and stays Int.
        startFrame: Double,
        gateEndFrame: Double,
        voiceDurationFrames: Int,
        cylinder: Int,
        gain: Double,
        accelerate: Voice.Accelerate,
        fm: Voice.Fm?,
        signal: Ignitor,
        freqHz: Double,
        /** The voice's random stream (seeded-voice-rng; same instance the exciter was built
         *  with). NO default on purpose: a future call site must not silently fall back to
         *  the global and split the build/render channels. */
        voiceRandom: Random,
        cut: Int?,
        cull: Double?,
        /** The stages after the ignite stage (`treeStages`). */
        treeStages: List<BlockRenderer>,
    ): Voice {
        val endFrame = gateEndFrame + releaseSec * sampleRate

        val signalCtx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = voiceDurationFrames,
            gateEndFrame = voiceDurationFrames,
            scratchBuffers = scratchBuffers,
            random = voiceRandom,
        )

        val pipeline = buildPitchPipeline(
            accelerate = accelerate,
            fm = fm,
            freqHz = freqHz,
            sampleRate = sampleRate,
            startFrame = startFrame,
            endFrame = endFrame,
        ) + IgniteRenderer(
            signal = signal,
            signalCtx = signalCtx,
            freqHz = freqHz,
        ) + treeStages

        val blockCtx = BlockContext(
            audioBuffer = voiceBuffer,
            freqModBuffer = freqModBuffer,
            scratchBuffers = scratchBuffers,
            sampleRate = sampleRate,
            // The voice's time limits, one instance: the voice owns and writes it, every stage reads it.
            limits = VoiceLimits(startFrame = startFrame, gateEndFrame = gateEndFrame, endFrame = endFrame),
        )

        return Voice(
            cylinderId = cylinder,
            gain = gain,
            pan = data.pan ?: 0.5,
            // By reference, never a copy: the map is immutable on the wire and only the orbit's
            // owner reads it (see Voice.katalystParams).
            katalystParams = data.katalystParams,
            cut = cut,
            cull = cull,
            pipeline = pipeline,
            blockCtx = blockCtx,
        )
    }
}

/** The finite value this bag holds for [slot] (an `IgnitorDsl.Param` of `IgnitorDsl.Slots`), or null: unset or non-finite. */
private fun Map<String, Double>?.finiteSlot(slot: IgnitorDsl): Double? =
    this?.get((slot as IgnitorDsl.Param).name)?.takeIf { it.isFinite() } // NaN-guard: non-finite reads as unset

/** [finiteSlot], or the slot's own default when unset: the default has one home, the `Param`. */
private fun Map<String, Double>?.finiteSlotOrDefault(slot: IgnitorDsl): Double =
    finiteSlot(slot) ?: (slot as IgnitorDsl.Param).default
