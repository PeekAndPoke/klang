/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel

import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.FilterDefs
import io.peekandpoke.klang.audio_bridge.FilterEnvDef
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.PipelineValue
import io.peekandpoke.klang.audio_bridge.SoundValue
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.uniqueId

/**
 * Sprudel-specific voice data with flat fields.
 *
 * This is the intermediate representation used within the Sprudel pattern system.
 * It uses flat fields (no complex objects like AdsrDef or FilterDefs) to match
 * the flat value model of cyclic-pattern languages.
 *
 * Gets converted to [VoiceData] when passed to the audio engine.
 *
 * **All properties are `var` by design — for performance.** The pattern engine mutates voice data in
 * place down the modifier chain (via [io.peekandpoke.klang.sprudel.lang.voiceSetter]) instead of
 * allocating a fresh copy per modifier, which is what previously dominated query cost. The trade-off:
 * an instance is NOT safe to share — **the caller is responsible for cloning when a value might be
 * reused or handed to more than one consumer** (use [clone]). The leaf emitters (`AtomicPattern`,
 * `AtomicInfinitePattern`) clone on emission so every queried event owns its
 * data; mutate freely from there. There is intentionally no shared `empty` singleton — construct a
 * fresh one with `SprudelVoiceData()`. See `docs/tasks/mutable-voicedata-optimization.md`.
 */
data class SprudelVoiceData(
    // note, scale, freq
    var note: String?,
    var freqHz: Double?,
    var scale: String?,
    /** Chord name (e.g., "Cmaj7", "Dm7", "F/A") for harmonic context */
    var chord: String?,

    // Gain / Dynamics
    var gain: Double?,
    var legato: Double?,
    /** Volume scaling (0-1), multiplies with gain */
    var velocity: Double?,
    /** Gain applied after voice processing, before mixing to the cylinder */
    var postGain: Double?,

    // Sound, bank, sound index
    /** Sample bank (e.g. "MPC60" or "AkaiMPC60"), optional.*/
    var bank: String?,
    /**
     * The sound this voice references. Either a [SoundValue.Named] (sample bank entry,
     * pre-registered ignitor, etc., possibly with `name:index` form parsed into [soundIndex])
     * or a [SoundValue.Osc] inlining an [IgnitorDsl] tree — the latter gets denormalized to
     * a synthetic name at the wire boundary by [toVoiceData].
     */
    var sound: SoundValue?,
    /** Sound index */
    var soundIndex: Int?,

    // Oscillator parameters (generic map: "density", "voices", "freqSpread", "panSpread", "warmth")
    var oscParams: Map<String, Double>?,

    // ADSR amplitude envelope — grouped (see SvdAdsr). Flat fields (attack/decay/…) are accessors below.
    var adsr: SvdAdsr?,

    // Pitch modulation (glide + vibrato) — grouped (see SvdPitchMod). Flat fields are accessors below.
    var pitchMod: SvdPitchMod?,

    // Pitch envelope — grouped (see SvdPitchEnv).
    var pitchEnv: SvdPitchEnv?,

    // FM synthesis — grouped (see SvdFm).
    var fm: SvdFm?,

    // Distortion + lo-fi (coarse / crush) — grouped (see SvdDistortion).
    var distortion: SvdDistortion?,

    // Phaser — grouped (see SvdPhaser).
    var phaser: SvdPhaser?,

    // Tremolo — grouped (see SvdTremolo).
    var tremolo: SvdTremolo?,

    // Ducking / sidechain — grouped (see SvdDuck).
    var duck: SvdDuck?,

    // Filters — grouped (see SvdFilter): cutoff + resonance + optional envelope, one group per filter type.
    // Flat fields (cutoff/hcutoff/bandf/notchf, resonance/…, lp*/hp*/bp*/nf*) are accessors below.
    var lpf: SvdFilter?,
    var hpf: SvdFilter?,
    var bpf: SvdFilter?,
    var notch: SvdFilter?,

    // Routing
    /** The mix channel / bus / orbit / cylinder */
    var cylinder: Int?,

    // Panning (-1.0 = Left, 0.0 = Center, 1.0 = Right)
    var pan: Double?,

    // Delay — grouped (see SvdDelay). Property is `delayFx` (the flat `delay` mix-amount is an accessor below).
    var delayFx: SvdDelay?,

    // Reverb — grouped (see SvdReverb).
    var reverb: SvdReverb?,

    // Sample manipulation — grouped (see SvdSample).
    var sample: SvdSample?,

    // Voice / Singing
    /** Vowel formant filter (a, e, i, o, u) */
    var vowel: String?,
    /** Vowel formant dry/wet amount (0.0 = dry source, higher = more vowel colour). Null → default. */
    var vowelMix: Double?,

    // Body resonator
    /** Body resonator material (wood, tube, glass, membrane) — fixed modal resonances mixed over the dry source. */
    var body: String?,
    /** Body resonator dry/wet mix (0.0 = dry, 1.0 = wet). Null → default mix. */
    var bodyMix: Double?,

    // Dynamics / Compression
    /** Dynamic range compression settings (threshold:ratio:knee:attack:release) */
    var compressor: String?,

    // Playback control
    /** Solo value - 0.0 = disabled, 0.0..1.0 = enabled (amount), null = not set */
    var solo: Double?,

    /** Unique pattern ID for tracking solo state across pattern changes */
    var patternId: String?,

    /**
     * The voice pipeline this voice references. Either a [PipelineValue.Named] (a built-in like
     * `"modern"`/`"pedal"`, or a pre-registered custom) or a [PipelineValue.Dsl] inlining a [PipelineDsl]
     * stage chain — the latter gets denormalized to a synthetic name in [toVoiceData]. Unknown/null → modern.
     */
    var pipeline: PipelineValue?,

    // Custom value
    var value: SprudelVoiceValue?,
) {

    // --- Flat-field accessors over the grouped storage -------------------------------------------------
    // Bridge so the rest of the engine/DSL/tests keep using the flat names (data.attack, data.cutoff, …)
    // while storage is grouped. A non-null write lazily creates the group; a null write only clears an
    // existing group (never allocates an empty one). Reads are null-safe through the (possibly null) group.

    private fun adsrOrNew(): SvdAdsr = adsr ?: SvdAdsr().also { adsr = it }
    private fun lpfOrNew(): SvdFilter = lpf ?: SvdFilter().also { lpf = it }
    private fun hpfOrNew(): SvdFilter = hpf ?: SvdFilter().also { hpf = it }
    private fun bpfOrNew(): SvdFilter = bpf ?: SvdFilter().also { bpf = it }
    private fun notchOrNew(): SvdFilter = notch ?: SvdFilter().also { notch = it }
    private fun pitchModOrNew(): SvdPitchMod = pitchMod ?: SvdPitchMod().also { pitchMod = it }
    private fun pitchEnvOrNew(): SvdPitchEnv = pitchEnv ?: SvdPitchEnv().also { pitchEnv = it }
    private fun fmOrNew(): SvdFm = fm ?: SvdFm().also { fm = it }
    private fun distortionOrNew(): SvdDistortion = distortion ?: SvdDistortion().also { distortion = it }
    private fun phaserOrNew(): SvdPhaser = phaser ?: SvdPhaser().also { phaser = it }
    private fun tremoloOrNew(): SvdTremolo = tremolo ?: SvdTremolo().also { tremolo = it }
    private fun duckOrNew(): SvdDuck = duck ?: SvdDuck().also { duck = it }
    private fun delayFxOrNew(): SvdDelay = delayFx ?: SvdDelay().also { delayFx = it }
    private fun reverbOrNew(): SvdReverb = reverb ?: SvdReverb().also { reverb = it }
    private fun sampleOrNew(): SvdSample = sample ?: SvdSample().also { sample = it }

    var attack: Double?
        get() = adsr?.attack
        set(v) {
            if (v != null || adsr != null) adsrOrNew().attack = v
        }
    var decay: Double?
        get() = adsr?.decay
        set(v) {
            if (v != null || adsr != null) adsrOrNew().decay = v
        }
    var sustain: Double?
        get() = adsr?.sustain
        set(v) {
            if (v != null || adsr != null) adsrOrNew().sustain = v
        }
    var release: Double?
        get() = adsr?.release
        set(v) {
            if (v != null || adsr != null) adsrOrNew().release = v
        }
    var attackCurve: AdsrCurve?
        get() = adsr?.attackCurve
        set(v) {
            if (v != null || adsr != null) adsrOrNew().attackCurve = v
        }
    var decayCurve: AdsrCurve?
        get() = adsr?.decayCurve
        set(v) {
            if (v != null || adsr != null) adsrOrNew().decayCurve = v
        }
    var releaseCurve: AdsrCurve?
        get() = adsr?.releaseCurve
        set(v) {
            if (v != null || adsr != null) adsrOrNew().releaseCurve = v
        }

    var cutoff: Double?
        get() = lpf?.cutoff
        set(v) {
            if (v != null || lpf != null) lpfOrNew().cutoff = v
        }
    var resonance: Double?
        get() = lpf?.resonance
        set(v) {
            if (v != null || lpf != null) lpfOrNew().resonance = v
        }
    var lpattack: Double?
        get() = lpf?.attack
        set(v) {
            if (v != null || lpf != null) lpfOrNew().attack = v
        }
    var lpdecay: Double?
        get() = lpf?.decay
        set(v) {
            if (v != null || lpf != null) lpfOrNew().decay = v
        }
    var lpsustain: Double?
        get() = lpf?.sustain
        set(v) {
            if (v != null || lpf != null) lpfOrNew().sustain = v
        }
    var lprelease: Double?
        get() = lpf?.release
        set(v) {
            if (v != null || lpf != null) lpfOrNew().release = v
        }
    var lpenv: Double?
        get() = lpf?.env
        set(v) {
            if (v != null || lpf != null) lpfOrNew().env = v
        }

    var hcutoff: Double?
        get() = hpf?.cutoff
        set(v) {
            if (v != null || hpf != null) hpfOrNew().cutoff = v
        }
    var hresonance: Double?
        get() = hpf?.resonance
        set(v) {
            if (v != null || hpf != null) hpfOrNew().resonance = v
        }
    var hpattack: Double?
        get() = hpf?.attack
        set(v) {
            if (v != null || hpf != null) hpfOrNew().attack = v
        }
    var hpdecay: Double?
        get() = hpf?.decay
        set(v) {
            if (v != null || hpf != null) hpfOrNew().decay = v
        }
    var hpsustain: Double?
        get() = hpf?.sustain
        set(v) {
            if (v != null || hpf != null) hpfOrNew().sustain = v
        }
    var hprelease: Double?
        get() = hpf?.release
        set(v) {
            if (v != null || hpf != null) hpfOrNew().release = v
        }
    var hpenv: Double?
        get() = hpf?.env
        set(v) {
            if (v != null || hpf != null) hpfOrNew().env = v
        }

    var bandf: Double?
        get() = bpf?.cutoff
        set(v) {
            if (v != null || bpf != null) bpfOrNew().cutoff = v
        }
    var bandq: Double?
        get() = bpf?.resonance
        set(v) {
            if (v != null || bpf != null) bpfOrNew().resonance = v
        }
    var bpattack: Double?
        get() = bpf?.attack
        set(v) {
            if (v != null || bpf != null) bpfOrNew().attack = v
        }
    var bpdecay: Double?
        get() = bpf?.decay
        set(v) {
            if (v != null || bpf != null) bpfOrNew().decay = v
        }
    var bpsustain: Double?
        get() = bpf?.sustain
        set(v) {
            if (v != null || bpf != null) bpfOrNew().sustain = v
        }
    var bprelease: Double?
        get() = bpf?.release
        set(v) {
            if (v != null || bpf != null) bpfOrNew().release = v
        }
    var bpenv: Double?
        get() = bpf?.env
        set(v) {
            if (v != null || bpf != null) bpfOrNew().env = v
        }

    var notchf: Double?
        get() = notch?.cutoff
        set(v) {
            if (v != null || notch != null) notchOrNew().cutoff = v
        }
    var nresonance: Double?
        get() = notch?.resonance
        set(v) {
            if (v != null || notch != null) notchOrNew().resonance = v
        }
    var nfattack: Double?
        get() = notch?.attack
        set(v) {
            if (v != null || notch != null) notchOrNew().attack = v
        }
    var nfdecay: Double?
        get() = notch?.decay
        set(v) {
            if (v != null || notch != null) notchOrNew().decay = v
        }
    var nfsustain: Double?
        get() = notch?.sustain
        set(v) {
            if (v != null || notch != null) notchOrNew().sustain = v
        }
    var nfrelease: Double?
        get() = notch?.release
        set(v) {
            if (v != null || notch != null) notchOrNew().release = v
        }
    var nfenv: Double?
        get() = notch?.env
        set(v) {
            if (v != null || notch != null) notchOrNew().env = v
        }

    var accelerate: Double?
        get() = pitchMod?.accelerate
        set(v) {
            if (v != null || pitchMod != null) pitchModOrNew().accelerate = v
        }
    var vibrato: Double?
        get() = pitchMod?.vibrato
        set(v) {
            if (v != null || pitchMod != null) pitchModOrNew().vibrato = v
        }
    var vibratoMod: Double?
        get() = pitchMod?.vibratoMod
        set(v) {
            if (v != null || pitchMod != null) pitchModOrNew().vibratoMod = v
        }

    var pAttack: Double?
        get() = pitchEnv?.pAttack
        set(v) {
            if (v != null || pitchEnv != null) pitchEnvOrNew().pAttack = v
        }
    var pDecay: Double?
        get() = pitchEnv?.pDecay
        set(v) {
            if (v != null || pitchEnv != null) pitchEnvOrNew().pDecay = v
        }
    var pRelease: Double?
        get() = pitchEnv?.pRelease
        set(v) {
            if (v != null || pitchEnv != null) pitchEnvOrNew().pRelease = v
        }
    var pEnv: Double?
        get() = pitchEnv?.pEnv
        set(v) {
            if (v != null || pitchEnv != null) pitchEnvOrNew().pEnv = v
        }
    var pCurve: Double?
        get() = pitchEnv?.pCurve
        set(v) {
            if (v != null || pitchEnv != null) pitchEnvOrNew().pCurve = v
        }
    var pAnchor: Double?
        get() = pitchEnv?.pAnchor
        set(v) {
            if (v != null || pitchEnv != null) pitchEnvOrNew().pAnchor = v
        }

    var fmh: Double?
        get() = fm?.fmh
        set(v) {
            if (v != null || fm != null) fmOrNew().fmh = v
        }
    var fmAttack: Double?
        get() = fm?.fmAttack
        set(v) {
            if (v != null || fm != null) fmOrNew().fmAttack = v
        }
    var fmDecay: Double?
        get() = fm?.fmDecay
        set(v) {
            if (v != null || fm != null) fmOrNew().fmDecay = v
        }
    var fmSustain: Double?
        get() = fm?.fmSustain
        set(v) {
            if (v != null || fm != null) fmOrNew().fmSustain = v
        }
    var fmEnv: Double?
        get() = fm?.fmEnv
        set(v) {
            if (v != null || fm != null) fmOrNew().fmEnv = v
        }

    var distort: Double?
        get() = distortion?.distort
        set(v) {
            if (v != null || distortion != null) distortionOrNew().distort = v
        }
    var distortShape: String?
        get() = distortion?.distortShape
        set(v) {
            if (v != null || distortion != null) distortionOrNew().distortShape = v
        }
    var distortOversample: Int?
        get() = distortion?.distortOversample
        set(v) {
            if (v != null || distortion != null) distortionOrNew().distortOversample = v
        }
    var coarse: Double?
        get() = distortion?.coarse
        set(v) {
            if (v != null || distortion != null) distortionOrNew().coarse = v
        }
    var coarseOversample: Int?
        get() = distortion?.coarseOversample
        set(v) {
            if (v != null || distortion != null) distortionOrNew().coarseOversample = v
        }
    var crush: Double?
        get() = distortion?.crush
        set(v) {
            if (v != null || distortion != null) distortionOrNew().crush = v
        }
    var crushOversample: Int?
        get() = distortion?.crushOversample
        set(v) {
            if (v != null || distortion != null) distortionOrNew().crushOversample = v
        }

    var phaserRate: Double?
        get() = phaser?.phaserRate
        set(v) {
            if (v != null || phaser != null) phaserOrNew().phaserRate = v
        }
    var phaserDepth: Double?
        get() = phaser?.phaserDepth
        set(v) {
            if (v != null || phaser != null) phaserOrNew().phaserDepth = v
        }
    var phaserCenter: Double?
        get() = phaser?.phaserCenter
        set(v) {
            if (v != null || phaser != null) phaserOrNew().phaserCenter = v
        }
    var phaserSweep: Double?
        get() = phaser?.phaserSweep
        set(v) {
            if (v != null || phaser != null) phaserOrNew().phaserSweep = v
        }

    var tremoloSync: Double?
        get() = tremolo?.tremoloSync
        set(v) {
            if (v != null || tremolo != null) tremoloOrNew().tremoloSync = v
        }
    var tremoloDepth: Double?
        get() = tremolo?.tremoloDepth
        set(v) {
            if (v != null || tremolo != null) tremoloOrNew().tremoloDepth = v
        }
    var tremoloSkew: Double?
        get() = tremolo?.tremoloSkew
        set(v) {
            if (v != null || tremolo != null) tremoloOrNew().tremoloSkew = v
        }
    var tremoloPhase: Double?
        get() = tremolo?.tremoloPhase
        set(v) {
            if (v != null || tremolo != null) tremoloOrNew().tremoloPhase = v
        }
    var tremoloShape: String?
        get() = tremolo?.tremoloShape
        set(v) {
            if (v != null || tremolo != null) tremoloOrNew().tremoloShape = v
        }

    var duckCylinder: Int?
        get() = duck?.duckCylinder
        set(v) {
            if (v != null || duck != null) duckOrNew().duckCylinder = v
        }
    var duckAttack: Double?
        get() = duck?.duckAttack
        set(v) {
            if (v != null || duck != null) duckOrNew().duckAttack = v
        }
    var duckDepth: Double?
        get() = duck?.duckDepth
        set(v) {
            if (v != null || duck != null) duckOrNew().duckDepth = v
        }

    var delay: Double?
        get() = delayFx?.delay
        set(v) {
            if (v != null || delayFx != null) delayFxOrNew().delay = v
        }
    var delayTime: Double?
        get() = delayFx?.delayTime
        set(v) {
            if (v != null || delayFx != null) delayFxOrNew().delayTime = v
        }
    var delayFeedback: Double?
        get() = delayFx?.delayFeedback
        set(v) {
            if (v != null || delayFx != null) delayFxOrNew().delayFeedback = v
        }

    var room: Double?
        get() = reverb?.room
        set(v) {
            if (v != null || reverb != null) reverbOrNew().room = v
        }
    var roomSize: Double?
        get() = reverb?.roomSize
        set(v) {
            if (v != null || reverb != null) reverbOrNew().roomSize = v
        }
    var roomFade: Double?
        get() = reverb?.roomFade
        set(v) {
            if (v != null || reverb != null) reverbOrNew().roomFade = v
        }
    var roomLp: Double?
        get() = reverb?.roomLp
        set(v) {
            if (v != null || reverb != null) reverbOrNew().roomLp = v
        }
    var roomDim: Double?
        get() = reverb?.roomDim
        set(v) {
            if (v != null || reverb != null) reverbOrNew().roomDim = v
        }
    var iResponse: String?
        get() = reverb?.iResponse
        set(v) {
            if (v != null || reverb != null) reverbOrNew().iResponse = v
        }

    var begin: Double?
        get() = sample?.begin
        set(v) {
            if (v != null || sample != null) sampleOrNew().begin = v
        }
    var end: Double?
        get() = sample?.end
        set(v) {
            if (v != null || sample != null) sampleOrNew().end = v
        }
    var speed: Double?
        get() = sample?.speed
        set(v) {
            if (v != null || sample != null) sampleOrNew().speed = v
        }
    var unit: String?
        get() = sample?.unit
        set(v) {
            if (v != null || sample != null) sampleOrNew().unit = v
        }
    var loop: Boolean?
        get() = sample?.loop
        set(v) {
            if (v != null || sample != null) sampleOrNew().loop = v
        }
    var cut: Int?
        get() = sample?.cut
        set(v) {
            if (v != null || sample != null) sampleOrNew().cut = v
        }
    var loopBegin: Double?
        get() = sample?.loopBegin
        set(v) {
            if (v != null || sample != null) sampleOrNew().loopBegin = v
        }
    var loopEnd: Double?
        get() = sample?.loopEnd
        set(v) {
            if (v != null || sample != null) sampleOrNew().loopEnd = v
        }
    // --------------------------------------------------------------------------------------------------

    /**
     * Fresh deep-enough copy: the flat core fields are copied shallow (immutable scalars), and each
     * non-null group is `copy()`-ed so the clone owns its own groups (single-owner invariant — see the
     * leaf emitters `AtomicPattern`/`AtomicInfinitePattern`). `oscParams` is
     * treated as immutable-replace, so sharing its reference is fine. As more clusters become groups,
     * add them to the deep-copy list here.
     */
    fun clone(): SprudelVoiceData = copy(
        adsr = adsr?.copy(),
        lpf = lpf?.copy(),
        hpf = hpf?.copy(),
        bpf = bpf?.copy(),
        notch = notch?.copy(),
        pitchMod = pitchMod?.copy(),
        pitchEnv = pitchEnv?.copy(),
        fm = fm?.copy(),
        distortion = distortion?.copy(),
        phaser = phaser?.copy(),
        tremolo = tremolo?.copy(),
        duck = duck?.copy(),
        delayFx = delayFx?.copy(),
        reverb = reverb?.copy(),
        sample = sample?.copy(),
    )

    fun merge(other: SprudelVoiceData): SprudelVoiceData {
        return SprudelVoiceData(
            note = other.note ?: note,
            freqHz = other.freqHz ?: freqHz,
            scale = other.scale ?: scale,
            chord = other.chord ?: chord,
            gain = other.gain ?: gain,
            legato = other.legato ?: legato,
            velocity = other.velocity ?: velocity,
            postGain = other.postGain ?: postGain,
            bank = other.bank ?: bank,
            sound = other.sound ?: sound,
            soundIndex = other.soundIndex ?: soundIndex,
            oscParams = mergeOscParams(oscParams, other.oscParams),
            adsr = mergeSvdAdsr(adsr, other.adsr),
            pitchMod = mergeSvdPitchMod(pitchMod, other.pitchMod),
            pitchEnv = mergeSvdPitchEnv(pitchEnv, other.pitchEnv),
            fm = mergeSvdFm(fm, other.fm),
            distortion = mergeSvdDistortion(distortion, other.distortion),
            phaser = mergeSvdPhaser(phaser, other.phaser),
            tremolo = mergeSvdTremolo(tremolo, other.tremolo),
            duck = mergeSvdDuck(duck, other.duck),
            lpf = mergeSvdFilter(lpf, other.lpf),
            hpf = mergeSvdFilter(hpf, other.hpf),
            bpf = mergeSvdFilter(bpf, other.bpf),
            notch = mergeSvdFilter(notch, other.notch),
            cylinder = other.cylinder ?: cylinder,
            pan = other.pan ?: pan,
            delayFx = mergeSvdDelay(delayFx, other.delayFx),
            reverb = mergeSvdReverb(reverb, other.reverb),
            sample = mergeSvdSample(sample, other.sample),
            vowel = other.vowel ?: vowel,
            vowelMix = other.vowelMix ?: vowelMix,
            body = other.body ?: body,
            bodyMix = other.bodyMix ?: bodyMix,
            compressor = other.compressor ?: compressor,
            solo = other.solo ?: solo,
            patternId = patternId,  // Never merge - preserve original source ID
            pipeline = other.pipeline ?: pipeline,
            value = other.value ?: value
        )
    }

    /**
     * In-place counterpart of [merge]: folds [other]'s non-null fields into this instance (other wins),
     * mutating it rather than allocating. `patternId` is preserved (never taken from other), matching
     * [merge]. Only safe on a single-owner instance (see [clone]). Guarded against drift from [merge] by
     * `SprudelVoiceDataSpec`.
     */
    fun mergeFrom(other: SprudelVoiceData) {
        note = other.note ?: note
        freqHz = other.freqHz ?: freqHz
        scale = other.scale ?: scale
        chord = other.chord ?: chord
        gain = other.gain ?: gain
        legato = other.legato ?: legato
        velocity = other.velocity ?: velocity
        postGain = other.postGain ?: postGain
        bank = other.bank ?: bank
        sound = other.sound ?: sound
        soundIndex = other.soundIndex ?: soundIndex
        oscParams = mergeOscParams(oscParams, other.oscParams)
        adsr = mergeSvdAdsr(adsr, other.adsr)
        pitchMod = mergeSvdPitchMod(pitchMod, other.pitchMod)
        pitchEnv = mergeSvdPitchEnv(pitchEnv, other.pitchEnv)
        fm = mergeSvdFm(fm, other.fm)
        distortion = mergeSvdDistortion(distortion, other.distortion)
        phaser = mergeSvdPhaser(phaser, other.phaser)
        tremolo = mergeSvdTremolo(tremolo, other.tremolo)
        duck = mergeSvdDuck(duck, other.duck)
        lpf = mergeSvdFilter(lpf, other.lpf)
        hpf = mergeSvdFilter(hpf, other.hpf)
        bpf = mergeSvdFilter(bpf, other.bpf)
        notch = mergeSvdFilter(notch, other.notch)
        cylinder = other.cylinder ?: cylinder
        pan = other.pan ?: pan
        delayFx = mergeSvdDelay(delayFx, other.delayFx)
        reverb = mergeSvdReverb(reverb, other.reverb)
        sample = mergeSvdSample(sample, other.sample)
        vowel = other.vowel ?: vowel
        vowelMix = other.vowelMix ?: vowelMix
        body = other.body ?: body
        bodyMix = other.bodyMix ?: bodyMix
        compressor = other.compressor ?: compressor
        solo = other.solo ?: solo
        // patternId intentionally preserved (never taken from other) — matches merge()
        pipeline = other.pipeline ?: pipeline
        value = other.value ?: value
    }

    fun isTruthy(): Boolean {
        val noteStr = note ?: ""
        // "0" and "false" strings are false, "~" is false (but usually filtered out before)
        val noteTruthy = noteStr.isNotEmpty() && noteStr != "~" && noteStr != "0" && noteStr != "false"

        val valueTruthy = value?.isTruthy() ?: false

        return valueTruthy || noteTruthy
    }

    fun isNotTruthy(): Boolean {
        return !isTruthy()
    }

    /**
     * Converts this Sprudel-specific voice data to audio engine [VoiceData].
     *
     * Maps flat fields to complex objects:
     * - attack, decay, sustain, release → AdsrDef
     * - cutoff/resonance, hcutoff/hresonance, bandf/bandq, notchf/nresonance → FilterDefs
     *
     * For inline ignitors ([SoundValue.Osc]) the wire-level `sound` name is resolved via
     * the process-wide [uniqueId] map — playbacks are expected to pre-register inline
     * ignitors with their backend so that name is already known to the runtime by the
     * time voice events referencing it are scheduled.
     */
    fun toVoiceData(): VoiceData {
        val soundName: String? = when (val s = sound) {
            null -> null
            is SoundValue.Named -> s.name
            is SoundValue.Osc -> s.osc.uniqueId()
        }

        // Inline pipelines ([PipelineValue.Dsl]) resolve to their stable synthetic name (uniqueId);
        // names pass through. Mirrors the sound resolution above.
        val pipelineName: String? = when (val p = pipeline) {
            null -> null
            is PipelineValue.Named -> p.name
            is PipelineValue.Dsl -> p.pipeline.uniqueId()
        }

        // Build filter list from flat fields, each with its own resonance
        val filters = buildList {
            cutoff?.let { cutoffValue ->
                // Build envelope if any lpattack/lpdecay/lpsustain/lprelease/lpenv fields are present
                val envelope =
                    if (lpattack != null || lpdecay != null || lpsustain != null || lprelease != null || lpenv != null) {
                        FilterEnvDef(
                            attack = lpattack,
                            decay = lpdecay,
                            sustain = lpsustain,
                            release = lprelease,
                            depth = lpenv,
                        )
                    } else {
                        null
                    }

                add(
                    FilterDef.LowPass(
                        cutoffHz = cutoffValue,
                        q = resonance ?: 1.0,
                        envelope = envelope
                    )
                )
            }
            hcutoff?.let { hcutoffValue ->
                // Build envelope if any hpattack/hpdecay/hpsustain/hprelease/hpenv fields are present
                val envelope =
                    if (hpattack != null || hpdecay != null || hpsustain != null || hprelease != null || hpenv != null) {
                        FilterEnvDef(
                            attack = hpattack,
                            decay = hpdecay,
                            sustain = hpsustain,
                            release = hprelease,
                            depth = hpenv,
                        )
                    } else {
                        null
                    }

                add(
                    FilterDef.HighPass(
                        cutoffHz = hcutoffValue,
                        q = hresonance ?: 1.0,
                        envelope = envelope
                    )
                )
            }
            bandf?.let { bandfValue ->
                // Build envelope if any bpattack/bpdecay/bpsustain/bprelease/bpenv fields are present
                val envelope =
                    if (bpattack != null || bpdecay != null || bpsustain != null || bprelease != null || bpenv != null) {
                        FilterEnvDef(
                            attack = bpattack,
                            decay = bpdecay,
                            sustain = bpsustain,
                            release = bprelease,
                            depth = bpenv,
                        )
                    } else {
                        null
                    }

                add(
                    FilterDef.BandPass(
                        cutoffHz = bandfValue,
                        q = bandq ?: 1.0,
                        envelope = envelope
                    )
                )
            }
            notchf?.let { notchfValue ->
                // Build envelope if any nfattack/nfdecay/nfsustain/nfrelease/nfenv fields are present
                val envelope =
                    if (nfattack != null || nfdecay != null || nfsustain != null || nfrelease != null || nfenv != null) {
                        FilterEnvDef(
                            attack = nfattack,
                            decay = nfdecay,
                            sustain = nfsustain,
                            release = nfrelease,
                            depth = nfenv,
                        )
                    } else {
                        null
                    }

                add(
                    FilterDef.Notch(
                        cutoffHz = notchfValue,
                        q = nresonance ?: 1.0,
                        envelope = envelope
                    )
                )
            }

            // Vowel formant filter — blended over the dry source (source-filter model), like body.
            vowel?.let { vowelValue ->
                val formantBands = resolveVowelBands(vowelValue)

                formantBands?.let { bands ->
                    add(FilterDef.Formant(bands = bands, mix = vowelMix ?: 0.5))
                }
            }

            // Body resonator — fixed modal resonances blended over the dry source.
            body?.let { material ->
                resolveBodyModes(material)?.let { modes ->
                    // Default body amount when the user didn't set bodyMix — a moderate, audible
                    // amount (0..1; the blend keeps a broadband floor, so it never thins).
                    add(FilterDef.Body(bands = modes, mix = bodyMix ?: 0.5))
                }
            }
        }

        // Canonical filter chain order: HIGHPASS → BANDPASS → NOTCH → FORMANT → LOWPASS.
        // Chain order is audible once the filters are nonlinear (analog>0 enables the
        // analog-style state-dependent saturation, which does NOT commute): the highpass strips
        // bass before the lowpass's saturator sees it, and the lowpass sits LAST to tame
        // harmonics generated upstream — the "lowpass after distortion" rule, matching
        // the MS-20 / Juno / Diva convention. sprudel's flat fields carry no order of
        // their own, so we impose the canonical order here; the engine (VoiceFactory)
        // bakes whatever order it is handed. At analog=0 the filters are linear and
        // commute, so this ordering is spectrally a no-op.
        val orderedFilters = filters.sortedBy { def ->
            when (def) {
                is FilterDef.HighPass -> 0
                is FilterDef.BandPass -> 1
                is FilterDef.Notch -> 2
                is FilterDef.Formant -> 3
                // Body sits before the lowpass so the resonator sees full-spectrum input and
                // the lowpass tames whatever the body emphasizes (same "lowpass last" logic).
                is FilterDef.Body -> 4
                is FilterDef.LowPass -> 5
            }
        }

        return VoiceData(
            note = note,
            freqHz = freqHz,
            scale = scale,
            gain = gain,
            velocity = velocity,
            postGain = postGain,
            legato = legato,
            bank = bank,
            sound = soundName,
            soundIndex = soundIndex,
            oscParams = oscParams,
            filters = FilterDefs(orderedFilters),
            adsr = AdsrDef.Std(
                attack = attack,
                decay = decay,
                sustain = sustain,
                release = release,
                attackCurve = attackCurve,
                decayCurve = decayCurve,
                releaseCurve = releaseCurve,
            ),
            accelerate = accelerate,
            vibrato = vibrato,
            vibratoMod = vibratoMod,
            pAttack = pAttack,
            pDecay = pDecay,
            pRelease = pRelease,
            pEnv = pEnv,
            pCurve = pCurve,
            pAnchor = pAnchor,
            fmh = fmh,
            fmAttack = fmAttack,
            fmDecay = fmDecay,
            fmSustain = fmSustain,
            fmEnv = fmEnv,
            distort = distort,
            distortShape = distortShape,
            distortOversample = distortOversample,
            coarse = coarse,
            coarseOversample = coarseOversample,
            crush = crush,
            crushOversample = crushOversample,
            phaser = phaserRate,
            phaserDepth = phaserDepth,
            phaserCenter = phaserCenter,
            phaserSweep = phaserSweep,
            tremoloSync = tremoloSync,
            tremoloDepth = tremoloDepth,
            tremoloSkew = tremoloSkew,
            tremoloPhase = tremoloPhase,
            tremoloShape = tremoloShape,
            duckCylinder = duckCylinder,
            duckAttack = duckAttack,
            duckDepth = duckDepth,
            cutoff = cutoff,
            hcutoff = hcutoff,
            bandf = bandf,
            resonance = resonance, // For backward compatibility, use LPF resonance as default
            cylinder = cylinder,
            pan = pan,
            delay = delay,
            delayTime = delayTime,
            delayFeedback = delayFeedback,
            room = room,
            roomSize = roomSize,
            roomFade = roomFade,
            roomLp = roomLp,
            roomDim = roomDim,
            iResponse = iResponse,
            begin = begin,
            end = end,
            speed = speed,
            loop = loop,
            cut = cut,
            loopBegin = loopBegin,
            loopEnd = loopEnd,
            compressor = compressor,
            solo = solo,
            sourceId = patternId,
            pipeline = pipelineName,
        )
    }

    /**
     * Resolves a body-resonator material name to its fixed modal resonances. Returns null for
     * an unknown material — the body is then skipped (fail soft, never throw on user input).
     *
     * Each mode is `freq Hz / db / Q`. After the bank's `1/Q` normalization (in `BodyFilter`),
     * `db` is the mode's *actual* peak emphasis in dB — a few dB, not 20+. Modes are dense so the
     * bank covers the spectrum (overlapping skirts keep the inter-mode response up); higher modes
     * use lower Q so they ring shorter, as real bodies damp high frequencies faster.
     *
     * Starting-point tables — expect to tune `db`, the mode sets, and `BODY_FLOOR` by ear.
     */
    private fun resolveBodyModes(material: String): List<FilterDef.Body.Mode>? {
        fun m(freq: Double, db: Double, q: Double) = FilterDef.Body.Mode(freq, db, q)

        return when (material.lowercase()) {
            // Warm resonant box (guitar/marimba-ish body).
            "wood" -> listOf(
                m(100.0, 3.0, 12.0),
                m(200.0, 2.0, 11.0),
                m(300.0, 1.0, 10.0),
                m(430.0, 0.0, 9.0),
                m(650.0, -1.0, 8.0),
                m(900.0, -2.0, 7.0),
                m(1300.0, -4.0, 6.0),
                m(1900.0, -6.0, 5.0),
            )
            // Resonant pipe with a body — the de-plasticized tube.
            "tube" -> listOf(
                m(85.0, 4.0, 14.0),
                m(175.0, 2.0, 12.0),
                m(270.0, 1.0, 11.0),
                m(450.0, 0.0, 9.0),
                m(700.0, -1.0, 8.0),
                m(1000.0, -3.0, 7.0),
                m(1400.0, -5.0, 6.0),
                m(2000.0, -7.0, 5.0),
            )
            // Bright, high-Q, long ring.
            "glass" -> listOf(
                m(700.0, 2.0, 40.0),
                m(1050.0, 1.0, 50.0),
                m(1600.0, 0.0, 55.0),
                m(2100.0, -1.0, 60.0),
                m(2800.0, -2.0, 50.0),
                m(3300.0, -3.0, 45.0),
                m(4000.0, -5.0, 40.0),
                m(4700.0, -7.0, 35.0),
            )
            // Drum-like, inharmonic, fast decay.
            "membrane" -> listOf(
                m(150.0, 2.0, 6.0),
                m(230.0, 1.0, 5.0),
                m(310.0, 0.0, 5.0),
                m(385.0, 0.0, 4.0),
                m(460.0, -1.0, 4.0),
                m(550.0, -2.0, 4.0),
                m(650.0, -3.0, 3.0),
                m(780.0, -4.0, 3.0),
            )

            else -> null
        }
    }

    private fun resolveVowelBands(vowelValue: String): List<FilterDef.Formant.Band>? {
        val parts = vowelValue.lowercase().split(':')
        val (voice, vowel) = if (parts.size > 1) {
            parts[0] to parts[1]
        } else {
            "soprano" to parts[0]
        }

        // Helper for band creation
        fun b(freq: Double, db: Double, q: Double) = FilterDef.Formant.Band(freq, db, q)

        return when (voice) {
            "bass" -> when (vowel) {
                "a", "ei", "au" -> listOf(
                    b(600.0, 0.0, 60.0),
                    b(1040.0, -7.0, 70.0),
                    b(2250.0, -9.0, 110.0),
                    b(2450.0, -9.0, 120.0),
                    b(2750.0, -20.0, 130.0)
                )

                "e" -> listOf(
                    b(400.0, 0.0, 60.0),
                    b(1620.0, -12.0, 70.0),
                    b(2400.0, -9.0, 110.0),
                    b(2800.0, -12.0, 120.0),
                    b(3100.0, -18.0, 130.0)
                )

                "i" -> listOf(
                    b(250.0, 0.0, 60.0),
                    b(1750.0, -30.0, 70.0),
                    b(2600.0, -16.0, 110.0),
                    b(3050.0, -22.0, 120.0),
                    b(3340.0, -28.0, 130.0)
                )

                "o" -> listOf(
                    b(400.0, 0.0, 60.0),
                    b(750.0, -11.0, 70.0),
                    b(2400.0, -21.0, 110.0),
                    b(2600.0, -20.0, 120.0),
                    b(2900.0, -40.0, 130.0)
                )

                "u" -> listOf(
                    b(350.0, 0.0, 60.0),
                    b(600.0, -20.0, 70.0),
                    b(2400.0, -32.0, 110.0),
                    b(2675.0, -28.0, 120.0),
                    b(2950.0, -36.0, 130.0)
                )
                // German Umlauts
                "ae", "ä" -> listOf(
                    b(600.0, 0.0, 60.0),
                    b(1400.0, -10.0, 70.0),
                    b(2200.0, -12.0, 110.0),
                    b(2450.0, -12.0, 120.0),
                    b(2750.0, -22.0, 130.0)
                )

                "oe", "ö" -> listOf(
                    b(400.0, 0.0, 60.0),
                    b(1300.0, -14.0, 70.0),
                    b(2000.0, -12.0, 110.0),
                    b(2400.0, -14.0, 120.0),
                    b(3100.0, -20.0, 130.0)
                )

                "ue", "ü" -> listOf(
                    b(250.0, 0.0, 60.0),
                    b(1400.0, -28.0, 70.0),
                    b(2100.0, -18.0, 110.0),
                    b(3050.0, -24.0, 120.0),
                    b(3340.0, -30.0, 130.0)
                )
                // German Diphthongs (nucleus)
                "eu", "äu" -> listOf(
                    b(500.0, 0.0, 60.0),
                    b(900.0, -10.0, 70.0),
                    b(2300.0, -15.0, 110.0),
                    b(2500.0, -20.0, 120.0),
                    b(2800.0, -30.0, 130.0)
                )

                else -> null
            }

            "tenor" -> when (vowel) {
                "a", "ei", "au" -> listOf(
                    b(650.0, 0.0, 70.0),
                    b(1080.0, -6.0, 80.0),
                    b(2650.0, -7.0, 110.0),
                    b(2900.0, -8.0, 120.0),
                    b(3250.0, -22.0, 130.0)
                )

                "e" -> listOf(
                    b(400.0, 0.0, 70.0),
                    b(1700.0, -14.0, 80.0),
                    b(2600.0, -12.0, 110.0),
                    b(3200.0, -14.0, 120.0),
                    b(3580.0, -20.0, 130.0)
                )

                "i" -> listOf(
                    b(290.0, 0.0, 70.0),
                    b(1870.0, -15.0, 80.0),
                    b(2800.0, -18.0, 110.0),
                    b(3250.0, -20.0, 120.0),
                    b(3540.0, -30.0, 130.0)
                )

                "o" -> listOf(
                    b(450.0, 0.0, 70.0),
                    b(800.0, -11.0, 80.0),
                    b(2830.0, -22.0, 110.0),
                    b(3500.0, -22.0, 120.0),
                    b(3800.0, -50.0, 130.0)
                )

                "u" -> listOf(
                    b(350.0, 0.0, 70.0),
                    b(600.0, -20.0, 80.0),
                    b(2700.0, -17.0, 110.0),
                    b(2900.0, -14.0, 120.0),
                    b(3300.0, -26.0, 130.0)
                )
                // German Umlauts
                "ae", "ä" -> listOf(
                    b(650.0, 0.0, 70.0),
                    b(1500.0, -8.0, 80.0),
                    b(2650.0, -10.0, 110.0),
                    b(2900.0, -12.0, 120.0),
                    b(3250.0, -24.0, 130.0)
                )

                "oe", "ö" -> listOf(
                    b(400.0, 0.0, 70.0),
                    b(1400.0, -16.0, 80.0),
                    b(2200.0, -14.0, 110.0),
                    b(3200.0, -16.0, 120.0),
                    b(3580.0, -22.0, 130.0)
                )

                "ue", "ü" -> listOf(
                    b(290.0, 0.0, 70.0),
                    b(1500.0, -18.0, 80.0),
                    b(2300.0, -20.0, 110.0),
                    b(3250.0, -22.0, 120.0),
                    b(3540.0, -32.0, 130.0)
                )
                // German Diphthongs (nucleus)
                "eu", "äu" -> listOf(
                    b(550.0, 0.0, 70.0),
                    b(950.0, -10.0, 80.0),
                    b(2750.0, -15.0, 110.0),
                    b(2950.0, -20.0, 120.0),
                    b(3300.0, -30.0, 130.0)
                )

                else -> null
            }

            "alto", "countertenor" -> when (vowel) {
                "a", "ei", "au" -> listOf(
                    b(660.0, 0.0, 70.0),
                    b(1120.0, -6.0, 80.0),
                    b(2750.0, -23.0, 110.0),
                    b(3000.0, -24.0, 120.0),
                    b(3350.0, -38.0, 130.0)
                )

                "e" -> listOf(
                    b(440.0, 0.0, 70.0),
                    b(1800.0, -14.0, 80.0),
                    b(2700.0, -18.0, 110.0),
                    b(3000.0, -20.0, 120.0),
                    b(3300.0, -20.0, 130.0)
                )

                "i" -> listOf(
                    b(270.0, 0.0, 70.0),
                    b(1850.0, -20.0, 80.0),
                    b(2900.0, -24.0, 110.0),
                    b(3350.0, -26.0, 120.0),
                    b(3590.0, -36.0, 130.0)
                )

                "o" -> listOf(
                    b(430.0, 0.0, 70.0),
                    b(820.0, -10.0, 80.0),
                    b(2700.0, -26.0, 110.0),
                    b(3000.0, -22.0, 120.0),
                    b(3300.0, -34.0, 130.0)
                )

                "u" -> listOf(
                    b(370.0, 0.0, 70.0),
                    b(630.0, -20.0, 80.0),
                    b(2750.0, -23.0, 110.0),
                    b(3000.0, -24.0, 120.0),
                    b(3400.0, -34.0, 130.0)
                )
                // German Umlauts
                "ae", "ä" -> listOf(
                    b(660.0, 0.0, 70.0),
                    b(1500.0, -8.0, 80.0),
                    b(2750.0, -25.0, 110.0),
                    b(3000.0, -26.0, 120.0),
                    b(3350.0, -40.0, 130.0)
                )

                "oe", "ö" -> listOf(
                    b(440.0, 0.0, 70.0),
                    b(1400.0, -16.0, 80.0),
                    b(2300.0, -20.0, 110.0),
                    b(3000.0, -22.0, 120.0),
                    b(3300.0, -22.0, 130.0)
                )

                "ue", "ü" -> listOf(
                    b(270.0, 0.0, 70.0),
                    b(1500.0, -22.0, 80.0),
                    b(2400.0, -26.0, 110.0),
                    b(3350.0, -28.0, 120.0),
                    b(3590.0, -38.0, 130.0)
                )
                // German Diphthongs (nucleus)
                "eu", "äu" -> listOf(
                    b(550.0, 0.0, 70.0),
                    b(970.0, -10.0, 80.0),
                    b(2750.0, -15.0, 110.0),
                    b(3000.0, -20.0, 120.0),
                    b(3350.0, -30.0, 130.0)
                )

                else -> null
            }

            "soprano" -> when (vowel) {
                "a", "ei", "au" -> listOf(
                    b(800.0, 0.0, 80.0),
                    b(1150.0, -6.0, 90.0),
                    b(2900.0, -32.0, 120.0),
                    b(3900.0, -20.0, 130.0),
                    b(4950.0, -50.0, 140.0)
                )

                "e" -> listOf(
                    b(350.0, 0.0, 80.0),
                    b(2000.0, -20.0, 90.0),
                    b(2800.0, -15.0, 120.0),
                    b(3600.0, -40.0, 130.0),
                    b(4950.0, -56.0, 140.0)
                )

                "i" -> listOf(
                    b(270.0, 0.0, 80.0),
                    b(2140.0, -12.0, 90.0),
                    b(3050.0, -26.0, 120.0),
                    b(4000.0, -26.0, 130.0),
                    b(4950.0, -44.0, 140.0)
                )

                "o" -> listOf(
                    b(450.0, 0.0, 80.0),
                    b(800.0, -11.0, 90.0),
                    b(2830.0, -22.0, 120.0),
                    b(3800.0, -22.0, 130.0),
                    b(4950.0, -50.0, 140.0)
                )

                "u" -> listOf(
                    b(325.0, 0.0, 80.0),
                    b(700.0, -16.0, 90.0),
                    b(2700.0, -35.0, 120.0),
                    b(3800.0, -40.0, 130.0),
                    b(4950.0, -60.0, 140.0)
                )
                // German Umlauts
                "ae", "ä" -> listOf(
                    b(700.0, 0.0, 80.0),
                    b(1800.0, -10.0, 90.0),
                    b(2800.0, -20.0, 120.0),
                    b(3900.0, -25.0, 130.0),
                    b(4950.0, -52.0, 140.0)
                )

                "oe", "ö" -> listOf(
                    b(450.0, 0.0, 80.0),
                    b(1500.0, -22.0, 90.0),
                    b(2500.0, -18.0, 120.0),
                    b(3600.0, -42.0, 130.0),
                    b(4950.0, -58.0, 140.0)
                )

                "ue", "ü" -> listOf(
                    b(350.0, 0.0, 80.0),
                    b(1700.0, -20.0, 90.0),
                    b(2500.0, -28.0, 120.0),
                    b(4000.0, -30.0, 130.0),
                    b(4950.0, -46.0, 140.0)
                )
                // German Diphthongs (nucleus)
                "eu", "äu" -> listOf(
                    b(600.0, 0.0, 80.0),
                    b(950.0, -10.0, 90.0),
                    b(2850.0, -15.0, 120.0),
                    b(3850.0, -20.0, 130.0),
                    b(4950.0, -30.0, 140.0)
                )

                else -> null
            }

            else -> null
        }
    }
}

/**
 * Shared all-null template, cloned by [createSprudelVoiceData]. `@PublishedApi internal` so the inline
 * factory can reference it across the module. Never mutate it directly — it is only ever `clone()`d.
 * This full-field constructor call (plus `merge()`/`mergeFrom`) is the compile-time guard that no field
 * is forgotten, since the primary constructor has no defaults.
 */
@PublishedApi
internal val blueprint = SprudelVoiceData(
    note = null,
    freqHz = null,
    scale = null,
    chord = null,
    gain = null,
    legato = null,
    velocity = null,
    postGain = null,
    bank = null,
    sound = null,
    soundIndex = null,
    oscParams = null,
    adsr = null,
    pitchMod = null,
    pitchEnv = null,
    fm = null,
    distortion = null,
    phaser = null,
    tremolo = null,
    duck = null,
    lpf = null,
    hpf = null,
    bpf = null,
    notch = null,
    cylinder = null,
    pan = null,
    delayFx = null,
    reverb = null,
    sample = null,
    vowel = null,
    vowelMix = null,
    body = null,
    bodyMix = null,
    compressor = null,
    solo = null,
    patternId = null,
    pipeline = null,
    value = null,
)

/**
 * Factory for a fresh [SprudelVoiceData], configured via [config].
 *
 * Clones the all-null [blueprint] and applies [config] to set the fields you want — avoids threading
 * 100+ constructor params through every call site. `inline`, so `config` is inlined (no lambda
 * allocation): `createSprudelVoiceData { gain = 0.5 }` compiles to `blueprint.clone().also { it.gain = 0.5 }`.
 *
 * Note: inside [config] the receiver is the new instance, so a bare name on the right-hand side resolves
 * to the instance's property, not an outer local — write `also { it.field = localValue }` when the local
 * shares a field's name (e.g. `value`).
 */
inline fun createSprudelVoiceData(config: SprudelVoiceData.() -> Unit = {}): SprudelVoiceData =
    blueprint.clone().apply(config)

/** Merges two oscParams maps: other's values override this's values. */
private fun mergeOscParams(
    base: Map<String, Double>?,
    other: Map<String, Double>?,
): Map<String, Double>? = when {
    base == null -> other
    other == null -> base
    else -> base + other
}

/** Returns a copy with the given oscParam set. If value is null, returns this unchanged. */
fun SprudelVoiceData.withOscParam(key: String, value: Double?): SprudelVoiceData {
    if (value == null) return this
    return copy(oscParams = (oscParams.orEmpty()) + (key to value))
}

/**
 * In-place counterpart of [withOscParam]: sets the given oscParam on this instance (null is a no-op).
 * `oscParams` is treated as immutable-replace, so a fresh map is assigned to the field — no new
 * [SprudelVoiceData] is allocated. Only safe on a single-owner instance (see [clone]).
 */
fun SprudelVoiceData.putOscParam(key: String, value: Double?) {
    if (value == null) return
    oscParams = (oscParams.orEmpty()) + (key to value)
}

/**
 * In-place counterpart of [withOscParams]: sets the given oscParams on this instance. Null values
 * are ignored. Only safe on a single-owner instance (see [clone]).
 */
fun SprudelVoiceData.putOscParams(vararg params: Pair<String, Double?>) {
    val nonNull = params.filter { it.second != null }
    if (nonNull.isEmpty()) return
    val merged = oscParams.orEmpty().toMutableMap()
    for ((k, v) in nonNull) merged[k] = v!!
    oscParams = merged
}

/** Merges multiple oscParams in a single copy. Null values are ignored. */
fun SprudelVoiceData.withOscParams(vararg params: Pair<String, Double?>): SprudelVoiceData {
    val nonNull = params.filter { it.second != null }
    if (nonNull.isEmpty()) return this
    val merged = oscParams.orEmpty().toMutableMap()
    for ((k, v) in nonNull) merged[k] = v!!
    return copy(oscParams = merged)
}

/** Merges all oscParams from another voice data in a single copy. */
fun SprudelVoiceData.mergeOscParamsFrom(other: SprudelVoiceData): SprudelVoiceData {
    val otherParams = other.oscParams
    if (otherParams.isNullOrEmpty()) return this
    return copy(oscParams = (oscParams.orEmpty()) + otherParams)
}

/**
 * In-place counterpart of [mergeOscParamsFrom]: folds [other]'s oscParams into this instance.
 * Only safe on a single-owner instance (see [clone]).
 */
fun SprudelVoiceData.putOscParamsFrom(other: SprudelVoiceData) {
    val otherParams = other.oscParams
    if (otherParams.isNullOrEmpty()) return
    oscParams = (oscParams.orEmpty()) + otherParams
}

/**
 * Convenience accessor that extracts the [SoundValue.Named.name] from [SprudelVoiceData.sound],
 * or null if sound is null or a [SoundValue.Osc].
 */
val SprudelVoiceData.soundName: String? get() = (sound as? SoundValue.Named)?.name
