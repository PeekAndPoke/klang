/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import kotlin.math.pow

/*
 * The drive law, `gain = 10^(amount * 1.2)`: exponential in the amount, for a perceptually even knob (0.1 is about
 * +2.4 dB, 0.25 about +6 dB, 0.5 about +12 dB). THE ONE HOME of it (KLANG-00467, cylinder amp step 1): the voice's
 * `drive` and both `distort` hosts read it through `DistortionCore.drive` in `audio_be`, which delegates here, and the
 * Katalyst chain's `drive` door builds its gain from it (`KatalystBuilder.drive`). It lives in the bridge because the
 * chain's door does, and the bridge cannot reach `audio_be`.
 *
 * Two forms, one law: [driveGain] for a number, and [driveGainKnob] for a Katalyst knob that is not a number (a
 * `Kat.param`, an expression, a modulator), whose value is only known while the chain runs. Both read the two
 * constants below; `KatalystDriveSpec` (audio_be) renders them against each other and against the voice.
 */

/** The base of the drive law: the gain is this to the power of the amount times [DRIVE_EXPONENT_PER_AMOUNT]. */
internal const val DRIVE_GAIN_BASE: Double = 10.0

/** The exponent per unit of amount of the drive law: 1.2 decades of gain (24 dB) per unit. */
internal const val DRIVE_EXPONENT_PER_AMOUNT: Double = 1.2

/**
 * The drive GAIN of a distort or drive [amount], `10^(amount * 1.2)`. No gate here: the hosts decide what an amount at
 * or below 0, or not finite, means (the voice's `drive` passes its input through there, the `distort` stage switches
 * off; `audio/ref/off-values.md`).
 */
fun driveGain(amount: Double): Double = DRIVE_GAIN_BASE.pow(amount * DRIVE_EXPONENT_PER_AMOUNT)

/**
 * The drive law as a Katalyst knob over [amount]: `10^(max(amount, 0) * 1.2)`, the gain a chain's `times` stage
 * multiplies by. The floor at 0 is the voice's gate written into the law: the voice's `drive` passes its input through
 * for an amount at or below 0, and `10^0` is exactly 1. A non-finite slot value unsets the whole knob (the knob rule,
 * Q5 of KLANG-00302 (katalyst knob expressions)), and an unset `times` operand is the identity: unity again, as the
 * voice's drive at a non-finite amount.
 *
 * The same law, not the same edges, in two places. An amount so large that the gain is not finite (above about 257)
 * reads as unity through the number door (the `times` stage reads a non-finite operand as unset) and as the engine's
 * safe ceiling, 1e15, through this knob (the `pow` law clamps). And a non-finite value WRITTEN over a finite default
 * unsets this knob, so it is unity on a chain, where a voice's slot falls back to its default. No clamp is added for
 * either: the Motor stays raw.
 */
fun driveGainKnob(amount: KatalystKnobDsl): KatalystKnobDsl = KatalystKnobDsl.Pow(
    base = KatalystKnobDsl.Constant(DRIVE_GAIN_BASE),
    exp = KatalystKnobDsl.Times(
        left = KatalystKnobDsl.Max(left = amount, right = KatalystKnobDsl.Constant(0.0)),
        right = KatalystKnobDsl.Constant(DRIVE_EXPONENT_PER_AMOUNT),
    ),
)
