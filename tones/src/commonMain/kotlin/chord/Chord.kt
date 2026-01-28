
package io.peekandpoke.klang.tones.chord

import io.peekandpoke.klang.tones.distance.Distance
import io.peekandpoke.klang.tones.interval.Interval
import io.peekandpoke.klang.tones.note.Note
import io.peekandpoke.klang.tones.pcset.PcSet
import io.peekandpoke.klang.tones.scale.ScaleTypeDictionary

/**
 * Represents a musical chord.
 */
class Chord(
    /** The [ChordType] properties. */
    val chordType: ChordType,
    /** The name of the chord (e.g., "C major seventh"). */
    val name: String,
    /** The symbol of the chord (e.g., "Cmaj7"). */
    val symbol: String,
    /** The tonic note of the chord, if applicable. */
    val tonic: String?,
    /** The type of the chord (e.g., "major seventh"). */
    val type: String,
    /** The root note of the chord (after inversions). */
    val root: String,
    /** The bass note of the chord (if different from root). */
    val bass: String,
    /** The degree of the chord that is at the root (1 for root position, 2 for 1st inversion, etc.). */
    val rootDegree: Int,
    /** The list of notes in the chord. */
    val notes: List<String>,
    /** The list of intervals in the chord. */
    val intervals: List<String>,
) {
    val empty: Boolean get() = chordType.empty
    val setNum: Int get() = chordType.setNum
    val quality: ChordQuality get() = chordType.quality
    val chroma: String get() = chordType.chroma
    val normalized: String get() = chordType.normalized
    val aliases: List<String> get() = chordType.aliases

    companion object {
        /**
         * Represents an empty or invalid chord.
         */
        val NoChord = Chord(
            chordType = ChordType.NoChordType,
            name = "",
            symbol = "",
            tonic = null,
            type = "",
            root = "",
            bass = "",
            rootDegree = 0,
            notes = emptyList(),
            intervals = emptyList()
        )

        /** Cache for tokenized chord names. */
        private val tokenizeCache = mutableMapOf<String, Triple<String, String, String>>()

        /** Cache for parsed chords. */
        private val chordCache = mutableMapOf<String, Chord>()

        /**
         * Tokenizes a chord name into [tonic, type, bass].
         */
        fun tokenize(name: String): Triple<String, String, String> = tokenizeCache.getOrPut(name) {
            val tokens = Note.tokenize(name)
            val letter = tokens[0]
            val acc = tokens[1]
            val oct = tokens[2]
            val rest = tokens[3]

            when {
                letter == "" -> tokenizeBass("", name)
                letter == "A" && rest == "ug" -> tokenizeBass("", "aug")
                // Fix: Handle 'sus' chords where 's' was incorrectly parsed as accidental
                // Note.tokenize includes 's' as a sharp notation, so "Csus" becomes letter=C, acc=s, rest=us
                (acc == "s" || acc == "f") && rest.startsWith("us") -> {
                    // Reconstruct: tonic is just the letter, type is "sus" + rest after "us"
                    tokenizeBass(letter, acc + rest)
                }
                else -> tokenizeBass(letter + acc, oct + rest)
            }
        }

        // Splits a chord type string into [type, bass] if a slash is present
        private fun tokenizeBass(note: String, chord: String): Triple<String, String, String> {
            val split = chord.split("/")
            if (split.size == 1) {
                return Triple(note, split[0], "")
            }

            val tokens = Note.tokenize(split[1])
            val letter = tokens[0]
            val oct = tokens[2]
            val rest = tokens[3]

            return if (letter != "" && oct == "" && rest == "") {
                Triple(note, split[0], letter + tokens[1])
            } else {
                Triple(note, chord, "")
            }
        }

        /**
         * Get a Chord from a chord name string.
         */
        fun get(src: String): Chord = chordCache.getOrPut(src) {
            if (src.isEmpty()) return@getOrPut NoChord
            val (tonic, type, bass) = tokenize(src)
            val chord = getChord(type, tonic, bass)

            if (chord.empty) getChord(src, "", "") else chord
        }

        /**
         * Get a Chord from a list of tokens [tonic, type, bass].
         */
        fun get(src: List<String>): Chord {
            val tonic = src.getOrNull(0) ?: ""
            val type = src.getOrNull(1) ?: ""
            val bass = src.getOrNull(2) ?: ""
            return getChord(type, tonic, bass)
        }

        /**
         * Returns the chord itself.
         */
        fun get(chord: Chord): Chord = chord

        /**
         * Get chord properties.
         */
        fun getChord(typeName: String, optionalTonic: String? = null, optionalBass: String? = null): Chord {
            val type = ChordTypeDictionary.get(typeName)
            val tonicNote = Note.get(optionalTonic ?: "")
            val bassNote = Note.get(optionalBass ?: "")

            if (type.empty ||
                (!optionalTonic.isNullOrEmpty() && tonicNote.empty) ||
                (!optionalBass.isNullOrEmpty() && bassNote.empty)
            ) {
                return NoChord
            }

            val bassInterval = if (tonicNote.empty || bassNote.empty) "" else Distance.distance(tonicNote, bassNote)
            val bassIndex = if (bassInterval.isEmpty()) -1 else type.intervals.indexOf(bassInterval)

            val hasRoot = bassIndex >= 0
            val root = if (hasRoot) bassNote else Note.NoNote
            val rootDegree = if (bassIndex == -1) 0 else bassIndex + 1
            val hasBass = bassNote.pc.isNotEmpty() && bassNote.pc != tonicNote.pc

            val intervals = type.intervals.toMutableList()

            if (hasRoot) {
                repeat((1 until rootDegree).count()) {
                    val first = intervals[0]
                    val numStr = first.takeWhile { it.isDigit() || it == '-' || it == '+' }
                    val quality = first.drop(numStr.length)
                    val newNum = numStr.toInt() + 7
                    intervals.add("$newNum$quality")
                    intervals.removeAt(0)
                }
            } else if (hasBass) {
                val ivl = Interval.subtract(Distance.distance(tonicNote, bassNote), "8P")
                if (ivl.isNotEmpty()) {
                    intervals.add(0, ivl)
                }
            }

            val notes = if (tonicNote.empty) emptyList() else intervals.map { Distance.transpose(tonicNote.pc, it) }
            val actualTypeName = if (typeName in type.aliases) typeName else type.aliases.firstOrNull() ?: ""

            val symbol = "${if (tonicNote.empty) "" else tonicNote.pc}$actualTypeName${
                if (hasRoot && rootDegree > 1) "/" + root.pc else if (hasBass) "/" + bassNote.pc else ""
            }"

            val namePrefix = if (!optionalTonic.isNullOrEmpty()) tonicNote.pc + " " else ""
            val overPart =
                if (hasRoot && rootDegree > 1) " over " + root.pc else if (hasBass) " over " + bassNote.pc else ""
            val fullName = "$namePrefix${type.name}$overPart"

            return Chord(
                chordType = type,
                name = fullName,
                symbol = symbol,
                tonic = tonicNote.pc,
                type = type.name,
                root = root.pc,
                bass = if (hasBass) bassNote.pc else "",
                intervals = intervals,
                rootDegree = rootDegree,
                notes = notes
            )
        }

        /**
         * Transpose a chord name.
         */
        fun transpose(chordName: String, interval: String): String {
            val (tonic, type, bass) = tokenize(chordName)
            if (tonic.isEmpty()) return chordName
            val tr = if (bass.isEmpty()) "" else Distance.transpose(bass, interval)
            val slash = if (tr.isEmpty()) "" else "/$tr"
            return Distance.transpose(tonic, interval) + type + slash
        }

        /**
         * Get all scales where the given chord fits.
         */
        fun chordScales(name: String): List<String> {
            val s = get(name)
            if (s.empty) return emptyList()
            val isChordIncluded = PcSet.isSupersetOf(s.chroma)
            return ScaleTypeDictionary.all()
                .filter { isChordIncluded(it.chroma) }
                .map { it.name }
        }

        /**
         * Get all chords names that are a superset of the given one.
         */
        fun extended(chordName: String): List<String> {
            val s = get(chordName)
            if (s.empty) return emptyList()
            val isSuperset = PcSet.isSupersetOf(s.chroma)
            val tonic = s.tonic ?: ""
            return ChordTypeDictionary.all()
                .filter { isSuperset(it.chroma) }
                .map { tonic + (it.aliases.firstOrNull() ?: "") }
        }

        /**
         * Find all chords names that are a subset of the given one.
         */
        fun reduced(chordName: String): List<String> {
            val s = get(chordName)
            if (s.empty) return emptyList()
            val isSubset = PcSet.isSubsetOf(s.chroma)
            val tonic = s.tonic ?: ""
            return ChordTypeDictionary.all()
                .filter { isSubset(it.chroma) }
                .map { tonic + (it.aliases.firstOrNull() ?: "") }
        }

        /**
         * Return the chord notes.
         */
        fun notes(chordName: String, tonic: String? = null): List<String> {
            val chord = get(chordName)
            val t = if (tonic.isNullOrEmpty()) chord.tonic else tonic
            if (t.isNullOrEmpty() || chord.empty) return emptyList()
            return chord.intervals.map { Distance.transpose(t, it) }
        }

        /**
         * Returns a function to get a note name from the chord degree.
         */
        fun degrees(chordName: String, tonic: String? = null): (Int) -> String {
            val chord = get(chordName)
            val t = if (tonic.isNullOrEmpty()) chord.tonic else tonic
            val transposer = Distance.tonicIntervalsTransposer(chord.intervals, t)
            return { degree ->
                if (degree != 0 && !t.isNullOrEmpty()) transposer(if (degree > 0) degree - 1 else degree) else ""
            }
        }

        /**
         * Same as `degrees` but with 0-based index.
         */
        fun steps(chordName: String, tonic: String? = null): (Int) -> String {
            val chord = get(chordName)
            val t = if (tonic.isNullOrEmpty()) chord.tonic else tonic
            return Distance.tonicIntervalsTransposer(chord.intervals, t)
        }
    }
}
