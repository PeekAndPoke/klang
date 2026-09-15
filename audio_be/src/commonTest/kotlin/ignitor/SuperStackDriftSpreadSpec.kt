/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import kotlin.math.abs
import kotlin.random.Random

/**
 * `analogSpread` on the unison family, heard rather than counted: what the blend does to a stack
 * of voices, and that the unison default is one fixed render nothing may move by accident.
 *
 * The supersine cases run through the DSL and the runtime dispatch (`toExciter`), so a knob lost
 * anywhere between `IgnitorDsl.SuperSine` and the engine shows up here. `gainJitter` is off in
 * them: the jitter DRAW happens after the lanes are drawn, so switching drift on would otherwise
 * shift the jitter values and the renders would differ for a reason that is not the drift.
 *
 * The superpluck cases pass the node rng and the voice rng as two separate streams (production
 * shares one), so the excitation bursts are identical across the compared renders and the only
 * difference left is the drift. A plucked string has no clean beating envelope to measure, unlike
 * two sines: its own modes are slightly inharmonic and its block envelope wobbles at every
 * setting, so the pluck is pinned by what the knob CHANGES, not by an envelope shape.
 */
class SuperStackDriftSpreadSpec : StringSpec({
    val sampleRate = 44100
    val blockFrames = 128

    fun ctx(rng: Random): IgniteContext = IgniteContext(
        sampleRate = sampleRate,
        voiceDurationFrames = sampleRate * 8,
        gateEndFrame = sampleRate * 8,
        releaseFrames = 0,
        scratchBuffers = ScratchBuffers(blockFrames),
        random = rng,
    )

    fun render(sig: Ignitor, freqHz: Double, rng: Random, blocks: Int): DoubleArray {
        val c = ctx(rng)
        val buf = AudioBuffer(blockFrames)
        val out = DoubleArray(blocks * blockFrames)

        for (b in 0 until blocks) {
            c.updateOffsetAndLength(0, blockFrames)
            c.voiceElapsedFrames = b * blockFrames
            sig.generate(buf, freqHz, c)

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buf[i]
            }
        }

        return out
    }

    /** How much the block-wise peak moves across the whole render, as a fraction of the loudest block. */
    fun peakSwing(samples: DoubleArray): Double {
        var lo = Double.MAX_VALUE
        var hi = 0.0

        for (b in 0 until samples.size / blockFrames) {
            var p = 0.0

            for (i in 0 until blockFrames) {
                p = maxOf(p, abs(samples[b * blockFrames + i]))
            }

            lo = minOf(lo, p)
            hi = maxOf(hi, p)
        }

        return (hi - lo) / hi
    }

    fun maxDiff(a: DoubleArray, b: DoubleArray): Double {
        a.size shouldBe b.size
        var d = 0.0

        for (i in a.indices) {
            d = maxOf(d, abs(a[i] - b[i]))
        }

        return d
    }

    /** Two sine voices at ONE pitch (no unison detune), five seconds, through the DSL and runtime. */
    fun sineStack(analog: Double, spread: Double): DoubleArray {
        val rng = Random(9)
        val dsl = IgnitorDsl.SuperSine(
            voices = IgnitorDsl.Constant(2.0),
            spread = IgnitorDsl.Constant(0.0),
            analog = IgnitorDsl.Constant(analog),
            analogSpread = IgnitorDsl.Constant(spread),
            gainJitter = 0.0,
        )

        return render(dsl.toExciter(random = rng), 220.0, rng, 5 * sampleRate / blockFrames)
    }

    /**
     * Funnels every draw through `nextBits`, so lane draws are countable. A sibling of the one in
     * `SuperStackTransitionSpec`; the unit is whatever this platform's `nextDouble` costs, which is
     * why the cost of one lane is measured below rather than written down.
     */
    class CountingRandom(seed: Int) : Random() {
        private val inner = Random(seed)
        var draws = 0

        override fun nextBits(bitCount: Int): Int {
            draws++

            return inner.nextBits(bitCount)
        }
    }

    /** Two strings at ONE pitch, two seconds; excitation stream and drift stream kept apart. */
    fun pluck(analog: Double, spread: Double): DoubleArray {
        val ig = Ignitors.superKarplusStrong(
            freq = ConstantIgnitor(220.0),
            voices = ConstantIgnitor(2.0),
            detune = ConstantIgnitor(0.0),
            analog = ConstantIgnitor(analog),
            analogSpread = ConstantIgnitor(spread),
            rng = Random(1),
        )

        return render(ig, 220.0, Random(2), 2 * sampleRate / blockFrames)
    }

    "analogSpread 0: two voices at one pitch stay ONE sine, and the stack still drifts as a whole" {
        val locked = sineStack(analog = 20.0, spread = 0.0)

        // One walk for both voices keeps their frequencies equal, so the sum is a single sine and
        // its amplitude never moves: no beating.
        (peakSwing(locked) < 0.01) shouldBe true

        // The shared walk is really there: against a stack with the drift off (same start phases,
        // no gain jitter, so nothing else can differ) the wobble moves whole samples.
        (maxDiff(locked, sineStack(analog = 0.0, spread = 0.0)) > 0.1) shouldBe true
    }

    "analogSpread 1: the voices walk apart and the stack beats" {
        (peakSwing(sineStack(analog = 20.0, spread = 1.0)) > 0.1) shouldBe true
    }

    "the default spread 1 renders the same stack today, tomorrow and after the next refactor" {
        // Captured from the engine on this branch, in the commit that gave the shared lane its own
        // seed (the round-1 review batch). It first held the render taken at `0facbd1c`, the commit
        // before DriftLanes, and matched it sample for sample; taking one int off the voice stream
        // for the shared seed moved every later draw of a drifting stack by one, so the numbers
        // below were retaken. Retaken again 2026-09-15, when every lane moved from a step per
        // sample to a step per block with a linear ramp across it (the analog drift was 19 percent
        // of a drifting guitar) and the layers' steady-state sigma took its exact AR(1) form. What
        // the case pins is unchanged: the unison default is one fixed render, and any accidental
        // change to the drift path shows up here as a bit difference.
        val expected = doubleArrayOf(
        0.4661566921865289, 0.4761836881299545, 0.4862106848170367, 0.4962376822477751,
        0.5062646804221701, 0.5162916793402214, 0.526318679001929, 0.5363456794072932,
        0.5058871996161176, 0.28816497915915956, 0.2981919817954929, 0.30821898517548263,
        0.31824598929912873, 0.32827299416643146, 0.33829999977739045, 0.34832700613200585,
        0.3583540132302777, 0.368381021072206, 0.37840802965779063, 0.38843503898703186,
        0.39846204905992916, 0.4084890598764831, 0.41851607143669345, 0.4285430837405601,
        0.4385700967880831, 0.4485971105792627, 0.1957837757504271, 0.1660920844252935,
        0.1761191004474423, 0.18614611721324748, 0.1961731347227091, 0.20620015297582714,
        0.21622717197260158, 0.2262541917130323, 0.2362812121971196, 0.24630823342486338,
        0.25633525539626334, 0.2663622781113198, 0.14935495366851353, -0.017706363468579137,
        -0.0076793385225533955, 0.002347687167128798, 0.012374713600467317, 0.02240174077746232,
        0.03242876869811365, 0.04245579736242146, 0.05248282677038565, 0.06250985692200631,
        0.07253688781728332, 0.08256391945621679, 0.09259095183880661, 0.10261798496505285,
        0.11264501883495556, -0.08711160894164305, -0.15228617485232615, -0.14225913875145424,
        -0.13223210190692591, -0.12220506431874123, -0.29095862156236296, -0.38194487272979294,
        -0.3719178329106389, -0.3618907923478285, -0.3518637510413618, -0.516263070177371,
        -0.614724334391848, -0.6046972908544122, -0.5946702465733196, -0.5846432015485707,
        -0.5746161557801654, -0.5645891092681037, -0.5545620620123857, -0.5445350140130111,
        -0.5345079652699802, -0.5244809157832929, -0.514453865552949, -0.504426814578949,
        -0.4943997628612924, -0.48437271039997937, -0.4743456571950099, -0.46431860324638413,
        -0.454291548554102, -0.44426449311816324, -0.43423743693856826, -0.4242103800153168,
        -0.4141833223484088, -0.4041562639378446, -0.3941292047836239, -0.38410214488574684,
        -0.3740750842442133, -0.3640480228590234, -0.3540209607301771, -0.34399389785767437,
        -0.33396683424151513, -0.3239397698816996, -0.31391270477822764, -0.3038856389310992,
        -0.29385857234031443, -0.2838315050058732, -0.2738044369277755, -0.2637773681060216,
        -0.25375029854061104, -0.2437232282315441, -0.23369615717882086, -0.22366908538244112,
        -0.21364201284240497, -0.2036149395587124, -0.19358786553136348, -0.18356079076035814,
        -0.17353371524569633, -0.16350663898737816, -0.15347956198540358, -0.14345248423977253,
        -0.13342540575048512, -0.12339832651754122, -0.113371246540941, -0.10334416582068441,
        -0.09331708435677132, -0.08329000214920185, -0.07326291919797598, -0.0632358355030937,
        -0.053208751064555, -0.043181665882359915, -0.03315457995650836, -0.023127493287000422,
        -0.013100405873836068, -0.003073317717015245, 0.0069537711834619376, 0.01698086082759545,
        0.027007951215385503, 0.03703504234683187, -0.14680081024014813, -0.23032792789946954,
        -0.22030083785041166, -0.2102737481621499, -0.20024665883468434, -0.19021956986801491,
        -0.1801924812621416, -0.17016539301706454, -0.1601383051327836, -0.1501112176092988,
        -0.14008413044661022, -0.13005704364471785, -0.12002995720362147, -0.11000287112332137,
        -0.09997578540381741, -0.08994870004510958, -0.07992161504719797, -0.06989453041008246,
        -0.05986744613376317, -0.04984036221823997, -0.03981327866351299, -0.02978619546958214,
        -0.019759112636447487, -0.00973203016410891, 2.9505194743345564E-4, 0.010322133698179609,
        0.020349215088129688, 0.03037629611728361, 0.04040337678564131, 0.0504304570932029,
        0.060457537039968326, 0.07048461662593755, 0.0805116958511106, 0.09053877471548748,
        0.10056585321906823, 0.11059293136185283, 0.1206200091438413, 0.1306470865650335,
        0.14067416362542967, 0.15070124032502966, 0.16072831666383347, 0.17075539264184114,
        0.1807824682590526, 0.19080954351546797, 0.20083661841108708, 0.21086369294591,
        0.2208907671199369, 0.23091784093316756, 0.24094491438560206, 0.2509719874772404,
        0.26099906020808256, 0.2710261325781287, 0.2810532045873785, 0.29108027623583216,
        0.30110734752348967, 0.31113441845035095, 0.32116148901641633, 0.33118855922168533,
        0.3412156290661582, 0.3512426985498349, 0.36126976767271546, 0.37129683643480005,
        0.3813239048360883, 0.3913509728765803, 0.40137804055627635, 0.41140510787517603,
        0.4214321748332796, 0.4314592414305871, 0.44148630766709834, 0.45151337354281346,
        0.4615404390577325, 0.47156750421185534, 0.4815945690051819, 0.4916216334377124,
        0.5016486975094466, 0.5116757612203847, 0.5217028245705269, 0.5317298875598727,
        0.5417569501884224, 0.45747172318097185, 0.29357637107330214, 0.3036034326194633,
        0.31363049380482844, 0.32365755462939727, 0.33368461509317, 0.3437116751961466,
        0.35373873493832697, 0.36376579431971123, 0.3737928533402992, 0.38381991200009125,
        0.39384697029908694, 0.4038740282372865, 0.41390108581468993, 0.42392814303129717,
        0.43395519988710823, 0.44398225638212324, 0.32118253398842295, 0.16147731232246704,
        0.1715043677350936, 0.18153142278692383, 0.191558477477958, 0.2015855318081959,
        0.2116125857776378, 0.22163963938628345, 0.23166669263413286, 0.2416937455211862,
        0.25172079804744346, 0.26174785021290453, 0.06857402297237322, -0.022320735779543112,
        -0.012293684696470636, -0.002266633974194174, 0.007760416387285973, 0.017787466387970057,
        0.027814516027857922, 0.03784156530694966, 0.04786861422524512, 0.057895662782744564,
        0.0679227109794479, 0.07794975881535499, 0.0879768062904659, 0.09800385340478061,
        0.1080309001582993, 0.11805794655102178, -0.01544592677535786, -0.14687322540397818,
        )

        val rng = Random(5)
        val ig = Ignitors.superSaw(
            freq = ConstantIgnitor(220.0),
            voices = ConstantIgnitor(7.0),
            analog = ConstantIgnitor(3.0),
            rng = rng,
        )

        val actual = render(ig, 220.0, rng, 2)

        actual.size shouldBe expected.size

        for (i in expected.indices) {
            actual[i].toRawBits() shouldBe expected[i].toRawBits()
        }
    }

    "superpluck: a string that comes back after a shrink draws a FRESH lane" {
        // Two strings, then one, then two again. A regrown string RE-PLUCKS by design, so it must
        // also get a lane that attacks in tune, not the walk it had before it went away. Counted
        // rather than heard: the excitation runs on its own stream, so every draw counted here is
        // a drift lane.
        val stepping = object : Ignitor {
            override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
                for (i in ctx.offset until ctx.windowEnd) {
                    val abs = ctx.voiceElapsedFrames + (i - ctx.offset)

                    buffer[i] = when {
                        abs < blockFrames -> 2.0
                        abs < 2 * blockFrames -> 1.0
                        else -> 2.0
                    }
                }
            }
        }

        val counting = CountingRandom(4)
        val ig = Ignitors.superKarplusStrong(
            freq = ConstantIgnitor(220.0),
            voices = stepping,
            detune = ConstantIgnitor(0.0),
            analog = ConstantIgnitor(20.0),
            rng = Random(1),
        )

        val c = ctx(counting)
        val buf = AudioBuffer(blockFrames)

        fun renderBlock(b: Int) {
            c.updateOffsetAndLength(0, blockFrames)
            c.voiceElapsedFrames = b * blockFrames
            ig.generate(buf, 220.0, c)
        }

        renderBlock(0)

        val noteOn = counting.draws

        renderBlock(1)

        val shrink = counting.draws - noteOn

        renderBlock(2)

        val regrow = counting.draws - noteOn - shrink

        // What one lane costs on this platform, measured the same way.
        val laneCost = CountingRandom(9).let { r ->
            AnalogDrift(20.0, sampleRate, r)

            r.draws
        }

        (noteOn > 0) shouldBe true      // note-on drew the seed and both strings' lanes
        shrink shouldBe 0               // dropping a string draws nothing
        regrow shouldBe laneCost        // and getting it back draws exactly one fresh lane
    }

    "superpluck: the blend reaches the strings, and does nothing while analog is 0" {
        val locked = pluck(analog = 20.0, spread = 0.0)

        // Drift is present at spread 0, it is shared, not switched off.
        (maxDiff(locked, pluck(analog = 0.0, spread = 0.0)) > 0.02) shouldBe true

        // And the two ends of the knob are two different sounds.
        (maxDiff(locked, pluck(analog = 20.0, spread = 1.0)) > 0.05) shouldBe true

        // With no drift depth there is nothing to spread, at either end, bit for bit.
        maxDiff(pluck(analog = 0.0, spread = 0.0), pluck(analog = 0.0, spread = 1.0)) shouldBe 0.0
    }
})
