/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import io.peekandpoke.klang.audio_be.cylinders.CylinderSwapRig
import io.peekandpoke.klang.audio_be.cylinders.offerAndCommit
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_be.voices.PlaybackCtx
import io.peekandpoke.klang.audio_be.voices.VoiceFactory
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers
import io.peekandpoke.klang.audio_bridge.BodyMaterials
import io.peekandpoke.klang.audio_bridge.KatalystBuilder
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.Kat
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.VowelBands
import io.peekandpoke.klang.audio_bridge.constants.BODY_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.BODY_WET
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_KNEE_DB
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_RATIO
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_RELEASE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_THRESHOLD_DB
import io.peekandpoke.klang.audio_bridge.constants.DELAY_CAP
import io.peekandpoke.klang.audio_bridge.constants.DELAY_FEEDBACK
import io.peekandpoke.klang.audio_bridge.constants.DELAY_TIME_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.DELAY_WET
import io.peekandpoke.klang.audio_bridge.constants.DUCK_RELEASE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.PHASER_CENTER_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.PHASER_SWEEP_HZ
import io.peekandpoke.klang.audio_bridge.constants.REVERB_SIZE
import io.peekandpoke.klang.audio_bridge.constants.REVERB_WET
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_WET
import io.peekandpoke.klang.common.infra.toKlangMap
import kotlin.random.Random

// The rows of the click table (KLANG-00420, probe 3): the controls, every Katalyst stage's knobs and switches through the
// owner's slot map, two chain swaps through a real cylinder, and the voice edges of every source through the real
// VoiceFactory.

/** The first instant's event frame: block 563, 1.50 s in, after the tone has faded in and every stage has settled. */
const val CLICK_EVENT_FRAME: Int = 563 * PROBE_BLOCK_FRAMES

/** How many instants a Katalyst or control case is measured at: one block apart, so the tone's phase moves 105.6 degrees each. */
const val CLICK_INSTANTS: Int = 4

/** The event window for a switch-off of a delay or a reverb: long enough for its drain to finish and its stage to go Off. */
const val CLICK_DRAIN_SECONDS: Double = 8.0

private val defaultConfig = ClickConfig()

private val drainConfig = ClickConfig(eventSeconds = CLICK_DRAIN_SECONDS)

/** The event frame of instant [instant]. */
private fun eventAt(instant: Int): Int = CLICK_EVENT_FRAME + instant * PROBE_BLOCK_FRAMES

/** The tone for instant [instant] under [config]: long enough for its windows, its peak at the first instant's event. */
private fun toneFor(instant: Int, config: ClickConfig): DoubleArray =
    clickTone(frames = wholeProbeBlocks(eventAt(instant) + config.tailFrames + PROBE_BLOCK_FRAMES), peakFrame = CLICK_EVENT_FRAME)

// ── The controls ──────────────────────────────────────────────────────────────────────────────────────────────────────

/** A control made by hand: the tone times a gain [gainAt] of (frame, event frame), the same on both channels. */
private fun handControl(knob: String, change: String, gainAt: (n: Int, e: Int) -> Double): ClickCase =
    ClickCase(family = "control", stage = "by hand", knob = knob, change = change, instants = CLICK_INSTANTS, config = defaultConfig) { i ->
        val e = eventAt(i)
        val tone = toneFor(instant = i, config = defaultConfig)
        val out = DoubleArray(tone.size) { n -> tone[n] * gainAt(n, e) }

        ClickRender(left = out, right = out.copyOf(), eventFrame = e)
    }

/** The meter's range: nothing, a hard step, a one-block ramp, a 50 ms glide (all 1.0 to 0.25 on the tone). */
fun clickControlCases(): List<ClickCase> = listOf(
    handControl(knob = "nothing happens", change = "the tone alone") { _, _ -> 1.0 },
    handControl(knob = "hard gain step", change = "1 -> 0.25 at one sample") { n, e -> if (n < e) 1.0 else 0.25 },
    handControl(knob = "one-block gain ramp", change = "1 -> 0.25 linear over 128 frames") { n, e ->
        if (n < e) 1.0 else 1.0 - 0.75 * minOf(1.0, (n - e + 1) / 128.0)
    },
    handControl(knob = "50 ms gain glide", change = "1 -> 0.25 linear over 2400 frames") { n, e ->
        if (n < e) 1.0 else 1.0 - 0.75 * minOf(1.0, (n - e + 1) / 2400.0)
    },
    ownerChange(stage = "classic chain", knob = "nothing (empty map both sides)", change = "{} -> {}", dsl = KatalystDsl.classic, before = emptyMap(), after = emptyMap(), family = "control"),
)

// ── Katalyst: the owner's slot map changes at a block boundary ───────────────────────────────────────────────────────

/** One Katalyst case: [dsl] driven with the owner map [before], then [after] from the event block on. */
private fun ownerChange(
    stage: String,
    knob: String,
    change: String,
    dsl: KatalystDsl,
    before: Map<String, Double>,
    after: Map<String, Double>,
    config: ClickConfig = defaultConfig,
    family: String = "katalyst",
): ClickCase {
    val beforeMap = before.toKlangMap()
    val afterMap = after.toKlangMap()

    return ClickCase(family = family, stage = stage, knob = knob, change = change, instants = CLICK_INSTANTS, config = config) { i ->
        val e = eventAt(i)
        val tone = toneFor(instant = i, config = config)
        val out = katalystOwnerChangeStage(dsl = dsl, before = beforeMap, after = afterMap, eventFrame = e)
            .render(ProbeSignal(name = "tone", left = tone, right = tone.copyOf(), inputEndFrame = tone.size))

        ClickRender(left = out.left, right = out.right, eventFrame = e)
    }
}

/** A switch ON (empty map to [on]) and OFF ([on] to empty), on [dsl]. */
private fun switches(stage: String, dsl: KatalystDsl, on: Map<String, Double>, offConfig: ClickConfig = defaultConfig): List<ClickCase> = listOf(
    ownerChange(stage = stage, knob = "switch ON", change = "{} -> ${label(on)}", dsl = dsl, before = emptyMap(), after = on),
    ownerChange(stage = stage, knob = "switch OFF", change = "${label(on)} -> {}", dsl = dsl, before = on, after = emptyMap(), config = offConfig),
)

/** [knob] jumping from its value in [base] to [far] and, in a second case, back. */
private fun jump(stage: String, dsl: KatalystDsl, base: Map<String, Double>, knob: String, far: Double, farLabel: String = far.toString()): List<ClickCase> {
    val near = base[knob]?.toString() ?: "unset"
    val moved = base + (knob to far)

    return listOf(
        ownerChange(stage = stage, knob = knob, change = "$near -> $farLabel", dsl = dsl, before = base, after = moved),
        ownerChange(stage = stage, knob = knob, change = "$farLabel -> $near", dsl = dsl, before = moved, after = base),
    )
}

/** A map as a short label: its keys' last part and values. */
private fun label(map: Map<String, Double>): String = map.entries.joinToString(prefix = "{", postfix = "}", separator = " ") { (k, v) -> "${k.substringAfter('.')}=$v" }

/** One stage of the classic chain's kind, alone: a chain of [stage] only, its knobs the classic chain's slots. */
private fun alone(stage: KatalystStageDsl): KatalystDsl = KatalystDsl.of(stage)

/** The Katalyst cases: every classic-chain stage through the classic chain's own slots, then the stages of a declared chain. */
fun clickKatalystCases(): List<ClickCase> = buildList {
    val classic = KatalystDsl.classic

    val body = mapOf("body.material" to BodyMaterials.indexOf("wood"), "body.wet" to BODY_WET, "body.floor" to BODY_FLOOR)
    addAll(switches(stage = "body", dsl = classic, on = body))
    addAll(jump(stage = "body", dsl = classic, base = body, knob = "body.material", far = BodyMaterials.indexOf("bell"), farLabel = "bell"))
    addAll(jump(stage = "body", dsl = classic, base = body, knob = "body.wet", far = 1.0))
    addAll(jump(stage = "body", dsl = classic, base = body, knob = "body.floor", far = 0.0))

    val vowel = mapOf("vowel.vowel" to VowelBands.indexOf("a"), "vowel.wet" to VOWEL_WET, "vowel.floor" to VOWEL_FLOOR)
    addAll(switches(stage = "vowel", dsl = classic, on = vowel))
    addAll(jump(stage = "vowel", dsl = classic, base = vowel, knob = "vowel.vowel", far = VowelBands.indexOf("i"), farLabel = "i"))
    addAll(jump(stage = "vowel", dsl = classic, base = vowel, knob = "vowel.wet", far = 1.0))
    addAll(jump(stage = "vowel", dsl = classic, base = vowel, knob = "vowel.floor", far = 0.0))

    val delay = mapOf("delay.wet" to DELAY_WET, "delay.time" to DELAY_TIME_SECONDS, "delay.feedback" to DELAY_FEEDBACK, "delay.cap" to DELAY_CAP)
    addAll(switches(stage = "delay", dsl = classic, on = delay, offConfig = drainConfig))
    addAll(jump(stage = "delay", dsl = classic, base = delay, knob = "delay.wet", far = 1.0))
    addAll(jump(stage = "delay", dsl = classic, base = delay, knob = "delay.time", far = 0.05))
    addAll(jump(stage = "delay", dsl = classic, base = delay, knob = "delay.feedback", far = 0.9))
    addAll(jump(stage = "delay", dsl = classic, base = delay, knob = "delay.cap", far = 0.2))

    val reverb = mapOf("reverb.wet" to REVERB_WET, "reverb.size" to REVERB_SIZE)
    addAll(switches(stage = "reverb", dsl = classic, on = reverb, offConfig = drainConfig))
    addAll(jump(stage = "reverb", dsl = classic, base = reverb, knob = "reverb.wet", far = 1.0))
    addAll(jump(stage = "reverb", dsl = classic, base = reverb, knob = "reverb.size", far = 1.0))
    addAll(jump(stage = "reverb", dsl = classic, base = reverb, knob = "reverb.lowpass", far = 1000.0))

    val phaser = mapOf("phaser.wet" to 0.5, "phaser.rate" to 0.5, "phaser.center" to PHASER_CENTER_HZ, "phaser.sweep" to PHASER_SWEEP_HZ, "phaser.floor" to PHASER_FLOOR)
    addAll(switches(stage = "phaser", dsl = classic, on = phaser))
    addAll(jump(stage = "phaser", dsl = classic, base = phaser, knob = "phaser.wet", far = 1.0))
    addAll(jump(stage = "phaser", dsl = classic, base = phaser, knob = "phaser.rate", far = 10.0))
    addAll(jump(stage = "phaser", dsl = classic, base = phaser, knob = "phaser.center", far = 200.0))
    addAll(jump(stage = "phaser", dsl = classic, base = phaser, knob = "phaser.sweep", far = 100.0))
    addAll(jump(stage = "phaser", dsl = classic, base = phaser, knob = "phaser.floor", far = 0.0))

    val compressor = mapOf(
        "compressor.threshold" to COMPRESSOR_THRESHOLD_DB,
        "compressor.ratio" to COMPRESSOR_RATIO,
        "compressor.knee" to COMPRESSOR_KNEE_DB,
        "compressor.attack" to COMPRESSOR_ATTACK_SECONDS,
        "compressor.release" to COMPRESSOR_RELEASE_SECONDS,
    )
    addAll(switches(stage = "compressor", dsl = classic, on = compressor))
    addAll(jump(stage = "compressor", dsl = classic, base = compressor, knob = "compressor.threshold", far = -40.0))
    addAll(jump(stage = "compressor", dsl = classic, base = compressor, knob = "compressor.ratio", far = 20.0))
    addAll(jump(stage = "compressor", dsl = classic, base = compressor, knob = "compressor.knee", far = 0.0))
    addAll(jump(stage = "compressor", dsl = classic, base = compressor, knob = "compressor.attack", far = 0.1))
    addAll(jump(stage = "compressor", dsl = classic, base = compressor, knob = "compressor.release", far = 1.0))

    addAll(jump(stage = "gain (fader)", dsl = classic, base = emptyMap(), knob = "gain.gain", far = 0.25))
    addAll(jump(stage = "gain (fader)", dsl = classic, base = mapOf("gain.gain" to 1.0), knob = "gain.gain", far = 0.0))

    val duck = mapOf("duck.cylinder" to 1.0, "duck.depth" to 0.8, "duck.release" to DUCK_RELEASE_SECONDS)
    addAll(switches(stage = "duck (sidechain: the tone)", dsl = classic, on = duck))
    addAll(jump(stage = "duck (sidechain: the tone)", dsl = classic, base = duck, knob = "duck.depth", far = 0.2))

    // Stages a pattern declares: each alone in its chain, its moving knob a Kat.param slot.
    val unset = SLOT_UNSET
    val distortSoft = alone(KatalystStageDsl.Distort(amount = Kat.param(name = "distort.amount", default = unset), shape = 0))
    val distortOn = mapOf("distort.amount" to 0.5)
    addAll(switches(stage = "distort:soft", dsl = distortSoft, on = distortOn))
    addAll(jump(stage = "distort:soft", dsl = distortSoft, base = distortOn, knob = "distort.amount", far = 2.0))

    val region = KatalystBuilder().oversampleBy(4) { it.distort(amount = Kat.param(name = "distort.amount", default = unset), shape = "hard") }.node
    addAll(switches(stage = "oversampleBy(4) { distort:hard }", dsl = region, on = distortOn))
    addAll(jump(stage = "oversampleBy(4) { distort:hard }", dsl = region, base = distortOn, knob = "distort.amount", far = 2.0))

    val eq = KatalystBuilder().eq { e ->
        e.band(freq = Kat.param(name = "eq.freq", default = 1000.0), q = Kat.param(name = "eq.q", default = 0.707), db = Kat.param(name = "eq.db", default = 0.0))
    }.node
    addAll(jump(stage = "eq band", dsl = eq, base = emptyMap(), knob = "eq.db", far = 12.0))
    addAll(jump(stage = "eq band", dsl = eq, base = mapOf("eq.db" to 12.0), knob = "eq.freq", far = 150.0))
    addAll(jump(stage = "eq band", dsl = eq, base = mapOf("eq.db" to 12.0, "eq.freq" to 150.0), knob = "eq.q", far = 5.0))

    fun arithmetic(stage: String, build: (KatalystKnobDsl) -> KatalystBuilder, key: String, base: Map<String, Double>, far: Double) {
        addAll(jump(stage = stage, dsl = build(Kat.param(name = key, default = unset)).node, base = base, knob = key, far = far))
    }

    arithmetic(stage = "times", build = { KatalystBuilder().times(it) }, key = "op.times", base = mapOf("op.times" to 1.0), far = 0.25)
    arithmetic(stage = "plus", build = { KatalystBuilder().plus(it) }, key = "op.plus", base = mapOf("op.plus" to 0.0), far = 0.25)
    arithmetic(stage = "div", build = { KatalystBuilder().div(it) }, key = "op.div", base = mapOf("op.div" to 1.0), far = 4.0)
    arithmetic(stage = "pow", build = { KatalystBuilder().pow(it) }, key = "op.pow", base = mapOf("op.pow" to 1.0), far = 2.0)
    arithmetic(stage = "min door (Max stage, a floor)", build = { KatalystBuilder().min(it) }, key = "op.floor", base = emptyMap(), far = 0.3)
    arithmetic(stage = "max door (Min stage, a cap)", build = { KatalystBuilder().max(it) }, key = "op.cap", base = emptyMap(), far = -0.3)

    val limiter = KatalystBuilder().limiter(threshold = Kat.param(name = "limiter.threshold", default = -1.0), lookahead = 0.005).node
    addAll(jump(stage = "limiter (lookahead 5 ms)", dsl = limiter, base = emptyMap(), knob = "limiter.threshold", far = -12.0))
}

// ── A chain swap at a real cylinder ──────────────────────────────────────────────────────────────────────────────────

/**
 * A chain swap at a cylinder driven the way `Cylinders.processAndMix` drives it ([CylinderSwapRig], the swap specs'
 * rig): the cylinder first runs [first] (installed while idle, at once), the tone is written into its mix every block,
 * and at the event block it is asked for [second]. A sounding owner (no slots) holds the cylinder throughout.
 */
private fun swapCase(name: String, first: Pair<String, KatalystDsl>?, second: Pair<String, KatalystDsl>, config: ClickConfig = defaultConfig): ClickCase =
    ClickCase(family = "swap", stage = "cylinder chain swap", knob = name, change = "${first?.first ?: "classic"} -> ${second.first}", instants = CLICK_INSTANTS, config = config) { i ->
        val e = eventAt(i)
        val tone = toneFor(instant = i, config = config)
        val rig = CylinderSwapRig(sampleRate = PROBE_SAMPLE_RATE)
        val left = DoubleArray(tone.size)
        val right = DoubleArray(tone.size)

        first?.let { rig.registry.register(it.first, it.second) }
        rig.registry.register(second.first, second.second)

        var at = 0

        while (at < tone.size) {
            if (at == 0 && first != null) {
                rig.cylinder.requestChain(first.first)
            }

            if (at == e) {
                rig.cylinder.requestChain(second.first)
            }

            val block = at
            val out = rig.block(level = 0.0) {
                tone.copyInto(destination = rig.cylinder.mixBuffer.left, destinationOffset = 0, startIndex = block, endIndex = block + PROBE_BLOCK_FRAMES)
                tone.copyInto(destination = rig.cylinder.mixBuffer.right, destinationOffset = 0, startIndex = block, endIndex = block + PROBE_BLOCK_FRAMES)
            }

            out.copyInto(destination = left, destinationOffset = at, startIndex = 0, endIndex = PROBE_BLOCK_FRAMES)
            rig.cylinder.mixBuffer.right.copyInto(destination = right, destinationOffset = at, startIndex = 0, endIndex = PROBE_BLOCK_FRAMES)
            at += PROBE_BLOCK_FRAMES
        }

        ClickRender(left = left, right = right, eventFrame = e)
    }

/**
 * Chain swaps: a crossfade to a quieter chain, into a room, into a delay (its first echo comes back after the ramp,
 * KLANG-00428, audit finding L7a-02), and out of a room (its drain).
 */
fun clickSwapCases(): List<ClickCase> {
    val half = "gain 0.5" to KatalystDsl.of(KatalystStageDsl.Gain(gain = KatalystKnobDsl.Constant(0.5)))
    val room = "room (reverb wet .5 size 3)" to KatalystDsl.of(
        KatalystStageDsl.Reverb(wet = KatalystKnobDsl.Constant(0.5), size = KatalystKnobDsl.Constant(3.0)),
    )
    val echo = "delay (wet .5 time .25 feedback .3)" to KatalystDsl.of(
        KatalystStageDsl.Delay(
            wet = KatalystKnobDsl.Constant(0.5),
            time = KatalystKnobDsl.Constant(0.25),
            feedback = KatalystKnobDsl.Constant(0.3),
        ),
    )

    return listOf(
        swapCase(name = "to a quieter chain", first = null, second = half),
        swapCase(name = "into a room", first = null, second = room),
        swapCase(name = "into a delay", first = null, second = echo),
        swapCase(name = "out of a room (drain)", first = room, second = half, config = drainConfig),
    )
}

// ── Ignitor: the voice edges through the real VoiceFactory ───────────────────────────────────────────────────────────

/** The sources whose voice edges are measured: the built-in sounds of the cost table, by their registered names. */
val CLICK_VOICE_SOUNDS: List<String> = listOf(
    "sine", "saw", "square", "pulze", "tri", "ramp", "zawtooth", "zamp", "impulse",
    "supersaw", "supersine", "supersquare", "supertri", "superramp", "superpluck", "pluck",
    "whitenoise", "brownnoise", "pinknoise", "perlinnoise", "berlinnoise", "dust", "crackle",
)

/** The voice's onset: mid-block (frame 37 of block 188, 0.50 s), as a note starts anywhere in a block. */
const val CLICK_VOICE_START: Int = 188 * PROBE_BLOCK_FRAMES + 37

/**
 * The voice's gate: 2.0 s after its onset, plus a quarter period of the 110 Hz tone (109 frames at 48 kHz), so the note
 * end under the default release lands near a crest of a voice that starts at phase 0 (audit S-01). Without it the tone
 * ran 225.5 cycles to its end, a zero crossing, and the sine's note-end row was blind to any step there by
 * construction (it read clean while the voice was cut at -49.6 dB, audit L2-01).
 */
const val CLICK_VOICE_GATE: Int = CLICK_VOICE_START + 2 * PROBE_SAMPLE_RATE + 109

/** Where a cut's fade starts: 1.0 s after the onset, mid-block too. */
const val CLICK_VOICE_CUT: Int = CLICK_VOICE_START + PROBE_SAMPLE_RATE

/**
 * One voice of the built-in [sound] at [CLICK_TONE_HZ] with the note slots [params], scheduled through the real
 * [VoiceFactory] (the production registry, `registerDefaults`), onset [CLICK_VOICE_START] and gate [CLICK_VOICE_GATE],
 * rendered block by block into its cylinder (gain 1, pan centre) for [frames]; cut at [cutFrame] when it is not -1
 * (`Voice.cutOff`, called before the block that holds the fade's start, as the
 * scheduler cuts a voice when the cutting one is activated).
 */
fun renderVoiceEdges(sound: String, params: Map<String, Double>, frames: Int, cutFrame: Int = -1): ProbeOutput {
    val sampleRate = PROBE_SAMPLE_RATE
    val blockFrames = PROBE_BLOCK_FRAMES
    val registry = IgnitorRegistry().apply { registerDefaults() }
    val factory = VoiceFactory(sampleRate = sampleRate, blockFrames = blockFrames, scratchBuffers = ScratchBuffers(blockFrames))
    // Half a frame past each frame, so the factory's floor lands on it exactly.
    val voice = factory.makeVoice(
        scheduled = ScheduledVoice(
            playbackId = "probe",
            data = VoiceData.empty.copy(sound = sound, freqHz = CLICK_TONE_HZ, ignitorParams = (mapOf("analog" to 0.0) + params).toKlangMap()),
            startTime = (CLICK_VOICE_START + 0.5) / sampleRate,
            gateEndTime = (CLICK_VOICE_GATE + 0.5) / sampleRate,
            playbackStartTime = 0.0,
        ),
        backendStartTimeSec = 0.0,
        playbackCtx = PlaybackCtx(playbackId = "probe", ignitorRegistry = registry, phasePools = PhasePools(Random(1))),
        getSample = { null },
    ) ?: error("no voice for $sound")
    val ctx = VoiceTestHelpers.createContext(blockStart = 0.0, blockFrames = blockFrames, sampleRate = sampleRate)
    val total = wholeProbeBlocks(frames)
    val left = DoubleArray(total)
    val right = DoubleArray(total)
    var at = 0

    while (at < total) {
        ctx.blockStart = at.toDouble()

        if (cutFrame >= at && cutFrame < at + blockFrames) {
            voice.cutOff(fadeStartFrame = cutFrame.toDouble())
        }

        voice.render(ctx)

        val cylinder = ctx.cylinders.offerAndCommit(id = voice.cylinderId, voice = voice, blockStart = ctx.blockStart)

        cylinder.mixBuffer.left.copyInto(destination = left, destinationOffset = at, startIndex = 0, endIndex = blockFrames)
        cylinder.mixBuffer.right.copyInto(destination = right, destinationOffset = at, startIndex = 0, endIndex = blockFrames)
        cylinder.mixBuffer.left.fill(0.0)
        cylinder.mixBuffer.right.fill(0.0)
        at += blockFrames
    }

    return ProbeOutput(left = left, right = right)
}

/** One voice-edge case: [sound] with [params], measured at [eventFrame]; cut at [cutFrame] when it is not -1. */
private fun voiceEdge(sound: String, envelope: String, params: Map<String, Double>, edge: String, eventFrame: Int, cutFrame: Int = -1): ClickCase =
    ClickCase(family = "voice", stage = sound, knob = edge, change = envelope, instants = 1, config = defaultConfig) { _ ->
        val out = renderVoiceEdges(sound = sound, params = params, frames = eventFrame + defaultConfig.tailFrames + PROBE_BLOCK_FRAMES, cutFrame = cutFrame)

        ClickRender(left = out.left, right = out.right, eventFrame = eventFrame)
    }

/** Every source's note start and note end with classic()'s default ADSR and with it off, and its cut with the default ADSR. */
fun clickVoiceCases(): List<ClickCase> = buildList {
    val adsrOff = mapOf("adsr.on" to 0.0)

    for (sound in CLICK_VOICE_SOUNDS) {
        add(voiceEdge(sound = sound, envelope = "classic(), default adsr", params = emptyMap(), edge = "note start", eventFrame = CLICK_VOICE_START))
        add(voiceEdge(sound = sound, envelope = "classic(), default adsr", params = emptyMap(), edge = "note end (gate)", eventFrame = CLICK_VOICE_GATE))
        add(voiceEdge(sound = sound, envelope = "classic(), adsr.on=0", params = adsrOff, edge = "note start", eventFrame = CLICK_VOICE_START))
        add(voiceEdge(sound = sound, envelope = "classic(), adsr.on=0 (teardown fade)", params = adsrOff, edge = "note end (gate)", eventFrame = CLICK_VOICE_GATE))
        add(
            voiceEdge(
                sound = sound,
                envelope = "classic(), default adsr",
                params = emptyMap(),
                edge = "cut (Voice.cutOff, 4 ms fade)",
                eventFrame = CLICK_VOICE_CUT,
                cutFrame = CLICK_VOICE_CUT,
            ),
        )
    }
}

/** The whole table: the controls, the Katalyst stages, the chain swaps, the voice edges. */
fun clickCases(): List<ClickCase> = clickControlCases() + clickKatalystCases() + clickSwapCases() + clickVoiceCases()
