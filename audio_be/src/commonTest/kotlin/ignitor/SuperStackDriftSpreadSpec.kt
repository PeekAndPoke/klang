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
        // below were retaken. What the case pins is unchanged: the unison default is one fixed
        // render, and any accidental change to the drift path shows up here as a bit difference.
        val expected = doubleArrayOf(
        0.4661566921865289, 0.4761836946318679, 0.48621069135290884, 0.49623766309423367,
        0.5062646143278072, 0.5162915799677074, 0.5263185566802719, 0.536345557083367,
        0.5059009859311275, 0.2881647989190387, 0.2981917409053834, 0.3082186971935449,
        0.3182456338778833, 0.3282725439836098, 0.3382994393130203, 0.34832635350454966,
        0.35835328560351654, 0.368380246523389, 0.37840719761434544, 0.3884341096226068,
        0.39846107422335525, 0.4084880206889799, 0.41851496195880317, 0.4285418770383928,
        0.43856877869972566, 0.44859570667757265, 0.19589360857535787, 0.16609055732868286,
        0.17611748777902775, 0.18614442107610016, 0.1961713621030448, 0.20619831115060658,
        0.21622526823728944, 0.22625225206170035, 0.2362792327834678, 0.2463062053943685,
        0.25633317902733554, 0.26636016366409776, 0.14947068033726713, -0.017708556576856892,
        -0.007681583586222278, 0.002345394323942051, 0.01237236026761233, 0.02239930447443523,
        0.03242625544086993, 0.042453215754242164, 0.05248017466732806, 0.06250714101668511,
        0.07253410119536206, 0.08256102752972, 0.09258795030121919, 0.10261485860542167,
        0.11264175731603905, -0.08677866278789152, -0.15228966305336733, -0.14226271274791147,
        -0.13223575724377656, -0.12220878423607352, -0.2913606798051722, -0.38194870971654027,
        -0.3719217137537553, -0.3618946833895237, -0.35186762350142214, -0.5155000726538286,
        -0.6147282020327044, -0.6047011460518548, -0.5946740816744995, -0.5846470368195871,
        -0.5746199710350308, -0.564592910540909, -0.5545658226610063, -0.5445387511142483,
        -0.5345116768498032, -0.52448462059906, -0.5144575334706072, -0.5044304372480848,
        -0.49440333261643243, -0.4843762189354982, -0.4743491450522627, -0.4643220811876194,
        -0.4542950201226655, -0.4442679596568529, -0.4342408718311977, -0.42421378643903696,
        -0.41418672109370563, -0.4041596501704653, -0.394132625684007, -0.3841056194331913,
        -0.37407857956053314, -0.3640515441188367, -0.35402449708617967, -0.3439974415809931,
        -0.3339703936156353, -0.3239433298505013, -0.31391628274926564, -0.30388920172378325,
        -0.29386212523557487, -0.28383504843676277, -0.27380796744049013, -0.26378088657627263,
        -0.25375381442999223, -0.2437267268981723, -0.23369964222606565, -0.22367255430627342,
        -0.21364544305232647, -0.20361832815866665, -0.1935911872846712, -0.1835640394741216,
        -0.17353686761064605, -0.16350967922153314, -0.15348248224362648, -0.14345527985974393,
        -0.1334281091083782, -0.12340094078377427, -0.11337376284093782, -0.10334655997586564,
        -0.0933193658561602, -0.08329219624279088, -0.0732650312873766, -0.06323785776952154,
        -0.053210685350617626, -0.0431834747250239, -0.0331562626636035, -0.02312902576821274,
        -0.01310176359314627, -0.0030744916605732475, 0.006952793542845226, 0.016980109351165645,
        0.027007402719113185, 0.03703469915921356, -0.14945830574985985, -0.230327882160493,
        -0.22030061416143454, -0.21027334754795945, -0.2002460780684361, -0.1902187866495335,
        -0.1801915116669691, -0.17016422077962867, -0.16013693694705372, -0.15010963562560692,
        -0.14008231561215406, -0.13005498848044497, -0.12002769223652991, -0.11000039491845214,
        -0.09997308893601849, -0.08994576269448781, -0.07991843189121837, -0.06989108652017698,
        -0.0598637237164483, -0.049836359408089975, -0.03980901905974449, -0.02978171630137222,
        -0.01975444629453349, -0.009727156534132378, 3.0013940588721755E-4, 0.010327424716844683,
        0.020354705964892167, 0.03038197583077075, 0.040409222798825084, 0.05043648991518554,
        0.060463734268714125, 0.07049097535766115, 0.08051821876189508, 0.09054547912584955,
        0.1005727492551247, 0.11060004266980011, 0.12062734509649253, 0.1306546773492114,
        0.14068201612236864, 0.1507093826547551, 0.16073673240653277, 0.17076409513128982,
        0.18079145571821942, 0.19081883624352852, 0.20084623039531732, 0.21087360601821775,
        0.22090098368514538, 0.23092837579609135, 0.2409557452211959, 0.25098311910120286,
        0.2610104880087442, 0.27103785166968164, 0.28106522115751525, 0.29109257021310586,
        0.3011199176743174, 0.31114727411388243, 0.3211746289704797, 0.33120198004807744,
        0.3412293621718463, 0.3512567238373674, 0.36128406954384384, 0.3713114175917992,
        0.38133878444547864, 0.3913661358474637, 0.40139350015279995, 0.41142087376555336,
        0.42144823153422034, 0.4314755814760322, 0.4415029337785487, 0.45153028033757314,
        0.46155763374147757, 0.47158502557117765, 0.48161242613579014, 0.4916398032949645,
        0.50166716369743, 0.5116945273545219, 0.5217219124771448, 0.5317492983786433,
        0.5417766896163614, 0.4587804650385593, 0.2935967787267585, 0.3036241728536173,
        0.31365156472484074, 0.32367895523084167, 0.33370635450948005, 0.3437338015484249,
        0.3537612368163448, 0.36378866252109043, 0.37381609205308153, 0.38384350598188416,
        0.39387092196333934, 0.4038983076436673, 0.41392570383936345, 0.42395311732957286,
        0.433980510333798, 0.44400788527169505, 0.31648770442483026, 0.16150354544867582,
        0.17153091625960645, 0.18155832133763167, 0.19158571440030564, 0.20161311128812903,
        0.2116404975158309, 0.2216678823542787, 0.23169527028064954, 0.24172265027186818,
        0.251750032120817, 0.2617774361145293, 0.06546562618922042, -0.02229043656277163,
        -0.01226301315507834, -0.002235591155221983, 0.007791823217896197, 0.017819232301785646,
        0.027846652307984863, 0.03787408731912404, 0.047901531514673065, 0.05792897687997211,
        0.06795644909344165, 0.0779839329542662, 0.08801141652126698, 0.09803889038951946,
        0.10806635485589816, 0.11809380908754706, -0.01190500684051795, -0.14683651268527145,
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
