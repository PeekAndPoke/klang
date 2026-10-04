/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.peekandpoke.klang.audio_bridge.constants.BODY_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.DELAY_CAP
import io.peekandpoke.klang.audio_bridge.constants.DUCK_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.PHASER_CENTER_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.PHASER_RATE_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_SWEEP_HZ
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_FLOOR

// ═════════════════════════════════════════════════════════════════════════════════════════════
// The knobs of the classic chain, one group per stage: `KatalystDsl.Slots.reverb.wet` (the Ignitor/Katalyst naming,
// `docs/plans/ignitor-katalyst-naming.md` section 4.3).
//
// The twin of `IgnitorDslClassic.kt`'s groups on the other host: every knob is named `<stage>.<knob>`, which is the
// vocabulary `katp` and the sprudel bus doors write, and [KatalystDsl.classic] is built FROM these groups, so the chain
// and the handles a song names can never drift. The defaults, and why each one is what it is (zero, unset, the shared
// constant, unity), are documented once, in the KDoc of [KatalystDsl.classic].
// ═════════════════════════════════════════════════════════════════════════════════════════════

/**
 * A named knob of a Katalyst chain: what `katp` writes. `Katalyst.param(...)` makes one, `Katalyst.slot.*` holds the
 * classic chain's.
 *
 * A HANDLE, never on the wire: a chain holds [param], the plain [IgnitorDsl.Param] the engine reads. The separate type
 * is a frontend distinction the engine does not need: the engine reads ANY `Param` on a chain from the orbit's `katp`
 * state by name. The type is what lets the doors tell the author's intent apart: an Ignitor param handed to a
 * Katalyst knob, or a Katalyst param handed to `ignp`, is a script error at the call (the plan's decisions Q2 and Q3,
 * 2026-10-03; the reason in full is on `toKatalystKnob` in `klangscript-libs`). It is not an [IgnitorDsl], so it has
 * no arithmetic: a knob listens to `katp` only when it IS the slot.
 *
 * @property param the slot the chain carries: its name and its default.
 */
data class KatalystParam(val param: IgnitorDsl.Param) {
    /** The slot name, `<stage>.<knob>` for a classic knob: what `katp` writes. */
    val name: String get() = param.name
}

/** The one constructor of this file: the knob `<stage>.<knob>` with its default (no description, as the chain always had). */
private fun knob(stage: String, knob: String, default: Double): KatalystParam =
    KatalystParam(IgnitorDsl.Param(name = "$stage.$knob", default = default))

/** The body stage's knobs (`Slots.body`): `body.material` (an INDEX into [BodyMaterials], unset), `body.wet` (unset), `body.floor`. */
class KatalystBodySlots internal constructor() {
    val material: KatalystParam = knob("body", "material", SLOT_UNSET)
    val wet: KatalystParam = knob("body", "wet", SLOT_UNSET)
    val floor: KatalystParam = knob("body", "floor", BODY_FLOOR)
}

/** The vowel stage's knobs (`Slots.vowel`): `vowel.vowel` (an INDEX into [VowelBands], unset), `vowel.wet` (unset), `vowel.floor`. */
class KatalystVowelSlots internal constructor() {
    val vowel: KatalystParam = knob("vowel", "vowel", SLOT_UNSET)
    val wet: KatalystParam = knob("vowel", "wet", SLOT_UNSET)
    val floor: KatalystParam = knob("vowel", "floor", VOWEL_FLOOR)
}

/** The delay stage's knobs (`Slots.delay`): `delay.wet`, `delay.time`, `delay.feedback` (all 0.0, off), `delay.cap`. */
class KatalystDelaySlots internal constructor() {
    val wet: KatalystParam = knob("delay", "wet", 0.0)
    val time: KatalystParam = knob("delay", "time", 0.0)
    val feedback: KatalystParam = knob("delay", "feedback", 0.0)
    val cap: KatalystParam = knob("delay", "cap", DELAY_CAP)
}

/** The reverb stage's knobs (`Slots.reverb`): `reverb.wet`, `reverb.size` (both 0.0, off), `reverb.lowpass` (unset). */
class KatalystReverbSlots internal constructor() {
    val wet: KatalystParam = knob("reverb", "wet", 0.0)
    val size: KatalystParam = knob("reverb", "size", 0.0)
    val lowpass: KatalystParam = knob("reverb", "lowpass", SLOT_UNSET)
}

/** The phaser stage's knobs (`Slots.phaser`): `phaser.rate`, `phaser.wet` (0.0, off), `phaser.center`, `phaser.sweep`, `phaser.floor`. */
class KatalystPhaserSlots internal constructor() {
    val rate: KatalystParam = knob("phaser", "rate", PHASER_RATE_HZ)
    val wet: KatalystParam = knob("phaser", "wet", 0.0)
    val center: KatalystParam = knob("phaser", "center", PHASER_CENTER_HZ)
    val sweep: KatalystParam = knob("phaser", "sweep", PHASER_SWEEP_HZ)
    val floor: KatalystParam = knob("phaser", "floor", PHASER_FLOOR)
}

/**
 * The compressor stage's knobs (`Slots.compressor`): `compressor.threshold`, `.ratio`, `.knee`, `.attack`, `.release`,
 * all five unset, because the engine's gate is "any of the five set" (see [KatalystDsl.classic]).
 */
class KatalystCompressorSlots internal constructor() {
    val threshold: KatalystParam = knob("compressor", "threshold", SLOT_UNSET)
    val ratio: KatalystParam = knob("compressor", "ratio", SLOT_UNSET)
    val knee: KatalystParam = knob("compressor", "knee", SLOT_UNSET)
    val attack: KatalystParam = knob("compressor", "attack", SLOT_UNSET)
    val release: KatalystParam = knob("compressor", "release", SLOT_UNSET)
}

/** The group fader's knob (`Slots.gain`): `gain.gain`, at unity. It reads oddly and is right: the stage `gain`, its knob `gain`. */
class KatalystGainSlots internal constructor() {
    val gain: KatalystParam = knob("gain", "gain", 1.0)
}

/** The duck stage's knobs (`Slots.duck`): `duck.orbit` (unset, no source), `duck.depth` (0.0), `duck.attack`. */
class KatalystDuckSlots internal constructor() {
    val orbit: KatalystParam = knob("duck", "orbit", SLOT_UNSET)
    val depth: KatalystParam = knob("duck", "depth", 0.0)
    val attack: KatalystParam = knob("duck", "attack", DUCK_ATTACK_SECONDS)
}
