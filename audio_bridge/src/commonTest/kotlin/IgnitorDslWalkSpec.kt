/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Round-trip contract for [childNodes]/[withChildNodes] over EVERY [IgnitorDsl] node type.
 *
 * The exhaustive `when` in `IgnitorDslWalk.kt` makes the COMPILER force a new node type to get
 * arms; this corpus forces the new type to get COVERAGE (the count assertion below fails until
 * an entry is added). Together they close the hole where a node silently stops being descended
 * into and optimizations quietly stop firing beneath it.
 *
 * Each node is built with a distinct marker per child slot, so the round-trip detects the two
 * failure modes that matter: a slot read in one order and written in another, and a child that
 * is enumerated but not rebuilt (or vice versa).
 */
class IgnitorDslWalkSpec : StringSpec({

    // Distinct, order-revealing markers. PARAM-backed, not Constant, and that is load-bearing:
    // Constant.collectParams is a no-op, so with Constant markers the collectParams cross-check
    // below would compare emptySet() against emptySet() for all 77 non-Param rows and guard
    // exactly nothing. The name carries the slot index so a swapped pair is visible.
    fun m(i: Int): IgnitorDsl = IgnitorDsl.Param("p$i", 100.0 + i)

    /**
     * Every node, with EVERY IgnitorDsl slot filled by a distinct marker and the expected child
     * count stated independently. The declared count is what makes a MISSING SLOT detectable:
     * a corpus entry that simply omits a slot (as this one did for `Dust.bipolar`, which shipped
     * a real bug) round-trips perfectly against a walker arm that also omits it.
     */
    val corpus: List<Triple<String, IgnitorDsl, Int>> = listOf(
        Triple("Abs", IgnitorDsl.Abs(inner = m(0)), 1),
        Triple("Accelerate", IgnitorDsl.Accelerate(inner = m(0), amount = m(1)), 2),
        Triple("Adsr", IgnitorDsl.Adsr(
                inner = m(0),
                attackSec = m(1),
                decaySec = m(2),
                sustainLevel = m(3),
                releaseSec = m(4),
                declickSeconds = m(5),
                expK = m(6),
                attackCurve = AdsrCurve.Linear,
                decayCurve = AdsrCurve.Linear,
                releaseCurve = AdsrCurve.Linear,
            ), 7),
        Triple("Bandpass", IgnitorDsl.Bandpass(inner = m(0), cutoffHz = m(1), q = m(2), analog = m(3)), 4),
        Triple("BerlinNoise", IgnitorDsl.BerlinNoise(rate = m(0), octaves = m(1), persistence = m(2)), 3),
        Triple("Bipolar", IgnitorDsl.Bipolar(inner = m(0)), 1),
        Triple("BrownNoise", IgnitorDsl.BrownNoise(depth = m(0)), 1),
        Triple("Ceil", IgnitorDsl.Ceil(inner = m(0)), 1),
        Triple("Clamp", IgnitorDsl.Clamp(inner = m(0), lo = m(1), hi = m(2)), 3),
        Triple("Clip", IgnitorDsl.Clip(inner = m(0), shape = "fold", oversample = 3), 1),
        Triple("Coarse", IgnitorDsl.Coarse(inner = m(0), amount = m(1)), 2),
        Triple("Constant", IgnitorDsl.Constant(0.5), 0),
        Triple("Crackle", IgnitorDsl.Crackle(chaos = m(0)), 1),
        Triple("Crush", IgnitorDsl.Crush(inner = m(0), amount = m(1)), 2),
        Triple("Detune", IgnitorDsl.Detune(inner = m(0), semitones = m(1)), 2),
        Triple("Distort", IgnitorDsl.Distort(inner = m(0), amount = m(1), shape = "fold", oversample = 3), 2),
        Triple("Div", IgnitorDsl.Div(left = m(0), right = m(1)), 2),
        Triple("Drive", IgnitorDsl.Drive(inner = m(0), amount = m(1), driveType = "tube"), 2),
        Triple("Dust", IgnitorDsl.Dust(density = m(0), tail = m(1), bipolar = m(2)), 3),
        Triple("Eq", IgnitorDsl.Eq(
                inner = m(0),
                sections = listOf(
                    IgnitorDsl.EqSection.Bandpass(freqHz = m(1), q = m(2)),
                    IgnitorDsl.EqSection.Bell(freqHz = m(3), q = m(4), db = m(5)),
                    IgnitorDsl.EqSection.Highpass(freqHz = m(6), q = m(7)),
                    IgnitorDsl.EqSection.Lowpass(freqHz = m(8), q = m(9)),
                    IgnitorDsl.EqSection.Notch(freqHz = m(10), q = m(11)),
                    IgnitorDsl.EqSection.RawTap(freqHz = m(12), q = m(13), gain = m(14)),
                ),
            ), 15),
        Triple("Exp", IgnitorDsl.Exp(inner = m(0)), 1),
        Triple("Floor", IgnitorDsl.Floor(inner = m(0)), 1),
        Triple("Fm", IgnitorDsl.Fm(
                carrier = m(0),
                modulator = m(1),
                ratio = m(2),
                depth = m(3),
                envAttackSec = m(4),
                envDecaySec = m(5),
                envSustainLevel = m(6),
                envReleaseSec = m(7),
            ), 8),
        Triple("Frac", IgnitorDsl.Frac(inner = m(0)), 1),
        Triple("Freq", IgnitorDsl.Freq, 0),
        Triple("Highpass", IgnitorDsl.Highpass(inner = m(0), cutoffHz = m(1), q = m(2), analog = m(3)), 4),
        Triple("Impulse", IgnitorDsl.Impulse(freq = m(0), analog = m(1)), 2),
        Triple("Lerp", IgnitorDsl.Lerp(left = m(0), right = m(1), t = m(2)), 3),
        Triple("Log", IgnitorDsl.Log(inner = m(0)), 1),
        Triple("Lowpass", IgnitorDsl.Lowpass(inner = m(0), cutoffHz = m(1), q = m(2), analog = m(3)), 4),
        Triple("Max", IgnitorDsl.Max(left = m(0), right = m(1)), 2),
        Triple("Min", IgnitorDsl.Min(left = m(0), right = m(1)), 2),
        Triple("Minus", IgnitorDsl.Minus(left = m(0), right = m(1)), 2),
        Triple("Mod", IgnitorDsl.Mod(left = m(0), right = m(1)), 2),
        Triple("Neg", IgnitorDsl.Neg(inner = m(0)), 1),
        Triple("Notch", IgnitorDsl.Notch(inner = m(0), cutoffHz = m(1), q = m(2), analog = m(3)), 4),
        Triple("OnePoleLowpass", IgnitorDsl.OnePoleLowpass(inner = m(0), cutoffHz = m(1)), 2),
        Triple("OptimizerHint", IgnitorDsl.OptimizerHint(inner = m(0), on = 7), 1),
        Triple("Param", IgnitorDsl.Param("p", 1.0, description = "hard"), 0),
        Triple("PerlinNoise", IgnitorDsl.PerlinNoise(rate = m(0), octaves = m(1), persistence = m(2)), 3),
        Triple("Phaser", IgnitorDsl.Phaser(inner = m(0), rate = m(1), blend = m(2), center = m(3), sweep = m(4)), 5),
        Triple("PinkNoise", IgnitorDsl.PinkNoise(), 0),
        Triple("PitchEnvelope", IgnitorDsl.PitchEnvelope(
                inner = m(0),
                amount = m(1),
                attackSec = m(2),
                decaySec = m(3),
                releaseSec = m(4),
                curve = m(5),
                anchor = m(6),
            ), 7),
        Triple("PitchMod", IgnitorDsl.PitchMod(inner = m(0), mod = m(1)), 2),
        Triple("Pluck", IgnitorDsl.Pluck(
                freq = m(0),
                decay = m(1),
                brightness = m(2),
                pickPosition = m(3),
                stiffness = m(4),
                analog = m(5),
            ), 6),
        Triple("Plus", IgnitorDsl.Plus(left = m(0), right = m(1)), 2),
        Triple("Pow", IgnitorDsl.Pow(base = m(0), exp = m(1)), 2),
        Triple("Pulze", IgnitorDsl.Pulze(freq = m(0), duty = m(1), analog = m(2), flankSamples = 7.5, riseFlank = 7.5, fallFlank = 7.5), 3),
        Triple("Ramp", IgnitorDsl.Ramp(freq = m(0), analog = m(1), resetSamples = 7.5, shapeMax = 7.5), 2),
        Triple("Range", IgnitorDsl.Range(inner = m(0), lo = m(1), hi = m(2)), 3),
        Triple("RawPulze", IgnitorDsl.RawPulze(freq = m(0), duty = m(1), analog = m(2)), 3),
        Triple("Recip", IgnitorDsl.Recip(inner = m(0)), 1),
        Triple("Round", IgnitorDsl.Round(inner = m(0)), 1),
        Triple("Sawtooth", IgnitorDsl.Sawtooth(freq = m(0), analog = m(1), resetSamples = 7.5, shapeMax = 7.5), 2),
        Triple("Select", IgnitorDsl.Select(cond = m(0), whenTrue = m(1), whenFalse = m(2)), 3),
        Triple("Shimmer", IgnitorDsl.Shimmer(inner = m(0), blend = m(1), feedback = m(2), tone = m(3), pitches = listOf(3.0, 7.0)), 4),
        Triple("Sign", IgnitorDsl.Sign(inner = m(0)), 1),
        Triple("Silence", IgnitorDsl.Silence, 0),
        Triple("Sine", IgnitorDsl.Sine(freq = m(0), analog = m(1)), 2),
        Triple("Sq", IgnitorDsl.Sq(inner = m(0)), 1),
        Triple("Sqrt", IgnitorDsl.Sqrt(inner = m(0)), 1),
        Triple("Square", IgnitorDsl.Square(freq = m(0), analog = m(1)), 2),
        Triple("SuperPluck", IgnitorDsl.SuperPluck(
                freq = m(0),
                voices = m(1),
                spread = m(2),
                decay = m(3),
                brightness = m(4),
                pickPosition = m(5),
                stiffness = m(6),
                analog = m(7),
            ), 8),
        Triple("SuperRamp", IgnitorDsl.SuperRamp(freq = m(0), voices = m(1), spread = m(2), analog = m(3), spreadPower = 7.5, sideAtten = 7.5, gainJitter = 7.5, centerJitterScale = 7.5, phasePool = 7.5, drawTries = 7.5, kMin = 7.5, kMax = 7.5, poolSize = 7.5, refreshEvery = 7.5, selection = 7.5, warmup = 7.5), 4),
        Triple("SuperSaw", IgnitorDsl.SuperSaw(freq = m(0), voices = m(1), spread = m(2), analog = m(3), spreadPower = 7.5, sideAtten = 7.5, gainJitter = 7.5, centerJitterScale = 7.5, phasePool = 7.5, drawTries = 7.5, kMin = 7.5, kMax = 7.5, poolSize = 7.5, refreshEvery = 7.5, selection = 7.5, warmup = 7.5), 4),
        Triple("SuperSine", IgnitorDsl.SuperSine(freq = m(0), voices = m(1), spread = m(2), analog = m(3), spreadPower = 7.5, sideAtten = 7.5, gainJitter = 7.5, centerJitterScale = 7.5, phasePool = 7.5, drawTries = 7.5, kMin = 7.5, kMax = 7.5, poolSize = 7.5, refreshEvery = 7.5, selection = 7.5, warmup = 7.5), 4),
        Triple("SuperSquare", IgnitorDsl.SuperSquare(freq = m(0), voices = m(1), spread = m(2), analog = m(3), spreadPower = 7.5, sideAtten = 7.5, gainJitter = 7.5, centerJitterScale = 7.5, phasePool = 7.5, drawTries = 7.5, kMin = 7.5, kMax = 7.5, poolSize = 7.5, refreshEvery = 7.5, selection = 7.5, warmup = 7.5), 4),
        Triple("SuperTri", IgnitorDsl.SuperTri(freq = m(0), voices = m(1), spread = m(2), analog = m(3), spreadPower = 7.5, sideAtten = 7.5, gainJitter = 7.5, centerJitterScale = 7.5, phasePool = 7.5, drawTries = 7.5, kMin = 7.5, kMax = 7.5, poolSize = 7.5, refreshEvery = 7.5, selection = 7.5, warmup = 7.5), 4),
        Triple("Tanh", IgnitorDsl.Tanh(inner = m(0)), 1),
        Triple("Times", IgnitorDsl.Times(left = m(0), right = m(1)), 2),
        Triple("Tremolo", IgnitorDsl.Tremolo(inner = m(0), rate = m(1), depth = m(2)), 3),
        Triple("Triangle", IgnitorDsl.Triangle(freq = m(0), analog = m(1)), 2),
        Triple("Unipolar", IgnitorDsl.Unipolar(inner = m(0)), 1),
        Triple("Variants", IgnitorDsl.Variants(listOf(m(0), m(1), m(2))), 3),
        Triple("Vibrato", IgnitorDsl.Vibrato(inner = m(0), rate = m(1), depth = m(2)), 3),
        Triple("WhiteNoise", IgnitorDsl.WhiteNoise(color = m(0)), 1),
        Triple("Zamp", IgnitorDsl.Zamp(freq = m(0), analog = m(1)), 2),
        Triple("Zawtooth", IgnitorDsl.Zawtooth(freq = m(0), analog = m(1)), 2),
    )

    "the corpus covers every IgnitorDsl node type" {
        // Bump this together with a new node's walker arms and its corpus entry.
        corpus.size shouldBe 78
        corpus.map { it.first }.toSet().size shouldBe 78
    }

    "every node reports exactly the declared number of children" {
        // The guard that would have caught Dust.bipolar: the count is stated here from the
        // DECLARATION, so a walker arm that forgets a slot disagrees with it.
        for ((name, node, expected) in corpus) {
            withClue(name) { node.childNodes().size shouldBe expected }
        }
    }

    "childNodes reaches every param the node itself collects" {
        // Independent cross-check against the node's OWN collectParams, which walks all its
        // children by hand. Catches a forgotten slot generically, without trusting the count
        // above: a slot missing from the walker hides params that collectParams still finds.
        for ((name, node, _) in corpus) {
            // Param is the one node that collects ITSELF rather than a child, so it is the one
            // place where "reachable via children" legitimately differs from collectParams.
            if (node is IgnitorDsl.Param) {
                continue
            }

            withClue(name) {
                val viaWalker = mutableListOf<IgnitorDsl.Param>()
                node.childNodes().forEach { it.collectParams(viaWalker) }
                val viaNode = mutableListOf<IgnitorDsl.Param>().also { node.collectParams(it) }

                viaWalker.map { it.name }.toSet() shouldBe viaNode.map { it.name }.toSet()
            }
        }
    }

    "childNodes reports the children in constructor order" {
        for ((name, node, _) in corpus) {
            withClue(name) {
                val kids = node.childNodes()
                // Markers were assigned in constructor order, so reading them back in the same
                // order is what pins the enumeration (a swapped pair shows up as 101.0, 100.0).
                kids shouldBe kids.sortedBy { (it as? IgnitorDsl.Param)?.default ?: 0.0 }
            }
        }
    }

    "withChildNodes round-trips through childNodes for every node type" {
        for ((name, node, _) in corpus) {
            withClue(name) {
                val replacements = node.childNodes().indices.map { m(it + 900) }
                val rebuilt = node.withChildNodes(replacements)

                rebuilt.childNodes() shouldBe replacements
                rebuilt::class shouldBe node::class
            }
        }
    }

    "withChildNodes with the SAME children reproduces an equal node" {
        for ((name, node, _) in corpus) {
            withClue(name) {
                node.withChildNodes(node.childNodes()) shouldBe node
            }
        }
    }

    "withChildNodes rejects a size mismatch loudly" {
        // Internal invariant, not user input: a mis-chunked list would swap a bell's q into its
        // db. It must throw at build time rather than silently produce a different sound.
        val eq = IgnitorDsl.Sine().eq().band(1200.0).tap(850.0)
        shouldThrow<IllegalArgumentException> { eq.withChildNodes(eq.childNodes().drop(1)) }
        shouldThrow<IllegalArgumentException> { eq.withChildNodes(eq.childNodes() + m(0)) }
    }

    "an Eq re-chunks its sections correctly (variable arity)" {
        // Eq is the only node whose children are CHUNKED ACROSS SUB-OBJECTS: 1 + sum(section
        // arities. Variants is variable-arity too, but it is one flat list). A mis-chunk keeps
        // the child COUNT identical, so the generic round-trip above cannot see it.
        val eq = IgnitorDsl.Sine().eq().band(1200.0, 0.9, 6.0).tap(850.0, 0.8, 1.7)
        val replaced = eq.withChildNodes(eq.childNodes().indices.map { m(it + 500) })
                as IgnitorDsl.Eq

        val bell = replaced.sections[0] as IgnitorDsl.EqSection.Bell
        val tap = replaced.sections[1] as IgnitorDsl.EqSection.RawTap
        (replaced.inner as IgnitorDsl.Param).default shouldBe 600.0
        (bell.freqHz as IgnitorDsl.Param).default shouldBe 601.0
        (bell.q as IgnitorDsl.Param).default shouldBe 602.0
        (bell.db as IgnitorDsl.Param).default shouldBe 603.0
        (tap.freqHz as IgnitorDsl.Param).default shouldBe 604.0
        (tap.q as IgnitorDsl.Param).default shouldBe 605.0
        (tap.gain as IgnitorDsl.Param).default shouldBe 606.0
    }
})
