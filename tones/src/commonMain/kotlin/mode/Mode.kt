package io.peekandpoke.klang.tones.mode

import io.peekandpoke.klang.tones.distance.Distance
import io.peekandpoke.klang.tones.interval.Interval
import io.peekandpoke.klang.tones.pcset.PcSet
import io.peekandpoke.klang.tones.pitch.NamedPitch
import io.peekandpoke.klang.tones.utils.TonesArray

/**
 * Represents a musical mode.
 */
data class Mode(
    /** The pitch class set of the mode. */
    val pcSet: PcSet,
    /** The name of the mode. */
    val name: String,
    /** The number of the mode (0-indexed) relative to the parent scale (e.g., Ionian = 0, Dorian = 1, etc.). */
    val modeNum: Int,
    /** The number of fifths from C to the mode root (Ionian = 0, Dorian = 2, Lydian = -1, etc.). */
    val alt: Int,
    /** The triad chord type of the mode. */
    val triad: String,
    /** The seventh chord type of the mode. */
    val seventh: String,
    /** The aliases of the mode. */
    val aliases: List<String>,
    /** The chroma of the mode. */
    val chroma: String,
    /** The normalized chroma of the mode. */
    val normalized: String,
    /** The intervals of the mode. */
    val intervals: List<String>,
) {
    /** Whether the mode is empty. */
    val empty: Boolean get() = pcSet.empty

    /** The decimal representation of the chroma bitmask (Bit 11 is the root). */
    val setNum: Int get() = pcSet.setNum

    companion object {
        /** An empty mode. */
        val NoMode = Mode(
            pcSet = PcSet.EmptyPcSet,
            name = "",
            modeNum = -1,
            alt = 0,
            triad = "",
            seventh = "",
            aliases = emptyList(),
            chroma = "",
            normalized = "",
            intervals = emptyList()
        )

        /**
         * Returns a [Mode] by name.
         */
        fun get(name: String): Mode = ModeDictionary.get(name)

        /**
         * Returns the [Mode] itself.
         */
        fun get(mode: Mode): Mode = mode

        /**
         * Converts a [NamedPitch] to a [Mode].
         */
        fun get(named: NamedPitch): Mode = ModeDictionary.get(named)

        /**
         * Returns a list of all mode names.
         */
        fun names(): List<String> = ModeDictionary.names()

        /**
         * Returns a list of all modes.
         */
        fun all(): List<Mode> = ModeDictionary.all()

        /**
         * Returns the notes of a mode given a tonic.
         */
        fun notes(modeName: String, tonic: String): List<String> {
            val m = get(modeName)
            if (m.empty) return emptyList()
            return m.intervals.map { Distance.transpose(tonic, it) }
        }

        /**
         * Returns the triads of a mode given a tonic.
         */
        fun triads(modeName: String, tonic: String): List<String> {
            val m = get(modeName)
            if (m.empty) return emptyList()
            val triadTypes = TonesArray.rotate(m.modeNum, listOf("", "m", "m", "", "", "m", "dim"))
            val tonics = m.intervals.map { Distance.transpose(tonic, it) }
            return triadTypes.mapIndexed { i, type -> tonics[i] + type }
        }

        /**
         * Returns the seventh chords of a mode given a tonic.
         */
        fun seventhChords(modeName: String, tonic: String): List<String> {
            val m = get(modeName)
            if (m.empty) return emptyList()
            val seventhTypes = TonesArray.rotate(m.modeNum, listOf("Maj7", "m7", "m7", "Maj7", "7", "m7", "m7b5"))
            val tonics = m.intervals.map { Distance.transpose(tonic, it) }
            return seventhTypes.mapIndexed { i, type -> tonics[i] + type }
        }

        /**
         * Returns the distance between two modes as an interval name.
         */
        fun distance(destination: String, source: String): String {
            val from = get(source)
            val to = get(destination)
            if (from.empty || to.empty) return ""
            return Interval.simplify(Interval.transposeFifths("1P", to.alt - from.alt))
        }

        /**
         * Returns the relative tonic of a destination mode given a source mode and its tonic.
         */
        fun relativeTonic(destination: String, source: String, tonic: String): String {
            val dist = distance(destination, source)
            if (dist.isEmpty()) return ""
            return Distance.transpose(tonic, dist)
        }
    }
}

