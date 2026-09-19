/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge


/**
 * Defines a voice
 */
data class VoiceData(
    // note, scale, freq
    // TODO: note can also be numbers -> Midi and detune, f.e. 50.3
    val note: String?,
    val freqHz: Double?,
    val scale: String?,

    // Gain / Dynamics
    /**
     * The channel fader: the tone-neutral level at which the voice leaves, applied with [pan] in
     * the send stage. `null` is unset and reads as 1.0.
     *
     * The ONE level word on the wire. A frontend's articulation shorthand (sprudel's `velocity`,
     * a MIDI key velocity) is multiplied into it before it crosses, so the backend never learns
     * that word (signal-flow plan section 6).
     */
    val gain: Double?,
    val legato: Double?,

    // Sound, bank, sound index
    /** Sample bank (e.g. "MPC60" or "AkaiMPC60"), optional.*/
    val bank: String?,
    /** Parsed from osc if it looks like "bd:2". sound="bd", soundIndex=2 */
    val sound: String?,
    /** Sound index */
    val soundIndex: Int?,

    // Oscillator parameters (generic map: "density", "voices", "spread", "panSpread", "onepole" [Hz])
    val oscParams: Map<String, Double>?,

    /**
     * The orbit bus slots this voice writes, keyed `<stage>.<knob>` exactly as [KatalystDsl.classic]
     * names them (`"reverb.size"`, `"compressor.ratio"`, `"duck.orbit"`). The [oscParams] shape, the
     * other host: `oscParams` is the voice's own instrument, this is the orbit's chain.
     *
     * **Applied by the orbit's OWNER voice**, the one holding the cylinder's lease: a declared
     * chain's [IgnitorDsl.Param] knobs resolve to `katalystParams[name]` and fall back to the
     * slot's authored default when the map does not carry it. The chain RE-READS the map when its
     * instance changes, not every block; per block it only re-writes the numbers it already
     * resolved. So this is orbit state with the owner's lifetime, not a per-note snapshot: last
     * writer within the owner wins, and the values die with the voice that carried them. A knob
     * written as a constant in the chain is not a slot and is never overridden.
     *
     * Written by `.katp(name, value)` and by the bus doors as aliases (`reverb(...)`, `delay(...)`,
     * `compressor(...)`, `duck(...)`, `phaser(...)`, `body(...)`, `vowel(...)`), which is what
     * makes a door and its slot the same knob.
     *
     * Which chain reads which slot of this map is ONE rule with ONE home, the `katp` door's KDoc
     * in `sprudel/lang/lang_katalyst.kt`. In short: EVERY chain reads it, for every stage it
     * declares, the chain a cylinder is born with included (Katalyst step 5b-1). The bus FIELDS
     * below are not a knob source any more; they carry the per-voice send AMOUNTS until step 5b-2
     * and leave the wire in 5b-3.
     */
    val katalystParams: Map<String, Double>? = null,

    // Filters
    val filters: FilterDefs = FilterDefs.empty,

    // ADSR
    val adsr: AdsrDef,

    // Pitch / Glisando
    /**
     * Pitch glide over the event's duration, in SEMITONES (P unit unification, 2026-08-24:
     * converted from octaves — a second wire producer must send semitones; 12 = one octave,
     * engine law `ratio = 2^((semitones/12)·progress)`).
     */
    val accelerate: Double?,

    // Vibrato
    val vibrato: Double?,
    val vibratoMod: Double?,

    // Pitch envelope
    val pAttack: Double?,
    val pDecay: Double?,
    val pRelease: Double?,
    val pEnv: Double?,
    val pCurve: Double?,
    val pAnchor: Double?,

    // FM Synthesis
    val fmh: Double?,
    val fmAttack: Double?,
    val fmDecay: Double?,
    val fmSustain: Double?,
    val fmEnv: Double?,

    // Effects
    val distort: Double?,
    /** Distortion shape: soft, hard, gentle, softsat, cubic, exp, sineshaper, zerosquare, chebyshev, fold, linearfold, diode, tube, asym, stompbox, rectify */
    val distortShape: String?,
    /** Distortion oversampling factor (2=2x, 4=4x, 8=8x; non-power-of-2 floored; <=1 = off) */
    val distortOversample: Int? = null,
    val coarse: Double?,
    /** Coarse (sample-rate reducer) oversampling factor (2=2x, 4=4x, 8=8x; non-power-of-2 floored; <=1 = off) */
    val coarseOversample: Int? = null,
    val crush: Double?,
    /** Crush (bit-depth reducer) oversampling factor (2=2x, 4=4x, 8=8x; non-power-of-2 floored; <=1 = off) */
    val crushOversample: Int? = null,

    // Phaser
    val phaser: Double?,
    val phaserDepth: Double?,
    val phaserCenter: Double?,
    val phaserSweep: Double?,
    /** Minimum dry coefficient of the phaser wet/dry law; null = engine default 1.0 (purely additive). */
    val phaserFloor: Double? = null,

    // Tremolo
    val tremoloSync: Double?,
    val tremoloDepth: Double?,
    val tremoloSkew: Double?,
    val tremoloPhase: Double?,
    val tremoloShape: String?,

    // Ducking / Sidechain
    val duckCylinder: Int?,
    val duckAttack: Double?,
    val duckDepth: Double?,

    // HPF / LPF
    /** Low pass filter cutoff frequency */
    val cutoff: Double?,
    /** High pass filter cutoff frequency */
    val hcutoff: Double?,
    /** Band pass filter cutoff frequency */
    val bandf: Double?,
    /** Resonance amount for filters */
    val resonance: Double?,

    // Routing
    val cylinder: Int?,

    // Panning (-1.0 = Left, 0.0 = Center, 1.0 = Right)
    val pan: Double?,

    // Delay
    val delay: Double?, // Mix amount (0.0 to 1.0)
    val delayTime: Double?, // Time in seconds
    val delayFeedback: Double?, // Feedback amount; >= 1.0 self-oscillates, bounded by delayCap
    /** Ceiling the delay feedback saturates toward (default 1.0). Sprudel `delay(cap = ...)`. */
    val delayCap: Double? = null,

    // Reverb
    val reverb: Double?, // Send amount (0.0 to 1.0)
    val reverbSize: Double?, // Tail length, authored ~0..10 scale (normalized in VoiceFactory)
    val reverbLowpass: Double?, // Tail damping cutoff in Hz

    // Sample manipulation
    val begin: Double?,
    val end: Double?,
    val speed: Double?,
    val loop: Boolean?,
    val cut: Int?,
    val loopBegin: Double?,
    val loopEnd: Double?,

    // Dynamics / Compression (per-param since C0.2; audio_be applies defaults for missing values)
    val compressorThreshold: Double?,
    val compressorRatio: Double?,
    val compressorKnee: Double?,
    val compressorAttack: Double?,
    val compressorRelease: Double?,

    // Solo
    /** Solo amount: 1.0 = full solo (mute others), 0.0 = no solo. */
    val solo: Double?,

    /** Unique source ID for tracking which audio source this voice came from (e.g., pattern, track, instrument) */
    val sourceId: String?,

    /**
     * Voice pipeline name — selects the topology of the Filter stage.
     *
     * Known values (case-insensitive): `"modern"` (default, ADSR last — classic subtractive VCF→VCA),
     * `"pedal"` (ADSR first — guitar-pedal feel, waveshapers respond to dynamics).
     * Unknown or null values fall back to modern.
     */
    val pipeline: String? = null,

    /**
     * Master-chain name — selects the [MasterDsl] applied to this playback's bus from this event's
     * start time onward (last writer wins per playback).
     *
     * Resolved from the authoring-layer `MasterValue` at the wire boundary: an inline chain
     * denormalizes to its `MasterDsl.uniqueId()`, a named reference passes through. Null means
     * "no change" — the playback keeps whatever master it already had.
     *
     * A master reference rides *any* event, so `note("c3").master(…)` swaps the master at that
     * note's onset and still sounds the note. An event that carries *only* a master is marked
     * [control].
     */
    val master: String? = null,

    /**
     * Orbit-chain name: selects the `KatalystDsl` the voice's orbit runs, from this event's start
     * time onward (last writer wins per orbit).
     *
     * Resolved from the authoring-layer `KatalystValue` at the wire boundary: an inline chain
     * denormalizes to its `KatalystDsl.uniqueId()`, a named reference passes through. Null means
     * "no change": the orbit keeps whatever chain it already had.
     *
     * A Katalyst reference rides *any* event, so `note("c3").katalyst(…)` swaps the orbit's chain
     * at that note's onset and still sounds the note. An event that carries *only* a Katalyst is
     * marked [control].
     *
     * The backend registers the chain but does not read it yet (Katalyst step 1, 2026-09-17); the
     * cylinder starts running declared chains in step 2.
     */
    val katalyst: String? = null,

    /**
     * Control-only event: carries engine/bus configuration (e.g. [master]) and is **never
     * synthesized**.
     *
     * The scheduler consumes such an event at its start time and drops it before voice creation.
     * The flag has to be explicit: a voice with `sound == null` is *not* silent — the ignitor
     * registry resolves a null sound to the default oscillator.
     */
    val control: Boolean? = null,

    /**
     * Semantic tags accumulated via the pattern language's `.tag(...)`. A set: tags are unique and
     * carry NO ordering guarantee. Consumed by UI subscribers (visualizations) and analysis tools;
     * the synthesis engine ignores them.
     */
    val tags: Set<String>? = null,

    /**
     * Silence-culling window in seconds (the `cull(seconds)` door). Once the voice is in its
     * release and its output has stayed under the audibility floor for this long, it ends itself
     * instead of rendering the rest of its scheduled tail. `null` = the engine default
     * ([io.peekandpoke.klang.audio_bridge.constants.VOICE_CULL_SECONDS]); a negative value
     * (`noCull()`, [io.peekandpoke.klang.audio_bridge.constants.VOICE_CULL_NEVER]) never culls.
     * The gate (the held part of the note) is never culled, whatever the value.
     */
    val cull: Double? = null,
) {
    companion object {
        val empty = VoiceData(
            note = null,
            freqHz = null,
            scale = null,
            gain = null,
            legato = null,
            bank = null,
            sound = null,
            soundIndex = null,
            oscParams = null,
            katalystParams = null,
            filters = FilterDefs.empty,
            adsr = AdsrDef.empty,
            accelerate = null,
            vibrato = null,
            vibratoMod = null,
            pAttack = null,
            pDecay = null,
            pRelease = null,
            pEnv = null,
            pCurve = null,
            pAnchor = null,
            fmh = null,
            fmAttack = null,
            fmDecay = null,
            fmSustain = null,
            fmEnv = null,
            distort = null,
            distortShape = null,
            coarse = null,
            crush = null,
            phaser = null,
            phaserDepth = null,
            phaserCenter = null,
            phaserSweep = null,
            phaserFloor = null,
            tremoloSync = null,
            tremoloDepth = null,
            tremoloSkew = null,
            tremoloPhase = null,
            tremoloShape = null,
            duckCylinder = null,
            duckAttack = null,
            duckDepth = null,
            cutoff = null,
            hcutoff = null,
            bandf = null,
            resonance = null,
            cylinder = null,
            pan = null,
            delay = null,
            delayTime = null,
            delayFeedback = null,
            reverb = null,
            reverbSize = null,
            reverbLowpass = null,
            begin = null,
            end = null,
            speed = null,
            loop = null,
            cut = null,
            loopBegin = null,
            loopEnd = null,
            compressorThreshold = null,
            compressorRatio = null,
            compressorKnee = null,
            compressorAttack = null,
            compressorRelease = null,
            solo = null,
            sourceId = null,
        )
    }

    fun asSampleRequest(): SampleRequest {
        return SampleRequest(bank = bank, sound = sound, index = soundIndex, note = note)
    }
}
