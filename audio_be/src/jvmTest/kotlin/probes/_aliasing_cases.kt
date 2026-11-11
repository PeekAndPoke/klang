/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import io.peekandpoke.klang.audio_be.ignitor.builtInSources
import io.peekandpoke.klang.audio_bridge.DistortionShapes
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystBuilder
import io.peekandpoke.klang.audio_bridge.classic
import io.peekandpoke.klang.audio_bridge.kat
import io.peekandpoke.klang.audio_bridge.oversampleBy
import kotlin.math.pow

// The rows of the aliasing table (KLANG-00420, probe 2): the controls, every source of the cost table bare, and every
// shaper on a pure sine: the distortion shapes on both hosts (the fused `Distort` node `classic()` builds, the doors'
// `Shape(Drive(...))` and the Katalyst `distort`), crush, coarse, and oversampling regions around a shape.

/**
 * The pitches, in Hz: a low control pitch and four high ones whose harmonics and aliases stay apart at 48 kHz. 7044.4
 * replaces the brief's 7040, where the 11th fold lands EXACTLY on a harmonic (11 * 48000 = 75 * 7040), so those aliases
 * would read as harmonics; 7044.4 keeps every fold up to the 40th at least 160 Hz from a harmonic. 110 keeps the same
 * coincidence (11 * 48000 = 4800 * 110): there aliases of the 11th fold and up (from harmonics past about 4800) count as
 * harmonics, which only matters for a source or shape whose spectrum reaches that far.
 */
val ALIASING_PITCHES: List<Double> = listOf(110.0, 1244.5, 2637.0, 4186.0, 7044.4)

/** The shapers' input level: a pure sine at this peak. */
const val ALIASING_SINE_LEVEL: Double = 0.5

/** The two drive amounts every shape is measured at (the `distort` amount: drive `10^(amount * 1.2)`, x4.0 and x251). */
val ALIASING_DRIVES: List<Double> = listOf(0.5, 2.0)

/** The shapes the oversampling regions are measured around: smooth, clipping, folding, asymmetric. */
val ALIASING_REGION_SHAPES: List<String> = listOf("soft", "hard", "fold", "rectify")

/** The region factors measured (1x is the shape's own row). */
val ALIASING_REGION_FACTORS: List<Int> = listOf(2, 4, 8, 16)

private val caseConfig: AliasingConfig = AliasingConfig()

/** A source's tree rendered bare at [pitch] with the note slots [params] (`analog` pinned to 0 on every source). */
private fun sourceRender(tree: IgnitorDsl, params: Map<String, Double>, pitch: Double): () -> DoubleArray = {
    ignitorProbeStage(tree = tree, params = mapOf("analog" to 0.0) + params, freqHz = pitch).render(heldSilence(caseConfig.framesNeeded)).left
}

/** A stage rendered on a pure sine at [pitch]; the left channel (a Katalyst stage gets the same sine on both). */
private fun shaperRender(probe: ProbeStage, pitch: Double): () -> DoubleArray = {
    probe.render(heldSine(hz = pitch, amplitude = ALIASING_SINE_LEVEL, frames = caseConfig.framesNeeded)).left
}

private fun c(value: Double): IgnitorDsl = IgnitorDsl.Constant(value)

/** The note that every non-measurable unison row carries. */
private const val UNISON_NOTE: String =
    "not measurable this way: detuned unison, every voice's partials sit off the note's harmonics by its detune, so the detune reads as non-harmonic"

/** The note of a rectifier's rows: its levels re the fundamental mean nothing there. */
private const val RECTIFY_NOTE: String =
    "even harmonics only: the fundamental is absent, so read nonh_reH (re all harmonics), not the columns re the fundamental"

/** The controls at [pitch]: the meter's floor on a sine made here, and the two engine harnesses passing that sine through. */
fun aliasingControlCases(pitch: Double): List<AliasingCase> = listOf(
    AliasingCase(
        family = "control",
        stage = "pure sine (test code)",
        knobs = "peak $ALIASING_SINE_LEVEL",
        pitchHz = pitch,
        kind = AliasingKind.CONTROL,
        note = "the meter's floor: a sine computed in doubles here",
        findPitch = false,
        render = { probeSine(hz = pitch, amplitude = ALIASING_SINE_LEVEL, frames = wholeProbeBlocks(caseConfig.framesNeeded)) },
    ),
    AliasingCase(
        family = "control",
        stage = "ignitor sample leaf",
        knobs = "the sine through the bare leaf",
        pitchHz = pitch,
        kind = AliasingKind.CONTROL,
        note = "the Ignitor harness on the same sine",
        findPitch = false,
        render = shaperRender(probe = ignitorProbeStage(tree = IgnitorDsl.Sample, freqHz = pitch), pitch = pitch),
    ),
    AliasingCase(
        family = "control",
        stage = "katalyst empty chain",
        knobs = "the sine through no stage",
        pitchHz = pitch,
        kind = AliasingKind.CONTROL,
        note = "the Katalyst harness on the same sine",
        findPitch = false,
        render = shaperRender(probe = katalystProbeStage(dsl = KatalystBuilder().node), pitch = pitch),
    ),
)

/** Every source of the cost table ([sourceCostCases]) bare at [pitch], each with what its number may be read as. */
fun aliasingSourceCases(pitch: Double): List<AliasingCase> = buildList {
    val sources = builtInSources()

    fun source(stage: String, knobs: String, tree: IgnitorDsl, params: Map<String, Double> = emptyMap(), kind: AliasingKind = AliasingKind.MEASURED, note: String = "") {
        add(
            AliasingCase(
                family = "source",
                stage = stage,
                knobs = "$knobs, analog=0",
                pitchHz = pitch,
                kind = kind,
                note = note,
                findPitch = true,
                render = sourceRender(tree = tree, params = params, pitch = pitch),
            ),
        )
    }

    fun builtIn(name: String, knobs: String = "bare", params: Map<String, Double> = emptyMap(), kind: AliasingKind = AliasingKind.MEASURED, note: String = "") {
        source(stage = name, knobs = knobs, tree = sources.getValue(name), params = params, kind = kind, note = note)
    }

    builtIn("sine")
    source(stage = "sine", knobs = "partial bank, harmonics=4", tree = IgnitorDsl.Sine(harmonics = c(4.0)))
    builtIn("saw")
    source(stage = "square node", knobs = "bare (not the built-in square)", tree = IgnitorDsl.Square())
    builtIn("square", knobs = "bare (pulze, duty .5)")
    builtIn("pulze", knobs = "bare (raw pulse)")
    builtIn("tri")
    builtIn("ramp")
    builtIn("zawtooth")
    builtIn("zamp")
    builtIn("impulse")

    for (name in listOf("supersaw", "supersine", "supersquare", "supertri", "superramp")) {
        builtIn(name, knobs = "voices=1", params = mapOf("voices" to 1.0))
        builtIn(name, knobs = "voices=8 (default)", kind = AliasingKind.NOT_MEASURABLE, note = UNISON_NOTE)
    }

    val stringNote = "not measurable this way: a Karplus-Strong string's partials are stretched by its loop filter's phase delay " +
        "and the note decays through the window, so off-harmonic energy is not aliasing alone"

    builtIn("pluck", kind = AliasingKind.NOT_MEASURABLE, note = stringNote)
    builtIn("superpluck", knobs = "voices=8 (default)", kind = AliasingKind.NOT_MEASURABLE, note = stringNote)

    for (name in listOf("whitenoise", "brownnoise", "pinknoise", "perlinnoise", "berlinnoise", "dust", "crackle")) {
        builtIn(name, kind = AliasingKind.BY_DESIGN, note = "by design: noise, every bin is non-harmonic")
    }

    // The sample playhead plays a sine sample whose own pitch is pitch / rate, so the voice sounds at the pitch.
    for ((knobs, rate) in listOf("rate=1" to 1.0, "rate=2^(1/12), cubic interpolation" to 2.0.pow(1.0 / 12.0))) {
        val pcm = probeSine(hz = pitch / rate, amplitude = ALIASING_SINE_LEVEL, frames = caseConfig.framesNeeded * 2)

        add(
            AliasingCase(
                family = "source",
                stage = "sample playhead",
                knobs = "sine sample, $knobs",
                pitchHz = pitch,
                kind = AliasingKind.MEASURED,
                note = "",
                findPitch = true,
                render = {
                    sampleVoiceProbeStage(tree = IgnitorDsl.Sample, pcm = pcm, rate = rate, freqHz = pitch)
                        .render(heldSilence(caseConfig.framesNeeded)).left
                },
            ),
        )
    }
}

/** Every shaper on a pure sine at [pitch]: the shapes on three laws, crush and coarse, and the regions around four shapes. */
fun aliasingShaperCases(pitch: Double): List<AliasingCase> = buildList {
    val classicOverSample = IgnitorDsl.Sample.classic()
    val adsrOff = mapOf("adsr.on" to 0.0)

    fun shaper(family: String, stage: String, knobs: String, probe: ProbeStage, kind: AliasingKind = AliasingKind.MEASURED, note: String = "") {
        val shown = if (note.isEmpty() && stage.endsWith(":rectify")) RECTIFY_NOTE else note

        add(
            AliasingCase(
                family = family,
                stage = stage,
                knobs = knobs,
                pitchHz = pitch,
                kind = kind,
                note = shown,
                findPitch = false,
                render = shaperRender(probe = probe, pitch = pitch),
            ),
        )
    }

    for (shape in DistortionShapes.names) {
        val index = DistortionShapes.indexOf(shape)

        for (amount in ALIASING_DRIVES) {
            shaper(
                family = "ignitor",
                stage = "distort:$shape",
                knobs = "classic() fused node, amount=$amount, 1x",
                probe = ignitorProbeStage(
                    tree = classicOverSample,
                    params = adsrOff + mapOf("distort.amount" to amount, "distort.shape" to index),
                    freqHz = pitch,
                ),
            )
            shaper(
                family = "ignitor",
                stage = "shape(drive):$shape",
                knobs = "the doors' Shape(Drive), amount=$amount, 1x",
                probe = ignitorProbeStage(
                    tree = IgnitorDsl.Shape(inner = IgnitorDsl.Drive(inner = IgnitorDsl.Sample, amount = c(amount)), shape = c(index)),
                    freqHz = pitch,
                ),
            )
            shaper(
                family = "katalyst",
                stage = "distort:$shape",
                knobs = "amount=$amount, 1x",
                probe = katalystProbeStage(dsl = KatalystBuilder().distort(amount = amount.kat, shape = shape).node),
            )
        }
    }

    for (shape in ALIASING_REGION_SHAPES) {
        val index = DistortionShapes.indexOf(shape)

        for (amount in ALIASING_DRIVES) {
            for (factor in ALIASING_REGION_FACTORS) {
                shaper(
                    family = "ignitor",
                    stage = "distort:$shape",
                    knobs = "fused node in oversampleBy($factor), amount=$amount",
                    probe = ignitorProbeStage(
                        tree = IgnitorDsl.Sample.oversampleBy(c(factor.toDouble())) { IgnitorDsl.Distort(inner = it, amount = c(amount), shape = c(index)) },
                        freqHz = pitch,
                    ),
                )
                shaper(
                    family = "katalyst",
                    stage = "distort:$shape",
                    knobs = "in oversampleBy($factor), amount=$amount",
                    probe = katalystProbeStage(dsl = KatalystBuilder().oversampleBy(factor) { it.distort(amount = amount.kat, shape = shape) }.node),
                )
            }
        }
    }

    val crushNote = "by design: the quantizer is not oversampled, its staircase's harmonics fold"
    val coarseNote = "by design: the sample-and-hold makes images around multiples of the reduced rate"

    for (bits in listOf(8.0, 4.0)) {
        shaper(
            family = "ignitor",
            stage = "crush",
            knobs = "bits=$bits",
            probe = ignitorProbeStage(tree = classicOverSample, params = adsrOff + mapOf("crush.bits" to bits), freqHz = pitch),
            kind = AliasingKind.BY_DESIGN,
            note = crushNote,
        )
    }

    for (factor in listOf(1.5, 4.0)) {
        shaper(
            family = "ignitor",
            stage = "coarse",
            knobs = "factor=$factor",
            probe = ignitorProbeStage(tree = classicOverSample, params = adsrOff + mapOf("coarse.factor" to factor), freqHz = pitch),
            kind = AliasingKind.BY_DESIGN,
            note = coarseNote,
        )
    }
}

/** The whole table: per pitch, the controls, the sources, the shapers. */
fun aliasingCases(): List<AliasingCase> = ALIASING_PITCHES.flatMap { pitch ->
    aliasingControlCases(pitch) + aliasingSourceCases(pitch) + aliasingShaperCases(pitch)
}
