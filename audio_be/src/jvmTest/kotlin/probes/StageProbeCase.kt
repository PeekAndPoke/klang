/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import io.peekandpoke.klang.audio_bridge.DistortionShapes
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystBuilder
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.LfoShapes
import io.peekandpoke.klang.audio_bridge.classic
import io.peekandpoke.klang.audio_bridge.kat

/**
 * One row group of the stage probe: [stage] at the knob set [knobs] (a readable label of what was set; "default" is
 * the stage's authored values), of [family] (`katalyst` for a bus stage, `ignitor` for a per-note stage), rendered by
 * [probe].
 */
class StageProbeCase(
    val family: String,
    val stage: String,
    val knobs: String,
    val probe: ProbeStage,
)

/** The classic voice chain over the sample leaf: every per-note stage of `classic()` in its production place. */
private val classicOverSample: IgnitorDsl = IgnitorDsl.Sample.classic()

/** `classic()`'s amplitude envelope switched off, so a stage other than the ADSR runs alone. */
private val adsrOff: Map<String, Double> = mapOf("adsr.on" to 0.0)

/**
 * A `classic()` stage alone: the classic tree over the sample leaf, the note's slots [params] switching the one stage
 * on, every other stage at its off value (so the gate does not build it) and the ADSR off unless [keepAdsr].
 */
private fun classicCase(stage: String, knobs: String, params: Map<String, Double>, keepAdsr: Boolean = false): StageProbeCase =
    StageProbeCase(
        family = "ignitor",
        stage = stage,
        knobs = knobs,
        probe = ignitorProbeStage(tree = classicOverSample, params = if (keepAdsr) params else adsrOff + params),
    )

/** An Ignitor node outside `classic()`, built over the sample leaf with no slots. */
private fun nodeCase(stage: String, knobs: String, tree: IgnitorDsl): StageProbeCase =
    StageProbeCase(family = "ignitor", stage = stage, knobs = knobs, probe = ignitorProbeStage(tree = tree))

/** A Katalyst chain of the stages [build] appends to an empty builder (the doors an author writes). */
private fun busCase(stage: String, knobs: String, build: (KatalystBuilder) -> KatalystBuilder): StageProbeCase =
    StageProbeCase(family = "katalyst", stage = stage, knobs = knobs, probe = katalystProbeStage(dsl = build(KatalystBuilder()).node))

private fun c(value: Double): IgnitorDsl = IgnitorDsl.Constant(value)

/** The per-note filter cases of one `classic()` SVF door (`lpf`, `hpf`, `bpf`, `notch`). */
private fun svfCases(stage: String, door: String, passes: Boolean): List<StageProbeCase> {
    val freq = "$door.freq"
    val q = "$door.q"

    return listOfNotNull(
        classicCase(stage = stage, knobs = "freq=1000", params = mapOf(freq to 1000.0)),
        classicCase(stage = stage, knobs = "freq=23999 (Nyquist-1)", params = mapOf(freq to 23999.0)),
        classicCase(stage = stage, knobs = "freq=1 (floor 5)", params = mapOf(freq to 1.0)),
        classicCase(stage = stage, knobs = "freq=1000 q=200 (max)", params = mapOf(freq to 1000.0, q to 200.0)),
        classicCase(stage = stage, knobs = "freq=23999 q=200", params = mapOf(freq to 23999.0, q to 200.0)),
        classicCase(stage = stage, knobs = "freq=20 q=200", params = mapOf(freq to 20.0, q to 200.0)),
        classicCase(stage = stage, knobs = "freq=1000 analog=1", params = mapOf(freq to 1000.0, "analog" to 1.0)),
        classicCase(stage = stage, knobs = "freq=23999 q=200 analog=1", params = mapOf(freq to 23999.0, q to 200.0, "analog" to 1.0)),
        if (passes) classicCase(stage = stage, knobs = "freq=1000 passes=16 (max)", params = mapOf(freq to 1000.0, "$door.passes" to 16.0)) else null,
    )
}

/** Every per-note stage that processes an input: the stages `classic()` places, then the other effect nodes. */
fun ignitorStageProbeCases(): List<StageProbeCase> = buildList {
    add(classicCase(stage = "onepole", knobs = "freq=1000", params = mapOf("onepole" to 1000.0)))
    add(classicCase(stage = "onepole", knobs = "freq=23999 (Nyquist-1)", params = mapOf("onepole" to 23999.0)))
    add(classicCase(stage = "onepole", knobs = "freq=1", params = mapOf("onepole" to 1.0)))

    add(classicCase(stage = "crush", knobs = "bits=8", params = mapOf("crush.bits" to 8.0)))
    add(classicCase(stage = "crush", knobs = "bits=1 (lowest on)", params = mapOf("crush.bits" to 1.0)))
    add(classicCase(stage = "crush", knobs = "bits=24", params = mapOf("crush.bits" to 24.0)))

    add(classicCase(stage = "coarse", knobs = "factor=4", params = mapOf("coarse.factor" to 4.0)))
    add(classicCase(stage = "coarse", knobs = "factor=1.5", params = mapOf("coarse.factor" to 1.5)))
    add(classicCase(stage = "coarse", knobs = "factor=4800", params = mapOf("coarse.factor" to 4800.0)))

    for ((index, shape) in DistortionShapes.names.withIndex()) {
        add(classicCase(stage = "distort:$shape", knobs = "amount=0.5", params = mapOf("distort.amount" to 0.5, "distort.shape" to index.toDouble())))
        add(classicCase(stage = "distort:$shape", knobs = "amount=3", params = mapOf("distort.amount" to 3.0, "distort.shape" to index.toDouble())))
    }

    addAll(svfCases(stage = "highpass", door = "hpf", passes = true))
    addAll(svfCases(stage = "bandpass", door = "bpf", passes = false))
    addAll(svfCases(stage = "notch", door = "notch", passes = false))
    addAll(svfCases(stage = "lowpass", door = "lpf", passes = true))

    add(classicCase(stage = "tremolo", knobs = "depth=0.5 rate=5", params = mapOf("tremolo.depth" to 0.5)))
    add(
        classicCase(
            stage = "tremolo",
            knobs = "depth=1 rate=40 shape=square",
            params = mapOf("tremolo.depth" to 1.0, "tremolo.rate" to 40.0, "tremolo.shape" to LfoShapes.names.indexOf("square").toDouble()),
        ),
    )

    add(classicCase(stage = "adsr", knobs = "default", params = emptyMap(), keepAdsr = true))
    add(
        classicCase(
            stage = "adsr",
            knobs = "a=0 d=0 s=1 r=0",
            params = mapOf("adsr.attack" to 0.0, "adsr.decay" to 0.0, "adsr.sustain" to 1.0, "adsr.release" to 0.0),
            keepAdsr = true,
        ),
    )
    add(classicCase(stage = "adsr", knobs = "release=5", params = mapOf("adsr.release" to 5.0), keepAdsr = true))

    val sample = IgnitorDsl.Sample

    add(nodeCase(stage = "drive+shape", knobs = "amount=0.5 soft", tree = IgnitorDsl.Shape(inner = IgnitorDsl.Drive(inner = sample, amount = c(0.5)))))
    add(
        nodeCase(
            stage = "drive+shape",
            knobs = "amount=3 asym",
            tree = IgnitorDsl.Shape(inner = IgnitorDsl.Drive(inner = sample, amount = c(3.0)), shape = c(DistortionShapes.indexOf("asym"))),
        ),
    )
    add(nodeCase(stage = "phaser", knobs = "default (rate .5 wet .5)", tree = IgnitorDsl.Phaser(inner = sample)))
    add(
        nodeCase(
            stage = "phaser",
            knobs = "wet=1 rate=20 center=20000 sweep=20000 floor=0",
            tree = IgnitorDsl.Phaser(inner = sample, wet = c(1.0), rate = c(20.0), center = c(20000.0), sweep = c(20000.0), floor = c(0.0)),
        ),
    )
    add(nodeCase(stage = "shimmer", knobs = "default", tree = IgnitorDsl.Shimmer(inner = sample)))
    add(nodeCase(stage = "shimmer", knobs = "wet=1 feedback=0.95 (max)", tree = IgnitorDsl.Shimmer(inner = sample, wet = c(1.0), feedback = c(0.95))))
    add(
        nodeCase(
            stage = "eq",
            knobs = "band 1000 q=.707 +12 dB",
            tree = IgnitorDsl.Eq(inner = sample, sections = listOf(IgnitorDsl.EqSection.Bell(freq = c(1000.0), db = c(12.0)))),
        ),
    )
    add(
        nodeCase(
            stage = "eq",
            knobs = "band 23999 q=200 +24 dB",
            tree = IgnitorDsl.Eq(inner = sample, sections = listOf(IgnitorDsl.EqSection.Bell(freq = c(23999.0), q = c(200.0), db = c(24.0)))),
        ),
    )
    add(
        nodeCase(
            stage = "eq",
            knobs = "tap 1000 q=200 gain=1",
            tree = IgnitorDsl.Eq(inner = sample, sections = listOf(IgnitorDsl.EqSection.RawTap(freq = c(1000.0), q = c(200.0)))),
        ),
    )
}

/** Every bus stage the Katalyst DSL builds, through its doors. */
fun katalystStageProbeCases(): List<StageProbeCase> = buildList {
    add(busCase(stage = "body", knobs = "wood wet=.5 floor=.4") { it.body(material = "wood") })
    add(busCase(stage = "body", knobs = "bell wet=1 floor=0") { it.body(wet = 1.0.kat, material = "bell") { b -> b.floor(0.0.kat) } })
    add(busCase(stage = "body", knobs = "steel wet=1 floor=0") { it.body(wet = 1.0.kat, material = "steel") { b -> b.floor(0.0.kat) } })

    add(busCase(stage = "vowel", knobs = "a wet=.5 floor=.2") { it.vowel(vowel = "a") })
    add(busCase(stage = "vowel", knobs = "soprano:i wet=1 floor=0") { it.vowel(wet = 1.0.kat, vowel = "soprano:i") { b -> b.floor(0.0.kat) } })

    add(busCase(stage = "delay", knobs = "default (wet .25 time .25 fb .3)") { it.delay() })
    add(busCase(stage = "delay", knobs = "time=0.01 fb=0.99") { it.delay(time = 0.01.kat, feedback = 0.99.kat) })
    add(busCase(stage = "delay", knobs = "fb=1 (endless)") { it.delay(feedback = 1.0.kat) })
    add(busCase(stage = "delay", knobs = "wet=1 fb=1.5 cap=1") { it.delay(wet = 1.0.kat, feedback = 1.5.kat) })

    add(busCase(stage = "reverb", knobs = "default (wet .25 size 5)") { it.reverb() })
    add(busCase(stage = "reverb", knobs = "wet=1 size=10 (max)") { it.reverb(wet = 1.0.kat, size = 10.0.kat) })
    add(busCase(stage = "reverb", knobs = "wet=1 size=10 lowpass=20000") { it.reverb(wet = 1.0.kat, size = 10.0.kat, lowpass = 20000.0.kat) })
    add(busCase(stage = "reverb", knobs = "wet=1 size=0") { it.reverb(wet = 1.0.kat, size = 0.0.kat) })

    add(busCase(stage = "phaser", knobs = "bare (wet 0)") { it.phaser() })
    add(busCase(stage = "phaser", knobs = "wet=.5 rate=.5") { it.phaser(wet = 0.5.kat, rate = 0.5.kat) })
    add(
        busCase(stage = "phaser", knobs = "wet=1 rate=20 center=20000 sweep=20000 floor=0") {
            it.phaser(wet = 1.0.kat, rate = 20.0.kat, center = 20000.0.kat, sweep = 20000.0.kat) { p -> p.floor(0.0.kat) }
        },
    )

    add(busCase(stage = "compressor", knobs = "default (-20 dB 4:1 knee 6)") { it.compressor() })
    add(
        busCase(stage = "compressor", knobs = "thr=-60 ratio=100 knee=0 att=0 rel=0") {
            it.compressor(threshold = (-60.0).kat, ratio = 100.0.kat, knee = 0.0.kat, attack = 0.0.kat, release = 0.0.kat)
        },
    )
    add(busCase(stage = "compressor", knobs = "lookahead=0.05 (max)") { it.compressor(lookahead = 0.05) })
    add(busCase(stage = "limiter", knobs = "default (-1 dB 20:1)") { it.limiter() })
    add(busCase(stage = "limiter", knobs = "lookahead=0.005") { it.limiter(lookahead = 0.005) })

    for (shape in DistortionShapes.names) {
        add(busCase(stage = "distort:$shape", knobs = "amount=0.5") { it.distort(amount = 0.5.kat, shape = shape) })
        add(busCase(stage = "distort:$shape", knobs = "amount=3") { it.distort(amount = 3.0.kat, shape = shape) })
    }

    add(busCase(stage = "gain", knobs = "default (1)") { it.gain() })
    add(busCase(stage = "gain", knobs = "gain=100") { it.gain(100.0.kat) })

    add(busCase(stage = "eq", knobs = "band 1000 q=.707 +12 dB") { it.eq { e -> e.band(freq = 1000.0.kat, db = 12.0.kat) } })
    add(busCase(stage = "eq", knobs = "band 23999 q=200 +24 dB") { it.eq { e -> e.band(freq = 23999.0.kat, q = 200.0.kat, db = 24.0.kat) } })
    add(busCase(stage = "eq", knobs = "band 5 q=200 +24 dB") { it.eq { e -> e.band(freq = 5.0.kat, q = 200.0.kat, db = 24.0.kat) } })
    add(busCase(stage = "eq", knobs = "tap 1000 q=200 gain=1") { it.eq { e -> e.tap(freq = 1000.0.kat, q = 200.0.kat) } })

    add(busCase(stage = "plus", knobs = "0.5") { it.plus(0.5.kat) })
    add(busCase(stage = "minus", knobs = "0.5") { it.minus(0.5.kat) })
    add(busCase(stage = "times", knobs = "2") { it.times(2.0.kat) })
    add(busCase(stage = "div", knobs = "0.001") { it.div(0.001.kat) })
    add(busCase(stage = "div", knobs = "1e-20") { it.div(1e-20.kat) })
    add(busCase(stage = "pow", knobs = "0.5") { it.pow(0.5.kat) })
    add(busCase(stage = "pow", knobs = "3") { it.pow(3.0.kat) })
    add(busCase(stage = "pow", knobs = "-1") { it.pow((-1.0).kat) })
    add(busCase(stage = "min door (Max stage)", knobs = "0.5") { it.min(0.5.kat) })
    add(busCase(stage = "max door (Min stage)", knobs = "-0.5") { it.max((-0.5).kat) })
    add(busCase(stage = "abs", knobs = "-") { it.abs() })

    add(busCase(stage = "blend (parallel)", knobs = "wet=.5 over reverb wet=1") { it.blend(0.5) { b -> b.reverb(wet = 1.0.kat) } })
    add(busCase(stage = "bands (parallel)", knobs = "cut 500, both bands empty") { it.bands { b -> b.cut(500.0) } })
    add(busCase(stage = "oversampleBy", knobs = "2x distort soft .5") { it.oversampleBy(2) { b -> b.distort(amount = 0.5.kat) } })
    add(busCase(stage = "oversampleBy", knobs = "16x distort hard 3") { it.oversampleBy(16) { b -> b.distort(amount = 3.0.kat, shape = "hard") } })

    add(busCase(stage = "duck", knobs = "cyl=1 depth=.8 (self-trigger)") { it.duck(cylinder = 1.0.kat, depth = 0.8.kat) })
    add(busCase(stage = "duck", knobs = "cyl=1 depth=1 release=0") { it.duck(cylinder = 1.0.kat, depth = 1.0.kat, release = 0.0.kat) })
}

/** The whole catalogue: the bus stages, then the per-note stages. */
fun stageProbeCases(): List<StageProbeCase> = katalystStageProbeCases() + ignitorStageProbeCases()
