package io.peekandpoke.klang.tones.scale

import io.peekandpoke.klang.tones.pcset.PcSet
import io.peekandpoke.klang.tones.scale.ScaleType.Companion.NoScaleType

/**
 * Describes a scale formula independent of any tonic — the "type" half of a [Scale].
 *
 * A ScaleType carries the interval structure (e.g. `["1P","2M","3M","4P","5P","6M","7M"]`
 * for major) together with a binary chroma representation and set-theoretic properties
 * derived from the underlying [PcSet].
 *
 * Instances are read-only singletons managed by [ScaleTypeDictionary].
 */
data class ScaleType(
    /** The pitch-class set encoding of this scale's interval structure. */
    val pcset: PcSet,
    /** Canonical name, e.g. `"major"`, `"minor pentatonic"`, `"whole tone"`. */
    val name: String,
    /** Alternative names, e.g. `["ionian"]` for major. */
    val aliases: List<String>,
    /** Interval names from the tonic, e.g. `["1P","2M","3M","4P","5P","6M","7M"]`. */
    val intervals: List<String>,
) {
    /** `true` for the sentinel [NoScaleType]. */
    val empty: Boolean get() = pcset.empty

    /** Fort set-class number (numeric encoding of the pitch-class set). */
    val setNum: Int get() = pcset.setNum

    /** 12-character binary string, `1` = pitch class present, starting from C. */
    val chroma: String get() = pcset.chroma

    /** Most compact rotation of [chroma] — used for equivalence comparisons across transpositions. */
    val normalized: String get() = pcset.normalized

    companion object {
        /** Sentinel for unresolved / empty scale types. */
        val NoScaleType = ScaleType(
            pcset = PcSet.EmptyPcSet,
            name = "",
            aliases = emptyList(),
            intervals = emptyList()
        )
    }
}
