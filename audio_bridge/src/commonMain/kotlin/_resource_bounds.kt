/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import kotlin.math.roundToInt

// The engine's RESOURCE bounds: counts that size arrays or filter cascades at note-on on the render thread, so a
// live-typed huge value is coerced here, in one place, instead of allocating without end. None of them clamps a tone.

/**
 * Upper bound for the `passes` cascade count (C5). 16 stages is 192 dB/oct, far past any
 * musical use; the bound exists because `passes` is a RESOURCE count, not a tone knob:
 * a live-typed `lpf(passes = 1e9)` would otherwise allocate a billion filter stages inside a note-on
 * on the render thread. Nothing about the sound of a reachable value is clamped.
 */
const val FILTER_MAX_PASSES = 16

/**
 * The ONE place a `passes` value is coerced (parameter-parity rule: conversions live in a
 * single place). Every consumer funnels through here (the sprudel voice-data builder, the
 * ignitor runtime, the graph optimizer and the engine's q-ladder), so a value that is legal
 * on one door cannot be illegal on another.
 *
 * Two callers coerce while CONSTRUCTING the value rather than while consuming it: sprudel's
 * `classicSlotParams` (which writes the `lpf.passes` / `hpf.passes` slot the wire carries) and, on the ignitor door, the
 * KlangScript stdlib builder, the latter because its generated thunk would truncate a `Double`
 * (`0.3 * 10` is 2.9999999999999996, and every KlangScript number is a double). Coercing
 * early only normalises the value that gets stored and encoded; every consumer re-coerces
 * idempotently, so the two timings cannot disagree about the rendered filter.
 */
fun coercePasses(passes: Int): Int = passes.coerceIn(1, FILTER_MAX_PASSES)

/**
 * The pattern-value door onto [coercePasses]. Sprudel carries every control value as a
 * `Double`, and pattern arithmetic lands on things like `2.9999999996`; truncating there
 * silently drops a cascade stage, so the value is ROUNDED. `roundToInt()` throws on NaN, and
 * this runs on the render thread, hence the explicit guard rather than a try.
 *
 * A NON-FINITE value is one pass, the house rule that a non-finite wire number reads as unset
 * (`/dsl-design` section 4). Until phase 3 step 5 only the NaN was guarded and `+Infinity`
 * saturated `roundToInt()` to the 16-pass ceiling; the filters' `passes` knob (read at voice
 * build for `classic()`'s `lpf.passes` / `hpf.passes` slots) reads through here too, so the
 * pattern side and the tree agree on every value.
 */
fun coercePasses(passes: Double): Int {
    if (!passes.isFinite()) { // NaN-guard: non-finite reads as unset, one pass
        return 1
    }

    return coercePasses(passes.roundToInt())
}

/**
 * Upper bound for a unison stack's voice count (the super oscillators and the super pluck). Like [FILTER_MAX_PASSES]
 * it bounds a RESOURCE count, not a tone: the count sizes arrays at note-on on the render thread, so a live-typed
 * `voices(1e9)` would allocate a billion voice states there. 256 (maintainer, 2026-10-08; 64 before) keeps every
 * authored sound as it was (the largest authored count is 32) and leaves room for wide stacks. The cost at the top: a
 * super pluck string carries a 2,500-sample delay line, so 256 strings build about 5 MB per note on the render thread.
 * It also keeps `PhasePool`'s `tries * voices` (tries at most 64, so at most 16,384) far from an `Int` overflow.
 */
const val UNISON_MAX_VOICES = 256

/**
 * The ONE place a unison voice count is coerced: the engine's two runtime reads (super oscillators, super pluck) and
 * the graph census, so the census counts what renders. Not on the doors: a door clamp would not cover the wire or a
 * signal. A non-finite value reads as 0 voices, silence; a finite one truncates and lands in `[0, UNISON_MAX_VOICES]`.
 */
fun coerceUnisonVoices(value: Double): Int {
    if (!value.isFinite()) { // NaN-guard: a non-finite count reads as no voices
        return 0
    }

    return value.toInt().coerceIn(0, UNISON_MAX_VOICES)
}

/**
 * Upper bound for the explicit partials of one sine (`IgnitorDsl.Sine.partials`, maintainer, 2026-10-09, Q26). A
 * RESOURCE count like [UNISON_MAX_VOICES]: each partial sizes a phase, a gain, an increment and a drift lane at
 * note-on on the render thread. A song that lists more plays the first 256 (coerced, never refused); the rest are not
 * built at all, their knob subtrees included.
 */
const val SINE_MAX_PARTIALS = 256

/**
 * The ONE place the explicit partial count is coerced: the engine's build and the graph census, so the census counts
 * what renders. Not on the doors, as for the unison voices: the wire can carry any list.
 */
fun coerceSinePartials(count: Int): Int = count.coerceIn(0, SINE_MAX_PARTIALS)
