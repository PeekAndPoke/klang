package io.peekandpoke.klang.tones.key

import io.peekandpoke.klang.tones.distance.transpose
import io.peekandpoke.klang.tones.distance.transposeFifths
import io.peekandpoke.klang.tones.note.accToAlt
import io.peekandpoke.klang.tones.note.altToAcc
import io.peekandpoke.klang.tones.note.note
import io.peekandpoke.klang.tones.roman.romanNumeral

/**
 * Properties of a key scale.
 */
data class KeyScale(
    val tonic: String,
    val grades: List<String>,
    val intervals: List<String>,
    val scale: List<String>,
    val triads: List<String>,
    val chords: List<String>,
    val chordsHarmonicFunction: List<String>,
    val chordScales: List<String>,
    val secondaryDominants: List<String>,
    val secondaryDominantSupertonics: List<String>,
    val substituteDominants: List<String>,
    val substituteDominantSupertonics: List<String>,
) {
    /** Deprecated: use secondaryDominantSupertonics */
    val secondaryDominantsMinorRelative: List<String> get() = secondaryDominantSupertonics

    /** Deprecated: use substituteDominantSupertonics */
    val substituteDominantsMinorRelative: List<String> get() = substituteDominantSupertonics

    companion object {
        val NoKeyScale = KeyScale(
            tonic = "",
            grades = emptyList(),
            intervals = emptyList(),
            scale = emptyList(),
            triads = emptyList(),
            chords = emptyList(),
            chordsHarmonicFunction = emptyList(),
            chordScales = emptyList(),
            secondaryDominants = emptyList(),
            secondaryDominantSupertonics = emptyList(),
            substituteDominants = emptyList(),
            substituteDominantSupertonics = emptyList()
        )
    }
}

/**
 * Base interface for Major and Minor keys.
 */
interface Key {
    val type: String
    val tonic: String
    val alteration: Int
    val keySignature: String
}

/**
 * Properties of a major key.
 */
data class MajorKey(
    override val type: String = "major",
    override val tonic: String,
    override val alteration: Int,
    override val keySignature: String,
    val minorRelative: String,
    val grades: List<String>,
    val intervals: List<String>,
    val scale: List<String>,
    val triads: List<String>,
    val chords: List<String>,
    val chordsHarmonicFunction: List<String>,
    val chordScales: List<String>,
    val secondaryDominants: List<String>,
    val secondaryDominantSupertonics: List<String>,
    val substituteDominants: List<String>,
    val substituteDominantSupertonics: List<String>,
) : Key {
    /** Deprecated: use secondaryDominantSupertonics */
    val secondaryDominantsMinorRelative: List<String> get() = secondaryDominantSupertonics

    /** Deprecated: use substituteDominantSupertonics */
    val substituteDominantsMinorRelative: List<String> get() = substituteDominantSupertonics

    companion object {
        val NoMajorKey = MajorKey(
            tonic = "",
            alteration = 0,
            keySignature = "",
            minorRelative = "",
            grades = emptyList(),
            intervals = emptyList(),
            scale = emptyList(),
            triads = emptyList(),
            chords = emptyList(),
            chordsHarmonicFunction = emptyList(),
            chordScales = emptyList(),
            secondaryDominants = emptyList(),
            secondaryDominantSupertonics = emptyList(),
            substituteDominants = emptyList(),
            substituteDominantSupertonics = emptyList()
        )
    }
}

/**
 * Properties of a minor key.
 */
data class MinorKey(
    override val type: String = "minor",
    override val tonic: String,
    override val alteration: Int,
    override val keySignature: String,
    val relativeMajor: String,
    val natural: KeyScale,
    val harmonic: KeyScale,
    val melodic: KeyScale,
) : Key {
    companion object {
        val NoMinorKey = MinorKey(
            tonic = "",
            alteration = 0,
            keySignature = "",
            relativeMajor = "",
            natural = KeyScale.NoKeyScale,
            harmonic = KeyScale.NoKeyScale,
            melodic = KeyScale.NoKeyScale
        )
    }
}

/**
 * Represents a chord in a key with its roles.
 */
data class KeyChord(
    val name: String,
    val roles: List<String>,
)

private fun mapScaleToType(scale: List<String>, list: List<String>, sep: String = ""): List<String> =
    list.mapIndexed { i, type -> if (type.isEmpty()) scale[i] else "${scale[i]}$sep$type" }

private fun supertonics(dominants: List<String>, targetTriads: List<String>): List<String> =
    dominants.mapIndexed { index, chord ->
        if (chord.isEmpty()) ""
        else {
            val domRoot = chord.dropLast(1)
            val minorRoot = transpose(domRoot, "5P")
            val target = targetTriads[index]
            val isMinor = target.endsWith("m")
            if (isMinor) "${minorRoot}m7" else "${minorRoot}m7b5"
        }
    }

private fun keyScaleFactory(
    grades: List<String>,
    triads: List<String>,
    chordTypes: List<String>,
    harmonicFunctions: List<String>,
    chordScales: List<String>,
): (String) -> KeyScale {
    return { tonic ->
        val intervals = grades.map { romanNumeral(it).interval }
        val scale = intervals.map { transpose(tonic, it) }
        val chords = mapScaleToType(scale, chordTypes)
        val secondaryDominants = scale
            .map { transpose(it, "5P") }
            .map { note ->
                // A secondary dominant is a V chord which:
                // 1. is not diatonic to the key,
                // 2. it must have a diatonic root.
                if (scale.contains(note) && !chords.contains(note + "7")) note + "7" else ""
            }

        val secondaryDominantSupertonics = supertonics(secondaryDominants, triads)
        val substituteDominants = secondaryDominants.map { chord ->
            if (chord.isEmpty()) ""
            else {
                val domRoot = chord.dropLast(1)
                val subRoot = transpose(domRoot, "5d")
                subRoot + "7"
            }
        }
        val substituteDominantSupertonics = supertonics(substituteDominants, triads)

        KeyScale(
            tonic = tonic,
            grades = grades,
            intervals = intervals,
            scale = scale,
            triads = mapScaleToType(scale, triads),
            chords = chords,
            chordsHarmonicFunction = harmonicFunctions,
            chordScales = mapScaleToType(scale, chordScales, " "),
            secondaryDominants = secondaryDominants,
            secondaryDominantSupertonics = secondaryDominantSupertonics,
            substituteDominants = substituteDominants,
            substituteDominantSupertonics = substituteDominantSupertonics
        )
    }
}

private val MajorScale = keyScaleFactory(
    "I II III IV V VI VII".split(" "),
    " m m   m dim".split(" "),
    "maj7 m7 m7 maj7 7 m7 m7b5".split(" "),
    "T SD T SD D T D".split(" "),
    "major,dorian,phrygian,lydian,mixolydian,minor,locrian".split(",")
)

private val NaturalScale = keyScaleFactory(
    "I II bIII IV V bVI bVII".split(" "),
    "m dim  m m  ".split(" "),
    "m7 m7b5 maj7 m7 m7 maj7 7".split(" "),
    "T SD T SD D SD SD".split(" "),
    "minor,locrian,major,dorian,phrygian,lydian,mixolydian".split(",")
)

private val HarmonicScale = keyScaleFactory(
    "I II bIII IV V bVI VII".split(" "),
    "m dim aug m   dim".split(" "),
    "mMaj7 m7b5 +maj7 m7 7 maj7 o7".split(" "),
    "T SD T SD D SD D".split(" "),
    "harmonic minor,locrian 6,major augmented,lydian diminished,phrygian dominant,lydian #9,ultralocrian".split(",")
)

private val MelodicScale = keyScaleFactory(
    "I II bIII IV V VI VII".split(" "),
    "m m aug   dim dim".split(" "),
    "m6 m7 +maj7 7 7 m7b5 m7b5".split(" "),
    "T SD T SD D  ".split(" "),
    "melodic minor,dorian b2,lydian augmented,lydian dominant,mixolydian b6,locrian #2,altered".split(",")
)

private fun distInFifths(from: String, to: String): Int {
    val f = note(from)
    val t = note(to)
    val fCoord = f.coord ?: return 0
    val tCoord = t.coord ?: return 0

    val fFifths = when (fCoord) {
        is io.peekandpoke.klang.tones.pitch.PitchCoordinates.PitchClass -> fCoord.fifths
        is io.peekandpoke.klang.tones.pitch.PitchCoordinates.Note -> fCoord.fifths
        else -> 0
    }
    val tFifths = when (tCoord) {
        is io.peekandpoke.klang.tones.pitch.PitchCoordinates.PitchClass -> tCoord.fifths
        is io.peekandpoke.klang.tones.pitch.PitchCoordinates.Note -> tCoord.fifths
        else -> 0
    }

    return tFifths - fFifths
}

/**
 * Get major key properties for a given tonic.
 */
fun majorKey(tonic: String): MajorKey {
    val pc = note(tonic).pc
    if (pc.isEmpty()) return MajorKey.NoMajorKey

    val ks = MajorScale(pc)
    val alteration = distInFifths("C", pc)

    return MajorKey(
        tonic = pc,
        alteration = alteration,
        keySignature = altToAcc(alteration),
        minorRelative = transpose(pc, "-3m"),
        grades = ks.grades,
        intervals = ks.intervals,
        scale = ks.scale,
        triads = ks.triads,
        chords = ks.chords,
        chordsHarmonicFunction = ks.chordsHarmonicFunction,
        chordScales = ks.chordScales,
        secondaryDominants = ks.secondaryDominants,
        secondaryDominantSupertonics = ks.secondaryDominantSupertonics,
        substituteDominants = ks.substituteDominants,
        substituteDominantSupertonics = ks.substituteDominantSupertonics
    )
}

/**
 * Get minor key properties for a given tonic.
 */
fun minorKey(tnc: String): MinorKey {
    val pc = note(tnc).pc
    if (pc.isEmpty()) return MinorKey.NoMinorKey

    val alteration = distInFifths("C", pc) - 3
    return MinorKey(
        tonic = pc,
        alteration = alteration,
        keySignature = altToAcc(alteration),
        relativeMajor = transpose(pc, "3m"),
        natural = NaturalScale(pc),
        harmonic = HarmonicScale(pc),
        melodic = MelodicScale(pc)
    )
}

/**
 * Get a list of available chords for a given major key.
 */
fun majorKeyChords(tonic: String): List<KeyChord> {
    val key = majorKey(tonic)
    val chords = mutableListOf<KeyChord>()

    fun updateChord(name: String, newRole: String) {
        if (name.isEmpty()) return
        val existingIndex = chords.indexOfFirst { it.name == name }
        if (existingIndex == -1) {
            chords.add(KeyChord(name, if (newRole.isNotEmpty()) listOf(newRole) else emptyList()))
        } else {
            val existing = chords[existingIndex]
            if (newRole.isNotEmpty() && !existing.roles.contains(newRole)) {
                chords[existingIndex] = existing.copy(roles = existing.roles + newRole)
            }
        }
    }

    key.chords.forEachIndexed { index, chordName ->
        updateChord(chordName, key.chordsHarmonicFunction.getOrElse(index) { "" })
    }
    key.secondaryDominants.forEachIndexed { index, chordName ->
        updateChord(chordName, "V/${key.grades[index]}")
    }
    key.secondaryDominantSupertonics.forEachIndexed { index, chordName ->
        updateChord(chordName, "ii/${key.grades[index]}")
    }
    key.substituteDominants.forEachIndexed { index, chordName ->
        updateChord(chordName, "subV/${key.grades[index]}")
    }
    key.substituteDominantSupertonics.forEachIndexed { index, chordName ->
        updateChord(chordName, "subii/${key.grades[index]}")
    }

    return chords
}

/**
 * Get a list of available chords for a given minor key.
 */
fun minorKeyChords(tonic: String): List<KeyChord> {
    val key = minorKey(tonic)
    val chords = mutableListOf<KeyChord>()

    keyChordsOf(key.natural, chords)
    keyChordsOf(key.harmonic, chords)
    keyChordsOf(key.melodic, chords)
    return chords
}

private fun keyChordsOf(ks: KeyScale, chords: MutableList<KeyChord>) {
    fun updateChord(name: String, newRole: String) {
        if (name.isEmpty()) return
        val existingIndex = chords.indexOfFirst { it.name == name }
        if (existingIndex == -1) {
            chords.add(KeyChord(name, if (newRole.isNotEmpty()) listOf(newRole) else emptyList()))
        } else {
            val existing = chords[existingIndex]
            if (newRole.isNotEmpty() && !existing.roles.contains(newRole)) {
                chords[existingIndex] = existing.copy(roles = existing.roles + newRole)
            }
        }
    }

    ks.chords.forEachIndexed { index, chordName ->
        updateChord(chordName, ks.chordsHarmonicFunction.getOrElse(index) { "" })
    }
    ks.secondaryDominants.forEachIndexed { index, chordName ->
        updateChord(chordName, "V/${ks.grades[index]}")
    }
    ks.secondaryDominantSupertonics.forEachIndexed { index, chordName ->
        updateChord(chordName, "ii/${ks.grades[index]}")
    }
    ks.substituteDominants.forEachIndexed { index, chordName ->
        updateChord(chordName, "subV/${ks.grades[index]}")
    }
    ks.substituteDominantSupertonics.forEachIndexed { index, chordName ->
        updateChord(chordName, "subii/${ks.grades[index]}")
    }
}

/**
 * Given a key signature, returns the tonic of the major key.
 */
fun majorTonicFromKeySignature(sig: Any?): String? {
    return when (sig) {
        is Int -> transposeFifths("C", sig)
        is String -> {
            if (sig.matches(Regex("^[b#]+$"))) {
                transposeFifths("C", accToAlt(sig))
            } else {
                null
            }
        }

        else -> null
    }
}
