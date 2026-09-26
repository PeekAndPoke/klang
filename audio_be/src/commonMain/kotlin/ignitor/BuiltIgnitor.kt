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
    /**
     * True when the SIGNAL SPINE of this subtree carries a stage that can silence the voice's own
     * output for a stretch and then bring it back: today, any BUILT tremolo node (one the gate did
     * not remove). A square tremolo at full depth is exact silence for half a cycle, a skewed one
     * for longer, and the silence culler, which ends a voice whose release stays under the floor for
     * `VOICE_CULL_SECONDS`, would kill the voice at its first off-half. The voice factory ORs this
     * into its cull-never decision, the rule the strip's tremolo has always had (`VoiceFactory`:
     * depth above 0, whatever the shape).
     *
     * Section 6 of `docs/tasks/builtin-instruments.md` ("the build must report 'this tree gates its
     * own output'"), landed in phase 3 step 3b (2026-09-25), when the square and skewed shapes made
     * the hazard easy to reach from a script. It was reachable before, more rarely: the floor is
     * absolute (`VOICE_CULL_FLOOR`, 1e-5), so even the old sine-only tremolo at depth 1 held a voice
     * under it for `VOICE_CULL_SECONDS` or longer when slow or quiet enough (a full-scale sine below
     * about 0.04 Hz, a -40 dB release tail at about 0.4 Hz). Step 6's teardown-fade work builds on
     * this same idea: the build reporting what the voice must do about the tree's own amplitude shape
     * ([endsInEnvelope]).
     *
     * Absorbed along the spine like [releaseTailSec] (a tremolo on a CUTOFF silences nothing), and
     * carried in the cached value for the same reason.
     */
    val gatesOutput: Boolean = false,
    /**
     * True when the ROOT of this subtree is an amplitude envelope that the build BUILT (an `Adsr`
     * whose `on` switch did not turn it off). Set by the envelope's own arm; a stage the gate did NOT
     * build (a gate row, the unity `mul` fold included) hands its inner's answer through, because
     * that node IS its inner (`classic()`'s unwritten lowpass over an authored envelope, step 10's case),
     * and so does a `detune`, which scales the frequency and leaves the amplitude alone.
     *
     * Unlike [releaseTailSec] and [gatesOutput] it is NOT absorbed along the spine: an envelope
     * UNDER a later BUILT stage does not take the voice's last frames to zero, so it answers only for
     * the node it is. Pitch-mod wrappers and optimizer hints pass their inner's answer through (they
     * do not touch the amplitude).
     *
     * The voice factory reads it for a voice whose tree is the whole voice (a built-in on `classic()`,
     * phase 3 step 6): when it is false, the voice appends the teardown fade the strip's `adsrOff`
     * always had (`TeardownFadeRenderer`, section 6 of `docs/tasks/builtin-instruments.md`). When it is
     * true, the envelope ends the voice and a fade on top would change its last frames.
     */
    val endsInEnvelope: Boolean = false,
)

/** Null-tolerant max: `null` means "no tail", so it loses to any actual value. */
internal fun maxTail(a: Double?, b: Double?): Double? = when {
    a == null -> b
    b == null -> a
    else -> if (a >= b) a else b
}
