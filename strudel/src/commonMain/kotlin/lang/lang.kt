@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.audio_bridge.AdsrEnvelope
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.VoiceValue
import io.peekandpoke.klang.audio_bridge.VoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.parser.parseMiniNotation
import io.peekandpoke.klang.strudel.math.PerlinNoise
import io.peekandpoke.klang.strudel.pattern.*
import io.peekandpoke.klang.strudel.pattern.ContextModifierPattern.Companion.withContext
import io.peekandpoke.klang.strudel.pattern.ReinterpretPattern.Companion.reinterpretVoice
import io.peekandpoke.klang.tones.Tones
import io.peekandpoke.klang.tones.scale.Scale
import kotlin.math.PI
import kotlin.math.sin

@DslMarker
annotation class StrudelDsl

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Helpers
// ///

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangInit = false

/** Default modifier for patterns that don't have a specific semantic yet (like sequence or stack items) */
val defaultModifier: VoiceDataModifier = {
    copy(value = it?.asVoiceValue())
}

/** Cleans up the scale name */
private fun String.cleanScaleName() = replace(":", " ")

/** Safely convert any value to a double or null */
private fun Any.asDoubleOrNull(): Double? = when (this) {
    is Number -> this.toDouble()
    is String -> this.toDoubleOrNull()
    else -> null
}

/** Safely convert any value to an int or null */
private fun Any.asIntOrNull(): Int? = when (this) {
    is Number -> this.toInt()
    is String -> this.toDoubleOrNull()?.toInt()
    else -> null
}

/**
 * Resolves the note and frequency based on the index and the current scale.
 *
 * @param newIndex An optional new index to force (e.g. from n("0")).
 *                 If null, it tries to use existing soundIndex or interpret value as index.
 */
private fun VoiceData.resolveNote(newIndex: Int? = null): VoiceData {
    val effectiveScale = scale?.cleanScaleName()

    // Determine the effective index:
    // 1. Explicit argument (newIndex)
    // 2. Existing soundIndex
    // 3. Existing value interpreted as integer
    val n = newIndex ?: soundIndex ?: value?.asInt

    // Try to resolve note from index + scale
    if (n != null && !effectiveScale.isNullOrEmpty()) {
        val noteName = Scale.steps(effectiveScale).invoke(n)
        return copy(
            note = noteName,
            freqHz = Tones.noteToFreq(noteName),
            soundIndex = null, // this clears the sound-index
            value = null, // this also clears the value
        )
    }

    // Fallback cases

    // Case A: Explicit index was provided, but no scale found.
    // We must set the soundIndex.
    if (newIndex != null) {
        return copy(soundIndex = newIndex)
    }

    // Case B: Reinterpretation or fallback.
    // If we derived an index 'n' (e.g. from value), we preserve it.
    // We also ensure 'note' is populated (e.g. from 'value' if 'note' is missing).
    val fallbackNote = note ?: value?.asString

    return copy(
        note = fallbackNote,
        freqHz = Tones.noteToFreq(fallbackNote ?: ""),
        soundIndex = n ?: soundIndex
    )
}

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Continuous patterns
// ///

/** Empty pattern that does not produce any events */
@StrudelDsl
val silence by dslObject { EmptyPattern }

/** Empty pattern that does not produce any events */
@StrudelDsl
val rest by dslObject { silence }

/** Sine oscillator: 0 to 1, period of 1 cycle */
@StrudelDsl
val sine by dslObject {
    ContinuousPattern { t -> (sin(t * 2.0 * PI) + 1.0) / 2.0 }
}

/** Sawtooth oscillator: 0 to 1, period of 1 cycle */
@StrudelDsl
val saw by dslObject {
    ContinuousPattern { t -> t % 1.0 }
}

/** Inverse Sawtooth oscillator: 1 to 0, period of 1 cycle */
@StrudelDsl
val isaw by dslObject {
    ContinuousPattern { t -> 1.0 - (t % 1.0) }
}

/** Triangle oscillator: 0 to 1 to 0, period of 1 cycle */
@StrudelDsl
val tri by dslObject {
    ContinuousPattern { t ->
        val phase = t % 1.0
        if (phase < 0.5) phase * 2.0 else 2.0 - (phase * 2.0)
    }
}

/** Square oscillator: 0 or 1, period of 1 cycle */
@StrudelDsl
val square by dslObject {
    ContinuousPattern { t -> if (t % 1.0 < 0.5) 0.0 else 1.0 }
}

/** Square oscillator: 0 or 1, period of 1 cycle */
@StrudelDsl
val perlin by dslObject {
    ContinuousPattern { t -> (PerlinNoise.noise(t) + 1.0) / 2.0 }
}

/**
 * Sets the range of a continuous pattern to a new minimum and maximum value.
 */
@StrudelDsl
val StrudelPattern.range by dslPatternExtension { p, args ->
    // TODO: We must be able to provide these parameters from other patterns as well
    val min = args.getOrNull(0)?.asDoubleOrNull() ?: 0.0
    val max = args.getOrNull(1)?.asDoubleOrNull() ?: 1.0

    p.withContext {
        setIfAbsent(ContinuousPattern.minKey, min)
        setIfAbsent(ContinuousPattern.maxKey, max)
    }
}

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Structural patterns
// ///

// -- seq() ------------------------------------------------------------------------------------------------------------

/** Creates a sequence pattern. */
private fun applySeq(patterns: List<StrudelPattern>): StrudelPattern {
    return if (patterns.isEmpty()) EmptyPattern
    else if (patterns.size == 1) patterns.first()
    else SequencePattern(patterns)
}

/** Creates a sequence pattern. */
@StrudelDsl
val seq by dslFunction { args ->
    // specialized modifier for seq to prioritize value
    args.toPattern(defaultModifier)
}

@StrudelDsl
val StrudelPattern.seq by dslPatternExtension { p, args ->
    val patterns = listOf(p) + args.toListOfPatterns(defaultModifier)
    applySeq(patterns)
}

@StrudelDsl
val String.seq by dslStringExtension { p, args ->
    val patterns = listOf(p) + args.toListOfPatterns(defaultModifier)
    applySeq(patterns)
}

// -- stack() ----------------------------------------------------------------------------------------------------------

private fun applyStack(patterns: List<StrudelPattern>): StrudelPattern {
    return if (patterns.size == 1) patterns.first() else StackPattern(patterns)
}

/** Plays multiple patterns at the same time. */
@StrudelDsl
val stack by dslFunction { args ->
    // If the result of converting args is a SequencePattern, we treat its children as the stack elements.
    // If it's a single pattern, we wrap it.
    // Wait, the original logic was:
    // args.toPattern(defaultModifier).let { if (it is SequencePattern) StackPattern(it.patterns) else StackPattern(listOf(it)) }

    // This implies that stack("a", "b") -> toPattern -> Sequence("a", "b") -> Stack("a", "b").
    // stack("a") -> toPattern -> "a" -> Stack("a").
    // stack(seq("a", "b")) -> toPattern -> Sequence("a", "b") -> Stack("a", "b").

    // So we need to mimic this behavior or simply take the list of patterns.
    // If we use args.toListOfPatterns(defaultModifier), we get [a, b].
    // Then we can just wrap them in StackPattern.

    val patterns = args.toListOfPatterns(defaultModifier)
    if (patterns.isEmpty()) silence else StackPattern(patterns)
}

@StrudelDsl
val StrudelPattern.stack by dslPatternExtension { p, args ->
    val patterns = listOf(p) + args.toListOfPatterns(defaultModifier)
    applyStack(patterns)
}

@StrudelDsl
val String.stack by dslStringExtension { p, args ->
    val patterns = listOf(p) + args.toListOfPatterns(defaultModifier)
    applyStack(patterns)
}

// -- arrange() --------------------------------------------------------------------------------------------------------

private fun applyArrange(args: List<Any?>): StrudelPattern {
    val segments = args.map { arg ->
        when (arg) {
            // Case: pattern (defaults to 1 cycle)
            is StrudelPattern -> 1.0 to arg
            // Case: [2, pattern]
            is List<*> if arg.size == 2 && arg[0] is Number && arg[1] is StrudelPattern -> {
                val dur = (arg[0] as Number).toDouble()
                val pat = arg[1] as StrudelPattern
                dur to pat
            }
            // Case: [pattern] (defaults to 1 cycle)
            is List<*> if arg.size == 1 && arg[0] is StrudelPattern -> 1.0 to (arg[0] as StrudelPattern)
            // Unknown
            else -> 0.0 to silence
        }
    }.filter { it.first > 0.0 }

    return if (segments.isEmpty()) silence
    else ArrangementPattern(segments)
}

// arrange([2, a], b) -> 2 cycles of a, 1 cycle of b.
@StrudelDsl
val arrange by dslFunction { args ->
    applyArrange(args)
}

@StrudelDsl
val StrudelPattern.arrange by dslPatternExtension { p, args ->
    // When calling p.arrange(args), p is the first element (implicitly 1 cycle unless args says otherwise, but args are the *other* elements).
    // So it's effectively arrange(p, *args).
    applyArrange(listOf(p) + args)
}

@StrudelDsl
val String.arrange by dslStringExtension { p, args ->
    // "p".arrange(args) -> arrange("p", *args).
    // "p" needs to be converted to a pattern first because arrangeImpl expects patterns or [dur, pat] lists.
    // dslStringExtension passes 'p' as a Pattern (parsed via defaultModifier) to the handler?
    // Wait, looking at dslStringExtension in lang_helpers.kt:
    // It calls `val pattern = parse(recv)` then `handler(pattern, args)`.
    // So `p` here is `StrudelPattern`.
    applyArrange(listOf(p) + args)
}

// -- pickRestart() ----------------------------------------------------------------------------------------------------

// pickRestart([a, b, c]) -> picks patterns sequentially per cycle (slowcat)
// TODO: make this really work. Must be dslMethod -> https://strudel.cc/learn/conditional-modifiers/#pickrestart
@StrudelDsl
val pickRestart by dslFunction { args ->
    val patterns = args.toListOfPatterns(defaultModifier)
    if (patterns.isEmpty()) {
        silence
    } else {
        // seq plays all in 1 cycle. slow(n) makes each take 1 cycle.
        val s = SequencePattern(patterns)
        // We need to call .slow on the result. But 'slow' is a property delegate now!
        // In Kotlin code: s.slow(n). In internal code, we access the DslMethod 'slow'.
        s.slow(patterns.size)
    }
}

// -- cat() ------------------------------------------------------------------------------------------------------------

private fun applyCat(patterns: List<StrudelPattern>): StrudelPattern = when {
    patterns.isEmpty() -> silence
    patterns.size == 1 -> patterns.first()
    else -> ArrangementPattern(patterns.map { 1.0 to it })
}

@StrudelDsl
val cat by dslFunction { args ->
    applyCat(args.toListOfPatterns(defaultModifier))
}

@StrudelDsl
val StrudelPattern.cat by dslPatternExtension { p, args ->
    applyCat(listOf(p) + args.toListOfPatterns(defaultModifier))
}

@StrudelDsl
val String.cat by dslStringExtension { p, args ->
    applyCat(listOf(p) + args.toListOfPatterns(defaultModifier))
}

// -- struct() ---------------------------------------------------------------------------------------------------------

private fun applyStruct(source: StrudelPattern, structArg: Any?): StrudelPattern {
    val structure = when (structArg) {
        is StrudelPattern -> structArg
        is String -> parseMiniNotation(input = structArg) { AtomicPattern(VoiceData.empty.copy(note = it)) }
        else -> silence
    }

    return StructurePattern(
        source = source,
        other = structure,
        mode = StructurePattern.Mode.Out,
        filterByTruthiness = true
    )
}

/**
 * Structures the pattern according to another pattern (the mask).
 */
@StrudelDsl
val struct by dslFunction { args ->
    val structArg = args.getOrNull(0)
    val source = args.filterIsInstance<StrudelPattern>().let {
        if (it.size >= 2 && structArg is StrudelPattern) it[1] else it.firstOrNull()
    } ?: return@dslFunction silence

    applyStruct(source, structArg)
}

@StrudelDsl
val StrudelPattern.struct by dslPatternExtension { source, args ->
    applyStruct(source, args.firstOrNull())
}

@StrudelDsl
val String.struct by dslStringExtension { source, args ->
    applyStruct(source, args.firstOrNull())
}

// -- structAll() ------------------------------------------------------------------------------------------------------

private fun applyStructAll(source: StrudelPattern, structArg: Any?): StrudelPattern {
    val structure = when (structArg) {
        is StrudelPattern -> structArg
        is String -> parseMiniNotation(input = structArg) { AtomicPattern(VoiceData.empty.copy(note = it)) }
        else -> silence
    }

    // We use a different implementation for structAll that preserves all source events
    return StructurePattern(
        source = source,
        other = structure,
        mode = StructurePattern.Mode.Out,
        filterByTruthiness = false
    )
}

/**
 * Structures the pattern according to another pattern, keeping all source events that overlap.
 *
 * Example: structAll("x", note("c e")) -> keeps both c and e within the x window
 */
@StrudelDsl
val structAll by dslFunction { args ->
    val structArg = args.getOrNull(0)
    val source = args.filterIsInstance<StrudelPattern>().let {
        if (it.size >= 2 && structArg is StrudelPattern) it[1] else it.firstOrNull()
    } ?: return@dslFunction silence

    applyStructAll(source, structArg)
}

@StrudelDsl
val StrudelPattern.structAll by dslPatternExtension { source, args ->
    applyStructAll(source, args.firstOrNull())
}

@StrudelDsl
val String.structAll by dslStringExtension { source, args ->
    applyStructAll(source, args.firstOrNull())
}

private fun applyMask(source: StrudelPattern, maskArg: Any?): StrudelPattern {
    val maskPattern = when (maskArg) {
        is StrudelPattern -> maskArg
        is String -> parseMiniNotation(input = maskArg) { AtomicPattern(VoiceData.empty.copy(note = it)) }
        else -> silence
    }

    return StructurePattern(
        source = source,
        other = maskPattern,
        mode = StructurePattern.Mode.In,
        filterByTruthiness = true
    )
}

/**
 * Filters the pattern using a boolean mask.
 * Only events from the source that overlap with "truthy" events in the mask are kept.
 */
@StrudelDsl
val mask by dslFunction { args ->
    val maskArg = args.getOrNull(0)
    val source = args.filterIsInstance<StrudelPattern>().let {
        if (it.size >= 2 && maskArg is StrudelPattern) it[1] else it.firstOrNull()
    } ?: return@dslFunction silence

    applyMask(source, maskArg)
}

@StrudelDsl
val StrudelPattern.mask by dslPatternExtension { source, args ->
    applyMask(source, args.firstOrNull())
}

@StrudelDsl
val String.mask by dslStringExtension { source, args ->
    applyMask(source, args.firstOrNull())
}

// -- maskAll() --------------------------------------------------------------------------------------------------------

private fun applyMaskAll(source: StrudelPattern, maskArg: Any?): StrudelPattern {
    val maskPattern = when (maskArg) {
        is StrudelPattern -> maskArg
        is String -> parseMiniNotation(input = maskArg) { AtomicPattern(VoiceData.empty.copy(note = it)) }
        else -> silence
    }

    return StructurePattern(
        source = source,
        other = maskPattern,
        mode = StructurePattern.Mode.In,
        filterByTruthiness = false
    )
}

/**
 * Filters the pattern using a mask, keeping all source events that overlap with the mask's structure.
 */
@StrudelDsl
val maskAll by dslFunction { args ->
    val maskArg = args.getOrNull(0)
    val source = args.filterIsInstance<StrudelPattern>().let {
        if (it.size >= 2 && maskArg is StrudelPattern) it[1] else it.firstOrNull()
    } ?: return@dslFunction silence

    applyMaskAll(source, maskArg)
}

@StrudelDsl
val StrudelPattern.maskAll by dslPatternExtension { source, args ->
    applyMaskAll(source, args.firstOrNull())
}

@StrudelDsl
val String.maskAll by dslStringExtension { source, args ->
    applyMaskAll(source, args.firstOrNull())
}

/**
 * Layers a modified version of the pattern on top of itself.
 *
 * Example: s("bd sd").superimpose { it.fast(2) }
 */
@StrudelDsl
val StrudelPattern.superimpose by dslPatternExtension { source, args ->
    @Suppress("UNCHECKED_CAST")
    val transform = args.firstOrNull() as? (StrudelPattern) -> StrudelPattern ?: { it }
    SuperimposePattern(source, transform)
}

/**
 * Applies multiple transformation functions to the pattern and stacks the results.
 *
 * Example: s("bd").layer({ it.fast(2) }, { it.rev() })
 */
@StrudelDsl
val StrudelPattern.layer by dslPatternExtension { source, args ->
    val transforms = args.filterIsInstance<(StrudelPattern) -> StrudelPattern>()

    if (transforms.isEmpty()) {
        silence
    } else {
        val patterns = transforms.mapNotNull { transform ->
            try {
                transform(source)
            } catch (e: Exception) {
                println("Error applying layer transform: ${e.stackTraceToString()}")
                null
            }
        }

        if (patterns.size == 1) {
            patterns.first()
        } else {
            StackPattern(patterns)
        }
    }
}

/**
 * Alias for [layer].
 */
@StrudelDsl
val StrudelPattern.apply by dslPatternExtension { source, args ->
    source.layer(args)
}


// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Tempo / Timing / Order modifiers
// ///

// -- slow() -----------------------------------------------------------------------------------------------------------

private fun applySlow(pattern: StrudelPattern, factorArg: Any?): StrudelPattern {
    val factor = factorArg?.asDoubleOrNull() ?: 1.0
    return TempoModifierPattern(pattern, factor = factor, invertPattern = false)
}

/** Slows down all inner patterns by the given factor */
@StrudelDsl
val slow by dslFunction { args ->
    val factor: Any?
    val sourceParts: List<Any?>

    // Heuristic: If >1 args, the first one is the factor, the rest is the source.
    // If only 1 arg, it is treated as the source (with factor 1.0).
    if (args.size > 1) {
        factor = args[0]
        sourceParts = args.drop(1)
    } else {
        factor = 1.0
        sourceParts = args
    }

    val source = sourceParts.toPattern(defaultModifier)
    applySlow(source, factor)
}

@StrudelDsl
val StrudelPattern.slow by dslPatternExtension { p, args ->
    applySlow(p, args.firstOrNull())
}

@StrudelDsl
val String.slow by dslStringExtension { p, args ->
    applySlow(p, args.firstOrNull())
}

// -- fast() -----------------------------------------------------------------------------------------------------------

private fun applyFast(pattern: StrudelPattern, factorArg: Any?): StrudelPattern {
    val factor = factorArg?.asDoubleOrNull() ?: 1.0
    return TempoModifierPattern(pattern, factor = factor, invertPattern = true)
}

/** Speeds up all inner patterns by the given factor */
@StrudelDsl
val fast by dslFunction { args ->
    val factor: Any?
    val sourceParts: List<Any?>

    if (args.size > 1) {
        factor = args[0]
        sourceParts = args.drop(1)
    } else {
        factor = 1.0
        sourceParts = args
    }

    val source = sourceParts.toPattern(defaultModifier)
    applyFast(source, factor)
}

/** Speeds up all inner patterns by the given factor */
@StrudelDsl
val StrudelPattern.fast by dslPatternExtension { p, args ->
    applyFast(p, args.firstOrNull())
}

@StrudelDsl
val String.fast by dslStringExtension { p, args ->
    applyFast(p, args.firstOrNull())
}

// -- rev() ------------------------------------------------------------------------------------------------------------

private fun applyRev(pattern: StrudelPattern, n: Int): StrudelPattern {
    // TODO: Make this parameter available through a pattern as well (e.g. sine)
    return if (n <= 1) {
        ReversePattern(pattern)
    } else {
        // Reverses every n-th cycle by speeding up, reversing, then slowing back down
        pattern.fast(n).rev().slow(n)
    }
}

/** Reverses the pattern */
@StrudelDsl
val rev by dslFunction { args ->
    val n = args.firstOrNull()?.asIntOrNull() ?: 1
    val pattern = args.filterIsInstance<StrudelPattern>().firstOrNull()
        ?: return@dslFunction silence

    applyRev(pattern, n)
}

/** Reverses the pattern */
@StrudelDsl
val StrudelPattern.rev by dslPatternExtension { p, args ->
    val n = args.firstOrNull()?.asIntOrNull() ?: 1
    applyRev(p, n)
}

/** Reverses the pattern */
@StrudelDsl
val String.rev by dslStringExtension { p, args ->
    val n = args.firstOrNull()?.asIntOrNull() ?: 1
    applyRev(p, n)
}

// -- palindrome() -----------------------------------------------------------------------------------------------------

private fun applyPalindrome(pattern: StrudelPattern): StrudelPattern {
    return applyCat(listOf(pattern, applyRev(pattern, 1)))
}

/** Plays the pattern forward then backward over two cycles */
@StrudelDsl
val palindrome by dslFunction { args ->
    val pattern = args.filterIsInstance<StrudelPattern>().firstOrNull()
        ?: return@dslFunction silence

    applyPalindrome(pattern)
}

/** Plays the pattern forward then backward over two cycles */
@StrudelDsl
val StrudelPattern.palindrome by dslPatternExtension { p, _ ->
    applyPalindrome(p)
}

/** Plays the pattern forward then backward over two cycles */
@StrudelDsl
val String.palindrome by dslStringExtension { p, _ ->
    applyPalindrome(p)
}

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Note / Sound generation
// ///

// -- scale() ----------------------------------------------------------------------------------------------------------

private val scaleMutation = voiceModifier { scale ->
    val newScale = scale?.toString()?.cleanScaleName()
    copy(scale = newScale).resolveNote()
}

private fun applyScale(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    // If there is no argument, we might be reapplying scale logic or just doing nothing?
    // But scale() usually takes an argument.
    // If called as scale(), it should behave like other patterns.

    return source.applyParam(args, scaleMutation) { src, ctrl ->
        src.copy(scale = ctrl.scale).resolveNote()
    }
}

@StrudelDsl
val StrudelPattern.scale by dslPatternExtension { p, args ->
    applyScale(p, args)
}

@StrudelDsl
val scale by dslFunction { args -> args.toPattern(scaleMutation) }

@StrudelDsl
val String.scale by dslStringExtension { p, args ->
    applyScale(p, args)
}

// -- note() -----------------------------------------------------------------------------------------------------------

private val noteMutation = voiceModifier { input ->
    val newNote = input?.toString()
    copy(note = newNote, freqHz = Tones.noteToFreq(newNote ?: ""))
}

private fun applyNote(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return if (args.isEmpty()) {
        source.reinterpretVoice { it.resolveNote() }
    } else {
        source.applyParam(args, noteMutation) { src, ctrl -> src.noteMutation(ctrl.note) }
    }
}

/** Modifies the notes of a pattern */
@StrudelDsl
val StrudelPattern.note by dslPatternExtension { p, args ->
    applyNote(p, args)
}

/** Creates a pattern with notes */
@StrudelDsl
val note by dslFunction { args -> args.toPattern(noteMutation) }

/** Modifies the notes of a pattern defined by a string */
@StrudelDsl
val String.note by dslStringExtension { p, args ->
    applyNote(p, args)
}

// -- n() --------------------------------------------------------------------------------------------------------------

private val nMutation = voiceModifier {
    resolveNote(it?.asIntOrNull())
}

private fun applyN(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return if (args.isEmpty()) {
        source.reinterpretVoice { it.resolveNote() }
    } else {
        source.applyParam(args, nMutation) { src, ctrl -> src.resolveNote(ctrl.soundIndex) }
    }
}

/** Sets the note number or sample index */
@StrudelDsl
val StrudelPattern.n by dslPatternExtension { p, args ->
    applyN(p, args)
}

/** Sets the note number or sample index */
@StrudelDsl
val n by dslFunction { args -> args.toPattern(nMutation) }

/** Sets the note number or sample index on a string pattern */
@StrudelDsl
val String.n by dslStringExtension { p, args ->
    applyN(p, args)
}

// -- sound() / s() ----------------------------------------------------------------------------------------------------

private val soundMutation = voiceModifier {
    val split = it?.toString()?.split(":") ?: emptyList()

    copy(
        sound = split.getOrNull(0),
        soundIndex = split.getOrNull(1)?.toIntOrNull(),
    )
}

private fun applySound(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return if (args.isEmpty()) {
        source.reinterpretVoice { it.soundMutation(it.value?.asString) }
    } else {
        source.applyParam(args, soundMutation) { src, ctrl ->
            src.copy(
                sound = ctrl.sound ?: src.sound,
                soundIndex = ctrl.soundIndex ?: src.soundIndex
            )
        }
    }
}

/** Modifies the sounds of a pattern */
@StrudelDsl
val StrudelPattern.sound by dslPatternExtension { p, args ->
    applySound(p, args)
}

/** Creates a pattern with sounds */
@StrudelDsl
val sound by dslFunction { args -> args.toPattern(soundMutation) }

/** Modifies the sounds of a pattern defined by a string */
@StrudelDsl
val String.sound by dslStringExtension { p, args ->
    applySound(p, args)
}

/** Alias for [sound] */
@StrudelDsl
val StrudelPattern.s by dslPatternExtension { p, args -> applySound(p, args) }

/** Alias for [sound] */
@StrudelDsl
val s by dslFunction { args -> args.toPattern(soundMutation) }

/** Alias for [sound] on a string */
@StrudelDsl
val String.s by dslStringExtension { p, args -> applySound(p, args) }

// -- bank() -----------------------------------------------------------------------------------------------------------

private val bankMutation = voiceModifier {
    copy(bank = it?.toString())
}

private fun applyBank(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyParam(args, bankMutation) { src, ctrl -> src.bankMutation(ctrl.bank) }
}

/** Modifies the banks of a pattern */
@StrudelDsl
val StrudelPattern.bank by dslPatternExtension { p, args ->
    applyBank(p, args)
}

/** Creates a pattern with banks */
@StrudelDsl
val bank by dslFunction { args -> args.toPattern(bankMutation) }

/** Modifies the banks of a pattern defined by a string */
@StrudelDsl
val String.bank by dslStringExtension { p, args ->
    applyBank(p, args)
}

// -- legato() / clip() ------------------------------------------------------------------------------------------------

private val legatoMutation = voiceModifier {
    copy(legato = it?.asDoubleOrNull())
}

private fun applyLegato(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyParam(args, legatoMutation) { src, ctrl -> src.legatoMutation(ctrl.legato) }
}

/** Modifies the legatos of a pattern */
@StrudelDsl
val StrudelPattern.legato by dslPatternExtension { p, args ->
    applyLegato(p, args)
}

/** Creates a pattern with legatos */
@StrudelDsl
val legato by dslFunction { args -> args.toPattern(legatoMutation) }

/** Modifies the legatos of a pattern defined by a string */
@StrudelDsl
val String.legato by dslStringExtension { p, args ->
    applyLegato(p, args)
}

/** Alias for [legato] */
@StrudelDsl
val StrudelPattern.clip by dslPatternExtension { p, args -> applyLegato(p, args) }

/** Alias for [legato] */
@StrudelDsl
val clip by dslFunction { args -> args.toPattern(legatoMutation) }

/** Alias for [legato] on a string */
@StrudelDsl
val String.clip by dslStringExtension { p, args -> applyLegato(p, args) }

// -- vibrato() --------------------------------------------------------------------------------------------------------

private val vibratoMutation = voiceModifier {
    copy(vibrato = it?.asDoubleOrNull())
}

private fun applyVibrato(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyParam(args, vibratoMutation) { src, ctrl -> src.vibratoMutation(ctrl.vibrato) }
}

/** Sets the vibrato frequency (speed) in Hz. */
@StrudelDsl
val StrudelPattern.vibrato by dslPatternExtension { p, args ->
    applyVibrato(p, args)
}

/** Sets the vibrato frequency (speed) in Hz. */
@StrudelDsl
val vibrato by dslFunction { args -> args.toPattern(vibratoMutation) }

/** Sets the vibrato frequency (speed) in Hz on a string. */
@StrudelDsl
val String.vibrato by dslStringExtension { p, args ->
    applyVibrato(p, args)
}

/** Alias for [vibrato] */
@StrudelDsl
val StrudelPattern.vib by dslPatternExtension { p, args -> applyVibrato(p, args) }

/** Alias for [vibrato] */
@StrudelDsl
val vib by dslFunction { args -> args.toPattern(vibratoMutation) }

/** Alias for [vibrato] on a string */
@StrudelDsl
val String.vib by dslStringExtension { p, args -> applyVibrato(p, args) }

// -- vibratoMod() -----------------------------------------------------------------------------------------------------

private val vibratoModMutation = voiceModifier {
    copy(vibratoMod = it?.asDoubleOrNull())
}

private fun applyVibratoMod(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyParam(args, vibratoModMutation) { src, ctrl -> src.vibratoModMutation(ctrl.vibratoMod) }
}

/** Sets the vibratoMod depth (amplitude). */
@StrudelDsl
val StrudelPattern.vibratoMod by dslPatternExtension { p, args ->
    applyVibratoMod(p, args)
}

/** Sets the vibratoMod depth (amplitude). */
@StrudelDsl
val vibratoMod by dslFunction { args -> args.toPattern(vibratoModMutation) }

/** Sets the vibratoMod depth (amplitude) on a string. */
@StrudelDsl
val String.vibratoMod by dslStringExtension { p, args ->
    applyVibratoMod(p, args)
}

/** Alias for [vibratoMod] */
@StrudelDsl
val StrudelPattern.vibmod by dslPatternExtension { p, args -> applyVibratoMod(p, args) }

/** Alias for [vibratoMod] */
@StrudelDsl
val vibmod by dslFunction { args -> args.toPattern(vibratoModMutation) }

/** Alias for [vibratoMod] on a string */
@StrudelDsl
val String.vibmod by dslStringExtension { p, args -> applyVibratoMod(p, args) }

// -- accelerate() -----------------------------------------------------------------------------------------------------

private val accelerateMutation = voiceModifier {
    copy(accelerate = it?.asDoubleOrNull())
}

private fun applyAccelerate(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyParam(args, accelerateMutation) { src, ctrl -> src.accelerateMutation(ctrl.accelerate) }
}

@StrudelDsl
val StrudelPattern.accelerate by dslPatternExtension { p, args ->
    applyAccelerate(p, args)
}

@StrudelDsl
val accelerate by dslFunction { args -> args.toPattern(accelerateMutation) }

@StrudelDsl
val String.accelerate by dslStringExtension { p, args ->
    applyAccelerate(p, args)
}

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Dynamics, Gain, Pan, Envelope ...
// ///

// -- gain() -----------------------------------------------------------------------------------------------------------

private val gainMutation = voiceModifier {
    copy(gain = it?.toString()?.toDoubleOrNull())
}

private fun applyGain(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyParam(args, gainMutation) { src, ctrl -> src.gainMutation(ctrl.gain) }
}

/** Modifies the gains of a pattern */
@StrudelDsl
val StrudelPattern.gain by dslPatternExtension { p, args ->
    applyGain(p, args)
}

/** Creates a pattern with gains */
@StrudelDsl
val gain by dslFunction { args -> args.toPattern(gainMutation) }

/** Modifies the gains of a pattern defined by a string */
@StrudelDsl
val String.gain by dslStringExtension { p, args ->
    applyGain(p, args)
}

// -- pan() ------------------------------------------------------------------------------------------------------------

private val panMutation = voiceModifier {
    copy(pan = it?.toString()?.toDoubleOrNull())
}

private fun applyPan(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyParam(args, panMutation) { src, ctrl -> src.panMutation(ctrl.pan) }
}

/** Modifies the pans of a pattern */
@StrudelDsl
val StrudelPattern.pan by dslPatternExtension { p, args ->
    applyPan(p, args)
}

/** Creates a pattern with pans */
@StrudelDsl
val pan by dslFunction { args -> args.toPattern(panMutation) }

/** Modifies the pans of a pattern defined by a string */
@StrudelDsl
val String.pan by dslStringExtension { p, args ->
    applyPan(p, args)
}

// -- unison() ---------------------------------------------------------------------------------------------------------

private val unisonMutation = voiceModifier {
    copy(voices = it?.asDoubleOrNull())
}

private fun applyUnison(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyParam(args, unisonMutation) { src, ctrl -> src.unisonMutation(ctrl.voices) }
}

/** Modifies the voices of a pattern */
@StrudelDsl
val StrudelPattern.unison by dslPatternExtension { p, args ->
    applyUnison(p, args)
}

/** Creates a pattern with unison */
@StrudelDsl
val unison by dslFunction { args -> args.toPattern(unisonMutation) }

/** Modifies the voices of a pattern defined by a string */
@StrudelDsl
val String.unison by dslStringExtension { p, args ->
    applyUnison(p, args)
}

/** Alias for [unison] */
@StrudelDsl
val StrudelPattern.uni by dslPatternExtension { p, args -> applyUnison(p, args) }

/** Alias for [unison] */
@StrudelDsl
val uni by dslFunction { args -> args.toPattern(unisonMutation) }

/** Alias for [unison] on a string */
@StrudelDsl
val String.uni by dslStringExtension { p, args -> applyUnison(p, args) }

// -- detune() ---------------------------------------------------------------------------------------------------------

private val detuneMutation = voiceModifier {
    copy(freqSpread = it?.asDoubleOrNull())
}

private fun applyDetune(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyParam(args, detuneMutation) { src, ctrl -> src.detuneMutation(ctrl.freqSpread) }
}

/** Sets the oscillator frequency spread (for supersaw) */
@StrudelDsl
val StrudelPattern.detune by dslPatternExtension { p, args ->
    applyDetune(p, args)
}

/** Sets the oscillator frequency spread (for supersaw) */
@StrudelDsl
val detune by dslFunction { args -> args.toPattern(detuneMutation) }

/** Sets the oscillator frequency spread (for supersaw) on a string */
@StrudelDsl
val String.detune by dslStringExtension { p, args ->
    applyDetune(p, args)
}

// -- spread() ---------------------------------------------------------------------------------------------------------

private val spreadMutation = voiceModifier {
    copy(panSpread = it?.asDoubleOrNull())
}

private fun applySpread(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyParam(args, spreadMutation) { src, ctrl -> src.spreadMutation(ctrl.panSpread) }
}

/** Sets the oscillator pan spread (for supersaw) */
@StrudelDsl
val StrudelPattern.spread by dslPatternExtension { p, args ->
    applySpread(p, args)
}

/** Sets the oscillator pan spread (for supersaw) */
@StrudelDsl
val spread by dslFunction { args -> args.toPattern(spreadMutation) }

/** Sets the oscillator pan spread (for supersaw) on a string */
@StrudelDsl
val String.spread by dslStringExtension { p, args ->
    applySpread(p, args)
}

// -- density() --------------------------------------------------------------------------------------------------------

private val densityMutation = voiceModifier {
    copy(density = it?.asDoubleOrNull())
}

private fun applyDensity(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyParam(args, densityMutation) { src, ctrl -> src.densityMutation(ctrl.density) }
}

/** Sets the oscillator density (for supersaw) */
@StrudelDsl
val StrudelPattern.density by dslPatternExtension { p, args ->
    applyDensity(p, args)
}

/** Sets the oscillator density (for supersaw) */
@StrudelDsl
val density by dslFunction { args -> args.toPattern(densityMutation) }

/** Sets the oscillator density (for supersaw) on a string */
@StrudelDsl
val String.density by dslStringExtension { p, args ->
    applyDensity(p, args)
}

/** Alias for [density] */
@StrudelDsl
val StrudelPattern.d by dslPatternExtension { p, args -> applyDensity(p, args) }

/** Alias for [density] */
@StrudelDsl
val d by dslFunction { args -> args.toPattern(densityMutation) }

/** Alias for [density] on a string */
@StrudelDsl
val String.d by dslStringExtension { p, args -> applyDensity(p, args) }

// -- ADSR attack() ----------------------------------------------------------------------------------------------------

private val attackMutation = voiceModifier {
    copy(adsr = adsr.copy(attack = it?.asDoubleOrNull()))
}

private fun applyAttack(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyParam(args, attackMutation) { src, ctrl -> src.attackMutation(ctrl.adsr.attack) }
}

/** Sets the note envelope attack */
@StrudelDsl
val StrudelPattern.attack by dslPatternExtension { p, args ->
    applyAttack(p, args)
}

/** Sets the note envelope attack */
@StrudelDsl
val attack by dslFunction { args -> args.toPattern(attackMutation) }

/** Sets the note envelope attack on a string */
@StrudelDsl
val String.attack by dslStringExtension { p, args ->
    applyAttack(p, args)
}

// -- ADSR decay() -----------------------------------------------------------------------------------------------------

private val decayMutation = voiceModifier {
    copy(adsr = adsr.copy(decay = it?.asDoubleOrNull()))
}

private fun applyDecay(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyParam(args, decayMutation) { src, ctrl -> src.decayMutation(ctrl.adsr.decay) }
}

/** Sets the note envelope decay */
@StrudelDsl
val StrudelPattern.decay by dslPatternExtension { p, args ->
    applyDecay(p, args)
}

/** Sets the note envelope decay */
@StrudelDsl
val decay by dslFunction { args -> args.toPattern(decayMutation) }

/** Sets the note envelope decay on a string */
@StrudelDsl
val String.decay by dslStringExtension { p, args ->
    applyDecay(p, args)
}

// -- ADSR sustain() ---------------------------------------------------------------------------------------------------

private val sustainMutation = voiceModifier {
    copy(adsr = adsr.copy(sustain = it?.asDoubleOrNull()))
}

private fun applySustain(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyParam(args, sustainMutation) { src, ctrl -> src.sustainMutation(ctrl.adsr.sustain) }
}

/** Sets the note envelope sustain */
@StrudelDsl
val StrudelPattern.sustain by dslPatternExtension { p, args ->
    applySustain(p, args)
}

/** Sets the note envelope sustain */
@StrudelDsl
val sustain by dslFunction { args -> args.toPattern(sustainMutation) }

/** Sets the note envelope sustain on a string */
@StrudelDsl
val String.sustain by dslStringExtension { p, args ->
    applySustain(p, args)
}

// -- ADSR release() ---------------------------------------------------------------------------------------------------

private val releaseMutation = voiceModifier {
    copy(adsr = adsr.copy(release = it?.asDoubleOrNull()))
}

private fun applyRelease(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyParam(args, releaseMutation) { src, ctrl -> src.releaseMutation(ctrl.adsr.release) }
}

/** Sets the note envelope release */
@StrudelDsl
val StrudelPattern.release by dslPatternExtension { p, args ->
    applyRelease(p, args)
}

/** Sets the note envelope release */
@StrudelDsl
val release by dslFunction { args -> args.toPattern(releaseMutation) }

/** Sets the note envelope release on a string */
@StrudelDsl
val String.release by dslStringExtension { p, args ->
    applyRelease(p, args)
}

// -- ADSR adsr() ------------------------------------------------------------------------------------------------------

private val adsrMutation = voiceModifier {
    val parts = it?.toString()?.split(":")
        ?.mapNotNull { d -> d.toDoubleOrNull() } ?: emptyList()

    val newAdsr = AdsrEnvelope(
        attack = parts.getOrNull(0),
        decay = parts.getOrNull(1),
        sustain = parts.getOrNull(2),
        release = parts.getOrNull(3),
    )

    copy(adsr = newAdsr.mergeWith(adsr))
}

private fun applyAdsr(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyParam(args, adsrMutation) { src, ctrl ->
        src.copy(adsr = ctrl.adsr.mergeWith(src.adsr))
    }
}

/** Sets the note envelope via string or pattern */
@StrudelDsl
val StrudelPattern.adsr by dslPatternExtension { p, args ->
    applyAdsr(p, args)
}

/** Sets the note envelope via string or pattern */
@StrudelDsl
val adsr by dslFunction { args -> args.toPattern(adsrMutation) }

/** Sets the note envelope via string or pattern on a string */
@StrudelDsl
val String.adsr by dslStringExtension { p, args ->
    applyAdsr(p, args)
}

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Filters
// ///

// -- lpf() ------------------------------------------------------------------------------------------------------------

private val lpfMutation = voiceModifier {
    val cutoff = it?.asDoubleOrNull()
    val filter = FilterDef.LowPass(cutoffHz = cutoff ?: 1000.0, q = resonance)

    copy(
        cutoff = cutoff,
        filters = filters.addOrReplace(filter),
    )
}

private fun applyLpf(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyParam(args, lpfMutation) { src, ctrl ->
        src
            .copy(resonance = ctrl.resonance ?: src.resonance)
            .lpfMutation(ctrl.filters.getByType<FilterDef.LowPass>()?.cutoffHz)
    }
}

/** Adds a Low Pass Filter with the given cutoff frequency. */
@StrudelDsl
val StrudelPattern.lpf by dslPatternExtension { p, args ->
    applyLpf(p, args)
}

/** Adds a Low Pass Filter with the given cutoff frequency. */
@StrudelDsl
val lpf by dslFunction { args -> args.toPattern(lpfMutation) }

/** Adds a Low Pass Filter with the given cutoff frequency on a string. */
@StrudelDsl
val String.lpf by dslStringExtension { p, args ->
    applyLpf(p, args)
}

// -- hpf() ------------------------------------------------------------------------------------------------------------

private val hpfMutation = voiceModifier {
    val cutoff = it?.asDoubleOrNull()
    val filter = FilterDef.HighPass(cutoffHz = cutoff ?: 1000.0, q = resonance)

    copy(
        hcutoff = cutoff,
        filters = filters.addOrReplace(filter),
    )
}

private fun applyHpf(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyParam(args, hpfMutation) { src, ctrl ->
        src
            .copy(resonance = ctrl.resonance ?: src.resonance)
            .hpfMutation(ctrl.filters.getByType<FilterDef.HighPass>()?.cutoffHz)
    }
}

/** Adds a High Pass Filter with the given cutoff frequency. */
@StrudelDsl
val StrudelPattern.hpf by dslPatternExtension { p, args ->
    applyHpf(p, args)
}

/** Adds a High Pass Filter with the given cutoff frequency. */
@StrudelDsl
val hpf by dslFunction { args -> args.toPattern(hpfMutation) }

/** Adds a High Pass Filter with the given cutoff frequency on a string. */
@StrudelDsl
val String.hpf by dslStringExtension { p, args ->
    applyHpf(p, args)
}

// -- bandf() / bpf() --------------------------------------------------------------------------------------------------

private val bandfMutation = voiceModifier {
    val cutoff = it?.asDoubleOrNull()
    val filter = FilterDef.BandPass(cutoffHz = cutoff ?: 1000.0, q = resonance)

    copy(
        bandf = cutoff,
        filters = filters.addOrReplace(filter),
    )
}

private fun applyBandf(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyParam(args, bandfMutation) { src, ctrl ->
        src
            .copy(resonance = ctrl.resonance ?: src.resonance)
            .bandfMutation(ctrl.filters.getByType<FilterDef.BandPass>()?.cutoffHz)
    }
}

/** Adds a Band Pass Filter with the given cutoff frequency. */
@StrudelDsl
val StrudelPattern.bandf by dslPatternExtension { p, args ->
    applyBandf(p, args)
}

/** Adds a Band Pass Filter with the given cutoff frequency. */
@StrudelDsl
val bandf by dslFunction { args -> args.toPattern(bandfMutation) }

/** Adds a Band Pass Filter with the given cutoff frequency on a string. */
@StrudelDsl
val String.bandf by dslStringExtension { p, args ->
    applyBandf(p, args)
}

/** Alias for [bandf] */
@StrudelDsl
val StrudelPattern.bpf by dslPatternExtension { p, args -> applyBandf(p, args) }

/** Alias for [bandf] */
@StrudelDsl
val bpf by dslFunction { args -> args.toPattern(bandfMutation) }

/** Alias for [bandf] on a string */
@StrudelDsl
val String.bpf by dslStringExtension { p, args -> applyBandf(p, args) }

// -- notchf() ---------------------------------------------------------------------------------------------------------

private val notchfMutation = voiceModifier {
    val cutoff = it?.asDoubleOrNull()
    val filter = FilterDef.Notch(cutoffHz = cutoff ?: 1000.0, q = resonance)

    copy(
        cutoff = cutoff,
        filters = filters.addOrReplace(filter),
    )
}

private fun applyNotchf(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyParam(args, notchfMutation) { src, ctrl ->
        src
            .copy(resonance = ctrl.resonance ?: src.resonance)
            .notchfMutation(ctrl.filters.getByType<FilterDef.Notch>()?.cutoffHz)
    }
}

/** Adds a Notch Filter with the given cutoff frequency. */
@StrudelDsl
val StrudelPattern.notchf by dslPatternExtension { p, args ->
    applyNotchf(p, args)
}

/** Adds a Notch Filter with the given cutoff frequency. */
@StrudelDsl
val notchf by dslFunction { args -> args.toPattern(notchfMutation) }

/** Adds a Notch Filter with the given cutoff frequency on a string. */
@StrudelDsl
val String.notchf by dslStringExtension { p, args ->
    applyNotchf(p, args)
}

// -- resonance() / res() ----------------------------------------------------------------------------------------------

private val resonanceMutation = voiceModifier {
    val newQ = it?.asDoubleOrNull() ?: 1.0

    val newFilters = filters.modifyAll { filter ->
        when (filter) {
            is FilterDef.LowPass -> filter.copy(q = newQ)
            is FilterDef.HighPass -> filter.copy(q = newQ)
            is FilterDef.BandPass -> filter.copy(q = newQ)
            is FilterDef.Notch -> filter.copy(q = newQ)
        }
    }

    copy(resonance = newQ, filters = newFilters)
}

private fun applyResonance(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyParam(args, resonanceMutation) { src, ctrl -> src.resonanceMutation(ctrl.resonance) }
}

/** Sets the filter resonance. */
@StrudelDsl
val StrudelPattern.resonance by dslPatternExtension { p, args ->
    applyResonance(p, args)
}

/** Sets the filter resonance. */
@StrudelDsl
val resonance by dslFunction { args -> args.toPattern(resonanceMutation) }

/** Sets the filter resonance on a string. */
@StrudelDsl
val String.resonance by dslStringExtension { p, args ->
    applyResonance(p, args)
}

/** Alias for [resonance] */
@StrudelDsl
val StrudelPattern.res by dslPatternExtension { p, args -> applyResonance(p, args) }

/** Alias for [resonance] */
@StrudelDsl
val res by dslFunction { args -> args.toPattern(resonanceMutation) }

/** Alias for [resonance] on a string */
@StrudelDsl
val String.res by dslStringExtension { p, args -> applyResonance(p, args) }

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Effects
// ///

// -- distort() --------------------------------------------------------------------------------------------------------

private val distortMutation = voiceModifier {
    copy(distort = it?.asDoubleOrNull())
}

private fun applyDistort(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyParam(args, distortMutation) { src, ctrl -> src.distortMutation(ctrl.distort) }
}

@StrudelDsl
val StrudelPattern.distort by dslPatternExtension { p, args ->
    applyDistort(p, args)
}

@StrudelDsl
val distort by dslFunction { args -> args.toPattern(distortMutation) }

@StrudelDsl
val String.distort by dslStringExtension { p, args ->
    applyDistort(p, args)
}

// -- crush() ----------------------------------------------------------------------------------------------------------

private val crushMutation = voiceModifier { copy(crush = it?.asDoubleOrNull()) }

private fun applyCrush(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyParam(args, crushMutation) { src, ctrl -> src.crushMutation(ctrl.crush) }
}

@StrudelDsl
val StrudelPattern.crush by dslPatternExtension { p, args ->
    applyCrush(p, args)
}

@StrudelDsl
val crush by dslFunction { args -> args.toPattern(crushMutation) }

@StrudelDsl
val String.crush by dslStringExtension { p, args ->
    applyCrush(p, args)
}

// -- coarse() ---------------------------------------------------------------------------------------------------------

private val coarseMutation = voiceModifier {
    copy(coarse = it?.asDoubleOrNull())
}

private fun applyCoarse(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyParam(args, coarseMutation) { src, ctrl -> src.coarseMutation(ctrl.coarse) }
}

@StrudelDsl
val StrudelPattern.coarse by dslPatternExtension { p, args ->
    applyCoarse(p, args)
}

@StrudelDsl
val coarse by dslFunction { args -> args.toPattern(coarseMutation) }

@StrudelDsl
val String.coarse by dslStringExtension { p, args ->
    applyCoarse(p, args)
}

// -- room() -----------------------------------------------------------------------------------------------------------

private val roomMutation = voiceModifier {
    copy(room = it?.asDoubleOrNull())
}

private fun applyRoom(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyParam(args, roomMutation) { src, ctrl -> src.roomMutation(ctrl.room) }
}

@StrudelDsl
val StrudelPattern.room by dslPatternExtension { p, args ->
    applyRoom(p, args)
}

@StrudelDsl
val room by dslFunction { args -> args.toPattern(roomMutation) }

@StrudelDsl
val String.room by dslStringExtension { p, args ->
    applyRoom(p, args)
}

// -- roomsize() / rsize() ---------------------------------------------------------------------------------------------

private val roomSizeMutation = voiceModifier {
    copy(roomSize = it?.asDoubleOrNull())
}

private fun applyRoomSize(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyParam(args, roomSizeMutation) { src, ctrl -> src.roomSizeMutation(ctrl.roomSize) }
}

@StrudelDsl
val StrudelPattern.roomsize by dslPatternExtension { p, args ->
    applyRoomSize(p, args)
}

@StrudelDsl
val roomsize by dslFunction { args -> args.toPattern(roomSizeMutation) }

@StrudelDsl
val String.roomsize by dslStringExtension { p, args ->
    applyRoomSize(p, args)
}

/** Alias for [roomsize] */
@StrudelDsl
val StrudelPattern.rsize by dslPatternExtension { p, args -> applyRoomSize(p, args) }

/** Alias for [roomsize] */
@StrudelDsl
val rsize by dslFunction { args -> args.toPattern(roomSizeMutation) }

/** Alias for [roomsize] on a string */
@StrudelDsl
val String.rsize by dslStringExtension { p, args -> applyRoomSize(p, args) }

// -- delay() ----------------------------------------------------------------------------------------------------------

private val delayMutation = voiceModifier {
    copy(delay = it?.asDoubleOrNull())
}

private fun applyDelay(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyParam(args, delayMutation) { src, ctrl -> src.delayMutation(ctrl.delay) }
}

@StrudelDsl
val StrudelPattern.delay by dslPatternExtension { p, args ->
    applyDelay(p, args)
}

@StrudelDsl
val delay by dslFunction { args -> args.toPattern(delayMutation) }

@StrudelDsl
val String.delay by dslStringExtension { p, args ->
    applyDelay(p, args)
}

// -- delaytime() ------------------------------------------------------------------------------------------------------

private val delayTimeMutation = voiceModifier {
    copy(delayTime = it?.asDoubleOrNull())
}

private fun applyDelayTime(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyParam(args, delayTimeMutation) { src, ctrl -> src.delayTimeMutation(ctrl.delayTime) }
}

@StrudelDsl
val StrudelPattern.delaytime by dslPatternExtension { p, args ->
    applyDelayTime(p, args)
}

@StrudelDsl
val delaytime by dslFunction { args -> args.toPattern(delayTimeMutation) }

@StrudelDsl
val String.delaytime by dslStringExtension { p, args ->
    applyDelayTime(p, args)
}

// -- delayfeedback() --------------------------------------------------------------------------------------------------

private val delayFeedbackMutation = voiceModifier {
    copy(delayFeedback = it?.asDoubleOrNull())
}

private fun applyDelayFeedback(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyParam(args, delayFeedbackMutation) { src, ctrl -> src.delayFeedbackMutation(ctrl.delayFeedback) }
}

@StrudelDsl
val StrudelPattern.delayfeedback by dslPatternExtension { p, args ->
    applyDelayFeedback(p, args)
}

@StrudelDsl
val delayfeedback by dslFunction { args -> args.toPattern(delayFeedbackMutation) }

@StrudelDsl
val String.delayfeedback by dslStringExtension { p, args ->
    applyDelayFeedback(p, args)
}

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Routing
// ///

// -- orbit() ----------------------------------------------------------------------------------------------------------

private val orbitMutation = voiceModifier {
    copy(orbit = it?.asIntOrNull())
}

private fun applyOrbit(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyParam(args, orbitMutation) { src, ctrl -> src.orbitMutation(ctrl.orbit) }
}

@StrudelDsl
val StrudelPattern.orbit by dslPatternExtension { p, args ->
    applyOrbit(p, args)
}

@StrudelDsl
val orbit by dslFunction { args -> args.toPattern(orbitMutation) }

@StrudelDsl
val String.orbit by dslStringExtension { p, args ->
    applyOrbit(p, args)
}

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Arithmatic
// ///

private fun applyBinaryOp(
    source: StrudelPattern,
    args: List<Any?>,
    op: (VoiceValue, VoiceValue) -> VoiceValue?,
): StrudelPattern {
    // We use defaultModifier for args because we just want the 'value'
    val controlPattern = args.toPattern(defaultModifier)

    return ControlPattern(
        source = source,
        control = controlPattern,
        mapper = { it }, // No mapping needed
        combiner = { srcData, ctrlData ->
            val amount = ctrlData.value ?: return@ControlPattern srcData

            val srcValue = srcData.value
                ?: srcData.soundIndex?.toDouble()?.asVoiceValue()
                ?: srcData.note?.asVoiceValue()
                ?: return@ControlPattern srcData

            val newValue = op(srcValue, amount)

            srcData.copy(
                value = newValue ?: srcData.value,
                // If we successfully modified the value, we clear the soundIndex
                // because the value is now the source of truth (e.g. index 0 -> add 1 -> index 1)
                soundIndex = if (newValue != null) null else srcData.soundIndex
            )
        }
    )
}

private fun applyUnaryOp(
    source: StrudelPattern,
    op: (VoiceValue) -> VoiceValue?,
): StrudelPattern {
    // Unary ops (like log2) apply directly to the source values without a control pattern
    return source.reinterpretVoice { srcData ->
        val srcValue = srcData.value
            ?: srcData.soundIndex?.toDouble()?.asVoiceValue()
            ?: srcData.note?.asVoiceValue()
            ?: return@reinterpretVoice srcData

        val newValue = op(srcValue)

        srcData.copy(
            value = newValue ?: srcData.value,
            soundIndex = if (newValue != null) null else srcData.soundIndex
        )
    }
}

// -- add() ------------------------------------------------------------------------------------------------------------

/**
 * Adds the given amount to the pattern's value.
 * Example: n("0 2").add("5") -> n("5 7")
 */
@StrudelDsl
val StrudelPattern.add by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a + b }
}

/** Adds the given amount to the pattern's value on a string. */
@StrudelDsl
val String.add by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a + b }
}

/**
 * Top-level add function.
 * Usage: add(amount, pattern) -> adds amount to pattern
 * Usage: add(value) -> creates a pattern with that value
 */
@StrudelDsl
val add by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        val amountArgs = args.dropLast(1)
        applyBinaryOp(source, amountArgs) { a, b -> a + b }
    } else {
        args.toPattern(defaultModifier)
    }
}

// -- sub() ------------------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.sub by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a - b }
}

@StrudelDsl
val String.sub by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a - b }
}

@StrudelDsl
val sub by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a - b }
    } else {
        silence // sub() as a source doesn't make much sense without arguments
    }
}

// -- mul() ------------------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.mul by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a * b }
}

@StrudelDsl
val String.mul by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a * b }
}

@StrudelDsl
val mul by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a * b }
    } else {
        silence
    }
}

// -- div() ------------------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.div by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a / b }
}

@StrudelDsl
val String.div by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a / b }
}

@StrudelDsl
val div by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a / b }
    } else {
        silence
    }
}

// -- mod() ------------------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.mod by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a % b }
}

@StrudelDsl
val String.mod by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a % b }
}

@StrudelDsl
val mod by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a % b }
    } else {
        silence
    }
}

// -- pow() ------------------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.pow by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a pow b }
}

@StrudelDsl
val String.pow by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a pow b }
}

@StrudelDsl
val pow by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a pow b }
    } else {
        silence
    }
}

// -- band() (Bitwise AND) ---------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.band by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a band b }
}

@StrudelDsl
val String.band by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a band b }
}

@StrudelDsl
val band by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a band b }
    } else {
        silence
    }
}

// -- bor() (Bitwise OR) -----------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.bor by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a bor b }
}

@StrudelDsl
val String.bor by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a bor b }
}

@StrudelDsl
val bor by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a bor b }
    } else {
        silence
    }
}

// -- bxor() (Bitwise XOR) ---------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.bxor by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a bxor b }
}

@StrudelDsl
val String.bxor by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a bxor b }
}

@StrudelDsl
val bxor by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a bxor b }
    } else {
        silence
    }
}

// -- blshift() (Bitwise Left Shift) -----------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.blshift by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a shl b }
}

@StrudelDsl
val String.blshift by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a shl b }
}

@StrudelDsl
val blshift by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a shl b }
    } else {
        silence
    }
}

// -- brshift() (Bitwise Right Shift) ----------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.brshift by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a shr b }
}

@StrudelDsl
val String.brshift by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a shr b }
}

@StrudelDsl
val brshift by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a shr b }
    } else {
        silence
    }
}

// -- log2() -----------------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.log2 by dslPatternExtension { source, _ ->
    applyUnaryOp(source) { it.log2() }
}

@StrudelDsl
val String.log2 by dslStringExtension { source, _ ->
    applyUnaryOp(source) { it.log2() }
}

@StrudelDsl
val log2 by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (source != null) {
        applyUnaryOp(source) { it.log2() }
    } else {
        silence
    }
}

// -- lt() (Less Than) -------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.lt by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a lt b }
}

@StrudelDsl
val String.lt by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a lt b }
}

@StrudelDsl
val lt by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a lt b }
    } else {
        silence
    }
}

// -- gt() (Greater Than) ----------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.gt by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a gt b }
}

@StrudelDsl
val String.gt by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a gt b }
}

@StrudelDsl
val gt by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a gt b }
    } else {
        silence
    }
}

// -- lte() (Less Than or Equal) ---------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.lte by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a lte b }
}

@StrudelDsl
val String.lte by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a lte b }
}

@StrudelDsl
val lte by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a lte b }
    } else {
        silence
    }
}

// -- gte() (Greater Than or Equal) ------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.gte by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a gte b }
}

@StrudelDsl
val String.gte by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a gte b }
}

@StrudelDsl
val gte by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a gte b }
    } else {
        silence
    }
}

// -- eq() (Equal) -----------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.eq by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a eq b }
}

@StrudelDsl
val String.eq by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a eq b }
}

@StrudelDsl
val eq by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a eq b }
    } else {
        silence
    }
}

// -- eqt() (Truthiness Equal) -----------------------------------------------------------------------------------------

/** Truthiness equality comparison */
@StrudelDsl
val StrudelPattern.eqt by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a eqt b }
}

/** Truthiness equality comparison on a string */
@StrudelDsl
val String.eqt by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a eqt b }
}

/** Truthiness equality comparison function */
@StrudelDsl
val eqt by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a eqt b }
    } else {
        silence
    }
}

// -- ne() (Not Equal) -------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.ne by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a ne b }
}

@StrudelDsl
val String.ne by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a ne b }
}

@StrudelDsl
val ne by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a ne b }
    } else {
        silence
    }
}

// -- net() (Truthiness Not Equal) -------------------------------------------------------------------------------------

/** Truthiness inequality comparison */
@StrudelDsl
val StrudelPattern.net by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a net b }
}

/** Truthiness inequality comparison on a string */
@StrudelDsl
val String.net by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a net b }
}

/** Truthiness inequality comparison function */
@StrudelDsl
val net by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a net b }
    } else {
        silence
    }
}

// -- and() (Logical AND) ----------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.and by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a and b }
}

@StrudelDsl
val String.and by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a and b }
}

@StrudelDsl
val and by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a and b }
    } else {
        silence
    }
}

// -- or() (Logical OR) ------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.or by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a or b }
}

@StrudelDsl
val String.or by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a or b }
}

@StrudelDsl
val or by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a or b }
    } else {
        silence
    }
}
