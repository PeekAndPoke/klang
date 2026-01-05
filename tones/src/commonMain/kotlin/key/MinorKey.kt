package io.peekandpoke.klang.tones.key

/**
 * Properties of a minor key.
 */
data class MinorKey(
    /** The type of the key (minor) */
    override val type: String = "minor",
    /** The tonic of the key */
    override val tonic: String,
    /** The number of sharps (positive) or flats (negative) */
    override val alteration: Int,
    /** The key signature (accidentals) */
    override val keySignature: String,
    /** The relative major key tonic */
    val relativeMajor: String,
    /** The natural minor scale properties */
    val natural: KeyScale,
    /** The harmonic minor scale properties */
    val harmonic: KeyScale,
    /** The melodic minor scale properties */
    val melodic: KeyScale,
) : Key {
    companion object {
        /** An empty minor key */
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
