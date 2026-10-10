/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.peekandpoke.klang.audio_bridge.constants.ENV_DECLICK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_ATTACK_SEC
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_DECAY_SEC
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_RELEASE_SEC
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_SUSTAIN_LEVEL
import io.peekandpoke.klang.audio_bridge.constants.FM_ENV_ATTACK_SEC
import io.peekandpoke.klang.audio_bridge.constants.FM_ENV_DECAY_SEC
import io.peekandpoke.klang.audio_bridge.constants.FM_ENV_RELEASE_SEC
import io.peekandpoke.klang.audio_bridge.constants.FM_ENV_SUSTAIN_LEVEL
import io.peekandpoke.klang.audio_bridge.constants.FM_RATIO
import io.peekandpoke.klang.audio_bridge.constants.MOD_ENV_CURVE
import io.peekandpoke.klang.audio_bridge.constants.PITCH_ENV_ATTACK_SEC
import io.peekandpoke.klang.audio_bridge.constants.PITCH_ENV_DECAY_SEC
import io.peekandpoke.klang.audio_bridge.constants.PITCH_ENV_RELEASE_SEC
import io.peekandpoke.klang.audio_bridge.constants.PITCH_ENV_SUSTAIN_LEVEL
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET
import io.peekandpoke.klang.audio_bridge.constants.VIBRATO_PHASE
import io.peekandpoke.klang.audio_bridge.constants.VIBRATO_RANGE_FROM
import io.peekandpoke.klang.audio_bridge.constants.VIBRATO_RANGE_TO
import io.peekandpoke.klang.audio_bridge.constants.VIBRATO_RATE_HZ
import io.peekandpoke.klang.audio_bridge.constants.VOICE_ADSR_ATTACK_SEC
import io.peekandpoke.klang.audio_bridge.constants.VOICE_ADSR_DECAY_SEC
import io.peekandpoke.klang.audio_bridge.constants.VOICE_ADSR_RELEASE_SEC
import io.peekandpoke.klang.audio_bridge.constants.VOICE_ADSR_SUSTAIN_LEVEL

// ═════════════════════════════════════════════════════════════════════════════════════════════
// `classic()`: the voice strip's chain as a tail of slotted Ignitor stages (phase 3 step 5; the strip retired in step 9)
//
// The plan is `docs/plans/signal-flow-redesign.md` section 5 and `docs/tasks-archive/2026-09/20260928-builtin-instruments.md`.
// The SLOTS below are grouped per stage and named `<door>.<param>`: the sprudel door's name, then the
// engine door's word (`lpf.freq`, `crush.bits`, `adsr.attack`, ...), the `<stage>.<knob>` rule of the
// Katalyst's classic chain (Q21, `/dsl-design` section 4). Sprudel's door parameter and reader take the
// same word. Each slot's KDoc names the sprudel reader it mirrors, and sprudel's `toVoiceData` writes exactly
// these keys (`classicSlotParams`, phase 3 step 8). Every default is the value the voice strip
// used when the pattern wrote nothing, read from the same constant the strip read.
// ═════════════════════════════════════════════════════════════════════════════════════════════

/**
 * The one `Param` constructor of this file: `<door>.<param>`, its default, and a description. The
 * description is user-visible (tooltips, generated docs), so it says "mirrors sprudel's reader" only
 * where sprudel HAS that reader; the slots without one ([description] given) say what writes them.
 */
private fun slot(door: String, param: String, default: Double, description: String? = null): IgnitorDsl.Param =
    IgnitorDsl.Param(
        name = "$door.$param",
        default = default,
        description = description ?: "Mirrors sprudel's reader `$door.$param`",
    )

/**
 * The slots of a lowpass or a highpass stage (`Slots.lpf`, `Slots.hpf`), mirroring sprudel's
 * `lpf(freq, q, passes, env, attack, decay, sustain, release)` and its readers `lpf.freq` ... `lpf.release`
 * (`hpf` the same).
 *
 * @property freq cutoff in Hz; mirrors `<door>.freq`. Default UNSET: an unwritten cutoff is no
 *   filter at all (the gate's filter row), as a pattern without `lpf` had none on the strip.
 * @property q resonance; mirrors `<door>.q`. Default 0.707, the strip's `?: 0.707`.
 * @property passes cascade count, read once at voice build; mirrors `<door>.passes`. Default 1.
 * @property env cutoff-envelope depth in semitones; mirrors `<door>.env`. Default UNSET: the strip's
 *   depth is an absence that becomes `FILTER_ENV_DEPTH_SEMITONES` when a stage knob is written (the
 *   slot-layer fill in the runtime's `filterEnvDef`) and no envelope otherwise.
 * @property attack cutoff-envelope attack in seconds; mirrors `<door>.attack`.
 * @property decay cutoff-envelope decay in seconds; mirrors `<door>.decay`.
 * @property sustain cutoff-envelope sustain share of the depth; mirrors `<door>.sustain`.
 * @property release cutoff-envelope release in seconds; mirrors `<door>.release`.
 */
class PassFilterSlots internal constructor(door: String) {
    val freq: IgnitorDsl = slot(door = door, param = "freq", default = SLOT_UNSET)
    val q: IgnitorDsl = slot(door = door, param = "q", default = 0.707)
    val passes: IgnitorDsl = slot(door = door, param = "passes", default = 1.0)
    val env: IgnitorDsl = slot(door = door, param = "env", default = SLOT_UNSET)
    val attack: IgnitorDsl = slot(door = door, param = "attack", default = FILTER_ENV_ATTACK_SEC)
    val decay: IgnitorDsl = slot(door = door, param = "decay", default = FILTER_ENV_DECAY_SEC)
    val sustain: IgnitorDsl = slot(door = door, param = "sustain", default = FILTER_ENV_SUSTAIN_LEVEL)
    val release: IgnitorDsl = slot(door = door, param = "release", default = FILTER_ENV_RELEASE_SEC)
}

/**
 * The slots of a bandpass or a notch stage (`Slots.bpf`, `Slots.notch`): [PassFilterSlots] without
 * `passes`, mirroring sprudel's `bpf(freq, q, env, attack, decay, sustain, release)` and its readers
 * (`notch` the same). Defaults as there.
 */
class BandFilterSlots internal constructor(door: String) {
    val freq: IgnitorDsl = slot(door = door, param = "freq", default = SLOT_UNSET)
    val q: IgnitorDsl = slot(door = door, param = "q", default = 0.707)
    val env: IgnitorDsl = slot(door = door, param = "env", default = SLOT_UNSET)
    val attack: IgnitorDsl = slot(door = door, param = "attack", default = FILTER_ENV_ATTACK_SEC)
    val decay: IgnitorDsl = slot(door = door, param = "decay", default = FILTER_ENV_DECAY_SEC)
    val sustain: IgnitorDsl = slot(door = door, param = "sustain", default = FILTER_ENV_SUSTAIN_LEVEL)
    val release: IgnitorDsl = slot(door = door, param = "release", default = FILTER_ENV_RELEASE_SEC)
}

/**
 * The one slot of the crush stage (`Slots.crush`), mirroring sprudel's `crush.bits`: the bit depth.
 * Default 0.0, the strip's untouched value, which the gate reads as off (below 1). Sprudel's
 * `oversample` of this stage has no slot: it moved to `oversampling-regions.md`.
 */
class CrushSlots internal constructor() {
    val bits: IgnitorDsl = slot(door = "crush", param = "bits", default = 0.0)
}

/**
 * The one slot of the coarse stage (`Slots.coarse`), mirroring sprudel's `coarse.factor`: the
 * sample-hold factor. Default 0.0, the strip's untouched value, which the gate reads as off (1 or
 * less). Sprudel's `oversample` of this stage has no slot: it moved to `oversampling-regions.md`.
 */
class CoarseSlots internal constructor() {
    val factor: IgnitorDsl = slot(door = "coarse", param = "factor", default = 0.0)
}

/**
 * The slots of the distort stage (`Slots.distort`), mirroring sprudel's `distort(amount, shape, oversample)`:
 * `distort.amount` (default 0.0, off), `distort.shape` (an INDEX into [DistortionShapes], default
 * `soft`; sprudel's name has no reader, the slot carries its index) and `distort.oversample` (the
 * factor, default 0, no oversampler; the D7 stopgap, read at voice build).
 */
class DistortSlots internal constructor() {
    val amount: IgnitorDsl = slot(door = "distort", param = "amount", default = 0.0)
    val shape: IgnitorDsl = slot(
        door = "distort", param = "shape", default = DistortionShapes.SOFT_INDEX.toDouble(),
        description = "The waveshaper as its index in the shape list; what sprudel's `distort(shape = ...)` names",
    )
    val oversample: IgnitorDsl = slot(door = "distort", param = "oversample", default = 0.0)
}

/**
 * The slots of the tremolo stage (`Slots.tremolo`), mirroring sprudel's
 * `tremolo(depth, rate, shape)`: `tremolo.depth` (default 0.0, off), `tremolo.rate` (the
 * RATE in Hz; default 0.0, the strip's untouched rate, NOT the node's 5.0) and `tremolo.shape` (an
 * INDEX into [LfoShapes], default `sine`; no sprudel reader, the name is a string there).
 *
 * No range slots, by decision (maintainer, 2026-10-06, "not in sprudel yet"): the node's `rangeFrom` / `rangeTo` stay
 * at their default `(-1, 0)` here, so a pattern's tremolo is always the classic dip. Sprudel's tremolo is a flat door
 * with no builder layer for a two-value knob; the engine is ready (two slots at -1 and 0 render the same bits), and a
 * song that wants a swell reopens it (`docs/tasks-archive/2026-10/20261006-oscillator-phase-knob.md`, decision 4).
 */
class TremoloSlots internal constructor() {
    val depth: IgnitorDsl = slot(door = "tremolo", param = "depth", default = 0.0)
    val rate: IgnitorDsl = slot(door = "tremolo", param = "rate", default = 0.0)
    val shape: IgnitorDsl = slot(
        door = "tremolo", param = "shape", default = LfoShapes.SINE_INDEX.toDouble(),
        description = "The LFO shape as its index in the shape list; what sprudel's `tremolo(shape = ...)` names",
    )
}

/**
 * The slots of the amplitude envelope (`Slots.adsr`), mirroring sprudel's `adsr(attack, decay, sustain,
 * release)` and its readers `adsr.attack` ... `adsr.release`, plus `adsr.on`, the switch sprudel's
 * `adsrOn(flag)` and `adsrOff()` write (two doors, one knob, so it is named for the stage and not for
 * either door; no sprudel reader).
 *
 * The four stage defaults are the voice envelope's (`VOICE_ADSR_*` in `constants/EnvelopeDefaults.kt`), NOT the
 * Ignitor `adsr` node's own: sustain 1.0 against 0.7, release
 * 0.05 against 0.3. The release matters beyond the envelope's shape: an OFF envelope still reports it
 * as the voice's tail, so an `adsrOff` voice lives exactly as long as it did on the strip. `adsr.on` defaults
 * to 1.0 (on) and must never default to 0.0, which is OFF (the envelope row of the gate).
 */
class AdsrSlots internal constructor() {
    val attack: IgnitorDsl = slot(door = "adsr", param = "attack", default = VOICE_ADSR_ATTACK_SEC)
    val decay: IgnitorDsl = slot(door = "adsr", param = "decay", default = VOICE_ADSR_DECAY_SEC)
    val sustain: IgnitorDsl = slot(door = "adsr", param = "sustain", default = VOICE_ADSR_SUSTAIN_LEVEL)
    val release: IgnitorDsl = slot(door = "adsr", param = "release", default = VOICE_ADSR_RELEASE_SEC)
    val on: IgnitorDsl = slot(door = "adsr", param = "on", default = 1.0, description = "The envelope's switch, 0 is off; what sprudel's `adsrOn()` / `adsrOff()` write")
}

/** The description of a curves group's three slots, which sprudel writes by name ([door]) and has no reader for. */
private fun curveSlotDescription(door: String): String =
    "A stage's curve as its index in the curve list; what sprudel's `$door(...)` names"

/** The description of the amplitude envelope's three curve slots. */
private val CURVE_SLOT_DESCRIPTION = curveSlotDescription("adsrCurves")

/**
 * The curve slots of the amplitude envelope (`Slots.adsrCurves`), mirroring sprudel's
 * `adsrCurves(attack, decay, release)`: each carries an INDEX into [AdsrCurves] (sprudel writes names,
 * and its `adsrCurves` object has no readers), default [AdsrCurve.Default], exponential, the curve
 * every amplitude envelope has when nothing is written.
 */
class AdsrCurvesSlots internal constructor() {
    val attack: IgnitorDsl = slot(door = "adsrCurves", param = "attack", default = AdsrCurves.indexOf(AdsrCurve.Default), description = CURVE_SLOT_DESCRIPTION)
    val decay: IgnitorDsl = slot(door = "adsrCurves", param = "decay", default = AdsrCurves.indexOf(AdsrCurve.Default), description = CURVE_SLOT_DESCRIPTION)
    val release: IgnitorDsl = slot(door = "adsrCurves", param = "release", default = AdsrCurves.indexOf(AdsrCurve.Default), description = CURVE_SLOT_DESCRIPTION)
}

/**
 * The curve slots of a modulation envelope: a filter's cutoff envelope (`Slots.lpfCurves`, `Slots.hpfCurves`,
 * `Slots.bpfCurves`, `Slots.notchCurves`, phase 3 step 5b (c2)) and the pitch envelope (`Slots.penvCurves`, pitch
 * pipeline step 1), mirroring sprudel's `lpfCurves(attack, decay, release)`, its three filter siblings and
 * `penvCurves(attack, decay, release)`: `<door>Curves.attack|decay|release`, each an INDEX into [AdsrCurves]
 * (sprudel writes names, and the curves objects have no readers), default the index of `MOD_ENV_CURVE`, the curve
 * every modulation envelope has when nothing is written (decision D3; the defaults' one home is
 * `constants/EnvelopeDefaults.kt`).
 */
class ModEnvelopeCurvesSlots internal constructor(door: String) {
    private val description = curveSlotDescription(door)

    val attack: IgnitorDsl = slot(door = door, param = "attack", default = AdsrCurves.indexOf(MOD_ENV_CURVE), description = description)
    val decay: IgnitorDsl = slot(door = door, param = "decay", default = AdsrCurves.indexOf(MOD_ENV_CURVE), description = description)
    val release: IgnitorDsl = slot(door = door, param = "release", default = AdsrCurves.indexOf(MOD_ENV_CURVE), description = description)
}

/**
 * The slots of the vibrato stage (`Slots.vibrato`), mirroring sprudel's `vibrato(rate, semitones, rangeFrom, rangeTo,
 * phase)` (alias `vib`) and its readers `vibrato.rate`, `vibrato.semitones`, `vibrato.rangeFrom`, `vibrato.rangeTo`,
 * `vibrato.phase` (pitch pipeline step 2, the range and the phase since 7c, decision D9; the names are decision D4, the
 * param part the Ignitor door's word).
 *
 * @property rate the LFO rate in Hz; mirrors `vibrato.rate`. Default `VIBRATO_RATE_HZ`, what the strip read for an
 *   unwritten rate.
 * @property semitones the depth in semitones; mirrors `vibrato.semitones`. It is the stage's SWITCH: default 0.0, which
 *   the gate reads as off (the `vibrato` row of `audio/ref/off-values.md`: off at a FINITE depth `<= 0`). **It must
 *   default to that safe literal, never to `SLOT_UNSET`:** the vibrato's gate keeps a NON-FINITE depth built (the
 *   runtime reads it as the node's default, `VIBRATO_SEMITONES`), so an unset default would give every voice of every
 *   song a vibrato (the shape of `mul`'s "must default to a safe literal", pitch pipeline step 0).
 * @property rangeFrom with [rangeTo], where the LFO's swing sits (the node's `rangeFrom`); mirrors `vibrato.rangeFrom`.
 *   Default `VIBRATO_RANGE_FROM` (-1). At the defaults `(-1, 1)` the runtime builds no range (it reads the two leaves at
 *   build), so an unwritten range renders the unranged vibrato's bits.
 * @property rangeTo see [rangeFrom]; mirrors `vibrato.rangeTo`. Default `VIBRATO_RANGE_TO` (1).
 * @property phase the LFO's phase in cycles (the node's `phase`); mirrors `vibrato.phase`. Default `VIBRATO_PHASE`
 *   (0), at which the runtime builds no phase input.
 *
 * The range and the phase are not switches: a call that writes only them (`vib(rangeFrom = 0)`) switches nothing on,
 * like a rate alone (`/dsl-design` section 4).
 */
class VibratoSlots internal constructor() {
    val rate: IgnitorDsl = slot(door = "vibrato", param = "rate", default = VIBRATO_RATE_HZ)
    val semitones: IgnitorDsl = slot(door = "vibrato", param = "semitones", default = 0.0)
    val rangeFrom: IgnitorDsl = slot(door = "vibrato", param = "rangeFrom", default = VIBRATO_RANGE_FROM)
    val rangeTo: IgnitorDsl = slot(door = "vibrato", param = "rangeTo", default = VIBRATO_RANGE_TO)
    val phase: IgnitorDsl = slot(door = "vibrato", param = "phase", default = VIBRATO_PHASE)
}

/**
 * The slots of the pitch envelope stage (`Slots.penv`), mirroring sprudel's `penv(semitones, attack, decay, sustain,
 * release)` and its readers `penv.semitones` ... `penv.release` (pitch pipeline step 1; the names are decision D4).
 *
 * @property semitones the pitch at the envelope's peak, in semitones; mirrors `penv.semitones`. It is the stage's
 *   SWITCH: default 0.0, which the gate reads as off (the `pitch envelope` row of `audio/ref/off-values.md`), so an
 *   unwritten `penv.semitones` builds no stage, and a call that writes only stages (`penv(attack = 0.1)`) switches
 *   nothing on, as on the retired strip.
 * @property attack attack in seconds; mirrors `penv.attack`. Default `PITCH_ENV_ATTACK_SEC`.
 * @property decay decay in seconds; mirrors `penv.decay`. Default `PITCH_ENV_DECAY_SEC`.
 * @property sustain the held share of [semitones]; mirrors `penv.sustain`. Default `PITCH_ENV_SUSTAIN_LEVEL`.
 * @property release release in seconds, from the gate; mirrors `penv.release`. Default `PITCH_ENV_RELEASE_SEC`.
 *
 * The defaults are the Ignitor `pitchEnvelope`'s, from `constants/PitchEnvelopeDefaults.kt`, the ones the strip read.
 */
class PitchEnvelopeSlots internal constructor() {
    val semitones: IgnitorDsl = slot(door = "penv", param = "semitones", default = 0.0)
    val attack: IgnitorDsl = slot(door = "penv", param = "attack", default = PITCH_ENV_ATTACK_SEC)
    val decay: IgnitorDsl = slot(door = "penv", param = "decay", default = PITCH_ENV_DECAY_SEC)
    val sustain: IgnitorDsl = slot(door = "penv", param = "sustain", default = PITCH_ENV_SUSTAIN_LEVEL)
    val release: IgnitorDsl = slot(door = "penv", param = "release", default = PITCH_ENV_RELEASE_SEC)
}

/**
 * The slots of the FM stage (`Slots.fm`), mirroring sprudel's `fm(depth, ratio, attack, decay, sustain, release)` and
 * its readers `fm.depth` ... `fm.release` (pitch pipeline step 4; the names are decision D4, the Ignitor `fm`'s words).
 *
 * @property ratio the modulator's frequency as a multiple of the note; mirrors `fm.ratio`. Default `FM_RATIO`, what
 *   the strip read for an unwritten ratio.
 * @property depth the peak modulation in Hz; mirrors `fm.depth`. It is the stage's SWITCH: default 0.0, which the gate
 *   reads as off (the `fm` row of `audio/ref/off-values.md`; a non-finite value reads as unset, so off too), so an
 *   unwritten `fm.depth` builds no stage, and a call that writes only the ratio or the envelope (`fm(ratio = 2)`)
 *   switches nothing on, as on the retired strip.
 * @property attack the depth envelope's attack in seconds; mirrors `fm.attack`. Default `FM_ENV_ATTACK_SEC` (0.0).
 * @property decay the depth envelope's decay in seconds; mirrors `fm.decay`. Default `FM_ENV_DECAY_SEC` (0.0).
 * @property sustain the depth envelope's held share of [depth]; mirrors `fm.sustain`. Default `FM_ENV_SUSTAIN_LEVEL` (1.0).
 * @property release the depth envelope's release in seconds, from the gate; mirrors `fm.release` (decision D3: new in
 *   step 4, the strip had none). Default `FM_ENV_RELEASE_SEC` (0.0).
 *
 * The defaults are the Ignitor `fm` node's own, from the same constants (`constants/PitchModDefaults.kt`), which are the
 * values the strip read for an unwritten stage. With all four envelope stages at their defaults the node runs NO envelope: the depth is full
 * from the onset through the whole release tail. The strip always ran its envelope with a release of 0, so its FM
 * collapsed to 0 at the first block from the gate on (block-framing ledger E10, E11); the stage does not (the fifth
 * non-identical cause of step 4, `docs/tasks/pitch-pipeline-into-the-tree.md`).
 */
class FmSlots internal constructor() {
    val ratio: IgnitorDsl = slot(door = "fm", param = "ratio", default = FM_RATIO)
    val depth: IgnitorDsl = slot(door = "fm", param = "depth", default = 0.0)
    val attack: IgnitorDsl = slot(door = "fm", param = "attack", default = FM_ENV_ATTACK_SEC)
    val decay: IgnitorDsl = slot(door = "fm", param = "decay", default = FM_ENV_DECAY_SEC)
    val sustain: IgnitorDsl = slot(door = "fm", param = "sustain", default = FM_ENV_SUSTAIN_LEVEL)
    val release: IgnitorDsl = slot(door = "fm", param = "release", default = FM_ENV_RELEASE_SEC)
}

/**
 * The playback slots of the SAMPLE instrument (`Slots.sample`), the four sample doors of sprudel: `begin(pos)`,
 * `end(pos)`, `speed(rate)` and `loop(flag)` (phase 3 step 8). Flat names, like `onepole`: each door has one knob,
 * and the key is the door's own name. Not `classic()` slots: the engine reads them where it builds the sample's
 * playhead, before any tree, and there an UNSET `begin` or `end` differs from any number (it decides whether the
 * sample's own loop applies), so [begin] and [end] default to unset. [speed] defaults to 1.0, [loop] to 0.0 (off).
 */
class SampleSlots internal constructor() {
    val begin: IgnitorDsl = IgnitorDsl.Param(name = "begin", default = SLOT_UNSET, description = "Mirrors sprudel's reader `begin`")
    val end: IgnitorDsl = IgnitorDsl.Param(name = "end", default = SLOT_UNSET, description = "Mirrors sprudel's reader `end`")
    val speed: IgnitorDsl = IgnitorDsl.Param(name = "speed", default = 1.0, description = "Mirrors sprudel's reader `speed`")
    val loop: IgnitorDsl = IgnitorDsl.Param(
        name = "loop",
        default = 0.0,
        description = "The sample's loop switch, 0 is off; what sprudel's `loop(flag)` writes",
    )
}

/**
 * Wraps this signal in the voice chain the old voice strip ran, stage for stage: the subtractive synth voice that
 * `sound("saw")` has always been, as a plain function over the node type (`docs/plans/signal-flow-redesign.md`
 * section 5). The script door is `x.classic()`, and it calls this function: THIS is the one place the
 * order is written.
 *
 * ```
 * this -> fm -> pitchEnvelope -> accelerate -> vibrato -> onepole -> crush -> coarse -> distort -> highpass -> bandpass -> notch -> lowpass -> tremolo -> adsr
 * ```
 *
 * The PITCH stages come first, directly on the instrument (pitch pipeline, `docs/tasks/pitch-pipeline-into-the-tree.md`
 * section 2): their mods bubble down to every pitched source, so their place among the amplitude stages does not
 * change the sound, and the nesting decides the grouping of the ratio product, which is the retired pitch strip's
 * (vibrato outermost, then accelerate, then the pitch envelope, FM innermost: `((V * A) * P) * F`). The amplitude
 * stages are the retired strip's order with the canonical filter sub-order of
 * `SprudelVoiceData.toVoiceData`, behind the pattern's `onepole`, which sat on the source in front of the
 * strip. Every knob is a slot of [IgnitorDsl.Slots] (the groups above), so a
 * pattern FILLS it and never adds structure, and every unwritten stage is NOT BUILT: its slot's default
 * is the stage's off value in the gate's table (`audio/ref/off-values.md`), so an
 * unwritten `classic()` builds exactly one stage, the envelope, at the voice envelope's defaults.
 *
 * What it deliberately does NOT contain: `pregain`. An instrument places `.pregain()` where the player's
 * touch enters (section 5 of the plan).
 *
 * Every voice is its tree (the voice strip retired in phase 3 step 9, the pitch strip's last door in pitch pipeline
 * step 4): the engine adds nothing around it but the teardown fade unless the root is a built envelope with a static release
 * (`BuiltIgnitor.endsInEnvelope`), and the channel. Every built-in sound is
 * `source.pregain().classic()` (since step 6); an authored instrument gets the voice chain by appending
 * `.classic()` as its LAST call ([endsInClassic]). An instrument without it is played as its bare tree, and a
 * tree with `classic()` below its root (`a.classic().plus(b.classic())`) gets the doors per branch.
 *
 * Per stage, what each node is and how it relates to the retired strip (proven bit-identical row by row
 * before the strip was deleted; their frozen fingerprints were retired at the phase 3 end checkpoint, 2026-10-02):
 *  - the crush renders the strip's own `floor` quantizer, one shared law (decision D1, landed in step 4).
 *    It has no oversampler, so `crush(oversample = ...)` does nothing (tracked in
 *    `docs/tasks/oversampling-regions.md`);
 *  - the distort is the fused [IgnitorDsl.Distort] node, the one that switches drive AND shape off as a
 *    unit (`Shape(Drive(...))` would put the shaper on every voice). Decision D2 (option A, landed in
 *    step 4): the node renders the strip's loop (no soft cap, the drive inside the oversampler), so it
 *    is bit-identical, while the `distort`/`shape` doors keep their capped law;
 *  - the four filters humanize from the voice's `analog` slot, as the strip did, but draw per filter
 *    where the strip drew every tolerance first (the section 8 migration cost); their cutoff
 *    envelopes are the strip's since D3 (one law, the block interpolation, and the default curve
 *    `MOD_ENV_CURVE` on both), and their curves are the `<door>Curves` slots since step 5b (c2);
 *  - the FM is the Ignitor `fm` node, filled by sprudel's `fm` through the `fm.*` slots (pitch pipeline step 4), its
 *    modulator a sine at `analog` 0 (the strip's modulator never drifted; an unset `Sine` would read the `analog`
 *    slot). Innermost, so the classic pitch stages above it move the whole operator, carrier and modulator (decision
 *    D1, the placement rule of step 3b); an `fm` in the instrument sits inside its carrier and its modulator follows
 *    the classic FM too (`sgbell`). NOT the strip's sound, by decision D3 (the node's law): the depth envelope runs
 *    per sample where the strip held it per block (ledger E11 closed); an envelope-free door (every stage at its
 *    default: attack 0, decay 0, sustain 1 or more, release 0; the node decides by value, not by what was written)
 *    keeps its depth through the release tail, where the strip collapsed it to 0 at the
 *    first block from the gate on; the rounding order (at most 7.6e-15 at the output before the gate, measured); the
 *    modulator sine seeds its drift lane from the voice's random stream on its first block, so a pitched voice that
 *    also draws while it renders (a supersaw's or a pluck's dice, `analog` above 0) takes other random values, the
 *    same statistics (a noise-only voice never renders the modulator and keeps its draws); the modulator now follows
 *    `vib`, `penv` and `accelerate`; over a forking `detune` (`sgpad`, the only built-in with one) the one modulator
 *    serves both pitches and jumps a whole block per pitch, so the sound depends on the block size and the pad loses
 *    its pitch (the author rule's shape, step 3b; ledger E8; recorded, kept quiet). A non-finite knob is dropped at sprudel's boundary and reads as unset (a
 *    non-finite depth: no FM; a non-finite ratio: ratio 1);
 *  - the pitch envelope is the Ignitor `pitchEnvelope` node, the law the retired pitch strip shared with it
 *    (`renderPitchEnvelopeRatios`), so sprudel's `penv` renders the strip's bits (pitch pipeline step 1); an `fm`
 *    node in the instrument moves under it as one operator, carrier and modulator, as on the strip (decision D1,
 *    pitch pipeline step 3b). Except where the plan accepts a difference: a musical oscillator in a parameter
 *    position (a filter LFO) stays unbent for good
 *    (plan section 2); three pitch factors on one path regroup the product (one rounding, about -270 dB), for good
 *    where two of the instrument's own pitch nodes meet a door; an instrument without `classic()` ignores the
 *    door (D6);
 *  - the vibrato is the Ignitor `vibrato` node, filled by sprudel's `vib` through the `vibrato.*` slots (pitch pipeline
 *    step 2), with the same accepted differences as the pitch envelope. Since pitch pipeline 7b the node is composed:
 *    for sprudel's per-event constant depth it is the strip's law to one rounding, but its sine's drift-seed draw gives
 *    a voice that also draws while it renders (a supersaw, a noise layer, `analog > 0`) other dice
 *    (`audio/ref/voice-synthesis.md`);
 *  - the accelerate is the Ignitor `accelerate` node, filled by sprudel's `accelerate` through the flat `accelerate`
 *    slot (pitch pipeline step 3). NOT the strip's sound, by decision D2: the glide spans the GATE (onset to gate
 *    close) and holds its target through the release, where the strip glided over the scheduled end, the release
 *    tail included (a sound change for every `accelerate` voice with a release tail; in the corpus only Kokon's
 *    `strike`), and a gate of 0 frames (`legato(0)`) holds the target from the first frame where the strip glided over
 *    the release (Q27). A non-finite amount is dropped at sprudel's boundary and plays the bare voice, where the strip
 *    froze the oscillator; from about 598 semitones the ratio is clamped at `SAFE_MAX`. Beyond that, the pitch
 *    envelope's accepted differences;
 *  - the envelope evaluates the one envelope law (`EnvelopeCore`, shared with the old strip VCA since
 *    phase 3 D3), and its de-click is the constant `ENV_DECLICK_SECONDS`, not a slot: no door writes the
 *    de-click per note.
 *
 * No arguments and no configure lambda: an author who wants another order writes their own tail from
 * the same slots, and the same doors fill it.
 */
fun IgnitorDsl.classic(): IgnitorDsl {
    val s = IgnitorDsl.Slots

    // The pitch stages, at the front: mods bubble to the pitched sources, so their place among the amplitude stages
    // does not change the sound, and the root stays the envelope (`endsInClassic`).
    // FM innermost (pitch pipeline step 4): the pitch stages above it bend carrier and modulator together (D1).
    val fmed = IgnitorDsl.Fm(
        carrier = this,
        // The strip's modulator never drifted: an unset `Sine` reads the `analog` slot.
        modulator = IgnitorDsl.Sine(analog = IgnitorDsl.Constant(0.0)),
        ratio = s.fm.ratio,
        depth = s.fm.depth,
        attack = s.fm.attack,
        decay = s.fm.decay,
        sustain = s.fm.sustain,
        release = s.fm.release,
    )
    val pitchEnveloped = IgnitorDsl.PitchEnvelope(
        inner = fmed,
        semitones = s.penv.semitones,
        attack = s.penv.attack,
        decay = s.penv.decay,
        sustain = s.penv.sustain,
        release = s.penv.release,
        attackCurve = s.penvCurves.attack,
        decayCurve = s.penvCurves.decay,
        releaseCurve = s.penvCurves.release,
    )
    // Accelerate between the pitch envelope and the vibrato, its final place (pitch pipeline step 3).
    val accelerated = IgnitorDsl.Accelerate(inner = pitchEnveloped, semitones = s.accelerate)
    // The vibrato OUTSIDE, its final place: the outer mod is combined first, so the product groups as the strip's
    // `(V * A) * P` (pitch pipeline steps 2 and 3).
    val vibratoed = IgnitorDsl.Vibrato(
        inner = accelerated,
        rate = s.vibrato.rate,
        semitones = s.vibrato.semitones,
        rangeFrom = s.vibrato.rangeFrom,
        rangeTo = s.vibrato.rangeTo,
        phase = s.vibrato.phase,
    )
    val onepoled = IgnitorDsl.OnePoleLowpass(inner = vibratoed, freq = s.onepole)
    val crushed = IgnitorDsl.Crush(inner = onepoled, bits = s.crush.bits)
    val coarsened = IgnitorDsl.Coarse(inner = crushed, factor = s.coarse.factor)
    val distorted = IgnitorDsl.Distort(
        inner = coarsened,
        amount = s.distort.amount,
        shape = s.distort.shape,
        oversample = s.distort.oversample,
    )
    val highpassed = IgnitorDsl.Highpass(
        inner = distorted,
        freq = s.hpf.freq,
        q = s.hpf.q,
        analog = s.analog,
        passes = s.hpf.passes,
        env = s.hpf.env,
        attack = s.hpf.attack,
        decay = s.hpf.decay,
        sustain = s.hpf.sustain,
        release = s.hpf.release,
        attackCurve = s.hpfCurves.attack,
        decayCurve = s.hpfCurves.decay,
        releaseCurve = s.hpfCurves.release,
        humanize = true,
    )
    val bandpassed = IgnitorDsl.Bandpass(
        inner = highpassed,
        freq = s.bpf.freq,
        q = s.bpf.q,
        analog = s.analog,
        env = s.bpf.env,
        attack = s.bpf.attack,
        decay = s.bpf.decay,
        sustain = s.bpf.sustain,
        release = s.bpf.release,
        attackCurve = s.bpfCurves.attack,
        decayCurve = s.bpfCurves.decay,
        releaseCurve = s.bpfCurves.release,
        humanize = true,
    )
    val notched = IgnitorDsl.Notch(
        inner = bandpassed,
        freq = s.notch.freq,
        q = s.notch.q,
        analog = s.analog,
        env = s.notch.env,
        attack = s.notch.attack,
        decay = s.notch.decay,
        sustain = s.notch.sustain,
        release = s.notch.release,
        attackCurve = s.notchCurves.attack,
        decayCurve = s.notchCurves.decay,
        releaseCurve = s.notchCurves.release,
        humanize = true,
    )
    val lowpassed = IgnitorDsl.Lowpass(
        inner = notched,
        freq = s.lpf.freq,
        q = s.lpf.q,
        analog = s.analog,
        passes = s.lpf.passes,
        env = s.lpf.env,
        attack = s.lpf.attack,
        decay = s.lpf.decay,
        sustain = s.lpf.sustain,
        release = s.lpf.release,
        attackCurve = s.lpfCurves.attack,
        decayCurve = s.lpfCurves.decay,
        releaseCurve = s.lpfCurves.release,
        humanize = true,
    )
    val tremoloed = IgnitorDsl.Tremolo(
        inner = lowpassed,
        rate = s.tremolo.rate,
        depth = s.tremolo.depth,
        shape = s.tremolo.shape,
    )

    return IgnitorDsl.Adsr(
        inner = tremoloed,
        attack = s.adsr.attack,
        decay = s.adsr.decay,
        sustain = s.adsr.sustain,
        release = s.adsr.release,
        attackCurve = s.adsrCurves.attack,
        decayCurve = s.adsrCurves.decay,
        releaseCurve = s.adsrCurves.release,
        declick = IgnitorDsl.Constant(ENV_DECLICK_SECONDS),
        on = s.adsr.on,
    )
}

/**
 * True when this tree ENDS in [classic]: its ROOT is `classic()`'s envelope, recognised by its switch, the slot
 * `adsr.on`. `classic()` is the one place that slot is placed (no door has the switch), so the root's switch IS
 * the tag, and no marker node or wire field is needed (phase 3 step 10, `docs/tasks-archive/2026-09/20260928-builtin-instruments.md`).
 *
 * Such a tree carries the voice chain, so the voice doors reach it. Every built-in sound is one, and so is an
 * authored instrument whose LAST call is `.classic()`. The engine no longer asks (every voice is its tree since
 * phase 3 step 9); the tag stays for the editor and the future auto-attach below.
 *
 * It compares the switch by NAME: a registered tree reaches the engine through the wire codec, which builds new
 * `Param` instances.
 *
 * It reads the ROOT only, so `classic()` must be the last call: `x.classic().mul(0.5)` does not end in it. An optimizer hint is not a stage, so it looks through one at the root:
 * `x.classic().optimizer(0)` (the by-ear A/B, which its KDoc says to put last on a sound) ends in `classic()`,
 * and switching the optimizer off does not change the answer. This is the tag the `.sprudel()` auto-attach
 * of `docs/plans/future/signal-graph-engine.md` asks for: "does this instrument carry the voice chain yet?".
 */
fun IgnitorDsl.endsInClassic(): Boolean {
    var root = this

    while (root is IgnitorDsl.OptimizerHint) {
        root = root.inner
    }

    // The name is read from the slot, never retyped. Not a top-level val: `Slots` reads this file's own vals
    // while it initializes, so a val here that reads `Slots` back would see it half built on the JVM.
    return root is IgnitorDsl.Adsr && (root.on as? IgnitorDsl.Param)?.name == (IgnitorDsl.Slots.adsr.on as IgnitorDsl.Param).name
}
