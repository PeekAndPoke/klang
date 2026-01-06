package io.peekandpoke.klang.tones.pcset

import io.peekandpoke.klang.tones.distance.Distance
import io.peekandpoke.klang.tones.interval.Interval
import io.peekandpoke.klang.tones.note.Note
import io.peekandpoke.klang.tones.utils.TonesArray

/** A chroma string representing a pitch class set as 12 binary digits (e.g., "101011010101"). */
typealias PcSetChroma = String

/** A decimal number representing a pitch class set (0-4095). */
typealias PcSetNum = Int

/**
 * A checker function that can validate notes or chromas against a set.
 */
interface PcSetChecker {
    operator fun invoke(chroma: String): Boolean
    operator fun invoke(notes: List<String>): Boolean
}

/**
 * The properties of a pitch class set.
 */
data class PcSet(
    val name: String = "",
    val empty: Boolean = false,
    val setNum: PcSetNum = 0,
    val chroma: PcSetChroma = "000000000000",
    val normalized: PcSetChroma = "000000000000",
    val intervals: List<String> = emptyList(),
) {
    companion object {
        /** The default empty pitch class set. */
        val EmptyPcSet = PcSet(
            empty = true,
            chroma = "000000000000",
            normalized = "000000000000",
            intervals = emptyList()
        )

        /** Regular expression to validate chroma strings (12 binary digits). */
        private val REGEX = Regex("^[01]{12}$")

        /** Cache for [PcSet] objects to avoid redundant calculations. */
        private val cache = mutableMapOf(EmptyPcSet.chroma to EmptyPcSet)

        /** Standard intervals from C for each of the 12 semitones. */
        private val IVLS = listOf(
            "1P", "2m", "2M", "3m", "3M", "4P", "5d", "5P", "6m", "6M", "7m", "7M"
        )

        /**
         * Returns true if the string is a valid chroma (12-length string with only 1s or 0s).
         */
        fun isChroma(set: String): Boolean = REGEX.matches(set)

        /**
         * Returns true if the number is a valid pcset number (0-4095).
         */
        private fun isPcsetNum(num: Int): Boolean = num in 0..4095

        /**
         * Get the pitch class set from a chroma string.
         */
        fun get(chroma: String): PcSet {
            if (!isChroma(chroma)) return EmptyPcSet
            return cache.getOrPut(chroma) { chromaToPcset(chroma) }
        }

        /**
         * Get the pitch class set from a set number.
         */
        fun get(setNum: Int): PcSet {
            if (!isPcsetNum(setNum)) return EmptyPcSet
            return get(setNumToChroma(setNum))
        }

        /**
         * Get the pitch class set from a collection of notes or intervals.
         */
        fun get(notes: List<String>): PcSet = get(listToChroma(notes))

        /**
         * Returns the [PcSet] itself.
         */
        fun get(pcset: PcSet): PcSet = pcset

        /**
         * Get pitch class set chroma.
         */
        fun chroma(set: String): PcSetChroma = if (isChroma(set)) set else get(set).chroma
        fun chroma(setNum: Int): PcSetChroma = get(setNum).chroma
        fun chroma(notes: List<String>): PcSetChroma = get(notes).chroma
        fun chroma(pcset: PcSet): PcSetChroma = pcset.chroma

        /**
         * Get intervals (from C) of a set.
         */
        fun intervals(set: String): List<String> = get(set).intervals
        fun intervals(setNum: Int): List<String> = get(setNum).intervals
        fun intervals(notes: List<String>): List<String> = get(notes).intervals
        fun intervals(pcset: PcSet): List<String> = pcset.intervals

        /**
         * Get pitch class set number.
         */
        fun num(set: String): PcSetNum = if (isChroma(set)) chromaToNumber(set) else get(set).setNum
        fun num(notes: List<String>): PcSetNum = get(notes).setNum
        fun num(pcset: PcSet): PcSetNum = pcset.setNum

        /**
         * Get the notes of a pcset starting from C.
         */
        fun notes(set: String): List<String> = notesInternal(get(set))
        fun notes(setNum: Int): List<String> = notesInternal(get(setNum))
        fun notes(notes: List<String>): List<String> = notesInternal(get(notes))
        fun notes(pcset: PcSet): List<String> = notesInternal(pcset)

        private fun notesInternal(pcset: PcSet): List<String> {
            return pcset.intervals.map { ivl -> Distance.transpose("C", ivl) }
        }

        /**
         * Get a list of all possible pitch class sets (all possible chromas) *having C as root*.
         */
        fun chromas(): List<PcSetChroma> {
            return TonesArray.range(2048, 4095).map { setNumToChroma(it) }
        }

        /**
         * Given a list of notes or a pcset chroma, produce the rotations
         * of the chroma discarding the ones that starts with "0".
         */
        fun modes(chroma: String, normalize: Boolean = true): List<PcSetChroma> {
            val binary = chroma.map { it.toString() }

            return TonesArray.compact(
                binary.indices.map { i ->
                    val r = TonesArray.rotate(i, binary)
                    if (normalize && r[0] == "0") null else r.joinToString("")
                }
            )
        }

        fun modes(notes: List<String>, normalize: Boolean = true): List<PcSetChroma> =
            modes(listToChroma(notes), normalize)

        /**
         * Test if two pitch class sets are equal.
         */
        fun isEqual(s1: String, s2: String): Boolean = num(s1) == num(s2)
        fun isEqual(s1: List<String>, s2: List<String>): Boolean = num(s1) == num(s2)

        /**
         * Test if a collection of notes is a subset of a given set.
         */
        fun isSubsetOf(set: String): PcSetChecker {
            val s = num(set)
            return object : PcSetChecker {
                override fun invoke(chroma: String): Boolean {
                    val o = num(chroma)
                    return s != 0 && s != o && (o and s) == o
                }

                override fun invoke(notes: List<String>): Boolean = invoke(listToChroma(notes))
            }
        }

        fun isSubsetOf(notes: List<String>): PcSetChecker = isSubsetOf(listToChroma(notes))

        /**
         * Test if a collection of notes is a superset of a given set.
         */
        fun isSupersetOf(set: String): PcSetChecker {
            val s = num(set)
            return object : PcSetChecker {
                override fun invoke(chroma: String): Boolean {
                    val o = num(chroma)
                    return s != 0 && s != o && (o or s) == o
                }

                override fun invoke(notes: List<String>): Boolean = invoke(listToChroma(notes))
            }
        }

        fun isSupersetOf(notes: List<String>): PcSetChecker = isSupersetOf(listToChroma(notes))

        /**
         * Test if a given pitch class set includes a note.
         */
        fun isNoteIncludedIn(set: String): (String) -> Boolean {
            val s = get(set)
            return { noteName ->
                val n = Note.get(noteName)
                s.chroma != EmptyPcSet.chroma && !n.empty && s.chroma[n.chroma] == '1'
            }
        }

        fun isNoteIncludedIn(notes: List<String>): (String) -> Boolean = isNoteIncludedIn(listToChroma(notes))

        /**
         * Filter a list with a pitch class set.
         */
        fun filter(set: String): (List<String>) -> List<String> {
            val isIncluded = isNoteIncludedIn(set)
            return { notes -> notes.filter { isIncluded(it) } }
        }

        fun filter(setNotes: List<String>): (List<String>) -> List<String> = filter(listToChroma(setNotes))

        // Converts a pcset number to a 12-digit chroma string
        private fun setNumToChroma(num: PcSetNum): PcSetChroma {
            return num.toString(2).padStart(12, '0')
        }

        // Converts a chroma string to its corresponding decimal number
        private fun chromaToNumber(chroma: PcSetChroma): PcSetNum {
            return chroma.toInt(2)
        }

        // Generates all 12 rotations of a chroma string
        private fun chromaRotations(chroma: PcSetChroma): List<PcSetChroma> {
            val binary = chroma.map { it.toString() }
            return binary.indices.map { i -> TonesArray.rotate(i, binary).joinToString("") }
        }

        // Internal factory to create a PcSet from a chroma string
        private fun chromaToPcset(chroma: PcSetChroma): PcSet {
            val setNum = chromaToNumber(chroma)

            val normalizedNum = chromaRotations(chroma)
                .map { chromaToNumber(it) }
                .filter { it >= 2048 }
                .minOrNull() ?: setNum

            val normalized = setNumToChroma(normalizedNum)
            val intervals = chromaToIntervals(chroma)

            return PcSet(
                empty = false,
                name = "",
                setNum = setNum,
                chroma = chroma,
                normalized = normalized,
                intervals = intervals
            )
        }

        // Converts a chroma string to a list of interval names (from C)
        private fun chromaToIntervals(chroma: PcSetChroma): List<String> {
            val intervals = mutableListOf<String>()
            for (i in 0 until 12) {
                if (chroma[i] == '1') {
                    intervals.add(IVLS[i])
                }
            }
            return intervals
        }

        // Converts a list of notes or intervals to a chroma string
        private fun listToChroma(set: List<String>): PcSetChroma {
            if (set.isEmpty()) {
                return EmptyPcSet.chroma
            }

            val binary = IntArray(12) { 0 }
            for (item in set) {
                val p = Note.get(item)

                if (p.empty) {
                    val i = Interval.get(item)
                    if (!i.empty) {
                        binary[i.chroma] = 1
                    }
                } else {
                    binary[p.chroma] = 1
                }
            }

            return binary.joinToString("")
        }
    }
}
