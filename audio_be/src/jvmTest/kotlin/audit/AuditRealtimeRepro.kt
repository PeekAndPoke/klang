/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.audit

import io.kotest.core.spec.style.StringSpec
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.PlaybackEngine
import io.peekandpoke.klang.audio_be.PlaybackEngineDispatcher
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.VoiceWatch
import io.peekandpoke.klang.audio_be.cylinders.Cylinder
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.effects.DelayLine
import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.buildExciter
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_be.katalyst.KatalystDelayEffect
import io.peekandpoke.klang.audio_be.katalyst.delay
import io.peekandpoke.klang.audio_be.probes.allocatedBytesNow
import io.peekandpoke.klang.audio_be.probes.median
import io.peekandpoke.klang.audio_be.probes.nanoNow
import io.peekandpoke.klang.audio_be.probes.probeOutputFile
import io.peekandpoke.klang.audio_be.probes.writeProbeFile
import io.peekandpoke.klang.audio_be.voices.PlaybackCtx
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceFactory
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.classic
import io.peekandpoke.klang.audio_bridge.ign
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.optimize
import io.peekandpoke.klang.audio_bridge.oversampleBy
import io.peekandpoke.klang.audio_bridge.uniqueId
import io.peekandpoke.klang.common.infra.KlangMap
import io.peekandpoke.klang.common.infra.klangMapOf
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

// The disproof pass of the engine audit (KLANG-00420), cluster "realtime and lifecycle": reproductions of the layer
// auditors' findings, measured in the real engine. A measurement, not a gate: nothing here asserts an engine value.
// Every test is enabled only with KLANG_PROBE=audit, prints what it measured and writes it to
// `<build dir>/audio_be/probes/audit-realtime.txt` (the probes' `probeOutputFile`). Timings use the cost probe's clock
// (`nanoNow`) and its per-thread byte counter (`allocatedBytesNow`), medians over repeated runs, with a control.

private const val RT_SR = 48000
private const val RT_BLOCK = 128
private const val RT_PID = "audit"

/** One block's deadline at 48 kHz, in microseconds: 128 / 48000 s. */
private const val RT_DEADLINE_US = RT_BLOCK * 1_000_000.0 / RT_SR

/** The authored shimmer instrument of the build rows: a saw under a shimmer at wet 0, then `classic()`. */
private const val RT_SHIMMER_SOUND = "auditshimmerwet0"

/** A bare saw, no `classic()`: the floor the off tail of `classic()` is measured against (L5-04). */
private const val RT_BARE_SAW_SOUND = "auditbaresaw"

private fun rtFx(x: Double, digits: Int = 2): String = String.format(Locale.ROOT, "%.${digits}f", x)

private fun rtMedian(values: List<Double>): Double = median(values.toDoubleArray())

private fun rtMax(values: List<Double>): Double = if (values.isEmpty()) Double.NaN else values.max()

private fun rtShare(us: Double): String = rtFx(us / RT_DEADLINE_US * 100.0, 1) + " % of the block"

/** The bytes two back-to-back readings of the counter report: the reading's own overhead, subtracted from every window. */
private fun rtByteReadingOverhead(): Double {
    val samples = DoubleArray(301)

    for (i in samples.indices) {
        val a = allocatedBytesNow()
        val b = allocatedBytesNow()
        samples[i] = b - a
    }

    return median(samples)
}

private fun rtVoice(
    note: String,
    atSec: Double,
    gateSec: Double,
    sound: String = "saw",
    freqHz: Double = 220.0,
    gain: Double = 0.3,
    cylinder: Int = 0,
    cut: Int? = null,
    ignitorParams: KlangMap<Double>? = null,
    katalystParams: KlangMap<Double>? = null,
    playbackStartTime: Double = 0.0,
): ScheduledVoice = ScheduledVoice(
    playbackId = RT_PID,
    data = VoiceData.empty.copy(
        note = note,
        sound = sound,
        freqHz = freqHz,
        gain = gain,
        cylinder = cylinder,
        cut = cut,
        ignitorParams = ignitorParams,
        katalystParams = katalystParams,
    ),
    startTime = atSec,
    gateEndTime = atSec + gateSec,
    playbackStartTime = playbackStartTime,
)

/** The live dispatcher at 48 kHz, block 128, timeline from 0; every block timed alone, its feedback drained after. */
private class RtRig(voiceWatch: VoiceWatch? = null) {
    val link = KlangCommLink(capacity = 8192)
    val d: PlaybackEngineDispatcher = PlaybackEngineDispatcher.create(
        sampleRate = RT_SR,
        blockFrames = RT_BLOCK,
        commLink = link.backend,
        performanceTimeMs = { 0.0 },
        voiceWatch = voiceWatch,
    )
    val out = StereoBuffer(RT_BLOCK)
    var cursor = 0.0
    var lastNanos = 0.0
    var lastBytes = 0.0

    init {
        d.setBackendStartTime(0.0)
    }

    val nowSec: Double get() = cursor / RT_SR

    fun send(cmd: KlangCommLink.Cmd) {
        d.handle(cmd)
    }

    fun engine(): PlaybackEngine? = d.engine(RT_PID)

    /** One block; true when a diagnostics report came out of it. */
    fun block(): Boolean {
        val bytesBefore = allocatedBytesNow()
        val start = nanoNow()
        d.renderBlock(cursorFrame = cursor, out = out)
        val end = nanoNow()
        val bytesAfter = allocatedBytesNow()

        cursor += RT_BLOCK
        lastNanos = end - start
        lastBytes = bytesAfter - bytesBefore

        var emitted = false

        while (true) {
            val feedback = link.frontend.feedback.receive() ?: break

            if (feedback is KlangCommLink.Feedback.Diagnostics) {
                emitted = true
            }
        }

        return emitted
    }

    fun renderUntil(sec: Double) {
        while (nowSec < sec) {
            block()
        }
    }
}

/** Per kind of block: the nanoseconds and bytes of every block of that kind. */
private class RtKind {
    val nanos = ArrayList<Double>()
    val bytes = ArrayList<Double>()
}

private class RtBusVariant(
    val label: String,
    val kat: KlangMap<Double>,
    val offKat: KlangMap<Double>?,
    val spacingSec: Double,
    val notes: Int,
)

private fun rtDelayState(effect: KatalystDelayEffect): String = effect.currentState::class.simpleName ?: "?"

/**
 * One cylinder with [RtBusVariant.kat] on its owner, a short saw note every [RtBusVariant.spacingSec] seconds (and, with
 * an off map, a second note half a second later that switches the delay off), rendered through the live dispatcher;
 * every block classified by what happened in it.
 */
private fun rtRunBus(variant: RtBusVariant, overhead: Double): LinkedHashMap<String, RtKind> {
    val rig = RtRig()
    val voices = ArrayList<ScheduledVoice>()

    for (k in 0 until variant.notes) {
        val at = 0.2 + k * variant.spacingSec
        voices.add(rtVoice(note = "on$k", atSec = at, gateSec = 0.05, katalystParams = variant.kat))

        if (variant.offKat != null) {
            voices.add(rtVoice(note = "off$k", atSec = at + 0.5, gateSec = 0.05, katalystParams = variant.offKat))
        }
    }

    rig.send(KlangCommLink.Cmd.ScheduleVoices(playbackId = RT_PID, voices = voices))

    val totalBlocks = ((0.2 + variant.notes * variant.spacingSec) * RT_SR / RT_BLOCK).toInt()
    val kinds = LinkedHashMap<String, RtKind>()
    var cylinder: Cylinder? = null
    var delay: KatalystDelayEffect? = null
    var activations = 0
    var lastVoices = 0

    for (b in 0 until totalBlocks) {
        val activeBefore = cylinder?.isActive == true
        val stateBefore = delay?.let { rtDelayState(it) }

        rig.block()

        val engine = rig.engine()

        if (cylinder == null) {
            cylinder = engine?.cylinders?.cylinders?.firstOrNull()
            delay = cylinder?.delay
        }

        val activeAfter = cylinder?.isActive == true
        val stateAfter = delay?.let { rtDelayState(it) }
        val voicesNow = engine?.scheduler?.getActiveVoiceCount() ?: 0
        val onset = voicesNow > lastVoices
        lastVoices = voicesNow

        val kind = when {
            !activeBefore && activeAfter -> {
                activations++

                if (activations == 1) "activation, unit rented fresh (with the note's build)" else "re-activation, unit kept (with the note's build)"
            }

            activeBefore && !activeAfter && stateBefore == "Draining" -> "DRAIN END and DEACTIVATION in one block (two ring clears)"
            activeBefore && !activeAfter -> "DEACTIVATION, chain.reset()"
            stateBefore == "Active" && stateAfter == "Draining" -> "SWITCH-OFF, Active.leave with the tap-window scan"
            stateBefore == "Active" && stateAfter == "Off" -> "SWITCH-OFF, Active.leave, ring reset at once"
            stateBefore == "Draining" && stateAfter == "Off" -> "DRAIN END, DelayLine.reset()"
            onset -> "onset block (a voice build)"
            activeAfter -> "ordinary block, cylinder active"
            else -> "ordinary block, cylinder idle"
        }

        val k = kinds.getOrPut(kind) { RtKind() }
        k.nanos.add(rig.lastNanos)
        k.bytes.add(rig.lastBytes - overhead)
    }

    return kinds
}

/** A build case: a sound and the note's slots. */
private class RtBuildCase(val label: String, val sound: String, val params: KlangMap<Double>?)

private fun rtShimmerInstrument(): IgnitorDsl = IgnitorDsl.Shimmer(
    inner = IgnitorDsl.Saw(freq = IgnitorDsl.Freq, analog = IgnitorDsl.Slots.analog),
    wet = IgnitorDsl.Constant(0.0),
).classic()

private fun rtBareSaw(): IgnitorDsl = IgnitorDsl.Saw(freq = IgnitorDsl.Freq, analog = IgnitorDsl.Slots.analog)

/** Every stage of `classic()` switched on by its slot (the adsr is on by default). */
private fun rtAllClassicOn(): KlangMap<Double> = klangMapOf(
    "fm.depth" to 100.0,
    "penv.semitones" to 12.0,
    "accelerate" to 12.0,
    "vibrato.semitones" to 0.5,
    "onepole" to 3000.0,
    "crush.bits" to 8.0,
    "coarse.factor" to 2.0,
    "distort.amount" to 0.5,
    "hpf.freq" to 100.0,
    "bpf.freq" to 1000.0,
    "notch.freq" to 3000.0,
    "lpf.freq" to 5000.0,
    "tremolo.depth" to 0.5,
)

private fun rtBuildCases(): List<RtBuildCase> = listOf(
    RtBuildCase(label = "bare saw, no classic() (registered tree)", sound = RT_BARE_SAW_SOUND, params = null),
    RtBuildCase(label = "saw, everything off (adsr.on 0)", sound = "saw", params = klangMapOf("adsr.on" to 0.0)),
    RtBuildCase(label = "saw, default voice (classic adsr only)", sound = "saw", params = null),
    RtBuildCase(label = "saw, every classic stage on", sound = "saw", params = rtAllClassicOn()),
    RtBuildCase(label = "pluck (default)", sound = "pluck", params = null),
    RtBuildCase(label = "superpluck (8 strings, default)", sound = "superpluck", params = null),
    RtBuildCase(label = "shimmer at wet 0 over a saw, classic()", sound = RT_SHIMMER_SOUND, params = null),
    RtBuildCase(label = "saw in a 4x region (lowpass in the body), classic()", sound = RT_REGION_SOUND, params = null),
)

/** An authored instrument with an oversampling region (L5-09: the region's child context at the first block). */
private const val RT_REGION_SOUND = "auditregion4x"

private fun rtRegionInstrument(): IgnitorDsl = IgnitorDsl.Saw(freq = IgnitorDsl.Freq, analog = IgnitorDsl.Slots.analog)
    .oversampleBy(4.ign) { y -> IgnitorDsl.Lowpass(inner = y, freq = IgnitorDsl.Constant(3000.0)) }
    .classic()

private class RtWatch : VoiceWatch {
    val lines = ArrayList<String>()

    override fun started(playbackId: String, data: VoiceData, atSec: Double) {
        lines.add("started ${data.note} at ${rtFx(atSec, 3)} s")
    }

    override fun late(playbackId: String, data: VoiceData, atSec: Double) {
        lines.add("late ${data.note} at ${rtFx(atSec, 3)} s")
    }

    override fun soundless(playbackId: String, data: VoiceData, atSec: Double) {
        lines.add("soundless ${data.note} at ${rtFx(atSec, 3)} s")
    }
}

/** A render context of its own (private cylinders), for a voice rendered alone. */
private fun rtRenderContext(): Voice.RenderContext = Voice.RenderContext(
    cylinders = Cylinders(blockFrames = RT_BLOCK, sampleRate = RT_SR),
    sampleRate = RT_SR,
    blockFrames = RT_BLOCK,
    voiceBuffer = AudioBuffer(RT_BLOCK),
    scratchBuffers = ScratchBuffers(RT_BLOCK),
)

/** A tree built and rendered alone from voice frame [startElapsed], held gate, [blocks] blocks, mono. */
private fun rtRenderTree(tree: IgnitorDsl, freqHz: Double, blocks: Int, startElapsed: Int = 0): DoubleArray {
    val built = tree.buildExciter(ignitorParams = null, random = Random(7), freqHz = freqHz, sampleRate = RT_SR, blockFrames = RT_BLOCK)
    val ctx = IgniteContext(
        sampleRate = RT_SR,
        voiceDurationFrames = Int.MAX_VALUE,
        gateEndFrame = Int.MAX_VALUE,
        scratchBuffers = ScratchBuffers(RT_BLOCK),
        random = Random(7),
    )
    val buffer = AudioBuffer(RT_BLOCK)
    val out = DoubleArray(blocks * RT_BLOCK)

    for (b in 0 until blocks) {
        buffer.fill(0.0)
        ctx.voiceElapsedFrames = startElapsed + b * RT_BLOCK
        ctx.updateOffsetAndLength(offset = 0, length = RT_BLOCK)
        built.ignitor.generate(buffer, freqHz, ctx)
        buffer.copyInto(destination = out, destinationOffset = b * RT_BLOCK, startIndex = 0, endIndex = RT_BLOCK)
    }

    return out
}

private fun rtRms(x: DoubleArray): Double {
    var sum = 0.0

    for (v in x) {
        sum += v * v
    }

    return if (x.isEmpty()) 0.0 else sqrt(sum / x.size)
}

private fun rtPeak(x: DoubleArray, from: Int = 0, to: Int = x.size): Double {
    var peak = 0.0

    for (i in from until to) {
        peak = max(peak, abs(x[i]))
    }

    return peak
}

/** The largest |a - b| between block [b] and block [b] - 1 of [x]. */
private fun rtBlockRepeat(x: DoubleArray, b: Int): Double {
    var worst = 0.0

    for (i in 0 until RT_BLOCK) {
        worst = max(worst, abs(x[b * RT_BLOCK + i] - x[(b - 1) * RT_BLOCK + i]))
    }

    return worst
}

class AuditRealtimeRepro : StringSpec({

    val requested = System.getenv("KLANG_PROBE") == "audit"
    val report = StringBuilder()

    fun line(text: String) {
        println(text)
        report.appendLine(text)
    }

    fun flush() {
        writeProbeFile(target = probeOutputFile("audit-realtime.txt"), text = report.toString(), what = "audit realtime")
    }

    "L6-03, L7b-06: the block in which a cylinder with a delay of 0.25 s or 10 s, or a reverb, deactivates".config(enabled = requested) {
        val overhead = rtByteReadingOverhead()
        val delayShort = klangMapOf("delay.wet" to 1.0, "delay.time" to 0.25, "delay.feedback" to 0.0)
        val delayLong = klangMapOf("delay.wet" to 1.0, "delay.time" to 10.0, "delay.feedback" to 0.0)
        val room = klangMapOf("reverb.wet" to 1.0, "reverb.size" to 5.0)
        val variants = listOf(
            RtBusVariant(label = "delay 0.25 s (class 0 ring, 24064 frames)", kat = delayShort, offKat = null, spacingSec = 2.0, notes = 25),
            RtBusVariant(label = "delay 10 s (class 5 ring, 770048 frames, 12.3 MB)", kat = delayLong, offKat = null, spacingSec = 24.0, notes = 8),
            RtBusVariant(label = "reverb size 5 (one network)", kat = room, offKat = null, spacingSec = 10.0, notes = 10),
            RtBusVariant(
                label = "delay 10 s switched off by the next note (delay.time 0)",
                kat = delayLong,
                offKat = klangMapOf("delay.time" to 0.0),
                spacingSec = 26.0,
                notes = 6,
            ),
        )

        line("== L6-03, L7b-06: deactivation, switch-off and drain-end blocks, live dispatcher, 48 kHz, block 128 (deadline ${rtFx(RT_DEADLINE_US, 1)} us)")
        line("   byte-counter reading overhead subtracted: ${rtFx(overhead, 0)} B")

        // Warm-up pass: every variant once, discarded (the JIT, the allocator)
        for (variant in variants) {
            rtRunBus(variant = variant, overhead = overhead)
        }

        for (variant in variants) {
            val kinds = rtRunBus(variant = variant, overhead = overhead)

            line("-- ${variant.label}")

            for ((kind, k) in kinds) {
                val med = rtMedian(k.nanos) / 1000.0

                line(
                    "   $kind: n=${k.nanos.size}, median ${rtFx(med, 1)} us (${rtShare(med)}), " +
                        "max ${rtFx(rtMax(k.nanos) / 1000.0, 1)} us, median bytes ${rtFx(rtMedian(k.bytes), 0)}",
                )
            }
        }

        flush()
    }

    "L6-03, L7b-06: DelayLine.reset and Reverb.reset alone, hot and after a cache eviction, and a fresh 10 s ring".config(enabled = requested) {
        val class0 = DelayLine(ring = StereoBuffer(24064), sampleRate = RT_SR)
        val class5 = DelayLine(ring = StereoBuffer(770048), sampleRate = RT_SR)
        val reverb = Reverb(sampleRate = RT_SR)
        val evict = DoubleArray(8 * 1024 * 1024)
        val warm = 15
        val rounds = 41
        val hot0 = ArrayList<Double>()
        val hot5 = ArrayList<Double>()
        val cold5 = ArrayList<Double>()
        val coldReverb = ArrayList<Double>()
        val hotReverb = ArrayList<Double>()
        val fresh5 = ArrayList<Double>()
        val control = ArrayList<Double>()
        var sink = 0.0

        for (r in 0 until warm + rounds) {
            val t0 = nanoNow()
            class0.reset()
            val t1 = nanoNow()
            class5.reset()
            val t2 = nanoNow()
            reverb.reset()
            val t3 = nanoNow()
            val t4 = nanoNow()

            for (i in evict.indices) {
                evict[i] = (i + r).toDouble()
            }

            val t5 = nanoNow()
            class5.reset()
            val t6 = nanoNow()

            for (i in evict.indices) {
                evict[i] = (i - r).toDouble()
            }

            val t7 = nanoNow()
            reverb.reset()
            val t8 = nanoNow()
            val t9 = nanoNow()
            val fresh = StereoBuffer(770048)
            val t10 = nanoNow()
            sink += fresh.left[r] + evict[r]

            if (r >= warm) {
                hot0.add(t1 - t0)
                hot5.add(t2 - t1)
                hotReverb.add(t3 - t2)
                control.add(t4 - t3)
                cold5.add(t6 - t5)
                coldReverb.add(t8 - t7)
                fresh5.add(t10 - t9)
            }
        }

        line("== L6-03, L7b-06: the clears alone, median of $rounds rounds after $warm warm-up rounds (sink ${rtFx(sink, 0)})")
        line("   control (two clock readings): ${rtFx(rtMedian(control), 0)} ns")
        line("   DelayLine.reset, class 0 ring (24064 frames, 385 KB), hot: ${rtFx(rtMedian(hot0) / 1000.0, 1)} us (${rtShare(rtMedian(hot0) / 1000.0)})")
        line("   DelayLine.reset, class 5 ring (770048 frames, 12.3 MB), hot: ${rtFx(rtMedian(hot5) / 1000.0, 1)} us (${rtShare(rtMedian(hot5) / 1000.0)}), max ${rtFx(rtMax(hot5) / 1000.0, 1)} us")
        line("   DelayLine.reset, class 5 ring, after a 64 MB eviction: ${rtFx(rtMedian(cold5) / 1000.0, 1)} us (${rtShare(rtMedian(cold5) / 1000.0)}), max ${rtFx(rtMax(cold5) / 1000.0, 1)} us")
        line("   Reverb.reset (one network, ${reverb.storeCount} samples), hot: ${rtFx(rtMedian(hotReverb) / 1000.0, 1)} us; after an eviction: ${rtFx(rtMedian(coldReverb) / 1000.0, 1)} us")
        line("   a fresh StereoBuffer(770048) (what a first activation of a 10 s delay allocates): ${rtFx(rtMedian(fresh5) / 1000.0, 1)} us (${rtShare(rtMedian(fresh5) / 1000.0)}), max ${rtFx(rtMax(fresh5) / 1000.0, 1)} us")

        flush()
    }

    "L2-06, L4-09, L5-03, L5-04, L5-09: bytes and microseconds per voice build (VoiceFactory.makeVoice) and per first block".config(enabled = requested) {
        val registry = IgnitorRegistry().apply { registerDefaults() }
        registry.register(name = RT_SHIMMER_SOUND, dsl = rtShimmerInstrument())
        registry.register(name = RT_BARE_SAW_SOUND, dsl = rtBareSaw())
        registry.register(name = RT_REGION_SOUND, dsl = rtRegionInstrument())

        val playbackCtx = PlaybackCtx(playbackId = RT_PID, ignitorRegistry = registry.fork(), phasePools = PhasePools(Random(1)))
        val factory = VoiceFactory(sampleRate = RT_SR, blockFrames = RT_BLOCK, scratchBuffers = ScratchBuffers(RT_BLOCK))
        val cases = rtBuildCases()
        val scheduled = cases.map { rtVoice(note = "c4", atSec = 1.0, gateSec = 0.5, sound = it.sound, freqHz = 261.63, ignitorParams = it.params) }

        // Warm the render path of every case first (the first run measured the heavy cases' blocks JIT-cold)
        run {
            val warmCtx = rtRenderContext()

            for (i in 0 until 1500) {
                for (sv in scheduled) {
                    val v = factory.makeVoice(scheduled = sv, backendStartTimeSec = 0.0, playbackCtx = playbackCtx, getSample = { null })!!
                    warmCtx.blockStart = 48000.0
                    v.render(warmCtx)
                    warmCtx.blockStart = 48000.0 + RT_BLOCK
                    v.render(warmCtx)
                }
            }
        }
        val overhead = rtByteReadingOverhead()
        val perRound = 40
        val warm = 25
        val rounds = 31
        val buildNs = List(cases.size) { ArrayList<Double>() }
        val buildBytes = List(cases.size) { ArrayList<Double>() }
        val firstNs = List(cases.size) { ArrayList<Double>() }
        val firstBytes = List(cases.size) { ArrayList<Double>() }
        val secondNs = List(cases.size) { ArrayList<Double>() }
        val secondBytes = List(cases.size) { ArrayList<Double>() }
        val renderCtx = rtRenderContext()
        var sinkVoice: Voice? = null

        for (r in 0 until warm + rounds) {
            val order = cases.indices.shuffled(Random(r))

            for (ci in order) {
                val sv = scheduled[ci]

                val b0 = allocatedBytesNow()
                val t0 = nanoNow()

                for (i in 0 until perRound) {
                    sinkVoice = factory.makeVoice(scheduled = sv, backendStartTimeSec = 0.0, playbackCtx = playbackCtx, getSample = { null })
                }

                val t1 = nanoNow()
                val b1 = allocatedBytesNow()

                // The first and the second block of one fresh voice
                val v = factory.makeVoice(scheduled = sv, backendStartTimeSec = 0.0, playbackCtx = playbackCtx, getSample = { null })!!
                renderCtx.blockStart = 48000.0
                val b2 = allocatedBytesNow()
                val t2 = nanoNow()
                v.render(renderCtx)
                val t3 = nanoNow()
                val b3 = allocatedBytesNow()
                renderCtx.blockStart = 48000.0 + RT_BLOCK
                val b4 = allocatedBytesNow()
                val t4 = nanoNow()
                v.render(renderCtx)
                val t5 = nanoNow()
                val b5 = allocatedBytesNow()

                if (r >= warm) {
                    buildNs[ci].add((t1 - t0) / perRound)
                    buildBytes[ci].add((b1 - b0 - overhead) / perRound)
                    firstNs[ci].add(t3 - t2)
                    firstBytes[ci].add(b3 - b2 - overhead)
                    secondNs[ci].add(t5 - t4)
                    secondBytes[ci].add(b5 - b4 - overhead)
                }
            }
        }

        line("== L2-06, L4-09, L5-03, L5-04, L5-09: one voice build through VoiceFactory.makeVoice (the production build, optimized registered tree), 48 kHz")
        line("   median of $rounds rounds of $perRound builds each (after $warm warm-up rounds, cases interleaved); then one fresh voice's first and second block")
        line("   (sink: ${sinkVoice?.id ?: -1})")

        for (ci in cases.indices) {
            val us = rtMedian(buildNs[ci]) / 1000.0

            line(
                "   ${cases[ci].label}: build ${rtFx(us, 2)} us (${rtShare(us)}), ${rtFx(rtMedian(buildBytes[ci]), 0)} B; " +
                    "first block ${rtFx(rtMedian(firstNs[ci]) / 1000.0, 2)} us, ${rtFx(rtMedian(firstBytes[ci]), 0)} B; " +
                    "second block ${rtFx(rtMedian(secondNs[ci]) / 1000.0, 2)} us, ${rtFx(rtMedian(secondBytes[ci]), 0)} B",
            )
        }

        flush()
    }

    "L2-06: a burst of 16 simultaneous note starts, the onset block against the deadline (live dispatcher)".config(enabled = requested) {
        val cases = rtBuildCases().filter { it.sound != RT_BARE_SAW_SOUND }
        val onsetNs = List(cases.size) { ArrayList<Double>() }
        val onsetBytes = List(cases.size) { ArrayList<Double>() }
        val steadyNs = List(cases.size) { ArrayList<Double>() }
        val overhead = rtByteReadingOverhead()
        val shimmer = rtShimmerInstrument()
        val region = rtRegionInstrument()
        val warm = 4
        val reps = 14

        for (rep in 0 until warm + reps) {
            for (ci in cases.indices.shuffled(Random(rep))) {
                val case = cases[ci]
                val rig = RtRig()
                rig.d.ignitorRegistry.register(name = RT_SHIMMER_SOUND, dsl = shimmer)
                rig.d.ignitorRegistry.register(name = RT_REGION_SOUND, dsl = region)

                val onset = 0.512
                val voices = (0 until 16).map { i ->
                    rtVoice(
                        note = "n$i",
                        atSec = onset,
                        gateSec = 0.3,
                        sound = case.sound,
                        freqHz = 220.0 * 2.0.pow(i / 12.0),
                        ignitorParams = case.params,
                    )
                }

                rig.send(KlangCommLink.Cmd.ScheduleVoices(playbackId = RT_PID, voices = voices))

                var onsetBlockNs = Double.NaN
                var onsetBlockBytes = Double.NaN
                val after = ArrayList<Double>()

                while (rig.nowSec < onset + 0.12) {
                    val before = rig.engine()?.scheduler?.getActiveVoiceCount() ?: 0
                    rig.block()
                    val now = rig.engine()?.scheduler?.getActiveVoiceCount() ?: 0

                    if (now > before) {
                        onsetBlockNs = rig.lastNanos
                        onsetBlockBytes = rig.lastBytes - overhead
                    } else if (now == 16 && after.size < 20) {
                        after.add(rig.lastNanos)
                    }
                }

                if (rep >= warm) {
                    onsetNs[ci].add(onsetBlockNs)
                    onsetBytes[ci].add(onsetBlockBytes)
                    steadyNs[ci].add(rtMedian(after))
                }
            }
        }

        line("== L2-06: 16 voices starting in one block, fresh dispatcher per repetition, median of $reps repetitions after $warm warm-up")

        for (ci in cases.indices) {
            val onsetUs = rtMedian(onsetNs[ci]) / 1000.0
            val steadyUs = rtMedian(steadyNs[ci]) / 1000.0

            line(
                "   ${cases[ci].label}: onset block ${rtFx(onsetUs, 1)} us (${rtShare(onsetUs)}), max ${rtFx(rtMax(onsetNs[ci]) / 1000.0, 1)} us, " +
                    "${rtFx(rtMedian(onsetBytes[ci]) / 1024.0, 1)} KB; the next blocks with the 16 sounding ${rtFx(steadyUs, 1)} us (${rtShare(steadyUs)}); " +
                    "onset excess ${rtFx(onsetUs - steadyUs, 1)} us",
            )
        }

        flush()
    }

    "L2-06: one note's onset block, isolated (a note a second) against dense (a note every second block)".config(enabled = requested) {
        fun onsets(spacingSec: Double, notes: Int): Pair<Double, Double> {
            val rig = RtRig()
            val voices = (0 until notes).map { k -> rtVoice(note = "n$k", atSec = 0.1 + k * spacingSec, gateSec = 0.01, sound = "saw") }
            rig.send(KlangCommLink.Cmd.ScheduleVoices(playbackId = RT_PID, voices = voices))

            // The block each onset falls in (onset frames 4800 + k * spacing, never on a block border here)
            val onsetBlocks = voices.map { (it.startTime * RT_SR / RT_BLOCK).toInt() }.toSet()
            val onsetNs = ArrayList<Double>()
            val quietNs = ArrayList<Double>()
            var blockIndex = 0

            while (rig.nowSec < 0.1 + notes * spacingSec) {
                rig.block()

                if (blockIndex in onsetBlocks) {
                    onsetNs.add(rig.lastNanos)
                } else {
                    quietNs.add(rig.lastNanos)
                }

                blockIndex++
            }

            return rtMedian(onsetNs) to rtMedian(quietNs)
        }

        // Warm-up
        onsets(spacingSec = 2.0 * RT_BLOCK / RT_SR, notes = 2000)
        onsets(spacingSec = 0.25, notes = 40)

        val (denseOnset, denseQuiet) = onsets(spacingSec = 2.0 * RT_BLOCK / RT_SR, notes = 2000)
        val (sparseOnset, sparseQuiet) = onsets(spacingSec = 1.0, notes = 40)

        line("== L2-06: the onset block of one default saw voice (build + first block + bookkeeping), live dispatcher")
        line("   dense, a note every 2 blocks: onset block ${rtFx(denseOnset / 1000.0, 1)} us (${rtShare(denseOnset / 1000.0)}), other blocks ${rtFx(denseQuiet / 1000.0, 1)} us")
        line("   isolated, a note a second: onset block ${rtFx(sparseOnset / 1000.0, 1)} us (${rtShare(sparseOnset / 1000.0)}), other blocks ${rtFx(sparseQuiet / 1000.0, 1)} us")

        flush()
    }

    "L1-06, L8-05, L9-08: bytes and time per diagnostics report on the JVM, idle and with 8 cylinders".config(enabled = requested) {
        val overhead = rtByteReadingOverhead()

        fun measure(rig: RtRig, label: String) {
            repeat(2000) { rig.block() }

            val emitNs = ArrayList<Double>()
            val emitBytes = ArrayList<Double>()
            val quietNs = ArrayList<Double>()
            val quietBytes = ArrayList<Double>()
            val blocks = 8000

            for (b in 0 until blocks) {
                val emitted = rig.block()

                if (emitted) {
                    emitNs.add(rig.lastNanos)
                    emitBytes.add(rig.lastBytes - overhead)
                } else {
                    quietNs.add(rig.lastNanos)
                    quietBytes.add(rig.lastBytes - overhead)
                }
            }

            val seconds = blocks * RT_BLOCK / RT_SR.toDouble()
            val perReportBytes = rtMedian(emitBytes) - rtMedian(quietBytes)
            val perReportNs = rtMedian(emitNs) - rtMedian(quietNs)

            line(
                "   $label: ${emitNs.size} reports in ${rtFx(seconds, 2)} s (${rtFx(emitNs.size / seconds, 1)} per second); " +
                    "a report block ${rtFx(rtMedian(emitNs) / 1000.0, 2)} us and ${rtFx(rtMedian(emitBytes), 0)} B, " +
                    "a block without ${rtFx(rtMedian(quietNs) / 1000.0, 2)} us and ${rtFx(rtMedian(quietBytes), 0)} B; " +
                    "per report ${rtFx(perReportBytes, 0)} B and ${rtFx(perReportNs / 1000.0, 2)} us; " +
                    "bytes per second from the reports ${rtFx(perReportBytes * emitNs.size / seconds / 1024.0, 1)} KB/s",
            )
        }

        line("== L1-06, L8-05, L9-08: the diagnostics emission, live dispatcher on the JVM, 48 kHz, 8000 blocks after 2000 warm-up")

        measure(RtRig(), "idle dispatcher (no engine)")

        val song = RtRig()
        val held = (0 until 8).map { c -> rtVoice(note = "c$c", atSec = 0.0, gateSec = 1000.0, sound = "sine", cylinder = c) }
        song.send(KlangCommLink.Cmd.ScheduleVoices(playbackId = RT_PID, voices = held))
        measure(song, "one playback, 8 held sine voices on 8 cylinders")

        flush()
    }

    "L9-02, L9-04: a ScheduleVoices after its playback's Cleanup, and a gain sent to a stopped engine".config(enabled = requested) {
        line("== L9-02: Cleanup(p), then a straggler ScheduleVoices(p) for startTime 40 s with the old playbackStartTime 0")

        // A: the straggler arrives while the stopped engine still exists
        run {
            val watch = RtWatch()
            val rig = RtRig(voiceWatch = watch)
            rig.send(KlangCommLink.Cmd.ScheduleVoices(playbackId = RT_PID, voices = listOf(rtVoice(note = "first", atSec = 0.0, gateSec = 0.1, sound = "sine"))))
            rig.renderUntil(1.0)
            rig.send(KlangCommLink.Cmd.Cleanup(playbackId = RT_PID))
            val phaseAtCleanup = rig.engine()?.phase
            rig.send(
                KlangCommLink.Cmd.ScheduleVoices(
                    playbackId = RT_PID,
                    voices = listOf(rtVoice(note = "straggler", atSec = 40.0, gateSec = 0.1, sound = "sine", playbackStartTime = 0.0)),
                ),
            )
            val phaseAfter = rig.engine()?.phase
            rig.renderUntil(50.0)
            val at50 = "phase ${rig.engine()?.phase}, engines rendered ${rig.d.renderedEngineCountForTest}, ids ${rig.d.activePlaybackIds}"
            rig.renderUntil(90.0)
            val at90 = "phase ${rig.engine()?.phase}, engines rendered ${rig.d.renderedEngineCountForTest}, ids ${rig.d.activePlaybackIds}"

            line("   A (engine still Stopped, cleanup at 1.0 s): phase after Cleanup $phaseAtCleanup, after the straggler $phaseAfter")
            line("     watch: ${watch.lines}")
            line("     at 50 s: $at50; at 90 s: $at90")
        }

        // B: the straggler arrives after the stopped engine was disposed
        run {
            val watch = RtWatch()
            val rig = RtRig(voiceWatch = watch)
            rig.send(KlangCommLink.Cmd.ScheduleVoices(playbackId = RT_PID, voices = listOf(rtVoice(note = "first", atSec = 0.0, gateSec = 0.1, sound = "sine"))))
            rig.renderUntil(1.0)
            rig.send(KlangCommLink.Cmd.Cleanup(playbackId = RT_PID))

            while (rig.engine() != null && rig.nowSec < 10.0) {
                rig.block()
            }

            val disposedAt = rig.nowSec
            rig.send(
                KlangCommLink.Cmd.ScheduleVoices(
                    playbackId = RT_PID,
                    voices = listOf(rtVoice(note = "straggler", atSec = 40.0, gateSec = 0.1, sound = "sine", playbackStartTime = 0.0)),
                ),
            )
            rig.renderUntil(55.0)
            val at55 = "phase ${rig.engine()?.phase}, engines rendered ${rig.d.renderedEngineCountForTest}, ids ${rig.d.activePlaybackIds}"

            line("   B (engine disposed at ${rtFx(disposedAt, 3)} s, then the straggler): watch ${watch.lines}; at 55 s: $at55")
        }

        line("== L9-04 (1): play, Cleanup, SetGain(p, 0.3) at once (a restart's first command), the old engine goes idle, then the restart's ScheduleVoices")

        run {
            val rig = RtRig()
            rig.send(KlangCommLink.Cmd.ScheduleVoices(playbackId = RT_PID, voices = listOf(rtVoice(note = "first", atSec = 0.0, gateSec = 0.1, sound = "sine"))))
            rig.renderUntil(0.5)
            rig.send(KlangCommLink.Cmd.Cleanup(playbackId = RT_PID))
            rig.send(KlangCommLink.Cmd.SetGain(playbackId = RT_PID, gain = 0.3, rampSec = 0.0))
            val gainOnStopped = rig.engine()?.gainTargetForTest

            while (rig.engine() != null && rig.nowSec < 10.0) {
                rig.block()
            }

            val disposedAt = rig.nowSec
            rig.send(
                KlangCommLink.Cmd.ScheduleVoices(
                    playbackId = RT_PID,
                    voices = listOf(rtVoice(note = "restart", atSec = 0.0, gateSec = 0.1, sound = "sine", playbackStartTime = rig.nowSec)),
                ),
            )

            line(
                "   gain target on the stopped engine ${gainOnStopped}; it was disposed at ${rtFx(disposedAt, 3)} s; " +
                    "gain target of the engine the restart's voices land on: ${rig.engine()?.gainTargetForTest} (asked for 0.3)",
            )
        }

        flush()
    }

    "L5-02: two trees equal by structure and different by sharing get one name and make two sounds".config(enabled = requested) {
        val p = IgnitorDsl.Pluck()
        val shared = IgnitorDsl.Plus(left = p, right = IgnitorDsl.Lowpass(inner = p, freq = IgnitorDsl.Constant(800.0)))
        val twins = IgnitorDsl.Plus(left = IgnitorDsl.Pluck(), right = IgnitorDsl.Lowpass(inner = IgnitorDsl.Pluck(), freq = IgnitorDsl.Constant(800.0)))
        val blocks = 188
        val a = rtRenderTree(tree = shared.optimize(), freqHz = 220.0, blocks = blocks)
        val b = rtRenderTree(tree = twins.optimize(), freqHz = 220.0, blocks = blocks)
        val a2 = rtRenderTree(tree = shared.optimize(), freqHz = 220.0, blocks = blocks)
        val diff = DoubleArray(a.size) { a[it] - b[it] }
        val diffSelf = DoubleArray(a.size) { a[it] - a2[it] }

        line("== L5-02: let p = pluck(); p + p.lowpass(800)  against  pluck() + pluck().lowpass(800), 220 Hz, 0.5 s, built optimized")
        line("   structurally equal: ${shared == twins}; the same instance: ${shared === twins}")
        line("   uniqueId: ${shared.uniqueId()} and ${twins.uniqueId()} (equal: ${shared.uniqueId() == twins.uniqueId()})")
        line(
            "   rms shared ${rtFx(rtRms(a), 4)}, rms twins ${rtFx(rtRms(b), 4)}, rms of the difference ${rtFx(rtRms(diff), 4)} " +
                "(${rtFx(20.0 * log10(rtRms(diff) / rtRms(a)), 1)} dB re the shared one); control, the shared tree twice: ${rtFx(rtRms(diffSelf), 6)}",
        )

        flush()
    }

    "L2-07: a stopped playback's long note keeps its engine, and a release of 1e6 s".config(enabled = requested) {
        val rig = RtRig()
        rig.send(KlangCommLink.Cmd.ScheduleVoices(playbackId = RT_PID, voices = listOf(rtVoice(note = "long", atSec = 0.0, gateSec = 60.0, sound = "sine"))))
        rig.renderUntil(1.0)
        rig.send(KlangCommLink.Cmd.Cleanup(playbackId = RT_PID))

        var peakAt30 = 0.0

        while (rig.engine() != null && rig.nowSec < 120.0) {
            rig.block()

            if (rig.nowSec > 30.0 && rig.nowSec < 30.1) {
                peakAt30 = max(peakAt30, rtPeak(rig.out.left))
            }
        }

        line("== L2-07: one sine voice, gate 60 s, Cmd.Cleanup at 1.0 s, live dispatcher")
        line("   output peak at 30 s (after the stop): ${rtFx(peakAt30, 4)}; the engine was disposed at ${rtFx(rig.nowSec, 3)} s")

        val registry = IgnitorRegistry().apply { registerDefaults() }
        val factory = VoiceFactory(sampleRate = RT_SR, blockFrames = RT_BLOCK, scratchBuffers = ScratchBuffers(RT_BLOCK))
        val sv = rtVoice(note = "x", atSec = 0.0, gateSec = 1.0, sound = "sine", ignitorParams = klangMapOf("adsr.release" to 1e6))
        val voice = factory.makeVoice(
            scheduled = sv,
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(playbackId = RT_PID, ignitorRegistry = registry.fork(), phasePools = PhasePools(Random(1))),
            getSample = { null },
        )!!

        line("   a sine with adsr.release 1e6 and a 1 s gate: the voice ends ${rtFx(voice.endFrame / RT_SR, 0)} s after its onset (${rtFx(voice.endFrame / RT_SR / 86400.0, 1)} days)")

        flush()
    }

    "L5-07: inside a 16x region a shared node repeats one block once the region's frame counter saturates".config(enabled = requested) {
        val noise = IgnitorDsl.WhiteNoise()
        val tree = noise.oversampleBy(16.ign) { y -> IgnitorDsl.Plus(left = y, right = IgnitorDsl.Lowpass(inner = y, freq = IgnitorDsl.Constant(3000.0))) }
        val saturation = Int.MAX_VALUE / 16

        fun repeats(startElapsed: Int, optimized: Boolean): String {
            val t = if (optimized) tree.optimize() else tree
            val out = rtRenderTree(tree = t, freqHz = 220.0, blocks = 12, startElapsed = startElapsed)
            val diffs = (1 until 12).map { rtFx(rtBlockRepeat(out, it), 4) }

            return "from voice frame $startElapsed (saturates at $saturation): largest change from the block before, per block: $diffs"
        }

        line("== L5-07: whitenoise.oversampleBy(16) { y -> y + y.lowpass(3000) } (the region input read twice), 48 kHz")
        line("   minutes to saturation at 16x: ${rtFx(saturation / RT_SR.toDouble() / 60.0, 1)}, at 8x ${rtFx(Int.MAX_VALUE / 8 / RT_SR.toDouble() / 60.0, 1)}, at 2x ${rtFx(Int.MAX_VALUE / 2 / RT_SR.toDouble() / 3600.0, 2)} h")
        line("   control:   ${repeats(startElapsed = 0, optimized = true)}")
        line("   saturated: ${repeats(startElapsed = saturation - 4 * RT_BLOCK, optimized = true)}")
        line("   saturated, not optimized: ${repeats(startElapsed = saturation - 4 * RT_BLOCK, optimized = false)}")

        // Diagnostics: the memo's own behaviour at a clock that does not move (what saturation produces)
        fun stuck(t: IgnitorDsl, label: String) {
            val built = t.buildExciter(ignitorParams = null, random = Random(7), freqHz = 220.0, sampleRate = RT_SR, blockFrames = RT_BLOCK)
            val ctx = IgniteContext(
                sampleRate = RT_SR,
                voiceDurationFrames = Int.MAX_VALUE,
                gateEndFrame = Int.MAX_VALUE,
                scratchBuffers = ScratchBuffers(RT_BLOCK),
                random = Random(7),
            )
            val buffer = AudioBuffer(RT_BLOCK)
            val out = DoubleArray(12 * RT_BLOCK)

            for (b in 0 until 12) {
                buffer.fill(0.0)
                ctx.voiceElapsedFrames = 1000
                ctx.updateOffsetAndLength(offset = 0, length = RT_BLOCK)
                built.ignitor.generate(buffer, 220.0, ctx)
                buffer.copyInto(destination = out, destinationOffset = b * RT_BLOCK, startIndex = 0, endIndex = RT_BLOCK)
            }

            line("   clock held at frame 1000, $label: ${(1 until 12).map { rtFx(rtBlockRepeat(out, it), 4) }}")
        }

        val n2 = IgnitorDsl.WhiteNoise()
        stuck(IgnitorDsl.Plus(left = n2, right = IgnitorDsl.Lowpass(inner = n2, freq = IgnitorDsl.Constant(3000.0))), "no region, a shared noise read twice")
        stuck(tree, "the 16x region, its input read twice")

        val n3 = IgnitorDsl.WhiteNoise()
        val shared = n3.oversampleBy(16.ign) { y ->
            val z = IgnitorDsl.Lowpass(inner = y, freq = IgnitorDsl.Constant(3000.0))

            IgnitorDsl.Plus(left = z, right = IgnitorDsl.Highpass(inner = z, freq = IgnitorDsl.Constant(100.0)))
        }
        stuck(shared, "the 16x region, a lowpass in the body read twice")

        val out3 = rtRenderTree(tree = shared, freqHz = 220.0, blocks = 12, startElapsed = saturation - 4 * RT_BLOCK)
        line("   the 16x region with a lowpass read twice, through saturation: ${(1 until 12).map { rtFx(rtBlockRepeat(out3, it), 4) }}")

        flush()
    }
})
