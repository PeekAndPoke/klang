/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.SampleStore
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.builtInSources
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_be.voices.PlaybackCtx
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceFactory
import io.peekandpoke.klang.audio_bridge.AdsrCurves
import io.peekandpoke.klang.audio_bridge.DistortionShapes
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.LfoShapes
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.constants.PHASER_CENTER_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.PHASER_SWEEP_HZ
import io.peekandpoke.klang.audio_bridge.constants.REVERB_SIZE
import io.peekandpoke.klang.common.infra.toKlangMap
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// The rows of the block-framing table (KLANG-00420, probe 4): every source bare and as the built-in voice, every
// classic() stage alone over a saw, the pitch stages, the gate and the cut moved inside the block, a sample voice, and
// a voice feeding a cylinder's delay, reverb and phaser. All through the production voice path: the real VoiceFactory
// and Voice.render, so the first block's offset is the real one.

/** The note every voice row plays (Hz): 220, low enough that a saw has many partials, and not a divisor of the block. */
const val FRAMING_NOTE_HZ: Double = 220.0

/** The playback id of every framing render: the same id deals the same voice seed (`PlaybackCtx.coreRandom`). */
const val FRAMING_PID: String = "framing"

/** The name a bare tree is registered under for its render. */
private const val BARE_NAME: String = "framingbare"

/** The name of the sample voice: not a registered instrument, so the factory takes the sample branch. */
private const val SAMPLE_NAME: String = "framingsample"

/** What the voice's random stream draws next, read once the voice is done; null when it cannot be read. */
private fun voiceRandomOf(voice: Voice): Random? = runCatching {
    // The stream is the voice's private render context's (VoiceFactory hands `voiceRandom` to it). Read by
    // reflection in this test code only: no engine visibility is widened for the probe.
    val field = Voice::class.java.getDeclaredField("signalCtx").apply { isAccessible = true }

    (field.get(voice) as IgniteContext).random
}.getOrNull()

/**
 * One voice through the production path: a FRESH registry ([makeRegistry]), [VoiceFactory] and [PlaybackCtx] with the
 * pid [FRAMING_PID] (so every render deals the same voice seed), a fresh [Cylinders], and `Voice.render` per 128-frame
 * block from frame 0. The note: [data] at [FramingNote.onsetFrame], its gate [FramingNote.gateFrames] later, a cut
 * (`Voice.cutOff`, called before the block that holds it, as the scheduler cuts) at [FramingNote.cutFrame].
 *
 * Without [bus] the output is the cylinder's mix as the voice left it (the channel: gain, pan), read before any bus
 * stage, both channels. With [bus] it is the engine's fusion mix after `Cylinders.processAndMix`: the cylinder's
 * Katalyst chain run on the voice, owned by its `katalystParams`.
 */
fun renderFramedVoice(
    note: FramingNote,
    data: VoiceData,
    makeRegistry: () -> IgnitorRegistry = { IgnitorRegistry().apply { registerDefaults() } },
    getSample: (SampleRequest) -> SampleStore.SampleEntry.Complete? = { null },
    bus: Boolean = false,
): FramedOutput {
    val sampleRate = PROBE_SAMPLE_RATE
    val blockFrames = PROBE_BLOCK_FRAMES
    val factory = VoiceFactory(sampleRate = sampleRate, blockFrames = blockFrames, scratchBuffers = ScratchBuffers(blockFrames))
    // Half a frame past each frame, so the factory's floor lands on it exactly.
    val voice = factory.makeVoice(
        scheduled = ScheduledVoice(
            playbackId = FRAMING_PID,
            data = data,
            startTime = (note.onsetFrame + 0.5) / sampleRate,
            gateEndTime = (note.onsetFrame + note.gateFrames + 0.5) / sampleRate,
            playbackStartTime = 0.0,
        ),
        backendStartTimeSec = 0.0,
        playbackCtx = PlaybackCtx(playbackId = FRAMING_PID, ignitorRegistry = makeRegistry(), phasePools = PhasePools(Random(1))),
        getSample = getSample,
    ) ?: error("no voice for ${data.sound}")
    val random = voiceRandomOf(voice)
    val cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate)
    val ctx = Voice.RenderContext(
        cylinders = cylinders,
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        voiceBuffer = AudioBuffer(blockFrames),
        scratchBuffers = ScratchBuffers(blockFrames),
    )
    val fusion = StereoBuffer(blockFrames)
    val total = wholeProbeBlocks(note.renderFrames)
    val left = DoubleArray(total)
    val right = DoubleArray(total)
    val cutAt = if (note.cutFrame == FRAMING_NONE) FRAMING_NONE else note.onsetFrame + note.cutFrame
    var alive = true
    var at = 0

    while (at < total) {
        ctx.blockStart = at.toDouble()
        cylinders.clearAll()

        if (cutAt != FRAMING_NONE && cutAt >= at && cutAt < at + blockFrames) {
            voice.cutOff(fadeStartFrame = cutAt.toDouble())
        }

        if (alive) {
            alive = voice.render(ctx)
        }

        if (bus) {
            fusion.clear()
            cylinders.processAndMix(fusion, ctx.blockStart)
            fusion.left.copyInto(destination = left, destinationOffset = at, startIndex = 0, endIndex = blockFrames)
            fusion.right.copyInto(destination = right, destinationOffset = at, startIndex = 0, endIndex = blockFrames)
        } else {
            cylinders.cylinders.firstOrNull()?.let { cylinder ->
                cylinder.mixBuffer.left.copyInto(destination = left, destinationOffset = at, startIndex = 0, endIndex = blockFrames)
                cylinder.mixBuffer.right.copyInto(destination = right, destinationOffset = at, startIndex = 0, endIndex = blockFrames)
            }
        }

        at += blockFrames
    }

    return FramedOutput(left = left, right = right, rngFingerprint = if (alive) null else random?.nextInt())
}

/** The voice data of a framing row: [sound] at [FRAMING_NOTE_HZ] with the note slots [params] (and bus slots [bus]). */
private fun framingData(sound: String, params: Map<String, Double>, bus: Map<String, Double>? = null): VoiceData =
    VoiceData.empty.copy(
        sound = sound,
        freqHz = FRAMING_NOTE_HZ,
        ignitorParams = params.toKlangMap(),
        katalystParams = bus?.toKlangMap(),
    )

private val defaultConfig = FramingConfig()

/** A built-in sound's voice row (`source.pregain().classic()`, the production registry). */
private fun voiceCase(
    family: String,
    name: String,
    knobs: String,
    params: Map<String, Double>,
    sweep: FramingSweep = FramingSweep.ONSET,
    sound: String = "saw",
    known: String = "",
    config: FramingConfig = defaultConfig,
): FramingCase {
    val data = framingData(sound = sound, params = params)

    return FramingCase(family = family, name = name, knobs = knobs, sweep = sweep, config = config, known = known) { note ->
        renderFramedVoice(note = note, data = data)
    }
}

/** A source's bare tree (no classic(), no envelope), registered under its own name for the render. */
private fun bareCase(name: String, tree: IgnitorDsl, params: Map<String, Double>, knobs: String, known: String): FramingCase {
    val data = framingData(sound = BARE_NAME, params = params)
    val registry = { IgnitorRegistry().apply { registerDefaults(); register(BARE_NAME, tree) } }

    return FramingCase(family = "source bare", name = name, knobs = knobs, sweep = FramingSweep.ONSET, config = defaultConfig, known = known) { note ->
        renderFramedVoice(note = note, data = data, makeRegistry = registry)
    }
}

/** The sources: the built-in sounds the click detector measures, by their registered names. */
val FRAMING_SOURCES: List<String> = CLICK_VOICE_SOUNDS

/** Where audio/MEMORY.md names the drift law: quoted next to every analog > 0 row. */
private const val KNOWN_DRIFT: String =
    "by design: audio/MEMORY.md 'Analog drift ... Every lane steps once per block and ramps across it'; " +
        "voice-synthesis.md 'so the block size is part of its sound'"

private const val ADSR_OFF: String = "adsr.on"

/** Where the KLANG-00156 ledger names the filter envelope's per-block chord (E3), and its stated reach. */
private const val KNOWN_E3: String =
    "known: KLANG-00156 E3 'SVF cutoff-env chord straightens knees; segment shorter than a block erased; peak height " +
        "alignment- and block-size-dependent', listed there as 'latent: no production door passes an env here'; " +
        "SvfIgnitor KDoc 'modulates filter cutoff at control rate (at each block's two ends)'"

/** The known quote of a stage row with the note slots [params]. */
private fun knownFor(stage: String, params: Map<String, Double>): String = when {
    params.keys.any { it.endsWith(".env") } -> KNOWN_E3
    "analog" in params -> KNOWN_DRIFT
    stage == "accelerate" -> KNOWN_ACCELERATE
    else -> ""
}

/** Every source bare, as the built-in voice at analog 0 (the engine's default: the `analog` slot defaults to 0) and at analog 1 (drift on). */
fun framingSourceCases(): List<FramingCase> = buildList {
    val sources = builtInSources()

    for (sound in FRAMING_SOURCES) {
        add(bareCase(name = sound, tree = sources.getValue(sound), params = mapOf("analog" to 0.0), knobs = "bare, analog=0", known = ""))
    }

    for (sound in FRAMING_SOURCES) {
        add(voiceCase(family = "source voice", name = sound, knobs = "classic(), default adsr, analog=0 (default)", params = mapOf("analog" to 0.0), sound = sound))
    }

    for (sound in FRAMING_SOURCES) {
        add(
            voiceCase(
                family = "source voice",
                name = sound,
                knobs = "classic(), default adsr, analog=1",
                params = mapOf("analog" to 1.0),
                sound = sound,
                known = KNOWN_DRIFT,
            ),
        )
    }
}

/** One classic() stage alone over the saw voice (analog 0, the adsr off unless [keepAdsr]). */
private fun stageCase(stage: String, knobs: String, params: Map<String, Double>, keepAdsr: Boolean = false, sweep: FramingSweep = FramingSweep.ONSET, known: String = ""): FramingCase =
    voiceCase(
        family = if (sweep == FramingSweep.ONSET) "stage" else "stage ${sweep.name.lowercase()}",
        name = stage,
        knobs = knobs,
        params = mapOf("analog" to 0.0) + (if (keepAdsr) emptyMap() else mapOf(ADSR_OFF to 0.0)) + params,
        sweep = sweep,
        known = known,
    )

/** The classic() stages and their knob sets: the stage probe's catalogue of per-note stages, plus envelopes on the filters. */
private fun stageKnobSets(): List<Triple<String, String, Map<String, Double>>> = buildList {
    fun knob(stage: String, knobs: String, params: Map<String, Double>) {
        add(Triple(stage, knobs, params))
    }

    fun curve(name: String): Double = AdsrCurves.names.indexOf(name).toDouble()

    knob(stage = "onepole", knobs = "freq=1000", params = mapOf("onepole" to 1000.0))
    knob(stage = "crush", knobs = "bits=8", params = mapOf("crush.bits" to 8.0))
    knob(stage = "crush", knobs = "bits=1", params = mapOf("crush.bits" to 1.0))
    knob(stage = "coarse", knobs = "factor=4", params = mapOf("coarse.factor" to 4.0))
    knob(stage = "coarse", knobs = "factor=1.5", params = mapOf("coarse.factor" to 1.5))
    knob(stage = "coarse", knobs = "factor=3", params = mapOf("coarse.factor" to 3.0))

    knob(stage = "distort:soft", knobs = "amount=0.5", params = mapOf("distort.amount" to 0.5))

    for ((index, shape) in DistortionShapes.names.withIndex()) {
        knob(stage = "distort:$shape", knobs = "amount=3", params = mapOf("distort.amount" to 3.0, "distort.shape" to index.toDouble()))
    }

    for (door in listOf("lpf", "hpf")) {
        knob(stage = door, knobs = "freq=1000", params = mapOf("$door.freq" to 1000.0))
        knob(stage = door, knobs = "freq=1000 q=10", params = mapOf("$door.freq" to 1000.0, "$door.q" to 10.0))
        knob(stage = door, knobs = "freq=1000 passes=4", params = mapOf("$door.freq" to 1000.0, "$door.passes" to 4.0))
        knob(stage = door, knobs = "freq=1000 analog=1 (filter drift)", params = mapOf("$door.freq" to 1000.0, "analog" to 1.0))
        knob(stage = door, knobs = "freq=500 env=24 (default env times)", params = mapOf("$door.freq" to 500.0, "$door.env" to 24.0))
        knob(
            stage = door,
            knobs = "freq=500 env=24 a=0.001 d=0.0013 s=0.3 r=0.0017 (sub-block)",
            params = mapOf("$door.freq" to 500.0, "$door.env" to 24.0, "$door.attack" to 0.001, "$door.decay" to 0.0013, "$door.sustain" to 0.3, "$door.release" to 0.0017),
        )
    }

    for (door in listOf("bpf", "notch")) {
        knob(stage = door, knobs = "freq=1000", params = mapOf("$door.freq" to 1000.0))
        knob(stage = door, knobs = "freq=1000 env=24", params = mapOf("$door.freq" to 1000.0, "$door.env" to 24.0))
    }

    knob(stage = "tremolo", knobs = "depth=0.5 rate=5", params = mapOf("tremolo.depth" to 0.5))
    knob(
        stage = "tremolo",
        knobs = "depth=1 rate=40 shape=square",
        params = mapOf("tremolo.depth" to 1.0, "tremolo.rate" to 40.0, "tremolo.shape" to LfoShapes.names.indexOf("square").toDouble()),
    )

    knob(stage = "adsr", knobs = "default", params = mapOf(ADSR_OFF to 1.0))
    knob(stage = "adsr", knobs = "attack=0.0005 (24 frames, under a block)", params = mapOf(ADSR_OFF to 1.0, "adsr.attack" to 0.0005))
    knob(stage = "adsr", knobs = "a=0 d=0 s=1 r=0", params = mapOf(ADSR_OFF to 1.0, "adsr.attack" to 0.0, "adsr.decay" to 0.0, "adsr.sustain" to 1.0, "adsr.release" to 0.0))
    knob(
        stage = "adsr",
        knobs = "a=0.0021 d=0.0013 s=0.5 r=0.0017 (every segment under a block)",
        params = mapOf(ADSR_OFF to 1.0, "adsr.attack" to 0.0021, "adsr.decay" to 0.0013, "adsr.sustain" to 0.5, "adsr.release" to 0.0017),
    )
    knob(
        stage = "adsr",
        knobs = "linear curves a=0.01 d=0.05 s=0.5 r=0.1",
        params = mapOf(
            ADSR_OFF to 1.0, "adsr.attack" to 0.01, "adsr.decay" to 0.05, "adsr.sustain" to 0.5, "adsr.release" to 0.1,
            "adsrCurves.attack" to curve("linear"), "adsrCurves.decay" to curve("linear"), "adsrCurves.release" to curve("linear"),
        ),
    )
    knob(stage = "adsr", knobs = "release=0.2", params = mapOf(ADSR_OFF to 1.0, "adsr.release" to 0.2))
}

/** Every classic() stage alone over the saw voice, onset sweep. */
fun framingStageCases(): List<FramingCase> = stageKnobSets().map { (stage, knobs, params) ->
    stageCase(stage = stage, knobs = knobs, params = params, keepAdsr = ADSR_OFF in params, known = knownFor(stage = stage, params = params))
}

/** The pitch stages alone over the saw voice (the adsr off): vibrato, accelerate, pitch envelope, fm and fm with its envelope. */
private fun pitchKnobSets(): List<Triple<String, String, Map<String, Double>>> = listOf(
    Triple("vibrato", "semitones=0.5 rate=5", mapOf("vibrato.semitones" to 0.5)),
    Triple("vibrato", "semitones=2 rate=13.7", mapOf("vibrato.semitones" to 2.0, "vibrato.rate" to 13.7)),
    Triple("accelerate", "12 semitones", mapOf("accelerate" to 12.0)),
    Triple("pitch envelope", "12 semitones (default times)", mapOf("penv.semitones" to 12.0)),
    Triple(
        "pitch envelope",
        "12 st a=0.001 d=0.0013 s=0.3 r=0.0017 (sub-block)",
        mapOf("penv.semitones" to 12.0, "penv.attack" to 0.001, "penv.decay" to 0.0013, "penv.sustain" to 0.3, "penv.release" to 0.0017),
    ),
    Triple("fm", "depth=100 ratio=default (envelope-free)", mapOf("fm.depth" to 100.0)),
    Triple(
        "fm",
        "depth=300 ratio=1.4 a=0.005 d=0.031 s=0.3 r=0.03 (envelope)",
        mapOf("fm.depth" to 300.0, "fm.ratio" to 1.4, "fm.attack" to 0.005, "fm.decay" to 0.031, "fm.sustain" to 0.3, "fm.release" to 0.03),
    ),
)

private const val KNOWN_ACCELERATE: String =
    "known: KLANG-00156 E7 'per-block-anchored multiplicative recurrence differs by 1-2 ulp' and its P4 row " +
        "'AccelerateRenderer ... NOT bit-identical, and correctly so ... 5.3e-15 across onsets'"

/** The pitch stages, onset sweep. */
fun framingPitchCases(): List<FramingCase> = pitchKnobSets().map { (stage, knobs, params) ->
    stageCase(stage = stage, knobs = knobs, params = params, known = knownFor(stage = stage, params = params))
}

/**
 * The note END moved inside the block: the gate sweep for every stage and pitch case and the plain built-in voices at
 * analog 0 (their default adsr's release); the cut sweep for a few voices.
 */
fun framingTransitionCases(): List<FramingCase> = buildList {
    for (sound in FRAMING_SOURCES) {
        add(voiceCase(family = "gate", name = sound, knobs = "classic(), default adsr, analog=0", params = mapOf("analog" to 0.0), sweep = FramingSweep.GATE, sound = sound))
    }

    for ((stage, knobs, params) in stageKnobSets() + pitchKnobSets()) {
        add(
            stageCase(
                stage = stage,
                knobs = knobs,
                params = params,
                keepAdsr = ADSR_OFF in params,
                sweep = FramingSweep.GATE,
                known = knownFor(stage = stage, params = params),
            ),
        )
    }

    for (sound in listOf("sine", "saw", "supersaw", "pluck", "whitenoise")) {
        add(voiceCase(family = "cut", name = sound, knobs = "classic(), default adsr, analog=0", params = mapOf("analog" to 0.0), sweep = FramingSweep.CUT, sound = sound))
    }

    add(
        voiceCase(
            family = "cut",
            name = "saw",
            knobs = "classic(), adsr.on=0 (teardown), analog=0",
            params = mapOf("analog" to 0.0, ADSR_OFF to 0.0),
            sweep = FramingSweep.CUT,
        ),
    )
}

/** The sample PCM: two seconds of two non-harmonic sines at 48 kHz, so the sample's value shows its playhead. */
private val framingSamplePcm: MonoSamplePcm = MonoSamplePcm(
    sampleRate = PROBE_SAMPLE_RATE,
    pcm = DoubleArray(2 * PROBE_SAMPLE_RATE) { n ->
        val t = n.toDouble() / PROBE_SAMPLE_RATE

        0.5 * sin(2.0 * PI * 220.0 * t) + 0.3 * sin(2.0 * PI * 1234.5 * t)
    },
)

/**
 * The sample voice: the sample instrument over [framingSamplePcm], played at [rate] (the sample's pitch is the note's
 * divided by it), the shape `BlockFramingInvarianceSpec`'s sample driver gives the factory (an unregistered sound name,
 * a `Complete` entry).
 */
private fun sampleCase(knobs: String, rate: Double, params: Map<String, Double>, sweep: FramingSweep): FramingCase {
    val data = framingData(sound = SAMPLE_NAME, params = mapOf("analog" to 0.0) + params)
    val entry = { req: SampleRequest -> SampleStore.SampleEntry.Complete(req = req, note = null, pitchHz = FRAMING_NOTE_HZ / rate, sample = framingSamplePcm) }

    return FramingCase(
        family = if (sweep == FramingSweep.ONSET) "sample" else "sample ${sweep.name.lowercase()}",
        name = "sample voice",
        knobs = knobs,
        sweep = sweep,
        config = defaultConfig,
        known = "",
    ) { note ->
        renderFramedVoice(note = note, data = data, getSample = entry)
    }
}

/** The sample voice at rate 1 and a semitone up, default adsr and the adsr off, onset and gate sweeps. */
fun framingSampleCases(): List<FramingCase> = buildList {
    val semitoneUp = 2.0.pow(1.0 / 12.0)

    for (sweep in listOf(FramingSweep.ONSET, FramingSweep.GATE)) {
        add(sampleCase(knobs = "rate=1, default adsr", rate = 1.0, params = emptyMap(), sweep = sweep))
        add(sampleCase(knobs = "rate=2^(1/12), default adsr", rate = semitoneUp, params = emptyMap(), sweep = sweep))
        add(sampleCase(knobs = "rate=1, adsr.on=0 (teardown)", rate = 1.0, params = mapOf(ADSR_OFF to 0.0), sweep = sweep))
    }
}

/** The bus rows' window: two seconds after the gate, for the echoes and the room's tail. */
private val busConfig = FramingConfig(tailFrames = 2 * PROBE_SAMPLE_RATE)

/**
 * A saw voice (default adsr, analog 0) on cylinder 0, its `katalystParams` [bus] switching the classic chain's stages
 * on; read after `Cylinders.processAndMix`. Does the bus's output move with the note, sample for sample?
 */
private fun busCase(stage: String, knobs: String, bus: Map<String, Double>, known: String): FramingCase {
    val data = framingData(sound = "saw", params = mapOf("analog" to 0.0), bus = bus)

    return FramingCase(family = "bus", name = stage, knobs = knobs, sweep = FramingSweep.ONSET, config = busConfig, known = known) { note ->
        renderFramedVoice(note = note, data = data, bus = true)
    }
}

private const val KNOWN_BUS_GLIDE: String =
    "audio/MEMORY.md 'Knob glide KNOB_GLIDE_SECONDS 0.05 in whole blocks'; KLANG-00156 D14 'Bus params land on the BLOCK grid' " +
        "(a switch-on from a fresh cylinder snaps: click detector, KLANG-00420 'Delay switched ON from off: the feed snaps')"

private const val KNOWN_REVERB_BIAS: String =
    "deliberate exception: audio/CLAUDE.md 'reverb `+ ANTI_DENORMAL`' (Reverb.kt 'adds a tiny ANTI_DENORMAL = 1e-18 bias'): " +
        "the bias runs on the cylinder's clock from its first block, before the note"

/** The Katalyst side: a voice whose onset moves inside the block, feeding the cylinder's classic chain. */
fun framingBusCases(): List<FramingCase> = listOf(
    busCase(stage = "no bus stage", knobs = "empty katalystParams (the classic chain, every stage off)", bus = emptyMap(), known = ""),
    busCase(stage = "delay", knobs = "wet=0.5 time=0.25 feedback=0.5", bus = mapOf("delay.wet" to 0.5, "delay.time" to 0.25, "delay.feedback" to 0.5), known = KNOWN_BUS_GLIDE),
    busCase(stage = "reverb", knobs = "wet=0.5 size=$REVERB_SIZE", bus = mapOf("reverb.wet" to 0.5, "reverb.size" to REVERB_SIZE), known = KNOWN_REVERB_BIAS),
    busCase(
        stage = "delay + reverb",
        knobs = "delay wet=0.5 time=0.25 fb=0.5, reverb wet=0.5 size=$REVERB_SIZE",
        bus = mapOf("delay.wet" to 0.5, "delay.time" to 0.25, "delay.feedback" to 0.5, "reverb.wet" to 0.5, "reverb.size" to REVERB_SIZE),
        known = KNOWN_REVERB_BIAS,
    ),
    busCase(
        stage = "phaser",
        knobs = "wet=0.5 rate=0.5",
        bus = mapOf("phaser.wet" to 0.5, "phaser.rate" to 0.5, "phaser.center" to PHASER_CENTER_HZ, "phaser.sweep" to PHASER_SWEEP_HZ, "phaser.floor" to PHASER_FLOOR),
        known = "by design: KLANG-00156 D2 'the sweep's time base is cylinder-active time' (the bus LFO runs on the cylinder's clock, not the note's); " +
            "D4 'PhaserCore.prepareBlock samples the LFO WAVEFORM at block boundaries'",
    ),
)

/** The whole table: sources, stages, pitch stages, the note end and the cut, the sample voice, the bus. */
fun framingCases(): List<FramingCase> =
    framingSourceCases() + framingStageCases() + framingPitchCases() + framingTransitionCases() + framingSampleCases() + framingBusCases()
