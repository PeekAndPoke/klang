/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.song

import io.peekandpoke.klang.viz.liveact.Timed
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One sounding event of a recorded song, as the recorder wrote it: [t] and [d] in seconds, [c] in cycles. */
@Serializable
data class RecordedEvent(
    val t: Double,
    val d: Double,
    val c: Double = 0.0,
    val tags: List<String> = emptyList(),
    val note: String? = null,
    val freq: Double? = null,
    val gain: Double? = null,
    val sound: String? = null,
    val index: Int? = null,
    val pan: Double? = null,
)

/** One render of a song, as recorded beside its audio: its tempo ([rpm], cycles a minute) and every event. */
@Serializable
data class Recording(
    val rpm: Double,
    val cps: Double = 0.0,
    val cycles: Double = 0.0,
    val seconds: Double = 0.0,
    val events: List<RecordedEvent>,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** A recording from its JSON [text]. It throws where the text is no recording. */
        fun parse(text: String): Recording = json.decodeFromString(serializer(), text)
    }
}

/** One event as the players read it: when, how long, how hard ([v], 0.25 to 1 within its tag), and what. */
data class SongEvent(
    override val t: Double,
    val d: Double,
    val v: Double,
    val midi: Int?,
    val sound: String?,
) : Timed

/** A drum, a cymbal, or the kick. */
enum class Drum { Kick, Snare, Tom, Floor, Hat, Hat2, Crash, Ride }

/** What hits a drum: a hand, or a foot on the kick's double pedal. */
enum class Limb { L, R, KickL, KickR }

/** One hit on the kit. */
data class DrumHit(override val t: Double, val drum: Drum, val limb: Limb, val v: Double) : Timed

/** One note of a tune: its pitch as a midi number, [d] seconds long. */
data class Note(override val t: Double, val midi: Int, val v: Double, val d: Double) : Timed

/** One pluck or strum of a string player: [midi] is its lowest note, [all] every note that sounds in it. */
data class Strum(override val t: Double, val d: Double, val midi: Int, val v: Double, val all: List<Int>) : Timed

/**
 * A part of a song, as its player reads it: the [events] it plays, as far as the song is known. A recording is known
 * whole; a song played live grows while it plays ([grows]), and the part with it. A player asks [refresh] before it
 * moves and plans anew where that says true.
 */
class Part<T>(val grows: Boolean, private val version: () -> Int, private val read: () -> List<T>) {
    private var seen: Int? = null

    /** The part's events as of the last [refresh], sorted by time. */
    var events: List<T> = emptyList()
        private set

    /** Reads the part again where the song has grown or forgotten since the last call; true then, and at the first call. */
    fun refresh(): Boolean {
        val now = version()

        if (now == seen) {
            return false
        }

        seen = now
        events = read()

        return true
    }

    companion object {
        /** A part that is [events] and stays so (test notes). */
        fun <T> fixed(events: List<T>): Part<T> = Part(grows = false, version = { 0 }, read = { events })
    }
}

/**
 * A song as the band reads it: the events by their tags, each with its strength within its tag. Each player takes
 * the events with its tags.
 *
 * A recording is given whole. A song played live starts empty and grows ([add]) as it is read ahead of the sound:
 * every reading (the hits, the notes, the strums, the times) is then of the song as far as it is known, and
 * [version] says when that has changed. What was read of the past stays as it was when more is added, except within
 * a fifth of a second of the newest events (a doubled hit, or a cymbal a hand is not free for, is told from what
 * sounds beside it). An event's strength is its gain against the loudest of its tag known when it is added, and
 * stays that: a recording, added at once, is measured against its loudest event.
 *
 * A song played live for long also forgets ([forget]): what lies well behind what is heard is dropped, so the song
 * stays as small as the stretch the band looks at. The readings then begin where the song still knows itself, and
 * what remains reads as it did before: which foot and which hand hits a drum goes on from the last hits forgotten
 * (for each kit of drum tags read before the song forgot, see [drumHits]).
 * Only an event longer than the stretch kept is forgotten while it still sounds (it goes by its start).
 *
 * An event with several tags has a strength for each of them. (The prototype this was ported from kept one for the
 * event, the last tag's; no recording so far has an event with two tags.)
 */
class Song(rpm: Double, val grows: Boolean = true) {

    /** A [recording], known whole. */
    constructor(recording: Recording) : this(rpm = recording.rpm, grows = false) {
        add(recording.events)
    }

    /** Beats a minute: a cycle is a bar of four beats. */
    val bpm: Double = rpm * 4

    /** Counts up whenever events are added or forgotten: what was read before is to be read again. */
    var version: Int = 0
        private set

    private val known = mutableMapOf<String, MutableList<SongEvent>>()

    /** The loudest gain of each tag so far. */
    private val loudest = mutableMapOf<String, Double>()

    private var tagged: Map<String, List<SongEvent>>? = null
    private val hits = mutableMapOf<DrumTags, List<DrumHit>>()
    private val notes = mutableMapOf<String, List<Note>>()
    private val strums = mutableMapOf<List<String>, List<Strum>>()
    private val starts = mutableMapOf<List<String>, List<Double>>()

    /**
     * What the drummer hit last of what was forgotten ([forget]), for each kit read so far ([drumHits]): the feet and
     * the hands go on from it. A kit is remembered from its first reading on.
     */
    private val forgottenHits = mutableMapOf<DrumTags, ForgottenHits>()

    /**
     * What a drummer hit last of what the song forgot: the last [kick] and the last hit on the [snare] (the feet and
     * the hands go on from them), and what the [hands] hit, as far back as a hand is busy with it ([BUSY] seconds).
     */
    private class ForgottenHits(val kick: DrumHit? = null, val snare: DrumHit? = null, val hands: List<DrumHit> = emptyList())

    /** More of the song: [events], in any order. */
    fun add(events: List<RecordedEvent>) {
        if (events.isEmpty()) {
            return
        }

        for (e in events) {
            for (tag in e.tags) {
                loudest[tag] = max(loudest[tag] ?: 1e-9, e.gain ?: 1.0)
            }
        }

        for (e in events) {
            for (tag in e.tags) {
                // how hard an event is played: its gain against the loudest of its tag, never under a quarter
                val most = loudest.getValue(tag)

                known.getOrPut(tag) { mutableListOf() }.add(
                    SongEvent(
                        t = e.t,
                        d = e.d,
                        v = min(1.0, max(0.25, (e.gain ?: most) / most)),
                        midi = midiOf(e),
                        sound = e.sound,
                    )
                )
            }
        }

        changed()
    }

    /**
     * Forgets the events that start before [before] (seconds). Ask it only for what lies well behind what is heard:
     * a player still needs its last note before now, and looks back a few seconds.
     *
     * The song is cut at a quiet moment: where events lie within 30 ms before [before], the cut goes back to before
     * them. So what sounds together (a part doubled, the notes of a strum) is never cut in two, and what remains is
     * told apart as it was.
     */
    fun forget(before: Double) {
        var cut = before

        for (t in known.values.flatMap { list -> list.map { it.t }.filter { it < before } }.sortedDescending()) {
            if (cut - t > 0.03) {
                break
            }

            cut = t
        }

        if (known.values.none { list -> list.any { it.t < cut } }) {
            return
        }

        // which foot and which hand hit last, of the hits that go, on every kit read so far
        for ((kit, earlier) in forgottenHits.toList()) {
            val gone = drumHits(kit).filter { it.t < cut }

            forgottenHits[kit] = ForgottenHits(
                kick = gone.lastOrNull { it.drum == Drum.Kick } ?: earlier.kick,
                snare = gone.lastOrNull { it.drum == Drum.Snare } ?: earlier.snare,
                hands = (earlier.hands + gone).filter { it.drum != Drum.Kick && it.t >= cut - BUSY },
            )
        }

        for (list in known.values) {
            list.removeAll { it.t < cut }
        }

        changed()
    }

    /**
     * Drops the events that start at [from] (seconds) or later: what was read ahead and will not sound as it was read
     * (the song's code or tempo changed while it plays). What began before stays, it has sounded. The new stretch is
     * then [add]ed; asked for both before the band moves again, the band plans once.
     */
    fun drop(from: Double) {
        if (known.values.none { list -> list.any { it.t >= from } }) {
            return
        }

        for (list in known.values) {
            list.removeAll { it.t >= from }
        }

        changed()
    }

    /** What was read is to be read again. */
    private fun changed() {
        version++
        tagged = null
        hits.clear()
        notes.clear()
        strums.clear()
        starts.clear()
    }

    /** The events by tag, each list sorted by time. */
    val byTag: Map<String, List<SongEvent>>
        get() = tagged ?: known.mapValues { (_, list) -> list.sortedBy { it.t } }.also { tagged = it }

    private fun tagged(tag: String): List<SongEvent> = byTag[tag].orEmpty()

    /** This song's part read by [read] (its hits, the notes of a tag, ...), growing with it. */
    fun <T> part(read: Song.() -> List<T>): Part<T> = Part(grows = grows, version = { version }, read = { read() })

    /** When the events with any of [tags] start, sorted. */
    fun times(tags: List<String>): List<Double> = starts.getOrPut(tags) {
        tags.flatMap { tag -> tagged(tag).map { it.t } }.sorted()
    }

    /**
     * The drummer's hits. The kick with both feet: the right one leads, and in a run the two take turns. A fast run
     * on the snare is played by both hands in turn. The right hand keeps the hat and the ride going; a crash or the
     * open hat between them is the left hand's when it is free (no snare just then), so no hand has to jump between
     * two cymbals. Which of the song's tags are the kick, the snare and the cymbals, the [kit] says (the score's).
     */
    fun drumHits(kit: DrumTags): List<DrumHit> = hits.getOrPut(kit) { readDrumHits(kit) }

    private fun readDrumHits(kit: DrumTags): List<DrumHit> {
        val forgotten = forgottenHits.getOrPut(kit) { ForgottenHits() }
        val hits = mutableListOf<DrumHit>()
        // when each hand hits, sorted: the snare's hits come in order, and so do the cymbals' after them
        val snares = mapOf(Limb.L to mutableListOf<Double>(), Limb.R to mutableListOf())
        val cymbalHits = mapOf(Limb.L to mutableListOf<Double>(), Limb.R to mutableListOf())

        // (what the hands hit last of what was forgotten still keeps them busy)
        for (h in forgotten.hands) {
            (if (h.drum == Drum.Snare) snares else cymbalHits).getValue(h.limb).add(h.t)
        }

        var foot: DrumHit? = forgotten.kick

        for (e in merged(tagged(kit.kick)) { "k" }) {
            val before = foot
            val run = before != null && e.t - before.t < 0.45
            val limb = if (run && before.limb == Limb.KickR) Limb.KickL else Limb.KickR

            foot = DrumHit(t = e.t, drum = Drum.Kick, limb = limb, v = e.v).also { hits.add(it) }
        }

        var previous: DrumHit? = forgotten.snare

        for (e in merged(tagged(kit.snare)) { "s" }) {
            val before = previous
            val fast = before != null && e.t - before.t < 0.22
            val limb = if (fast && before.limb == Limb.L) Limb.R else Limb.L

            previous = DrumHit(t = e.t, drum = Drum.Snare, limb = limb, v = e.v).also { hits.add(it) }
            snares.getValue(limb).add(e.t)
        }

        fun busy(limb: Limb, t: Double, within: Double): Boolean {
            return near(times = snares.getValue(limb), t = t, within = within) ||
                near(times = cymbalHits.getValue(limb), t = t, within = within)
        }

        val cymbals = kit.cymbals.flatMap { tagged(it) }.sortedBy { it.t }

        for (e in merged(cymbals) { it.sound.orEmpty() }) {
            val drum = cymbalOf(e)
            val limb = if ((drum == Drum.Crash || drum == Drum.Hat2) && !busy(limb = Limb.L, t = e.t, within = BUSY)) Limb.L else Limb.R

            if (busy(limb = limb, t = e.t, within = 0.12)) {
                continue
            }

            hits.add(DrumHit(t = e.t, drum = drum, limb = limb, v = e.v))
            cymbalHits.getValue(limb).add(e.t)
        }

        return hits.sortedBy { it.t }
    }

    /** A player's notes from the events with [tag]. */
    fun notesOf(tag: String): List<Note> = notes.getOrPut(tag) {
        merged(tagged(tag)) { it.midi.toString() }.mapNotNull { e ->
            e.midi?.let { Note(t = e.t, midi = it, v = e.v, d = e.d) }
        }
    }

    /**
     * A string player's plucks and strums from the events with any of [tags]: what sounds together is one, its
     * lowest note leading.
     */
    fun strumsOf(tags: List<String>): List<Strum> = strums.getOrPut(tags) { readStrums(tags) }

    private fun readStrums(tags: List<String>): List<Strum> {
        val all = tags.flatMap { tagged(it) }.filter { it.midi != null }.sortedBy { it.t }
        val out = mutableListOf<Strum>()

        for (n in all) {
            val midi = n.midi ?: continue
            val last = out.lastOrNull()

            if (last != null && n.t - last.t < 0.03) {
                out[out.size - 1] = last.copy(
                    midi = min(last.midi, midi),
                    v = max(last.v, n.v),
                    d = max(last.d, n.d),
                    all = if (midi in last.all) last.all else last.all + midi,
                )
            } else {
                out.add(Strum(t = n.t, d = n.d, midi = midi, v = n.v, all = listOf(midi)))
            }
        }

        return out
    }

    companion object {
        /** How long a hand is busy with a hit at the most, in seconds: what a crash or the open hat asks of the left hand. */
        private const val BUSY = 0.19

        private val NAMES = mapOf('c' to 0, 'd' to 2, 'e' to 4, 'f' to 5, 'g' to 7, 'a' to 9, 'b' to 11)
        private val NOTE = Regex("^([a-g])([#sb]*)(-?\\d+)$", RegexOption.IGNORE_CASE)

        /** An event's midi number: from its frequency, else from its note's name ("f#5", "eb4"); null without both. */
        fun midiOf(e: RecordedEvent): Int? {
            e.freq?.takeIf { it > 0 }?.let { return (69 + 12 * log2(it / 440)).roundToInt() }

            val match = NOTE.find(e.note.orEmpty()) ?: return null
            val (letter, signs, octave) = match.destructured
            val base = NAMES.getValue(letter.lowercase()[0]) + 12 * (octave.toInt() + 1)

            return base + signs.count { it != 'b' } - signs.count { it == 'b' }
        }

        /**
         * One hit where several events sound at once (a part doubled left and right): of the events with the same
         * [key], one that follows another by 30 ms or less is left out. The doubles are a few thousandths apart
         * (each has its own small delay), never further.
         */
        fun merged(list: List<SongEvent>, key: (SongEvent) -> String): List<SongEvent> {
            val out = mutableListOf<SongEvent>()
            val last = mutableMapOf<String, Double>()

            for (e in list.sortedBy { it.t }) {
                val k = key(e)
                val before = last[k]

                if (before == null || e.t - before > 0.03) {
                    out.add(e)
                }

                last[k] = e.t
            }

            return out
        }

        /** Whether any of [times] (sorted) is less than [within] seconds from [t]. */
        private fun near(times: List<Double>, t: Double, within: Double): Boolean {
            var lo = 0
            var hi = times.size

            while (lo < hi) {
                val mid = (lo + hi) shr 1

                // (written as the difference, as the test below is: the two then never disagree by a rounding)
                if (t - times[mid] >= within) {
                    lo = mid + 1
                } else {
                    hi = mid
                }
            }

            return lo < times.size && abs(times[lo] - t) < within
        }

        /** Which cymbal an event of the hats sounds: hh the closed hat, oh the second hat (open), cr the crash, rd the ride. */
        private fun cymbalOf(e: SongEvent): Drum {
            val s = e.sound.orEmpty().lowercase()

            return when {
                s.startsWith("cr") -> Drum.Crash
                s.startsWith("oh") -> Drum.Hat2
                s.startsWith("rd") -> Drum.Ride
                else -> Drum.Hat
            }
        }
    }
}
