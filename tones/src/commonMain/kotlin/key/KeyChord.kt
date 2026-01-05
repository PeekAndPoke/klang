package io.peekandpoke.klang.tones.key

/**
 * Represents a chord in a key with its roles.
 */
data class KeyChord(
    /** The name of the chord */
    val name: String,
    /** The harmonic roles of the chord in the key */
    val roles: List<String>,
)
