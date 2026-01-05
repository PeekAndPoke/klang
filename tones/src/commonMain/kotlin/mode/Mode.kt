package io.peekandpoke.klang.tones.mode

import io.peekandpoke.klang.tones.collection.TonalArray
import io.peekandpoke.klang.tones.distance.Distance
import io.peekandpoke.klang.tones.interval.Interval
import io.peekandpoke.klang.tones.pcset.Pcset
import io.peekandpoke.klang.tones.pitch.NamedPitch
import io.peekandpoke.klang.tones.scale.ScaleTypeDictionary

/**
 * Represents a musical mode.
 */
data class Mode(
    val pcset: Pcset,
    val name: String,
    val modeNum: Int,
    val alt: Int,
    val triad: String,
    val seventh: String,
    val aliases: List<String>,
    val chroma: String,
    val normalized: String,
    val intervals: List<String>,
) {
    val empty: Boolean get() = pcset.empty
    val setNum: Int get() = pcset.setNum

    companion object {
        val NoMode = Mode(
            pcset = Pcset.EmptyPcset,
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
        fun get(name: Any?): Mode = ModeDictionary.get(name)

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
        fun notes(modeName: Any?, tonic: String): List<String> {
            val m = get(modeName)
            if (m.empty) return emptyList()
            return m.intervals.map { Distance.transpose(tonic, it) }
        }

        /**
         * Returns the triads of a mode given a tonic.
         */
        fun triads(modeName: Any?, tonic: String): List<String> {
            val m = get(modeName)
            if (m.empty) return emptyList()
            val triadTypes = TonalArray.rotate(m.modeNum, listOf("", "m", "m", "", "", "m", "dim"))
            val tonics = m.intervals.map { Distance.transpose(tonic, it) }
            return triadTypes.mapIndexed { i, type -> tonics[i] + type }
        }

        /**
         * Returns the seventh chords of a mode given a tonic.
         */
        fun seventhChords(modeName: Any?, tonic: String): List<String> {
            val m = get(modeName)
            if (m.empty) return emptyList()
            val seventhTypes = TonalArray.rotate(m.modeNum, listOf("Maj7", "m7", "m7", "Maj7", "7", "m7", "m7b5"))
            val tonics = m.intervals.map { Distance.transpose(tonic, it) }
            return seventhTypes.mapIndexed { i, type -> tonics[i] + type }
        }

        /**
         * Returns the distance between two modes as an interval name.
         */
        fun distance(destination: Any?, source: Any?): String {
            val from = get(source)
            val to = get(destination)
            if (from.empty || to.empty) return ""
            return Interval.simplify(Interval.transposeFifths("1P", to.alt - from.alt))
        }

        /**
         * Returns the relative tonic of a destination mode given a source mode and its tonic.
         */
        fun relativeTonic(destination: Any?, source: Any?, tonic: String): String {
            val dist = distance(destination, source)
            if (dist.isEmpty()) return ""
            return Distance.transpose(tonic, dist)
        }
    }
}

object ModeDictionary {
    private val modes: List<Mode> by lazy {
        listOf(
            // modeNum, setNum, alt, name, triad, seventh, alias
            toMode(0, 2773, 0, "ionian", "", "Maj7", "major"),
            toMode(1, 2902, 2, "dorian", "m", "m7", ""),
            toMode(2, 3418, 4, "phrygian", "m", "m7", ""),
            toMode(3, 2741, -1, "lydian", "", "Maj7", ""),
            toMode(4, 2774, 1, "mixolydian", "", "7", ""),
            toMode(5, 2906, 3, "aeolian", "m", "m7", "minor"),
            toMode(6, 3434, 5, "locrian", "dim", "m7b5", "")
        )
    }

    private val index: Map<String, Mode> by lazy {
        val idx = mutableMapOf<String, Mode>()
        modes.forEach { mode ->
            idx[mode.name] = mode
            mode.aliases.forEach { alias -> idx[alias] = mode }
        }
        idx
    }

    fun get(name: Any?): Mode {
        return when (name) {
            is String -> index[name.lowercase()] ?: Mode.NoMode
            is Mode -> name
            is NamedPitch -> get(name.name)
            else -> Mode.NoMode
        }
    }

    fun all(): List<Mode> = modes.toList()

    fun names(): List<String> = modes.map { it.name }

    private fun toMode(
        modeNum: Int,
        setNum: Int,
        alt: Int,
        name: String,
        triad: String,
        seventh: String,
        alias: String,
    ): Mode {
        val aliases = if (alias.isNotEmpty()) listOf(alias) else emptyList()
        val st = ScaleTypeDictionary.get(name)
        val chroma = setNum.toString(2)
        return Mode(
            pcset = st.pcset,
            name = name,
            modeNum = modeNum,
            alt = alt,
            triad = triad,
            seventh = seventh,
            aliases = aliases,
            chroma = chroma,
            normalized = chroma,
            intervals = st.intervals
        )
    }
}
