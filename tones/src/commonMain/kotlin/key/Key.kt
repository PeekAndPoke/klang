package io.peekandpoke.klang.tones.key

import io.peekandpoke.klang.tones.distance.Distance
import io.peekandpoke.klang.tones.note.Note
import io.peekandpoke.klang.tones.pitch.PitchCoordinates
import io.peekandpoke.klang.tones.roman.RomanNumeral

/**
 * Base interface for Major and Minor keys.
 */
interface Key {
    /** The type of the key (major or minor) */
    val type: String

    /** The tonic of the key */
    val tonic: String

    /** The number of sharps or flats */
    val alteration: Int

    /** The key signature */
    val keySignature: String

    companion object {
        /**
         * Get major key properties for a given tonic.
         */
        fun majorKey(tonic: String): MajorKey {
            val pc = Note.get(tonic).pc
            if (pc.isEmpty()) return MajorKey.NoMajorKey

            val ks = MajorScale(pc)
            val alteration = distInFifths("C", pc)

            return MajorKey(
                tonic = pc,
                alteration = alteration,
                keySignature = Note.altToAcc(alteration),
                minorRelative = Distance.transpose(pc, "-3m"),
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
            val pc = Note.get(tnc).pc
            if (pc.isEmpty()) return MinorKey.NoMinorKey

            val alteration = distInFifths("C", pc) - 3
            return MinorKey(
                tonic = pc,
                alteration = alteration,
                keySignature = Note.altToAcc(alteration),
                relativeMajor = Distance.transpose(pc, "3m"),
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

        // Collects chords and their roles from a given KeyScale
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
         * Given a key signature as number of fifths, returns the tonic of the major key.
         */
        fun majorTonicFromKeySignature(fifths: Int): String {
            return Distance.transposeFifths("C", fifths)
        }

        /**
         * Given a key signature as accidental string (e.g., "###" or "bbb"), returns the tonic of the major key.
         */
        fun majorTonicFromKeySignature(signature: String): String? {
            return if (signature.matches(Regex("^[b#]+$"))) {
                Distance.transposeFifths("C", Note.accToAlt(signature))
            } else {
                null
            }
        }

        // Maps scale notes to chord types
        private fun mapScaleToType(scale: List<String>, list: List<String>, sep: String = ""): List<String> =
            list.mapIndexed { i, type -> if (type.isEmpty()) scale[i] else "${scale[i]}$sep$type" }

        // Calculates secondary and substitute dominant supertonics (ii-V relations)
        private fun supertonics(dominants: List<String>, targetTriads: List<String>): List<String> =
            dominants.mapIndexed { index, chord ->
                if (chord.isEmpty()) ""
                else {
                    val domRoot = chord.dropLast(1)
                    val minorRoot = Distance.transpose(domRoot, "5P")
                    val target = targetTriads[index]
                    val isMinor = target.endsWith("m")
                    if (isMinor) "${minorRoot}m7" else "${minorRoot}m7b5"
                }
            }

        // Factory function to create a KeyScale generator for a given scale definition
        private fun keyScaleFactory(
            grades: List<String>,
            triads: List<String>,
            chordTypes: List<String>,
            harmonicFunctions: List<String>,
            chordScales: List<String>,
        ): (String) -> KeyScale {
            return { tonic ->
                val intervals = grades.map { RomanNumeral.get(it).interval }
                val scale = intervals.map { Distance.transpose(tonic, it) }
                val chords = mapScaleToType(scale, chordTypes)
                val secondaryDominants = scale
                    .map { Distance.transpose(it, "5P") }
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
                        val subRoot = Distance.transpose(domRoot, "5d")
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

        // Major scale generator
        private val MajorScale = keyScaleFactory(
            "I II III IV V VI VII".split(" "),
            " m m   m dim".split(" "),
            "maj7 m7 m7 maj7 7 m7 m7b5".split(" "),
            "T SD T SD D T D".split(" "),
            "major,dorian,phrygian,lydian,mixolydian,minor,locrian".split(",")
        )

        // Natural minor scale generator
        private val NaturalScale = keyScaleFactory(
            "I II bIII IV V bVI bVII".split(" "),
            "m dim  m m  ".split(" "),
            "m7 m7b5 maj7 m7 m7 maj7 7".split(" "),
            "T SD T SD D SD SD".split(" "),
            "minor,locrian,major,dorian,phrygian,lydian,mixolydian".split(",")
        )

        // Harmonic minor scale generator
        private val HarmonicScale = keyScaleFactory(
            "I II bIII IV V bVI VII".split(" "),
            "m dim aug m   dim".split(" "),
            "mMaj7 m7b5 +maj7 m7 7 maj7 o7".split(" "),
            "T SD T SD D SD D".split(" "),
            "harmonic minor,locrian 6,major augmented,lydian diminished,phrygian dominant,lydian #9,ultralocrian".split(
                ","
            )
        )

        // Melodic minor scale generator
        private val MelodicScale = keyScaleFactory(
            "I II bIII IV V VI VII".split(" "),
            "m m aug   dim dim".split(" "),
            "m6 m7 +maj7 7 7 m7b5 m7b5".split(" "),
            "T SD T SD D  ".split(" "),
            "melodic minor,dorian b2,lydian augmented,lydian dominant,mixolydian b6,locrian #2,altered".split(",")
        )

        // Calculates the distance in fifths between two notes
        @Suppress("SameParameterValue")
        private fun distInFifths(from: String, to: String): Int {
            val f = Note.get(from)
            val t = Note.get(to)
            val fCoord = f.coord ?: return 0
            val tCoord = t.coord ?: return 0

            val fFifths = when (fCoord) {
                is PitchCoordinates.PitchClass -> fCoord.fifths
                is PitchCoordinates.Note -> fCoord.fifths
                else -> 0
            }
            val tFifths = when (tCoord) {
                is PitchCoordinates.PitchClass -> tCoord.fifths
                is PitchCoordinates.Note -> tCoord.fifths
                else -> 0
            }

            return tFifths - fFifths
        }
    }
}
