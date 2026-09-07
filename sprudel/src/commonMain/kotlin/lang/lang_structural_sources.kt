/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.common.SourceLocationChain
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceValue
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel._innerJoin
import io.peekandpoke.klang.sprudel.createSprudelVoiceData
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.map
import io.peekandpoke.klang.sprudel.pattern.AtomicPattern
import io.peekandpoke.klang.sprudel.pattern.EmptyPattern
import io.peekandpoke.klang.sprudel.pattern.GapPattern
import io.peekandpoke.klang.sprudel.pattern.PropertyOverridePattern
import io.peekandpoke.klang.sprudel.pattern.SequencePattern
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log2

// -- silence / rest / nothing -----------------------------------------------------------------------------------------

/**
 * An empty pattern that produces no events.
 *
 * Use `silence` wherever a [SprudelPattern] argument is required but nothing should play.
 * It is the identity element for [stack] and acts as a rest in sequencing functions like [cat].
 *
 *
 * ```KlangScript(Playable)
 * seq("c3", silence, "e3", "g3").note()  // rest on the second step
 * ```
 *
 * ```KlangScript(Playable)
 * cat(s("bd sd"), silence)  // phrase followed by a silent cycle
 * ```
 * @alias rest, nothing
 * @category continuous
 * @tags silence, rest, empty, quiet
 */
@KlangScript.Constant
val silence: SprudelPattern = EmptyPattern

/**
 * An empty pattern that produces no events. Alias for [silence].
 *
 *
 * ```KlangScript(Playable)
 * seq("c3", rest, "e3", "g3").note()  // rest on the second step
 * ```
 *
 * ```KlangScript(Playable)
 * cat(s("bd sd"), rest)  // phrase followed by a silent cycle
 * ```
 * @alias silence, nothing
 * @category continuous
 * @tags rest, silence, empty, quiet
 */
@KlangScript.Constant
val rest: SprudelPattern = EmptyPattern

/**
 * An empty pattern that produces no events. Alias for [silence].
 *
 *
 * ```KlangScript(Playable)
 * seq("c3", nothing, "e3", "g3").note()  // rest on the second step
 * ```
 *
 * ```KlangScript(Playable)
 * cat(s("bd sd"), nothing)  // phrase followed by a silent cycle
 * ```
 * @alias silence, rest
 * @category continuous
 * @tags nothing, silence, rest, empty, quiet
 */
@KlangScript.Constant
val nothing: SprudelPattern = EmptyPattern

// -- gap() ------------------------------------------------------------------------------------------------------------

/** Creates a silent pattern occupying the given number of steps. Supports control patterns via _innerJoin. */
private fun applyGap(args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val stepsArg = args.getOrNull(0) ?: return GapPattern(1.0)

    // For static values create GapPattern directly so its weight is preserved.
    // SequencePattern reads .weight once at construction for proportional allocation.
    val staticSteps = stepsArg.value?.asDoubleOrNull()
    if (staticSteps != null) {
        return GapPattern(staticSteps)
    }

    // For control patterns evaluate the step count per event via _innerJoin.
    // Proportional weight in sequences is not supported for control patterns (defaults to 1).
    return silence._innerJoin(stepsArg) { _, stepsVal ->
        val steps = stepsVal?.asDoubleOrNull() ?: 1.0
        GapPattern(steps)
    }
}

/**
 * Creates a silent slot that produces no events.
 *
 * When called with a static step count and used inside [seq] or [cat], the gap occupies
 * proportionally more space than adjacent 1-step elements. Equivalent to the `~` rest character
 * in mini-notation.
 *
 * Control patterns (e.g. `gap("<1 2>")`) are supported — the step count is evaluated per event —
 * but proportional space allocation in sequences is not affected (weight defaults to 1 for
 * control patterns, since [seq] reads weights once at construction time).
 *
 * @param steps Step count for the silence (default 1). Accepts static numbers and control patterns.
 * @return A silent pattern with the given step weight
 *
 * ```KlangScript(Playable)
 * seq("bd", gap(), "hh").s()  // bd, 1-step rest, hh — each gets 1/3 of the cycle
 * ```
 *
 * ```KlangScript(Playable)
 * seq("bd", gap(2), "hh").s()  // bd=1/4, rest=2/4, hh=1/4
 * ```
 * @category structural
 * @tags silence, rest, gap, rhythm
 */
@KlangScript.Function
fun gap(vararg steps: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyGap(steps.toList().asSprudelDslArgs(callInfo))

/**
 * Replaces this pattern with a silent slot occupying the given number of steps.
 *
 * ```KlangScript(Playable)
 * note("c").gap()  // Replaces with 1-step silence
 * ```
 *
 * ```KlangScript(Playable)
 * note("c").gap(2)  // Replaces with 2-step silence
 * ```
 */
@Suppress("UnusedReceiverParameter")
@KlangScript.Function
fun SprudelPattern.gap(vararg steps: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyGap(steps.toList().asSprudelDslArgs(callInfo))

/**
 * Replaces this string pattern with a silent slot occupying the given number of steps.
 *
 * ```KlangScript(Playable)
 * seq("bd", "hh".gap(), "sd").s()  // Middle step replaced by silence
 * ```
 */
@KlangScript.Function
fun String.gap(vararg steps: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).gap(*steps, callInfo = callInfo)

// -- mini() -----------------------------------------------------------------------------------------------------------

/**
 * Parses mini-notation and returns the resulting pattern. Effectively an alias for [seq].
 *
 * Mini-notation is the compact pattern language for expressing sequences, sub-sequences,
 * alternations, and other rhythmic structures inline as strings.
 *
 * @param patterns Strings or other pattern-like values to parse as mini-notation.
 * @return A pattern built from the mini-notation input
 *
 * ```KlangScript(Playable)
 * mini("c d e f").note()  // Four notes, one per quarter cycle
 * ```
 *
 * ```KlangScript(Playable)
 * mini("bd [sd cp] hh").s()  // Nested sub-sequence in square brackets
 * ```
 * @category structural
 * @tags mini, notation, parse, sequence
 */
@KlangScript.Function
fun mini(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    patterns.toList().asSprudelDslArgs(callInfo).toPattern()

/**
 * Parses this string as mini-notation and returns the resulting pattern.
 *
 * ```KlangScript(Playable)
 * "c d e f".mini().note()  // Four notes from mini-notation string
 * ```
 */
@KlangScript.Function
fun String.mini(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation)

// -- pure() -----------------------------------------------------------------------------------------------------------

/**
 * Creates an atomic pattern that repeats a single value every cycle.
 *
 * @param value The value to wrap in a pattern.
 * @return A pattern that emits `value` once per cycle
 *
 * ```KlangScript(Playable)
 * pure("c").note()  // repeats note "c" every cycle
 * ```
 *
 * ```KlangScript(Playable)
 * pure(1)  // repeats the number 1 every cycle
 * ```
 * @category structural
 * @tags pure, value, atomic, repeat
 */
@KlangScript.Function
fun pure(value: PatternLike, @Suppress("unused") callInfo: CallInfo? = null): SprudelPattern =
    AtomicPattern(createSprudelVoiceData().also { it.value = value.asVoiceValue() })

// -- run() ------------------------------------------------------------------------------------------------------------

private fun applyRun(n: Int): SprudelPattern {
    // TODO: support control pattern

    if (n <= 0) return silence
    // "0 1 2 ... n-1" — build the integer ramp directly as a sequence.
    val items = (0 until n).map {
        AtomicPattern(createSprudelVoiceData { value = it.asVoiceValue() })
    }

    return SequencePattern(items)
}

/**
 * Creates a discrete pattern of integers from 0 to `n - 1`.
 *
 * Equivalent to `n("0 1 2 … n-1")`. Useful for driving scale or sample index patterns.
 *
 * @param n Number of steps; the pattern produces values 0, 1, …, n-1.
 * @return A sequential pattern of integers from 0 to n-1.
 *
 * ```KlangScript(Playable)
 * n(run(4)).scale("C4:pentatonic")  // 4 scale degrees per cycle
 * ```
 *
 * ```KlangScript(Playable)
 * n(run(8)).s("piano")  // 8 sequential notes
 * ```
 * @category structural
 * @tags run, sequence, range, index, discrete
 */
@KlangScript.Function
fun run(n: Int, @Suppress("unused") callInfo: CallInfo? = null): SprudelPattern = applyRun(n)

// -- binaryN() --------------------------------------------------------------------------------------------------------

private fun applyBinaryN(n: Int, bits: Int): SprudelPattern {
    if (bits <= 0) return silence

    val items = (0 until bits).map { i ->
        // Lay the bits out most-significant first (big-endian), e.g.
        // binaryN(55532, 16) -> "1 1 0 1 1 0 0 0 1 1 1 0 1 1 0 0".

        // Shift: bits - 1 - i
        val shift = bits - 1 - i
        val bit = (n shr shift) and 1
        AtomicPattern(createSprudelVoiceData { value = bit.asVoiceValue() })
    }
    return SequencePattern(items)
}

/**
 * Creates a binary pattern from a number, padded to `bits` bits long (MSB first).
 *
 * @param n    The integer to convert to binary.
 * @param bits Total pattern length in bits (default 16).
 * @return A sequential pattern of 0s and 1s representing the binary value.
 *
 * ```KlangScript(Playable)
 * s("hh").struct(binaryN(9, 4))  // 1 0 0 1 — binary 9 in 4 bits
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh").struct(binaryN(146, 8))  // 1 0 0 1 0 0 1 0 - binary 146 in 8 bits
 * ```
 * @category structural
 * @tags binaryN, binary, bits, structure, pattern
 */
@KlangScript.Function
fun binaryN(n: Int, bits: Int = 16, @Suppress("unused") callInfo: CallInfo? = null): SprudelPattern = applyBinaryN(n, bits)

// -- binary() ---------------------------------------------------------------------------------------------------------

/**
 * Creates a binary pattern from a number, with bit length calculated automatically.
 *
 * @param n The integer to convert to binary.
 * @return A sequential pattern of 0s and 1s with minimal bit width.
 *
 * ```KlangScript(Playable)
 * s("hh").struct(binary(5))  // 1 0 1 — 3 bits
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh").struct(binary(13))  // 1 1 0 1 — 4 bits
 * ```
 * @category structural
 * @tags binary, binaryN, bits, structure, pattern
 */
@KlangScript.Function
fun binary(n: Int, @Suppress("unused") callInfo: CallInfo? = null): SprudelPattern {
    return if (n == 0) {
        applyBinaryN(0, 1)
    } else {
        // Calculate bits: floor(log2(n)) + 1
        val bits = floor(log2(abs(n).toDouble())).toInt() + 1
        applyBinaryN(n, bits)
    }
}

// -- binaryNL() -------------------------------------------------------------------------------------------------------

private fun applyBinaryNL(n: Int, bits: Int): SprudelPattern {
    if (bits <= 0) return silence

    val bitList = (0 until bits).mapNotNull { i ->
        // Shift: bits - 1 - i (MSB first)
        val shift = bits - 1 - i
        val bit = (n shr shift) and 1
        bit.asVoiceValue()
    }

    // Returns a single event containing the list of bits as a Seq value
    return AtomicPattern(
        createSprudelVoiceData { value = SprudelVoiceValue.Seq(bitList) }
    )
}

/**
 * Creates a binary list pattern from a number, padded to `bits` bits long.
 *
 * Like [binaryN] but returns the bits as a single event containing a list value rather than
 * a sequence of discrete events.
 *
 * @param n    The integer to convert to binary.
 * @param bits Total length in bits (default 16).
 * @return A single-event pattern whose value is a list of 0s and 1s.
 *
 * ```KlangScript(Playable)
 * s("hh").struct(binaryNL(5, 4))  // list [0, 1, 0, 1]
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh").struct(binaryNL(255, 8))  // list [1, 1, 1, 1, 1, 1, 1, 1]
 * ```
 * @category structural
 * @tags binaryNL, binary, bits, list, structure
 */
@KlangScript.Function
fun binaryNL(n: Int, bits: Int = 16, @Suppress("unused") callInfo: CallInfo? = null): SprudelPattern = applyBinaryNL(n, bits)

// -- binaryL() --------------------------------------------------------------------------------------------------------

/**
 * Creates a binary list pattern from a number, with bit length calculated automatically.
 *
 * Like [binary] but returns bits as a list value in a single event.
 *
 * @param n The integer to convert to binary.
 * @return A single-event pattern whose value is a list of 0s and 1s with minimal bit width.
 *
 * ```KlangScript(Playable)
 * s("hh").struct(binaryL(5))  // list [1, 0, 1]
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh").struct(binaryL(13))  // list [1, 1, 0, 1]
 * ```
 * @category structural
 * @tags binaryL, binaryNL, binary, bits, list, structure
 */
@KlangScript.Function
fun binaryL(n: Int, @Suppress("unused") callInfo: CallInfo? = null): SprudelPattern {
    return if (n == 0) {
        applyBinaryNL(0, 1)
    } else {
        // Calculate bits: floor(log2(n)) + 1
        val bits = floor(log2(abs(n).toDouble())).toInt() + 1
        applyBinaryNL(n, bits)
    }
}

// -- morse() ----------------------------------------------------------------------------------------------------------

private val morseMap = mapOf(
    // Letters
    'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
    'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
    'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
    'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
    'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
    'Z' to "--..",
    // Umlauts and special chars
    'Ä' to ".-.-", 'Ö' to "---.", 'Ü' to "..--", 'ß' to "...--..",
    'É' to "..-..", 'Ñ' to "--.--", 'À' to ".--.-", 'È' to ".-..-",
    // Numbers
    '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-", '5' to ".....",
    '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----.", '0' to "-----",
    // Punctuation
    '.' to ".-.-.-", ',' to "--..--", '?' to "..--..", '\'' to ".----.",
    '!' to "-.-.--", '/' to "-..-.", '(' to "-.--.", ')' to "-.--.-",
    '&' to ".-...", ':' to "---...", ';' to "-.-.-.", '=' to "-...-",
    '+' to ".-.-.", '-' to "-....-", '_' to "..--.-", '"' to ".-..-.",
    '$' to "...-..-", '@' to ".--.-."
)

// Lookup tables for stripping accents from uppercase characters (Latin-1 + Latin Extended-A)
// Covers: ÀÁÂÃÄÅĀĂĄ ÇĆĈĊČ ĎĐ ÈÉÊËĒĔĖĘĚ ĜĞĠĢ ĤĦ ÌÍÎÏĨĪĬĮİ ĴĶ ĹĻĽĿŁ ÑŃŅŇ ÒÓÔÕÖØŌŎŐ ŔŖŘ ŚŜŞŠ ŢŤŦ ÙÚÛÜŨŪŬŮŰŲ Ŵ ÝŶŸ ŹŻŽ
private const val ACCENT_CHARS =
    "ÀÁÂÃÄÅĀĂĄÇĆĈĊČĎĐÈÉÊËĒĔĖĘĚĜĞĠĢĤĦÌÍÎÏĨĪĬĮİĴĶĹĻĽĿŁÑŃŅŇÒÓÔÕÖØŌŎŐŔŖŘŚŜŞŠŢŤŦÙÚÛÜŨŪŬŮŰŲŴÝŶŸŹŻŽ"

private const val BASE_CHARS =
    "AAAAAAAAACCCCCDDEEEEEEEEEGGGGHHIIIIIIIIIJKLLLLLNNNNOOOOOOOOORRRSSSSTTTUUUUUUUUUUWYYYZZZ"

private fun Char.stripAccents(): Char {
    val index = ACCENT_CHARS.indexOf(this)
    return if (index >= 0) BASE_CHARS[index] else this
}

private fun applyMorse(textArg: SprudelDslArg<Any?>?): SprudelPattern {
    val text = textArg?.value?.toString() ?: return silence
    if (text.isEmpty()) return silence

    val baseLoc = textArg.location
    val patterns = mutableListOf<SprudelPattern>()
    var totalWeight = 0.0

    // Helper to add a weighted pattern
    fun add(p: SprudelPattern, weight: Double) {
        patterns.add(PropertyOverridePattern(p, weightOverride = weight))
        totalWeight += weight
    }

    // Helper to create a note pattern with location
    fun createNote(charIndex: Int): SprudelPattern {
        val loc = baseLoc?.let {
            // Adjust for quotes: startColumn points to the opening quote.
            // So the content starts at startColumn + 1.
            // We create a location for the specific character with length 1.
            val charStartCol = it.startColumn + 1 + charIndex

            it.copy(
                startColumn = charStartCol,
                endColumn = charStartCol + 1
            )
        }

        val chain = loc?.let { SourceLocationChain.single(it) }

        // "x" is the standard note for rhythm/struct
        return AtomicPattern(
            data = createSprudelVoiceData { value = SprudelVoiceValue.Num(1.0) },
            sourceLocations = chain
        )
    }

    var isFirstWord = true
    var isFirstCharInWord = true

    text.forEachIndexed { index, char ->
        if (char.isWhitespace()) {
            isFirstCharInWord = true
            return@forEachIndexed
        }

        val upperChar = char.uppercaseChar()

        // Logic:
        // 1. Try to find the exact character (e.g. 'É', 'Ä')
        // 2. If not found, try stripping accents (e.g. 'Ê' -> 'E')
        val symbols = morseMap[upperChar] ?: morseMap[upperChar.stripAccents()]

        if (symbols != null) {
            // Handle gaps
            if (isFirstCharInWord) {
                if (!isFirstWord) {
                    // Word gap: 7 units
                    add(silence, 7.0)
                }
                isFirstWord = false
                isFirstCharInWord = false
            } else {
                // Intra-word (letter) gap: 3 units
                add(silence, 3.0)
            }

            // Add symbols
            symbols.forEachIndexed { sIndex, symbol ->
                if (sIndex > 0) {
                    // Intra-char (symbol) gap: 1 unit
                    add(silence, 1.0)
                }

                if (symbol == '.') {
                    // Dot: 1 unit
                    add(createNote(index), 1.0)
                } else {
                    // Dash: 3 units
                    add(createNote(index), 3.0)
                }
            }
        }
    }

    // Add trailing word gap to ensure separation when looping
    add(silence, 7.0)

    if (patterns.isEmpty()) return silence

    val seq = SequencePattern(patterns)

    // Slow down to maintain constant speed (1 unit = 1/8 cycle)
    return seq.slow(totalWeight / 16.0) // using 1/16th cycle per unit for a tighter rhythm
}

/**
 * Structures this pattern using a Morse code rhythm derived from the given text.
 *
 * Dots are 1 unit; dashes are 3 units. Gaps are inserted automatically:
 * 1 unit between symbols within a character, 3 units between characters, 7 units between words.
 *
 * ```KlangScript(Playable)
 * note("c3 d3 e3").morse("sos")         // structure notes with SOS rhythm
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh").morse("hello world!")                   // structure a kick with "hi" Morse code
 * ```
 *
 * @param text The text to encode as Morse code. Case-insensitive; unknown characters are skipped.
 *
 * @category structural
 * @tags morse, code, rhythm, structure, pattern
 */
@KlangScript.Function
fun SprudelPattern.morse(text: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.struct(applyMorse(listOf(text).asSprudelDslArgs(callInfo).firstOrNull()))

/**
 * Parses this string as a pattern and structures it using a Morse code rhythm.
 *
 * ```KlangScript(Playable)
 * "hh".morse("hi").s()            // structure a sawtooth sound with "hi" Morse code
 * ```
 *
 * @param text The text to encode as Morse code. Case-insensitive; unknown characters are skipped.
 */
@KlangScript.Function
fun String.morse(text: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).morse(text, callInfo)

/**
 * Creates a rhythmic pattern from a string using Morse code timing.
 *
 * The resulting pattern contains events with value `1.0` for dots and dashes, separated by silences.
 * Use this to drive a `struct` or as a standalone rhythm, then layer notes or sounds on top.
 *
 * ```KlangScript(Playable)
 * morse("sos").note("c4")               // SOS rhythm as notes on c4
 * ```
 *
 * ```KlangScript(Playable)
 * morse("hello world").s("hh")          // encode a message as a kick drum pattern
 * ```
 *
 * @param text The text to encode as Morse code. Case-insensitive; unknown characters are skipped.
 */
@KlangScript.Function
fun morse(text: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyMorse(listOf(text).asSprudelDslArgs(callInfo).firstOrNull())

