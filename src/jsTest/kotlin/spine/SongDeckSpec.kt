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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay

/**
 * The song that plays, in the Spine (KLANG-00406, steps 1 and 2): Klang Amp's song and a page's on the deck, a new one
 * in place of the one before, a page's song playing on after its page and taken back by it, the controllers' release,
 * the stage, live play left alone, and a stop while the engine starts (KLANG-00398).
 */
class SongDeckSpec : StringSpec({

    /**
     * A player whose backend never comes: the playbacks are bookkeeping, the rules run without audio. [ready], when
     * given, holds the engine's start until it completes.
     */
    class SilentEngine(
        initial: Player.Status = Player.Status.READY,
        private val ready: CompletableDeferred<Unit>? = null,
    ) : PlaybackEngine {
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

        override suspend fun readyPlayer(): KlangPlayer {
            ready?.await()

            return player
        }

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

    /** A page's controller for the song [id], as a song page builds it: not exclusive, on the deck by its Play. */
    fun SilentEngine.page(id: String): KlangCodePlaybackCtrl =
        KlangCodePlaybackCtrl.builder().code(song(id).code).title("Song $id").engine(this).build()

    /** Whether [ctrl] still follows the engine's status: a released one does not. */
    fun SilentEngine.follows(ctrl: KlangCodePlaybackCtrl): Boolean {
        val next = if (statusSource() == Player.Status.READY) Player.Status.LOADING else Player.Status.READY

        statusSource(next)

        return ctrl.state().playerStatus == next
    }

    suspend fun withDeck(engine: SilentEngine = SilentEngine(), test: suspend (SongDeck, SilentEngine) -> Unit) {
        val deck = SongDeck(engine = engine)

        try {
            test(deck, engine)
        } finally {
            deck.stop()
            engine.close()
        }
    }

    "Klang Amp's way: a song put on the deck plays, and is on the stage" {
        withDeck { deck, engine ->
            val ctrl = deck.play(song("a"))

            eventually { ctrl.state().isPlaying shouldBe true }
            deck.onDeck() shouldBe SongDeck.OnDeck(songId = "a", ctrl = ctrl, fromPage = false)
            Playbacks.nowPlaying()?.title shouldBe "Song a"
            engine.player.playbacks().size shouldBe 1
        }
    }

    "a new song stops the one before, which is released as no page shows it" {
        withDeck { deck, engine ->
            val first = deck.play(song("a"))
            eventually { first.state().isPlaying shouldBe true }

            val second = deck.play(song("b"))

            eventually { second.state().isPlaying shouldBe true }
            first.state().isPlaying shouldBe false
            engine.player.playbacks() shouldBe listOf(second.playback())
            deck.onDeck()?.songId shouldBe "b"
            withClue("the song before released") { engine.follows(first) shouldBe false }
        }
    }

    "a page's song plays on when the page goes, and the page opened again takes it back" {
        withDeck { deck, engine ->
            val page = engine.page("p")
            val hide = deck.show(page)

            deck.play(songId = "p", ctrl = page)
            eventually { page.state().isPlaying shouldBe true }

            hide()

            withClue("the song plays on after its page") { page.state().isPlaying shouldBe true }
            engine.player.playbacks().size shouldBe 1
            engine.follows(page) shouldBe true
            deck.ctrlFor("p") shouldBeSameInstanceAs page
            deck.ctrlFor("q") shouldBe null
        }
    }

    "two pages showing one controller (one fading out): it is released when the last goes, a hide counting once" {
        withDeck { deck, engine ->
            val page = engine.page("p")
            val fadingOut = deck.show(page)
            val opened = deck.show(page)

            fadingOut()
            fadingOut()

            withClue("kept for the page still showing it") { engine.follows(page) shouldBe true }

            opened()

            engine.follows(page) shouldBe false
        }
    }

    "a song Klang Amp put on is not taken by its page: the page shows the user's version" {
        withDeck { deck, _ ->
            deck.play(song("a"))

            withClue("Klang Amp's song is not the page's") { deck.ctrlFor("a") shouldBe null }
        }
    }

    "a page's song stopped while the page shows it stays with the page, and is released when the page goes" {
        withDeck { deck, engine ->
            val page = engine.page("p")
            val hide = deck.show(page)

            deck.play(songId = "p", ctrl = page)
            eventually { page.state().isPlaying shouldBe true }

            page.stop()

            deck.onDeck() shouldBe null
            withClue("kept for the page that shows it") { engine.follows(page) shouldBe true }

            hide()

            withClue("released when its page goes") { engine.follows(page) shouldBe false }
        }
    }

    "the page's Update plays on the deck live: the same playback, the new code" {
        withDeck { deck, engine ->
            val page = engine.page("p")

            deck.play(songId = "p", ctrl = page)
            eventually { page.state().isPlaying shouldBe true }
            val first = page.playback()

            page.setCode("import * from \"sprudel\"\nsound(\"hh\")")
            deck.play(songId = "p", ctrl = page)

            eventually { page.state().isCodeModified shouldBe false }
            page.playback() shouldBeSameInstanceAs first
            deck.onDeck()?.ctrl shouldBeSameInstanceAs page
        }
    }

    "a stop takes the song off the deck and off the stage" {
        withDeck { deck, engine ->
            val ctrl = deck.play(song("a"))
            eventually { ctrl.state().isPlaying shouldBe true }

            deck.stop()

            deck.onDeck() shouldBe null
            Playbacks.nowPlaying() shouldBe null
            engine.player.playbacks() shouldBe emptyList()
        }
    }

    "the deck takes the stage from a waiting song, and a code example that takes it back takes the song off the deck" {
        withDeck { deck, engine ->
            val waiting = engine.page("w")
            val example = KlangCodePlaybackCtrl.builder().code(song("e").code).exclusive().engine(engine).build()

            try {
                waiting.play()
                eventually { waiting.state().isPlaying shouldBe true }

                val ctrl = deck.play(song("a"))

                eventually {
                    withClue("the waiting song stopped by the deck") {
                        (ctrl.state().isPlaying && !waiting.state().isPlaying) shouldBe true
                    }
                }

                example.play()

                eventually { example.state().isPlaying shouldBe true }
                ctrl.state().isPlaying shouldBe false
                deck.onDeck() shouldBe null
            } finally {
                waiting.release()
                example.release()
            }
        }
    }

    "live play is on no stage: a song put on the deck leaves it playing" {
        withDeck { deck, engine ->
            val keys = engine.player.createRealtimePlayback("keys")

            val ctrl = deck.play(song("a"))
            eventually { ctrl.state().isPlaying shouldBe true }

            engine.player.playbacks().contains(keys) shouldBe true
        }
    }

    "a stop while the engine starts wins: nothing plays once it is ready (KLANG-00398)" {
        val ready = CompletableDeferred<Unit>()

        withDeck(engine = SilentEngine(ready = ready)) { deck, engine ->
            val ctrl = deck.play(song("a"))

            deck.stop()
            ready.complete(Unit)
            delay(100)

            withClue("nothing plays after a stop during the start") { engine.player.playbacks() shouldBe emptyList() }
            ctrl.state().isPlaying shouldBe false
        }
    }

    "a released controller stops what it plays: its owner, a code example, leaves nothing playing" {
        val engine = SilentEngine()
        val example = engine.page("e")

        try {
            example.play()
            eventually { example.state().isPlaying shouldBe true }

            example.release()

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
