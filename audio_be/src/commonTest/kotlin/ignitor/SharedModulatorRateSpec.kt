/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import kotlin.math.abs
import kotlin.random.Random

/**
 * One stateful modulator shared by two oscillators at different pitches (`docs/tasks-archive/2026-10/20261007-shared-modulator-memo-rate.md`).
 *
 * An oscillator renders its control inputs (`phase`, `duty`) at its OWN resolved frequency, and the fm modulator runs
 * at the fm frequency times the ratio, so one shared [MemoizingIgnitor] is called with two `freqHz` values per block.
 * While the memo keyed on `freqHz` for every subtree, both calls missed and a shared LFO advanced twice per block:
 * double rate, each reader getting alternate blocks. A subtree that reads no [IgnitorDsl.Freq] renders the same
 * samples at any `freqHz`, so its memo now ignores it ([MemoizingIgnitor.freqInvariant]).
 *
 * The oracle of every render row is the same tree built twice, once per layer, and summed: two independent modulators
 * advancing once per block each. Every row is deterministic (`analog = 0`, no noise), so the two must be equal.
 */
class SharedModulatorRateSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val blocks = 40
    val noteHz = 220.0

    fun renderNode(dsl: IgnitorDsl): DoubleArray {
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = sampleRate,
            gateEndFrame = sampleRate,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = Random(7),
        )
        val ignitor = dsl.buildExciter(random = ctx.random, freqHz = noteHz, sampleRate = sampleRate).ignitor
        val buffer = AudioBuffer(blockFrames)
        val out = DoubleArray(blocks * blockFrames)

        for (b in 0 until blocks) {
            ctx.updateOffsetAndLength(0, blockFrames)
            ignitor.generate(buffer, noteHz, ctx)

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buffer[i]
            }

            ctx.voiceElapsedFrames += blockFrames
        }

        return out
    }

    fun c(v: Double) = IgnitorDsl.Constant(v)

    /** Max difference between the two layers sharing one tree and the two layers built separately and summed. */
    fun sharedAgainstSeparate(a: IgnitorDsl, b: IgnitorDsl): Double {
        val shared = renderNode(IgnitorDsl.Plus(a, b))
        val sa = renderNode(a)
        val sb = renderNode(b)
        var worst = 0.0

        for (i in shared.indices) {
            worst = maxOf(worst, abs(shared[i] - (sa[i] + sb[i])))
        }

        return worst
    }

    fun peak(dsl: IgnitorDsl): Double = renderNode(dsl).maxOf { abs(it) }

    val upper = IgnitorDsl.Times(IgnitorDsl.Freq, c(1.5))

    fun phaseLfo() = IgnitorDsl.Times(IgnitorDsl.Sine(freq = c(30.0), analog = c(0.0)), c(0.3))

    fun dutyLfo() = IgnitorDsl.Plus(IgnitorDsl.Times(IgnitorDsl.Sine(freq = c(30.0), analog = c(0.0)), c(0.2)), c(0.5))

    "a phase LFO shared by two oscillators at the SAME pitch renders as two separate LFOs (control row)" {
        val lfo = phaseLfo()
        val a = IgnitorDsl.Saw(analog = c(0.0), phase = lfo)
        // A tri, not a ramp: a saw plus a ramp at one phase cancel to silence.
        val b = IgnitorDsl.Tri(analog = c(0.0), phase = lfo)

        peak(IgnitorDsl.Plus(a, b)) shouldBeGreaterThan 0.1
        sharedAgainstSeparate(a, b) shouldBe 0.0
    }

    "a phase LFO shared by two oscillators at DIFFERENT pitches runs once per block" {
        val lfo = phaseLfo()
        val a = IgnitorDsl.Saw(analog = c(0.0), phase = lfo)
        val b = IgnitorDsl.Saw(freq = upper, analog = c(0.0), phase = lfo)

        peak(IgnitorDsl.Plus(a, b)) shouldBeGreaterThan 0.1
        sharedAgainstSeparate(a, b) shouldBe 0.0
    }

    "a duty LFO shared by two pulses at DIFFERENT pitches runs once per block" {
        val duty = dutyLfo()
        val a = IgnitorDsl.Pulze(analog = c(0.0), duty = duty)
        val b = IgnitorDsl.Pulze(freq = upper, analog = c(0.0), duty = duty)

        peak(IgnitorDsl.Plus(a, b)) shouldBeGreaterThan 0.1
        sharedAgainstSeparate(a, b) shouldBe 0.0
    }

    "an fm modulator also heard on the spine runs once per block at ratio 2" {
        // The fm modulator is driven at `freq x ratio`, the spine at the note: two freqHz values per block.
        val m = IgnitorDsl.Sine(freq = c(310.0), analog = c(0.0))
        val a = IgnitorDsl.Fm(carrier = IgnitorDsl.Sine(analog = c(0.0)), modulator = m, ratio = c(2.0), depth = c(150.0))

        peak(IgnitorDsl.Plus(a, m)) shouldBeGreaterThan 0.1
        sharedAgainstSeparate(a, m) shouldBe 0.0
    }

    "RESIDUE (author rule): a shared modulator that reads Freq anywhere renders once per pitch, its LFO too" {
        // The rule authors follow (`ignitor-reference.md`): a shared modulator that reads `Ignitor.freq()` anywhere
        // (here only a scaling next to a pitch-free LFO) keeps its freq key and renders once per pitch, so its LFO
        // runs twice per block; build it once per layer. A build-time fix (re-pitching doors) was built in review
        // round 1 and dropped by the maintainer on 2026-10-07 for its weight. These shapes pin the residue.
        val lfo = IgnitorDsl.Sine(freq = c(30.0), analog = c(0.0))
        val tracked = IgnitorDsl.Times(lfo, IgnitorDsl.Times(IgnitorDsl.Freq, c(0.002)))

        sharedAgainstSeparate(
            IgnitorDsl.Saw(analog = c(0.0), phase = tracked),
            IgnitorDsl.Saw(freq = upper, analog = c(0.0), phase = tracked),
        ) shouldBeGreaterThan 0.1

        val wob = IgnitorDsl.Times(IgnitorDsl.Sine(freq = c(30.0), analog = c(0.0)), c(0.3))
        val duty = IgnitorDsl.Plus(IgnitorDsl.Times(wob, IgnitorDsl.Div(c(220.0), IgnitorDsl.Freq)), c(0.5))

        sharedAgainstSeparate(
            IgnitorDsl.Pulze(analog = c(0.0), duty = duty),
            IgnitorDsl.Pulze(freq = upper, analog = c(0.0), duty = duty),
        ) shouldBeGreaterThan 0.1
    }

    "RESIDUE (a maintainer decision): an LFO whose RATE reads Freq, shared at two pitches, still runs twice per block" {
        // Under each oscillator Freq is that oscillator's own pitch, so the two readers want two different LFOs; one
        // shared instance renders at both rates and advances twice. This row flips to `shouldBe 0.0` the day a pitch
        // context forks it (`docs/tasks-archive/2026-10/20261007-shared-modulator-memo-rate.md`, the residue); until then authors build it
        // once per layer.
        val lfo = IgnitorDsl.Times(IgnitorDsl.Sine(freq = IgnitorDsl.Div(IgnitorDsl.Freq, c(8.0)), analog = c(0.0)), c(0.3))
        val a = IgnitorDsl.Saw(analog = c(0.0), phase = lfo)
        val b = IgnitorDsl.Saw(freq = upper, analog = c(0.0), phase = lfo)

        sharedAgainstSeparate(a, b) shouldBeGreaterThan 0.1
    }

    // ── One pitch mod over several pitched sources (review round 1, B-1) ─────────────────────────────────────────
    // The oracle: the same mod given to each layer on its own, summed. Each layer's own mod advances once per block.

    fun vibrato(inner: IgnitorDsl, rate: IgnitorDsl = c(2.0), semitones: IgnitorDsl = c(1.0)) =
        IgnitorDsl.Vibrato(inner = inner, rate = rate, semitones = semitones)

    "a vibrato over ONE source renders bit for bit what the mod applied by hand renders (the mod memo always caches)" {
        // The mod's memo caches per block even with one reader; the copy must hand over the very samples the mod
        // writes, so a single-source voice is unchanged by B-1.
        val auto = renderNode(vibrato(IgnitorDsl.Saw(analog = c(0.0))))
        val manual = run {
            val ctx = IgniteContext(
                sampleRate = sampleRate,
                voiceDurationFrames = sampleRate,
                gateEndFrame = sampleRate,
                scratchBuffers = ScratchBuffers(blockFrames),
                random = Random(7),
            )
            val saw = IgnitorDsl.Saw(analog = c(0.0)).buildExciter(random = ctx.random, freqHz = noteHz, sampleRate = sampleRate).ignitor
            val ignitor = ModApplyingIgnitor(saw, vibratoModIgnitor(ConstantIgnitor(2.0), ConstantIgnitor(1.0)))
            val buffer = AudioBuffer(blockFrames)
            val out = DoubleArray(blocks * blockFrames)

            for (b in 0 until blocks) {
                ctx.updateOffsetAndLength(0, blockFrames)
                ignitor.generate(buffer, noteHz, ctx)
                buffer.copyInto(out, b * blockFrames, 0, blockFrames)
                ctx.voiceElapsedFrames += blockFrames
            }

            out
        }

        auto.maxOf { abs(it) } shouldBeGreaterThan 0.1
        auto.map { it.toRawBits() } shouldBe manual.map { it.toRawBits() }
    }

    "one vibrato over a sine and a tri at one pitch runs its LFO once per block" {
        val a = IgnitorDsl.Sine(analog = c(0.0))
        val b = IgnitorDsl.Tri(analog = c(0.0))
        val shared = renderNode(vibrato(IgnitorDsl.Plus(a, b)))
        val sa = renderNode(vibrato(a))
        val sb = renderNode(vibrato(b))

        shared.maxOf { abs(it) } shouldBeGreaterThan 0.1
        shared.indices.maxOf { abs(shared[it] - (sa[it] + sb[it])) } shouldBe 0.0
    }

    "one vibrato over a saw at the note and a saw at 1.5 times it runs its LFO once per block" {
        val a = IgnitorDsl.Saw(analog = c(0.0))
        val b = IgnitorDsl.Saw(freq = upper, analog = c(0.0))
        val shared = renderNode(vibrato(IgnitorDsl.Plus(a, b), rate = c(5.0)))
        val sa = renderNode(vibrato(a, rate = c(5.0)))
        val sb = renderNode(vibrato(b, rate = c(5.0)))

        shared.maxOf { abs(it) } shouldBeGreaterThan 0.1
        shared.indices.maxOf { abs(shared[it] - (sa[it] + sb[it])) } shouldBe 0.0
    }

    "a vibrato whose depth is an LFO, over two sources, reads that LFO once per block" {
        val depth = IgnitorDsl.Plus(IgnitorDsl.Times(IgnitorDsl.Sine(freq = c(0.7), analog = c(0.0)), c(0.5)), c(0.5))
        val a = IgnitorDsl.Sine(analog = c(0.0))
        val b = IgnitorDsl.Tri(analog = c(0.0))
        val shared = renderNode(vibrato(IgnitorDsl.Plus(a, b), semitones = depth))
        val sa = renderNode(vibrato(a, semitones = depth))
        val sb = renderNode(vibrato(b, semitones = depth))

        shared.maxOf { abs(it) } shouldBeGreaterThan 0.1
        shared.indices.maxOf { abs(shared[it] - (sa[it] + sb[it])) } shouldBe 0.0
    }

    "one vibrato over a saw and the same saw detuned an octave runs its LFO once per block" {
        // The detuned source calls the mod at twice the note: the mod's memo must drop its freq key.
        val a = IgnitorDsl.Saw(analog = c(0.0))
        val b = IgnitorDsl.Detune(inner = IgnitorDsl.Saw(analog = c(0.0)), semitones = c(12.0))
        val shared = renderNode(vibrato(IgnitorDsl.Plus(a, b)))
        val sa = renderNode(vibrato(a))
        val sb = renderNode(vibrato(b))

        shared.maxOf { abs(it) } shouldBeGreaterThan 0.1
        shared.indices.maxOf { abs(shared[it] - (sa[it] + sb[it])) } shouldBe 0.0
    }

    "a vibrato over one source and a pitch-enveloped one runs its LFO once per block" {
        // The outer mod has two readers: the plain source, and the inner pitch envelope's product.
        fun penv(inner: IgnitorDsl) = IgnitorDsl.PitchEnvelope(inner = inner, semitones = c(7.0), attackSec = c(0.01), decaySec = c(0.3))

        val a = IgnitorDsl.Sine(analog = c(0.0))
        val b = IgnitorDsl.Tri(analog = c(0.0))
        val shared = renderNode(vibrato(IgnitorDsl.Plus(a, penv(b))))
        val sa = renderNode(vibrato(a))
        val sb = renderNode(vibrato(penv(b)))

        shared.maxOf { abs(it) } shouldBeGreaterThan 0.1
        shared.indices.maxOf { abs(shared[it] - (sa[it] + sb[it])) } shouldBe 0.0
    }

    "one fm over a sum of a sine and a tri runs its modulator once per block" {
        fun fm(carrier: IgnitorDsl) = IgnitorDsl.Fm(
            carrier = carrier,
            modulator = IgnitorDsl.Sine(freq = c(310.0), analog = c(0.0)),
            ratio = c(2.0),
            depth = c(200.0),
        )

        val a = IgnitorDsl.Sine(analog = c(0.0))
        val b = IgnitorDsl.Tri(analog = c(0.0))
        val shared = renderNode(fm(IgnitorDsl.Plus(a, b)))
        val sa = renderNode(fm(a))
        val sb = renderNode(fm(b))

        shared.maxOf { abs(it) } shouldBeGreaterThan 0.1
        shared.indices.maxOf { abs(shared[it] - (sa[it] + sb[it])) } shouldBe 0.0
    }

    "the build resolves the freq key at the share: kept for a Freq reader, dropped for a Freq-free node" {
        val cache = IgnitorBuildCache(freqHz = noteHz)
        val reader = IgnitorDsl.Sine(analog = c(0.0))
        val lfo = phaseLfo()

        // Two visits each: the second is the share, where the key is resolved.
        val readerMemo = reader.buildIgnitor(null, cache).ignitor as MemoizingIgnitor
        reader.buildIgnitor(null, cache)
        val lfoMemo = lfo.buildIgnitor(null, cache).ignitor as MemoizingIgnitor
        lfo.buildIgnitor(null, cache)

        readerMemo.freqInvariant shouldBe false
        lfoMemo.freqInvariant shouldBe true
    }

    "a stateless input that reads Freq, shared at two pitches, gives each oscillator its own pitch's value" {
        // An adsr is a pure function of the frame and the freq, so its value per pitch is exact: the kept freq key
        // renders it once per pitch and each saw reads what a separate build reads. A memo that dropped the key would
        // hand the second saw the first saw's value (review round 3, the load-bearing direction).
        val tracked = IgnitorDsl.Adsr(
            inner = IgnitorDsl.Times(IgnitorDsl.Freq, c(0.001)),
            attackSec = c(0.05),
            decaySec = c(0.05),
            sustainLevel = c(0.5),
            releaseSec = c(0.1),
        )
        val a = IgnitorDsl.Saw(analog = c(0.0), phase = tracked)
        val b = IgnitorDsl.Saw(freq = upper, analog = c(0.0), phase = tracked)

        peak(IgnitorDsl.Plus(a, b)) shouldBeGreaterThan 0.1
        sharedAgainstSeparate(a, b) shouldBe 0.0
    }

    "the freq key: dropped exactly for a subtree that reads no Freq and carries no pitch mod" {
        val cache = IgnitorBuildCache(freqHz = noteHz)
        val mod = ConstantIgnitor(1.0)

        // No Freq anywhere below: the LFO.
        cache.isFreqInvariant(phaseLfo(), null) shouldBe true
        // Freq as the oscillator's own pitch, and Freq deeper down, under an arithmetic node.
        cache.isFreqInvariant(IgnitorDsl.Sine(analog = c(0.0)), null) shouldBe false
        cache.isFreqInvariant(IgnitorDsl.Times(IgnitorDsl.Sine(freq = upper, analog = c(0.0)), c(0.3)), null) shouldBe false
        // Freq-free, but under a pitch mod: the memo wraps the mod too, whose knobs could read Freq.
        cache.isFreqInvariant(phaseLfo(), mod) shouldBe false
    }

    "RESIDUE (second): a pitch mod that keeps its freq key renders again for a layer detuned under it" {
        // FM's `freq` defaults to the note, and a vibrato depth that reads Freq makes its mod read it: such a mod keeps
        // its freq key, the detuned layer calls it at twice the note, and its modulator or LFO advances twice per block
        // (review round 2: 3.82 and 3.41). Build the detuned layer with its own mod.
        val stack = IgnitorDsl.Plus(
            IgnitorDsl.Saw(analog = c(0.0)),
            IgnitorDsl.Detune(inner = IgnitorDsl.Saw(analog = c(0.0)), semitones = c(12.0)),
        )
        val layers = listOf(stack.left, stack.right)

        fun fm(inner: IgnitorDsl) = IgnitorDsl.Fm(
            carrier = inner, modulator = IgnitorDsl.Sine(freq = c(310.0), analog = c(0.0)), ratio = c(2.0), depth = c(150.0),
        )

        fun trackedVibrato(inner: IgnitorDsl) =
            vibrato(inner, rate = c(5.0), semitones = IgnitorDsl.Div(IgnitorDsl.Times(c(0.5), IgnitorDsl.Freq), c(220.0)))

        for (mod in listOf(::fm, ::trackedVibrato)) {
            val shared = renderNode(mod(stack))
            val sa = renderNode(mod(layers[0]))
            val sb = renderNode(mod(layers[1]))

            shared.indices.maxOf { abs(shared[it] - (sa[it] + sb[it])) } shouldBeGreaterThan 0.1
        }
    }

})
