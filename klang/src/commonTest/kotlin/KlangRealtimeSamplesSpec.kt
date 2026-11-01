/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_fe.samples.NullAudioDecoder
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.audio_fe.utils.AssetLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.Volatile

/**
 * The realtime path loads samples (KLANG-00402, D2: the MIDI pads play drum hits): [KlangRealtimeVoicePlayback.preload]
 * for a kit known ahead, and a sample the backend asks for when a voice started without it.
 */
class KlangRealtimeSamplesSpec : StringSpec({

    /** The sounds asked for, replaced whole on each one (the loads run on other threads). */
    class Asked {
        @Volatile
        var names: List<String> = emptyList()
    }

    /** A player with a bank of two sounds, bd and sd, each noting when it is asked for and providing nothing. */
    suspend fun withPlayer(test: suspend (KlangPlayer, Asked) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val asked = Asked()
        val sounds = listOf("bd", "sd").map { name ->
            object : Samples.SoundProvider {
                override val key: String = name
                override val variantCount: Int = 1
                override val sampleType: Samples.SampleType = Samples.SampleType.SINGLE

                override suspend fun provide(request: SampleRequest): Samples.ResolvedSample? {
                    asked.names = asked.names + name

                    return null
                }
            }
        }

        val player = KlangPlayer(
            options = KlangPlayer.Options(
                samples = Samples(
                    index = Samples.Index(banks = listOf(Samples.Bank(key = "", sounds = sounds))),
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
            test(player, asked)
        } finally {
            player.shutdown()
            scope.cancel()
        }
    }

    suspend fun until(what: () -> Boolean) {
        withTimeout(5_000) {
            while (!what()) {
                delay(10)
            }
        }
    }

    "a kit known ahead is loaded with preload" {
        withPlayer { player, asked ->
            val pads = player.createRealtimePlayback("pads")

            pads.preload(setOf(SampleRequest(bank = null, sound = "bd", index = null, note = null)))

            until { "bd" in asked.names }
            asked.names shouldContain "bd"
        }
    }

    "a sample the backend asks for (a voice started without it) is loaded" {
        withPlayer { player, asked ->
            val pads = player.createRealtimePlayback("pads")

            pads.handleFeedback(
                KlangCommLink.Feedback.RequestSample(
                    playbackId = pads.playbackId,
                    req = SampleRequest(bank = null, sound = "sd", index = null, note = null),
                )
            )

            until { "sd" in asked.names }
            asked.names shouldContain "sd"
        }
    }
})
