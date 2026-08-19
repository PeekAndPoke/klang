/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.filters.AudioFilter

/**
 * Click-free hot-swap for a stereo [AudioFilter] pair.
 *
 * A resonant bank (body / vowel) carries state — its SVF integrators are mid-ring. Replacing the
 * instance outright makes the wet output jump from the old ring to the new bank's zero state in one
 * sample → an audible click on live material/mix/floor changes (e.g. editing `body(...)` with
 * auto-update). This wrapper instead **crossfades**: on [set] it keeps the previous pair alive and,
 * over [fadeSeconds], runs BOTH pairs and ramps old→new. At the swap sample the blend equals the old
 * output (continuous — no step); by the end it is fully the new bank.
 *
 * Used by [KatalystBodyEffect] / [KatalystFormantEffect] (their intentional twins share this one
 * declick path). Scratch buffers are per-instance and grow-once; both banks run only during the short
 * fade, so the doubled cost is a transient.
 */
class KatalystFilterSwap(sampleRate: Double, fadeSeconds: Double = 0.012) {

    private val fadeLen: Int = (sampleRate * fadeSeconds).toInt().coerceAtLeast(1)

    private var curL: AudioFilter? = null
    private var curR: AudioFilter? = null
    private var oldL: AudioFilter? = null
    private var oldR: AudioFilter? = null
    private var fadePos: Int = 0

    private var scratchL: AudioBuffer = AudioBuffer(0)
    private var scratchR: AudioBuffer = AudioBuffer(0)

    /** True once a pair is installed (mirrors the old `left != null` "active" flag). */
    val active: Boolean get() = curL != null

    /** Install a new stereo pair, crossfading from the current one (if any). */
    fun set(left: AudioFilter, right: AudioFilter) {
        if (curL != null) {
            // Start (or restart) a fade FROM whatever is current. A restart mid-fade drops the
            // oldest pair — a rare double-change may tick faintly, a single change is click-free.
            oldL = curL
            oldR = curR
            fadePos = 0
        }
        curL = left
        curR = right
    }

    /** Drop both pairs — the effect is off. */
    fun clear() {
        curL = null
        curR = null
        oldL = null
        oldR = null
        fadePos = 0
    }

    /** Process the orbit mix in place through the current pair, crossfading from the old one if fading. */
    fun process(mix: StereoBuffer, n: Int) {
        val l = curL ?: return
        val r = curR ?: return

        val oL = oldL
        if (oL == null) {
            l.process(mix.left, 0, n)
            r.process(mix.right, 0, n)
            return
        }
        val oR = oldR ?: return

        if (scratchL.size < n) {
            scratchL = AudioBuffer(n)
            scratchR = AudioBuffer(n)
        }
        // Keep the dry input for the OLD pair before the NEW pair overwrites the mix in place.
        mix.left.copyInto(scratchL, 0, 0, n)
        mix.right.copyInto(scratchR, 0, 0, n)

        l.process(mix.left, 0, n)   // new → mix
        r.process(mix.right, 0, n)
        oL.process(scratchL, 0, n)  // old → scratch
        oR.process(scratchR, 0, n)

        val len = fadeLen
        for (i in 0 until n) {
            val t = ((fadePos + i).toDouble() / len).coerceAtMost(1.0)
            mix.left[i] = scratchL[i] * (1.0 - t) + mix.left[i] * t
            mix.right[i] = scratchR[i] * (1.0 - t) + mix.right[i] * t
        }
        fadePos += n
        if (fadePos >= len) {
            oldL = null
            oldR = null
        }
    }
}
