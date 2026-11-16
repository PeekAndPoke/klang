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
 * A recorded song as the band reads it: the events by their tags, each with its strength within its tag. Each
 * player takes the events with its tags.
 *
 * An event with several tags has a strength for each of them. (The prototype this was ported from kept one for the
 * event, the last tag's; no recording so far has an event with two tags.)
 */
class Song(recording: Recording) {
    /** Beats a minute: a cycle is a bar of four beats. */
    val bpm: Double = recording.rpm * 4

    /** The events by tag, each list sorted by time. */
    val byTag: Map<String, List<SongEvent>> = recording.events
        .flatMap { e -> e.tags.map { tag -> tag to e } }
        .groupBy(keySelector = { it.first }, valueTransform = { it.second })
        .mapValues { (_, list) ->
            // how hard an event is played: its gain against the loudest of its tag, never under a quarter
            val loudest = max(list.maxOf { it.gain ?: 1.0 }, 1e-9)

            list.sortedBy { it.t }.map { e ->
                SongEvent(
                    t = e.t,
                    d = e.d,
                    v = min(1.0, max(0.25, (e.gain ?: loudest) / loudest)),
                    midi = midiOf(e),
                    sound = e.sound,
                )
            }
        }

    private fun tagged(tag: String): List<SongEvent> = byTag[tag].orEmpty()

    /** When the events with any of [tags] start, sorted. */
    fun times(tags: List<String>): List<Double> = tags.flatMap { tag -> tagged(tag).map { it.t } }.sorted()

    /**
     * The drummer's hits. The kick with both feet: the right one leads, and in a run the two take turns. A fast run
     * on the snare is played by both hands in turn. The right hand keeps the hat and the ride going; a crash or the
     * open hat between them is the left hand's when it is free (no snare just then), so no hand has to jump between
     * two cymbals.
     */
    fun drumHits(): List<DrumHit> {
        val hits = mutableListOf<DrumHit>()

        var foot: DrumHit? = null

        for (e in merged(tagged("kick")) { "k" }) {
            val before = foot
            val run = before != null && e.t - before.t < 0.45
            val limb = if (run && before.limb == Limb.KickR) Limb.KickL else Limb.KickR

            foot = DrumHit(t = e.t, drum = Drum.Kick, limb = limb, v = e.v).also { hits.add(it) }
        }

        var previous: DrumHit? = null

        for (e in merged(tagged("snare")) { "s" }) {
            val before = previous
            val fast = before != null && e.t - before.t < 0.22
            val limb = if (fast && before.limb == Limb.L) Limb.R else Limb.L

            previous = DrumHit(t = e.t, drum = Drum.Snare, limb = limb, v = e.v).also { hits.add(it) }
        }

        fun busy(limb: Limb, t: Double, within: Double) = hits.any { it.limb == limb && abs(it.t - t) < within }

        val cymbals = (tagged("hats") + tagged("countin")).sortedBy { it.t }

        for (e in merged(cymbals) { it.sound.orEmpty() }) {
            val drum = cymbalOf(e)
            val limb = if ((drum == Drum.Crash || drum == Drum.Hat2) && !busy(Limb.L, e.t, 0.19)) Limb.L else Limb.R

            if (busy(limb, e.t, 0.12)) {
                continue
            }

            hits.add(DrumHit(t = e.t, drum = drum, limb = limb, v = e.v))
        }

        return hits.sortedBy { it.t }
    }

    /** A player's notes from the events with [tag]. */
    fun notesOf(tag: String): List<Note> {
        return merged(tagged(tag)) { it.midi.toString() }.mapNotNull { e ->
            e.midi?.let { Note(t = e.t, midi = it, v = e.v, d = e.d) }
        }
    }

    /**
     * A string player's plucks and strums from the events with any of [tags]: what sounds together is one, its
     * lowest note leading.
     */
    fun strumsOf(tags: List<String>): List<Strum> {
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
