package io.peekandpoke.klang.strudel.lang.addons

import io.peekandpoke.klang.script.ast.SourceLocationChain
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.StrudelVoiceValue
import io.peekandpoke.klang.strudel.lang.*
import io.peekandpoke.klang.strudel.pattern.AtomicPattern
import io.peekandpoke.klang.strudel.pattern.PropertyOverridePattern
import io.peekandpoke.klang.strudel.pattern.SequencePattern

/**
 * ADDONS: Structural functions that are NOT available in the original strudel impl
 */

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangStructuralAddonsInit = false


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
    "AAAAAAAAACCCCCDDEEEEEEEEEGGGGHHIIIIIIIIIIJKLLLLLNNNNOOOOOOOOORRRSSSSSTTTUUUUUUUUUWYYYZZZ"

private fun Char.stripAccents(): Char {
    val index = ACCENT_CHARS.indexOf(this)
    return if (index >= 0) BASE_CHARS[index] else this
}

private fun applyMorse(textArg: StrudelDslArg<Any?>?): StrudelPattern {
    val text = textArg?.value?.toString() ?: return silence
    if (text.isEmpty()) return silence

    val baseLoc = textArg.location
    val patterns = mutableListOf<StrudelPattern>()
    var totalWeight = 0.0

    // Helper to add a weighted pattern
    fun add(p: StrudelPattern, weight: Double) {
        patterns.add(PropertyOverridePattern(p, weightOverride = weight))
        totalWeight += weight
    }

    // Helper to create a note pattern with location
    fun createNote(charIndex: Int): StrudelPattern {
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
            data = StrudelVoiceData.empty.copy(value = StrudelVoiceValue.Num(1.0)),
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
 * Creates a pattern from a string using Morse code.
 * Dots are 1 step, dashes are 3 steps.
 * Gaps are inserted automatically: 1 step between symbols, 3 steps between letters, 7 steps between words.
 */
@StrudelDsl
val morse by dslFunction { args, /* callInfo */ _ ->
    applyMorse(args.firstOrNull())
}

/**
 * Structures the pattern using a Morse code rhythm.
 *
 * @example
 * note("a").morse("sos")
 * // equivalent to: note("a").struct(morse("sos"))
 */
@StrudelDsl
val StrudelPattern.morse by dslPatternExtension { p, args, /* callInfo */ _ ->
    p.struct(applyMorse(args.firstOrNull()))
}

/**
 * Structures the pattern using a Morse code rhythm.
 */
@StrudelDsl
val String.morse by dslStringExtension { p, args, callInfo -> p.morse(args, callInfo) }
