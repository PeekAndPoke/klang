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
 * of voices, and that the default still renders what it rendered before [DriftLanes] existed.
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

    "the default spread 1 renders exactly what the stack rendered before DriftLanes" {
        // Captured from the engine at commit 0facbd1c (the commit before this change) with the
        // same seed, then re-rendered here: the unison default must not have moved a bit.
        val expected = doubleArrayOf(
        0.4989405912797973, 0.5089685985751782, 0.5189965920228571, 0.5290245881620654,
        0.5390525776868701, 0.5490805738961516, 0.5591085740156013, 0.5691365447114172,
        0.5293022982836563, 0.2670835123351003, 0.27711149107342953, 0.28713943159087396,
        0.29716737310164454, 0.3071953061596068, 0.3172232274295706, 0.3272511714704652,
        0.3372791504036951, 0.34730713165963245, 0.3573351037595056, 0.3673630993228912,
        0.3773910978803882, 0.3874191174881695, 0.39744712464801446, 0.40747513316326345,
        0.417503127547747, 0.42753112476280664, 0.15976219054297242, 0.12799166736957585,
        0.1380196704773994, 0.1480476696723234, 0.15807566179018387, 0.16810364706850084,
        0.1781316524330297, 0.18815967041868586, 0.19818771792721535, 0.2082157578371883,
        0.2182438007143033, 0.22827185310854559, 0.11597931327118546, -0.04229888222628605,
        -0.03227087464794358, -0.022242856310444852, -0.012214810964903934, -0.002186776176970556,
        0.00784126426861952, 0.017869310354331525, 0.027897360459820768, 0.03792542574102827,
        0.047953450922375185, 0.05798149720347069, 0.06800954186093651, 0.07803756991405425,
        0.0880655747450878, -0.10166555185198159, -0.15490930095495897, -0.1448813148388226,
        -0.1348533127513594, -0.12482531450250922, -0.30447831029379435, -0.3946019148932486,
        -0.38457389009784937, -0.37454585704333043, -0.36451781032763136, -0.5394740866377914,
        -0.6336839430159817, -0.6236559587892607, -0.6136280086909532, -0.603600073993081,
        -0.5935721487153507, -0.5835442451410474, -0.5735163288468738, -0.5634884267639474,
        -0.5534605030590927, -0.5434325550663497, -0.5334046172237746, -0.5233766894320157,
        -0.5133487301242866, -0.5033207718558922, -0.49329279190742914, -0.48326481761686546,
        -0.473236809643345, -0.46320878688376266, -0.4531807540775259, -0.44315271309263315,
        -0.4331246818361932, -0.4230966385278618, -0.41306860467299156, -0.4030405447911626,
        -0.3930124971614833, -0.3829844639572769, -0.3729564611661453, -0.3629284742392648,
        -0.3529004537473373, -0.34287240038469624, -0.33284432269675956, -0.32281624409442566,
        -0.3127882024516674, -0.3027601383013187, -0.29273209059402666, -0.28270407253677093,
        -0.2726760478062719, -0.26264801680308514, -0.252619962340181, -0.2425919446885104,
        -0.23256393995386504, -0.2225359387629786, -0.21250793728022377, -0.20247993940369696,
        -0.19245194869346746, -0.18242393368493232, -0.17239590979189454, -0.1623678948047743,
        -0.1523398840391546, -0.1423118824549371, -0.13228388300207994, -0.12225589086325976,
        -0.11222787923634421, -0.10219984917764426, -0.09217181493373024, -0.08214380351611264,
        -0.07211581528017545, -0.062087813213101375, -0.05205981484219588, -0.04203185446526364,
        -0.03200386813590596, -0.021975854120539748, -0.011947839462292664, -0.001919819584467214,
        0.008108194560754345, 0.018136235324044528, -0.14195287236967252, -0.19741822922284785,
        -0.1873902075806375, -0.1773621814515238, -0.16733417715651716, -0.15730615267845105,
        -0.1472781217209389, -0.13725009815225192, -0.12722205823124855, -0.11719400671176688,
        -0.10716598672737493, -0.09713794399248454, -0.08710989939901309, -0.07708186254855418,
        -0.06705381283946524, -0.05702575576072027, -0.04699771112092985, -0.036969665433985964,
        -0.026941578118290163, -0.016913514861092854, -0.006885458273508463, 0.003142611297949599,
        0.013170676005418283, 0.023198719175099455, 0.03322675977875621, 0.04325476502610698,
        0.05328276323522159, 0.06331079103857115, 0.0733388410903366, 0.08336690541931596,
        0.09339497620173888, 0.10342301720277693, 0.11345106117612067, 0.12347909086429122,
        0.1335070981038601, 0.1435350972205896, 0.15356311001571665, 0.1635911424130235,
        0.1736192007509677, 0.1836472388970416, 0.19367524858985935, 0.20370325075847495,
        0.21373127191380517, 0.22375929160391486, 0.2337873088650432, 0.24381531463152473,
        0.2538433235484161, 0.26387131486970344, 0.2738992931068122, 0.2839272886443894,
        0.2939552676374253, 0.3039832367789702, 0.3140112077694335, 0.32403920166772887,
        0.3340672120525554, 0.3440952368742117, 0.35412326166538216, 0.36415127432168903,
        0.3741792905546514, 0.38420733135357393, 0.39423537019520677, 0.4042634187232778,
        0.4142914927656784, 0.4243195953795207, 0.43434770149033575, 0.4443757972997738,
        0.4544038954045385, 0.46443199029873583, 0.4744600825819057, 0.4844881816147403,
        0.49451629872167846, 0.5045443934387701, 0.5145724876251829, 0.5246005953879377,
        0.5346286961556029, 0.5446568003570712, 0.554684890090717, 0.5647129781528196,
        0.5747410572256482, 0.435141017378137, 0.2726882769461533, 0.2827163659047813,
        0.29274444167302577, 0.3027725030491278, 0.3128005868774559, 0.3228286430205371,
        0.33285670231500764, 0.34288472513280044, 0.35291276510359176, 0.3629408201485748,
        0.37296885391570983, 0.3829968787403521, 0.3930248885696323, 0.4030528964649342,
        0.41308090832565636, 0.4231089366010466, 0.29093142224660157, 0.12356950115006175,
        0.13359748861580048, 0.14362546072039267, 0.15365344983098111, 0.16368141631243144,
        0.17370937090811933, 0.18373729949853002, 0.1937652355408977, 0.20379317604304092,
        0.21382113895864838, 0.22384910672815028, 0.033399693492611, -0.04672179646649449,
        -0.03669382317404362, -0.02666584583487877, -0.016637878689125092, -0.006609917325792306,
        0.0034180523388311866, 0.013446033807678726, 0.023473989587811946, 0.0335019375315288,
        0.043529896285810804, 0.053557878011854035, 0.06358587048977735, 0.073613857061926,
        0.08364183643937184, 0.09366981141136874, -0.05956202116272327, -0.14930511924887713,
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
