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
 * The one constructor of this file: the knob `<stage>.<knob>` with its default (no description, as the chain always
 * had). A [KatalystKnobDsl.Param], the wire node the chain carries and the object `katp` takes, so the handle a song
 * names and the knob the chain reads are one value (decision Q1 of
 * `docs/tasks-archive/2026-10/20261012-katalyst-knob-expressions.md`: the
 * separate `KatalystParam` handle is retired, the type split is the wire's own).
 */
private fun knob(stage: String, knob: String, default: Double): KatalystKnobDsl.Param =
    KatalystKnobDsl.Param(name = "$stage.$knob", default = default)

/** The body stage's knobs (`Slots.body`): `body.material` (an INDEX into [BodyMaterials], unset), `body.wet` (unset), `body.floor`. */
class KatalystBodySlots internal constructor() {
    val material: KatalystKnobDsl.Param = knob(stage = "body", knob = "material", default = SLOT_UNSET)
    val wet: KatalystKnobDsl.Param = knob(stage = "body", knob = "wet", default = SLOT_UNSET)
    val floor: KatalystKnobDsl.Param = knob(stage = "body", knob = "floor", default = BODY_FLOOR)
}

/** The vowel stage's knobs (`Slots.vowel`): `vowel.vowel` (an INDEX into [VowelBands], unset), `vowel.wet` (unset), `vowel.floor`. */
class KatalystVowelSlots internal constructor() {
    val vowel: KatalystKnobDsl.Param = knob(stage = "vowel", knob = "vowel", default = SLOT_UNSET)
    val wet: KatalystKnobDsl.Param = knob(stage = "vowel", knob = "wet", default = SLOT_UNSET)
    val floor: KatalystKnobDsl.Param = knob(stage = "vowel", knob = "floor", default = VOWEL_FLOOR)
}

/** The delay stage's knobs (`Slots.delay`): `delay.wet`, `delay.time`, `delay.feedback` (all 0.0, off), `delay.cap`. */
class KatalystDelaySlots internal constructor() {
    val wet: KatalystKnobDsl.Param = knob(stage = "delay", knob = "wet", default = 0.0)
    val time: KatalystKnobDsl.Param = knob(stage = "delay", knob = "time", default = 0.0)
    val feedback: KatalystKnobDsl.Param = knob(stage = "delay", knob = "feedback", default = 0.0)
    val cap: KatalystKnobDsl.Param = knob(stage = "delay", knob = "cap", default = DELAY_CAP)
}

/** The reverb stage's knobs (`Slots.reverb`): `reverb.wet`, `reverb.size` (both 0.0, off), `reverb.lowpass` (unset). */
class KatalystReverbSlots internal constructor() {
    val wet: KatalystKnobDsl.Param = knob(stage = "reverb", knob = "wet", default = 0.0)
    val size: KatalystKnobDsl.Param = knob(stage = "reverb", knob = "size", default = 0.0)
    val lowpass: KatalystKnobDsl.Param = knob(stage = "reverb", knob = "lowpass", default = SLOT_UNSET)
}

/** The phaser stage's knobs (`Slots.phaser`): `phaser.rate`, `phaser.wet` (0.0, off), `phaser.center`, `phaser.sweep`, `phaser.floor`. */
class KatalystPhaserSlots internal constructor() {
    val rate: KatalystKnobDsl.Param = knob(stage = "phaser", knob = "rate", default = PHASER_RATE_HZ)
    val wet: KatalystKnobDsl.Param = knob(stage = "phaser", knob = "wet", default = 0.0)
    val center: KatalystKnobDsl.Param = knob(stage = "phaser", knob = "center", default = PHASER_CENTER_HZ)
    val sweep: KatalystKnobDsl.Param = knob(stage = "phaser", knob = "sweep", default = PHASER_SWEEP_HZ)
    val floor: KatalystKnobDsl.Param = knob(stage = "phaser", knob = "floor", default = PHASER_FLOOR)
}

/**
 * The compressor stage's knobs (`Slots.compressor`): `compressor.threshold`, `.ratio`, `.knee`, `.attack`, `.release`,
 * all five unset, because the engine's gate is "any of the five set" (see [KatalystDsl.classic]).
 */
class KatalystCompressorSlots internal constructor() {
    val threshold: KatalystKnobDsl.Param = knob(stage = "compressor", knob = "threshold", default = SLOT_UNSET)
    val ratio: KatalystKnobDsl.Param = knob(stage = "compressor", knob = "ratio", default = SLOT_UNSET)
    val knee: KatalystKnobDsl.Param = knob(stage = "compressor", knob = "knee", default = SLOT_UNSET)
    val attack: KatalystKnobDsl.Param = knob(stage = "compressor", knob = "attack", default = SLOT_UNSET)
    val release: KatalystKnobDsl.Param = knob(stage = "compressor", knob = "release", default = SLOT_UNSET)
}

/** The group fader's knob (`Slots.gain`): `gain.gain`, at unity. It reads oddly and is right: the stage `gain`, its knob `gain`. */
class KatalystGainSlots internal constructor() {
    val gain: KatalystKnobDsl.Param = knob(stage = "gain", knob = "gain", default = 1.0)
}

/** The duck stage's knobs (`Slots.duck`): `duck.orbit` (unset, no source), `duck.depth` (0.0), `duck.attack`. */
class KatalystDuckSlots internal constructor() {
    val orbit: KatalystKnobDsl.Param = knob(stage = "duck", knob = "orbit", default = SLOT_UNSET)
    val depth: KatalystKnobDsl.Param = knob(stage = "duck", knob = "depth", default = 0.0)
    val attack: KatalystKnobDsl.Param = knob(stage = "duck", knob = "attack", default = DUCK_ATTACK_SECONDS)
}
