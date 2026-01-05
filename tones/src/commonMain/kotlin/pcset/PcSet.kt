package io.peekandpoke.klang.tones.pcset

import io.peekandpoke.klang.tones.distance.Distance
import io.peekandpoke.klang.tones.interval.Interval
import io.peekandpoke.klang.tones.note.Note
import io.peekandpoke.klang.tones.utils.TonesArray

/**
 * The properties of a pitch class set.
 */
data class Pcset(
    val name: String = "",
    val empty: Boolean = false,
    val setNum: Int = 0,
    val chroma: String = "000000000000",
    val normalized: String = "000000000000",
    val intervals: List<String> = emptyList(),
) {
    companion object {
        val EmptyPcset = Pcset(
            empty = true,
            chroma = "000000000000",
            normalized = "000000000000",
            intervals = emptyList()
        )
    }
}

typealias PcsetChroma = String
typealias PcsetNum = Int

object PcSet {
    private val REGEX = Regex("^[01]{12}$")
    private val cache = mutableMapOf<PcsetChroma, Pcset>(Pcset.EmptyPcset.chroma to Pcset.EmptyPcset)
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
     * Returns true if the object is a [Pcset].
     */
    fun isPcset(set: Any?): Boolean {
        return set is Pcset && isChroma(set.chroma)
    }

    /**
     * Get the pitch class set of a collection of notes or set number or chroma.
     */
    fun get(src: Any?): Pcset {
        val chroma: PcsetChroma = when {
            isChroma(src) -> src as PcsetChroma
            isPcsetNum(src) -> setNumToChroma(src as Int)
            src is List<*> -> listToChroma(src)
            isPcset(src) -> (src as Pcset).chroma
            else -> Pcset.EmptyPcset.chroma
        }

        return cache.getOrPut(chroma) { chromaToPcset(chroma) }
    }

    /**
     * Get pitch class set chroma.
     */
    fun chroma(set: Any?): PcsetChroma = get(set).chroma

    /**
     * Get intervals (from C) of a set.
     */
    fun intervals(set: Any?): List<String> = get(set).intervals

    /**
     * Get pitch class set number.
     */
    fun num(set: Any?): Int = get(set).setNum

    /**
     * Get the notes of a pcset starting from C.
     */
    fun notes(set: Any?): List<String> {
        return get(set).intervals.map { ivl -> Distance.transpose("C", ivl) }
    }

    /**
     * Get a list of all possible pitch class sets (all possible chromas) *having C as root*.
     */
    fun chromas(): List<PcsetChroma> {
        return TonesArray.range(2048, 4095).map { setNumToChroma(it) }
    }

    /**
     * Given a list of notes or a pcset chroma, produce the rotations
     * of the chroma discarding the ones that starts with "0".
     */
    fun modes(set: Any?, normalize: Boolean = true): List<PcsetChroma> {
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
            s.chroma != Pcset.EmptyPcset.chroma && !n.empty && s.chroma[n.chroma] == '1'
        }
    }

    /**
     * Filter a list with a pitch class set.
     */
    fun filter(set: Any?): (List<String>) -> List<String> {
        val isIncluded = isNoteIncludedIn(set)
        return { notes -> notes.filter { isIncluded(it) } }
    }

    //// PRIVATE ////

    private fun setNumToChroma(num: Int): String {
        return num.toString(2).padStart(12, '0')
    }

    private fun chromaToNumber(chroma: String): Int {
        return chroma.toInt(2)
    }

    private fun chromaRotations(chroma: String): List<String> {
        val binary = chroma.map { it.toString() }
        return binary.indices.map { i -> TonesArray.rotate(i, binary).joinToString("") }
    }

    private fun chromaToPcset(chroma: PcsetChroma): Pcset {
        val setNum = chromaToNumber(chroma)
        val normalizedNum = chromaRotations(chroma)
            .map { chromaToNumber(it) }
            .filter { it >= 2048 }
            .minOrNull() ?: setNum

        val normalized = setNumToChroma(normalizedNum)
        val intervals = chromaToIntervals(chroma)

        return Pcset(
            empty = false,
            name = "",
            setNum = setNum,
            chroma = chroma,
            normalized = normalized,
            intervals = intervals
        )
    }

    private fun chromaToIntervals(chroma: PcsetChroma): List<String> {
        val intervals = mutableListOf<String>()
        for (i in 0 until 12) {
            if (chroma[i] == '1') {
                intervals.add(IVLS[i])
            }
        }
        return intervals
    }

    private fun listToChroma(set: List<*>): PcsetChroma {
        if (set.isEmpty()) {
            return Pcset.EmptyPcset.chroma
        }

        val binary = IntArray(12) { 0 }
        for (item in set) {
            var p = Note.get(item)
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
