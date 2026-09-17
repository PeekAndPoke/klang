/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:KlangScript.Library(KlangScriptLibraries.STDLIB)

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

/*
 * Builders for an orbit chain. `Katalyst(k => ...)` hands a [KatalystBuilder] to the lambda; every
 * knob appends ONE stage, in written order (the order IS the chain), and returns a new builder.
 * A stage with knobs of its own takes its own configure lambda:
 *
 *     katalyst(Katalyst(k => k
 *         .eq(e => e.band(freq = 300, q = 0.8, db = 2.0))
 *         .reverb(r => r.wet(0.15).size(3))
 *         .compressor(c => c.threshold(-21).ratio(3))
 *     ))
 *
 * Effects are the same DSP the master stages use; only the host differs, and the knobs use the
 * same names and scales as their sprudel twins, so a number means the same on either bus.
 *
 * Every knob takes a number OR an `Osc.param(...)` slot (`IgnitorDslLike`, the same door the
 * oscillator knobs use). The chain reads its knobs once per block, so a signal-rate node on one is
 * coerced, never rejected.
 */

// ── The chain ────────────────────────────────────────────────────────────────

/**
 * Builder for a [KatalystDsl] chain, handed to the `configure` lambda of `Katalyst(...)`. Knobs:
 * `classic`, `body`, `vowel`, `delay`, `reverb`, `phaser`, `compressor`, `duck`, `eq`, `gain`,
 * each appending a stage.
 */
data class KatalystBuilder(val node: KatalystDsl) {
    internal fun plus(stage: KatalystStageDsl): KatalystBuilder = copy(node = KatalystDsl(node.stages + stage))
}

/**
 * Appends the seven historical stages at once: body, vowel, delay, reverb, phaser, compressor and
 * the duck, in that order, with every knob a named slot.
 *
 * `Katalyst(k => k.classic().eq(...))` therefore reads as "the chain an orbit has always run, plus
 * an EQ at the end".
 *
 * The duck it brings is an unset one (no source orbit, so no ducking). Appending a `duck(...)`
 * after it is how you set one: the cylinder runs exactly one ducking effect, so the LAST duck in
 * the chain wins.
 */
@KlangScript.Function
fun KatalystBuilder.classic(): KatalystBuilder = copy(node = KatalystDsl(node.stages + KatalystDsl.classic.stages))

/**
 * Appends a resonant body: a bank of narrow modes over a broadband floor, the box a sound sits in.
 * @param material material name (`"wood"`, `"glass"`, `"tube"`, ...); unset leaves the stage unnamed.
 * @param configure receives the [KatalystBodyBuilder] (knobs: `wet`, `floor`) and returns it.
 */
@KlangScript.Function
fun KatalystBuilder.body(
    material: String? = null,
    configure: ((KatalystBodyBuilder) -> KatalystBodyBuilder)? = null,
): KatalystBuilder =
    plus(KatalystBodyBuilder(KatalystStageDsl.Body(material = material)).configuredBy("Katalyst body", configure).node)

/**
 * Appends a formant bank: the vowel a sound sings.
 * @param vowel vowel name, optionally `voice:vowel` (`"a"`, `"soprano:o"`); unset leaves the stage unnamed.
 * @param configure receives the [KatalystVowelBuilder] (knobs: `wet`, `floor`) and returns it.
 */
@KlangScript.Function
fun KatalystBuilder.vowel(
    vowel: String? = null,
    configure: ((KatalystVowelBuilder) -> KatalystVowelBuilder)? = null,
): KatalystBuilder =
    plus(KatalystVowelBuilder(KatalystStageDsl.Vowel(vowel = vowel)).configuredBy("Katalyst vowel", configure).node)

/**
 * Appends an orbit delay (the shared delay line).
 * @param configure receives the [KatalystDelayBuilder] (knobs: `wet`, `time`, `feedback`, `cap`) and returns it.
 */
@KlangScript.Function
fun KatalystBuilder.delay(configure: ((KatalystDelayBuilder) -> KatalystDelayBuilder)? = null): KatalystBuilder =
    plus(KatalystDelayBuilder(KatalystStageDsl.Delay()).configuredBy("Katalyst delay", configure).node)

/**
 * Appends an orbit reverb (the shared Freeverb).
 * @param configure receives the [KatalystReverbBuilder] (knobs: `wet`, `size`, `lowpass`) and returns it.
 */
@KlangScript.Function
fun KatalystBuilder.reverb(configure: ((KatalystReverbBuilder) -> KatalystReverbBuilder)? = null): KatalystBuilder =
    plus(KatalystReverbBuilder(KatalystStageDsl.Reverb()).configuredBy("Katalyst reverb", configure).node)

/**
 * Appends an orbit phaser: a sweeping all-pass notch comb.
 * @param configure receives the [KatalystPhaserBuilder] (knobs: `rate`, `wet`, `center`, `sweep`, `floor`) and returns it.
 */
@KlangScript.Function
fun KatalystBuilder.phaser(configure: ((KatalystPhaserBuilder) -> KatalystPhaserBuilder)? = null): KatalystBuilder =
    plus(KatalystPhaserBuilder(KatalystStageDsl.Phaser()).configuredBy("Katalyst phaser", configure).node)

/**
 * Appends the orbit compressor: the group dynamics.
 *
 * Put it after the EQ so the detector sees the corrected spectrum and a low cut turns into
 * headroom.
 *
 * @param configure receives the [KatalystCompressorBuilder] (knobs: `threshold`, `ratio`, `knee`, `attack`, `release`) and returns it.
 */
@KlangScript.Function
fun KatalystBuilder.compressor(
    configure: ((KatalystCompressorBuilder) -> KatalystCompressorBuilder)? = null,
): KatalystBuilder =
    plus(
        KatalystCompressorBuilder(KatalystStageDsl.Compressor())
            .configuredBy("Katalyst compressor", configure).node
    )

/**
 * Appends a sidechain duck: this orbit is pulled down whenever the orbit it listens to sounds.
 *
 * Declared in the chain, but run after every orbit has been processed, so where it sits in the
 * list makes no difference.
 *
 * @param configure receives the [KatalystDuckBuilder] (knobs: `orbit`, `depth`, `attack`) and returns it.
 */
@KlangScript.Function
fun KatalystBuilder.duck(configure: ((KatalystDuckBuilder) -> KatalystDuckBuilder)? = null): KatalystBuilder =
    plus(KatalystDuckBuilder(KatalystStageDsl.Duck()).configuredBy("Katalyst duck", configure).node)

/**
 * Appends the mix equalizer: one stage whose sections apply left to right.
 *
 * The same [EqBuilder] the oscillator's `.eq(...)` uses, so a section means the same thing on a
 * voice and on an orbit.
 *
 * @param configure receives the [EqBuilder] (knobs: `band`, `tap`) and returns it.
 */
@KlangScript.Function
fun KatalystBuilder.eq(configure: ((EqBuilder) -> EqBuilder)? = null): KatalystBuilder {
    // [EqBuilder] wraps the voice-side [IgnitorDsl.Eq] node, which needs an `inner` to filter.
    // A chain stage has no inner: it filters whatever the orbit hands it. So the builder gets
    // [IgnitorDsl.Silence] as a placeholder and only its SECTIONS travel into the stage.
    val built = EqBuilder(IgnitorDsl.Eq(inner = IgnitorDsl.Silence)).configuredBy("Katalyst eq", configure).node

    return plus(KatalystStageDsl.Eq(sections = built.sections))
}

/**
 * Appends make-up gain on the orbit: the group fader, after the inserts.
 * @param gain linear gain factor (1.0 = unity, 2.0 is about +6 dB).
 */
@KlangScript.Function
fun KatalystBuilder.gain(gain: IgnitorDslLike = 1.0): KatalystBuilder =
    plus(KatalystStageDsl.Gain(gain = gain.toIgnitorDsl()))

// ── Body ─────────────────────────────────────────────────────────────────────

/** Builder for a [KatalystStageDsl.Body] stage. Knobs: `wet`, `floor`. */
data class KatalystBodyBuilder(val node: KatalystStageDsl.Body)

/** How much of the orbit runs through the body, 0 to 1 (default 0.5). Orbit twin: `body(wet = ...)`. */
@KlangScript.Function
fun KatalystBodyBuilder.wet(wet: IgnitorDslLike): KatalystBodyBuilder = copy(node = node.copy(wet = wet.toIgnitorDsl()))

/**
 * Minimum dry share kept in the mix, 0 to 1 (default 0.4). Lower makes the body MORE audible: its
 * modes sit over less dry. Orbit twin: `body(floor = ...)`.
 */
@KlangScript.Function
fun KatalystBodyBuilder.floor(floor: IgnitorDslLike): KatalystBodyBuilder =
    copy(node = node.copy(floor = floor.toIgnitorDsl()))

// ── Vowel ────────────────────────────────────────────────────────────────────

/** Builder for a [KatalystStageDsl.Vowel] stage. Knobs: `wet`, `floor`. */
data class KatalystVowelBuilder(val node: KatalystStageDsl.Vowel)

/** How much of the orbit runs through the formant bank, 0 to 1 (default 0.5). Orbit twin: `vowel(wet = ...)`. */
@KlangScript.Function
fun KatalystVowelBuilder.wet(wet: IgnitorDslLike): KatalystVowelBuilder =
    copy(node = node.copy(wet = wet.toIgnitorDsl()))

/**
 * Minimum dry share kept between the formants, 0 to 1 (default 0.2). Much lower than the body's: a
 * vowel is a source strongly shaped by its formants. Orbit twin: `vowel(floor = ...)`.
 */
@KlangScript.Function
fun KatalystVowelBuilder.floor(floor: IgnitorDslLike): KatalystVowelBuilder =
    copy(node = node.copy(floor = floor.toIgnitorDsl()))

// ── Delay ────────────────────────────────────────────────────────────────────

/** Builder for a [KatalystStageDsl.Delay] stage. Knobs: `wet`, `time`, `feedback`, `cap`. */
data class KatalystDelayBuilder(val node: KatalystStageDsl.Delay)

/** How much of the orbit goes into the delay (default 0.25; 0.0 = off). Orbit twin: `delay(wet = ...)`. */
@KlangScript.Function
fun KatalystDelayBuilder.wet(wet: IgnitorDslLike): KatalystDelayBuilder =
    copy(node = node.copy(wet = wet.toIgnitorDsl()))

/** Delay time in seconds (default 0.25). Orbit twin: `delay(time = ...)`. */
@KlangScript.Function
fun KatalystDelayBuilder.time(seconds: IgnitorDslLike): KatalystDelayBuilder =
    copy(node = node.copy(time = seconds.toIgnitorDsl()))

/**
 * Feedback amount (default 0.3). At or above 1.0 the delay recirculates without loss and
 * self-oscillates, allowed, with `cap` deciding how loud. Orbit twin: `delay(feedback = ...)`.
 */
@KlangScript.Function
fun KatalystDelayBuilder.feedback(feedback: IgnitorDslLike): KatalystDelayBuilder =
    copy(node = node.copy(feedback = feedback.toIgnitorDsl()))

/** Level the recirculating signal saturates toward (default 1.0). Orbit twin: `delay(cap = ...)`. */
@KlangScript.Function
fun KatalystDelayBuilder.cap(cap: IgnitorDslLike): KatalystDelayBuilder =
    copy(node = node.copy(cap = cap.toIgnitorDsl()))

// ── Reverb ───────────────────────────────────────────────────────────────────

/** Builder for a [KatalystStageDsl.Reverb] stage. Knobs: `wet`, `size`, `lowpass`. */
data class KatalystReverbBuilder(val node: KatalystStageDsl.Reverb)

/** How much of the orbit goes into the reverb (default 0.25; 0.0 = off). Orbit twin: `reverb(wet = ...)`. */
@KlangScript.Function
fun KatalystReverbBuilder.wet(wet: IgnitorDslLike): KatalystReverbBuilder =
    copy(node = node.copy(wet = wet.toIgnitorDsl()))

/**
 * Tail length, on the SAME scale as sprudel `reverb(size = ...)`: typical 1..10, default 5.
 *
 * 3 is about a 1 s tail, 5 about 1.4 s, 10 about 12.5 s; the shortest reachable is about 0.7 s.
 * Above 10 is bounded at 10. Orbit twin: `reverb(size = ...)`.
 */
@KlangScript.Function
fun KatalystReverbBuilder.size(size: IgnitorDslLike): KatalystReverbBuilder =
    copy(node = node.copy(size = size.toIgnitorDsl()))

/**
 * High-frequency damping of the tail as a lowpass cutoff in Hz: lower is darker. Unset, the
 * engine's fixed default damping applies. Orbit twin: `reverb(lowpass = ...)`.
 */
@KlangScript.Function
fun KatalystReverbBuilder.lowpass(hz: IgnitorDslLike): KatalystReverbBuilder =
    copy(node = node.copy(lowpass = hz.toIgnitorDsl()))

// ── Phaser ───────────────────────────────────────────────────────────────────

/** Builder for a [KatalystStageDsl.Phaser] stage. Knobs: `rate`, `wet`, `center`, `sweep`, `floor`. */
data class KatalystPhaserBuilder(val node: KatalystStageDsl.Phaser)

/** Sweep rate in Hz (default 0, standing still). Orbit twin: `phaser(rate = ...)`. */
@KlangScript.Function
fun KatalystPhaserBuilder.rate(hz: IgnitorDslLike): KatalystPhaserBuilder =
    copy(node = node.copy(rate = hz.toIgnitorDsl()))

/** Wet amount, 0 to 1 (default 0, off). Orbit twin: `phaser(wet = ...)`. */
@KlangScript.Function
fun KatalystPhaserBuilder.wet(wet: IgnitorDslLike): KatalystPhaserBuilder =
    copy(node = node.copy(wet = wet.toIgnitorDsl()))

/** Center frequency of the sweep in Hz (default 1000). Orbit twin: `phaser(center = ...)`. */
@KlangScript.Function
fun KatalystPhaserBuilder.center(hz: IgnitorDslLike): KatalystPhaserBuilder =
    copy(node = node.copy(center = hz.toIgnitorDsl()))

/** Width of the sweep around the center, in Hz (default 1000). Orbit twin: `phaser(sweep = ...)`. */
@KlangScript.Function
fun KatalystPhaserBuilder.sweep(hz: IgnitorDslLike): KatalystPhaserBuilder =
    copy(node = node.copy(sweep = hz.toIgnitorDsl()))

/** Minimum dry coefficient of the wet/dry law (default 1.0, purely additive). Orbit twin: `phaser(floor = ...)`. */
@KlangScript.Function
fun KatalystPhaserBuilder.floor(floor: IgnitorDslLike): KatalystPhaserBuilder =
    copy(node = node.copy(floor = floor.toIgnitorDsl()))

// ── Compressor ───────────────────────────────────────────────────────────────

/** Builder for a [KatalystStageDsl.Compressor] stage. Knobs: `threshold`, `ratio`, `knee`, `attack`, `release`. */
data class KatalystCompressorBuilder(val node: KatalystStageDsl.Compressor)

/** Ceiling in dBFS where gain reduction starts (default -20). Orbit twin: `compressor(threshold = ...)`. */
@KlangScript.Function
fun KatalystCompressorBuilder.threshold(db: IgnitorDslLike): KatalystCompressorBuilder =
    copy(node = node.copy(threshold = db.toIgnitorDsl()))

/** Compression ratio above the threshold (default 4, i.e. 4:1). Orbit twin: `compressor(ratio = ...)`. */
@KlangScript.Function
fun KatalystCompressorBuilder.ratio(ratio: IgnitorDslLike): KatalystCompressorBuilder =
    copy(node = node.copy(ratio = ratio.toIgnitorDsl()))

/** Soft-knee width in dB (default 6). A hard corner injects harmonics on every crossing. Orbit twin: `compressor(knee = ...)`. */
@KlangScript.Function
fun KatalystCompressorBuilder.knee(db: IgnitorDslLike): KatalystCompressorBuilder =
    copy(node = node.copy(knee = db.toIgnitorDsl()))

/** How fast the gain closes, in seconds (default 0.003). Orbit twin: `compressor(attack = ...)`. */
@KlangScript.Function
fun KatalystCompressorBuilder.attack(seconds: IgnitorDslLike): KatalystCompressorBuilder =
    copy(node = node.copy(attack = seconds.toIgnitorDsl()))

/** How fast the gain opens again, in seconds (default 0.1). Orbit twin: `compressor(release = ...)`. */
@KlangScript.Function
fun KatalystCompressorBuilder.release(seconds: IgnitorDslLike): KatalystCompressorBuilder =
    copy(node = node.copy(release = seconds.toIgnitorDsl()))

// ── Duck ─────────────────────────────────────────────────────────────────────

/** Builder for a [KatalystStageDsl.Duck] stage. Knobs: `orbit`, `depth`, `attack`. */
data class KatalystDuckBuilder(val node: KatalystStageDsl.Duck)

/**
 * The orbit to listen to. Unset (the default) means no ducking: the stage carries the wire's
 * non-finite "never set" marker, and the runtime tests `isFinite()` rather than comparing, so a
 * finite negative is an orbit REQUEST like any other and not an off switch. Orbit twin:
 * `duck(orbit = ...)`.
 */
@KlangScript.Function
fun KatalystDuckBuilder.orbit(orbit: IgnitorDslLike): KatalystDuckBuilder =
    copy(node = node.copy(orbit = orbit.toIgnitorDsl()))

/** How far this orbit is pulled down, 0 to 1 (default 0, no ducking). Orbit twin: `duck(depth = ...)`. */
@KlangScript.Function
fun KatalystDuckBuilder.depth(depth: IgnitorDslLike): KatalystDuckBuilder =
    copy(node = node.copy(depth = depth.toIgnitorDsl()))

/** How fast the duck closes, in seconds (default 0.1). Orbit twin: `duck(attack = ...)`. */
@KlangScript.Function
fun KatalystDuckBuilder.attack(seconds: IgnitorDslLike): KatalystDuckBuilder =
    copy(node = node.copy(attack = seconds.toIgnitorDsl()))
