/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

/**
 * Generic child enumeration and rebuild for [IgnitorDsl] trees, the substrate the graph
 * optimizer walks on.
 *
 * Both functions are EXHAUSTIVE expression-form `when`s over all 78 node types, with NO
 * `else` arm on purpose: a fallback would silently stop descending into any node added later,
 * so optimizations would quietly stop firing under it with nothing failing to compile. Adding a
 * node type must break this file.
 *
 * **Child order is part of the contract** — [withChildNodes] re-reads the list positionally, so
 * the order here must match constructor order for every node, and both functions must agree.
 * `IgnitorDslWalkSpec` pins round-tripping for every type, sections included.
 *
 * Naming note: these are `childNodes`/`withChildNodes`, NOT `children`, because
 * [IgnitorDsl.Variants] already has a `children` PROPERTY that a function of the same name
 * would collide with inside its own arm.
 */
fun IgnitorDsl.childNodes(): List<IgnitorDsl> {
    return when (this) {
        is IgnitorDsl.Abs -> listOf(inner)
        is IgnitorDsl.Accelerate -> listOf(inner, amount)
        is IgnitorDsl.Adsr -> listOf(inner, attackSec, decaySec, sustainLevel, releaseSec, declickSeconds, expK)
        is IgnitorDsl.Bandpass -> listOf(inner, cutoffHz, q, analog)
        is IgnitorDsl.BerlinNoise -> listOf(rate, octaves, persistence)
        is IgnitorDsl.Bipolar -> listOf(inner)
        is IgnitorDsl.BrownNoise -> listOf(depth)
        is IgnitorDsl.Ceil -> listOf(inner)
        is IgnitorDsl.Clamp -> listOf(inner, lo, hi)
        is IgnitorDsl.Clip -> listOf(inner)
        is IgnitorDsl.Coarse -> listOf(inner, amount)
        is IgnitorDsl.Constant -> emptyList()
        is IgnitorDsl.Crackle -> listOf(chaos)
        is IgnitorDsl.Crush -> listOf(inner, amount)
        is IgnitorDsl.Detune -> listOf(inner, semitones)
        is IgnitorDsl.Distort -> listOf(inner, amount)
        is IgnitorDsl.Div -> listOf(left, right)
        is IgnitorDsl.Drive -> listOf(inner, amount)
        is IgnitorDsl.Dust -> listOf(density, tail, bipolar)
        is IgnitorDsl.Eq -> listOf(inner) + sections.flatMap { it.childNodes() }
        is IgnitorDsl.Exp -> listOf(inner)
        is IgnitorDsl.Floor -> listOf(inner)
        is IgnitorDsl.Fm -> listOf(carrier, modulator, ratio, depth, envAttackSec, envDecaySec, envSustainLevel, envReleaseSec)
        is IgnitorDsl.Frac -> listOf(inner)
        is IgnitorDsl.Freq -> emptyList()
        is IgnitorDsl.Highpass -> listOf(inner, cutoffHz, q, analog)
        is IgnitorDsl.Impulse -> listOf(freq, analog)
        is IgnitorDsl.Lerp -> listOf(left, right, t)
        is IgnitorDsl.Log -> listOf(inner)
        is IgnitorDsl.Lowpass -> listOf(inner, cutoffHz, q, analog)
        is IgnitorDsl.Max -> listOf(left, right)
        is IgnitorDsl.Min -> listOf(left, right)
        is IgnitorDsl.Minus -> listOf(left, right)
        is IgnitorDsl.Mod -> listOf(left, right)
        is IgnitorDsl.Neg -> listOf(inner)
        is IgnitorDsl.Notch -> listOf(inner, cutoffHz, q, analog)
        is IgnitorDsl.OnePoleLowpass -> listOf(inner, cutoffHz)
        is IgnitorDsl.OptimizerHint -> listOf(inner)
        is IgnitorDsl.Param -> emptyList()
        is IgnitorDsl.PerlinNoise -> listOf(rate, octaves, persistence)
        is IgnitorDsl.Phaser -> listOf(inner, rate, blend, center, sweep)
        is IgnitorDsl.PinkNoise -> emptyList()
        is IgnitorDsl.PitchEnvelope -> listOf(inner, amount, attackSec, decaySec, releaseSec, curve, anchor)
        is IgnitorDsl.PitchMod -> listOf(inner, mod)
        is IgnitorDsl.Pluck -> listOf(freq, decay, brightness, pickPosition, stiffness, analog)
        is IgnitorDsl.Plus -> listOf(left, right)
        is IgnitorDsl.Pow -> listOf(base, exp)
        is IgnitorDsl.Pulze -> listOf(freq, duty, analog)
        is IgnitorDsl.Ramp -> listOf(freq, analog)
        is IgnitorDsl.Range -> listOf(inner, lo, hi)
        is IgnitorDsl.RawPulze -> listOf(freq, duty, analog)
        is IgnitorDsl.Recip -> listOf(inner)
        is IgnitorDsl.Round -> listOf(inner)
        is IgnitorDsl.Sawtooth -> listOf(freq, analog)
        is IgnitorDsl.Select -> listOf(cond, whenTrue, whenFalse)
        is IgnitorDsl.Shimmer -> listOf(inner, blend, feedback, tone)
        is IgnitorDsl.Sign -> listOf(inner)
        is IgnitorDsl.Silence -> emptyList()
        is IgnitorDsl.Sine -> listOf(freq, analog)
        is IgnitorDsl.Sq -> listOf(inner)
        is IgnitorDsl.Sqrt -> listOf(inner)
        is IgnitorDsl.Square -> listOf(freq, analog)
        is IgnitorDsl.SuperPluck -> listOf(freq, voices, spread, decay, brightness, pickPosition, stiffness, analog)
        is IgnitorDsl.SuperRamp -> listOf(freq, voices, spread, analog)
        is IgnitorDsl.SuperSaw -> listOf(freq, voices, spread, analog)
        is IgnitorDsl.SuperSine -> listOf(freq, voices, spread, analog)
        is IgnitorDsl.SuperSquare -> listOf(freq, voices, spread, analog)
        is IgnitorDsl.SuperTri -> listOf(freq, voices, spread, analog)
        is IgnitorDsl.Tanh -> listOf(inner)
        is IgnitorDsl.Times -> listOf(left, right)
        is IgnitorDsl.Tremolo -> listOf(inner, rate, depth)
        is IgnitorDsl.Triangle -> listOf(freq, analog)
        is IgnitorDsl.Unipolar -> listOf(inner)
        is IgnitorDsl.Variants -> children
        is IgnitorDsl.Vibrato -> listOf(inner, rate, depth)
        is IgnitorDsl.WhiteNoise -> listOf(color)
        is IgnitorDsl.Zamp -> listOf(freq, analog)
        is IgnitorDsl.Zawtooth -> listOf(freq, analog)
    }
}

/**
 * Rebuilds this node with [new] in place of [childNodes], positionally.
 *
 * For any node WITH children this returns a new instance even when the children are unchanged;
 * the five childless leaves return `this`. Callers that want to preserve identity for untouched
 * subtrees must check that themselves (the optimizer does, so shared subtrees stay shared and
 * `===` survives a rewrite).
 *
 * @throws IllegalArgumentException on a size mismatch. This is an internal invariant, not user
 * input: a mis-chunked list would silently swap a bell's `q` into its `db`, so it must fail
 * loudly rather than change a sound silently.
 *
 * NOTE this DOES run on the JS audio thread: `IgnitorRegistry.register` is reached from the
 * worklet's `port.onmessage`. `register` wraps the whole optimize call so nothing escapes into
 * that dispatch, but do not add throwing or allocating work here on the assumption that it is
 * off the render path.
 */
fun IgnitorDsl.withChildNodes(new: List<IgnitorDsl>): IgnitorDsl {
    require(new.size == childNodes().size) {
        "withChildNodes size mismatch for ${this::class.simpleName}: expected ${childNodes().size}, got ${new.size}"
    }

    return when (this) {
        is IgnitorDsl.Abs -> copy(inner = new[0])
        is IgnitorDsl.Accelerate -> copy(inner = new[0], amount = new[1])
        is IgnitorDsl.Adsr -> copy(
            inner = new[0],
            attackSec = new[1],
            decaySec = new[2],
            sustainLevel = new[3],
            releaseSec = new[4],
            declickSeconds = new[5],
            expK = new[6],
        )
        is IgnitorDsl.Bandpass -> copy(inner = new[0], cutoffHz = new[1], q = new[2], analog = new[3])
        is IgnitorDsl.BerlinNoise -> copy(rate = new[0], octaves = new[1], persistence = new[2])
        is IgnitorDsl.Bipolar -> copy(inner = new[0])
        is IgnitorDsl.BrownNoise -> copy(depth = new[0])
        is IgnitorDsl.Ceil -> copy(inner = new[0])
        is IgnitorDsl.Clamp -> copy(inner = new[0], lo = new[1], hi = new[2])
        is IgnitorDsl.Clip -> copy(inner = new[0])
        is IgnitorDsl.Coarse -> copy(inner = new[0], amount = new[1])
        is IgnitorDsl.Constant -> this
        is IgnitorDsl.Crackle -> copy(chaos = new[0])
        is IgnitorDsl.Crush -> copy(inner = new[0], amount = new[1])
        is IgnitorDsl.Detune -> copy(inner = new[0], semitones = new[1])
        is IgnitorDsl.Distort -> copy(inner = new[0], amount = new[1])
        is IgnitorDsl.Div -> copy(left = new[0], right = new[1])
        is IgnitorDsl.Drive -> copy(inner = new[0], amount = new[1])
        is IgnitorDsl.Dust -> copy(density = new[0], tail = new[1], bipolar = new[2])
        is IgnitorDsl.Eq -> {
            var next = 1
            copy(
                inner = new[0],
                sections = sections.map { section ->
                    val take = section.childNodes().size
                    section.withChildNodes(new.subList(next, next + take)).also { next += take }
                },
            )
        }
        is IgnitorDsl.Exp -> copy(inner = new[0])
        is IgnitorDsl.Floor -> copy(inner = new[0])
        is IgnitorDsl.Fm -> copy(
            carrier = new[0],
            modulator = new[1],
            ratio = new[2],
            depth = new[3],
            envAttackSec = new[4],
            envDecaySec = new[5],
            envSustainLevel = new[6],
            envReleaseSec = new[7],
        )
        is IgnitorDsl.Frac -> copy(inner = new[0])
        is IgnitorDsl.Freq -> this
        is IgnitorDsl.Highpass -> copy(inner = new[0], cutoffHz = new[1], q = new[2], analog = new[3])
        is IgnitorDsl.Impulse -> copy(freq = new[0], analog = new[1])
        is IgnitorDsl.Lerp -> copy(left = new[0], right = new[1], t = new[2])
        is IgnitorDsl.Log -> copy(inner = new[0])
        is IgnitorDsl.Lowpass -> copy(inner = new[0], cutoffHz = new[1], q = new[2], analog = new[3])
        is IgnitorDsl.Max -> copy(left = new[0], right = new[1])
        is IgnitorDsl.Min -> copy(left = new[0], right = new[1])
        is IgnitorDsl.Minus -> copy(left = new[0], right = new[1])
        is IgnitorDsl.Mod -> copy(left = new[0], right = new[1])
        is IgnitorDsl.Neg -> copy(inner = new[0])
        is IgnitorDsl.Notch -> copy(inner = new[0], cutoffHz = new[1], q = new[2], analog = new[3])
        is IgnitorDsl.OnePoleLowpass -> copy(inner = new[0], cutoffHz = new[1])
        is IgnitorDsl.OptimizerHint -> copy(inner = new[0])
        is IgnitorDsl.Param -> this
        is IgnitorDsl.PerlinNoise -> copy(rate = new[0], octaves = new[1], persistence = new[2])
        is IgnitorDsl.Phaser -> copy(inner = new[0], rate = new[1], blend = new[2], center = new[3], sweep = new[4])
        is IgnitorDsl.PinkNoise -> this
        is IgnitorDsl.PitchEnvelope -> copy(
            inner = new[0],
            amount = new[1],
            attackSec = new[2],
            decaySec = new[3],
            releaseSec = new[4],
            curve = new[5],
            anchor = new[6],
        )
        is IgnitorDsl.PitchMod -> copy(inner = new[0], mod = new[1])
        is IgnitorDsl.Pluck -> copy(
            freq = new[0],
            decay = new[1],
            brightness = new[2],
            pickPosition = new[3],
            stiffness = new[4],
            analog = new[5],
        )
        is IgnitorDsl.Plus -> copy(left = new[0], right = new[1])
        is IgnitorDsl.Pow -> copy(base = new[0], exp = new[1])
        is IgnitorDsl.Pulze -> copy(freq = new[0], duty = new[1], analog = new[2])
        is IgnitorDsl.Ramp -> copy(freq = new[0], analog = new[1])
        is IgnitorDsl.Range -> copy(inner = new[0], lo = new[1], hi = new[2])
        is IgnitorDsl.RawPulze -> copy(freq = new[0], duty = new[1], analog = new[2])
        is IgnitorDsl.Recip -> copy(inner = new[0])
        is IgnitorDsl.Round -> copy(inner = new[0])
        is IgnitorDsl.Sawtooth -> copy(freq = new[0], analog = new[1])
        is IgnitorDsl.Select -> copy(cond = new[0], whenTrue = new[1], whenFalse = new[2])
        is IgnitorDsl.Shimmer -> copy(inner = new[0], blend = new[1], feedback = new[2], tone = new[3])
        is IgnitorDsl.Sign -> copy(inner = new[0])
        is IgnitorDsl.Silence -> this
        is IgnitorDsl.Sine -> copy(freq = new[0], analog = new[1])
        is IgnitorDsl.Sq -> copy(inner = new[0])
        is IgnitorDsl.Sqrt -> copy(inner = new[0])
        is IgnitorDsl.Square -> copy(freq = new[0], analog = new[1])
        is IgnitorDsl.SuperPluck -> copy(
            freq = new[0],
            voices = new[1],
            spread = new[2],
            decay = new[3],
            brightness = new[4],
            pickPosition = new[5],
            stiffness = new[6],
            analog = new[7],
        )
        is IgnitorDsl.SuperRamp -> copy(freq = new[0], voices = new[1], spread = new[2], analog = new[3])
        is IgnitorDsl.SuperSaw -> copy(freq = new[0], voices = new[1], spread = new[2], analog = new[3])
        is IgnitorDsl.SuperSine -> copy(freq = new[0], voices = new[1], spread = new[2], analog = new[3])
        is IgnitorDsl.SuperSquare -> copy(freq = new[0], voices = new[1], spread = new[2], analog = new[3])
        is IgnitorDsl.SuperTri -> copy(freq = new[0], voices = new[1], spread = new[2], analog = new[3])
        is IgnitorDsl.Tanh -> copy(inner = new[0])
        is IgnitorDsl.Times -> copy(left = new[0], right = new[1])
        is IgnitorDsl.Tremolo -> copy(inner = new[0], rate = new[1], depth = new[2])
        is IgnitorDsl.Triangle -> copy(freq = new[0], analog = new[1])
        is IgnitorDsl.Unipolar -> copy(inner = new[0])
        is IgnitorDsl.Variants -> copy(children = new)
        is IgnitorDsl.Vibrato -> copy(inner = new[0], rate = new[1], depth = new[2])
        is IgnitorDsl.WhiteNoise -> copy(color = new[0])
        is IgnitorDsl.Zamp -> copy(freq = new[0], analog = new[1])
        is IgnitorDsl.Zawtooth -> copy(freq = new[0], analog = new[1])
    }
}

/** Child enumeration for one EQ section. See [childNodes] for the ordering contract. */
fun IgnitorDsl.EqSection.childNodes(): List<IgnitorDsl> {
    return when (this) {
        is IgnitorDsl.EqSection.Bandpass -> listOf(freqHz, q)
        is IgnitorDsl.EqSection.Bell -> listOf(freqHz, q, db)
        is IgnitorDsl.EqSection.Highpass -> listOf(freqHz, q)
        is IgnitorDsl.EqSection.Lowpass -> listOf(freqHz, q)
        is IgnitorDsl.EqSection.Notch -> listOf(freqHz, q)
        is IgnitorDsl.EqSection.RawTap -> listOf(freqHz, q, gain)
    }
}

/** Rebuilds one EQ section with [new] in place of [childNodes], positionally. */
fun IgnitorDsl.EqSection.withChildNodes(new: List<IgnitorDsl>): IgnitorDsl.EqSection {
    require(new.size == childNodes().size) {
        "withChildNodes size mismatch for ${this::class.simpleName}: expected ${childNodes().size}, got ${new.size}"
    }

    return when (this) {
        is IgnitorDsl.EqSection.Bandpass -> copy(freqHz = new[0], q = new[1])
        is IgnitorDsl.EqSection.Bell -> copy(freqHz = new[0], q = new[1], db = new[2])
        is IgnitorDsl.EqSection.Highpass -> copy(freqHz = new[0], q = new[1])
        is IgnitorDsl.EqSection.Lowpass -> copy(freqHz = new[0], q = new[1])
        is IgnitorDsl.EqSection.Notch -> copy(freqHz = new[0], q = new[1])
        is IgnitorDsl.EqSection.RawTap -> copy(freqHz = new[0], q = new[1], gain = new[2])
    }
}
