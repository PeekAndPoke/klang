/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.demo

import io.peekandpoke.klang.viz.liveact.AudioFileTransport
import io.peekandpoke.klang.viz.liveact.LiveAct
import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Switches
import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.scores.DER_SCHMETTERLING
import io.peekandpoke.klang.viz.liveact.song.Recording
import io.peekandpoke.klang.viz.liveact.song.Song
import io.peekandpoke.klang.viz.liveact.sound.Loudness
import io.peekandpoke.klang.viz.liveact.sound.RecordedMusic
import io.peekandpoke.kraft.addons.registry.addons
import io.peekandpoke.kraft.addons.threejs.threeJs
import io.peekandpoke.kraft.kraftApp
import io.peekandpoke.kraft.vdom.preact.PreactVDomEngine
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.random.Random

// The demo: the live act alone on a page, playing a recording of Der Schmetterling and its recorded events by the
// song's score (with `?test`: the test notes in their place); or, with `?meet`, "Meet the Band" alone (MeetPage).
// The page's switches (`?seed=7&t=62.1&still`) are the show's: see Switch for the list of them all.

/** Where the recording lies, beside the page (git-ignored: see resources/recording/README.md). */
private const val RECORDING = "recording/schmetterling"

/** Where the figures lie, beside the page (klangviz's resources). */
private const val MODELS = "liveact/notstrom/"

private val kraft = kraftApp {
    addons {
        threeJs()
    }
}

/** The text at [url]; null where it is not there. */
private suspend fun textAt(url: String): String? {
    return try {
        val response = window.fetch(url).await()

        if (response.ok) response.text().await() else null
    } catch (_: Throwable) {
        null
    }
}

fun main() {
    val switches = Switches.parse(window.location.search)

    if (switches.has(Switch.Meet)) {
        kraft.mount(selector = "#app", engine = PreactVDomEngine()) {
            // the demo serves its figures fresh from the build: its version is a fixed word
            MeetPage(first = Player.of(switches[Switch.Meet]) ?: Player.Sirene, models = MODELS, version = "dev")
        }

        return
    }

    // Another performance every time the page is opened: one seed for all of the choreography. A frozen time
    // without a seed takes seed 0, so stills stay comparable.
    val seed = switches[Switch.Seed]?.toIntOrNull() ?: if (switches.has(Switch.Time)) 0 else Random.nextInt(100_000)

    MainScope().launch {
        // The song as recorded, when it is there (?test: the test notes instead), and its loudness
        val song = if (switches.has(Switch.Test)) null else textAt("$RECORDING-events.json")?.let { Song(Recording.parse(it)) }
        val loudness = song?.let { textAt("$RECORDING-energy.json") }?.let {
            Json { ignoreUnknownKeys = true }.decodeFromString(Loudness.serializer(), it)
        }

        // the audio is the same render the events were recorded from: its time is the stage's time
        val music = RecordedMusic(song = song, loudness = loudness) {
            if (song != null) AudioFileTransport("$RECORDING.wav") else null
        }
        val show = Show(seed = seed, music = music, switches = switches)

        kraft.mount(selector = "#app", engine = PreactVDomEngine()) {
            // the demo serves its figures fresh from the build: its version is a fixed word
            LiveAct(show = show, score = DER_SCHMETTERLING, models = MODELS, version = "dev")
        }
    }
}
