package io.peekandpoke.klang.sprudel

import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.FilterDefs
import io.peekandpoke.klang.audio_bridge.FilterEnvDef
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.SoundValue
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.uniqueId
import kotlinx.serialization.Serializable

/**
 * Sprudel-specific voice data with flat fields.
 *
 * This is the intermediate representation used within the Sprudel pattern system.
 * It uses flat fields (no complex objects like AdsrDef or FilterDefs) to match
 * the original JavaScript Strudel implementation.
 *
 * Gets converted to [VoiceData] when passed to the audio engine.
 *
 * **All properties are `var` by design — for performance.** The pattern engine mutates voice data in
 * place down the modifier chain (via [io.peekandpoke.klang.sprudel.lang.voiceSetter]) instead of
 * allocating a fresh copy per modifier, which is what previously dominated query cost. The trade-off:
 * an instance is NOT safe to share — **the caller is responsible for cloning when a value might be
 * reused or handed to more than one consumer** (use [clone]). The leaf emitters (`AtomicPattern`,
 * `AtomicInfinitePattern`, `StaticSprudelPattern`) clone on emission so every queried event owns its
 * data; mutate freely from there. There is intentionally no shared `empty` singleton — construct a
 * fresh one with `SprudelVoiceData()`. See `docs/tasks/mutable-voicedata-optimization.md`.
 */
@Serializable
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

    // ADSR (flattened)
    var attack: Double?,
    var decay: Double?,
    var sustain: Double?,
    var release: Double?,
    var attackCurve: AdsrCurve?,
    var decayCurve: AdsrCurve?,
    var releaseCurve: AdsrCurve?,

    // Pitch / Glisando
    var accelerate: Double?,

    // Vibrato
    var vibrato: Double?,
    var vibratoMod: Double?,

    // Pitch envelope
    var pAttack: Double?,
    var pDecay: Double?,
    var pRelease: Double?,
    var pEnv: Double?,
    var pCurve: Double?,
    var pAnchor: Double?,

    // FM Synthesis
    /** FM harmonicity ratio (carrier to modulator frequency ratio) */
    var fmh: Double?,
    /** FM envelope attack time */
    var fmAttack: Double?,
    /** FM envelope decay time */
    var fmDecay: Double?,
    /** FM envelope sustain level */
    var fmSustain: Double?,
    /** FM modulation depth/amount */
    var fmEnv: Double?,

    // Effects
    var distort: Double?,
    /** Distortion shape: soft, hard, gentle, softsat, cubic, exp, sineshaper, zerosquare, chebyshev, fold, linearfold, diode, tube, asym, stompbox, rectify */
    var distortShape: String?,
    /** Distortion oversampling factor (2=2x, 4=4x, 8=8x; non-power-of-2 floored; <=1 = off) */
    var distortOversample: Int?,
    var coarse: Double?,
    /** Coarse (sample-rate reducer) oversampling factor (2=2x, 4=4x, 8=8x; non-power-of-2 floored; <=1 = off) */
    var coarseOversample: Int?,
    var crush: Double?,
    /** Crush (bit-depth reducer) oversampling factor (2=2x, 4=4x, 8=8x; non-power-of-2 floored; <=1 = off) */
    var crushOversample: Int?,

    // Phaser
    /** Phaser modulation speed */
    var phaserRate: Double?,
    /** Phaser depth (0-1) */
    var phaserDepth: Double?,
    /** Phaser center frequency (Hz) */
    var phaserCenter: Double?,
    /** Phaser sweep range (Hz) */
    var phaserSweep: Double?,

    // Tremolo
    /** Tremolo modulation speed in cycles */
    var tremoloSync: Double?,
    /** Tremolo depth */
    var tremoloDepth: Double?,
    /** Tremolo waveform shape/skew (0-1) */
    var tremoloSkew: Double?,
    /** Tremolo phase offset in cycles */
    var tremoloPhase: Double?,
    /** Tremolo waveform type (tri, square, sine, saw, ramp) */
    var tremoloShape: String?,

    // Ducking / Sidechain
    /** Target cylinder to listen to for ducking (source of sidechain signal) */
    var duckCylinder: Int?,
    /** Duck return-to-normal time in seconds (attack/release time) */
    var duckAttack: Double?,
    /** Ducking amount (0.0 = no ducking, 1.0 = full silence) */
    var duckDepth: Double?,

    // Filters (flattened) - each filter has its own cutoff and resonance
    /** Low pass filter cutoff frequency */
    var cutoff: Double?,
    /** Low pass filter resonance/Q */
    var resonance: Double?,
    /** High pass filter cutoff frequency */
    var hcutoff: Double?,
    /** High pass filter resonance/Q */
    var hresonance: Double?,
    /** Band pass filter cutoff frequency */
    var bandf: Double?,
    /** Band pass filter resonance/Q */
    var bandq: Double?,
    /** Notch filter cutoff frequency */
    var notchf: Double?,
    /** Notch filter resonance/Q */
    var nresonance: Double?,

    // Lowpass filter envelope
    /** Low pass filter envelope attack time */
    var lpattack: Double?,
    /** Low pass filter envelope decay time */
    var lpdecay: Double?,
    /** Low pass filter envelope sustain level */
    var lpsustain: Double?,
    /** Low pass filter envelope release time */
    var lprelease: Double?,
    /** Low pass filter envelope depth/amount */
    var lpenv: Double?,

    // Highpass filter envelope
    /** High pass filter envelope attack time */
    var hpattack: Double?,
    /** High pass filter envelope decay time */
    var hpdecay: Double?,
    /** High pass filter envelope sustain level */
    var hpsustain: Double?,
    /** High pass filter envelope release time */
    var hprelease: Double?,
    /** High pass filter envelope depth/amount */
    var hpenv: Double?,

    // Bandpass filter envelope
    /** Band pass filter envelope attack time */
    var bpattack: Double?,
    /** Band pass filter envelope decay time */
    var bpdecay: Double?,
    /** Band pass filter envelope sustain level */
    var bpsustain: Double?,
    /** Band pass filter envelope release time */
    var bprelease: Double?,
    /** Band pass filter envelope depth/amount */
    var bpenv: Double?,

    // Notch filter envelope
    /** Notch filter envelope attack time */
    var nfattack: Double?,
    /** Notch filter envelope decay time */
    var nfdecay: Double?,
    /** Notch filter envelope sustain level */
    var nfsustain: Double?,
    /** Notch filter envelope release time */
    var nfrelease: Double?,
    /** Notch filter envelope depth/amount */
    var nfenv: Double?,

    // Routing
    /** The mix channel / bus / orbit / cylinder */
    var cylinder: Int?,

    // Panning (-1.0 = Left, 0.0 = Center, 1.0 = Right)
    var pan: Double?,

    // Delay
    var delay: Double?, // Mix amount (0.0 to 1.0)
    var delayTime: Double?, // Time in seconds
    var delayFeedback: Double?, // Feedback amount (0.0 to <1.0)

    // Reverb
    var room: Double?,
    var roomSize: Double?,
    /** Reverb fade time */
    var roomFade: Double?,
    /** Reverb lowpass start frequency */
    var roomLp: Double?,
    /** Reverb lowpass frequency at -60dB */
    var roomDim: Double?,
    /** Impulse response sample */
    var iResponse: String?,

    // Sample manipulation
    var begin: Double?,
    var end: Double?,
    var speed: Double?,
    var unit: String?,
    var loop: Boolean?,
    var cut: Int?,
    var loopBegin: Double?,
    var loopEnd: Double?,

    // Voice / Singing
    /** Vowel formant filter (a, e, i, o, u) */
    var vowel: String?,

    // Dynamics / Compression
    /** Dynamic range compression settings (threshold:ratio:knee:attack:release) */
    var compressor: String?,

    // Playback control
    /** Solo value - 0.0 = disabled, 0.0..1.0 = enabled (amount), null = not set */
    var solo: Double?,

    /** Unique pattern ID for tracking solo state across pattern changes */
    var patternId: String?,

    /**
     * Voice pipeline engine name — selects the topology of the Filter stage.
     * Known values: `"modern"` (default), `"pedal"`. Unknown/null → modern.
     */
    var engine: String?,

    // Custom value
    var value: SprudelVoiceValue?,
) {

    /**
     * Fresh shallow copy of this voice data.
     *
     * Used by the leaf emitters (`AtomicPattern`, `AtomicInfinitePattern`, `StaticSprudelPattern`)
     * to hand every event its own single-owner instance, which is the invariant that makes the
     * in-place mutation of `var` fields safe (see `docs/tasks/mutable-voicedata-optimization.md`).
     * Flat fields only — `oscParams` is treated as immutable-replace, so sharing its reference is fine.
     */
    fun clone(): SprudelVoiceData = copy()

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
            attack = other.attack ?: attack,
            decay = other.decay ?: decay,
            sustain = other.sustain ?: sustain,
            release = other.release ?: release,
            attackCurve = other.attackCurve ?: attackCurve,
            decayCurve = other.decayCurve ?: decayCurve,
            releaseCurve = other.releaseCurve ?: releaseCurve,
            accelerate = other.accelerate ?: accelerate,
            vibrato = other.vibrato ?: vibrato,
            vibratoMod = other.vibratoMod ?: vibratoMod,
            pAttack = other.pAttack ?: pAttack,
            pDecay = other.pDecay ?: pDecay,
            pRelease = other.pRelease ?: pRelease,
            pEnv = other.pEnv ?: pEnv,
            pCurve = other.pCurve ?: pCurve,
            pAnchor = other.pAnchor ?: pAnchor,
            fmh = other.fmh ?: fmh,
            fmAttack = other.fmAttack ?: fmAttack,
            fmDecay = other.fmDecay ?: fmDecay,
            fmSustain = other.fmSustain ?: fmSustain,
            fmEnv = other.fmEnv ?: fmEnv,
            distort = other.distort ?: distort,
            distortShape = other.distortShape ?: distortShape,
            distortOversample = other.distortOversample ?: distortOversample,
            coarse = other.coarse ?: coarse,
            coarseOversample = other.coarseOversample ?: coarseOversample,
            crush = other.crush ?: crush,
            crushOversample = other.crushOversample ?: crushOversample,
            phaserRate = other.phaserRate ?: phaserRate,
            phaserDepth = other.phaserDepth ?: phaserDepth,
            phaserCenter = other.phaserCenter ?: phaserCenter,
            phaserSweep = other.phaserSweep ?: phaserSweep,
            tremoloSync = other.tremoloSync ?: tremoloSync,
            tremoloDepth = other.tremoloDepth ?: tremoloDepth,
            tremoloSkew = other.tremoloSkew ?: tremoloSkew,
            tremoloPhase = other.tremoloPhase ?: tremoloPhase,
            tremoloShape = other.tremoloShape ?: tremoloShape,
            duckCylinder = other.duckCylinder ?: duckCylinder,
            duckAttack = other.duckAttack ?: duckAttack,
            duckDepth = other.duckDepth ?: duckDepth,
            cutoff = other.cutoff ?: cutoff,
            resonance = other.resonance ?: resonance,
            hcutoff = other.hcutoff ?: hcutoff,
            hresonance = other.hresonance ?: hresonance,
            bandf = other.bandf ?: bandf,
            bandq = other.bandq ?: bandq,
            notchf = other.notchf ?: notchf,
            nresonance = other.nresonance ?: nresonance,
            lpattack = other.lpattack ?: lpattack,
            lpdecay = other.lpdecay ?: lpdecay,
            lpsustain = other.lpsustain ?: lpsustain,
            lprelease = other.lprelease ?: lprelease,
            lpenv = other.lpenv ?: lpenv,
            hpattack = other.hpattack ?: hpattack,
            hpdecay = other.hpdecay ?: hpdecay,
            hpsustain = other.hpsustain ?: hpsustain,
            hprelease = other.hprelease ?: hprelease,
            hpenv = other.hpenv ?: hpenv,
            bpattack = other.bpattack ?: bpattack,
            bpdecay = other.bpdecay ?: bpdecay,
            bpsustain = other.bpsustain ?: bpsustain,
            bprelease = other.bprelease ?: bprelease,
            bpenv = other.bpenv ?: bpenv,
            nfattack = other.nfattack ?: nfattack,
            nfdecay = other.nfdecay ?: nfdecay,
            nfsustain = other.nfsustain ?: nfsustain,
            nfrelease = other.nfrelease ?: nfrelease,
            nfenv = other.nfenv ?: nfenv,
            cylinder = other.cylinder ?: cylinder,
            pan = other.pan ?: pan,
            delay = other.delay ?: delay,
            delayTime = other.delayTime ?: delayTime,
            delayFeedback = other.delayFeedback ?: delayFeedback,
            room = other.room ?: room,
            roomSize = other.roomSize ?: roomSize,
            roomFade = other.roomFade ?: roomFade,
            roomLp = other.roomLp ?: roomLp,
            roomDim = other.roomDim ?: roomDim,
            iResponse = other.iResponse ?: iResponse,
            begin = other.begin ?: begin,
            end = other.end ?: end,
            speed = other.speed ?: speed,
            unit = other.unit ?: unit,
            loop = other.loop ?: loop,
            cut = other.cut ?: cut,
            loopBegin = other.loopBegin ?: loopBegin,
            loopEnd = other.loopEnd ?: loopEnd,
            vowel = other.vowel ?: vowel,
            compressor = other.compressor ?: compressor,
            solo = other.solo ?: solo,
            patternId = patternId,  // Never merge - preserve original source ID
            engine = other.engine ?: engine,
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
        attack = other.attack ?: attack
        decay = other.decay ?: decay
        sustain = other.sustain ?: sustain
        release = other.release ?: release
        attackCurve = other.attackCurve ?: attackCurve
        decayCurve = other.decayCurve ?: decayCurve
        releaseCurve = other.releaseCurve ?: releaseCurve
        accelerate = other.accelerate ?: accelerate
        vibrato = other.vibrato ?: vibrato
        vibratoMod = other.vibratoMod ?: vibratoMod
        pAttack = other.pAttack ?: pAttack
        pDecay = other.pDecay ?: pDecay
        pRelease = other.pRelease ?: pRelease
        pEnv = other.pEnv ?: pEnv
        pCurve = other.pCurve ?: pCurve
        pAnchor = other.pAnchor ?: pAnchor
        fmh = other.fmh ?: fmh
        fmAttack = other.fmAttack ?: fmAttack
        fmDecay = other.fmDecay ?: fmDecay
        fmSustain = other.fmSustain ?: fmSustain
        fmEnv = other.fmEnv ?: fmEnv
        distort = other.distort ?: distort
        distortShape = other.distortShape ?: distortShape
        distortOversample = other.distortOversample ?: distortOversample
        coarse = other.coarse ?: coarse
        coarseOversample = other.coarseOversample ?: coarseOversample
        crush = other.crush ?: crush
        crushOversample = other.crushOversample ?: crushOversample
        phaserRate = other.phaserRate ?: phaserRate
        phaserDepth = other.phaserDepth ?: phaserDepth
        phaserCenter = other.phaserCenter ?: phaserCenter
        phaserSweep = other.phaserSweep ?: phaserSweep
        tremoloSync = other.tremoloSync ?: tremoloSync
        tremoloDepth = other.tremoloDepth ?: tremoloDepth
        tremoloSkew = other.tremoloSkew ?: tremoloSkew
        tremoloPhase = other.tremoloPhase ?: tremoloPhase
        tremoloShape = other.tremoloShape ?: tremoloShape
        duckCylinder = other.duckCylinder ?: duckCylinder
        duckAttack = other.duckAttack ?: duckAttack
        duckDepth = other.duckDepth ?: duckDepth
        cutoff = other.cutoff ?: cutoff
        resonance = other.resonance ?: resonance
        hcutoff = other.hcutoff ?: hcutoff
        hresonance = other.hresonance ?: hresonance
        bandf = other.bandf ?: bandf
        bandq = other.bandq ?: bandq
        notchf = other.notchf ?: notchf
        nresonance = other.nresonance ?: nresonance
        lpattack = other.lpattack ?: lpattack
        lpdecay = other.lpdecay ?: lpdecay
        lpsustain = other.lpsustain ?: lpsustain
        lprelease = other.lprelease ?: lprelease
        lpenv = other.lpenv ?: lpenv
        hpattack = other.hpattack ?: hpattack
        hpdecay = other.hpdecay ?: hpdecay
        hpsustain = other.hpsustain ?: hpsustain
        hprelease = other.hprelease ?: hprelease
        hpenv = other.hpenv ?: hpenv
        bpattack = other.bpattack ?: bpattack
        bpdecay = other.bpdecay ?: bpdecay
        bpsustain = other.bpsustain ?: bpsustain
        bprelease = other.bprelease ?: bprelease
        bpenv = other.bpenv ?: bpenv
        nfattack = other.nfattack ?: nfattack
        nfdecay = other.nfdecay ?: nfdecay
        nfsustain = other.nfsustain ?: nfsustain
        nfrelease = other.nfrelease ?: nfrelease
        nfenv = other.nfenv ?: nfenv
        cylinder = other.cylinder ?: cylinder
        pan = other.pan ?: pan
        delay = other.delay ?: delay
        delayTime = other.delayTime ?: delayTime
        delayFeedback = other.delayFeedback ?: delayFeedback
        room = other.room ?: room
        roomSize = other.roomSize ?: roomSize
        roomFade = other.roomFade ?: roomFade
        roomLp = other.roomLp ?: roomLp
        roomDim = other.roomDim ?: roomDim
        iResponse = other.iResponse ?: iResponse
        begin = other.begin ?: begin
        end = other.end ?: end
        speed = other.speed ?: speed
        unit = other.unit ?: unit
        loop = other.loop ?: loop
        cut = other.cut ?: cut
        loopBegin = other.loopBegin ?: loopBegin
        loopEnd = other.loopEnd ?: loopEnd
        vowel = other.vowel ?: vowel
        compressor = other.compressor ?: compressor
        solo = other.solo ?: solo
        // patternId intentionally preserved (never taken from other) — matches merge()
        engine = other.engine ?: engine
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

            // Vowel formant filter
            vowel?.let { vowelValue ->
                val formantBands = resolveVowelBands(vowelValue)

                formantBands?.let { bands ->
                    add(FilterDef.Formant(bands = bands))
                }
            }
        }

        // Canonical filter chain order: HIGHPASS → BANDPASS → NOTCH → FORMANT → LOWPASS.
        // Chain order is audible once the filters are nonlinear (analog>0 enables the
        // Obxd state-dependent saturation, which does NOT commute): the highpass strips
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
                is FilterDef.LowPass -> 4
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
            engine = engine,
        )
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
    attack = null,
    decay = null,
    sustain = null,
    release = null,
    attackCurve = null,
    decayCurve = null,
    releaseCurve = null,
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
    distortOversample = null,
    coarse = null,
    coarseOversample = null,
    crush = null,
    crushOversample = null,
    phaserRate = null,
    phaserDepth = null,
    phaserCenter = null,
    phaserSweep = null,
    tremoloSync = null,
    tremoloDepth = null,
    tremoloSkew = null,
    tremoloPhase = null,
    tremoloShape = null,
    duckCylinder = null,
    duckAttack = null,
    duckDepth = null,
    cutoff = null,
    resonance = null,
    hcutoff = null,
    hresonance = null,
    bandf = null,
    bandq = null,
    notchf = null,
    nresonance = null,
    lpattack = null,
    lpdecay = null,
    lpsustain = null,
    lprelease = null,
    lpenv = null,
    hpattack = null,
    hpdecay = null,
    hpsustain = null,
    hprelease = null,
    hpenv = null,
    bpattack = null,
    bpdecay = null,
    bpsustain = null,
    bprelease = null,
    bpenv = null,
    nfattack = null,
    nfdecay = null,
    nfsustain = null,
    nfrelease = null,
    nfenv = null,
    cylinder = null,
    pan = null,
    delay = null,
    delayTime = null,
    delayFeedback = null,
    room = null,
    roomSize = null,
    roomFade = null,
    roomLp = null,
    roomDim = null,
    iResponse = null,
    begin = null,
    end = null,
    speed = null,
    unit = null,
    loop = null,
    cut = null,
    loopBegin = null,
    loopEnd = null,
    vowel = null,
    compressor = null,
    solo = null,
    patternId = null,
    engine = null,
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

