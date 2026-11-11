/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBackend
import io.peekandpoke.klang.audio_be.OfflineBackend
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_bridge.KlangPatternEvent
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.SoundValue
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.uniqueId
import io.peekandpoke.klang.audio_fe.samples.NullAudioDecoder
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.audio_fe.utils.AssetLoader
import io.peekandpoke.klang.common.SourceLocationChain
import io.peekandpoke.klang.common.infra.KlangLock
import io.peekandpoke.klang.common.infra.withLock
import io.peekandpoke.ultra.streams.StreamSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs
import kotlin.math.ceil

/**
 * A playback's life on the wire, from the frontend's side (KLANG-00431): what a stop leaves behind it and what a start
 * after a stop sends. The backend cannot tell a straggler from a restart (audit L9-02), so the frontend sends nothing
 * after its `Cleanup` until it starts again, and a start sends everything a fresh engine needs: its gain right before
 * the first voices (L9-04) and every inline instrument again (L1-03). It asks for samples only for the voices the
 * engine plays as samples (L9-05), and an edit asks for its new samples at once (N-R4). On the JVM's dispatchers, where
 * the stop and the fetch loop run on two threads.
 */
class KlangPatternSchedulerLifecycleSpec : StringSpec({

    "a pattern query that runs while stop() is called sends nothing after the Cleanup (audit L9-02)" {
        val calls = AtomicInteger(0)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pattern = object : KlangPattern {
            override fun queryEvents(fromCycles: Double, toCycles: Double, cps: Double): List<KlangPatternEvent> {
                // The third query is the fetch loop's first (after the preload and the first cycle): a slow query
                // running when the stop comes
                if (calls.incrementAndGet() == 3) {
                    entered.countDown()
                    release.await(5, TimeUnit.SECONDS)
                }

                return everyCycle(from = fromCycles, to = toCycles, sound = "sine")
            }
        }

        withRig(pattern = pattern) { rig ->
            rig.scheduler.start()

            withClue("the fetch loop is inside the query") { entered.await(5, TimeUnit.SECONDS) shouldBe true }

            rig.scheduler.stop()
            release.countDown()

            withClue("the query has come back") { rig.awaitCondition { calls.get() >= 4 || rig.loopEnded() } }
            Thread.sleep(100)

            rig.sentAfterCleanup() shouldBe emptyList()
        }
    }

    "stop() has sent its Cleanup when it returns: a start right after it cannot send anything before it (audit L9-02, L9-04)" {
        // A fetcher dispatcher that runs its work 50 ms late: a Cleanup launched on it, not sent by the stop itself,
        // would come after a start made in between
        withRig(pattern = constant(sound = "sine"), slowFetcher = true) { rig ->
            rig.scheduler.start()
            rig.awaitCondition { rig.rounds() >= 1 }

            rig.scheduler.stop()

            rig.sent().last() shouldBe KlangCommLink.Cmd.Cleanup(PID)
        }
    }

    "a start stopped and started again before it launched its loop leaves one loop, and the next stop ends it" {
        withRig(pattern = constant(sound = "sine")) { rig ->
            // Between the start's flip and its loop: the stop and the second start come in, here through the start's
            // own callback, as another thread could
            rig.onStarted = {
                rig.onStarted = {}
                rig.scheduler.stop()
                rig.scheduler.start()
            }

            rig.scheduler.start()
            rig.awaitCondition { rig.rounds() >= 1 }

            rig.scheduler.stop()

            withClue("every loop that ran was ended by the stop: ${rig.rounds()} ran, ${rig.ended()} ended") {
                rig.awaitCondition { rig.ended() == rig.rounds() }
            }
            rig.rounds() shouldBe 1
        }
    }

    "a stop while the start's samples load ends the start: nothing is sent after it, the start is not announced (audit N-R3)" {
        val asked = CountDownLatch(1)
        // A sample whose load never ends: the start waits for it, as for a slow first load
        val hang: suspend () -> Unit = {
            asked.countDown()
            awaitCancellation()
        }
        val samples = samplesOf("bd" to hang)

        withRig(pattern = constant(sound = "bd"), samples = samples) { rig ->
            rig.scheduler.start()

            withClue("the preload asks for the sample") { asked.await(5, TimeUnit.SECONDS) shouldBe true }
            Thread.sleep(50)

            rig.scheduler.stop()
            Thread.sleep(300)

            rig.sentAfterCleanup() shouldBe emptyList()
            rig.sent().filterIsInstance<KlangCommLink.Cmd.ScheduleVoices>() shouldBe emptyList()
            rig.signals() shouldNotContain KlangPlaybackSignal.PlaybackStarted
        }
    }

    "a start after a stop plays at the gain kept, also when its engine was disposed during the preload (audit L9-04)" {
        val release = CompletableDeferred<Unit>()
        val asked = CountDownLatch(1)
        // The restart's pattern brings a sample that loads slowly: the stopped engine goes idle while it loads
        val slow: suspend () -> Unit = {
            asked.countDown()
            release.await()
        }
        var withSample = false
        val pattern = object : KlangPattern {
            override fun queryEvents(fromCycles: Double, toCycles: Double, cps: Double): List<KlangPatternEvent> {
                val notes = everyCycle(from = fromCycles, to = toCycles, sound = "sine")

                return if (withSample) notes + everyCycle(from = fromCycles, to = toCycles, sound = "slow") else notes
            }
        }

        withRig(pattern = pattern, samples = samplesOf("slow" to slow), backend = true) { rig ->
            val backend = rig.backend!!

            rig.scheduler.setGain(gain = 0.3, rampSec = 0.0)
            rig.scheduler.start()
            rig.awaitCondition { rig.rounds() >= 1 }
            val first = rig.peakOver(sec = 0.3)

            // Stopped while its note sounds: the restart's own SetGain reaches the stopped engine
            rig.scheduler.stop()
            backend.renderUntil(backend.nowSec + 0.1)
            withSample = true
            rig.scheduler.start()
            withClue("the restart waits for its sample") { asked.await(5, TimeUnit.SECONDS) shouldBe true }

            // The old note ends, the stopped engine goes idle and is disposed, the sample still loading
            backend.renderUntil(backend.nowSec + 3.0)
            release.complete(Unit)
            rig.awaitCondition { rig.rounds() >= 2 }
            val restart = rig.peakOver(sec = 0.3)

            withClue("the first start at 0.3 peaks at $first, the restart at $restart") {
                restart shouldBe (first plusOrMinus 1e-3)
            }
        }
    }

    "a start after a stop sends its Cleanup first and announces its inline instrument again (audit L1-03, L9-02)" {
        val inline = IgnitorDsl.Sine(freq = IgnitorDsl.Freq)
        val name = inline.uniqueId()

        withRig(pattern = constant(sound = name, inline = inline)) { rig ->
            rig.scheduler.start()
            rig.awaitCondition { rig.rounds() >= 1 }
            rig.scheduler.stop()
            rig.scheduler.start()
            rig.awaitCondition { rig.rounds() >= 2 }

            val sent = rig.sent()
            val registers = sent.indices.filter { sent[it] is KlangCommLink.Cmd.RegisterIgnitor }
            val cleanupAt = sent.indexOfFirst { it is KlangCommLink.Cmd.Cleanup }

            withClue("the stop's Cleanup goes out before the restart sends anything: its gain is first") {
                sent[cleanupAt + 1] shouldBe KlangCommLink.Cmd.SetGain(playbackId = PID, gain = 1.0, rampSec = 0.0)
            }

            registers.size shouldBe 2
            withClue("one for each start, the second after the stop") { (registers[1] > cleanupAt) shouldBe true }
            (sent[registers[1]] as KlangCommLink.Cmd.RegisterIgnitor).name shouldBe name
        }
    }

    "an edit after the stop announces no inline instrument: a RegisterIgnitor after the Cleanup revives the engine (audit L9-02)" {
        val inline = IgnitorDsl.Saw(freq = IgnitorDsl.Freq)

        withRig(pattern = constant(sound = "sine")) { rig ->
            rig.scheduler.start()
            rig.awaitCondition { rig.rounds() >= 1 }
            rig.scheduler.stop()

            rig.scheduler.updatePattern(constant(sound = inline.uniqueId(), inline = inline))

            rig.sentAfterCleanup().filterIsInstance<KlangCommLink.Cmd.RegisterIgnitor>() shouldBe emptyList()
        }
    }

    "only the voices the engine plays as samples are asked for: no built-in, no inline instrument, no unset sound (audit L9-05)" {
        val inline = IgnitorDsl.Sine(freq = IgnitorDsl.Freq)
        val notes = listOf(
            Note(startCycles = 0.0, soundName = "supersaw"),
            Note(startCycles = 0.1, soundName = "SuperSaw"),
            Note(startCycles = 0.2, soundName = null),
            Note(startCycles = 0.3, soundName = inline.uniqueId(), sound = SoundValue.Dsl(inline)),
            Note(startCycles = 0.4, soundName = "bd"),
        )
        val pattern = object : KlangPattern {
            override fun queryEvents(fromCycles: Double, toCycles: Double, cps: Double): List<KlangPatternEvent> =
                notes.filter { it.startCycles >= fromCycles && it.startCycles < toCycles }
        }

        withRig(pattern = pattern) { rig ->
            rig.scheduler.start()
            rig.awaitCondition { rig.rounds() >= 1 }
            Thread.sleep(100)

            rig.sampleSounds() shouldBe setOf("bd")
        }
    }

    "an edit asks for its new samples at once, after the look-ahead has run (audit N-R4)" {
        var sound = "bd"
        val pattern = object : KlangPattern {
            override fun queryEvents(fromCycles: Double, toCycles: Double, cps: Double): List<KlangPatternEvent> =
                everyCycle(from = fromCycles, to = toCycles, sound = sound)
        }

        withRig(pattern = pattern) { rig ->
            rig.scheduler.start()
            // The fetch loop's first round has run its sample look-ahead, eight cycles ahead
            rig.awaitCondition { rig.rounds() >= 1 }
            Thread.sleep(100)
            rig.sampleSounds() shouldNotContain "hh"

            sound = "hh"
            rig.scheduler.updatePattern(pattern)

            // The loop is parked: only the edit itself can have asked
            rig.awaitCondition { "hh" in rig.sampleSounds() }
            rig.sampleSounds() shouldContain "hh"
        }
    }
}) {
    private companion object {
        const val PID = "life"

        /** One note of [sound] on every whole cycle in [from], [to). */
        fun everyCycle(from: Double, to: Double, sound: String): List<KlangPatternEvent> {
            val out = ArrayList<KlangPatternEvent>()
            var cycle = ceil(from)

            while (cycle < to) {
                out.add(Note(startCycles = cycle, soundName = sound))
                cycle += 1.0
            }

            return out
        }

        /** A note of [sound] on every cycle; [inline] when the sound is an inline instrument of that name. */
        fun constant(sound: String, inline: IgnitorDsl? = null) = object : KlangPattern {
            override fun queryEvents(fromCycles: Double, toCycles: Double, cps: Double): List<KlangPatternEvent> =
                everyCycle(from = fromCycles, to = toCycles, sound = sound).map {
                    Note(startCycles = it.startCycles, soundName = sound, sound = inline?.let { dsl -> SoundValue.Dsl(dsl) })
                }
        }

        /** A sample index of one bank, "", whose sounds load by the given suspending functions. */
        fun samplesOf(vararg sounds: Pair<String, suspend () -> Unit>) = Samples(
            index = Samples.Index(
                banks = listOf(
                    Samples.Bank(
                        key = "",
                        sounds = sounds.map { (key, load) ->
                            object : Samples.SoundProvider {
                                override val key: String = key
                                override val variantCount: Int = 1
                                override val sampleType: Samples.SampleType = Samples.SampleType.SINGLE

                                override suspend fun provide(request: SampleRequest): Samples.ResolvedSample? {
                                    load()

                                    return null
                                }
                            }
                        },
                    ),
                ),
            ),
            loader = object : AssetLoader {
                override suspend fun download(uri: String): ByteArray? = null
            },
            decoder = NullAudioDecoder(),
        )

        suspend fun withRig(
            pattern: KlangPattern,
            samples: Samples = samplesOf(),
            backend: Boolean = false,
            slowFetcher: Boolean = false,
            test: suspend (Rig) -> Unit,
        ) {
            val rig = Rig(pattern = pattern, samples = samples, withBackend = backend, slowFetcher = slowFetcher)

            try {
                test(rig)
            } finally {
                rig.close()
            }
        }
    }

    /** Runs each piece of work 50 ms after it is dispatched, on a thread of its own. */
    private object SlowDispatcher : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            Thread {
                Thread.sleep(50)
                block.run()
            }.start()
        }
    }

    private class Note(
        override val startCycles: Double,
        private val soundName: String?,
        override val sound: SoundValue? = null,
    ) : KlangPatternEvent {
        override val durationCycles: Double = 0.25
        override val sourceLocations: SourceLocationChain? = null

        override fun toVoiceData(): VoiceData = VoiceData.empty.copy(note = "c4", sound = soundName, freqHz = 261.6)
    }

    /**
     * A scheduler on the JVM player's dispatchers, its commands and its preloader's recorded in the order sent. Its
     * fetch loop runs one round and then parks until the stop: every command a spec sees after that came from a start,
     * a stop or an edit.
     */
    private class Rig(pattern: KlangPattern, samples: Samples, withBackend: Boolean, slowFetcher: Boolean) {
        private val lock = KlangLock()
        private val link = KlangCommLink(capacity = 4096)

        /** A backend the commands also go to, rendered by the spec's own thread; null when the spec reads only the wire. */
        val backend: OfflineBackend? = if (withBackend) {
            OfflineBackend(config = AudioBackend.Config(commLink = link.backend, sampleRate = 48000, blockSize = 128))
        } else {
            null
        }
        private val commands = ArrayList<KlangCommLink.Cmd>()
        private val sampleCommands = ArrayList<KlangCommLink.Cmd>()
        private val seen = ArrayList<KlangPlaybackSignal>()
        private val rounds = AtomicInteger(0)
        private val parked = AtomicInteger(0)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val signalSource = StreamSource<KlangPlaybackSignal>(KlangPlaybackSignal.Idle)
        private val listening = signalSource.subscribeToStream { signal -> lock.withLock { seen.add(signal) } }

        private val context = KlangPlaybackContext(
            playerOptions = KlangPlayer.Options(samples = samples),
            samplePreloader = SamplePreloader(
                samples = samples,
                sendControl = { cmd ->
                    lock.withLock { sampleCommands.add(cmd) }
                    backend?.let { link.frontend.control.send(cmd) }
                },
                scope = scope,
                dispatcher = Dispatchers.Default,
            ),
            sendControl = { cmd ->
                lock.withLock { commands.add(cmd) }
                backend?.let { link.frontend.control.send(cmd) }
            },
            scope = scope,
            fetcherDispatcher = if (slowFetcher) SlowDispatcher else Dispatchers.Default,
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

        /** Called by the scheduler at each start, after its flip and before its loop (`onStarted`). */
        var onStarted: () -> Unit = {}

        val scheduler = KlangPatternScheduler(
            playbackId = PID,
            pattern = pattern,
            context = context,
            signals = signalSource,
            registrar = context.registrarFor(PID),
            onStarted = { onStarted() },
            pause = {
                rounds.incrementAndGet()

                try {
                    awaitCancellation()
                } finally {
                    parked.incrementAndGet()
                }
            },
        )

        fun sent(): List<KlangCommLink.Cmd> = lock.withLock { ArrayList(commands) }

        fun signals(): List<KlangPlaybackSignal> = lock.withLock { ArrayList(seen) }

        /** The fetch loop's rounds so far, over every start: each parks after its first. */
        fun rounds(): Int = rounds.get()

        /** The sounds the preloader sent a sample command for. */
        fun sampleSounds(): Set<String?> = lock.withLock { sampleCommands.toList() }
            .filterIsInstance<KlangCommLink.Cmd.Sample>()
            .map { it.req.sound }
            .toSet()

        /** Everything sent after the first `Cleanup`. */
        fun sentAfterCleanup(): List<KlangCommLink.Cmd> {
            val sent = sent()
            val at = sent.indexOfFirst { it is KlangCommLink.Cmd.Cleanup }

            return if (at < 0) emptyList() else sent.drop(at + 1)
        }

        /** Renders [sec] seconds on [backend] and returns the output's peak over them, both channels. */
        fun peakOver(sec: Double): Double {
            val be = backend!!
            var peak = 0.0

            be.renderUntil(be.nowSec + sec) { out ->
                for (i in 0 until out.left.size) {
                    peak = maxOf(peak, abs(out.left[i]), abs(out.right[i]))
                }
            }

            return peak
        }

        /** The fetch loops a stop ended while they were parked. */
        fun ended(): Int = parked.get()

        /** True once a parked fetch loop was cancelled. */
        fun loopEnded(): Boolean = parked.get() > 0

        /** Waits up to five seconds for [condition]. */
        fun awaitCondition(condition: () -> Boolean) {
            val deadline = System.nanoTime() + 5_000_000_000L

            while (!condition() && System.nanoTime() < deadline) {
                Thread.sleep(5)
            }

            withClue("the condition held within 5 s") { condition() shouldBe true }
        }

        fun close() {
            scheduler.stop()
            listening()
            scope.cancel()
        }
    }
}
