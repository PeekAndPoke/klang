/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.builtInSources
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystBuilder
import io.peekandpoke.klang.audio_bridge.classic
import kotlin.math.pow
import kotlin.random.Random

// The rows of the stage cost table (KLANG-00420, probe 7): the controls, every source alone, the stage probe's whole
// catalogue ([stageProbeCases], built the same way), and a few whole voices for scale.

/** The family of the source rows. */
const val COST_SOURCE: String = "source"

/** The stage name of the plain sine, the row every other row is also quoted against. */
const val COST_SINE: String = "sine"

/** The note the sources and voices play: middle C, a non-integral frequency (audio/ref/performance.md). */
const val COST_NOTE_HZ: Double = 261.6255653005986

/** The control rows' stage names, the [StageCostCase.baseline] of every other row. */
const val COST_CONTROL_COPY: String = "plain block copy"
const val COST_CONTROL_KATALYST: String = "katalyst empty chain"
const val COST_CONTROL_SILENCE: String = "ignitor silence"
const val COST_CONTROL_SAMPLE_LEAF: String = "ignitor sample leaf"

private fun control(stage: String, knobs: String, probe: ProbeStage): StageCostCase =
    StageCostCase(family = "control", stage = stage, knobs = knobs, baseline = null, probe = probe)

/** A source alone, bare (no `classic()`), playing a held [COST_NOTE_HZ], over the silence control's harness. */
private fun source(stage: String, knobs: String, tree: IgnitorDsl, params: Map<String, Double> = emptyMap()): StageCostCase =
    StageCostCase(
        family = COST_SOURCE,
        stage = stage,
        knobs = knobs,
        baseline = COST_CONTROL_SILENCE,
        probe = ignitorProbeStage(tree = tree, params = params, freqHz = COST_NOTE_HZ),
    )

/** A whole voice: [tree] with the note's slots [params], playing a held [COST_NOTE_HZ]. */
private fun voice(stage: String, knobs: String, tree: IgnitorDsl, params: Map<String, Double>): StageCostCase =
    StageCostCase(
        family = "voice",
        stage = stage,
        knobs = knobs,
        baseline = COST_CONTROL_SILENCE,
        probe = ignitorProbeStage(tree = tree, params = params, freqHz = COST_NOTE_HZ),
    )

/** Two seconds of seeded white noise at 48 kHz: the PCM of the sample-voice rows (it never runs out in a long render). */
private val costSamplePcm: DoubleArray = Random(0xBEEF).let { rng -> DoubleArray(2 * PROBE_SAMPLE_RATE) { rng.nextDouble() * 2.0 - 1.0 } }

/** A semitone up: a playback rate that is not a whole number, so the playhead interpolates. */
private val semitoneUp: Double = 2.0.pow(1.0 / 12.0)

/** The table's rows: the controls, the sources, the catalogue, the whole voices. */
fun stageCostCases(): List<StageCostCase> = buildList {
    add(control(stage = COST_CONTROL_COPY, knobs = "no engine, copy in and out", probe = copyProbeStage()))
    add(control(stage = COST_CONTROL_KATALYST, knobs = "no stage", probe = katalystProbeStage(dsl = KatalystBuilder().node)))
    add(control(stage = COST_CONTROL_SILENCE, knobs = "silence node", probe = ignitorProbeStage(tree = IgnitorDsl.Silence, freqHz = COST_NOTE_HZ)))
    add(control(stage = COST_CONTROL_SAMPLE_LEAF, knobs = "bare sample leaf", probe = ignitorProbeStage(tree = IgnitorDsl.Sample)))

    addAll(sourceCostCases())

    add(
        StageCostCase(
            family = "ignitor",
            stage = "classic() all off",
            knobs = "adsr.on=0",
            baseline = COST_CONTROL_SAMPLE_LEAF,
            probe = ignitorProbeStage(tree = IgnitorDsl.Sample.classic(), params = mapOf("adsr.on" to 0.0)),
        ),
    )

    for (case in stageProbeCases()) {
        add(
            StageCostCase(
                family = case.family,
                stage = case.stage,
                knobs = case.knobs,
                baseline = if (case.family == "katalyst") COST_CONTROL_KATALYST else COST_CONTROL_SAMPLE_LEAF,
                probe = case.probe,
            ),
        )
    }

    addAll(filterEnvelopeCostCases())
    addAll(voiceCostCases())
}

/**
 * The filter envelope's rows (KLANG-00432, L4-02): the stage probe's catalogue has the lowpass without one. Over the
 * sample leaf in `classic()` with its envelope off, as the catalogue's rows; the render is one held note of a second, so
 * "held" spends it in the sustain after 110 ms and "moving" sweeps through all of it (a decay of one second).
 */
fun filterEnvelopeCostCases(): List<StageCostCase> = buildList {
    val tree = IgnitorDsl.Sample.classic()
    val adsrOff = mapOf("adsr.on" to 0.0)

    fun row(knobs: String, params: Map<String, Double>) {
        add(
            StageCostCase(
                family = "ignitor",
                stage = "lowpass env",
                knobs = knobs,
                baseline = COST_CONTROL_SAMPLE_LEAF,
                probe = ignitorProbeStage(tree = tree, params = adsrOff + params),
            ),
        )
    }

    val moving = mapOf("lpf.freq" to 1000.0, "lpf.env" to 48.0, "lpf.attack" to 0.001, "lpf.decay" to 1.0, "lpf.sustain" to 0.0)

    row(knobs = "freq=1000 env=24, default times (held)", params = mapOf("lpf.freq" to 1000.0, "lpf.env" to 24.0))
    row(knobs = "freq=1000 env=48 a=0.001 d=1 s=0 (moving)", params = moving)
    row(knobs = "freq=1000 env=48 a=0.001 d=1 s=0 analog=1 (moving)", params = moving + mapOf("analog" to 1.0))
}

/** Every source node, bare: the built-in sounds' own trees ([builtInSources]) and the nodes no built-in uses. */
fun sourceCostCases(): List<StageCostCase> = buildList {
    val sources = builtInSources()

    fun builtIn(name: String, knobs: String = "bare", params: Map<String, Double> = emptyMap()) {
        add(source(stage = name, knobs = knobs, tree = sources.getValue(name), params = params))
    }

    builtIn("sine")
    add(source(stage = "sine", knobs = "partial bank, harmonics=4", tree = IgnitorDsl.Sine(harmonics = IgnitorDsl.Constant(4.0))))
    builtIn("saw")
    add(source(stage = "square node", knobs = "bare (not the built-in square)", tree = IgnitorDsl.Square()))
    builtIn("square", knobs = "bare (pulze, duty .5)")
    builtIn("pulze", knobs = "bare (raw pulse)")
    builtIn("tri")
    builtIn("ramp")
    builtIn("zawtooth")
    builtIn("zamp")
    builtIn("impulse")

    for (name in listOf("supersaw", "supersine", "supersquare", "supertri", "superramp", "superpluck")) {
        builtIn(name, knobs = "voices=8 (default)")
        builtIn(name, knobs = "voices=1", params = mapOf("voices" to 1.0))
        builtIn(name, knobs = "voices=16", params = mapOf("voices" to 16.0))
    }

    builtIn("whitenoise")
    builtIn("brownnoise")
    builtIn("pinknoise")
    builtIn("perlinnoise")
    builtIn("berlinnoise")
    builtIn("dust")
    builtIn("crackle")
    builtIn("pluck")

    for ((knobs, rate) in listOf("rate=1" to 1.0, "rate=2^(1/12)" to semitoneUp)) {
        add(
            StageCostCase(
                family = COST_SOURCE,
                stage = "sample playhead",
                knobs = knobs,
                baseline = COST_CONTROL_SILENCE,
                probe = sampleVoiceProbeStage(tree = IgnitorDsl.Sample, pcm = costSamplePcm, rate = rate),
            ),
        )
    }
}

/** A few whole voices for scale: the built-in voice shape (`source.pregain().classic()`) as `sound("saw")` builds it. */
fun voiceCostCases(): List<StageCostCase> = buildList {
    val sources = builtInSources()
    val saw = IgnitorRegistry.builtInVoice(sources.getValue("saw"))
    val supersaw = IgnitorRegistry.builtInVoice(sources.getValue("supersaw"))
    val adsrOff = mapOf("adsr.on" to 0.0)

    add(voice(stage = "saw voice", knobs = "everything off (adsr.on=0)", tree = saw, params = adsrOff))
    add(voice(stage = "saw voice", knobs = "adsr (default)", tree = saw, params = emptyMap()))
    add(voice(stage = "saw voice", knobs = "lpf=1000 + adsr", tree = saw, params = mapOf("lpf.freq" to 1000.0)))
    add(voice(stage = "supersaw voice", knobs = "lpf=1000 + adsr, voices=8", tree = supersaw, params = mapOf("lpf.freq" to 1000.0)))

    // The pitch stages, which the stage probe cannot run (they bend a pitch, they take no input): each alone on the saw voice.
    add(voice(stage = "saw voice", knobs = "fm depth=100 (adsr.on=0)", tree = saw, params = adsrOff + mapOf("fm.depth" to 100.0)))
    add(voice(stage = "saw voice", knobs = "vibrato 0.5 st (adsr.on=0)", tree = saw, params = adsrOff + mapOf("vibrato.semitones" to 0.5)))
    add(voice(stage = "saw voice", knobs = "penv 12 st (adsr.on=0)", tree = saw, params = adsrOff + mapOf("penv.semitones" to 12.0)))
    add(voice(stage = "saw voice", knobs = "accelerate 12 st (adsr.on=0)", tree = saw, params = adsrOff + mapOf("accelerate" to 12.0)))

    add(
        StageCostCase(
            family = "voice",
            stage = "sample instrument",
            knobs = "adsr (default), rate=2^(1/12)",
            baseline = COST_CONTROL_SILENCE,
            probe = sampleVoiceProbeStage(tree = IgnitorRegistry.SAMPLE_INSTRUMENT, pcm = costSamplePcm, rate = semitoneUp),
        ),
    )
}
