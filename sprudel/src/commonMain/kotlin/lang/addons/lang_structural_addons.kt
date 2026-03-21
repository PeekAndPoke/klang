@file:Suppress("ObjectPropertyName")

package io.peekandpoke.klang.sprudel.lang.addons

import io.peekandpoke.klang.common.SourceLocationChain
import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.sprudel.StrudelPattern
import io.peekandpoke.klang.sprudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.StrudelPatternEvent
import io.peekandpoke.klang.sprudel.StrudelVoiceData
import io.peekandpoke.klang.sprudel.StrudelVoiceValue
import io.peekandpoke.klang.sprudel.lang.*
import io.peekandpoke.klang.sprudel.lang.StrudelDslArg.Companion.asStrudelDslArg
import io.peekandpoke.klang.sprudel.lang.StrudelDslArg.Companion.asStrudelDslArgs
import io.peekandpoke.klang.sprudel.lang.addons.pattern.MergePattern
import io.peekandpoke.klang.sprudel.lang.addons.pattern.SoloPattern
import io.peekandpoke.klang.sprudel.pattern.AtomicPattern
import io.peekandpoke.klang.sprudel.pattern.PropertyOverridePattern
import io.peekandpoke.klang.sprudel.pattern.SequencePattern

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
    "AAAAAAAAACCCCCDDEEEEEEEEEGGGGHHIIIIIIIIIJKLLLLLNNNNOOOOOOOOORRRSSSSTTTUUUUUUUUUUWYYYZZZ"

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
            data = StrudelVoiceData.empty.copy(value = StrudelVoiceValue.Num(Rational.ONE)),
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

internal val StrudelPattern._morse by dslPatternExtension { p, args, /* callInfo */ _ ->
    p.struct(applyMorse(args.firstOrNull()))
}

internal val String._morse by dslStringExtension { p, args, callInfo -> p._morse(args, callInfo) }

internal val _morse by dslPatternFunction { args, /* callInfo */ _ ->
    applyMorse(args.firstOrNull())
}

// ===== USER-FACING OVERLOADS =====

/**
 * Structures this pattern using a Morse code rhythm derived from the given text.
 *
 * Dots are 1 unit; dashes are 3 units. Gaps are inserted automatically:
 * 1 unit between symbols within a character, 3 units between characters, 7 units between words.
 *
 * ```KlangScript
 * note("c3 d3 e3").morse("sos")         // structure notes with SOS rhythm
 * ```
 *
 * ```KlangScript
 * s("hh").morse("hello world!")                   // structure a kick with "hi" Morse code
 * ```
 *
 * @param text The text to encode as Morse code. Case-insensitive; unknown characters are skipped.
 *
 * @category structural
 * @tags morse, code, rhythm, structure, pattern, addon
 */
@StrudelDsl
fun StrudelPattern.morse(text: PatternLike): StrudelPattern = this._morse(listOf(text).asStrudelDslArgs())

/**
 * Parses this string as a pattern and structures it using a Morse code rhythm.
 *
 * ```KlangScript
 * "hh".morse("hi").s()            // structure a sawtooth sound with "hi" Morse code
 * ```
 *
 * @param text The text to encode as Morse code. Case-insensitive; unknown characters are skipped.
 */
@StrudelDsl
fun String.morse(text: PatternLike): StrudelPattern = this._morse(listOf(text).asStrudelDslArgs())

/**
 * Creates a rhythmic pattern from a string using Morse code timing.
 *
 * The resulting pattern contains events with value `1.0` for dots and dashes, separated by silences.
 * Use this to drive a `struct` or as a standalone rhythm, then layer notes or sounds on top.
 *
 * ```KlangScript
 * morse("sos").note("c4")               // SOS rhythm as notes on c4
 * ```
 *
 * ```KlangScript
 * morse("hello world").s("hh")          // encode a message as a kick drum pattern
 * ```
 *
 * @param text The text to encode as Morse code. Case-insensitive; unknown characters are skipped.
 */
@StrudelDsl
fun morse(text: PatternLike): StrudelPattern = _morse(listOf(text).asStrudelDslArgs())

// -- merge() ----------------------------------------------------------------------------------------------------------

private fun applyMerge(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val ctrl = args.toPattern()
    return MergePattern(source = pattern, control = ctrl)
}

internal val StrudelPattern._merge by dslPatternExtension { p, args, _ -> applyMerge(p, args) }
internal val String._merge by dslStringExtension { p, args, callInfo -> p._merge(args, callInfo) }
internal val _merge by dslPatternMapper { args, callInfo -> { p -> p._merge(args, callInfo) } }
internal val PatternMapperFn._merge by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_merge(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Overlays voice properties from a control pattern onto this pattern's events.
 *
 * For each source event the control is sampled at the event's onset time. Non-null fields
 * from the control's [StrudelVoiceData] override the corresponding fields in the source event.
 * Source fields that the control leaves `null` are kept unchanged.
 *
 * ```KlangScript
 * s("hh hh hh hh").merge(note("c3 d3 e3 f3"))   // high-hat gains note values from the note pattern
 * ```
 *
 * ```KlangScript
 * s("hh!8").merge(freq("100 200 300 400"))   // high-hat gains frequencies
 * ```
 *
 * ```KlangScript
 * s("hh!8").merge("x!8".freq(sine.range(100, 1000)))   // high-hat gains frequencies
 * ```
 *
 * ```KlangScript
 * note("<[c3 d3] [e3 f3]>").merge(seq("<0.2 0.4 0.6 0.8>").warmth())   // notes gain warmth per event
 * ```
 *
 * @param ctrl The pattern (or mini-notation string) whose voice data is merged in.
 *
 * @category structural
 * @tags merge, overlay, combine, voice, data, addon
 */
@StrudelDsl
fun StrudelPattern.merge(ctrl: PatternLike): StrudelPattern =
    this._merge(ctrl.asStrudelDslArg())

/**
 * Parses this string as a pattern and overlays voice properties from the control pattern.
 *
 * ```KlangScript
 * "1 2 3 4".merge("<0.2 0.4 0.6 0.8>".warmth()).scale("c3:major").n()   // value sequence gains warmth from control
 * ```
 *
 * @param ctrl The pattern (or mini-notation string) whose voice data is merged in.
 */
@StrudelDsl
fun String.merge(ctrl: PatternLike): StrudelPattern =
    this._merge(ctrl.asStrudelDslArg())

/**
 * Creates a [PatternMapperFn] that overlays voice properties from the control pattern.
 *
 * ```KlangScript
 * seq("1 2").apply(merge(seq("0.3 0.7").warmth())).scale("c3:major").n()   // apply warmth overlay as a mapper
 * ```
 *
 * @param ctrl The pattern (or mini-notation string) whose voice data is merged in.
 */
@StrudelDsl
fun merge(ctrl: PatternLike): PatternMapperFn = _merge(ctrl.asStrudelDslArg())

/**
 * Chains a voice-data overlay onto this [PatternMapperFn].
 *
 * @param ctrl The pattern (or mini-notation string) whose voice data is merged in.
 */
@StrudelDsl
fun PatternMapperFn.merge(ctrl: PatternLike): PatternMapperFn = _merge(ctrl.asStrudelDslArg())

// -- timeLoop() -------------------------------------------------------------------------------------------------------

/**
 * Core implementation: loops this pattern within the given duration in cycles.
 * Effectively repeats the pattern segment `[0, duration]` every `duration` cycles.
 */
fun StrudelPattern.timeLoop(duration: Rational): StrudelPattern {
    if (duration <= Rational.ZERO) return silence

    val source = this
    return object : StrudelPattern {
        override val weight: Double get() = source.weight
        override val numSteps: Rational? get() = source.numSteps
        override fun estimateCycleDuration(): Rational = duration

        override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
            val result = mutableListOf<StrudelPatternEvent>()

            // Calculate loop range covering [from, to]
            val startLoop = (from / duration).floor()

            // Loop through cycles
            var k = startLoop
            while (k * duration < to) {
                val loopStart = k * duration

                // Intersection of query with this loop
                val qStart = maxOf(from, loopStart)
                val qEnd = minOf(to, loopStart + duration)

                if (qStart < qEnd) {
                    // Map to local time [0, duration]
                    val localStart = qStart - loopStart
                    val localEnd = qEnd - loopStart

                    // Query source at [localStart, localEnd]
                    val events = source.queryArcContextual(localStart, localEnd, ctx)

                    // Shift events back to global time
                    for (ev in events) {
                        result.add(
                            ev.copy(
                                part = ev.part.shift(loopStart),
                                whole = ev.whole.shift(loopStart)
                            )
                        )
                    }
                }

                k += Rational.ONE
            }

            return result
        }
    }
}

private fun applyTimeLoop(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val duration = args.firstOrNull()?.value?.asRationalOrNull() ?: return source
    return source.timeLoop(duration)
}

internal val StrudelPattern._timeLoop by dslPatternExtension { p, args, _ -> applyTimeLoop(p, args) }

internal val String._timeLoop by dslStringExtension { p, args, callInfo -> p._timeLoop(args, callInfo) }

internal val _timeLoop by dslPatternMapper { args, callInfo -> { p -> p._timeLoop(args, callInfo) } }
internal val PatternMapperFn._timeLoop by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(
        _timeLoop(
            args,
            callInfo
        )
    )
}

// ===== USER-FACING OVERLOADS =====

/**
 * Loops this pattern within a fixed window of `duration` cycles, tiling it indefinitely.
 *
 * Unlike [fast] or [slow], `timeLoop` does not stretch or compress events — it freezes the
 * segment `[0, duration]` of this pattern and tiles it. Events outside the window are never
 * played. Useful for creating ostinato figures or locking a long sequence to a shorter loop.
 *
 * ```KlangScript
 * note("c3 d3 e3 f3 g3 a3 b3 c4").timeLoop(2)   // loop first 2 cycles of an 8-note run
 * ```
 *
 * ```KlangScript
 * s("bd sd hh oh").timeLoop(0.5)                 // stutter a 4-beat pattern into 2-beat loops
 * ```
 *
 * @param duration The loop window length in cycles. Must be greater than zero.
 *
 * @category structural
 * @tags timeLoop, loop, repeat, cycle, ostinato, window, addon
 */
@StrudelDsl
fun StrudelPattern.timeLoop(duration: PatternLike): StrudelPattern =
    this._timeLoop(listOf(duration).asStrudelDslArgs())

/**
 * Parses this string as a pattern and loops it within a fixed window of `duration` cycles.
 *
 * ```KlangScript
 * "c3 d3 e3 f3".timeLoop(0.5)   // loop the first half-cycle of a 4-note sequence
 * ```
 *
 * @param duration The loop window length in cycles. Must be greater than zero.
 */
@StrudelDsl
fun String.timeLoop(duration: PatternLike): StrudelPattern =
    this._timeLoop(listOf(duration).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that loops its input within a fixed window of `duration` cycles.
 *
 * ```KlangScript
 * note("c3 d3 e3 f3 g3 a3 b3 c4").apply(timeLoop(2))   // loop the first 2 cycles of an 8-note run
 * ```
 *
 * ```KlangScript
 * s("bd sd hh oh").apply(timeLoop(0.5))                 // stutter a 4-beat pattern into 2-beat loops
 * ```
 *
 * @param duration The loop window length in cycles. Must be greater than zero.
 *
 * @category structural
 * @tags timeLoop, loop, repeat, cycle, ostinato, window, addon
 */
@StrudelDsl
fun timeLoop(duration: PatternLike): PatternMapperFn =
    _timeLoop(listOf(duration).asStrudelDslArgs())

/**
 * Chains a timeLoop operation onto this [PatternMapperFn], looping the result within `duration` cycles.
 *
 * ```KlangScript
 * seq("0.2 0.4").apply(mul(2).timeLoop(0.5))   // mul doubles, then loop within 0.5 cycles
 * ```
 *
 * @param duration The loop window length in cycles. Must be greater than zero.
 */
@StrudelDsl
fun PatternMapperFn.timeLoop(duration: PatternLike): PatternMapperFn =
    _timeLoop(listOf(duration).asStrudelDslArgs())

// -- repeat() ---------------------------------------------------------------------------------------------------------

private fun applyRepeat(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val times = args.firstOrNull()?.value?.asIntOrNull() ?: 1
    if (times <= 0) return silence
    if (times == 1) return pattern
    val patterns = List(times) { pattern }
    return applyCat(patterns)
}

internal val StrudelPattern._repeat by dslPatternExtension { p, args, _ -> applyRepeat(p, args) }
internal val String._repeat by dslStringExtension { p, args, callInfo -> p._repeat(args, callInfo) }
internal val _repeat by dslPatternMapper { args, callInfo -> { p -> p._repeat(args, callInfo) } }
internal val PatternMapperFn._repeat by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(
        _repeat(
            args,
            callInfo
        )
    )
}

// ===== USER-FACING OVERLOADS =====

/**
 * Repeats this pattern `times` times sequentially.
 *
 * The total duration becomes `times × original_duration`. Unlike [fast], which compresses events
 * into fewer cycles, each repetition occupies its own full cycle. Useful for extending a short
 * pattern to fill multiple bars before it loops.
 *
 * ```KlangScript
 * note("a b").repeat(2)              // plays "a b a b" spread over 2 cycles
 * ```
 *
 * ```KlangScript
 * s("bd sd hh cp").repeat(4)         // loop a 4-beat bar four times before cycling
 * ```
 *
 * @param times The number of times to repeat. `0` returns silence; `1` returns the pattern unchanged.
 *
 * @category structural
 * @tags repeat, loop, duplicate, sequence, addon
 */
@StrudelDsl
fun StrudelPattern.repeat(times: PatternLike): StrudelPattern = this._repeat(listOf(times).asStrudelDslArgs())

/**
 * Parses this string as a pattern and repeats it `times` times sequentially.
 *
 * ```KlangScript
 * "a b".repeat(3).note()             // plays "a b a b a b" spread over 3 cycles
 * ```
 *
 * ```KlangScript
 * "bd sd".repeat(2).s()              // double-length drum bar
 * ```
 *
 * @param times The number of times to repeat. `0` returns silence; `1` returns the pattern unchanged.
 */
@StrudelDsl
fun String.repeat(times: PatternLike): StrudelPattern = this._repeat(listOf(times).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that repeats the input pattern `times` times sequentially.
 *
 * ```KlangScript
 * note("a b").apply(repeat(2))       // plays "a b a b" spread over 2 cycles
 * ```
 *
 * ```KlangScript
 * s("bd sd").apply(repeat(3))        // triple-length drum bar via mapper
 * ```
 *
 * @param times The number of times to repeat. `0` returns silence; `1` returns the pattern unchanged.
 *
 * @category structural
 * @tags repeat, loop, duplicate, sequence, addon
 */
@StrudelDsl
fun repeat(times: PatternLike): PatternMapperFn = _repeat(listOf(times).asStrudelDslArgs())

/**
 * Chains a repeat operation onto this [PatternMapperFn], repeating the result `times` times.
 *
 * ```KlangScript
 * note("a b").apply(fast(2).repeat(2))   // fast doubles density, repeat duplicates over 2 cycles
 * ```
 *
 * @param times The number of times to repeat. `0` returns silence; `1` returns the pattern unchanged.
 */
@StrudelDsl
fun PatternMapperFn.repeat(times: PatternLike): PatternMapperFn = _repeat(listOf(times).asStrudelDslArgs())

// -- solo() -----------------------------------------------------------------------------------------------------------

private fun applySolo(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val effectiveArgs = args.ifEmpty { listOf(StrudelDslArg.of(0.97)) }
    val soloControl = effectiveArgs.first().toPattern()
    return SoloPattern(source = source, soloControl = soloControl)
}

internal val StrudelPattern._solo by dslPatternExtension { p, args, _ -> applySolo(p, args) }
internal val String._solo by dslStringExtension { p, args, callInfo -> p._solo(args, callInfo) }
internal val _solo by dslPatternMapper { args, callInfo -> { p -> p._solo(args, callInfo) } }
internal val PatternMapperFn._solo by dslPatternMapperExtension { m, args, callInfo -> m.chain(_solo(args, callInfo)) }

// ===== USER-FACING OVERLOADS =====

/**
 * Solos this pattern, muting all non-soloed patterns during playback.
 *
 * Pass a value between `0.0` (no solo) and `1.0` (full solo). Omit or pass `null` to use
 * the default amount of `0.97`. Accepts control patterns for per-cycle dynamic toggling.
 *
 * ```KlangScript
 * stack(
 *   s("bd*4").solo(),             // only the kick is heard (amount = 0.97)
 *   note("c3 e3")                 // muted because another pattern is soloed
 * )
 * ```
 *
 * ```KlangScript
 * s("bd sd").solo(1)              // full solo
 * ```
 *
 * ```KlangScript
 * s("bd sd").solo(0.5)            // half solo amount
 * ```
 *
 * ```KlangScript
 * s("hh*8").solo("<1 0>")         // toggle solo on/off every other cycle
 * ```
 *
 * @param amount `0.0`..`1.0` solo strength; `null` defaults to `0.97`. Accepts control patterns.
 *
 * @category structural
 * @tags solo, mute, isolate, playback, addon
 */
@StrudelDsl
fun StrudelPattern.solo(amount: PatternLike? = null): StrudelPattern =
    this._solo(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Parses this string as a pattern and solos it, muting all non-soloed patterns.
 *
 * ```KlangScript
 * "bd sd".solo().s()              // solo this string pattern; everything else is muted
 * ```
 *
 * ```KlangScript
 * "bd sd".solo("<1 0>").s()       // toggle solo on/off every other cycle
 * ```
 *
 * @param amount `0.0`..`1.0` solo strength; `null` defaults to `0.97`. Accepts control patterns.
 */
@StrudelDsl
fun String.solo(amount: PatternLike? = null): StrudelPattern =
    this._solo(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that solos the input pattern, muting all non-soloed patterns.
 *
 * ```KlangScript
 * s("bd*4").apply(solo())         // solo the kick via a mapper (amount = 0.97)
 * ```
 *
 * ```KlangScript
 * s("hh*8").apply(solo("<1 0>"))  // toggle solo on/off every other cycle via a mapper
 * ```
 *
 * ```KlangScript
 * note("c3 e3 g3").apply(timeLoop(2).solo())   // loop then solo
 * ```
 *
 * @param amount `0.0`..`1.0` solo strength; `null` defaults to `0.97`. Accepts control patterns.
 *
 * @category structural
 * @tags solo, mute, isolate, playback, addon
 */
@StrudelDsl
fun solo(amount: PatternLike? = null): PatternMapperFn =
    _solo(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Chains a solo operation onto this [PatternMapperFn].
 *
 * ```KlangScript
 * s("bd*4").apply(timeLoop(2).solo())              // loop first 2 cycles, then solo
 * ```
 *
 * ```KlangScript
 * s("hh*8").apply(timeLoop(1).solo("<1 0>"))       // loop then conditionally solo
 * ```
 *
 * @param amount `0.0`..`1.0` solo strength; `null` defaults to `0.97`. Accepts control patterns.
 */
@StrudelDsl
fun PatternMapperFn.solo(amount: PatternLike? = null): PatternMapperFn =
    _solo(listOfNotNull(amount).asStrudelDslArgs())
