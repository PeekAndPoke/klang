/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.utils.TWO_PI
import io.peekandpoke.klang.audio_be.utils.safeDiv
import io.peekandpoke.klang.audio_be.utils.safeOut
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.classic
import io.peekandpoke.klang.audio_bridge.detune
import io.peekandpoke.klang.audio_bridge.fm
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.audio_bridge.pitchMod
import io.peekandpoke.klang.audio_bridge.plus
import io.peekandpoke.klang.audio_bridge.vibrato
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pitch pipeline step 3b, the general case's guard: the FM rule of decision D1 (every pitch modulation that reaches an
 * fm's carrier also reaches its modulator) for every fm topology, with the mechanism's cost pinned: every memo renders
 * ONCE per block, so a chain of N fms costs N + 1 oscillator renders (the plain candidate, the modulator asking the
 * outer mod at its own frequency, rendered 2^N times; [CarrierFreqMod] says why).
 *
 * **The probe rows.** Every oscillator is the probed [IgnitorDsl.Sample] leaf. It writes a known signal,
 * `0.4 sin(frame * hz * 1e-4)` (so every fm term is visible), and records per call the frequency it was driven at,
 * the ratio stream it read (`ctx.phaseMod`) and what it wrote. Every expected stream is COMPUTED, not rendered: each
 * fm term from the recorded modulator output with [fmModIgnitor]'s own expression, `safeOut(1 + out * depth /
 * safeDiv(fmHz))`, and the products in the build's grouping (the outermost mod first). Rows compare bit for bit.
 * Every lane must render exactly once per block (a second render is a mod rendered twice). The probed leaf is one
 * object, so a lane that must fork under a `detune` reads `Freq` (`p + freq * 0`), and lanes are told apart by their
 * frequency.
 *
 * **The real-sine rows** compare a shape's output with the construction that is its intended result, bit for bit.
 *
 * **One shape the engine does not process, an author rule** (maintainer, 2026-10-07 and 2026-10-09): one fm above a
 * `detune` that forks the note into two pitches. Its RESIDUE rows assert the difference and flip consciously the day
 * a fix lands; the build-time diagnostic is `docs/tasks/fm-above-forking-detune-diagnostic.md`.
 */
class FmModulatorTopologySpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val blocks = 40
    val frames = blocks * blockFrames
    val note = 220.0
    val p = IgnitorDsl.Sample
    val fifth = 2.0.pow(7.0 / 12.0)

    fun c(v: Double) = IgnitorDsl.Constant(v)

    fun sine(analog: Double = 0.0) = IgnitorDsl.Sine(freq = IgnitorDsl.Freq, analog = c(analog))

    /** A probed leaf that reads `Freq`, so a `detune` over it forks. It writes the probe's signal plus 0. */
    fun pf() = p.plus(IgnitorDsl.Freq.mul(0.0))

    val vib: (IgnitorDsl) -> IgnitorDsl = { it.vibrato(rate = 6.0, semitones = 0.5) }

    /** A vibrato whose RATE reads the note: its memo keeps the freq key, the shape that breaks a modulator asking at its own frequency. */
    val vibOnNote: (IgnitorDsl) -> IgnitorDsl = { IgnitorDsl.Vibrato(inner = it, rate = IgnitorDsl.Freq.mul(0.03), semitones = c(0.5)) }

    class Call(val elapsed: Int, val hz: Double, val ratios: DoubleArray, val out: DoubleArray)

    fun probeRender(dsl: IgnitorDsl, bag: Map<String, Double> = emptyMap()): List<Call> {
        val calls = ArrayList<Call>()
        val probe = object : Ignitor {
            override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
                val pm = ctx.phaseMod
                val ratios = DoubleArray(ctx.length) { pm?.get(ctx.offset + it) ?: 1.0 }
                val out = DoubleArray(ctx.length) { 0.4 * sin((ctx.voiceElapsedFrames + it) * freqHz * 1e-4) }

                calls.add(Call(elapsed = ctx.voiceElapsedFrames, hz = freqHz, ratios = ratios, out = out))

                for (i in 0 until ctx.length) {
                    buffer[ctx.offset + i] = out[i]
                }
            }
        }
        val ctx = IgniteContext(sampleRate = sampleRate, voiceDurationFrames = 2000, gateEndFrame = 2000, scratchBuffers = ScratchBuffers(blockFrames), random = Random(3))
        val built = dsl.buildExciter(ignitorParams = bag, random = ctx.random, freqHz = note, sampleRate = sampleRate, sampleSource = probe).ignitor
        val buffer = AudioBuffer(blockFrames)

        for (b in 0 until blocks) {
            ctx.updateOffsetAndLength(offset = 0, length = blockFrames)
            ctx.voiceElapsedFrames = b * blockFrames
            built.generate(buffer, note, ctx)
        }

        return calls
    }

    class Lanes(val calls: List<Call>, private val frames: Int) {
        fun key(hz: Double): Double = calls.map { it.hz }.distinct().singleOrNull { abs(it - hz) < 1e-6 }
            ?: error("no single lane at $hz Hz; lanes: ${calls.map { it.hz }.distinct()}")

        private fun stream(hz: Double, pick: (Call) -> DoubleArray): DoubleArray {
            val k = key(hz)
            val s = DoubleArray(frames)

            for (c in calls.filter { it.hz == k }) {
                pick(c).copyInto(destination = s, destinationOffset = c.elapsed)
            }

            return s
        }

        fun ratios(hz: Double) = stream(hz) { it.ratios }

        fun out(hz: Double) = stream(hz) { it.out }

        /** Renders per block of the lane at [hz], over all blocks (each block the same, or the row fails). */
        fun rendersPerBlock(hz: Double): Set<Int> = calls.filter { it.hz == key(hz) }.groupBy { it.elapsed }.values.map { it.size }.toSet()

        val totalPerBlock: Set<Int> get() = calls.groupBy { it.elapsed }.values.map { it.size }.toSet()
    }

    fun lanes(dsl: IgnitorDsl, bag: Map<String, Double> = emptyMap()) = Lanes(calls = probeRender(dsl, bag), frames = frames)

    /** The fm term [fmModIgnitor] writes (no depth envelope), from the modulator's recorded output. */
    fun term(out: DoubleArray, depth: Double, fmHz: Double) = DoubleArray(out.size) { safeOut((1.0 + out[it] * depth / safeDiv(fmHz))) }

    fun times(a: DoubleArray, b: DoubleArray) = DoubleArray(a.size) { a[it] * b[it] }

    /** The ratio stream a bare probed carrier reads under [wrap]: the pitch node alone. */
    fun alone(wrap: (IgnitorDsl) -> IgnitorDsl, bag: Map<String, Double> = emptyMap()) = lanes(wrap(p), bag).ratios(note)

    fun shouldMatch(what: String, actual: DoubleArray, expected: DoubleArray) {
        val differing = actual.indices.count { actual[it] != expected[it] }

        withClue("$what: frames differing (max ${actual.indices.maxOf { abs(actual[it] - expected[it]) }})") {
            differing shouldBe 0
        }
    }

    // ── Chains: N fms in sequence, x.fm(m1).fm(m2)...; an outer fm FOLLOWS (maintainer, 2026-10-09) ───────────────

    val ratios = listOf(1.5, 2.5, 3.5, 0.5)
    val depths = listOf(300.0, 200.0, 150.0, 100.0)

    fun chain(n: Int): IgnitorDsl {
        var x: IgnitorDsl = p

        for (k in 0 until n) {
            x = x.fm(modulator = p, ratio = ratios[k], depth = depths[k])
        }

        return x
    }

    /**
     * Fm k (1 = innermost) has its modulator at `note * ratio(k)`. Modulator k reads the pitch node above the chain,
     * then fm N, ..., fm k+1 (the outer fms, outermost first); the carrier reads all of them and every fm term.
     * [pitch] is the pitch node alone (null: none). Every lane renders once per block: N + 1 renders, plus [extra].
     */
    fun shouldFollowChain(n: Int, wrap: (IgnitorDsl) -> IgnitorDsl, pitch: DoubleArray?, bag: Map<String, Double> = emptyMap(), extra: Int = 0) {
        val l = lanes(wrap(chain(n)), bag)
        val terms = (0 until n).map { k -> term(out = l.out(note * ratios[k]), depth = depths[k], fmHz = note) }
        val outer = arrayOfNulls<DoubleArray>(n + 1)

        outer[n] = pitch

        for (k in n - 1 downTo 0) {
            val o = outer[k + 1]

            outer[k] = if (o == null) terms[k].copyOf() else times(o, terms[k])
        }

        shouldMatch("the carrier reads the pitch node and every fm term", l.ratios(note), outer[0]!!)

        for (k in 0 until n) {
            val hz = note * ratios[k]
            val reads = l.ratios(hz)
            val expected = outer[k + 1] ?: DoubleArray(frames) { 1.0 }

            shouldMatch("modulator ${k + 1} reads the pitch node and the fms outside its own", reads, expected)

            // The invariant in the oscillators' own terms: its phase increment over the increment of the carrier it
            // modulates (the carrier's pitch without this fm and the ones inside it) is the ratio. Documentation, kept
            // on purpose: the lane is looked up at `note * ratio` and its stream is asserted equal above, so this
            // cannot fail on its own (review round 1, A NIT 3); it states the rule in the units the plan writes it in.
            val worst = reads.indices.maxOf { abs(((TWO_PI * hz / sampleRate) * reads[it]) / ((TWO_PI * note / sampleRate) * expected[it]) / ratios[k] - 1.0) }

            withClue("modulator ${k + 1}: increment ratio error $worst") {
                (worst <= 1e-12) shouldBe true
            }

            l.rendersPerBlock(hz) shouldBe setOf(1)
        }

        withClue("every memo renders once per block: N + 1 oscillator renders") {
            l.totalPerBlock shouldBe setOf(n + 1 + extra)
        }
    }

    "chain: three fms" {
        shouldFollowChain(n = 3, wrap = { it }, pitch = null)
    }

    "chain: four fms" {
        shouldFollowChain(n = 4, wrap = { it }, pitch = null)
    }

    "chain: three fms under a vibrato" {
        shouldFollowChain(n = 3, wrap = vib, pitch = alone(vib))
    }

    "chain: three fms under a vibrato whose rate reads the note (a freq-keyed mod)" {
        shouldFollowChain(n = 3, wrap = vibOnNote, pitch = alone(vibOnNote))
    }

    "chain: four fms under a vibrato whose rate reads the note" {
        shouldFollowChain(n = 4, wrap = vibOnNote, pitch = alone(vibOnNote))
    }

    "chain: three fms under a pitchMod whose probed LFO reads the note: the outer LFO renders once per block" {
        // The LFO is a probed leaf an octave up (lane 2 x note), so its renders are counted like any oscillator's.
        val counted: (IgnitorDsl) -> IgnitorDsl = { it.pitchMod(IgnitorDsl.Detune(inner = pf(), semitones = c(12.0))) }
        val l = lanes(counted(chain(3)))

        l.rendersPerBlock(2.0 * note) shouldBe setOf(1)
        shouldFollowChain(n = 3, wrap = counted, pitch = alone(counted), extra = 1)
    }

    "chain: three fms in classic() under the sprudel vib door (the slot bag vib(6, 0.5) writes)" {
        val bag = mapOf("vibrato.rate" to 6.0, "vibrato.semitones" to 0.5)
        val classic: (IgnitorDsl) -> IgnitorDsl = { it.classic() }

        shouldFollowChain(n = 3, wrap = classic, pitch = alone(classic, bag), bag = bag)
    }

    // ── The general case ─────────────────────────────────────────────────────────────────────────────────────────

    for ((tag, wrap) in listOf("a vibrato" to vib, "a vibrato whose rate reads the note" to vibOnNote)) {
        "nested: a modulator of a modulator of a modulator, under $tag: every level follows" {
            val f3 = p.fm(modulator = p, ratio = 3.5, depth = 150.0)
            val f2 = p.fm(modulator = f3, ratio = 2.5, depth = 200.0)
            val l = lanes(wrap(p.fm(modulator = f2, ratio = 1.5, depth = 300.0)))
            val v = alone(wrap)
            val m3m = note * 1.5 * 2.5 * 3.5
            val m3c = note * 1.5 * 2.5
            val m2c = note * 1.5

            shouldMatch("the innermost modulator reads the vibrato", l.ratios(m3m), v)
            shouldMatch("fm3's carrier reads the vibrato and fm3", l.ratios(m3c), times(l.ratios(m3m), term(out = l.out(m3m), depth = 150.0, fmHz = m3c)))
            shouldMatch("fm2's carrier reads the vibrato and fm2", l.ratios(m2c), times(v, term(out = l.out(m3c), depth = 200.0, fmHz = m2c)))
            shouldMatch("the carrier reads the vibrato and fm1", l.ratios(note), times(v, term(out = l.out(m2c), depth = 300.0, fmHz = note)))
            l.totalPerBlock shouldBe setOf(4)
        }
    }

    "a pitch node at every level: each modulator reads the nodes above its fm times its own; the carrier's own stays the carrier's" {
        val penv: (IgnitorDsl) -> IgnitorDsl = {
            IgnitorDsl.PitchEnvelope(inner = it, semitones = c(12.0), attack = c(0.01), decay = c(0.05), sustain = c(0.0), release = c(0.1))
        }
        val f2 = p.fm(modulator = penv(p), ratio = 2.5, depth = 200.0).vibrato(rate = 4.0, semitones = 0.3)
        val top = p.vibrato(rate = 7.0, semitones = 0.2).fm(modulator = f2, ratio = 1.5, depth = 300.0).vibrato(rate = 6.0, semitones = 0.5)
        val l = lanes(top)
        val above = times(alone(vib), alone({ it.vibrato(rate = 4.0, semitones = 0.3) }))

        shouldMatch("the inner modulator: (outer vibrato * its fm's vibrato) * its own pitch envelope", l.ratios(825.0), times(above, alone(penv)))
        shouldMatch("the inner carrier: (outer vibrato * its fm's vibrato) * fm2", l.ratios(330.0), times(above, term(out = l.out(825.0), depth = 200.0, fmHz = 330.0)))
        shouldMatch(
            "the carrier: (outer vibrato * fm1) * its own vibrato, which reaches no modulator",
            l.ratios(note),
            times(times(alone(vib), term(out = l.out(330.0), depth = 300.0, fmHz = note)), alone({ it.vibrato(rate = 7.0, semitones = 0.2) })),
        )
        l.totalPerBlock shouldBe setOf(3)
    }

    "two summed branches with their own fm chains, one detuned, under one vibrato" {
        val a = p.fm(modulator = p, ratio = 1.5, depth = 300.0).fm(modulator = p, ratio = 2.5, depth = 200.0)
        val b = pf().fm(modulator = p, ratio = 3.5, depth = 150.0).detune(semitones = 7.0)
        val l = lanes(vib(a + b))
        val v = alone(vib)
        val fmA2 = term(out = l.out(550.0), depth = 200.0, fmHz = note)
        val bc = note * fifth

        shouldMatch("A: the outer modulator reads the vibrato", l.ratios(550.0), v)
        shouldMatch("A: the inner modulator reads the vibrato and fmA2", l.ratios(330.0), times(v, fmA2))
        shouldMatch("A: the carrier reads all three", l.ratios(note), times(times(v, fmA2), term(out = l.out(330.0), depth = 300.0, fmHz = note)))
        shouldMatch("B: the modulator reads the vibrato", l.ratios(bc * 3.5), v)
        shouldMatch("B: the carrier reads the vibrato and fmB", l.ratios(bc), times(v, term(out = l.out(bc * 3.5), depth = 150.0, fmHz = bc)))
        l.totalPerBlock shouldBe setOf(5)
    }

    "an fm voice in a parameter position (the outer fm's depth) follows its own vibrato only, never the outer one" {
        val inParam = IgnitorDsl.Detune(inner = pf().fm(modulator = p, ratio = 2.5, depth = 100.0).vibrato(rate = 5.0, semitones = 0.3), semitones = c(7.0))
        val l = lanes(vib(IgnitorDsl.Fm(carrier = p, modulator = p, ratio = c(3.5), depth = inParam)))
        val own = alone({ it.vibrato(rate = 5.0, semitones = 0.3) })
        val pc = note * fifth

        shouldMatch("the outer modulator reads the outer vibrato", l.ratios(770.0), alone(vib))
        shouldMatch("the param fm's modulator reads its own vibrato", l.ratios(pc * 2.5), own)
        shouldMatch("the param fm's carrier reads its own vibrato and its fm", l.ratios(pc), times(own, term(out = l.out(pc * 2.5), depth = 100.0, fmHz = pc)))
        l.totalPerBlock shouldBe setOf(4)
    }

    // ── Shared nodes, real sines: the shape against its intended construction ─────────────────────────────────────

    fun output(dsl: IgnitorDsl): DoubleArray {
        val ctx = IgniteContext(sampleRate = sampleRate, voiceDurationFrames = 2000, gateEndFrame = 2000, scratchBuffers = ScratchBuffers(blockFrames), random = Random(3))
        val built = dsl.buildExciter(random = ctx.random, freqHz = note, sampleRate = sampleRate).ignitor
        val buffer = AudioBuffer(blockFrames)
        val out = DoubleArray(frames)

        for (b in 0 until blocks) {
            ctx.updateOffsetAndLength(offset = 0, length = blockFrames)
            ctx.voiceElapsedFrames = b * blockFrames
            built.generate(buffer, note, ctx)
            buffer.copyInto(destination = out, destinationOffset = b * blockFrames, startIndex = 0, endIndex = blockFrames)
        }

        return out
    }

    "shared: one let as the modulator and on the spine renders as two separate sines (HEAD rendered the one at two pitches)" {
        val m = sine()

        output(vib(sine().fm(modulator = m, ratio = 2.0, depth = 300.0)) + m).toList() shouldBe
            output(vib(sine().fm(modulator = sine(), ratio = 2.0, depth = 300.0)) + sine()).toList()
    }

    "shared: one let as carrier and modulator of the same fm renders as two separate sines" {
        val s = sine()

        output(vib(s.fm(modulator = s, ratio = 1.0, depth = 300.0))).toList() shouldBe
            output(vib(sine().fm(modulator = sine(), ratio = 1.0, depth = 300.0))).toList()
    }

    "shared: one modulator let in two fms at two ratios renders as two modulators (HEAD rendered the one at two pitches)" {
        val m = sine()

        output(vib(sine().fm(modulator = m, ratio = 1.5, depth = 300.0) + sine().fm(modulator = m, ratio = 2.5, depth = 200.0))).toList() shouldBe
            output(vib(sine().fm(modulator = sine(), ratio = 1.5, depth = 300.0) + sine().fm(modulator = sine(), ratio = 2.5, depth = 200.0))).toList()
    }

    "shared: one fm let twice (f + f) under a vibrato keeps its drifting modulator ONE instance: f + f is f * 2" {
        val f = sine().fm(modulator = sine(analog = 0.5), ratio = 1.5, depth = 300.0)

        output(vib(f + f)).toList() shouldBe output(vib(f.mul(2.0))).toList()
    }

    "shared: one fm let under two different vibratos: each copy's modulator follows its own vibrato" {
        val f = sine().fm(modulator = sine(), ratio = 3.5, depth = 400.0)

        output(f.vibrato(rate = 6.0, semitones = 0.5) + f.vibrato(rate = 4.0, semitones = 0.3)).toList() shouldBe
            output(
                sine().fm(modulator = sine(), ratio = 3.5, depth = 400.0).vibrato(rate = 6.0, semitones = 0.5) +
                    sine().fm(modulator = sine(), ratio = 3.5, depth = 400.0).vibrato(rate = 4.0, semitones = 0.3),
            ).toList()
    }

    // ── Render counts inside a modulator, and the per-render pin ─────────────────────────────────────────────────

    "inside: a pitch node inside the modulator, over a forking detune, under a pitch node above the fm renders its LFO once per block" {
        // The modulator's own pitchMod reads a probed, pitch-free LFO (lane at the frequency its memo is asked at). Its
        // memo combines the outer mod's wrapper, which ignores the frequency it is called with, so the memo drops its
        // freq key and the two detuned layers of the modulator share one render (review round 1, A MAJOR 1: it kept
        // the key and rendered at both pitches).
        val modulator = (sine() + sine().detune(semitones = 7.0)).pitchMod(p.mul(0.01))
        val l = lanes(vib(p.fm(modulator = modulator, ratio = 1.5, depth = 300.0)))

        withClue("probe renders per block: the carrier and the modulator's LFO, once each; lanes ${l.calls.map { it.hz }.distinct()}") {
            l.totalPerBlock shouldBe setOf(2)
        }

        l.rendersPerBlock(note) shouldBe setOf(1)
    }

    "inside, nested: a pitch node in the modulator of a modulator, over a forking detune, renders its LFO once per block" {
        // The inner fm's wrapper wraps the outer fm's wrapper, not a memo, so only the `combineMods` clause for a
        // wrapper, whatever it wraps, drops the key here (review round 2, MINOR 2: narrowing the clause to a wrapper
        // over an invariant memo rendered this LFO twice, at 825 and 1236 Hz, even under a constant vibrato).
        val innermost = (sine() + sine().detune(semitones = 7.0)).pitchMod(p.mul(0.01))
        val inner = p.fm(modulator = innermost, ratio = 2.5, depth = 100.0)
        val l = lanes(vib(p.fm(modulator = inner, ratio = 1.5, depth = 300.0)))

        withClue("probe renders per block: the carrier, the inner fm's carrier, the innermost LFO, once each; lanes ${l.calls.map { it.hz }.distinct()}") {
            l.totalPerBlock shouldBe setOf(3)
        }
    }

    "repin: one fm in two detune scopes shares its wrapper, pinned at each carrier's pitch before each render" {
        // The fm node `op` is visited in two detune scopes under the same outer mod, so both visits share one wrapper
        // and the fm re-pins it at 220 Hz and at the fifth before rendering each modulator. The outer mod reads the
        // note (a probed LFO an octave up, so its renders are counted): it renders once per carrier pitch, the
        // memo's freq-key rule, and a stale pin would ask it at the other pitch, a third render and the wrong mod.
        val counted: (IgnitorDsl) -> IgnitorDsl = { it.pitchMod(IgnitorDsl.Detune(inner = pf(), semitones = c(12.0))) }
        val op = p.fm(modulator = p, ratio = 3.5, depth = 400.0)
        val l = lanes(counted(op + op.detune(semitones = 7.0)))
        val fc = note * fifth

        shouldMatch("the note's modulator reads what its carrier reads but its own term", l.ratios(note), times(l.ratios(note * 3.5), term(out = l.out(note * 3.5), depth = 400.0, fmHz = note)))
        shouldMatch("the fifth's modulator reads what its carrier reads but its own term", l.ratios(fc), times(l.ratios(fc * 3.5), term(out = l.out(fc * 3.5), depth = 400.0, fmHz = fc)))
        l.rendersPerBlock(2.0 * note) shouldBe setOf(1)
        l.rendersPerBlock(2.0 * fc) shouldBe setOf(1)
        l.totalPerBlock shouldBe setOf(6)
    }

    // ── The author rule: one fm above a forking detune ───────────────────────────────────────────────────────────

    "RESIDUE (author rule): one fm above a forking detune renders its one modulator at both pitches, once each per block" {
        // With a plain modulator each render reads its own pitch's outer mod, but the ONE modulator renders twice per
        // block (the next RESIDUE rows: with a pitch node inside the modulator under a mod that reads the note, the
        // second render reads the first pitch's outer mod). Author rule (maintainer, 2026-10-09): define the FM on both
        // layers and sum them, `x.fm(m1) + x.fm(m2).detune(7)`.
        val l = lanes(vib((p + IgnitorDsl.Detune(inner = pf(), semitones = c(7.0))).fm(modulator = p, ratio = 3.5, depth = 400.0)))

        shouldMatch("the modulator rendered for the note reads the vibrato", l.ratios(770.0), alone(vib))
        shouldMatch("the modulator rendered for the fifth reads the vibrato", l.ratios(770.0 * fifth), alone(vib))
        l.rendersPerBlock(770.0) shouldBe setOf(1)
        l.rendersPerBlock(770.0 * fifth) shouldBe setOf(1)
    }

    "RESIDUE (author rule): over a carrier at two pitches, under a mod that reads the note, a pitch node inside the modulator reads the first pitch's mod" {
        // Review round 2, MINOR 1, recorded as part of the residue. The fm re-pins its wrapper within the block, once
        // per carrier pitch; a pitch node inside the modulator whose knobs read no Freq is a memo invariant over the
        // wrapper, filled under the first pin. The witness is an exact identity node, `pitchMod(0)`: without it each
        // render reads its own pitch's outer mod, with it the fifth's render reads the note's. Flips consciously the
        // day a fix (a memo keyed by the pin) lands.
        val stack = p + IgnitorDsl.Detune(inner = pf(), semitones = c(7.0))
        val plain = lanes(vibOnNote(stack.fm(modulator = p, ratio = 3.5, depth = 400.0)))
        val withNode = lanes(vibOnNote(stack.fm(modulator = p.pitchMod(c(0.0)), ratio = 3.5, depth = 400.0)))
        val noteModulator = 770.0
        val fifthModulator = 770.0 * fifth

        withClue("a plain modulator: the fifth's render reads the fifth's outer mod, not the note's") {
            plain.ratios(fifthModulator).toList() shouldNotBe plain.ratios(noteModulator).toList()
        }

        shouldMatch("with the identity node: the fifth's render reads the note's outer mod", withNode.ratios(fifthModulator), withNode.ratios(noteModulator))
        shouldMatch("with the identity node: the note's render is the plain one", withNode.ratios(noteModulator), plain.ratios(noteModulator))
    }

    "RESIDUE (author rule): one fm above a forking detune is NOT one operator per layer; the recipe is" {
        val oneFm = vib((sine() + sine().detune(semitones = 7.0)).fm(modulator = sine(), ratio = 3.5, depth = 400.0))
        val perLayer = vib(sine().fm(modulator = sine(), ratio = 3.5, depth = 400.0) + sine().fm(modulator = sine(), ratio = 3.5, depth = 400.0).detune(semitones = 7.0))

        output(oneFm).toList() shouldNotBe output(perLayer).toList()
    }
})
