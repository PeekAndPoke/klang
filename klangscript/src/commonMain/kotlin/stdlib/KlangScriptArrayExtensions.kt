package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries
import io.peekandpoke.klang.script.runtime.*

/**
 * Array type extensions for KlangScript (Kotlin-style API).
 *
 * Methods are registered as extensions on [ArrayValue] in KlangScript.
 * The first parameter (`self`) is the receiver, injected automatically by the runtime.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(ArrayValue::class)
internal object KlangScriptArrayExtensions {

    // ── Properties ──────────────────────────────────────────────────────

    /**
     * Returns the number of elements in the array.
     *
     * ```KlangScript(Executable)
     * [1, 2, 3].size()
     * ```
     *
     * @param self The array
     * @return The size
     * @category array
     * @tags property
     */
    @KlangScript.Method
    fun size(self: ArrayValue): Double = self.elements.size.toDouble()

    // ── Access ──────────────────────────────────────────────────────────

    /**
     * Returns the first element, or null if the array is empty.
     *
     * @param self The array
     * @return The first element or null
     * @category array
     * @tags access
     */
    @KlangScript.Method
    fun first(self: ArrayValue): RuntimeValue = if (self.elements.isNotEmpty()) self.elements.first() else NullValue

    /**
     * Returns the last element, or null if the array is empty.
     *
     * @param self The array
     * @return The last element or null
     * @category array
     * @tags access
     */
    @KlangScript.Method
    fun last(self: ArrayValue): RuntimeValue = if (self.elements.isNotEmpty()) self.elements.last() else NullValue

    // ── Mutating ────────────────────────────────────────────────────────

    /**
     * Adds an element to the end of the array. Returns the new size.
     *
     * ```KlangScript(Executable)
     * let a = [1, 2]
     * a.add(3)
     * a.joinToString(", ")  // "1, 2, 3"
     * ```
     *
     * @param self The array
     * @param item The element to add
     * @return The new size of the array
     * @category array
     * @tags mutating
     */
    @KlangScript.Method
    fun add(self: ArrayValue, item: Any): Double {
        self.elements.add(wrapAsRuntimeValue(item))
        return self.elements.size.toDouble()
    }

    /**
     * Removes the element at the given index. Returns the removed element or null.
     *
     * @param self The array
     * @param index The index to remove
     * @return The removed element or null if out of bounds
     * @category array
     * @tags mutating
     */
    @KlangScript.Method
    fun removeAt(self: ArrayValue, index: Double): RuntimeValue {
        val idx = index.toInt()
        return if (idx in self.elements.indices) self.elements.removeAt(idx) else NullValue
    }

    /**
     * Removes and returns the last element, or null if empty.
     *
     * @param self The array
     * @return The removed element or null
     * @category array
     * @tags mutating
     */
    @KlangScript.Method
    fun removeLast(self: ArrayValue): RuntimeValue =
        if (self.elements.isNotEmpty()) self.elements.removeLast() else NullValue

    /**
     * Removes and returns the first element, or null if empty.
     *
     * @param self The array
     * @return The removed element or null
     * @category array
     * @tags mutating
     */
    @KlangScript.Method
    fun removeFirst(self: ArrayValue): RuntimeValue =
        if (self.elements.isNotEmpty()) self.elements.removeFirst() else NullValue

    // ── Non-mutating (return new arrays) ────────────────────────────────

    /**
     * Returns a new array with elements in reverse order.
     *
     * ```KlangScript(Executable)
     * [1, 2, 3].reversed().joinToString(", ")  // "3, 2, 1"
     * ```
     *
     * @param self The array
     * @return A new reversed array
     * @category array
     * @tags transform
     */
    @KlangScript.Method
    fun reversed(self: ArrayValue): ArrayValue = ArrayValue(self.elements.reversed().toMutableList())

    /**
     * Returns a new array with the first n elements removed.
     *
     * @param self The array
     * @param n Number of elements to drop
     * @return A new array without the first n elements
     * @category array
     * @tags slicing
     */
    @KlangScript.Method
    fun drop(self: ArrayValue, n: Double): ArrayValue = ArrayValue(self.elements.drop(n.toInt()).toMutableList())

    /**
     * Returns a new array with only the first n elements.
     *
     * @param self The array
     * @param n Number of elements to take
     * @return A new array with the first n elements
     * @category array
     * @tags slicing
     */
    @KlangScript.Method
    fun take(self: ArrayValue, n: Double): ArrayValue = ArrayValue(self.elements.take(n.toInt()).toMutableList())

    /**
     * Returns a sub-array from start (inclusive) to end (exclusive).
     *
     * @param self The array
     * @param start Start index (inclusive)
     * @param end End index (exclusive)
     * @return A new sub-array
     * @category array
     * @tags slicing
     */
    @KlangScript.Method
    fun subList(self: ArrayValue, start: Double, end: Double): ArrayValue {
        val s = start.toInt().coerceIn(0, self.elements.size)
        val e = end.toInt().coerceIn(0, self.elements.size)
        return ArrayValue(self.elements.subList(s, e).toMutableList())
    }

    /**
     * Joins all elements into a string with the given separator.
     *
     * ```KlangScript(Executable)
     * [1, 2, 3].joinToString(" - ")  // "1 - 2 - 3"
     * ```
     *
     * @param self The array
     * @param separator The separator string
     * @return The joined string
     * @category array
     * @tags transform
     */
    @KlangScript.Method
    fun joinToString(self: ArrayValue, separator: String): StringValue =
        StringValue(self.elements.joinToString(separator) { it.toDisplayString() })

    /**
     * Returns the index of the first occurrence of an item, or -1 if not found.
     *
     * @param self The array
     * @param item The item to search for
     * @return The index, or -1
     * @category array
     * @tags search
     */
    @KlangScript.Method
    fun indexOf(self: ArrayValue, item: Any): NumberValue {
        val wrapped = wrapAsRuntimeValue(item)
        return NumberValue(self.elements.indexOfFirst { it.value == wrapped.value }.toDouble())
    }

    /**
     * Returns true if the array contains the given item.
     *
     * @param self The array
     * @param item The item to check for
     * @return True if the array contains the item
     * @category array
     * @tags search
     */
    @KlangScript.Method
    fun contains(self: ArrayValue, item: Any): BooleanValue {
        val wrapped = wrapAsRuntimeValue(item)
        return BooleanValue(self.elements.any { it.value == wrapped.value })
    }

    /**
     * Returns true if the array has no elements.
     *
     * @param self The array
     * @return True if empty
     * @category array
     * @tags property
     */
    @KlangScript.Method
    fun isEmpty(self: ArrayValue): BooleanValue = BooleanValue(self.elements.isEmpty())

    /**
     * Returns true if the array has at least one element.
     *
     * @param self The array
     * @return True if not empty
     * @category array
     * @tags property
     */
    @KlangScript.Method
    fun isNotEmpty(self: ArrayValue): BooleanValue = BooleanValue(self.elements.isNotEmpty())
}
