@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.tones.scale

import io.peekandpoke.klang.tones.chord.ChordTypeDictionary
import io.peekandpoke.klang.tones.distance.Distance
import io.peekandpoke.klang.tones.note.Note
import io.peekandpoke.klang.tones.pcset.PcSet
import io.peekandpoke.klang.tones.scale.Scale.Companion.NoScale
import io.peekandpoke.klang.tones.scale.Scale.Companion.degrees
import io.peekandpoke.klang.tones.scale.Scale.Companion.getNoteNameOf
import io.peekandpoke.klang.tones.scale.Scale.Companion.notes
import io.peekandpoke.klang.tones.scale.Scale.Companion.rangeOfScale
import io.peekandpoke.klang.tones.utils.TonesArray

/**
 * Represents different types of note input for scale operations.
 */
sealed class NoteInput {
    /** Input by MIDI number */
    data class Midi(val value: Int) : NoteInput()

    /** Input by note name string */
    data class Name(val value: String) : NoteInput()

    /** Input by Note object */
    data class NoteObject(val value: Note) : NoteInput()
}

/**
 * Represents a musical scale — a [ScaleType] anchored to a specific [tonic] note.
 *
 * A scale is the combination of a scale formula (intervals / chroma) and a root note.
 * For example `Scale.get("C4 major")` produces a C major scale rooted at C4 with
 * concrete note names: `["C4", "D4", "E4", "F4", "G4", "A4", "B4"]`.
 *
 * When no tonic is provided the scale carries only the type information (intervals, chroma)
 * and [notes] is empty.
 */
data class Scale(
    /** The underlying scale type (intervals, chroma, name, aliases). */
    val scaleType: ScaleType,
    /** The tonic (root) note including octave, e.g. `"C4"`, or `null` if tonic-less. */
    val tonic: String?,
    /** The scale type name, e.g. `"major"`, `"minor pentatonic"`. */
    val type: String,
    /** Concrete note names derived by transposing each interval from [tonic], e.g. `["C4","D4",…]`. Empty when [tonic] is null. */
    val notes: List<String>,
) {
    /** `true` when this scale could not be resolved (sentinel value). */
    val empty: Boolean get() = scaleType.empty

    /** Full display name: `"<tonic> <type>"`, e.g. `"C4 major"`. Falls back to just [type] when tonic-less. */
    val name: String = if (!tonic.isNullOrEmpty()) "$tonic $type" else type

    /** Numeric set representation of the pitch-class set (Fort number encoding). */
    val setNum: Int get() = scaleType.setNum

    /** 12-character binary chroma string where `1` = pitch class present, e.g. `"101011010101"` for major. */
    val chroma: String get() = scaleType.chroma

    /** Normalized (most compact rotation) chroma — used for equivalence comparisons. */
    val normalized: String get() = scaleType.normalized

    /** Alternative names for this scale type, e.g. `["ionian"]` for major. */
    val aliases: List<String> get() = scaleType.aliases

    /** Interval names from the tonic, e.g. `["1P","2M","3M","4P","5P","6M","7M"]` for major. */
    val intervals: List<String> get() = scaleType.intervals

    companion object {
        /** Sentinel for unresolved / empty scales. */
        val NoScale = Scale(
            scaleType = ScaleType.NoScaleType,
            tonic = null,
            type = "",
            notes = emptyList()
        )

        private val tokenizeCache = mutableMapOf<String, Pair<String, String>>()
        private val scaleCache = mutableMapOf<String, Scale>()

        /**
         * Splits a scale name string into a `(tonic, type)` pair.
         *
         * Examples:
         * - `"C4 major"` → `("C4", "major")`
         * - `"major"` → `("", "major")`
         * - `"C4"` → `("C4", "")`
         *
         * @param name The scale name to tokenize, e.g. `"Db3 minor pentatonic"`.
         * @return Pair of `(tonicName, typeName)`. Either part may be empty.
         */
        fun tokenize(name: String): Pair<String, String> = tokenizeCache.getOrPut(name) {
            if (name.isEmpty()) return@getOrPut Pair("", "")

            val i = name.indexOf(" ")
            if (i < 0) {
                // If no space, check if the whole name is a note.
                // If yes, it's a note (no scale type). If no, it's a scale type (no tonic).
                val n = Note.get(name)
                return@getOrPut if (n.empty) Pair("", name.lowercase()) else Pair(n.name, "")
            }

            // If there's a space, try to parse the first part as a tonic
            val tonicPart = name.substring(0, i)
            val tonic = Note.get(tonicPart)

            if (tonic.empty) {
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
         * Parses a scale name and returns the corresponding [Scale].
         *
         * Accepts formats like `"C4 major"`, `"Db minor pentatonic"`, or `"C:major"`.
         * When the tonic has no explicit octave it defaults to octave 3.
         * Returns [NoScale] if the scale type cannot be found in the dictionary.
         *
         * @param src Scale name string, e.g. `"C4 major"`.
         */
        fun get(src: String): Scale = scaleCache.getOrPut(src) {
            val cleanSrc = src.replace(":", " ")
            val (tonicNameOriginal, typeName) = tokenize(cleanSrc)

            var tonicNote = Note.get(tonicNameOriginal)

            // If tonic is present but has no octave, default to 3 (e.g. "C" -> "C3")
            if (!tonicNote.empty && tonicNote.oct == null) {
                tonicNote = Note.get(tonicNote.pc + "3")
            }

            val tonic = tonicNote.name
            val st = ScaleTypeDictionary.get(typeName)

            // If scale type is not found, return NoScale
            if (st.empty) {
                return@getOrPut NoScale
            }

            val type = st.name
            // Calculate notes if tonic is present
            val notes = if (tonic.isNotEmpty()) {
                st.intervals.map { Distance.transpose(tonic, it) }
            } else {
                emptyList()
            }

            Scale(st, tonic.ifEmpty { null }, type, notes)
        }

        /**
         * Detects scales that match the given set of notes.
         *
         * Returns scale names ordered by match quality: exact matches first, then
         * superset matches (scales that contain all given notes plus more).
         *
         * @param notes List of note names, e.g. `["C4", "D4", "E4", "G4", "A4"]`.
         * @param tonic Optional forced tonic. When `null` the first note is used as tonic.
         * @param matchExact When `true`, only return exact matches (no superset scales).
         * @return Scale names, e.g. `["C major pentatonic", "C major", …]`.
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
         * Returns all scale type names that are a superset of the given scale or chroma.
         *
         * @param name A scale name (e.g. `"C major pentatonic"`) or a 12-char chroma string.
         * @return Type names of scales that contain all pitch classes of the input.
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
         * Returns all scale type names that are a subset of the given scale.
         *
         * @param name A scale name, e.g. `"C major"`.
         * @return Type names of scales whose pitch classes are all contained in the input.
         */
        fun reduced(name: String): List<String> {
            val isSubset = PcSet.isSubsetOf(get(name).chroma)
            return ScaleTypeDictionary.all()
                .filter { isSubset(it.chroma) }
                .map { it.name }
        }

        /**
         * Normalizes a list of note names into a sorted pitch-class set starting from the first note.
         *
         * Duplicates are removed and notes are sorted by chroma, then rotated so the
         * first input note comes first. Octave information is stripped.
         *
         * @param notes Note names, e.g. `["E4", "C4", "G4"]`.
         * @return Pitch classes in scale order, e.g. `["C", "E", "G"]`.
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
         * Returns the modes of the given scale as `(tonic, modeName)` pairs.
         *
         * Each pair maps a scale degree's note to the mode name that starts on that degree.
         * For example `modeNames("C major")` → `[("C","major"), ("D","dorian"), …]`.
         *
         * @param name Scale name, e.g. `"C4 major"`.
         * @return List of `(tonic, modeName)` pairs, one per degree that has a named mode.
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
         * Returns a function that maps a [NoteInput] to its enharmonic name within the given scale.
         *
         * If the input note's pitch class is not in the scale, returns `null`.
         *
         * @param scale List of pitch-class names defining the scale, e.g. `["C", "D", "E", "F", "G", "A", "B"]`.
         * @return Mapping function `(NoteInput) -> String?`.
         */
        fun getNoteNameOf(scale: List<String>): (NoteInput) -> String? {
            val notes = notes(scale)
            val chromas = notes.map { Note.get(it).chroma }

            return { input ->
                val currNote = when (input) {
                    is NoteInput.Midi -> Note.get(Note.fromMidi(input.value))
                    is NoteInput.Name -> Note.get(input.value)
                    is NoteInput.NoteObject -> input.value
                }
                val height = currNote.height

                if (height == -1) {
                    null
                } else {
                    val chroma = (height % 12 + 12) % 12
                    val position = chromas.indexOf(chroma)
                    if (position == -1) {
                        null
                    } else {
                        val nameWithOctave = Note.enharmonic(currNote.name, notes[position])
                        if (currNote.oct == null) Note.get(nameWithOctave).pc else nameWithOctave
                    }
                }
            }
        }

        /**
         * Like [getNoteNameOf] but accepts a scale name instead of a note list.
         *
         * @param scaleName Scale name, e.g. `"C4 major"`.
         * @return Mapping function `(NoteInput) -> String?`.
         */
        fun getNoteNameOf(scaleName: String): (NoteInput) -> String? {
            val s = get(scaleName)
            val notes = s.notes.ifEmpty { emptyList() }
            val chromas = notes.map { Note.get(it).chroma }

            return { input ->
                val currNote = when (input) {
                    is NoteInput.Midi -> Note.get(Note.fromMidi(input.value))
                    is NoteInput.Name -> Note.get(input.value)
                    is NoteInput.NoteObject -> input.value
                }
                val height = currNote.height

                if (height == -1 || notes.isEmpty()) {
                    null
                } else {
                    val chroma = (height % 12 + 12) % 12
                    val position = chromas.indexOf(chroma)
                    if (position == -1) {
                        null
                    } else {
                        val nameWithOctave = Note.enharmonic(currNote.name, notes[position])
                        if (currNote.oct == null) Note.get(nameWithOctave).pc else nameWithOctave
                    }
                }
            }
        }

        /**
         * Returns a function that generates all notes of the given scale within a MIDI range.
         *
         * @param scale List of pitch-class names defining the scale.
         * @return Function `(fromNote, toNote) -> List<String>` producing note names in ascending order.
         */
        fun rangeOfScale(scale: List<String>): (String, String) -> List<String> {
            val getName = getNoteNameOf(scale)
            return { fromNote, toNote ->
                val from = Note.get(fromNote).height
                val to = Note.get(toNote).height

                if (from == -1 || to == -1) {
                    emptyList()
                } else {
                    TonesArray.range(from, to).mapNotNull { getName(NoteInput.Midi(it)) }
                }
            }
        }

        /**
         * Like [rangeOfScale] but accepts a scale name instead of a note list.
         *
         * @param scaleName Scale name, e.g. `"C4 major"`.
         * @return Function `(fromNote, toNote) -> List<String>`.
         */
        fun rangeOfScale(scaleName: String): (String, String) -> List<String> {
            val getName = getNoteNameOf(scaleName)

            return { fromNote, toNote ->
                val from = Note.get(fromNote).height
                val to = Note.get(toNote).height

                if (from == -1 || to == -1) {
                    emptyList()
                } else {
                    TonesArray.range(from, to).mapNotNull { getName(NoteInput.Midi(it)) }
                }
            }
        }

        /**
         * Returns a function that maps a 1-based scale degree to a note name.
         *
         * Degree 1 = tonic, 2 = second, etc. Negative degrees go downward.
         * Degree 0 returns an empty string.
         *
         * @param scaleName Scale name, e.g. `"C4 major"`.
         * @return Function `(degree) -> noteName`.
         */
        fun degrees(scaleName: String): (Int) -> String {
            val s = get(scaleName)
            val transposer = Distance.tonicIntervalsTransposer(s.intervals, s.tonic)

            return { degree ->
                if (degree != 0) transposer(if (degree > 0) degree - 1 else degree) else ""
            }
        }

        /**
         * Like [degrees] but with 0-based indexing (step 0 = tonic, 1 = second, …).
         *
         * @param scaleName Scale name, e.g. `"C4 major"`.
         * @return Function `(step) -> noteName`.
         */
        fun steps(scaleName: String): (Int) -> String {
            val cleanName = scaleName.replace(":", " ")
            val scale = get(cleanName)
            return Distance.tonicIntervalsTransposer(scale.intervals, scale.tonic)
        }

        /**
         * Returns all chord type symbols whose pitch classes are a subset of the given scale.
         *
         * @param scaleName Scale name, e.g. `"C4 major"`.
         * @return Chord symbols, e.g. `["M", "m", "dim", …]`.
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
