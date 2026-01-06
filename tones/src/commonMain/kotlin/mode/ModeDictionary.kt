package io.peekandpoke.klang.tones.mode

import io.peekandpoke.klang.tones.pitch.NamedPitch
import io.peekandpoke.klang.tones.scale.ScaleTypeDictionary

/**
 * A dictionary of common musical modes.
 */
object ModeDictionary {
    /** The list of modes in the dictionary. */
    private val modes: List<Mode> by lazy {
        listOf(
            // modeNum: The degree of the parent Major scale (0-indexed: Ionian=0, Dorian=1, etc.)
            // setNum:  Decimal bitmask of the chroma (Bit 11 is the root, e.g., 2773 = 101011010101)
            // alt:     The number of fifths from C to the mode root (Ionian=0, Lydian=-1, etc.)
            // triad:   The default triad chord type
            // seventh: The default seventh chord type
            toMode(modeNum = 0, setNum = 2773, alt = 0, name = "ionian", triad = "", seventh = "Maj7", alias = "major"),
            toMode(modeNum = 1, setNum = 2902, alt = 2, name = "dorian", triad = "m", seventh = "m7", alias = ""),
            toMode(modeNum = 2, setNum = 3418, alt = 4, name = "phrygian", triad = "m", seventh = "m7", alias = ""),
            toMode(modeNum = 3, setNum = 2741, alt = -1, name = "lydian", triad = "", seventh = "Maj7", alias = ""),
            toMode(modeNum = 4, setNum = 2774, alt = 1, name = "mixolydian", triad = "", seventh = "7", alias = ""),
            toMode(modeNum = 5, setNum = 2906, alt = 3, name = "aeolian", triad = "m", seventh = "m7", alias = "minor"),
            toMode(modeNum = 6, setNum = 3434, alt = 5, name = "locrian", triad = "dim", seventh = "m7b5", alias = "")
        )
    }

    /** The index of modes by name and alias. */
    private val index: Map<String, Mode> by lazy {
        val idx = mutableMapOf<String, Mode>()
        modes.forEach { mode ->
            idx[mode.name] = mode
            mode.aliases.forEach { alias -> idx[alias] = mode }
        }
        idx
    }

    /**
     * Returns a [Mode] by name or alias.
     */
    fun get(name: String): Mode {
        return index[name.lowercase()] ?: Mode.NoMode
    }

    /**
     * Returns the [Mode] itself.
     */
    fun get(mode: Mode): Mode = mode

    /**
     * Converts a [NamedPitch] to a [Mode].
     */
    fun get(named: NamedPitch): Mode = get(named.name)

    /**
     * Returns a list of all modes in the dictionary.
     */
    fun all(): List<Mode> = modes.toList()

    /**
     * Returns a list of all mode names in the dictionary.
     */
    fun names(): List<String> = modes.map { it.name }

    // Internal helper to create a Mode from raw data.
    // modeNum: The mode number (0-indexed) relative to the parent scale (e.g., Ionian = 0, Dorian = 1, etc.).
    // setNum: The decimal representation of the chroma bitmask.
    // alt: The number of fifths from C to the mode root (Ionian = 0, Dorian = 2, Lydian = -1, etc.).
    // name: The name of the mode.
    // triad: The triad chord type of the mode.
    // seventh: The seventh chord type of the mode.
    // alias: The alias of the mode (e.g., "major" for "ionian").
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
            pcSet = st.pcset,
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
