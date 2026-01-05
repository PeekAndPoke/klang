package io.peekandpoke.klang.tones.scale

import io.peekandpoke.klang.tones.collection.range
import io.peekandpoke.klang.tones.collection.rotate
import io.peekandpoke.klang.tones.distance.tonicIntervalsTransposer
import io.peekandpoke.klang.tones.distance.transpose
import io.peekandpoke.klang.tones.note.enharmonic
import io.peekandpoke.klang.tones.note.fromMidi
import io.peekandpoke.klang.tones.note.note
import io.peekandpoke.klang.tones.pcset.*

/**
 * Represents a musical scale.
 */
data class Scale(
    val scaleType: ScaleType,
    val tonic: String?,
    val type: String,
    val notes: List<String>,
) {
    val empty: Boolean get() = scaleType.empty
    val name: String = if (tonic != null && tonic.isNotEmpty()) "$tonic $type" else type
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
    }
}

/**
 * Tokenizes a scale name into [tonic, type].
 */
fun tokenizeScale(name: String): Pair<String, String> {
    if (name.isEmpty()) return Pair("", "")

    val i = name.indexOf(" ")
    if (i < 0) {
        val n = note(name)
        return if (n.empty) Pair("", name.lowercase()) else Pair(n.name, "")
    }

    val tonicPart = name.substring(0, i)
    val tonic = note(tonicPart)

    return if (tonic.empty) {
        val n = note(name)
        if (n.empty) Pair("", name.lowercase()) else Pair(n.name, "")
    } else {
        val type = name.substring(tonic.name.length).trim().lowercase()
        Pair(tonic.name, type)
    }
}

/**
 * Get a Scale from a scale name.
 */
fun getScale(src: String): Scale {
    val (tonicName, typeName) = tokenizeScale(src)
    val tonic = note(tonicName).name
    val st = ScaleTypeDictionary.get(typeName)

    if (st.empty) {
        return Scale.NoScale
    }

    val type = st.name
    val notes = if (tonic.isNotEmpty()) {
        st.intervals.map { transpose(tonic, it) }
    } else {
        emptyList()
    }

    return Scale(st, if (tonic.isEmpty()) null else tonic, type, notes)
}

/**
 * Given a list of notes, detect the scales that match.
 */
fun detectScale(
    notes: List<String>,
    tonic: String? = null,
    matchExact: Boolean = false,
): List<String> {
    val notesChroma = chroma(notes)
    val tonicNote = note(tonic ?: notes.firstOrNull() ?: "")
    val tonicChroma = if (tonicNote.chroma != -1) tonicNote.chroma else return emptyList()

    val pitchClasses = notesChroma.split("").filter { it.isNotEmpty() }.toMutableList()
    pitchClasses[tonicChroma] = "1"

    val scaleChroma = rotate(tonicChroma, pitchClasses).joinToString("")
    val match = ScaleTypeDictionary.all().find { it.chroma == scaleChroma }

    val results = mutableListOf<String>()
    if (match != null) {
        results.add("${tonicNote.name} ${match.name}")
    }

    if (matchExact) {
        return results
    }

    extendedScales(scaleChroma).forEach { scaleName ->
        results.add("${tonicNote.name} $scaleName")
    }

    return results.distinct()
}

/**
 * Get all scales names that are a superset of the given one.
 */
fun extendedScales(name: String): List<String> {
    val chroma = if (isChroma(name)) name else getScale(name).chroma
    if (chroma == "000000000000") return emptyList()

    val isSuperset = isSupersetOf(chroma)
    return ScaleTypeDictionary.all()
        .filter { isSuperset(it.chroma) }
        .map { it.name }
}

/**
 * Find all scales names that are a subset of the given one.
 */
fun reducedScales(name: String): List<String> {
    val isSubset = isSubsetOf(getScale(name).chroma)
    return ScaleTypeDictionary.all()
        .filter { isSubset(it.chroma) }
        .map { it.name }
}

/**
 * Given an array of notes, return the scale: a pitch class set starting from the first note.
 */
fun scaleNotes(notes: List<String>): List<String> {
    val pcset = notes.map { note(it).pc }.filter { it.isNotEmpty() }
    if (pcset.isEmpty()) return emptyList()

    val tonic = pcset[0]
    val scale = pcset.distinct()
        .sorted() // Original TS uses sortedUniqNames, need to check if alphabetical is enough or if it needs tonal sorting
    // Re-evaluating sortedUniqNames: it usually sorts by chroma but starts from C.
    // Let's use a simpler approach for now or port sortedUniqNames if needed.
    // Actually tonaljs sortedUniqNames sorts notes by height/chroma.
    val sortedPcs = pcset.distinct().sortedBy { note(it).chroma }

    val tonicIndex = sortedPcs.indexOf(tonic)
    return rotate(tonicIndex, sortedPcs)
}

/**
 * Find mode names of a scale.
 */
fun modeNames(name: String): List<Pair<String, String>> {
    val s = getScale(name)
    if (s.empty) return emptyList()

    val tonics = if (s.tonic != null) s.notes else s.intervals
    return modes(s.chroma)
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
    val notes = scaleNotes(scale)
    val chromas = notes.map { note(it).chroma }

    return { noteOrMidi ->
        val currNote = when (noteOrMidi) {
            is Int -> note(fromMidi(noteOrMidi))
            else -> note(noteOrMidi)
        }
        val height = currNote.height

        if (height == -1) null
        else {
            val chroma = (height % 12 + 12) % 12
            val position = chromas.indexOf(chroma)
            if (position == -1) null
            else {
                val nameWithOctave = enharmonic(currNote.name, notes[position])
                if (currNote.oct == null) note(nameWithOctave).pc else nameWithOctave
            }
        }
    }
}

/**
 * Returns a function to get a note name from a scale name by a note or midi number.
 */
fun getNoteNameOf(scaleName: String): (Any) -> String? {
    val s = getScale(scaleName)
    val notes = if (s.notes.isNotEmpty()) s.notes else emptyList()
    val chromas = notes.map { note(it).chroma }

    return { noteOrMidi ->
        val currNote = when (noteOrMidi) {
            is Int -> note(fromMidi(noteOrMidi))
            else -> note(noteOrMidi)
        }
        val height = currNote.height

        if (height == -1 || notes.isEmpty()) null
        else {
            val chroma = (height % 12 + 12) % 12
            val position = chromas.indexOf(chroma)
            if (position == -1) null
            else {
                val nameWithOctave = enharmonic(currNote.name, notes[position])
                if (currNote.oct == null) note(nameWithOctave).pc else nameWithOctave
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
        val from = note(fromNote).height
        val to = note(toNote).height
        if (from == -1 || to == -1) emptyList()
        else {
            range(from, to)
                .map { getName(it) }
                .filterNotNull()
        }
    }
}

/**
 * Returns a range of notes from a scale name.
 */
fun rangeOfScale(scaleName: String): (String, String) -> List<String> {
    val getName = getNoteNameOf(scaleName)
    return { fromNote, toNote ->
        val from = note(fromNote).height
        val to = note(toNote).height
        if (from == -1 || to == -1) emptyList()
        else {
            range(from, to)
                .map { getName(it) }
                .filterNotNull()
        }
    }
}

/**
 * Returns a function to get a note name from the scale degree.
 */
fun scaleDegrees(scaleName: String): (Int) -> String {
    val s = getScale(scaleName)
    val transposer = tonicIntervalsTransposer(s.intervals, s.tonic)
    return { degree ->
        if (degree != 0) transposer(if (degree > 0) degree - 1 else degree) else ""
    }
}

/**
 * Same as scaleDegrees but with 0-based index.
 */
fun scaleSteps(scaleName: String): (Int) -> String {
    val s = getScale(scaleName)
    return tonicIntervalsTransposer(s.intervals, s.tonic)
}
