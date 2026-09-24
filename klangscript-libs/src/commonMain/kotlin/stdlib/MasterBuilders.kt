/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:KlangScript.Library(KlangScriptLibraries.STDLIB)

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.MasterDsl
import io.peekandpoke.klang.audio_bridge.MasterStageDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

/*
 * Builders for the master bus. `Master(m => ...)` hands a [MasterBuilder] to the lambda; every
 * knob appends ONE stage, in written order (the order IS the chain), and returns a new builder.
 * A stage's musical inputs are the parameters of its door, `wet` first wherever there is one; a
 * secondary knob (`cap`) sits on the stage's own builder behind a `configure` lambda:
 *
 *     master(Master(m => m
 *         .reverb(0.05, 9)
 *         .gain(2.5)
 *         .limiter(threshold = -3)
 *     ))
 *
 * Every door parameter is optional, and an omitted one is exactly what the bare stage carries (the
 * defaults of the [MasterStageDsl] data class), so `m.limiter()` means what it always meant.
 *
 * Effects are the same DSP the per-orbit effects use; only the host differs, and the knobs use
 * the same names and scales as their sprudel twins, so a number means the same on either bus.
 */

// ── The chain ────────────────────────────────────────────────────────────────

/** Builder for a [MasterDsl] chain, handed to the `configure` lambda of `Master(...)`. Knobs: `gain`, `limiter`, `reverb`, `delay`, each appending a stage. */
data class MasterBuilder(val node: MasterDsl) {
    internal fun plus(stage: MasterStageDsl): MasterBuilder = copy(node = MasterDsl(node.stages + stage))
}

/**
 * Appends a make-up gain stage, the honest way to mix a song low and bring it back up.
 * @param gain linear gain factor (1.0 = unity, 2.0 is about +6 dB).
 */
@KlangScript.Function
fun MasterBuilder.gain(gain: Double = 1.0): MasterBuilder = plus(MasterStageDsl.Gain(gain = gain))

/**
 * Appends a musical limiter on this playback's master bus. Flat, like every dynamics stage: each
 * knob is a musical input.
 *
 * Threshold, ratio, knee and release match the always-on safety limiter that already runs on the
 * summed mix, but this one has NO lookahead by default, so it shapes level rather than
 * anticipating transients, and adds no latency. The safety limiter is the actual brick wall.
 *
 * @param threshold ceiling in dBFS (default -1.0).
 * @param ratio compression ratio (default 20.0, about a brick wall).
 * @param knee soft-knee width in dB (default 2.0). A hard corner injects harmonics on every
 *   crossing.
 * @param attack how fast the gain closes, in seconds (default 0.001). With no lookahead this is a
 *   one-pole attack: short keeps transient punch. With [lookahead] on it becomes the
 *   gain-smoothing length instead; widen both together, because low-frequency cleanliness tracks
 *   the smoothing rather than the window.
 * @param lookahead lookahead in seconds (default 0, off). Lets the limiter start closing the gain
 *   BEFORE a transient arrives instead of chasing it, which is what stops loud hits punching
 *   through to the clip. The cost is latency: this playback is delayed by this much against every
 *   other one, so only reach for it when you want that trade. The always-on safety limiter on the
 *   summed mix already runs at 5 ms.
 * @param release envelope release in seconds (default 0.1).
 */
@KlangScript.Function
fun MasterBuilder.limiter(
    threshold: Double? = null,
    ratio: Double? = null,
    knee: Double? = null,
    attack: Double? = null,
    lookahead: Double? = null,
    release: Double? = null,
): MasterBuilder {
    val bare = MasterStageDsl.Limiter()

    return plus(
        MasterStageDsl.Limiter(
            threshold = threshold ?: bare.threshold,
            ratio = ratio ?: bare.ratio,
            knee = knee ?: bare.knee,
            attackSeconds = attack ?: bare.attackSeconds,
            releaseSeconds = release ?: bare.releaseSeconds,
            lookaheadSeconds = lookahead ?: bare.lookaheadSeconds,
        )
    )
}

/**
 * Appends a master reverb (shared Freeverb, used as an insert). Flat: every knob is a musical input.
 *
 * @param wet how much of the bus is sent into the reverb (default 0.25; 0.0 = off). Orbit twin:
 *   `reverb(wet = ...)`.
 * @param size tail length, on the SAME scale as sprudel `reverb(size = ...)`: typical 1 to 10,
 *   default 5. 3 is about a 1 s tail, 5 about 1.4 s, 10 about 12.5 s; the shortest reachable is
 *   about 0.7 s, and above 10 is bounded at 10. Orbit twin: `reverb(size = ...)`.
 * @param lowpass high-frequency damping of the tail as a lowpass cutoff in Hz: lower is darker.
 *   Omitted, the engine's fixed default damping applies. Orbit twin: `reverb(lowpass = ...)`.
 */
@KlangScript.Function
fun MasterBuilder.reverb(
    wet: Double? = null,
    size: Double? = null,
    lowpass: Double? = null,
): MasterBuilder {
    val bare = MasterStageDsl.Reverb()

    return plus(
        MasterStageDsl.Reverb(
            wet = wet ?: bare.wet,
            size = size ?: bare.size,
            lowpass = lowpass ?: bare.lowpass,
        )
    )
}

/**
 * Appends a master delay (shared delay line, used as an insert).
 *
 * @param wet how much of the bus is sent into the delay (default 0.25; 0.0 = off). Orbit twin:
 *   `delay(wet = ...)`.
 * @param time delay time in seconds (default 0.25). Orbit twin: `delay(time = ...)`.
 * @param feedback feedback amount (default 0.3). At or above 1.0 the delay recirculates without
 *   loss and self-oscillates, allowed, with `cap` deciding how loud. Orbit twin:
 *   `delay(feedback = ...)`.
 * @param configure receives the [MasterDelayBuilder] (knob: `cap`) and returns it.
 */
@KlangScript.Function
fun MasterBuilder.delay(
    wet: Double? = null,
    time: Double? = null,
    feedback: Double? = null,
    configure: ((MasterDelayBuilder) -> MasterDelayBuilder)? = null,
): MasterBuilder {
    val bare = MasterStageDsl.Delay()

    return plus(
        MasterDelayBuilder(
            MasterStageDsl.Delay(
                wet = wet ?: bare.wet,
                time = time ?: bare.time,
                feedback = feedback ?: bare.feedback,
                cap = bare.cap,
            )
        ).configuredBy("Master delay", configure).node
    )
}

// ── Delay ────────────────────────────────────────────────────────────────────

/** Builder for a [MasterStageDsl.Delay] stage. Knob: `cap`. */
data class MasterDelayBuilder(val node: MasterStageDsl.Delay)

/** Level the recirculating signal saturates toward (default 1.0). Orbit twin: `delay(cap = ...)`. */
@KlangScript.Function
fun MasterDelayBuilder.cap(cap: Double): MasterDelayBuilder = copy(node = node.copy(cap = cap))
