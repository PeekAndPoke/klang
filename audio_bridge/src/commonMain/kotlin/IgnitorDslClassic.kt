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
import io.peekandpoke.klang.audio_bridge.constants.MOD_ENV_CURVE
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET
import io.peekandpoke.klang.audio_bridge.constants.VOICE_ADSR_ATTACK_SEC
import io.peekandpoke.klang.audio_bridge.constants.VOICE_ADSR_DECAY_SEC
import io.peekandpoke.klang.audio_bridge.constants.VOICE_ADSR_RELEASE_SEC
import io.peekandpoke.klang.audio_bridge.constants.VOICE_ADSR_SUSTAIN_LEVEL

// ═════════════════════════════════════════════════════════════════════════════════════════════
// `classic()`: the voice strip's chain as a tail of slotted Ignitor stages (phase 3 step 5; the strip retired in step 9)
//
// The plan is `docs/plans/signal-flow-redesign.md` section 5 and `docs/tasks-archive/2026-09/20260928-builtin-instruments.md`.
// The SLOTS below are grouped per stage and named `<door>.<param>`, which is sprudel's own reader
// vocabulary (`lpf.freq`, `adsr.attack`, ...) and the `<stage>.<knob>` rule of the Katalyst's classic
// chain. Each slot's KDoc names the sprudel reader it mirrors, and sprudel's `toVoiceData` writes exactly
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
    val freq: IgnitorDsl = slot(door, "freq", SLOT_UNSET)
    val q: IgnitorDsl = slot(door, "q", 0.707)
    val passes: IgnitorDsl = slot(door, "passes", 1.0)
    val env: IgnitorDsl = slot(door, "env", SLOT_UNSET)
    val attack: IgnitorDsl = slot(door, "attack", FILTER_ENV_ATTACK_SEC)
    val decay: IgnitorDsl = slot(door, "decay", FILTER_ENV_DECAY_SEC)
    val sustain: IgnitorDsl = slot(door, "sustain", FILTER_ENV_SUSTAIN_LEVEL)
    val release: IgnitorDsl = slot(door, "release", FILTER_ENV_RELEASE_SEC)
}

/**
 * The slots of a bandpass or a notch stage (`Slots.bpf`, `Slots.notch`): [PassFilterSlots] without
 * `passes`, mirroring sprudel's `bpf(freq, q, env, attack, decay, sustain, release)` and its readers
 * (`notch` the same). Defaults as there.
 */
class BandFilterSlots internal constructor(door: String) {
    val freq: IgnitorDsl = slot(door, "freq", SLOT_UNSET)
    val q: IgnitorDsl = slot(door, "q", 0.707)
    val env: IgnitorDsl = slot(door, "env", SLOT_UNSET)
    val attack: IgnitorDsl = slot(door, "attack", FILTER_ENV_ATTACK_SEC)
    val decay: IgnitorDsl = slot(door, "decay", FILTER_ENV_DECAY_SEC)
    val sustain: IgnitorDsl = slot(door, "sustain", FILTER_ENV_SUSTAIN_LEVEL)
    val release: IgnitorDsl = slot(door, "release", FILTER_ENV_RELEASE_SEC)
}

/**
 * The one slot of a crush or a coarse stage (`Slots.crush`, `Slots.coarse`), mirroring sprudel's
 * `crush.amount` / `coarse.amount`. Default 0.0, the strip's untouched amount, which the gate reads
 * as off. Sprudel's `oversample` of these two has no slot: it moved to `oversampling-regions.md`.
 */
class AmountSlots internal constructor(door: String) {
    val amount: IgnitorDsl = slot(door, "amount", 0.0)
}

/**
 * The slots of the distort stage (`Slots.distort`), mirroring sprudel's `distort(amount, shape, oversample)`:
 * `distort.amount` (default 0.0, off), `distort.shape` (an INDEX into [DistortionShapes], default
 * `soft`; sprudel's name has no reader, the slot carries its index) and `distort.oversample` (the
 * factor, default 0, no oversampler; the D7 stopgap, read at voice build).
 */
class DistortSlots internal constructor() {
    val amount: IgnitorDsl = slot("distort", "amount", 0.0)
    val shape: IgnitorDsl = slot(
        "distort", "shape", DistortionShapes.SOFT_INDEX.toDouble(),
        "The waveshaper as its index in the shape list; what sprudel's `distort(shape = ...)` names",
    )
    val oversample: IgnitorDsl = slot("distort", "oversample", 0.0)
}

/**
 * The slots of the tremolo stage (`Slots.tremolo`), mirroring sprudel's
 * `tremolo(depth, sync, shape)`: `tremolo.depth` (default 0.0, off), `tremolo.sync` (the
 * RATE in Hz; default 0.0, the strip's untouched rate, NOT the node's 5.0) and `tremolo.shape` (an
 * INDEX into [LfoShapes], default `sine`; no sprudel reader, the name is a string there).
 */
class TremoloSlots internal constructor() {
    val depth: IgnitorDsl = slot("tremolo", "depth", 0.0)
    val sync: IgnitorDsl = slot("tremolo", "sync", 0.0)
    val shape: IgnitorDsl = slot(
        "tremolo", "shape", LfoShapes.SINE_INDEX.toDouble(),
        "The LFO shape as its index in the shape list; what sprudel's `tremolo(shape = ...)` names",
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
    val attack: IgnitorDsl = slot("adsr", "attack", VOICE_ADSR_ATTACK_SEC)
    val decay: IgnitorDsl = slot("adsr", "decay", VOICE_ADSR_DECAY_SEC)
    val sustain: IgnitorDsl = slot("adsr", "sustain", VOICE_ADSR_SUSTAIN_LEVEL)
    val release: IgnitorDsl = slot("adsr", "release", VOICE_ADSR_RELEASE_SEC)
    val on: IgnitorDsl = slot("adsr", "on", 1.0, "The envelope's switch, 0 is off; what sprudel's `adsrOn()` / `adsrOff()` write")
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
    val attack: IgnitorDsl = slot("adsrCurves", "attack", AdsrCurves.indexOf(AdsrCurve.Default), CURVE_SLOT_DESCRIPTION)
    val decay: IgnitorDsl = slot("adsrCurves", "decay", AdsrCurves.indexOf(AdsrCurve.Default), CURVE_SLOT_DESCRIPTION)
    val release: IgnitorDsl = slot("adsrCurves", "release", AdsrCurves.indexOf(AdsrCurve.Default), CURVE_SLOT_DESCRIPTION)
}

/**
 * The curve slots of a filter's cutoff envelope (`Slots.lpfCurves`, `Slots.hpfCurves`, `Slots.bpfCurves`,
 * `Slots.notchCurves`), mirroring sprudel's `lpfCurves(attack, decay, release)` and its three siblings
 * (phase 3 step 5b (c2)): `<door>Curves.attack|decay|release`, each an INDEX into [AdsrCurves] (sprudel
 * writes names, and the curves objects have no readers), default the index of `MOD_ENV_CURVE`, the curve
 * every modulation envelope has when nothing is written (decision D3; the defaults' one home is
 * `constants/EnvelopeDefaults.kt`).
 */
class FilterCurvesSlots internal constructor(door: String) {
    private val description = curveSlotDescription(door)

    val attack: IgnitorDsl = slot(door, "attack", AdsrCurves.indexOf(MOD_ENV_CURVE), description)
    val decay: IgnitorDsl = slot(door, "decay", AdsrCurves.indexOf(MOD_ENV_CURVE), description)
    val release: IgnitorDsl = slot(door, "release", AdsrCurves.indexOf(MOD_ENV_CURVE), description)
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
 * onepole -> crush -> coarse -> distort -> highpass -> bandpass -> notch -> lowpass -> tremolo -> adsr
 * ```
 *
 * That is the retired strip's order with the canonical filter sub-order of
 * `SprudelVoiceData.toVoiceData`, behind the pattern's `onepole`, which sat on the source in front of the
 * strip. Every knob is a slot of [IgnitorDsl.Slots] (the groups above), so a
 * pattern FILLS it and never adds structure, and every unwritten stage is NOT BUILT: its slot's default
 * is the stage's off value in the gate's table (`audio/ref/off-values.md`), so an
 * unwritten `classic()` builds exactly one stage, the envelope, at the voice envelope's defaults.
 *
 * What it deliberately does NOT contain: `pregain`. An instrument places `.pregain()` where the player's
 * touch enters (section 5 of the plan).
 *
 * Every voice is its tree (the voice strip retired in phase 3 step 9): the engine adds nothing around it but the
 * pitch pipeline, the teardown fade unless the root is a built envelope with a static release
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
 *  - the envelope evaluates the one envelope law (`EnvelopeCore`, shared with the old strip VCA since
 *    phase 3 D3), and its de-click is the constant `ENV_DECLICK_SECONDS`, not a slot: no door writes the
 *    de-click per note.
 *
 * No arguments and no configure lambda: an author who wants another order writes their own tail from
 * the same slots, and the same doors fill it.
 */
fun IgnitorDsl.classic(): IgnitorDsl {
    val s = IgnitorDsl.Slots

    val onepoled = IgnitorDsl.OnePoleLowpass(inner = this, freq = s.onepole)
    val crushed = IgnitorDsl.Crush(inner = onepoled, amount = s.crush.amount)
    val coarsened = IgnitorDsl.Coarse(inner = crushed, amount = s.coarse.amount)
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
        attackSec = s.hpf.attack,
        decaySec = s.hpf.decay,
        sustainLevel = s.hpf.sustain,
        releaseSec = s.hpf.release,
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
        attackSec = s.bpf.attack,
        decaySec = s.bpf.decay,
        sustainLevel = s.bpf.sustain,
        releaseSec = s.bpf.release,
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
        attackSec = s.notch.attack,
        decaySec = s.notch.decay,
        sustainLevel = s.notch.sustain,
        releaseSec = s.notch.release,
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
        attackSec = s.lpf.attack,
        decaySec = s.lpf.decay,
        sustainLevel = s.lpf.sustain,
        releaseSec = s.lpf.release,
        attackCurve = s.lpfCurves.attack,
        decayCurve = s.lpfCurves.decay,
        releaseCurve = s.lpfCurves.release,
        humanize = true,
    )
    val tremoloed = IgnitorDsl.Tremolo(
        inner = lowpassed,
        rate = s.tremolo.sync,
        depth = s.tremolo.depth,
        shape = s.tremolo.shape,
    )

    return IgnitorDsl.Adsr(
        inner = tremoloed,
        attackSec = s.adsr.attack,
        decaySec = s.adsr.decay,
        sustainLevel = s.adsr.sustain,
        releaseSec = s.adsr.release,
        attackCurve = s.adsrCurves.attack,
        decayCurve = s.adsrCurves.decay,
        releaseCurve = s.adsrCurves.release,
        declickSeconds = IgnitorDsl.Constant(ENV_DECLICK_SECONDS),
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
