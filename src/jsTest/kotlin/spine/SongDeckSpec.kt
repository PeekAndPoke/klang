/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.klang.Song
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.audio_fe.samples.NullAudioDecoder
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.audio_fe.utils.AssetLoader
import io.peekandpoke.klang.script.KlangScriptEngine
import io.peekandpoke.klang.script.stdlibLib
import io.peekandpoke.klang.sprudel.lang.sprudelLib
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.ultra.streams.StreamSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel

/**
 * The song that plays, in the Spine (KLANG-00406, step 1): put on, a new one in its place, a live update, the stop,
 * the stage taken from a page's playback and given up to one, live play left alone, and a controller's release.
 */
class SongDeckSpec : StringSpec({

    /** A player whose backend never comes: the playbacks are bookkeeping, the rules run without audio. */
    class SilentEngine(initial: Player.Status = Player.Status.READY) : PlaybackEngine {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

        val statusSource = StreamSource(initial)

        override val status = statusSource.readonly

        override suspend fun readyPlayer(): KlangPlayer = player

        override fun scriptEngine(): KlangScriptEngine = KlangScriptEngine.Builder().apply {
            registerLibrary(stdlibLib)
            registerLibrary(sprudelLib)
        }.build()

        fun close() {
            player.shutdown()
            scope.cancel()
        }
    }

    fun song(id: String) = Song(id = id, title = "Song $id", rpm = 30.0, code = "import * from \"sprudel\"\nsound(\"bd\")")

    suspend fun withDeck(test: suspend (SongDeck, SilentEngine) -> Unit) {
        val engine = SilentEngine()
        val deck = SongDeck(engine = engine)

        try {
            test(deck, engine)
        } finally {
            deck.stop()
            engine.close()
        }
    }

    "a song put on the deck plays, and is on the stage" {
        withDeck { deck, engine ->
            val a = song("a")

            deck.play(a)

            eventually { deck.state().isPlaying shouldBe true }
            deck.onDeck() shouldBe a
            Playbacks.nowPlaying()?.title shouldBe "Song a"
            engine.player.playbacks().size shouldBe 1
        }
    }

    "a new song stops the one before: one playback, the new song on the deck" {
        withDeck { deck, engine ->
            deck.play(song("a"))
            eventually { deck.state().isPlaying shouldBe true }
            val first = deck.playback()

            deck.play(song("b"))

            eventually {
                withClue("a new playback in place of the first") {
                    (deck.state().isPlaying && deck.playback() != null && deck.playback() !== first) shouldBe true
                }
            }
            engine.player.playbacks() shouldBe listOf(deck.playback())
            deck.onDeck()?.id shouldBe "b"
        }
    }

    "an update changes the song as it plays: the same playback, the song kept on the deck" {
        withDeck { deck, _ ->
            val a = song("a")

            deck.play(a)
            eventually { deck.state().isPlaying shouldBe true }
            val first = deck.playback()

            deck.update(code = "import * from \"sprudel\"\nsound(\"hh\")", rpm = 60.0)

            eventually { (deck.state().code.endsWith("sound(\"hh\")") && !deck.state().isCodeModified) shouldBe true }
            deck.playback() shouldBeSameInstanceAs first
            deck.state().rpm shouldBe 60.0
            deck.onDeck() shouldBe a
        }
    }

    "an update with no song on the deck changes nothing" {
        withDeck { deck, _ ->
            val before = deck.state().code

            deck.update(code = "sound(\"hh\")", rpm = 60.0)

            withClue("no song on the deck") { deck.state().code shouldBe before }
        }
    }

    "a stop takes the song off the deck and off the stage" {
        withDeck { deck, engine ->
            deck.play(song("a"))
            eventually { deck.state().isPlaying shouldBe true }

            deck.stop()

            deck.onDeck() shouldBe null
            Playbacks.nowPlaying() shouldBe null
            engine.player.playbacks() shouldBe emptyList()
        }
    }

    "the deck takes the stage from a page's playback, and a page that takes it back takes the song off the deck" {
        withDeck { deck, engine ->
            val page = KlangCodePlaybackCtrl.builder().code(song("p").code).engine(engine).build()
            val example = KlangCodePlaybackCtrl.builder().code(song("e").code).exclusive().engine(engine).build()

            try {
                page.play()
                eventually { page.state().isPlaying shouldBe true }

                deck.play(song("a"))

                eventually {
                    withClue("the page's playback stopped by the deck") {
                        (deck.state().isPlaying && !page.state().isPlaying) shouldBe true
                    }
                }

                example.play()

                eventually { example.state().isPlaying shouldBe true }
                deck.state().isPlaying shouldBe false
                deck.onDeck() shouldBe null
            } finally {
                page.release()
                example.release()
            }
        }
    }

    "live play is on no stage: a song put on the deck leaves it playing" {
        withDeck { deck, engine ->
            val keys = engine.player.createRealtimePlayback("keys")

            deck.play(song("a"))
            eventually { deck.state().isPlaying shouldBe true }

            engine.player.playbacks().contains(keys) shouldBe true
        }
    }

    "a released controller stops what it plays: its owner, a page, leaves nothing playing" {
        val engine = SilentEngine()
        val page = KlangCodePlaybackCtrl.builder().code(song("p").code).engine(engine).build()

        try {
            page.play()
            eventually { page.state().isPlaying shouldBe true }

            page.release()

            withClue("nothing plays after the release") { engine.player.playbacks() shouldBe emptyList() }
        } finally {
            engine.close()
        }
    }

    "a controller follows the engine's status until it is released" {
        val engine = SilentEngine(initial = Player.Status.LOADING)
        val ctrl = KlangCodePlaybackCtrl.builder().engine(engine).build()

        try {
            engine.statusSource(Player.Status.READY)
            ctrl.state().playerStatus shouldBe Player.Status.READY

            ctrl.release()
            engine.statusSource(Player.Status.FAILED)

            ctrl.state().playerStatus shouldBe Player.Status.READY
        } finally {
            engine.close()
        }
    }
})
