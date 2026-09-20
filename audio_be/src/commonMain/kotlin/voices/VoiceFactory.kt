/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_be.SampleStore
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.engines.PipelineRegistry
import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.filters.AudioFilter.Companion.combine
import io.peekandpoke.klang.audio_be.filters.LowPassHighPassFilters
import io.peekandpoke.klang.audio_be.ignitor.AnalogDrift
import io.peekandpoke.klang.audio_be.ignitor.analogDriftStepRate
import io.peekandpoke.klang.audio_be.ignitor.perVoiceCutoffOffsetMul
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.SampleIgnitor
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.filter.buildFilterPipeline
import io.peekandpoke.klang.audio_be.voices.strip.ignite.IgniteRenderer
import io.peekandpoke.klang.audio_be.voices.strip.pitch.buildPitchPipeline
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.constants.PHASER_CENTER_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.PHASER_RATE_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_SWEEP_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_WET
import io.peekandpoke.klang.audio_bridge.constants.VOICE_CULL_NEVER
import io.peekandpoke.klang.audio_bridge.StageDsl
import io.peekandpoke.klang.audio_bridge.VoiceData
import kotlin.random.Random

/**
 * Creates [Voice] instances from [ScheduledVoice] data.
 *
 * Extracted from [VoiceScheduler] to separate voice construction (parameter mapping,
 * pipeline building, filter creation) from scheduling concerns (timing, promotion, lifecycle).
 */
class VoiceFactory(
    private val sampleRate: Int,
    private val sampleRateDouble: Double,
    private val blockFrames: Int,
    private val ignitorRegistry: IgnitorRegistry,
    private val pipelineRegistry: PipelineRegistry,
    private val cylinders: Cylinders,
    private val voiceBuffer: AudioBuffer,
    private val freqModBuffer: DoubleArray,
    private val scratchBuffers: ScratchBuffers,
) {

    /**
     * Block rate ≈ sampleRate / blockFrames. Used to configure per-voice filter
     * `AnalogDrift` instances so that calling `nextMultiplier()` once per block
     * gives drift trajectories with the correct time constants (the drift coeffs
     * in `AnalogDriftCoeffs` are derived from the effective update rate, not
     * the audio rate).
     */
    private val driftUpdateRate: Int = analogDriftStepRate(sampleRate, blockFrames)

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
        val relativeStartTime = scheduled.startTime - backendStartTimeSec
        val relativeGateEndTime = scheduled.gateEndTime - backendStartTimeSec

        // Absolute backend frames — Double (see RenderClock.cursorFrame). `.toInt()` here would
        // overflow after ~12.4 h of backend uptime, silently placing every new voice at a nonsense
        // frame. Durations derived below are relative and stay Int.
        val startFrame = kotlin.math.floor(relativeStartTime * sampleRate)
        val gateEndFrameFromTime = kotlin.math.floor(relativeGateEndTime * sampleRate)

        // Handle legato (clip) logic
        val clip = data.legato
        val originalGateDuration = (gateEndFrameFromTime - startFrame).toInt()
        val effectiveGateDuration = if (clip != null) (originalGateDuration * clip).toInt() else originalGateDuration
        val gateEndFrame = startFrame + effectiveGateDuration

        // Create filters. The chain is baked in the EXACT order received from
        // `data.filters` — VoiceFactory never reorders. The chain-order decision
        // (highpass-first / lowpass-last for clean nonlinear behaviour) is made
        // upstream by the language layer (see SprudelVoiceData.toVoiceData), which
        // keeps the engine a faithful consumer and leaves explicit routing open.
        //
        // THE one place the factory reads `analog` off the bag, guarded here rather than at each
        // use. A non-finite override reads as UNSET, the same rule the `Param` leaf applies to every
        // slot (`IgnitorDslRuntime`, `/dsl-design` section 4) and the same rule `gain` follows below.
        // It matters because the readers disagree on which test a non-finite value fails:
        // `perVoiceCutoffOffsetMul` tests `analog <= 0.0` (a NaN fails it, and the cutoffs then go
        // non-finite), `AnalogDrift` and the SVF's saturating branch test `analog > 0.0` (a NaN
        // fails that too, an `+Infinity` passes both), and each test also decides whether the voice
        // DRAWS from its rng. What each non-finite value used to render is written out once, in
        // `VoiceBagGuardSpec`, which is the guard.
        val analog = data.oscParams?.get("analog")?.takeIf { it.isFinite() } ?: 0.0 // NaN-guard: non-finite reads as unset
        // The active engine's Filter stage carries the per-voice "filter feel" scales
        // (cutoff offset / drive / drift). Default StageDsl.Filter() == today's constants.
        val filterStage = pipelineRegistry.get(data.pipeline).stages
            .firstNotNullOfOrNull { it as? StageDsl.Filter } ?: StageDsl.Filter()
        // Body and vowel are orbit-level Katalyst stages, driven by the `body.*` / `vowel.*` slots of
        // the orbit's owner. A wire producer can still carry them in `filters` (sprudel does), so they
        // are dropped from the per-voice chain here; nothing on the voice reads them (step 5b-3).
        val voiceFilterDefs = data.filters.filters.filter { it !is FilterDef.Body && it !is FilterDef.Formant }

        // Seeded-voice-rng: deal THIS voice's stream from the playback's coreRandom at the
        // TOP of creation — before the filter build (per-voice cutoff tolerance + filter
        // drift draw from it) and before any branch can bail (a `?: return null` after the
        // deal would make the core draw count depend on e.g. async sample-load timing,
        // shifting every later voice's seed). One instance feeds EVERYTHING this voice owns:
        // filters here, the exciter build (IgnitorBuildCache.random), and the render context
        // (IgniteContext.random).
        val voiceRandom = Random(playbackCtx.coreRandom.nextInt())

        val filters = voiceFilterDefs.map { it.toFilter(analog, filterStage, voiceRandom) }
        val modulators = voiceFilterDefs.zip(filters).mapNotNull { (def, filter) ->
            def.toModulator(filter, sampleRate, analog, filterStage, voiceRandom)
        }
        val bakedFilters = filters.combine()

        // Routing
        val cylinder = data.cylinder ?: 0

        // Pitch / Glissando
        val accelerate = Voice.Accelerate(semitones = data.accelerate ?: 0.0)

        // Vibrato (depth in semitones — VibratoRenderer converts to ET frequency ratio)
        val vibratoDepthSemitones = data.vibratoMod ?: 0.0
        val vibrato = Voice.Vibrato(
            semitones = vibratoDepthSemitones,
            rate = if (vibratoDepthSemitones > 0.0) data.vibrato ?: 5.0 else 0.0,
        )

        // Pitch Envelope
        val pEnvAmount = data.pEnv ?: 0.0
        val pitchEnvelope = if (pEnvAmount != 0.0) {
            Voice.PitchEnvelope(
                attackFrames = (data.pAttack ?: 0.0) * sampleRate,
                decayFrames = (data.pDecay ?: 0.0) * sampleRate,
                releaseFrames = (data.pRelease ?: 0.0) * sampleRate,
                semitones = pEnvAmount,
                curve = data.pCurve ?: 1.0,
                anchor = data.pAnchor ?: 0.0,
            )
        } else {
            null
        }

        // Phaser
        val phaser = Voice.Phaser(
            rate = data.phaser ?: PHASER_RATE_HZ,
            depth = data.phaserDepth ?: PHASER_WET,
            center = data.phaserCenter ?: PHASER_CENTER_HZ,
            sweep = data.phaserSweep ?: PHASER_SWEEP_HZ,
            floor = data.phaserFloor ?: PHASER_FLOOR,
        )

        // Tremolo
        val tremolo = Voice.Tremolo(
            rate = data.tremoloSync ?: 0.0,
            depth = data.tremoloDepth ?: 0.0,
            skew = data.tremoloSkew ?: 0.0,
            phase = data.tremoloPhase ?: 0.0,
            shape = data.tremoloShape,
        )

        // Silence culling: a tremolo gates the output (a square shape at full depth is exact silence
        // for half a cycle), and a gated RELEASE would be culled at its first off-half. So a voice
        // with a tremolo is not culled unless the author set `cull(...)` themselves.
        val cull = data.cull ?: if (tremolo.depth > 0.0) VOICE_CULL_NEVER else null

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

        // Effects
        val distort = Voice.Distort(
            amount = data.distort ?: 0.0,
            shape = data.distortShape ?: "soft",
            oversample = Oversampler.factorToStages(data.distortOversample ?: 0),
        )
        val crush = Voice.Crush(
            amount = data.crush ?: 0.0,
            oversample = Oversampler.factorToStages(data.crushOversample ?: 0),
        )
        val coarse = Voice.Coarse(
            amount = data.coarse ?: 0.0,
            oversample = Oversampler.factorToStages(data.coarseOversample ?: 0),
        )

        // FM Synthesis
        val fm = if (data.fmh != null || (data.fmEnv ?: 0.0) != 0.0) {
            val ratio = data.fmh ?: 1.0
            val depth = data.fmEnv ?: 0.0
            val fmEnv = Voice.Envelope(
                attackFrames = (data.fmAttack ?: 0.0) * sampleRate,
                decayFrames = (data.fmDecay ?: 0.0) * sampleRate,
                sustainLevel = data.fmSustain ?: 1.0,
                releaseFrames = 0.0,
            )
            Voice.Fm(ratio, depth, fmEnv)
        } else {
            null
        }

        // Decision: oscillator vs sample
        val freqHz = data.freqHz
        val sound = data.sound
        val isOsci = ignitorRegistry.contains(sound)
        val isSample = !ignitorRegistry.contains(sound) && sound != null

        return when {
            isOsci -> {
                val resolvedAdsr = data.adsr.resolve(AdsrDef.defaultSynth)

                val voiceDurationFrames = (gateEndFrame - startFrame).toInt()
                // Build FIRST: the ignitor's release tail is a finding of the build, not a separate
                // analysis of the DSL tree, so `effectiveAdsr` has to come after it.
                val built = playbackCtx.ignitorRegistry.createExciter(
                    sound, data, freqHz ?: 0.0,
                    phasePools = playbackCtx.phasePools,
                    random = voiceRandom,
                    sampleRate = sampleRate,
                    blockFrames = blockFrames,
                ) ?: return null
                val signal = built.ignitor

                // Extend voice lifetime to cover an ignitor-level release tail. Because the tail
                // falls out of the build, `.oscp("release", ...)` overrides and folded release
                // expressions are already resolved in it. null = nothing tail-bearing, or a release
                // time that is itself modulated (no static answer): the voice's own release governs.
                val ignitorTailSec = built.releaseTailSec ?: 0.0
                val effectiveAdsr = if (ignitorTailSec > resolvedAdsr.release) {
                    resolvedAdsr.copy(release = ignitorTailSec)
                } else {
                    resolvedAdsr
                }

                buildVoice(
                    data, effectiveAdsr, startFrame, gateEndFrame, voiceDurationFrames, cylinder,
                    gain, accelerate, vibrato, pitchEnvelope, bakedFilters, modulators,
                    phaser, tremolo, distort, crush, coarse,
                    fm, signal, freqHz ?: 0.0, voiceRandom = voiceRandom,
                    cut = data.cut,
                    cull = cull,
                )
            }

            isSample -> {
                val sampleRequest = data.asSampleRequest()
                val entry = getSample(sampleRequest) ?: return null
                val sample = entry.sample
                if (sample.pcm.size <= 1) return null

                val resolvedAdsr = data.adsr
                    .mergeWith(sample.meta.adsr)
                    .resolve(AdsrDef.defaultSynth)

                val baseSamplePitchHz = entry.pitchHz
                val targetPitchHz = data.freqHz ?: baseSamplePitchHz
                val pitchRatio = (targetPitchHz / baseSamplePitchHz).coerceIn(1.0 / 32.0, 32.0)
                val loopSpeed = data.speed ?: 1.0
                val rate = (sample.sampleRate.toDouble() / sampleRate.toDouble()) * pitchRatio * loopSpeed
                val pcmSize = sample.pcm.size.toDouble()

                val loopBeginRatio = data.begin ?: 0.0
                val startSample = loopBeginRatio * pcmSize
                val loopEndRatio = data.end ?: 1.0
                val endSample = loopEndRatio * pcmSize

                val explicitLoop = data.loop == true
                val useMetaLoop = !explicitLoop && data.begin == null && data.end == null
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
                val playhead0 = if (data.begin != null) startSample else 0.0

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

                buildVoice(
                    data, resolvedAdsr, sampleStartFrame, gateEndFrame, voiceDurationFrames, cylinder,
                    gain, accelerate, vibrato, pitchEnvelope, bakedFilters, modulators,
                    phaser, tremolo, distort, crush, coarse,
                    fm, signal, baseSamplePitchHz,
                    voiceRandom = voiceRandom,
                    cut = data.cut,
                    cull = cull,
                )
            }

            else -> null
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Private helpers
    // ═════════════════════════════════════════════════════════════════════════════

    private fun FilterDef.toFilter(analog: Double, stage: StageDsl.Filter, rng: Random): AudioFilter {
        // Per-voice constant cutoff offset — set once per filter at note-on so that
        // two voices through "the same" configured filter no longer process identically.
        // Real analog filters have component tolerances; we simulate that with a small
        // random multiplier per filter instance. The engine's Filter stage scales it.
        val offsetMul = perVoiceCutoffOffsetMul(analog, stage.cutoffOffsetPerAnalog, rng)
        return when (this) {
            is FilterDef.LowPass -> LowPassHighPassFilters.createLPF(freq, q, sampleRateDouble, analog, offsetMul, stage.drivePerAnalog, passes = passes)
            is FilterDef.HighPass -> LowPassHighPassFilters.createHPF(
                freq,
                q,
                sampleRateDouble,
                analog,
                offsetMul,
                stage.drivePerAnalog,
                passes = passes,
            )
            is FilterDef.BandPass -> LowPassHighPassFilters.createBPF(freq, q, sampleRateDouble, offsetMul)
            is FilterDef.Notch -> LowPassHighPassFilters.createNotch(freq, q, sampleRateDouble, offsetMul)
            // Body / vowel are orbit-level Katalyst effects (KatalystBodyEffect / KatalystFormantEffect):
            // VoiceFactory drops them from the per-voice chain (see voiceFilterDefs) and the orbit takes its
            // resonators from the owner's `body.*` / `vowel.*` slots, so these arms are unreachable and exist
            // only to satisfy the sealed `when`.
            is FilterDef.Formant, is FilterDef.Body ->
                error("Body/Formant are orbit-level resonators, not per-voice filters")
        }
    }

    private fun FilterDef.toModulator(
        filter: AudioFilter,
        sampleRate: Int,
        analog: Double,
        stage: StageDsl.Filter,
        rng: Random,
    ): Voice.FilterModulator? {
        // Non-tunable filters (Formant) can't be modulated at all.
        if (filter !is AudioFilter.Tunable) return null

        val envData = when (this) {
            is FilterDef.LowPass -> this.envelope
            is FilterDef.HighPass -> this.envelope
            is FilterDef.BandPass -> this.envelope
            is FilterDef.Notch -> this.envelope
            is FilterDef.Formant -> null
            is FilterDef.Body -> null
        }

        // Per-voice slow cutoff drift. Constructed with the block-rate effective
        // sample rate so calling `nextMultiplier()` once per block in
        // `FilterModRenderer` produces drift trajectories with the correct
        // time constants. `analog * driftRelToOsc` scales it against oscillator pitch
        // drift (1.0 cent per unit analog), so the stage field IS the filter-to-pitch
        // ratio. At the shipped default of 0.25 the filter wanders 4x LESS than pitch —
        // whether that is the right way round is open, see docs/tasks/audio-bridge-constants.md §6.
        val drift = if (analog > 0.0) {
            AnalogDrift(analog * stage.driftRelToOsc, driftUpdateRate, rng)
        } else {
            null
        }

        // Nothing to modulate — no envelope AND no drift. Skip the per-block work.
        if (envData == null && drift == null) return null

        val baseCutoff = when (this) {
            is FilterDef.LowPass -> this.freq
            is FilterDef.HighPass -> this.freq
            is FilterDef.BandPass -> this.freq
            is FilterDef.Notch -> this.freq
            is FilterDef.Formant -> 0.0
            is FilterDef.Body -> 0.0
        }

        // When there's no envelope but drift is active, build a degenerate envelope
        // with depth=0 so the per-block `2^(0/12 * envValue) = 1` math (C3: semitone law)
        // leaves the cutoff untouched by the envelope side — only drift multiplies it.
        val envelope: Voice.Envelope
        val depth: Double
        if (envData != null) {
            val resolved = envData.resolve()
            envelope = Voice.Envelope(
                attackFrames = resolved.attack * sampleRate,
                decayFrames = resolved.decay * sampleRate,
                sustainLevel = resolved.sustain,
                releaseFrames = resolved.release * sampleRate,
            )
            depth = resolved.depth
        } else {
            envelope = Voice.Envelope(attackFrames = 0.0, decayFrames = 0.0, sustainLevel = 0.0, releaseFrames = 0.0)
            depth = 0.0
        }

        return Voice.FilterModulator(
            filter = filter,
            envelope = envelope,
            depth = depth,
            baseCutoff = baseCutoff,
            drift = drift,
        )
    }

    private fun buildVoice(
        data: VoiceData,
        resolvedAdsr: AdsrDef.Resolved,
        // Absolute backend frames are Double (RenderClock.cursorFrame); the DURATION is relative
        // to the voice and stays Int.
        startFrame: Double,
        gateEndFrame: Double,
        voiceDurationFrames: Int,
        cylinder: Int,
        gain: Double,
        accelerate: Voice.Accelerate,
        vibrato: Voice.Vibrato,
        pitchEnvelope: Voice.PitchEnvelope?,
        bakedFilters: AudioFilter,
        modulators: List<Voice.FilterModulator>,
        phaser: Voice.Phaser,
        tremolo: Voice.Tremolo,
        distort: Voice.Distort,
        crush: Voice.Crush,
        coarse: Voice.Coarse,
        fm: Voice.Fm?,
        signal: Ignitor,
        freqHz: Double,
        /** The voice's random stream (seeded-voice-rng; same instance the exciter was built
         *  with). NO default on purpose: a future call site must not silently fall back to
         *  the global and split the build/render channels. */
        voiceRandom: Random,
        cut: Int? = null,
        cull: Double? = null,
    ): Voice {
        val envelope = Voice.Envelope.of(resolvedAdsr, sampleRate)
        val endFrame = gateEndFrame + resolvedAdsr.release * sampleRate
        val releaseFrames = (resolvedAdsr.release * sampleRate).toInt()

        val signalCtx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = voiceDurationFrames,
            gateEndFrame = voiceDurationFrames,
            releaseFrames = releaseFrames,
            scratchBuffers = scratchBuffers,
            random = voiceRandom,
        )

        val pipeline = buildPitchPipeline(
            vibrato = vibrato,
            accelerate = accelerate,
            pitchEnvelope = pitchEnvelope,
            fm = fm,
            freqHz = freqHz,
            sampleRate = sampleRate,
            startFrame = startFrame,
            endFrame = endFrame,
        ) + IgniteRenderer(
            signal = signal,
            signalCtx = signalCtx,
            freqHz = freqHz,
            startFrame = startFrame,
        ) + buildFilterPipeline(
            pipeline = pipelineRegistry.get(data.pipeline),
            modulators = modulators,
            startFrame = startFrame,
            crush = crush,
            coarse = coarse,
            mainFilter = bakedFilters,
            envelope = envelope,
            distort = distort,
            tremolo = tremolo,
            phaser = phaser,
            sampleRate = sampleRate,
            vcaOn = resolvedAdsr.on,
        )

        val blockCtx = BlockContext(
            audioBuffer = voiceBuffer,
            freqModBuffer = freqModBuffer,
            scratchBuffers = scratchBuffers,
            sampleRate = sampleRate,
            startFrame = startFrame,
            endFrame = endFrame,
            gateEndFrame = gateEndFrame,
            freqHz = freqHz,
            signal = signal,
            signalCtx = signalCtx,
            cylinders = cylinders,
        )

        return Voice(
            cylinderId = cylinder,
            startFrame = startFrame,
            endFrame = endFrame,
            gateEndFrame = gateEndFrame,
            gain = gain,
            pan = data.pan ?: 0.5,
            phaser = phaser,
            // By reference, never a copy: the map is immutable on the wire and only the orbit's
            // owner reads it (see Voice.katalystParams).
            katalystParams = data.katalystParams,
            cut = cut,
            cull = cull,
            pipeline = pipeline,
            blockCtx = blockCtx,
            mainFilter = bakedFilters,
        )
    }
}
