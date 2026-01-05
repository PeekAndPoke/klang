@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.tones.scale

import io.peekandpoke.klang.tones.chord.ChordTypeDictionary
import io.peekandpoke.klang.tones.distance.Distance
import io.peekandpoke.klang.tones.note.Note
import io.peekandpoke.klang.tones.pcset.PcSet
import io.peekandpoke.klang.tones.utils.TonesArray

/**
 * Represents a musical musical scale.
 */
data class Scale(
    /** The [ScaleType] properties. */
    val scaleType: ScaleType,
    /** The tonic note of the scale, if applicable. */
    val tonic: String?,
    /** The type of the scale (e.g., "major"). */
    val type: String,
    /** The list of notes in the scale. */
    val notes: List<String>,
) {
    val empty: Boolean get() = scaleType.empty
    val name: String = if (!tonic.isNullOrEmpty()) "$tonic $type" else type
    val setNum: Int get() = scaleType.setNum
    val chroma: String get() = scaleType.chroma
    val normalized: String get() = scaleType.normalized
    val aliases: List<String> get() = scaleType.aliases
    val intervals: List<String> get() = scaleType.intervals

    companion object {
        val NoScale = Scale(
            scaleType = ScaleType.NoScaleType,
            tonic = null,
            type = "",
            notes = emptyList()
        )

        /**
         * Tokenizes a scale name into [tonic, type].
         */
        fun tokenize(name: String): Pair<String, String> {
            if (name.isEmpty()) return Pair("", "")

            val i = name.indexOf(" ")
            if (i < 0) {
                // If no space, check if the whole name is a note.
                // If yes, it's a note (no scale type). If no, it's a scale type (no tonic).
                val n = Note.get(name)
                return if (n.empty) Pair("", name.lowercase()) else Pair(n.name, "")
            }

            // If there's a space, try to parse the first part as a tonic
            val tonicPart = name.substring(0, i)
            val tonic = Note.get(tonicPart)

            return if (tonic.empty) {
                // If first part is not a note, try parsing the whole thing as a note (just in case)
                val n = Note.get(name)
                if (n.empty) Pair("", name.lowercase()) else Pair(n.name, "")
            } else {
                // First part is tonic, the rest is the scale type
                val type = name.substring(tonic.name.length).trim().lowercase()
                Pair(tonic.name, type)
            }
        }

        /**
         * Get a Scale from a scale name.
         */
        fun get(src: String): Scale {
            val (tonicName, typeName) = tokenize(src)
            val tonic = Note.get(tonicName).name
            val st = ScaleTypeDictionary.get(typeName)

            // If scale type is not found, return NoScale
            if (st.empty) {
                return NoScale
            }

            val type = st.name
            // Calculate notes if tonic is present
            val notes = if (tonic.isNotEmpty()) {
                st.intervals.map { Distance.transpose(tonic, it) }
            } else {
                emptyList()
            }

            return Scale(st, tonic.ifEmpty { null }, type, notes)
        }

        /**
         * Given a list of notes, detect the scales that match.
         */
        fun detect(
            notes: List<String>,
            tonic: String? = null,
            matchExact: Boolean = false,
        ): List<String> {
            val notesChroma = PcSet.chroma(notes)
            val tonicNote = Note.get(tonic ?: notes.firstOrNull() ?: "")
            val tonicChroma = if (tonicNote.chroma != -1) tonicNote.chroma else return emptyList()

            // Prepare the chroma relative to the tonic
            val pitchClasses = notesChroma.split("").filter { it.isNotEmpty() }.toMutableList()
            pitchClasses[tonicChroma] = "1"

            // Rotate chroma to start from the tonic
            val scaleChroma = TonesArray.rotate(tonicChroma, pitchClasses).joinToString("")

            // Find exact matches in the dictionary
            val match = ScaleTypeDictionary.all().find { it.chroma == scaleChroma }

            val results = mutableListOf<String>()
            if (match != null) {
                results.add("${tonicNote.name} ${match.name}")
            }

            if (matchExact) {
                return results
            }

            // Find extended matches (scales that contain these notes)
            extended(scaleChroma).forEach { scaleName ->
                results.add("${tonicNote.name} $scaleName")
            }

            return results.distinct()
        }

        /**
         * Get all scales names that are a superset of the given one.
         */
        fun extended(name: String): List<String> {
            val chroma = if (PcSet.isChroma(name)) name else get(name).chroma
            if (chroma == "000000000000") return emptyList()

            val isSuperset = PcSet.isSupersetOf(chroma)
            return ScaleTypeDictionary.all()
                .filter { isSuperset(it.chroma) }
                .map { it.name }
        }

        /**
         * Find all scales names that are a subset of the given one.
         */
        fun reduced(name: String): List<String> {
            val isSubset = PcSet.isSubsetOf(get(name).chroma)
            return ScaleTypeDictionary.all()
                .filter { isSubset(it.chroma) }
                .map { it.name }
        }

        /**
         * Given an array of notes, return the scale: a pitch class set starting from the first note.
         */
        fun notes(notes: List<String>): List<String> {
            val pcset = notes.map { Note.get(it).pc }.filter { it.isNotEmpty() }
            if (pcset.isEmpty()) return emptyList()

            val tonic = pcset[0]
            val sortedPcs = pcset.distinct().sortedBy { Note.get(it).chroma }

            val tonicIndex = sortedPcs.indexOf(tonic)
            return TonesArray.rotate(tonicIndex, sortedPcs)
        }

        /**
         * Find mode names of a scale.
         */
        fun modeNames(name: String): List<Pair<String, String>> {
            val s = get(name)
            if (s.empty) return emptyList()

            val tonics = if (s.tonic != null) s.notes else s.intervals
            return PcSet.modes(s.chroma)
                .mapIndexed { i, chroma ->
                    val modeName = ScaleTypeDictionary.get(chroma).name
                    if (modeName.isNotEmpty()) Pair(tonics[i], modeName) else null
                }
                .filterNotNull()
        }

        /**
         * Returns a function to get a note name from a scale by a note or midi number.
         */
        fun getNoteNameOf(scale: List<String>): (Any) -> String? {
            val notes = notes(scale)
            val chromas = notes.map { Note.get(it).chroma }

            return { noteOrMidi ->
                val currNote = when (noteOrMidi) {
                    is Int -> Note.get(Note.fromMidi(noteOrMidi))
                    is String -> Note.get(noteOrMidi)
                    is Note -> noteOrMidi
                    else -> Note.NoNote
                }
                val height = currNote.height

                if (height == -1) null
                else {
                    val chroma = (height % 12 + 12) % 12
                    val position = chromas.indexOf(chroma)
                    if (position == -1) null
                    else {
                        val nameWithOctave = Note.enharmonic(currNote.name, notes[position])
                        if (currNote.oct == null) Note.get(nameWithOctave).pc else nameWithOctave
                    }
                }
            }
        }

        /**
         * Returns a function to get a note name from a scale name by a note or midi number.
         */
        fun getNoteNameOf(scaleName: String): (Any) -> String? {
            val s = get(scaleName)
            val notes = s.notes.ifEmpty { emptyList() }
            val chromas = notes.map { Note.get(it).chroma }

            return { noteOrMidi ->
                val currNote = when (noteOrMidi) {
                    is Int -> Note.get(Note.fromMidi(noteOrMidi))
                    is String -> Note.get(noteOrMidi)
                    is Note -> noteOrMidi
                    else -> Note.NoNote
                }
                val height = currNote.height

                if (height == -1 || notes.isEmpty()) null
                else {
                    val chroma = (height % 12 + 12) % 12
                    val position = chromas.indexOf(chroma)
                    if (position == -1) null
                    else {
                        val nameWithOctave = Note.enharmonic(currNote.name, notes[position])
                        if (currNote.oct == null) Note.get(nameWithOctave).pc else nameWithOctave
                    }
                }
            }
        }

        /**
         * Returns a range of notes from a scale.
         */
        fun rangeOfScale(scale: List<String>): (String, String) -> List<String> {
            val getName = getNoteNameOf(scale)
            return { fromNote, toNote ->
                val from = Note.get(fromNote).height
                val to = Note.get(toNote).height

                if (from == -1 || to == -1) {
                    emptyList()
                } else {
                    TonesArray.range(from, to).mapNotNull { getName(it) }
                }
            }
        }

        /**
         * Returns a range of notes from a scale name.
         */
        fun rangeOfScale(scaleName: String): (String, String) -> List<String> {
            val getName = getNoteNameOf(scaleName)

            return { fromNote, toNote ->
                val from = Note.get(fromNote).height
                val to = Note.get(toNote).height

                if (from == -1 || to == -1) {
                    emptyList()
                } else {
                    TonesArray.range(from, to).mapNotNull { getName(it) }
                }
            }
        }

        /**
         * Returns a function to get a note name from the scale degree.
         */
        fun degrees(scaleName: String): (Int) -> String {
            val s = get(scaleName)
            val transposer = Distance.tonicIntervalsTransposer(s.intervals, s.tonic)

            return { degree ->
                if (degree != 0) transposer(if (degree > 0) degree - 1 else degree) else ""
            }
        }

        /**
         * Same as degrees but with 0-based index.
         */
        fun steps(scaleName: String): (Int) -> String {
            val s = get(scaleName)
            return Distance.tonicIntervalsTransposer(s.intervals, s.tonic)
        }

        /**
         * Find all chords that fits a given scale.
         */
        fun chords(scaleName: String): List<String> {
            val s = get(scaleName)
            val chroma = if (s.empty) ScaleTypeDictionary.get(scaleName).chroma else s.chroma
            if (chroma == "000000000000") return emptyList()

            return ChordTypeDictionary.all()
                .filter { PcSet.isSubsetOf(chroma)(it.chroma) }
                .map { it.aliases.firstOrNull() ?: "" }
                .filter { it.isNotEmpty() }
        }
    }
}
