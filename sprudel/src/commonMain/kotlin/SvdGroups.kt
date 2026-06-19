package io.peekandpoke.klang.sprudel

import io.peekandpoke.klang.audio_bridge.AdsrCurve

/**
 * Mutable sub-objects (`Svd*`) of [SprudelVoiceData], holding cohesive clusters of voice fields.
 *
 * **Why grouped:** the per-event leaf clone copies the whole [SprudelVoiceData]. A flat ~105-field class
 * copies all fields even though a leaf's data is mostly null. Grouping the optional clusters means a null
 * cluster is a single null reference (one slot, not cloned) instead of N null fields. [SprudelVoiceData.clone]
 * deep-copies only the non-null groups; setters lazily create a group on first write and then mutate it in
 * place (zero-copy), preserving the single-owner mutation model.
 *
 * These are sprudel-internal mutable mirrors — the immutable wire-format equivalents live in `audio_bridge`
 * (`AdsrDef`, `FilterDefs`/`FilterDef`/`FilterEnvDef`); [SprudelVoiceData.toVoiceData] maps across.
 *
 * `copy()` (the generated data-class copy) is the per-group deep clone — every field is an immutable scalar,
 * so a shallow `copy()` fully detaches the clone. Each group has a `mergeSvd*` helper (field-wise `over`-wins,
 * fresh result) used by [SprudelVoiceData.merge]/[SprudelVoiceData.mergeFrom].
 */

/** ADSR amplitude envelope. */
data class SvdAdsr(
    var attack: Double? = null,
    var decay: Double? = null,
    var sustain: Double? = null,
    var release: Double? = null,
    var attackCurve: AdsrCurve? = null,
    var decayCurve: AdsrCurve? = null,
    var releaseCurve: AdsrCurve? = null,
)

/**
 * One filter (low/high/band/notch): cutoff + resonance/Q plus an optional dynamic envelope
 * (attack/decay/sustain/release/[env] depth). The flat names differ per filter type
 * (cutoff/hcutoff/bandf/notchf, resonance/hresonance/bandq/nresonance, and the `lp`/`hp`/`bp`/`nf`
 * envelope prefixes) — they all map onto these generic fields via the accessors on [SprudelVoiceData].
 */
data class SvdFilter(
    var cutoff: Double? = null,
    var resonance: Double? = null,
    var attack: Double? = null,
    var decay: Double? = null,
    var sustain: Double? = null,
    var release: Double? = null,
    var env: Double? = null,
)

/** Pitch modulation: glide ([accelerate]) + vibrato. */
data class SvdPitchMod(
    var accelerate: Double? = null,
    var vibrato: Double? = null,
    var vibratoMod: Double? = null,
)

/** Pitch envelope. */
data class SvdPitchEnv(
    var pAttack: Double? = null,
    var pDecay: Double? = null,
    var pRelease: Double? = null,
    var pEnv: Double? = null,
    var pCurve: Double? = null,
    var pAnchor: Double? = null,
)

/** FM synthesis. */
data class SvdFm(
    var fmh: Double? = null,
    var fmAttack: Double? = null,
    var fmDecay: Double? = null,
    var fmSustain: Double? = null,
    var fmEnv: Double? = null,
)

/** Distortion + lo-fi (sample-rate / bit-depth reduction). */
data class SvdDistortion(
    var distort: Double? = null,
    var distortShape: String? = null,
    var distortOversample: Int? = null,
    var coarse: Double? = null,
    var coarseOversample: Int? = null,
    var crush: Double? = null,
    var crushOversample: Int? = null,
)

/** Phaser. */
data class SvdPhaser(
    var phaserRate: Double? = null,
    var phaserDepth: Double? = null,
    var phaserCenter: Double? = null,
    var phaserSweep: Double? = null,
)

/** Tremolo. */
data class SvdTremolo(
    var tremoloSync: Double? = null,
    var tremoloDepth: Double? = null,
    var tremoloSkew: Double? = null,
    var tremoloPhase: Double? = null,
    var tremoloShape: String? = null,
)

/** Ducking / sidechain. */
data class SvdDuck(
    var duckCylinder: Int? = null,
    var duckAttack: Double? = null,
    var duckDepth: Double? = null,
)

/** Delay. */
data class SvdDelay(
    var delay: Double? = null,
    var delayTime: Double? = null,
    var delayFeedback: Double? = null,
)

/** Reverb. */
data class SvdReverb(
    var room: Double? = null,
    var roomSize: Double? = null,
    var roomFade: Double? = null,
    var roomLp: Double? = null,
    var roomDim: Double? = null,
    var iResponse: String? = null,
)

/** Sample playback manipulation. */
data class SvdSample(
    var begin: Double? = null,
    var end: Double? = null,
    var speed: Double? = null,
    var unit: String? = null,
    var loop: Boolean? = null,
    var cut: Int? = null,
    var loopBegin: Double? = null,
    var loopEnd: Double? = null,
)

// --- Field-wise merge helpers: `over` wins per field; always return a fresh, single-owner group. ----------

fun mergeSvdAdsr(base: SvdAdsr?, over: SvdAdsr?): SvdAdsr? {
    if (base == null) return over?.copy()
    if (over == null) return base.copy()
    return SvdAdsr(
        attack = over.attack ?: base.attack,
        decay = over.decay ?: base.decay,
        sustain = over.sustain ?: base.sustain,
        release = over.release ?: base.release,
        attackCurve = over.attackCurve ?: base.attackCurve,
        decayCurve = over.decayCurve ?: base.decayCurve,
        releaseCurve = over.releaseCurve ?: base.releaseCurve,
    )
}

fun mergeSvdFilter(base: SvdFilter?, over: SvdFilter?): SvdFilter? {
    if (base == null) return over?.copy()
    if (over == null) return base.copy()
    return SvdFilter(
        cutoff = over.cutoff ?: base.cutoff,
        resonance = over.resonance ?: base.resonance,
        attack = over.attack ?: base.attack,
        decay = over.decay ?: base.decay,
        sustain = over.sustain ?: base.sustain,
        release = over.release ?: base.release,
        env = over.env ?: base.env,
    )
}

fun mergeSvdPitchMod(base: SvdPitchMod?, over: SvdPitchMod?): SvdPitchMod? {
    if (base == null) return over?.copy()
    if (over == null) return base.copy()
    return SvdPitchMod(
        accelerate = over.accelerate ?: base.accelerate,
        vibrato = over.vibrato ?: base.vibrato,
        vibratoMod = over.vibratoMod ?: base.vibratoMod,
    )
}

fun mergeSvdPitchEnv(base: SvdPitchEnv?, over: SvdPitchEnv?): SvdPitchEnv? {
    if (base == null) return over?.copy()
    if (over == null) return base.copy()
    return SvdPitchEnv(
        pAttack = over.pAttack ?: base.pAttack,
        pDecay = over.pDecay ?: base.pDecay,
        pRelease = over.pRelease ?: base.pRelease,
        pEnv = over.pEnv ?: base.pEnv,
        pCurve = over.pCurve ?: base.pCurve,
        pAnchor = over.pAnchor ?: base.pAnchor,
    )
}

fun mergeSvdFm(base: SvdFm?, over: SvdFm?): SvdFm? {
    if (base == null) return over?.copy()
    if (over == null) return base.copy()
    return SvdFm(
        fmh = over.fmh ?: base.fmh,
        fmAttack = over.fmAttack ?: base.fmAttack,
        fmDecay = over.fmDecay ?: base.fmDecay,
        fmSustain = over.fmSustain ?: base.fmSustain,
        fmEnv = over.fmEnv ?: base.fmEnv,
    )
}

fun mergeSvdDistortion(base: SvdDistortion?, over: SvdDistortion?): SvdDistortion? {
    if (base == null) return over?.copy()
    if (over == null) return base.copy()
    return SvdDistortion(
        distort = over.distort ?: base.distort,
        distortShape = over.distortShape ?: base.distortShape,
        distortOversample = over.distortOversample ?: base.distortOversample,
        coarse = over.coarse ?: base.coarse,
        coarseOversample = over.coarseOversample ?: base.coarseOversample,
        crush = over.crush ?: base.crush,
        crushOversample = over.crushOversample ?: base.crushOversample,
    )
}

fun mergeSvdPhaser(base: SvdPhaser?, over: SvdPhaser?): SvdPhaser? {
    if (base == null) return over?.copy()
    if (over == null) return base.copy()
    return SvdPhaser(
        phaserRate = over.phaserRate ?: base.phaserRate,
        phaserDepth = over.phaserDepth ?: base.phaserDepth,
        phaserCenter = over.phaserCenter ?: base.phaserCenter,
        phaserSweep = over.phaserSweep ?: base.phaserSweep,
    )
}

fun mergeSvdTremolo(base: SvdTremolo?, over: SvdTremolo?): SvdTremolo? {
    if (base == null) return over?.copy()
    if (over == null) return base.copy()
    return SvdTremolo(
        tremoloSync = over.tremoloSync ?: base.tremoloSync,
        tremoloDepth = over.tremoloDepth ?: base.tremoloDepth,
        tremoloSkew = over.tremoloSkew ?: base.tremoloSkew,
        tremoloPhase = over.tremoloPhase ?: base.tremoloPhase,
        tremoloShape = over.tremoloShape ?: base.tremoloShape,
    )
}

fun mergeSvdDuck(base: SvdDuck?, over: SvdDuck?): SvdDuck? {
    if (base == null) return over?.copy()
    if (over == null) return base.copy()
    return SvdDuck(
        duckCylinder = over.duckCylinder ?: base.duckCylinder,
        duckAttack = over.duckAttack ?: base.duckAttack,
        duckDepth = over.duckDepth ?: base.duckDepth,
    )
}

fun mergeSvdDelay(base: SvdDelay?, over: SvdDelay?): SvdDelay? {
    if (base == null) return over?.copy()
    if (over == null) return base.copy()
    return SvdDelay(
        delay = over.delay ?: base.delay,
        delayTime = over.delayTime ?: base.delayTime,
        delayFeedback = over.delayFeedback ?: base.delayFeedback,
    )
}

fun mergeSvdReverb(base: SvdReverb?, over: SvdReverb?): SvdReverb? {
    if (base == null) return over?.copy()
    if (over == null) return base.copy()
    return SvdReverb(
        room = over.room ?: base.room,
        roomSize = over.roomSize ?: base.roomSize,
        roomFade = over.roomFade ?: base.roomFade,
        roomLp = over.roomLp ?: base.roomLp,
        roomDim = over.roomDim ?: base.roomDim,
        iResponse = over.iResponse ?: base.iResponse,
    )
}

fun mergeSvdSample(base: SvdSample?, over: SvdSample?): SvdSample? {
    if (base == null) return over?.copy()
    if (over == null) return base.copy()
    return SvdSample(
        begin = over.begin ?: base.begin,
        end = over.end ?: base.end,
        speed = over.speed ?: base.speed,
        unit = over.unit ?: base.unit,
        loop = over.loop ?: base.loop,
        cut = over.cut ?: base.cut,
        loopBegin = over.loopBegin ?: base.loopBegin,
        loopEnd = over.loopEnd ?: base.loopEnd,
    )
}
