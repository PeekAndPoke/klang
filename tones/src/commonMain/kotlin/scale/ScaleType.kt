package io.peekandpoke.klang.tones.scale

import io.peekandpoke.klang.tones.pcset.PcSet

/**
 * Properties for a scale in the scale dictionary.
 */
data class ScaleType(
    /** The pitch class set of the scale. */
    val pcset: PcSet,
    /** The scale name. */
    val name: String,
    /** Alternative list of names. */
    val aliases: List<String>,
    /** An array of interval names. */
    val intervals: List<String>,
) {
    val empty: Boolean get() = pcset.empty
    val setNum: Int get() = pcset.setNum
    val chroma: String get() = pcset.chroma
    val normalized: String get() = pcset.normalized

    companion object {
        /**
         * Represents an empty or invalid scale type.
         */
        val NoScaleType = ScaleType(
            pcset = PcSet.EmptyPcSet,
            name = "",
            aliases = emptyList(),
            intervals = emptyList()
        )
    }
}
