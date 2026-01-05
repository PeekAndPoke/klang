package io.peekandpoke.klang.tones.key

/**
 * Properties of a key scale.
 */
data class KeyScale(
    /** The tonic of the key scale */
    val tonic: String,
    /** The scale degrees (grades) in Roman Numerals */
    val grades: List<String>,
    /** The intervals from the tonic */
    val intervals: List<String>,
    /** The notes in the scale */
    val scale: List<String>,
    /** The triads for each degree */
    val triads: List<String>,
    /** The 7th chords for each degree */
    val chords: List<String>,
    /** The harmonic function of each chord (T, SD, D) */
    val chordsHarmonicFunction: List<String>,
    /** The default scales for each chord degree */
    val chordScales: List<String>,
    /** The secondary dominants for each degree */
    val secondaryDominants: List<String>,
    /** The supertonics (ii-V) for the secondary dominants */
    val secondaryDominantSupertonics: List<String>,
    /** The substitute dominants for each degree */
    val substituteDominants: List<String>,
    /** The supertonics (ii-V) for the substitute dominants */
    val substituteDominantSupertonics: List<String>,
) {
    /** Deprecated: use secondaryDominantSupertonics */
    val secondaryDominantsMinorRelative: List<String> get() = secondaryDominantSupertonics

    /** Deprecated: use substituteDominantSupertonics */
    val substituteDominantsMinorRelative: List<String> get() = substituteDominantSupertonics

    companion object {
        /** An empty key scale */
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
