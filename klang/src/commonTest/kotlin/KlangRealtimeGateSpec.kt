/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.KlangPatternEvent
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_fe.samples.NullAudioDecoder
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.audio_fe.utils.AssetLoader
import io.peekandpoke.klang.common.SourceLocationChain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel

/**
 * The realtime path takes an event's gate from the event, as the cyclic path does (KLANG-00290 (engine follow ups) item
 * 24, D6: a pattern language's `legato` is folded into the gate on the frontend, it does not travel on the wire). A
 * fixed gate is scaled by the event's own [KlangPatternEvent.gateSeconds]; a held voice stays held, released by its stop.
 */
class KlangRealtimeGateSpec : StringSpec({

    /** An event that asks for half its duration as its gate, the way sprudel's `legato(0.5)` does. */
    val halfGate = object : KlangPatternEvent {
        override val startCycles: Double = 0.0
        override val durationCycles: Double = 1.0
        override val sourceLocations: SourceLocationChain? = null

        override fun toVoiceData(): VoiceData = VoiceData.empty.copy(note = "c3")

        override fun gateSeconds(durationSec: Double): Double = durationSec * 0.5
    }

    /** The gates of the realtime voices a player sent, in order (nobody drains its link here: the backend never runs). */
    fun startedGates(player: KlangPlayer): List<Double?> = buildList {
        while (true) {
            val cmd = player.commLink.backend.control.receive() ?: break

            if (cmd is KlangCommLink.Cmd.StartRealtimeVoice) {
                add(cmd.voice.gateDurSec)
            }
        }
    }

    suspend fun withPlayer(test: suspend (KlangPlayer) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val player = KlangPlayer(
            options = KlangPlayer.Options(
                samples = Samples(
                    index = Samples.Index(banks = emptyList()),
                    loader = object : AssetLoader {
                        override suspend fun download(uri: String): ByteArray? = null
                    },
                    decoder = NullAudioDecoder(),
                ),
            ),
            backendFactory = { awaitCancellation() },
            scope = scope,
            fetcherDispatcher = Dispatchers.Default,
            backendDispatcher = Dispatchers.Default,
            callbackDispatcher = Dispatchers.Default,
        )

        try {
            test(player)
        } finally {
            player.shutdown()
            scope.cancel()
        }
    }

    "a fixed gate is the event's own, a held voice stays held" {
        withPlayer { player ->
            val keys = player.createRealtimePlayback("keys")

            keys.startEvents(liveId = 1, events = listOf(halfGate), gateDurSec = 0.8)
            keys.startEvents(liveId = 2, events = listOf(halfGate), gateDurSec = null)

            startedGates(player) shouldBe listOf(0.4, null)
        }
    }
})
