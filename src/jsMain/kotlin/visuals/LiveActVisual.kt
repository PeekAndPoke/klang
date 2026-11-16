/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.visuals

import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_be.AudioAnalyzer
import io.peekandpoke.klang.audio_bridge.analyzer.SPECTRUM_FFT_SIZE
import io.peekandpoke.klang.audio_bridge.analyzer.createAnalyzerBuffer
import io.peekandpoke.klang.audio_engine.KlangCyclicPlayback
import io.peekandpoke.klang.spine.spine
import io.peekandpoke.klang.sprudel.SprudelDiagnostics
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.viz.liveact.Energy
import io.peekandpoke.klang.viz.liveact.LiveAct
import io.peekandpoke.klang.viz.liveact.LiveLoudness
import io.peekandpoke.klang.viz.liveact.Music
import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.Transport
import io.peekandpoke.klang.viz.liveact.TransportControls
import io.peekandpoke.klang.viz.liveact.song.RecordedEvent
import io.peekandpoke.klang.viz.liveact.song.Recording
import io.peekandpoke.klang.viz.liveact.song.Song
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.html.Tag
import kotlinx.html.div
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

@Suppress("FunctionName")
fun Tag.LiveActVisual() = comp {
    LiveActVisual(it)
}

/**
 * The live act for whatever plays in the app (KLANG-00449): the band Notstrom on its stage, playing the song on the
 * stage of the app (the Spine's `playbacks.nowPlaying`).
 *
 * The band needs the song ahead of the sound, so the playing code is compiled once more here and asked for its
 * events from the start to [HORIZON] cycles ahead. The music itself is the app's: the live act follows the
 * playback's time and starts and stops nothing. Its loudness is read from the player's analyser.
 *
 * Known limits, for now. Asking a song again gives other events where the song draws at random without a seed, so a
 * part that does (Der Schmetterling's marimba) is not played note for note (KLANG-00450). A change of the code or of
 * the tempo makes the band anew. The stage says the code in the editor, not the code that plays: a song edited and
 * not played again is read as edited. The band plays the parts it knows by their tags (the ones of Der
 * Schmetterling); a song without them shows the band standing. And Esc closes the visual here, so of the live
 * act's two keys that give the camera back only 0 is of use.
 */
class LiveActVisual(ctx: NoProps) : PureComponent(ctx) {

    /** A show that is ready to be seen, and a key that is another one for every show. */
    private class Ready(val show: Show, val key: String)

    /** What plays, as far as it matters to the band: another code, tempo or playback is another show. */
    private data class Playing(val code: String, val rpm: Double, val playback: KlangCyclicPlayback)

    private val scope = MainScope()
    private var shows = 0

    private var ready: Ready? by value(null)

    /** Whether something plays at all. */
    private var playing: Boolean by value(false)

    /** How far the playing song is read ahead so far, in cycles. */
    private var read: Int by value(0)

    /** The song read ahead for what plays now; a read for what no longer plays makes no show. */
    private val songs = LatestRead<Playing, Song>(
        scope = scope,
        read = { songOf(code = it.code, rpm = it.rpm) },
        changed = { before, now ->
            read = 0
            playing = now != null

            // Another playback, or none: its band leaves at once. The same playback with another code or tempo: its
            // band plays on until the new song is read.
            if (now == null || now.playback !== before?.playback) {
                ready = null
            }
        },
        done = { now, song ->
            shows++
            ready = Ready(
                show = Show(
                    seed = Random.nextInt(100_000),
                    music = PlayingMusic(song = song, playback = now.playback) { spine.player.player()?.getAnalyzer() },
                ),
                key = "live-act-$shows",
            )
        },
        failed = { _, cause -> console.error("The live act could not read the playing song ahead", cause) },
    )

    @Suppress("unused")
    private val nowPlaying by subscribingTo(spine.playbacks.nowPlaying) { now ->
        songs.want(now?.let { Playing(code = it.code, rpm = it.rpm, playback = it.playback) })
    }

    init {
        lifecycle {
            onUnmount { scope.cancel() }
        }
    }

    /**
     * The song as the band reads it: the code compiled, and its events from the start to [HORIZON] cycles, asked a
     * few cycles at a time so the page stays alive meanwhile. Null where the code gives no pattern.
     */
    private suspend fun songOf(code: String, rpm: Double): Song? {
        // compiled once more, on an engine of its own; what the build swallows was said to the editor already when the
        // song was started, so it is gathered here and dropped
        val pattern: KlangPattern = SprudelDiagnostics.collectingInto(mutableListOf()) {
            SprudelPattern.compile(spine.player.scriptEngine(), code)
        } ?: return null
        val cps = rpm / 60.0
        val events = mutableListOf<RecordedEvent>()
        var from = 0.0

        while (from < HORIZON) {
            val to = minOf(HORIZON, from + SLICE)

            for (event in pattern.queryEvents(fromCycles = from, toCycles = to, cps = cps)) {
                val data = event.toVoiceData()
                val tags = data.tags?.sorted().orEmpty()

                if (tags.isEmpty() || data.control == true) {
                    continue
                }

                events.add(
                    RecordedEvent(
                        t = event.startCycles / cps,
                        d = event.durationCycles / cps,
                        c = event.startCycles,
                        tags = tags,
                        note = data.note,
                        freq = data.freqHz?.takeIf { it.isFinite() },
                        gain = data.gain?.takeIf { it.isFinite() },
                        sound = data.sound,
                        index = data.soundIndex,
                        pan = data.pan?.takeIf { it.isFinite() },
                    )
                )
            }

            from = to
            read = to.toInt()
            delay(1)
        }

        return Song(Recording(rpm = rpm, cps = cps, cycles = HORIZON, seconds = HORIZON / cps, events = events))
    }

    override fun VDom.render() {
        div {
            css {
                put("position", "absolute")
                put("inset", "0")
                put("background", "#020308")
            }

            val now = ready

            if (now != null) {
                div {
                    // another show is another component: the band is made anew
                    key = now.key
                    css {
                        put("position", "absolute")
                        put("inset", "0")
                    }

                    LiveAct(show = now.show, models = MODELS)
                }
            } else {
                div {
                    css {
                        put("position", "absolute")
                        put("left", "0")
                        put("right", "0")
                        put("bottom", "18px")
                        put("text-align", "center")
                        put("color", "#9ab")
                        put("font", "14px monospace")
                    }

                    +(if (playing) "the band reads the song: $read of ${HORIZON.toInt()} bars" else "play a song: the band plays it")
                }
            }
        }
    }

    private companion object {
        /** Where the figures lie, beside the page (klangviz's resources). */
        const val MODELS = "/liveact/notstrom/"

        /** How far ahead the song is read, in cycles, and how many at a time. */
        const val HORIZON = 512.0
        const val SLICE = 8.0
    }
}

/**
 * The song the app plays, as the live act's [Music]: its events read ahead ([song]), the [playback]'s time, and the
 * loudness the player's analyser hears. The music is the app's: a listener follows it and moves nothing.
 */
private class PlayingMusic(
    override val song: Song?,
    private val playback: KlangCyclicPlayback,
    /** The player's analyser as of now; null while there is none. */
    private val analyser: () -> AudioAnalyzer?,
) : Music {

    /** Live; said so too where the song could not be read and the band plays test notes to it. */
    override val source: String = if (song != null) "live" else "live (the song could not be read)"

    private val loudness = LiveLoudness()
    private val spectrum = createAnalyzerBuffer(SPECTRUM_FFT_SIZE / 2)
    private var heardAt = -1.0

    override fun energyAt(t: Double): Energy {
        val now = window.performance.now() / 1000

        // asked more than once a frame: heard once
        if (now - heardAt < 0.004) {
            return loudness.current()
        }

        heardAt = now

        val analyser = analyser() ?: return loudness.feed(now = now, total = 0.0, low = 0.0, mid = 0.0, high = 0.0)

        analyser.getFft(spectrum)

        // the spectrum's bins, in dB, added up as power in the three bands a recording's loudness has
        val width = SAMPLE_RATE / SPECTRUM_FFT_SIZE
        var total = 0.0
        var low = 0.0
        var mid = 0.0
        var high = 0.0

        for (i in 0 until spectrum.length) {
            val db = spectrum.asDynamic()[i].unsafeCast<Double>()

            // NaN-guard: a bin that is not a number, or silent, adds nothing
            if (!db.isFinite()) {
                continue
            }

            val power = 10.0.pow(db / 10)
            val hz: Double = i * width

            total += power

            when {
                hz < 20 -> Unit
                hz < 150 -> low += power
                hz < 2500 -> mid += power
                hz < 16000 -> high += power
                else -> Unit
            }
        }

        return loudness.feed(now = now, total = sqrt(total), low = sqrt(low), mid = sqrt(mid), high = sqrt(high))
    }

    override fun listen(): Transport = object : Transport {
        /** The seconds of the song heard so far: its place, as long as the tempo stays. */
        override val time: Double get() = playback.heardSec() ?: 0.0

        override val paused: Boolean get() = playback.heardSec() == null

        override val controls: TransportControls? = null

        override fun release() {
            // the app's music goes on
        }
    }

    private companion object {
        /**
         * The rate the engine is taken to run at, as the spectrum's own reading takes it. Where it runs at 44.1 kHz the
         * three bands' edges lie about a tenth lower: of no matter for a level.
         */
        const val SAMPLE_RATE = 48000.0
    }
}
