/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine.audit

import io.kotest.core.spec.style.StringSpec
import io.peekandpoke.klang.audio_be.SampleStore
import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_bridge.KlangPatternEvent
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_engine.KlangDiagnostics
import io.peekandpoke.klang.audio_engine.KlangPatternScheduler
import io.peekandpoke.klang.audio_engine.KlangPlaybackContext
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.audio_engine.PlaybackClock
import io.peekandpoke.klang.audio_engine.SamplePreloader
import io.peekandpoke.klang.audio_fe.samples.NullAudioDecoder
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.audio_fe.utils.AssetLoader
import io.peekandpoke.klang.common.SourceLocationChain
import io.peekandpoke.klang.common.infra.KlangLock
import io.peekandpoke.klang.common.infra.withLock
import io.peekandpoke.ultra.streams.StreamSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.floor

// The disproof pass of the engine audit (KLANG-00420), cluster "realtime and lifecycle", the frontend half: the
// pattern scheduler and the sample preloader of the live path, driven with a counting sendControl. A measurement, not
// a gate: enabled only with KLANG_PROBE=audit; it prints what it saw and writes it to
// `<klang build dir>/probes/audit-realtime-frontend.txt`.

/** One event of the test patterns: a synth voice at [startCycles]. */
private class RtfEvent(
    override val startCycles: Double,
    private val note: String,
    private val soundName: String,
) : KlangPatternEvent {
    override val durationCycles: Double = 0.01
    override val sourceLocations: SourceLocationChain? = null

    override fun toVoiceData(): VoiceData = VoiceData.empty.copy(note = note, sound = soundName, freqHz = 220.0)
}

/** Every whole cycle in [from, to) gets [perCycle] events of [sound], notes n0 to n(perCycle - 1), spread over the cycle. */
private fun rtfEvents(from: Double, to: Double, perCycle: Int, sound: String): List<KlangPatternEvent> {
    val out = ArrayList<KlangPatternEvent>()
    var cycle = ceil(from)

    while (cycle < to) {
        for (i in 0 until perCycle) {
            out.add(RtfEvent(startCycles = cycle + i / (perCycle * 2.0), note = "n$i", soundName = sound))
        }

        cycle += 1.0
    }

    return out
}

/** The frontend's commands, in the order they were sent, from any thread. */
private class RtfSent {
    private val lock = KlangLock()
    private val cmds = ArrayList<KlangCommLink.Cmd>()

    fun add(cmd: KlangCommLink.Cmd) {
        lock.withLock { cmds.add(cmd) }
    }

    fun snapshot(): List<KlangCommLink.Cmd> = lock.withLock { ArrayList(cmds) }
}

private class RtfRig(pattern: KlangPattern, playbackId: String) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val preloaderSent = RtfSent()
    val schedulerSent = RtfSent()
    val samples = Samples(
        index = Samples.Index(banks = emptyList()),
        loader = object : AssetLoader {
            override suspend fun download(uri: String): ByteArray? = null
        },
        decoder = NullAudioDecoder(),
    )
    val preloader = SamplePreloader(
        samples = samples,
        sendControl = { cmd -> preloaderSent.add(cmd) },
        scope = scope,
        dispatcher = Dispatchers.Default,
    )
    val context = KlangPlaybackContext(
        playerOptions = KlangPlayer.Options(samples = samples),
        samplePreloader = preloader,
        sendControl = { cmd -> schedulerSent.add(cmd) },
        scope = scope,
        // The JVM player's dispatchers (klang/src/jvmMain/kotlin/index_jvm.kt)
        fetcherDispatcher = Dispatchers.Default,
        callbackDispatcher = Dispatchers.Default,
        backendReady = CompletableDeferred(Unit),
        clock = PlaybackClock(localMs = { 0.0 }),
        diagnostics = KlangDiagnostics(
            stream = StreamSource<KlangCommLink.Feedback.Diagnostics?>(null),
            ticks = StreamSource(Unit),
            capacity = 8,
            nowMs = { 0.0 },
        ),
    )
    val scheduler = KlangPatternScheduler(
        playbackId = playbackId,
        pattern = pattern,
        context = context,
        signals = StreamSource<KlangPlaybackSignal>(KlangPlaybackSignal.Idle),
        registrar = context.registrarFor(playbackId),
    )
}

/** Waits up to [timeoutMs] for [condition], polling every 5 ms; true when it held. */
private fun rtfAwait(timeoutMs: Int, condition: () -> Boolean): Boolean {
    val deadline = System.nanoTime().toDouble() + timeoutMs * 1e6

    while (System.nanoTime().toDouble() < deadline) {
        if (condition()) {
            return true
        }

        Thread.sleep(5)
    }

    return condition()
}

/** Where the frontend audit writes: `<klang build dir>/probes/`, found from where this class was compiled to. */
private fun rtfOutputFile(name: String): File {
    val classes = File(RtfRig::class.java.protectionDomain.codeSource.location.toURI())
    val buildDir = classes.parentFile?.parentFile?.parentFile?.parentFile ?: File(System.getProperty("java.io.tmpdir"))

    return File(buildDir, "probes/$name")
}

private fun rtfName(cmd: KlangCommLink.Cmd): String = when (cmd) {
    is KlangCommLink.Cmd.ScheduleVoices -> "ScheduleVoices(${cmd.voices.size})"
    is KlangCommLink.Cmd.Cleanup -> "Cleanup"
    is KlangCommLink.Cmd.SetGain -> "SetGain"
    is KlangCommLink.Cmd.ReplaceVoices -> "ReplaceVoices(${cmd.voices.size})"
    is KlangCommLink.Cmd.Sample -> "Sample"
    is KlangCommLink.Cmd.RegisterIgnitor -> "RegisterIgnitor"
    is KlangCommLink.Cmd.RegisterKatalyst -> "RegisterKatalyst"
    is KlangCommLink.Cmd.StartRealtimeVoice -> "StartRealtimeVoice"
    is KlangCommLink.Cmd.StopRealtimeVoice -> "StopRealtimeVoice"
}

class AuditRealtimeFrontendRepro : StringSpec({

    val requested = System.getenv("KLANG_PROBE") == "audit"
    val report = StringBuilder()

    fun line(text: String) {
        println(text)
        report.appendLine(text)
    }

    fun flush() {
        val target = rtfOutputFile("audit-realtime-frontend.txt")
        target.parentFile?.mkdirs()
        target.writeText(report.toString())
        println("audit realtime frontend written to ${target.absolutePath}")
    }

    "L9-05: every synth voice is preloaded as a sample, sent as NotFound, and kept by the backend's store".config(enabled = requested) {
        var sound = "supersaw"
        val pattern = object : KlangPattern {
            override fun queryEvents(fromCycles: Double, toCycles: Double, cps: Double): List<KlangPatternEvent> =
                rtfEvents(from = fromCycles, to = toCycles, perCycle = 100, sound = sound)
        }
        val rig = RtfRig(pattern = pattern, playbackId = "synth")
        val store = SampleStore(commLink = KlangCommLink(capacity = 4096).backend)

        fun notFound(): List<KlangCommLink.Cmd.Sample.NotFound> =
            rig.preloaderSent.snapshot().filterIsInstance<KlangCommLink.Cmd.Sample.NotFound>()

        try {
            rig.scheduler.start()

            val first = rtfAwait(timeoutMs = 5000) { notFound().size >= 100 }

            Thread.sleep(300)

            val afterStart = notFound()

            // An edit that changes only the instrument's name, as a new inline Ignitor's synthetic name does
            sound = "ignitor-edit-1"
            rig.scheduler.updatePattern(pattern)
            rtfAwait(timeoutMs = 5000) { notFound().size >= afterStart.size + 100 }
            Thread.sleep(300)

            val afterEdit = notFound()

            for (cmd in afterEdit) {
                store.addSample(cmd)
            }

            val inStore = afterEdit.count { store.contains(it.req) }
            val preloaderKept = afterEdit.count { rig.preloader.isLoaded(it.req) }

            line("== L9-05: a pattern of 100 distinct synth notes per cycle (sound supersaw), KlangPatternScheduler.start on the JVM dispatchers")
            line("   NotFound commands after the start's preload: ${afterStart.size} (reached 100: $first); distinct requests ${afterStart.map { it.req }.toSet().size}")
            line("   after one edit that renames the instrument (updatePattern): ${afterEdit.size} in all")
            line("   fed into a backend SampleStore: $inStore entries kept; the preloader's sent set holds $preloaderKept of them")
            line("   the requests carry the note: ${afterStart.take(3).map { it.req }}")
        } finally {
            rig.scheduler.stop()
            rig.scope.cancel()
        }

        flush()
    }

    "L9-02: on the JVM dispatchers a pattern query that is running at stop() sends its cycle after the Cleanup".config(enabled = requested) {
        val calls = AtomicInteger(0)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pattern = object : KlangPattern {
            override fun queryEvents(fromCycles: Double, toCycles: Double, cps: Double): List<KlangPatternEvent> {
                val n = calls.incrementAndGet()

                // The third query is the first one the fetch loop's tick makes (after the preload and the first cycle):
                // it stands for a slow query that is running when the user presses stop
                if (n == 3) {
                    entered.countDown()
                    release.await(5, TimeUnit.SECONDS)
                }

                return rtfEvents(from = floor(fromCycles), to = toCycles, perCycle = 1, sound = "sine")
                    .filter { it.startCycles >= fromCycles }
            }
        }
        val rig = RtfRig(pattern = pattern, playbackId = "race")

        try {
            rig.scheduler.start()

            val blocked = entered.await(5, TimeUnit.SECONDS)
            val beforeStop = rig.schedulerSent.snapshot().size

            rig.scheduler.stop()

            val cleanupSent = rtfAwait(timeoutMs = 3000) {
                rig.schedulerSent.snapshot().any { it is KlangCommLink.Cmd.Cleanup }
            }

            release.countDown()

            val scheduleAfter = rtfAwait(timeoutMs = 3000) {
                val sent = rig.schedulerSent.snapshot()
                val cleanupAt = sent.indexOfFirst { it is KlangCommLink.Cmd.Cleanup }

                cleanupAt >= 0 && sent.drop(cleanupAt + 1).any { it is KlangCommLink.Cmd.ScheduleVoices }
            }

            Thread.sleep(200)

            val sent = rig.schedulerSent.snapshot()

            line("== L9-02: the frontend half: stop() while the fetch loop's tick is inside a pattern query (a latch holds the third query)")
            line("   the tick was blocked in the query: $blocked; commands before stop(): ${sent.take(beforeStop).map { rtfName(it) }}")
            line("   after stop(): ${sent.drop(beforeStop).map { rtfName(it) }}; the Cleanup went out while the query blocked: $cleanupSent")
            line("   a ScheduleVoices followed the Cleanup: $scheduleAfter")
        } finally {
            release.countDown()
            rig.scheduler.stop()
            rig.scope.cancel()
        }

        flush()
    }
})
