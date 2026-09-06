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
 * A stage with knobs of its own takes its own configure lambda:
 *
 *     master(Master(m => m
 *         .reverb(r => r.wet(0.05).roomSize(9))
 *         .gain(2.5)
 *         .limiter(l => l.thresholdDb(-3))
 *     ))
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
 * Appends a musical limiter on this playback's master bus.
 *
 * Threshold, ratio, knee and release match the always-on safety limiter that already runs on the
 * summed mix, but this one has NO lookahead by default, so it shapes level rather than
 * anticipating transients, and adds no latency. The safety limiter is the actual brick wall.
 *
 * @param configure receives the [MasterLimiterBuilder] (knobs: `thresholdDb`, `ratio`, `kneeDb`, `attack`, `release`, `lookahead`) and returns it.
 */
@KlangScript.Function
fun MasterBuilder.limiter(configure: ((MasterLimiterBuilder) -> MasterLimiterBuilder)? = null): MasterBuilder =
    plus(MasterLimiterBuilder(MasterStageDsl.Limiter()).configuredBy("Master limiter", configure).node)

/**
 * Appends a master reverb (shared Freeverb, used as an insert).
 * @param configure receives the [MasterReverbBuilder] (knobs: `wet`, `roomSize`, `damp`, `roomFade`, `roomLp`) and returns it.
 */
@KlangScript.Function
fun MasterBuilder.reverb(configure: ((MasterReverbBuilder) -> MasterReverbBuilder)? = null): MasterBuilder =
    plus(MasterReverbBuilder(MasterStageDsl.Reverb()).configuredBy("Master reverb", configure).node)

/**
 * Appends a master delay (shared delay line, used as an insert).
 * @param configure receives the [MasterDelayBuilder] (knobs: `wet`, `time`, `feedback`, `cap`) and returns it.
 */
@KlangScript.Function
fun MasterBuilder.delay(configure: ((MasterDelayBuilder) -> MasterDelayBuilder)? = null): MasterBuilder =
    plus(MasterDelayBuilder(MasterStageDsl.Delay()).configuredBy("Master delay", configure).node)

// ── Limiter ──────────────────────────────────────────────────────────────────

/** Builder for a [MasterStageDsl.Limiter] stage. Knobs: `thresholdDb`, `ratio`, `kneeDb`, `attack`, `release`, `lookahead`. */
data class MasterLimiterBuilder(val node: MasterStageDsl.Limiter)

/** Ceiling in dBFS (default -1.0). */
@KlangScript.Function
fun MasterLimiterBuilder.thresholdDb(db: Double): MasterLimiterBuilder = copy(node = node.copy(thresholdDb = db))

/** Compression ratio (default 20.0, about a brick wall). */
@KlangScript.Function
fun MasterLimiterBuilder.ratio(ratio: Double): MasterLimiterBuilder = copy(node = node.copy(ratio = ratio))

/** Soft-knee width in dB (default 2.0). A hard corner injects harmonics on every crossing. */
@KlangScript.Function
fun MasterLimiterBuilder.kneeDb(db: Double): MasterLimiterBuilder = copy(node = node.copy(kneeDb = db))

/**
 * How fast the gain closes, in seconds (default 0.001).
 *
 * With no lookahead this is a one-pole attack: short keeps transient punch. With [lookahead] on
 * it becomes the gain-smoothing length instead; widen both together, because low-frequency
 * cleanliness tracks the smoothing rather than the window.
 */
@KlangScript.Function
fun MasterLimiterBuilder.attack(seconds: Double): MasterLimiterBuilder = copy(node = node.copy(attackSeconds = seconds))

/**
 * Lookahead in seconds (default 0, off).
 *
 * Lets the limiter start closing the gain BEFORE a transient arrives instead of chasing it, which
 * is what stops loud hits punching through to the clip. The cost is latency: this playback is
 * delayed by this much against every other one, so only reach for it when you want that trade.
 * The always-on safety limiter on the summed mix already runs at 5 ms.
 */
@KlangScript.Function
fun MasterLimiterBuilder.lookahead(seconds: Double): MasterLimiterBuilder = copy(node = node.copy(lookaheadSeconds = seconds))

/** Envelope release in seconds (default 0.1). */
@KlangScript.Function
fun MasterLimiterBuilder.release(seconds: Double): MasterLimiterBuilder = copy(node = node.copy(releaseSeconds = seconds))

// ── Reverb ───────────────────────────────────────────────────────────────────

/** Builder for a [MasterStageDsl.Reverb] stage. Knobs: `wet`, `roomSize`, `damp`, `roomFade`, `roomLp`. */
data class MasterReverbBuilder(val node: MasterStageDsl.Reverb)

/** How much of the bus is sent into the reverb (default 0.25; 0.0 = off). Orbit twin: `roomWet()`. */
@KlangScript.Function
fun MasterReverbBuilder.wet(wet: Double): MasterReverbBuilder = copy(node = node.copy(wet = wet))

/**
 * Tail length, on the SAME scale as sprudel `roomsize()`: typical 1..10, default 5.
 *
 * 3 is about a 1 s tail, 5 about 1.4 s, 10 about 12.5 s; the shortest reachable is about 0.7 s.
 * Above 10 is bounded: past unity the comb network has no steady state and runs away, so there
 * is nothing there.
 */
@KlangScript.Function
fun MasterReverbBuilder.roomSize(size: Double): MasterReverbBuilder = copy(node = node.copy(roomSize = size))

/** High-frequency damping, 0 = bright .. 1 = dark (default 0.5). Ignored when `roomLp` is set. */
@KlangScript.Function
fun MasterReverbBuilder.damp(damp: Double): MasterReverbBuilder = copy(node = node.copy(damp = damp))

/**
 * OVERRIDES `roomSize` for the tail, and is NOT on the same scale: this is the normalized 0..1
 * value (0 is about 0.7 s, 1 about 12.5 s), and despite the name it is not a time.
 * Orbit twin: `roomfade()` / `rfade()`.
 */
@KlangScript.Function
fun MasterReverbBuilder.roomFade(amount: Double): MasterReverbBuilder = copy(node = node.copy(roomFade = amount))

/** High-frequency damping as an absolute cutoff in Hz; overrides `damp`. Orbit twin: `roomlp()` / `rlp()`. */
@KlangScript.Function
fun MasterReverbBuilder.roomLp(hz: Double): MasterReverbBuilder = copy(node = node.copy(roomLp = hz))

// ── Delay ────────────────────────────────────────────────────────────────────

/** Builder for a [MasterStageDsl.Delay] stage. Knobs: `wet`, `time`, `feedback`, `cap`. */
data class MasterDelayBuilder(val node: MasterStageDsl.Delay)

/** How much of the bus is sent into the delay (default 0.25; 0.0 = off). */
@KlangScript.Function
fun MasterDelayBuilder.wet(wet: Double): MasterDelayBuilder = copy(node = node.copy(wet = wet))

/** Delay time in seconds (default 0.25). */
@KlangScript.Function
fun MasterDelayBuilder.time(seconds: Double): MasterDelayBuilder = copy(node = node.copy(timeSeconds = seconds))

/**
 * Feedback amount (default 0.3). At or above 1.0 the delay recirculates without loss and
 * self-oscillates, allowed, with `cap` deciding how loud. Orbit twin: `delayfeedback()`.
 */
@KlangScript.Function
fun MasterDelayBuilder.feedback(feedback: Double): MasterDelayBuilder = copy(node = node.copy(feedback = feedback))

/** Ceiling the feedback saturates toward (default 1.0 = unchanged). Orbit twin: `delaycap()` / `dcap()`. */
@KlangScript.Function
fun MasterDelayBuilder.cap(cap: Double): MasterDelayBuilder = copy(node = node.copy(cap = cap))
