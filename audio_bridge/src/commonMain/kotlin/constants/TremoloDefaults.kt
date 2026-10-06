/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge.constants

/**
 * The edge length of the tremolo's square, sawtooth and ramp, in seconds: the LFO oscillator's soft edge
 * (`flankSamples` on the square, `resetSamples` on the sawtooth and the ramp), converted to samples at the
 * voice's sample rate when the voice is built. The maintainer chose 16 ms by ear on 2026-09-29; 8 ms still
 * thumped on the sawtooth.
 *
 * The edge is capped by the oscillator's own shape (the square's duty, the saw's and ramp's `shapeMax`, both half
 * a cycle), so above about 31.25 Hz (a 32 ms cycle) the edges fill the whole cycle and the square, the sawtooth and
 * the ramp all become the same symmetric triangle; only where each starts in the cycle differs.
 */
const val TREMOLO_EDGE_SECONDS: Double = 0.016

/**
 * Where the tremolo's LFO swing sits by default, in the -1..1 language of the Ignitor `range` (`IgnitorDsl.Tremolo`'s
 * `rangeFrom` / `rangeTo`, the script builder's `range(from, to)`): the LFO's -1 maps to `from`, its +1 to `to`, and the
 * gain is `1 + depth * that`. `(-1, 0)` is the tremolo as it always was, the gain dipping from 1 to `1 - depth`; the
 * engine recognises exactly these two values and then builds the classic `range(1 - depth, 1)`, bit for bit.
 * Decided by the maintainer on 2026-10-05 (`docs/tasks/oscillator-phase-knob.md`).
 */
const val TREMOLO_RANGE_FROM: Double = -1.0

/** See [TREMOLO_RANGE_FROM]. */
const val TREMOLO_RANGE_TO: Double = 0.0
