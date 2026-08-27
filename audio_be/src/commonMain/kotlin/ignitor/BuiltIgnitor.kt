/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

/**
 * What one build of a DSL subtree produced: the runtime graph, plus what the build learned on the
 * way. Returned by [buildIgnitor] for every node and by [IgnitorRegistry.createExciter] for a whole
 * ignitor.
 *
 * **Why a structure and not an accumulator.** The build memoises by `(node identity, mod identity)`
 * (see [IgnitorBuildCache]), so a subtree referenced twice is built once. If the release tail were
 * summed into a shared counter gated on "am I on the signal path", a subtree first built in a
 * PARAMETER position would contribute nothing, and the later signal-path reference would hit the
 * cache and contribute nothing either — silently truncating the voice. Carrying the tail INSIDE the
 * cached value closes that: the off-spine build still computes it, the parent simply discards it,
 * and a later on-spine reference gets it back with the cache hit. `IgnitorTailSpec` guards this.
 *
 * The shape is deliberately open: further build-time findings belong here rather than in more
 * out-parameters.
 */
data class BuiltIgnitor(
    /** The runtime graph. */
    val ignitor: Ignitor,
    /**
     * Longest release tail on the SIGNAL SPINE of this subtree, in seconds, or `null`.
     *
     * `null` means one of two things, and neither warrants a guess: nothing tail-bearing was found,
     * or the release time is itself modulated so no single static value is correct. Callers treat
     * `null` as "contributes nothing" and let the voice's own release govern. This deliberately
     * replaces the old hardcoded `0.3` fallback in `maxReleaseSec`, which was a wrong number that
     * looked like an answer.
     *
     * Ranked by RELEASE, never by attack + release: an ADSR starts releasing at note-off wherever
     * it happens to be in its attack, so the envelope reaches zero at `gate end + release`
     * regardless of attack. Including attack could only over-allocate or mis-rank.
     */
    val releaseTailSec: Double? = null,
)

/** Null-tolerant max: `null` means "no tail", so it loses to any actual value. */
internal fun maxTail(a: Double?, b: Double?): Double? = when {
    a == null -> b
    b == null -> a
    else -> if (a >= b) a else b
}
