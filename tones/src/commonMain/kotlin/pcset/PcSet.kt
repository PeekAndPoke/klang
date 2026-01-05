package io.peekandpoke.klang.tones.pcset

import io.peekandpoke.klang.tones.distance.Distance
import io.peekandpoke.klang.tones.interval.Interval
import io.peekandpoke.klang.tones.note.Note
import io.peekandpoke.klang.tones.utils.TonesArray

typealias PcSetChroma = String
typealias PcSetNum = Int

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
        fun isChroma(set: Any?): Boolean {
            return set is String && REGEX.matches(set)
        }

        /**
         * Returns true if the number is a valid pcset number (0-4095).
         */
        private fun isPcsetNum(set: Any?): Boolean {
            return set is Int && set in 0..4095
        }

        /**
         * Returns true if the object is a [PcSet].
         */
        fun isPcset(set: Any?): Boolean {
            return set is PcSet && isChroma(set.chroma)
        }

        /**
         * Get the pitch class set of a collection of notes or set number or chroma.
         */
        fun get(src: Any?): PcSet {
            val chroma: PcSetChroma = when {
                isChroma(src) -> src as PcSetChroma
                isPcsetNum(src) -> setNumToChroma(src as PcSetNum)
                src is List<*> -> listToChroma(src)
                isPcset(src) -> (src as PcSet).chroma
                else -> EmptyPcSet.chroma
            }

            return cache.getOrPut(chroma) { chromaToPcset(chroma) }
        }

        /**
         * Get pitch class set chroma.
         */
        fun chroma(set: Any?): PcSetChroma = get(set).chroma

        /**
         * Get intervals (from C) of a set.
         */
        fun intervals(set: Any?): List<String> = get(set).intervals

        /**
         * Get pitch class set number.
         */
        fun num(set: Any?): PcSetNum = get(set).setNum

        /**
         * Get the notes of a pcset starting from C.
         */
        fun notes(set: Any?): List<String> {
            return get(set).intervals.map { ivl -> Distance.transpose("C", ivl) }
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
        fun modes(set: Any?, normalize: Boolean = true): List<PcSetChroma> {
            val pcs = get(set)
            val binary = pcs.chroma.map { it.toString() }

            return TonesArray.compact(
                binary.indices.map { i ->
                    val r = TonesArray.rotate(i, binary)
                    if (normalize && r[0] == "0") null else r.joinToString("")
                }
            )
        }

        /**
         * Test if two pitch class sets are equal.
         */
        fun isEqual(s1: Any?, s2: Any?): Boolean {
            return get(s1).setNum == get(s2).setNum
        }

        /**
         * Test if a collection of notes is a subset of a given set.
         */
        fun isSubsetOf(set: Any?): (Any?) -> Boolean {
            val s = get(set).setNum
            return { notes ->
                val o = get(notes).setNum
                s != 0 && s != o && (o and s) == o
            }
        }

        /**
         * Test if a collection of notes is a superset of a given set.
         */
        fun isSupersetOf(set: Any?): (Any?) -> Boolean {
            val s = get(set).setNum
            return { notes ->
                val o = get(notes).setNum
                s != 0 && s != o && (o or s) == o
            }
        }

        /**
         * Test if a given pitch class set includes a note.
         */
        fun isNoteIncludedIn(set: Any?): (String) -> Boolean {
            val s = get(set)
            return { noteName ->
                val n = Note.get(noteName)
                s.chroma != EmptyPcSet.chroma && !n.empty && s.chroma[n.chroma] == '1'
            }
        }

        /**
         * Filter a list with a pitch class set.
         */
        fun filter(set: Any?): (List<String>) -> List<String> {
            val isIncluded = isNoteIncludedIn(set)
            return { notes -> notes.filter { isIncluded(it) } }
        }

        /**
         * Converts a pcset number to a 12-digit chroma string.
         */
        private fun setNumToChroma(num: PcSetNum): PcSetChroma {
            return num.toString(2).padStart(12, '0')
        }

        /**
         * Converts a chroma string to its corresponding decimal number.
         */
        private fun chromaToNumber(chroma: PcSetChroma): PcSetNum {
            return chroma.toInt(2)
        }

        /**
         * Generates all 12 rotations of a chroma string.
         */
        private fun chromaRotations(chroma: PcSetChroma): List<PcSetChroma> {
            val binary = chroma.map { it.toString() }
            return binary.indices.map { i -> TonesArray.rotate(i, binary).joinToString("") }
        }

        /**
         * Internal factory to create a [PcSet] from a chroma string.
         */
        private fun chromaToPcset(chroma: PcSetChroma): PcSet {
            val setNum = chromaToNumber(chroma)

            // Find the "normalized" version of the chroma (the smallest set number among all rotations)
            // Only rotations starting with '1' (binary >= 2048) are considered.
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

        /**
         * Converts a chroma string to a list of interval names (from C).
         */
        private fun chromaToIntervals(chroma: PcSetChroma): List<String> {
            val intervals = mutableListOf<String>()
            for (i in 0 until 12) {
                if (chroma[i] == '1') {
                    intervals.add(IVLS[i])
                }
            }
            return intervals
        }

        /**
         * Converts a list of notes or intervals to a chroma string.
         */
        private fun listToChroma(set: List<*>): PcSetChroma {
            if (set.isEmpty()) {
                return EmptyPcSet.chroma
            }

            val binary = IntArray(12) { 0 }
            for (item in set) {
                val p = Note.get(item)

                if (p.empty) {
                    // If not a note, try parsing as an interval
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
