/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.visuals

import io.peekandpoke.klang.audio_be.AudioAnalyzer
import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_bridge.analyzer.SPECTRUM_FFT_SIZE
import io.peekandpoke.klang.audio_bridge.analyzer.createAnalyzerBuffer
import io.peekandpoke.klang.audio_engine.KlangCyclicPlayback
import io.peekandpoke.klang.spine.spine
import io.peekandpoke.klang.sprudel.SprudelDiagnostics
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.viz.liveact.LiveAct
import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.song.RecordedEvent
import io.peekandpoke.klang.viz.liveact.song.Song
import io.peekandpoke.klang.viz.liveact.sound.Energy
import io.peekandpoke.klang.viz.liveact.sound.LiveLoudness
import io.peekandpoke.klang.viz.liveact.sound.Music
import io.peekandpoke.klang.viz.liveact.sound.Transport
import io.peekandpoke.klang.viz.liveact.sound.TransportControls
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.html.Tag
import kotlinx.html.div
import kotlin.math.floor
import kotlin.math.max
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
 * The band needs the song a little ahead of the sound, so the playing code is compiled once more here and asked for
 * its events as it plays: always [AHEAD] cycles ahead of what is heard (and never less than [AHEAD_SEC] seconds:
 * a body's moves take seconds, however fast the song), a whole cycle at a time (as the player asks it). The band
 * is there as soon as the first stretch is read, and plans on as the song grows. The music itself is the app's:
 * the live act follows the playback's time and starts and stops nothing. Its loudness is read from the
 * player's analyser. What lies more than [KEEP_SEC] seconds behind what is heard is forgotten again, so a long
 * set stays as small as a short one.
 *
 * Known limits, for now. Asking a song again gives other events where the song's seed is another at every asking,
 * so a part that draws at random under it (Der Schmetterling's marimba) is not played note for note (KLANG-00450).
 * A change of the code or of the tempo makes the band anew. The stage says the code in the editor, not the code
 * that plays: a song edited and not played again is read as edited. The band plays the parts it knows by their
 * tags (the ones of Der Schmetterling); a song without them shows the band standing. While the page is hidden the
 * song is read and forgotten on, but nobody plans: seen again, the band takes up as if it had begun there (where a
 * hand was, and when the frontwoman played last, is not known to it). And Esc closes the visual here, so of the
 * live act's two keys that give the camera back only 0 is of use.
 */
class LiveActVisual(ctx: NoProps) : PureComponent(ctx) {

    /** A show that is ready to be seen, and a key that is another one for every show. */
    private class Ready(val show: Show, val key: String)

    /** What plays, as far as it matters to the band: another code, tempo or playback is another show. */
    private data class Playing(val code: String, val rpm: Double, val playback: KlangCyclicPlayback)

    /** A song as it is read while it plays: the [pattern] asked, and how far ([to], in cycles) it is read into [song]. */
    private class Reading(val song: Song, val pattern: KlangPattern, val cps: Double, var to: Double) {
        /** How far ahead of what is heard it is read, in cycles. */
        val ahead: Double = max(AHEAD, AHEAD_SEC * cps)

        /** Whether a cycle that could not be read was told of already. */
        var failed: Boolean = false
    }

    private val scope = MainScope()
    private var shows = 0

    private var ready: Ready? by value(null)

    /** Whether something plays at all. */
    private var playing: Boolean by value(false)

    /** What reads the song on, for the show that is seen. */
    private var following: Job? = null

    /** The song's first stretch for what plays now; a read for what no longer plays makes no show. */
    private val songs = LatestRead<Playing, Reading>(
        scope = scope,
        read = { begin(it) },
        changed = { before, now ->
            playing = now != null

            // Another playback, or none: its band leaves at once. The same playback with another code or tempo: its
            // band plays on, and its song is read on, until the new song's first stretch is read.
            if (now == null || now.playback !== before?.playback) {
                following?.cancel()
                following = null
                ready = null
            }
        },
        done = { now, reading ->
            following?.cancel()
            following = reading?.let { scope.launch { follow(reading = it, playback = now.playback) } }

            shows++
            ready = Ready(
                show = Show(
                    seed = Random.nextInt(100_000),
                    music = PlayingMusic(song = reading?.song, playback = now.playback) {
                        spine.player.player()?.getAnalyzer()
                    },
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
     * Begins to read what plays: the code compiled, and its events from where the playback is to as far ahead as it
     * is read ([Reading.ahead]).
     * Null where the code gives no pattern.
     */
    private suspend fun begin(playing: Playing): Reading? {
        // compiled once more, on an engine of its own; what the build swallows was said to the editor already when the
        // song was started, so it is gathered here and dropped
        val pattern: KlangPattern = SprudelDiagnostics.collectingInto(mutableListOf()) {
            SprudelPattern.compile(spine.player.scriptEngine(), playing.code)
        } ?: return null
        val cps = playing.rpm / 60.0
        val now = (playing.playback.heardSec() ?: 0.0) * cps
        // a bar back as well: a note that began just before still sounds
        val reading = Reading(song = Song(rpm = playing.rpm), pattern = pattern, cps = cps, to = max(0.0, floor(now) - 1))

        readOn(reading = reading, playback = playing.playback)

        return reading
    }

    /** Reads [reading] on for as long as it is followed: ahead of what is heard of [playback]. */
    private suspend fun follow(reading: Reading, playback: KlangCyclicPlayback) {
        while (true) {
            delay(FOLLOW_MS)
            readOn(reading = reading, playback = playback)
        }
    }

    /**
     * Reads [reading] up to as far ahead of what is heard of [playback] as it is read ([Reading.ahead]), a whole
     * cycle at a time (asked in other pieces, a pattern can give other events: KLANG-00450), and lets the page
     * breathe between them. A cycle that cannot be read is left out, as the player leaves it out, and told of once.
     */
    private suspend fun readOn(reading: Reading, playback: KlangCyclicPlayback) {
        val now = (playback.heardSec() ?: 0.0) * reading.cps

        // fallen behind what is heard (the page was hidden): what is past is not read any more
        if (reading.to < floor(now) - 1) {
            reading.to = floor(now) - 1
        }

        while (reading.to < now + reading.ahead) {
            val from = reading.to
            val events = try {
                reading.pattern.queryEvents(fromCycles = from, toCycles = from + 1, cps = reading.cps).mapNotNull { event ->
                    val data = event.toVoiceData()
                    val tags = data.tags?.sorted().orEmpty()

                    if (tags.isEmpty() || data.control == true) {
                        null
                    } else {
                        RecordedEvent(
                            t = event.startCycles / reading.cps,
                            d = event.durationCycles / reading.cps,
                            c = event.startCycles,
                            tags = tags,
                            note = data.note,
                            freq = data.freqHz?.takeIf { it.isFinite() },
                            gain = data.gain?.takeIf { it.isFinite() },
                            sound = data.sound,
                            index = data.soundIndex,
                            pan = data.pan?.takeIf { it.isFinite() },
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (!reading.failed) {
                    reading.failed = true
                    console.error("The live act could not read cycle ${from.toInt()} of the playing song", e)
                }

                emptyList()
            }

            // in one go with what is new, so the band plans once: what lies well behind what is heard is forgotten
            reading.song.forget(before = now / reading.cps - KEEP_SEC)
            reading.song.add(events)
            reading.to = from + 1
            delay(1)
        }
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

                    +(if (playing) "the band reads the song" else "play a song: the band plays it")
                }
            }
        }
    }

    private companion object {
        /** Where the figures lie, beside the page (klangviz's resources). */
        const val MODELS = "/liveact/notstrom/"

        /** How far ahead of what is heard the song is read, in cycles. */
        const val AHEAD = 8.0

        /**
         * And never less than this, in seconds: the frontwoman must know her next note thirteen seconds ahead to
         * come back from a stroll without a jump (see TrumpetPlayer), and a look between two players takes nine.
         */
        const val AHEAD_SEC = 14.0

        /**
         * How long what was heard is kept, in seconds. The players look back a few seconds; what they need of
         * longer ago (their last note, a look the camera has begun) they keep themselves.
         */
        const val KEEP_SEC = 16.0

        /** How often it is looked whether the song is to be read on, in milliseconds. */
        const val FOLLOW_MS = 200L
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
