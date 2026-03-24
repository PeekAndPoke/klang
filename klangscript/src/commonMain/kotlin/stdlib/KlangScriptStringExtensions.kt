package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries
import io.peekandpoke.klang.script.runtime.ArrayValue
import io.peekandpoke.klang.script.runtime.BooleanValue
import io.peekandpoke.klang.script.runtime.StringValue

/**
 * String type extensions for KlangScript.
 *
 * Methods are registered as extensions on [StringValue] in KlangScript.
 * The first parameter (`self`) is the receiver, injected automatically by the runtime.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(StringValue::class)
internal object KlangScriptStringExtensions {

    /**
     * Returns the length of the string.
     *
     * ```KlangScript(Executable)
     * "hello".length()
     * ```
     *
     * @param self The string
     * @return The number of characters
     * @category string
     * @tags property
     */
    @KlangScript.Method
    fun length(self: StringValue): Double = self.value.length.toDouble()

    /**
     * Returns the character at the given index, or an empty string if out of bounds.
     *
     * ```KlangScript(Executable)
     * "hello".charAt(1)  // "e"
     * ```
     *
     * @param self The string
     * @param index The zero-based index
     * @return The character at the index
     * @category string
     * @tags access
     */
    @KlangScript.Method
    fun charAt(self: StringValue, index: Double): StringValue {
        val idx = index.toInt()
        return if (idx in self.value.indices) StringValue(self.value[idx].toString()) else StringValue("")
    }

    /**
     * Returns a substring between start (inclusive) and end (exclusive).
     *
     * ```KlangScript(Executable)
     * "hello world".substring(0, 5)  // "hello"
     * ```
     *
     * @param self The string
     * @param start Start index (inclusive)
     * @param end End index (exclusive)
     * @return The substring
     * @category string
     * @tags slicing
     */
    @KlangScript.Method
    fun substring(self: StringValue, start: Double, end: Double): StringValue {
        val s = start.toInt().coerceIn(0, self.value.length)
        val e = end.toInt().coerceIn(0, self.value.length)
        return StringValue(self.value.substring(s, e))
    }

    /**
     * Returns the index of the first occurrence of a search string, or -1 if not found.
     *
     * ```KlangScript(Executable)
     * "hello".indexOf("ll")  // 2.0
     * ```
     *
     * @param self The string
     * @param searchStr The string to search for
     * @return The index, or -1
     * @category string
     * @tags search
     */
    @KlangScript.Method
    fun indexOf(self: StringValue, searchStr: String): Double = self.value.indexOf(searchStr).toDouble()

    /**
     * Splits the string by a separator and returns an array of substrings.
     *
     * ```KlangScript(Executable)
     * "a,b,c".split(",").joinToString(" ")  // "a b c"
     * ```
     *
     * @param self The string
     * @param separator The separator to split on
     * @return Array of substrings
     * @category string
     * @tags splitting
     */
    @KlangScript.Method
    fun split(self: StringValue, separator: String): ArrayValue =
        ArrayValue(self.value.split(separator).map { StringValue(it) }.toMutableList())

    /**
     * Converts the string to uppercase.
     *
     * ```KlangScript(Executable)
     * "hello".toUpperCase()  // "HELLO"
     * ```
     *
     * @param self The string
     * @return The uppercase string
     * @category string
     * @tags transform
     */
    @KlangScript.Method
    fun toUpperCase(self: StringValue): StringValue = StringValue(self.value.uppercase())

    /**
     * Converts the string to lowercase.
     *
     * ```KlangScript(Executable)
     * "HELLO".toLowerCase()  // "hello"
     * ```
     *
     * @param self The string
     * @return The lowercase string
     * @category string
     * @tags transform
     */
    @KlangScript.Method
    fun toLowerCase(self: StringValue): StringValue = StringValue(self.value.lowercase())

    /**
     * Removes leading and trailing whitespace.
     *
     * ```KlangScript(Executable)
     * "  hello  ".trim()  // "hello"
     * ```
     *
     * @param self The string
     * @return The trimmed string
     * @category string
     * @tags transform
     */
    @KlangScript.Method
    fun trim(self: StringValue): StringValue = StringValue(self.value.trim())

    /**
     * Returns true if the string starts with the given prefix.
     *
     * @param self The string
     * @param prefix The prefix to check
     * @return True if the string starts with the prefix
     * @category string
     * @tags search
     */
    @KlangScript.Method
    fun startsWith(self: StringValue, prefix: String): BooleanValue = BooleanValue(self.value.startsWith(prefix))

    /**
     * Returns true if the string ends with the given suffix.
     *
     * @param self The string
     * @param suffix The suffix to check
     * @return True if the string ends with the suffix
     * @category string
     * @tags search
     */
    @KlangScript.Method
    fun endsWith(self: StringValue, suffix: String): BooleanValue = BooleanValue(self.value.endsWith(suffix))

    /**
     * Replaces all occurrences of a search string with a replacement.
     *
     * ```KlangScript(Executable)
     * "hello world".replace("world", "klang")  // "hello klang"
     * ```
     *
     * @param self The string
     * @param search The string to find
     * @param replacement The replacement string
     * @return The new string with replacements
     * @category string
     * @tags transform
     */
    @KlangScript.Method
    fun replace(self: StringValue, search: String, replacement: String): StringValue =
        StringValue(self.value.replace(search, replacement))

    /**
     * Returns a substring between start (inclusive) and end (exclusive). Same as substring.
     *
     * @param self The string
     * @param start Start index (inclusive)
     * @param end End index (exclusive)
     * @return The sliced string
     * @category string
     * @tags slicing
     */
    @KlangScript.Method
    fun slice(self: StringValue, start: Double, end: Double): StringValue {
        val s = start.toInt().coerceIn(0, self.value.length)
        val e = end.toInt().coerceIn(0, self.value.length)
        return StringValue(self.value.substring(s, e))
    }

    /**
     * Concatenates another string to this string.
     *
     * @param self The string
     * @param other The string to append
     * @return The concatenated string
     * @category string
     * @tags transform
     */
    @KlangScript.Method
    fun concat(self: StringValue, other: String): StringValue = StringValue(self.value + other)

    /**
     * Repeats the string a given number of times.
     *
     * ```KlangScript(Executable)
     * "ha".repeat(3)  // "hahaha"
     * ```
     *
     * @param self The string
     * @param count Number of repetitions
     * @return The repeated string
     * @category string
     * @tags transform
     */
    @KlangScript.Method
    fun repeat(self: StringValue, count: Double): StringValue =
        StringValue(self.value.repeat(count.toInt().coerceAtLeast(0)))

    /**
     * Returns the string representation.
     *
     * @param self The string
     * @return The string itself
     * @category string
     */
    @KlangScript.Method(name = "toString")
    fun asString(self: StringValue): StringValue = StringValue(self.value)
}
