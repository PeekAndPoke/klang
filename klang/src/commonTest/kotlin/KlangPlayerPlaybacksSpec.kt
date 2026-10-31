/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_bridge.KlangPatternEvent
import io.peekandpoke.klang.audio_fe.samples.NullAudioDecoder
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.audio_fe.utils.AssetLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel

/** [KlangPlayer.playbacks] and [KlangPlayer.playing]: what the app's wake lock follows (KLANG-00396). */
class KlangPlayerPlaybacksSpec : StringSpec({

    val silentPattern = object : KlangPattern {
        override fun queryEvents(fromCycles: Double, toCycles: Double, cps: Double) = emptyList<KlangPatternEvent>()
    }

    /**
     * A player whose backend never comes: the playbacks are bookkeeping only. [KlangPlayer.playing] is subscribed to
     * from the start, as the app does: a derived stream follows only while someone listens.
     */
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

        val listening = player.playing.subscribeToStream { }

        try {
            test(player)
        } finally {
            listening()
            player.shutdown()
            scope.cancel()
        }
    }

    "a song is among the playbacks from its start to its stop, and the player plays meanwhile" {
        withPlayer { player ->
            val song = player.play(silentPattern)

            player.playbacks() shouldBe emptyList()
            player.playing() shouldBe false

            song.start()
            player.playbacks() shouldBe listOf(song)
            player.playing() shouldBe true

            song.stop()
            player.playbacks() shouldBe emptyList()
            player.playing() shouldBe false
        }
    }

    "the realtime voices are among the playbacks, but the player does not play for them" {
        withPlayer { player ->
            val keys = player.createRealtimePlayback("keys")

            player.playbacks() shouldBe listOf(keys)
            player.playing() shouldBe false

            val song = player.play(silentPattern)
            song.start()
            player.playing() shouldBe true

            song.stop()
            player.playbacks() shouldBe listOf(keys)
            player.playing() shouldBe false
        }
    }

    "a shutdown empties the playbacks" {
        withPlayer { player ->
            player.play(silentPattern).start()
            player.playing() shouldBe true

            player.shutdown()
            player.playbacks() shouldBe emptyList()
            player.playing() shouldBe false
        }
    }
})
