/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel

/**
 * The slot storage of ONE event, `<name>` to `Double`, and the one method the fill rule is
 * expressed through.
 *
 * Both of [SprudelVoiceData]'s bags are this class: `oscParams` (the voice's own instrument) and
 * `katalystParams` (the chain its orbit runs). They differ in their HOST, never in their shape, so
 * they share one type, one contract and one set of tests.
 *
 * **Mutable and single-owner.** A door writes one name in place instead of allocating a fresh bag
 * per slot, which is what keeps a pattern that fills a dozen slots out of the "twenty allocations
 * per note" class. The bag is allocated on the first write ([SprudelVoiceData.oscParamsOrNew] and
 * its twin), copied once per event by [SprudelVoiceData.clone], and copied once more at the wire by
 * [toMap]. Anything else would give two events one bag, and one event's write would land on the
 * other.
 *
 * ## The fill rule's one method
 *
 * `/dsl-design` §4 is the rule's one home. This class holds the one METHOD the rule is expressed
 * through, and only the facts about that method belong here.
 *
 * **[setOrDefault]'s default branch tests ABSENCE, not a value.** That is the never-overwrite half
 * of the rule: `body(wet = 0.3).body(material = "wood")` must end at 0.3, so a fill has to tell an author's
 * 0.3 from a knob nobody has touched. Only "is this name already here" can, because every candidate
 * sentinel is also a legal value: `DUCK_DEPTH` and `PHASER_WET` are 0.0, a threshold is negative,
 * and a raw `katp("body.wet", "NaN")` writes a non-finite value (`SLOT_UNSET` is NaN) that has to
 * stay as written rather than be re-filled. Absence is the only honest "nobody said". (No door
 * clears a slot any more: the two name setters that did lost their clear arm with the wet-first
 * order, step 3d(iii), 2026-09-24.)
 *
 * **A companion passes null.** A companion is by definition a knob the call did not name, and the
 * knob it DID name is already in the bag from its own setter's [set]. Handing the voice FIELD
 * instead would look like a value and is not one: after one call of the same door the field holds
 * the constant that call's own fill wrote, so `compressor(ratio = 8).katp("compressor.threshold",
 * -40).compressor(knee = 3)` would stamp -20 over the author's -40 (round 1 of step 5a-3's review).
 * The `value` parameter therefore has no non-null caller in production today and is exercised by
 * `ParamBagSpec` alone; it stays because it is the shape of the rule, and because the voice-side
 * doors hand their call arguments straight in when phase 3 of the signal-flow plan retires the
 * fields.
 */
class ParamBag {

    private val values: MutableMap<String, Double> = mutableMapOf()

    /** How many names the bag holds. */
    val size: Int get() = values.size

    /** True while nothing has been written. A bag that exists is usually not empty (see the class KDoc). */
    fun isEmpty(): Boolean = values.isEmpty()

    /** Reads one name, or null when it was never written. */
    operator fun get(name: String): Double? = values[name]

    /**
     * Writes [name], overwriting whatever was there.
     *
     * What the author asked for always wins, including over a value an earlier fill put here. It is
     * also the only way a stage's NAME KNOB is ever written, never through [setOrDefault], because
     * a fill must not invent a name (`/dsl-design` §4).
     */
    fun set(name: String, value: Double) {
        values[name] = value
    }

    /**
     * The fill rule: writes [value] when the caller has one, otherwise [default], and then only
     * when [name] is absent.
     *
     * [value] is what THIS call named, never a voice field (see the class KDoc); a companion fill
     * passes null. So an explicit value survives every later fill, a non-finite one included,
     * and a knob nobody has named takes its shared constant the moment its stage is named.
     */
    fun setOrDefault(name: String, value: Double?, default: Double) {
        if (value != null) {
            values[name] = value

            return
        }

        if (!values.containsKey(name)) {
            values[name] = default
        }
    }

    /**
     * A bag with the same entries and nothing shared, the per-event deep copy [SprudelVoiceData.clone]
     * makes: writing into the copy must never reach the original.
     */
    fun copy(): ParamBag = ParamBag().also { it.values.putAll(values) }

    /**
     * Folds [other] into this bag in place, last writer wins per name: [other]'s entries overwrite,
     * the receiver's other names stay. The in-place half of the merge, for
     * [SprudelVoiceData.mergeFrom].
     */
    fun mergeFrom(other: ParamBag) {
        values.putAll(other.values)
    }

    /**
     * The wire boundary: a COPY of the entries, never the bag's own storage.
     *
     * The backend holds `Voice.katalystParams` for the whole life of the voice and gates its
     * re-resolve on the map's IDENTITY, so a map sprudel could still write into would move an
     * orbit's settings with nothing to notice it.
     */
    fun toMap(): Map<String, Double> = values.toMap()

    override fun equals(other: Any?): Boolean = this === other || (other is ParamBag && values == other.values)

    override fun hashCode(): Int = values.hashCode()

    override fun toString(): String = "ParamBag($values)"
}

/** A bag holding [entries], for tests and for a producer that already has its values in hand. */
fun paramBagOf(vararg entries: Pair<String, Double>): ParamBag = ParamBag().also { bag ->
    for ((name, value) in entries) {
        bag.set(name, value)
    }
}

/** A bag holding [entries], the [Map] form of the twin above. */
fun paramBagOf(entries: Map<String, Double>): ParamBag = ParamBag().also { bag ->
    for ((name, value) in entries) {
        bag.set(name, value)
    }
}
