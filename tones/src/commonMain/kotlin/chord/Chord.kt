package io.peekandpoke.klang.tones.chord

import io.peekandpoke.klang.tones.distance.distance
import io.peekandpoke.klang.tones.distance.tonicIntervalsTransposer
import io.peekandpoke.klang.tones.interval.subtract
import io.peekandpoke.klang.tones.note.note
import io.peekandpoke.klang.tones.note.tokenizeNote
import io.peekandpoke.klang.tones.pcset.isSubsetOf
import io.peekandpoke.klang.tones.pcset.isSupersetOf
import io.peekandpoke.klang.tones.scale.ScaleTypeDictionary
import io.peekandpoke.klang.tones.distance.transpose as transposeNote

/**
 * Represents a musical chord.
 *
 * @property chordType The [ChordType] properties.
 * @property name The name of the chord (e.g., "C major seventh").
 * @property symbol The symbol of the chord (e.g., "Cmaj7").
 * @property tonic The tonic note of the chord, if applicable.
 * @property type The type of the chord (e.g., "major seventh").
 * @property root The root note of the chord (after inversions).
 * @property bass The bass note of the chord (if different from root).
 * @property rootDegree The degree of the chord that is at the root (1 for root position, 2 for 1st inversion, etc.).
 * @property notes The list of notes in the chord.
 * @property intervals The list of intervals in the chord.
 */
class Chord(
    val chordType: ChordType,
    val name: String,
    val symbol: String,
    val tonic: String?,
    val type: String,
    val root: String,
    val bass: String,
    val rootDegree: Int,
    val notes: List<String>,
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
    }
}

/**
 * Tokenizes a chord name into [tonic, type, bass].
 */
fun tokenizeChord(name: String): Triple<String, String, String> {
    val tokens = tokenizeNote(name)
    val letter = tokens[0]
    val acc = tokens[1]
    val oct = tokens[2]
    val rest = tokens[3]

    return if (letter == "") {
        tokenizeBass("", name)
    } else if (letter == "A" && rest == "ug") {
        tokenizeBass("", "aug")
    } else {
        tokenizeBass(letter + acc, oct + rest)
    }
}

private fun tokenizeBass(note: String, chord: String): Triple<String, String, String> {
    val split = chord.split("/")
    if (split.size == 1) {
        return Triple(note, split[0], "")
    }
    val tokens = tokenizeNote(split[1])
    val letter = tokens[0]
    val acc = tokens[1]
    val oct = tokens[2]
    val rest = tokens[3]

    // Only a pitch class is accepted as bass note
    return if (letter != "" && oct == "" && rest == "") {
        Triple(note, split[0], letter + acc)
    } else {
        Triple(note, chord, "")
    }
}

/**
 * Get a Chord from a chord name or tokens.
 */
fun chord(src: Any?): Chord {
    return when (src) {
        is String -> {
            if (src.isEmpty()) return Chord.NoChord
            val (tonic, type, bass) = tokenizeChord(src)
            val chord = getChord(type, tonic, bass)
            if (chord.empty) getChord(src, "", "") else chord
        }

        is List<*> -> {
            val list = src.map { it.toString() }
            val tonic = list.getOrNull(0) ?: ""
            val type = list.getOrNull(1) ?: ""
            val bass = list.getOrNull(2) ?: ""
            getChord(type, tonic, bass)
        }

        else -> Chord.NoChord
    }
}

/**
 * Get chord properties.
 */
fun getChord(typeName: String, optionalTonic: String? = null, optionalBass: String? = null): Chord {
    val type = ChordTypeDictionary.get(typeName)
    val tonicNote = note(optionalTonic ?: "")
    val bassNote = note(optionalBass ?: "")

    if (type.empty || (optionalTonic != null && optionalTonic.isNotEmpty() && tonicNote.empty) || (optionalBass != null && optionalBass.isNotEmpty() && bassNote.empty)) {
        return Chord.NoChord
    }

    val bassInterval = if (tonicNote.empty || bassNote.empty) "" else distance(tonicNote.pc, bassNote.pc)
    val bassIndex = if (bassInterval.isEmpty()) -1 else type.intervals.indexOf(bassInterval)
    val hasRoot = bassIndex >= 0
    val root = if (hasRoot) bassNote else note("")
    val rootDegree = if (bassIndex == -1) 0 else bassIndex + 1
    val hasBass = bassNote.pc.isNotEmpty() && bassNote.pc != tonicNote.pc

    val intervals = type.intervals.toMutableList()

    if (hasRoot) {
        for (i in 1 until rootDegree) {
            val first = intervals[0]
            val numStr = first.takeWhile { it.isDigit() || it == '-' || it == '+' }
            val quality = first.drop(numStr.length)
            val newNum = numStr.toInt() + 7
            intervals.add("$newNum$quality")
            intervals.removeAt(0)
        }
    } else if (hasBass) {
        val ivl = subtract(distance(tonicNote.pc, bassNote.pc), "8P")
        if (ivl.isNotEmpty()) {
            intervals.add(0, ivl)
        }
    }

    val notes = if (tonicNote.empty) emptyList() else intervals.map { transposeNote(tonicNote.pc, it) }

    val actualTypeName = if (typeName in type.aliases) typeName else type.aliases.firstOrNull() ?: ""

    val symbol = "${if (tonicNote.empty) "" else tonicNote.pc}$actualTypeName${
        if (hasRoot && rootDegree > 1) "/" + root.pc else if (hasBass) "/" + bassNote.pc else ""
    }"

    val name = "${if (optionalTonic != null && optionalTonic.isNotEmpty()) tonicNote.pc + " " else ""}${type.name}${
        if (hasRoot && rootDegree > 1) " over " + root.pc else if (hasBass) " over " + bassNote.pc else ""
    }"

    return Chord(
        chordType = type,
        name = name,
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
fun transposeChord(chordName: String, interval: String): String {
    val (tonic, type, bass) = tokenizeChord(chordName)
    if (tonic.isEmpty()) {
        return chordName
    }
    val tr = if (bass.isEmpty()) "" else transposeNote(bass, interval)
    val slash = if (tr.isEmpty()) "" else "/$tr"
    return transposeNote(tonic, interval) + type + slash
}

/**
 * Get all scales where the given chord fits.
 */
fun chordScales(name: String): List<String> {
    val s = chord(name)
    if (s.empty) return emptyList()
    val isChordIncluded = isSupersetOf(s.chroma)
    return ScaleTypeDictionary.all()
        .filter { isChordIncluded(it.chroma) }
        .map { it.name }
}

/**
 * Get all chords names that are a superset of the given one.
 */
fun extendedChords(chordName: String): List<String> {
    val s = chord(chordName)
    if (s.empty) return emptyList()
    val isSuperset = isSupersetOf(s.chroma)
    val tonic = s.tonic ?: ""
    return ChordTypeDictionary.all()
        .filter { isSuperset(it.chroma) }
        .map { tonic + (it.aliases.firstOrNull() ?: "") }
}

/**
 * Find all chords names that are a subset of the given one.
 */
fun reducedChords(chordName: String): List<String> {
    val s = chord(chordName)
    if (s.empty) return emptyList()
    val isSubset = isSubsetOf(s.chroma)
    val tonic = s.tonic ?: ""
    return ChordTypeDictionary.all()
        .filter { isSubset(it.chroma) }
        .map { tonic + (it.aliases.firstOrNull() ?: "") }
}

/**
 * Return the chord notes.
 */
fun chordNotes(chordName: Any?, tonic: String? = null): List<String> {
    val chord = chord(chordName)
    val t = if (tonic.isNullOrEmpty()) chord.tonic else tonic
    if (t.isNullOrEmpty() || chord.empty) return emptyList()
    return chord.intervals.map { transposeNote(t, it) }
}

/**
 * Returns a function to get a note name from the chord degree.
 */
fun chordDegrees(chordName: Any?, tonic: String? = null): (Int) -> String {
    val chord = chord(chordName)
    val t = if (tonic.isNullOrEmpty()) chord.tonic else tonic
    val transpose = tonicIntervalsTransposer(chord.intervals, t)
    return { degree ->
        if (degree != 0 && !t.isNullOrEmpty()) transpose(if (degree > 0) degree - 1 else degree) else ""
    }
}

/**
 * Same as `chordDegrees` but with 0-based index.
 */
fun chordSteps(chordName: Any?, tonic: String? = null): (Int) -> String {
    val chord = chord(chordName)
    val t = if (tonic.isNullOrEmpty()) chord.tonic else tonic
    return tonicIntervalsTransposer(chord.intervals, t)
}
