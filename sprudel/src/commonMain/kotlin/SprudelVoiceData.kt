/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel

import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.BodyMaterials
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.FilterDefs
import io.peekandpoke.klang.audio_bridge.FilterEnvDef
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystValue
import io.peekandpoke.klang.audio_bridge.MasterDsl
import io.peekandpoke.klang.audio_bridge.MasterValue
import io.peekandpoke.klang.audio_bridge.PipelineDsl
import io.peekandpoke.klang.audio_bridge.PipelineValue
import io.peekandpoke.klang.audio_bridge.SoundValue
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.VowelBands
import io.peekandpoke.klang.audio_bridge.coercePasses
import io.peekandpoke.klang.audio_bridge.constants.BODY_WET
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_WET
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
    /**
     * Articulation shorthand: the accents inside a line, multiplied into [gain] at the wire
     * ([toVoiceData]). A sprudel word only, it never crosses to the backend, which knows one
     * level word (signal-flow plan section 6).
     */
    var velocity: Double?,

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

    /**
     * Oscillator parameters (generic slots: "density", "voices", "spread", "panSpread", "onepole" [Hz]).
     *
     * **Mutable and single-owner, like the `Svd*` groups**, not immutable-replace: a door writes one
     * name in place ([putOscParam]) instead of allocating a fresh bag per slot, which is what keeps a
     * pattern that fills a dozen slots out of the "twenty allocations per note" class the June work
     * removed. [clone] deep-copies it, which is the one allocation per event the design allows, and
     * [toVoiceData] hands the wire a COPY (see there). The contract lives on [ParamBag].
     */
    var oscParams: ParamBag?,

    /**
     * The ORBIT's bus slots this event writes, named `<stage>.<knob>` (`"reverb.size"`,
     * `"compressor.ratio"`, `"duck.orbit"`). Same shape and same rules as [oscParams], the other
     * host: that bag is the voice's own instrument, this one the chain its orbit runs. Written by
     * `.katp(name, value)` and, until the voice fields leave the wire, by the bus doors as aliases.
     *
     * Mutable and single-owner, and merged the same way as [oscParams], last writer wins per name.
     * Carried to `VoiceData.katalystParams` by [toVoiceData].
     */
    var katalystParams: ParamBag?,

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

    // Reverb — grouped (see SvdReverb). Property is `reverbFx` (the flat `reverb` send amount is an accessor below).
    var reverbFx: SvdReverb?,

    // Sample manipulation — grouped (see SvdSample).
    var sample: SvdSample?,

    // Voice / Singing — grouped (see SvdVowel). Flat fields (vowel/vowelMix/vowelFloor) are accessors below.
    var vowelFx: SvdVowel?,

    // Body resonator — grouped (see SvdBody). Flat fields (body/bodyMix/bodyFloor) are accessors below.
    var bodyFx: SvdBody?,

    // Dynamics / Compression (per-param since C0.2)
    /** Compressor threshold in dB (e.g. -20). */
    var compressorThreshold: Double?,
    /** Compression ratio (e.g. 4 = 4:1 above threshold). */
    var compressorRatio: Double?,
    /** Knee smoothness in dB (0 = hard knee). */
    var compressorKnee: Double?,
    /** Attack time in seconds. */
    var compressorAttack: Double?,
    /** Release time in seconds. */
    var compressorRelease: Double?,

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

    /**
     * The master chain this event switches the playback's bus to, from its start time onward.
     * Either a [MasterValue.Named] (a pre-registered custom) or a [MasterValue.Dsl] inlining a
     * [MasterDsl] — the latter is denormalized to a synthetic name in [toVoiceData]. Null = no change.
     */
    var master: MasterValue?,

    /**
     * The orbit chain this event switches its orbit to, from its start time onward. Either a
     * [KatalystValue.Named] (a pre-registered custom) or a [KatalystValue.Dsl] inlining a
     * [KatalystDsl]; the latter is denormalized to a synthetic name in [toVoiceData]. Null = no
     * change.
     *
     * Like [master], the `.katalyst(...)` door REPLACES whatever chain the pattern already carries
     * (decided 2026-09-18): a chain is one instrument, and `k.classic()` inside the builder is how
     * you start from the familiar one. The field merges last-writer-wins like every other one.
     */
    var katalyst: KatalystValue?,

    /**
     * Control-only event: carries engine-level data (a [master] or [katalyst] swap) and is never
     * synthesized. Set by the top-level `master(...)` / `katalyst(...)` carriers; a
     * `note("c3").master(...)` leaves it null so the note still sounds.
     */
    var control: Boolean?,

    // Custom value
    var value: SprudelVoiceValue?,

    /**
     * Semantic tags accumulated via `.tag(...)`. A set by design: tags are unique and carry NO
     * ordering guarantee — consumers must never rely on accumulation order. Copied into engine
     * `VoiceData` by [toVoiceData] (and thus over the wire) for UI subscribers (visualizations)
     * and analysis tools; the synthesis engine ignores them. Treated as immutable-replace, UNLIKE
     * the param maps: a shared reference in [clone] and a fresh set on every write, because a tag
     * is added once per pattern node and never a dozen times per event.
     */
    var tags: Set<String>?,

    /**
     * Tweak names attached in mini-notation (`e3{swell}`) or via `.tweak(...)`, referencing
     * transforms that `tweaks(...)` binds later. A LIST by design, unlike [tags]: tweaks apply in
     * the order written and may repeat, so neither ordering nor duplicates may be dropped.
     *
     * Deliberately NOT copied into engine `VoiceData` by [toVoiceData]: a tweak is a pattern-layer
     * concern that never reaches synthesis, so it would be dead weight on the wire.
     *
     * Treated as immutable-replace like [tags], UNLIKE the param maps (shared reference in
     * [clone], fresh list on write).
     */
    var tweaks: List<String>?,

    /**
     * Silence-culling window in seconds (`cull(seconds)`); `null` = engine default, negative
     * (`noCull()`) = never. Mirrors `VoiceData.cull`, see there for the semantics.
     */
    var cull: Double?,
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
    private fun reverbFxOrNew(): SvdReverb = reverbFx ?: SvdReverb().also { reverbFx = it }
    private fun sampleOrNew(): SvdSample = sample ?: SvdSample().also { sample = it }
    private fun bodyFxOrNew(): SvdBody = bodyFx ?: SvdBody().also { bodyFx = it }
    private fun vowelFxOrNew(): SvdVowel = vowelFx ?: SvdVowel().also { vowelFx = it }

    // Body resonator — flat accessors over the grouped SvdBody storage.
    var body: String?
        get() = bodyFx?.material
        set(v) {
            if (v != null || bodyFx != null) bodyFxOrNew().material = v
        }
    var bodyMix: Double?
        get() = bodyFx?.mix
        set(v) {
            if (v != null || bodyFx != null) bodyFxOrNew().mix = v
        }
    var bodyFloor: Double?
        get() = bodyFx?.floor
        set(v) {
            if (v != null || bodyFx != null) bodyFxOrNew().floor = v
        }

    // Vowel / formant — flat accessors over the grouped SvdVowel storage.
    var vowel: String?
        get() = vowelFx?.vowel
        set(v) {
            if (v != null || vowelFx != null) vowelFxOrNew().vowel = v
        }
    var vowelMix: Double?
        get() = vowelFx?.mix
        set(v) {
            if (v != null || vowelFx != null) vowelFxOrNew().mix = v
        }
    var vowelFloor: Double?
        get() = vowelFx?.floor
        set(v) {
            if (v != null || vowelFx != null) vowelFxOrNew().floor = v
        }

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
    var adsrOn: Boolean?
        get() = adsr?.on
        set(v) {
            if (v != null || adsr != null) adsrOrNew().on = v
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
    var lpPasses: Double?
        get() = lpf?.passes
        set(v) {
            if (v != null || lpf != null) lpfOrNew().passes = v
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
    var hpPasses: Double?
        get() = hpf?.passes
        set(v) {
            if (v != null || hpf != null) hpfOrNew().passes = v
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

    var phaserFloor: Double?
        get() = phaser?.phaserFloor
        set(v) {
            if (v != null || phaser != null) phaserOrNew().phaserFloor = v
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
    var delayCap: Double?
        get() = delayFx?.delayCap
        set(v) {
            if (v != null || delayFx != null) delayFxOrNew().delayCap = v
        }

    var reverb: Double?
        get() = reverbFx?.reverb
        set(v) {
            if (v != null || reverbFx != null) reverbFxOrNew().reverb = v
        }
    var reverbSize: Double?
        get() = reverbFx?.reverbSize
        set(v) {
            if (v != null || reverbFx != null) reverbFxOrNew().reverbSize = v
        }
    var reverbLowpass: Double?
        get() = reverbFx?.reverbLowpass
        set(v) {
            if (v != null || reverbFx != null) reverbFxOrNew().reverbLowpass = v
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
     * leaf emitters `AtomicPattern`/`AtomicInfinitePattern`). `oscParams` and `katalystParams` are
     * mutable [ParamBag]s and are copied here for the same reason the groups are: the clone owns them, so
     * a door can write a name in place instead of allocating a bag per slot. As more clusters become groups,
     * add them to the deep-copy list here.
     */
    fun clone(): SprudelVoiceData = copy(
        oscParams = oscParams?.copy(),
        katalystParams = katalystParams?.copy(),
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
        reverbFx = reverbFx?.copy(),
        sample = sample?.copy(),
        bodyFx = bodyFx?.copy(),
        vowelFx = vowelFx?.copy(),
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
            bank = other.bank ?: bank,
            sound = other.sound ?: sound,
            soundIndex = other.soundIndex ?: soundIndex,
            oscParams = mergeParamBag(oscParams, other.oscParams),
            katalystParams = mergeParamBag(katalystParams, other.katalystParams),
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
            reverbFx = mergeSvdReverb(reverbFx, other.reverbFx),
            sample = mergeSvdSample(sample, other.sample),
            vowelFx = mergeSvdVowel(vowelFx, other.vowelFx),
            bodyFx = mergeSvdBody(bodyFx, other.bodyFx),
            compressorThreshold = other.compressorThreshold ?: compressorThreshold,
            compressorRatio = other.compressorRatio ?: compressorRatio,
            compressorKnee = other.compressorKnee ?: compressorKnee,
            compressorAttack = other.compressorAttack ?: compressorAttack,
            compressorRelease = other.compressorRelease ?: compressorRelease,
            solo = other.solo ?: solo,
            patternId = patternId,  // Never merge - preserve original source ID
            pipeline = other.pipeline ?: pipeline,
            master = other.master ?: master,
            katalyst = other.katalyst ?: katalyst,
            // control is NOT merged (like patternId): it says "this event makes no sound", which is
            // a property of the carrier itself. Taking it from `other` would let a merged-in master
            // carrier silence real notes.
            control = control,
            value = other.value ?: value,
            tags = mergeTags(tags, other.tags),
            tweaks = mergeTweaks(tweaks, other.tweaks),
            cull = other.cull ?: cull,
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
        bank = other.bank ?: bank
        sound = other.sound ?: sound
        soundIndex = other.soundIndex ?: soundIndex
        oscParams = mergeParamBagInto(oscParams, other.oscParams)
        katalystParams = mergeParamBagInto(katalystParams, other.katalystParams)
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
        reverbFx = mergeSvdReverb(reverbFx, other.reverbFx)
        sample = mergeSvdSample(sample, other.sample)
        vowelFx = mergeSvdVowel(vowelFx, other.vowelFx)
        bodyFx = mergeSvdBody(bodyFx, other.bodyFx)
        compressorThreshold = other.compressorThreshold ?: compressorThreshold
        compressorRatio = other.compressorRatio ?: compressorRatio
        compressorKnee = other.compressorKnee ?: compressorKnee
        compressorAttack = other.compressorAttack ?: compressorAttack
        compressorRelease = other.compressorRelease ?: compressorRelease
        solo = other.solo ?: solo
        // patternId intentionally preserved (never taken from other) — matches merge()
        pipeline = other.pipeline ?: pipeline
        master = other.master ?: master
        katalyst = other.katalyst ?: katalyst
        // control intentionally NOT merged — see merge()
        value = other.value ?: value
        tags = mergeTags(tags, other.tags)
        tweaks = mergeTweaks(tweaks, other.tweaks)
        cull = other.cull ?: cull
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
     * The wire's one level word: [gain] with [velocity] multiplied into it.
     *
     * `velocity` is sprudel's articulation shorthand and stops here (signal-flow plan section 6).
     * Both unset stays unset, so the wire stays sparse and the engine's own `?: 1.0` answers; any
     * other combination is `(gain ?: 1.0) * (velocity ?: 1.0)`, the operands and the order the
     * backend used to compute in the voice factory, so an unaccented voice keeps its bits.
     *
     * A non-finite value reads as UNSET, PER OPERAND and before the product (`/dsl-design` §4).
     * Substituting after the product instead would turn `gain(0.5).velocity(NaN)` into a NaN the
     * engine reads as 1.0, i.e. FULL level where the author asked for half, louder than anything
     * they wrote. Per operand, that call is `0.5`. Two operands that both read as unset give
     * `null`, the same answer as writing neither, so the wire stays sparse. A product that
     * OVERFLOWS to infinity is left alone here and caught by the engine's own guard.
     *
     * Folded HERE and not at the `velocity()` door: `velocity(p)` read as `gain(mul(p))` does
     * nothing on an event whose gain is unset (a mapper on an unset field is a no-op, by design)
     * and would make `.velocity(0.7).gain(0.5)` order-dependent.
     */
    private fun foldedGain(): Double? {
        // NaN-guard: per operand, before the product (see the KDoc for why the order matters)
        val g = gain?.takeIf { it.isFinite() }
        val v = velocity?.takeIf { it.isFinite() }

        if (g == null && v == null) {
            return null
        }

        return (g ?: 1.0) * (v ?: 1.0)
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

        // Same denormalization for the master chain reference.
        val masterName: String? = when (val m = master) {
            null -> null
            is MasterValue.Named -> m.name
            is MasterValue.Dsl -> m.master.uniqueId()
        }

        // ...and for the orbit chain reference.
        // `k.name` is the memoized `uniqueId()` of the chain: one structural hash per value
        // instance rather than one per event (see KatalystValue.Dsl.name).
        val katalystName: String? = when (val k = katalyst) {
            null -> null
            is KatalystValue.Named -> k.name
            is KatalystValue.Dsl -> k.name
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
                        freq = cutoffValue,
                        q = resonance ?: 0.707,
                        envelope = envelope,
                        passes = coercePasses(lpPasses ?: 1.0),
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
                        freq = hcutoffValue,
                        q = hresonance ?: 0.707,
                        envelope = envelope,
                        passes = coercePasses(hpPasses ?: 1.0),
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
                        freq = bandfValue,
                        q = bandq ?: 0.707,
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
                        freq = notchfValue,
                        q = nresonance ?: 0.707,
                        envelope = envelope
                    )
                )
            }

            // Vowel formant filter — blended over the dry source (source-filter model), like body.
            vowel?.let { vowelValue ->
                val formantBands = VowelBands.bandsFor(vowelValue)

                formantBands?.let { bands ->
                    add(FilterDef.Formant(bands = bands, mix = vowelMix ?: VOWEL_WET, floor = vowelFloor))
                }
            }

            // Body resonator — fixed modal resonances blended over the dry source.
            body?.let { material ->
                BodyMaterials.modesFor(material)?.let { modes ->
                    // Default body amount when the user didn't set bodyMix — a moderate, audible
                    // amount (0..1; the blend keeps a broadband floor, so it never thins).
                    // floor = null → engine default (BODY_FLOOR); bodyFloor() overrides it.
                    add(FilterDef.Body(bands = modes, mix = bodyMix ?: BODY_WET, floor = bodyFloor))
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
            gain = foldedGain(),
            legato = legato,
            bank = bank,
            sound = soundName,
            soundIndex = soundIndex,
            // A COPY, not the event's own bag: the wire value outlives the pattern event. The
            // backend holds `Voice.katalystParams` for the whole life of the voice and its chain
            // gates the re-resolve on the map's IDENTITY, so a map sprudel could still write into
            // would change an orbit's settings invisibly. `oscParams` follows the same rule, one
            // contract for both (`ParamBag.toMap`). The boundary already allocates a `VoiceData`,
            // and one copy here replaces the one-per-slot copies the doors used to make.
            oscParams = oscParams?.toMap(),
            katalystParams = katalystParams?.toMap(),
            filters = FilterDefs(orderedFilters),
            adsr = AdsrDef.Std(
                attack = attack,
                decay = decay,
                sustain = sustain,
                release = release,
                attackCurve = attackCurve,
                decayCurve = decayCurve,
                releaseCurve = releaseCurve,
                on = adsrOn,
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
            phaserFloor = phaserFloor,
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
            delayCap = delayCap,
            reverb = reverb,
            reverbSize = reverbSize,
            reverbLowpass = reverbLowpass,
            begin = begin,
            end = end,
            speed = speed,
            loop = loop,
            cut = cut,
            loopBegin = loopBegin,
            loopEnd = loopEnd,
            compressorThreshold = compressorThreshold,
            compressorRatio = compressorRatio,
            compressorKnee = compressorKnee,
            compressorAttack = compressorAttack,
            compressorRelease = compressorRelease,
            solo = solo,
            sourceId = patternId,
            pipeline = pipelineName,
            master = masterName,
            katalyst = katalystName,
            control = control,
            tags = tags,
            cull = cull,
        )
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
    bank = null,
    sound = null,
    soundIndex = null,
    oscParams = null,
    katalystParams = null,
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
    reverbFx = null,
    sample = null,
    vowelFx = null,
    bodyFx = null,
    compressorThreshold = null,
    compressorRatio = null,
    compressorKnee = null,
    compressorAttack = null,
    compressorRelease = null,
    solo = null,
    patternId = null,
    pipeline = null,
    master = null,
    katalyst = null,
    control = null,
    value = null,
    tags = null,
    tweaks = null,
    cull = null,
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

/**
 * Merges two tag sets (union). Null when both are null — a merge must not materialize an empty
 * set on untagged data.
 */
private fun mergeTags(
    base: Set<String>?,
    other: Set<String>?,
): Set<String>? = when {
    base == null -> other
    other == null -> base
    else -> base + other
}

/**
 * In-place tag add: no-op if [tag] is already present, else assigns a fresh set with [tag] added.
 * `tags` is treated as immutable-replace, unlike the param maps, so a fresh set is assigned to the
 * field and no new [SprudelVoiceData] is allocated. Only safe on a single-owner instance (see [clone]).
 */
fun SprudelVoiceData.addTag(tag: String) {
    val current = tags
    if (current != null && tag in current) return
    tags = current.orEmpty() + tag
}

/** Copy counterpart of [addTag]: returns this unchanged when [tag] is already present. */
fun SprudelVoiceData.withTag(tag: String): SprudelVoiceData {
    val current = tags
    if (current != null && tag in current) return this
    return copy(tags = current.orEmpty() + tag)
}

/**
 * Concatenates two tweak lists: [base] first, then [other]. Null when both are null — a merge must
 * not materialize an empty list on untweaked data.
 *
 * Concatenation, not union: order is significant and repeats are meaningful. [other] is the later
 * (outer) modifier in every [SprudelVoiceData.merge] direction, so inner tweaks stay in front.
 */
private fun mergeTweaks(
    base: List<String>?,
    other: List<String>?,
): List<String>? = when {
    base == null -> other
    other == null -> base
    else -> base + other
}

/**
 * In-place tweak append. Unlike [addTag] this is NOT idempotent: a repeated tweak applies twice,
 * which is the whole point of `tweaks` being a list. `tweaks` is treated as immutable-replace,
 * unlike the param maps, so a fresh list is assigned to the field and no new [SprudelVoiceData] is allocated. Only safe on a single-owner instance
 * (see [SprudelVoiceData.clone]).
 */
fun SprudelVoiceData.addTweak(tweak: String) {
    tweaks = tweaks.orEmpty() + tweak
}

/** Copy counterpart of [addTweak]. Always allocates: appending is never a no-op. */
fun SprudelVoiceData.withTweak(tweak: String): SprudelVoiceData =
    copy(tweaks = tweaks.orEmpty() + tweak)

/** Appends several tweaks in one copy, preserving [names]' order. Returns this when [names] is empty. */
fun SprudelVoiceData.withTweaks(names: List<String>): SprudelVoiceData = when {
    names.isEmpty() -> this
    else -> copy(tweaks = tweaks.orEmpty() + names)
}

/**
 * Merges two param bags (`oscParams`, `katalystParams`) into a FRESH one: other's values override
 * this's values, name by name. One function for both, because the two bags differ in their HOST,
 * not in their shape.
 *
 * Always a new bag, never an alias, exactly as `mergeSvdAdsr` and its siblings always return a new
 * group: [SprudelVoiceData.merge] builds a new instance, and handing it either operand's mutable
 * bag would make two owners of one bag. The in-place twin is [mergeParamBagInto].
 */
private fun mergeParamBag(base: ParamBag?, other: ParamBag?): ParamBag? = when {
    base == null -> other?.copy()
    other == null -> base.copy()
    else -> base.copy().also { it.mergeFrom(other) }
}

/**
 * In-place counterpart of [mergeParamBag], for [SprudelVoiceData.mergeFrom]: [other]'s entries go
 * into the receiver's own bag, which is the whole point of the bag being mutable. Only when the
 * receiver has none is a bag allocated, and then it is a copy: [other] belongs to another voice.
 */
private fun mergeParamBagInto(target: ParamBag?, other: ParamBag?): ParamBag? {
    if (other == null) {
        return target
    }

    if (target == null) {
        return other.copy()
    }

    target.mergeFrom(other)

    return target
}

/**
 * The voice's own [ParamBag], allocated on first use.
 *
 * The one door to the bag for a writer that is going to write: a stage fill reaches for it once and
 * then calls [ParamBag.set] / [ParamBag.setOrDefault] per slot, so a door that fills five knobs
 * allocates at most one bag. Only safe on a single-owner instance (see [SprudelVoiceData.clone]).
 */
fun SprudelVoiceData.oscParamsOrNew(): ParamBag = oscParams ?: ParamBag().also { oscParams = it }

/** The orbit chain's [ParamBag], the [oscParamsOrNew] twin on the other host. */
fun SprudelVoiceData.katalystParamsOrNew(): ParamBag = katalystParams ?: ParamBag().also { katalystParams = it }

/**
 * Writes ONE oscParam on this instance, in place, and nothing at all when [value] is null: a door
 * that wrote nothing on this event must not leave an empty bag behind, because an empty bag is not
 * the same wire value as none at all.
 *
 * The copying variants (`withOscParam`, `withOscParams`, `mergeOscParamsFrom`) went with the
 * immutable-replace storage on 2026-09-18: nothing called them, and a copy helper over a mutable
 * bag is a second way to own one.
 */
fun SprudelVoiceData.putOscParam(name: String, value: Double?) {
    if (value == null) return

    oscParamsOrNew().set(name, value)
}

/**
 * In-place write of ONE orbit-chain slot, the [putOscParam] twin on the other host (null is a
 * no-op for the same reason).
 *
 * ONE name into the bag this instance already owns. A door that fills a whole STAGE takes
 * [katalystParamsOrNew] instead and writes the slots on the bag, through the fill rule's one
 * method ([ParamBag.setOrDefault]). Only safe on a single-owner instance (see
 * [SprudelVoiceData.clone]).
 */
fun SprudelVoiceData.putKatalystParam(name: String, value: Double?) {
    if (value == null) return

    katalystParamsOrNew().set(name, value)
}

/**
 * Convenience accessor that extracts the [SoundValue.Named.name] from [SprudelVoiceData.sound],
 * or null if sound is null or a [SoundValue.Osc].
 */
val SprudelVoiceData.soundName: String? get() = (sound as? SoundValue.Named)?.name
