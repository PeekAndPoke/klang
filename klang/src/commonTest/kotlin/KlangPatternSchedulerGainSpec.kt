/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_bridge.KlangPatternEvent
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_fe.samples.NullAudioDecoder
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.audio_fe.utils.AssetLoader
import io.peekandpoke.klang.common.infra.KlangLock
import io.peekandpoke.klang.common.infra.withLock
import io.peekandpoke.ultra.streams.StreamSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel

/**
 * The frontend's playback is the one gate of `Cmd.SetGain` (KLANG-00406, step 5): it keeps every gain, sends it only
 * while started, and sends the gain it keeps first at a start. A gain sent after the stop's cleanup would make the
 * backend create an engine that nothing ever stops.
 */
class KlangPatternSchedulerGainSpec : StringSpec({

    "a stopped playback sends no gain and keeps it for its next start, where it goes first" {
        val sent = mutableListOf<KlangCommLink.Cmd>()
        val lock = KlangLock()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun gains(): List<KlangCommLink.Cmd.SetGain> = lock.withLock { sent.filterIsInstance<KlangCommLink.Cmd.SetGain>() }

        val playerOptions = KlangPlayer.Options(
            samples = Samples(
                index = Samples.Index(banks = emptyList()),
                loader = object : AssetLoader {
                    override suspend fun download(uri: String): ByteArray? = null
                },
                decoder = NullAudioDecoder(),
            ),
        )

        val context = KlangPlaybackContext(
            playerOptions = playerOptions,
            samplePreloader = SamplePreloader(
                samples = playerOptions.samples,
                sendControl = {},
                scope = scope,
                dispatcher = Dispatchers.Default,
            ),
            sendControl = { cmd -> lock.withLock { sent += cmd } },
            scope = scope,
            fetcherDispatcher = Dispatchers.Default,
            callbackDispatcher = Dispatchers.Unconfined,
            // Never ready: the fetch loop waits, so all this spec sees is what start, stop and setGain send
            backendReady = CompletableDeferred(),
            clock = PlaybackClock(localMs = { 0.0 }),
            diagnostics = KlangDiagnostics(
                stream = StreamSource<KlangCommLink.Feedback.Diagnostics?>(null),
                ticks = StreamSource(Unit),
                capacity = 8,
                nowMs = { 0.0 },
            ),
        )

        val scheduler = KlangPatternScheduler(
            playbackId = "gain",
            pattern = silence,
            context = context,
            signals = StreamSource<KlangPlaybackSignal>(KlangPlaybackSignal.Idle),
            registrar = context.registrarFor("gain"),
            pause = { awaitCancellation() },
        )

        try {
            scheduler.setGain(gain = 0.0, rampSec = 2.0)

            withClue("nothing sent before the start") { gains() shouldBe emptyList() }

            scheduler.start()

            withClue("the gain kept goes first, at once") {
                lock.withLock { sent.first() } shouldBe KlangCommLink.Cmd.SetGain(playbackId = "gain", gain = 0.0, rampSec = 0.0)
            }

            scheduler.setGain(gain = 0.5, rampSec = 2.0)

            gains().last() shouldBe KlangCommLink.Cmd.SetGain(playbackId = "gain", gain = 0.5, rampSec = 2.0)

            scheduler.stop()
            val sentBeforeStop = gains().size

            scheduler.setGain(gain = 1.0, rampSec = 2.0)

            withClue("nothing sent after the stop") { gains().size shouldBe sentBeforeStop }

            scheduler.start()

            gains().last() shouldBe KlangCommLink.Cmd.SetGain(playbackId = "gain", gain = 1.0, rampSec = 0.0)
        } finally {
            scheduler.stop()
            scope.cancel()
        }
    }
}) {
    private companion object {
        /** A pattern with no events: this spec looks at the gain only. */
        val silence = object : KlangPattern {
            override fun queryEvents(fromCycles: Double, toCycles: Double, cps: Double): List<KlangPatternEvent> = emptyList()
        }
    }
}
