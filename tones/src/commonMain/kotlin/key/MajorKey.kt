package io.peekandpoke.klang.tones.key

/**
 * Properties of a major key.
 */
data class MajorKey(
    /** The type of the key (major) */
    override val type: String = "major",
    /** The tonic of the key */
    override val tonic: String,
    /** The number of sharps (positive) or flats (negative) */
    override val alteration: Int,
    /** The key signature (accidentals) */
    override val keySignature: String,
    /** The relative minor key tonic */
    val minorRelative: String,
    /** The scale degrees in Roman Numerals */
    val grades: List<String>,
    /** The intervals from the tonic */
    val intervals: List<String>,
    /** The notes in the major scale */
    val scale: List<String>,
    /** The triads of the key */
    val triads: List<String>,
    /** The 7th chords of the key */
    val chords: List<String>,
    /** The harmonic function of each chord */
    val chordsHarmonicFunction: List<String>,
    /** The default scales for each degree */
    val chordScales: List<String>,
    /** The secondary dominants for each degree */
    val secondaryDominants: List<String>,
    /** The supertonics for the secondary dominants */
    val secondaryDominantSupertonics: List<String>,
    /** The substitute dominants for each degree */
    val substituteDominants: List<String>,
    /** The supertonics for the substitute dominants */
    val substituteDominantSupertonics: List<String>,
) : Key {
    /** Deprecated: use secondaryDominantSupertonics */
    val secondaryDominantsMinorRelative: List<String> get() = secondaryDominantSupertonics

    /** Deprecated: use substituteDominantSupertonics */
    val substituteDominantsMinorRelative: List<String> get() = substituteDominantSupertonics

    companion object {
        /** An empty major key */
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
