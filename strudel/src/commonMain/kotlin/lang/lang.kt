package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.audio_bridge.AdsrEnvelope
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.VoiceData
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
import kotlin.math.max
import kotlin.math.sin

@DslMarker
annotation class StrudelDsl

// Initialization Helper ///////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangInit = false

// Helpers /////////////////////////////////////////////////////////////////////////////////////////////////////////////

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

// Continuous patterns /////////////////////////////////////////////////////////////////////////////////////////////////

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

// -- Structural - seq() -----------------------------------------------------------------------------------------------

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

// -- Structural - stack() ---------------------------------------------------------------------------------------------

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

// -- Structural - arrange() -------------------------------------------------------------------------------------------

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

// -- Structural - pickRestart() ---------------------------------------------------------------------------------------


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

// -- Structural - cat() -----------------------------------------------------------------------------------------------

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

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Tempo / Time modifiers
// ///

// -- Tempo - slow() ---------------------------------------------------------------------------------------------------

/** Slows down all inner patterns by the given factor */
@StrudelDsl
val slow by dslFunction { args ->
    val factor = args.getOrNull(0)?.asDoubleOrNull() ?: 1.0
    // Find the first pattern argument? Or strict [factor, pattern]?
    // Strudel JS allows flexible args usually, but let's stick to [factor, pattern] or [pattern, factor]?
    // Usually slow(factor, pattern).
    val pattern = args.filterIsInstance<StrudelPattern>().firstOrNull() ?: silence

    TempoModifierPattern(pattern, max(1.0 / 128.0, factor))
}

@StrudelDsl
val StrudelPattern.slow by dslPatternExtension { p, args ->
    val factor = args.firstOrNull()?.asDoubleOrNull() ?: 1.0
    TempoModifierPattern(p, max(1.0 / 128.0, factor))
}

@StrudelDsl
val String.slow by dslStringExtension { p, args ->
    val factor = args.firstOrNull()?.asDoubleOrNull() ?: 1.0
    TempoModifierPattern(p, max(1.0 / 128.0, factor))
}

// -- Tempo - fast() ---------------------------------------------------------------------------------------------------

private fun fastImpl(pattern: StrudelPattern, factor: Double): StrudelPattern {
    return TempoModifierPattern(pattern, 1.0 / max(1.0 / 128.0, factor))
}

/** Speeds up all inner patterns by the given factor */
@StrudelDsl
val fast by dslFunction { args ->
    val factor = args.getOrNull(0)?.asDoubleOrNull() ?: 1.0
    val pattern = args.filterIsInstance<StrudelPattern>().firstOrNull() ?: silence
    fastImpl(pattern, factor)
}

/** Speeds up all inner patterns by the given factor */
@StrudelDsl
val StrudelPattern.fast by dslPatternExtension { p, args ->
    val factor = (args.firstOrNull() as? Number)?.toDouble() ?: 1.0
    fastImpl(p, factor)
}

@StrudelDsl
val String.fast by dslStringExtension { p, args ->
    val factor = (args.firstOrNull() as? Number)?.toDouble() ?: 1.0
    fastImpl(p, factor)
}

// -- Tempo - rev() ----------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.rev: DslPatternMethod by dslPatternExtension { pattern, args ->
    val firstArgInt = args.firstOrNull()?.toString()?.toIntOrNull() ?: 1

    if (firstArgInt <= 1) {
        ReversePattern(pattern)
    } else {
        pattern.fast(firstArgInt).rev().slow(firstArgInt)
    }
}

@StrudelDsl
val StrudelPattern.palindrome by dslPatternExtension { pattern, _ ->
    cat(pattern, pattern.rev())
}

/**
 * Structures the pattern according to another pattern (the mask).
 *
 * The structural pattern (usually mini-notation) uses 'x' to indicate where
 * the source pattern should be heard.
 *
 * Example: note("c e g").struct("x ~ x")
 */
@StrudelDsl
val StrudelPattern.struct by dslPatternExtension { source, args ->
    val structure = when (val structArg = args.firstOrNull()) {
        is StrudelPattern -> structArg
        is String -> parseMiniNotation(input = structArg) { AtomicPattern(VoiceData.empty.copy(note = it)) }
        else -> silence
    }

    StructPattern(source, structure)
}

/**
 * Filters the pattern using a boolean mask.
 *
 * Events in the source pattern are only heard if the mask pattern
 * has a truthy value at that time.
 *
 * Example: note("c e g").mask("1 0 1")
 */
@StrudelDsl
val StrudelPattern.mask by dslPatternExtension { source, args ->
    val maskPattern = when (val maskArg = args.firstOrNull()) {
        is StrudelPattern -> maskArg
        is String -> parseMiniNotation(input = maskArg) {
            AtomicPattern(VoiceData.empty.copy(note = it))
        }

        else -> silence
    }

    MaskPattern(source, maskPattern)
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


// note() //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val noteMutation = voiceModifier { input ->
    val newNote = input?.toString()
    copy(note = newNote, freqHz = Tones.noteToFreq(newNote ?: ""))
}

/** Modifies the notes of a pattern */
@StrudelDsl
val StrudelPattern.note by dslPatternExtension { p, args ->
    if (args.isEmpty()) {
        p.reinterpretVoice { it.resolveNote() }
    } else {
        p.applyParam(args, noteMutation) { source, control -> source.noteMutation(control.note) }
    }
}

/** Creates a pattern with notes */
@StrudelDsl
val note by dslFunction { args -> args.toPattern(noteMutation) }

// n() /////////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val nMutation = voiceModifier {
    resolveNote(it?.asIntOrNull())
}

/** Sets the note number or sample index */
@StrudelDsl
val StrudelPattern.n by dslPatternExtension { p, args ->
    if (args.isEmpty()) {
        p.reinterpretVoice { it.resolveNote() }
    } else {
        p.applyParam(args, nMutation) { source, control -> source.resolveNote(control.soundIndex) }
    }
}

/** Sets the note number or sample index */
@StrudelDsl
val n by dslFunction { args -> args.toPattern(nMutation) }

// sound() /////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val soundMutation = voiceModifier {
    val split = it?.toString()?.split(":") ?: emptyList()

    copy(
        sound = split.getOrNull(0),
        soundIndex = split.getOrNull(1)?.toIntOrNull(),
    )
}

/** Modifies the sounds of a pattern */
@StrudelDsl
val StrudelPattern.sound by dslPatternExtension { p, args ->
    p.applyParam(args, soundMutation) { source, control -> source.soundMutation(control.sound) }
}

/** Creates a pattern with sounds */
@StrudelDsl
val sound by dslFunction { args -> args.toPattern(soundMutation) }

/** Alias for [sound] */
@StrudelDsl
val StrudelPattern.s by dslPatternExtension { p, args -> p.sound(args) }

/** Alias for [sound] */
@StrudelDsl
val s by dslFunction { args -> args.toPattern(soundMutation) }

// bank() //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val bankMutation = voiceModifier {
    copy(bank = it?.toString())
}

/** Modifies the banks of a pattern */
@StrudelDsl
val StrudelPattern.bank by dslPatternExtension { p, args ->
    p.applyParam(args, bankMutation) { source, control -> source.bankMutation(control.bank) }
}

/** Creates a pattern with banks */
@StrudelDsl
val bank by dslFunction { args -> args.toPattern(bankMutation) }

// gain() //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val gainMutation = voiceModifier {
    copy(gain = it?.toString()?.toDoubleOrNull())
}

/** Modifies the gains of a pattern */
@StrudelDsl
val StrudelPattern.gain by dslPatternExtension { p, args ->
    p.applyParam(args, gainMutation) { source, control -> source.gainMutation(control.gain) }
}

/** Creates a pattern with gains */
@StrudelDsl
val gain by dslFunction { args -> args.toPattern(gainMutation) }

// pan() //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val panMutation = voiceModifier {
    copy(pan = it?.toString()?.toDoubleOrNull())
}

/** Modifies the pans of a pattern */
@StrudelDsl
val StrudelPattern.pan by dslPatternExtension { p, args ->
    p.applyParam(args, panMutation) { source, control -> source.panMutation(control.pan) }
}

/** Creates a pattern with pans */
@StrudelDsl
val pan by dslFunction { args -> args.toPattern(panMutation) }

// legato() //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val legatoMutation = voiceModifier {
    copy(legato = it?.asDoubleOrNull())
}

/** Modifies the legatos of a pattern */
@StrudelDsl
val StrudelPattern.legato by dslPatternExtension { p, args ->
    p.applyParam(args, legatoMutation) { source, control -> source.legatoMutation(control.legato) }
}

/** Creates a pattern with legatos */
@StrudelDsl
val legato by dslFunction { args -> args.toPattern(legatoMutation) }

/** Alias for [legato] */
@StrudelDsl
val StrudelPattern.clip by dslPatternExtension { p, args -> p.legato(args) }

/** Alias for [legato] */
@StrudelDsl
val clip by dslFunction { args -> args.toPattern(legatoMutation) }

// unison //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val unisonMutation = voiceModifier {
    copy(voices = it?.asDoubleOrNull())
}

/** Modifies the voices of a pattern */
@StrudelDsl
val StrudelPattern.unison by dslPatternExtension { p, args ->
    p.applyParam(args, unisonMutation) { source, control -> source.unisonMutation(control.voices) }
}

/** Creates a pattern with unison */
@StrudelDsl
val unison by dslFunction { args -> args.toPattern(unisonMutation) }

/** Alias for [unison] */
@StrudelDsl
val StrudelPattern.uni by dslPatternExtension { p, args -> p.unison(args) }

/** Alias for [unison] */
@StrudelDsl
val uni by dslFunction { args -> args.toPattern(unisonMutation) }

// detune //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val detuneMutation = voiceModifier {
    copy(freqSpread = it?.asDoubleOrNull())
}

/** Sets the oscillator frequency spread (for supersaw) */
@StrudelDsl
val StrudelPattern.detune by dslPatternExtension { p, args ->
    p.applyParam(args, detuneMutation) { source, control -> source.detuneMutation(control.freqSpread) }
}

/** Sets the oscillator frequency spread (for supersaw) */
@StrudelDsl
val detune by dslFunction { args -> args.toPattern(detuneMutation) }

// spread //////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val spreadMutation = voiceModifier {
    copy(panSpread = it?.asDoubleOrNull())
}

/** Sets the oscillator pan spread (for supersaw) */
@StrudelDsl
val StrudelPattern.spread by dslPatternExtension { p, args ->
    p.applyParam(args, spreadMutation) { source, control -> source.spreadMutation(control.panSpread) }
}

/** Sets the oscillator pan spread (for supersaw) */
@StrudelDsl
val spread by dslFunction { args -> args.toPattern(spreadMutation) }

// density /////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val densityMutation = voiceModifier {
    copy(density = it?.asDoubleOrNull())
}

/** Sets the oscillator density (for supersaw) */
@StrudelDsl
val StrudelPattern.density by dslPatternExtension { p, args ->
    p.applyParam(args, densityMutation) { source, control -> source.densityMutation(control.density) }
}

/** Sets the oscillator density (for supersaw) */
@StrudelDsl
val density by dslFunction { args -> args.toPattern(densityMutation) }

/** Alias for [density] */
@StrudelDsl
val StrudelPattern.d by dslPatternExtension { p, args -> p.density(args) }

/** Alias for [density] */
@StrudelDsl
val d by dslFunction { args -> args.toPattern(densityMutation) }

// ADSR Attack /////////////////////////////////////////////////////////////////////////////////////////////////////////

private val attackMutation = voiceModifier {
    copy(adsr = adsr.copy(attack = it?.asDoubleOrNull()))
}

/** Sets the note envelope attack */
@StrudelDsl
val StrudelPattern.attack by dslPatternExtension { p, args ->
    p.applyParam(args, attackMutation) { source, control -> source.attackMutation(control.adsr.attack) }
}

/** Sets the note envelope attack */
@StrudelDsl
val attack by dslFunction { args -> args.toPattern(attackMutation) }

// ADSR Decay //////////////////////////////////////////////////////////////////////////////////////////////////////////

private val decayMutation = voiceModifier {
    copy(adsr = adsr.copy(decay = it?.asDoubleOrNull()))
}

/** Sets the note envelope decay */
@StrudelDsl
val StrudelPattern.decay by dslPatternExtension { p, args ->
    p.applyParam(args, decayMutation) { source, control -> source.decayMutation(control.adsr.decay) }
}

/** Sets the note envelope decay */
@StrudelDsl
val decay by dslFunction { args -> args.toPattern(decayMutation) }

// ADSR Sustain ////////////////////////////////////////////////////////////////////////////////////////////////////////

private val sustainMutation = voiceModifier {
    copy(adsr = adsr.copy(sustain = it?.asDoubleOrNull()))
}

/** Sets the note envelope sustain */
@StrudelDsl
val StrudelPattern.sustain by dslPatternExtension { p, args ->
    p.applyParam(args, sustainMutation) { source, control -> source.sustainMutation(control.adsr.sustain) }
}

/** Sets the note envelope sustain */
@StrudelDsl
val sustain by dslFunction { args -> args.toPattern(sustainMutation) }

// ADSR Release ////////////////////////////////////////////////////////////////////////////////////////////////////////

private val releaseMutation = voiceModifier {
    copy(adsr = adsr.copy(release = it?.asDoubleOrNull()))
}

/** Sets the note envelope release */
@StrudelDsl
val StrudelPattern.release by dslPatternExtension { p, args ->
    p.applyParam(args, releaseMutation) { source, control -> source.releaseMutation(control.adsr.release) }
}

/** Sets the note envelope release */
@StrudelDsl
val release by dslFunction { args -> args.toPattern(releaseMutation) }

// ADSR Combined ///////////////////////////////////////////////////////////////////////////////////////////////////////

val adsrMutation = voiceModifier {
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

/** Sets the note envelope release */
@StrudelDsl
val StrudelPattern.adsr by dslPatternExtension { p, args ->
    p.applyParam(args, adsrMutation) { source, control -> source.copy(adsr = control.adsr.mergeWith(source.adsr)) }
}

/** Sets the note envelope release */
@StrudelDsl
val adsr by dslFunction { args -> args.toPattern(adsrMutation) }

// Filters - LowPass - lpf() ///////////////////////////////////////////////////////////////////////////////////////////

private val lpfMutation = voiceModifier {
    val cutoff = it?.asDoubleOrNull()
    val filter = FilterDef.LowPass(cutoffHz = cutoff ?: 1000.0, q = resonance)

    copy(
        cutoff = cutoff,
        filters = filters.addOrReplace(filter),
    )
}

/** Adds a Low Pass Filter with the given cutoff frequency. */
@StrudelDsl
val StrudelPattern.lpf by dslPatternExtension { p, args ->
    p.applyParam(args, lpfMutation) { source, control ->
        source
            .copy(resonance = control.resonance ?: source.resonance)
            .lpfMutation(control.filters.getByType<FilterDef.LowPass>()?.cutoffHz)
    }
}

/** Adds a Low Pass Filter with the given cutoff frequency. */
@StrudelDsl
val lpf by dslFunction { args -> args.toPattern(lpfMutation) }

// Filters - HighPass - hpf() //////////////////////////////////////////////////////////////////////////////////////////

private val hpfMutation = voiceModifier {
    val cutoff = it?.asDoubleOrNull()
    val filter = FilterDef.HighPass(cutoffHz = cutoff ?: 1000.0, q = resonance)

    copy(
        hcutoff = cutoff,
        filters = filters.addOrReplace(filter),
    )
}

/** Adds a High Pass Filter with the given cutoff frequency. */
@StrudelDsl
val StrudelPattern.hpf by dslPatternExtension { p, args ->
    p.applyParam(args, hpfMutation) { source, control ->
        source
            .copy(resonance = control.resonance ?: source.resonance)
            .hpfMutation(control.filters.getByType<FilterDef.HighPass>()?.cutoffHz)
    }
}

/** Adds a High Pass Filter with the given cutoff frequency. */
@StrudelDsl
val hpf by dslFunction { args -> args.toPattern(hpfMutation) }

// Filters - BandPass - bandf() ////////////////////////////////////////////////////////////////////////////////////////

private val bandfMutation = voiceModifier {
    val cutoff = it?.asDoubleOrNull()
    val filter = FilterDef.BandPass(cutoffHz = cutoff ?: 1000.0, q = resonance)

    copy(
        bandf = cutoff,
        filters = filters.addOrReplace(filter),
    )
}

/** Adds a High Pass Filter with the given cutoff frequency. */
@StrudelDsl
val StrudelPattern.bandf by dslPatternExtension { p, args ->
    p.applyParam(args, bandfMutation) { source, control ->
        source
            .copy(resonance = control.resonance ?: source.resonance)
            .bandfMutation(control.filters.getByType<FilterDef.BandPass>()?.cutoffHz)
    }
}

/** Adds a High Pass Filter with the given cutoff frequency. */
@StrudelDsl
val bandf by dslFunction { args -> args.toPattern(bandfMutation) }

/** Alias for [bandf] */
@StrudelDsl
val StrudelPattern.bpf by dslPatternExtension { p, args -> p.bandf(args) }

/** Alias for [bandf] */
@StrudelDsl
val bpf by dslFunction { args -> args.toPattern(bandfMutation) }

// Filters - Notch | inverse BandPass - notchf() ///////////////////////////////////////////////////////////////////////

private val notchfMutation = voiceModifier {
    val cutoff = it?.asDoubleOrNull()
    val filter = FilterDef.Notch(cutoffHz = cutoff ?: 1000.0, q = resonance)

    copy(
        cutoff = cutoff,
        filters = filters.addOrReplace(filter),
    )
}

/** Adds a Notch Filter with the given cutoff frequency. */
@StrudelDsl
val StrudelPattern.notchf by dslPatternExtension { p, args ->
    p.applyParam(args, notchfMutation) { source, control ->
        source
            .copy(resonance = control.resonance ?: source.resonance)
            .notchfMutation(control.filters.getByType<FilterDef.Notch>()?.cutoffHz)
    }
}

/** Adds a Notch Filter with the given cutoff frequency. */
@StrudelDsl
val notchf by dslFunction { args -> args.toPattern(notchfMutation) }

// Filters - resonance() ///////////////////////////////////////////////////////////////////////////////////////////////

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

/** Sets the note envelope release */
@StrudelDsl
val StrudelPattern.resonance by dslPatternExtension { p, args ->
    p.applyParam(args, resonanceMutation) { source, control -> source.resonanceMutation(control.resonance) }
}

/** Sets the note envelope release */
@StrudelDsl
val resonance by dslFunction { args -> args.toPattern(resonanceMutation) }

/** Alias for [resonance] */
@StrudelDsl
val StrudelPattern.res by dslPatternExtension { p, args -> p.resonance(args) }

/** Alias for [resonance] */
@StrudelDsl
val res by dslFunction { args -> args.toPattern(resonanceMutation) }

// distort() ///////////////////////////////////////////////////////////////////////////////////////////////////////////

private val distortMutation = voiceModifier {
    copy(distort = it?.asDoubleOrNull())
}

@StrudelDsl
val StrudelPattern.distort by dslPatternExtension { p, args ->
    p.applyParam(args, distortMutation) { source, control -> source.distortMutation(control.distort) }
}

@StrudelDsl
val distort by dslFunction { args -> args.toPattern(distortMutation) }

// crush() /////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val crushMutation = voiceModifier { copy(crush = it?.asDoubleOrNull()) }

@StrudelDsl
val StrudelPattern.crush by dslPatternExtension { p, args ->
    p.applyParam(args, crushMutation) { source, control -> source.crushMutation(control.crush) }
}

@StrudelDsl
val crush by dslFunction { args -> args.toPattern(crushMutation) }

// coarse() ////////////////////////////////////////////////////////////////////////////////////////////////////////////

private val coarseMutation = voiceModifier {
    copy(coarse = it?.asDoubleOrNull())
}

@StrudelDsl
val StrudelPattern.coarse by dslPatternExtension { p, args ->
    p.applyParam(args, coarseMutation) { source, control -> source.coarseMutation(control.coarse) }
}

@StrudelDsl
val coarse by dslFunction { args -> args.toPattern(coarseMutation) }

// Reverb room() ///////////////////////////////////////////////////////////////////////////////////////////////////////

private val roomMutation = voiceModifier {
    copy(room = it?.asDoubleOrNull())
}

@StrudelDsl
val StrudelPattern.room by dslPatternExtension { p, args ->
    p.applyParam(args, roomMutation) { source, control -> source.roomMutation(control.room) }
}

@StrudelDsl
val room by dslFunction { args -> args.toPattern(roomMutation) }

// Reverb roomsize() ///////////////////////////////////////////////////////////////////////////////////////////////////

private val roomSizeMutation = voiceModifier {
    copy(roomSize = it?.asDoubleOrNull())
}

@StrudelDsl
val StrudelPattern.roomsize by dslPatternExtension { p, args ->
    p.applyParam(args, roomSizeMutation) { source, control -> source.roomSizeMutation(control.roomSize) }
}

@StrudelDsl
val roomsize by dslFunction { args -> args.toPattern(roomSizeMutation) }

/** Alias for [roomsize] */
@StrudelDsl
val StrudelPattern.rsize by dslPatternExtension { p, args -> p.roomsize(args) }

/** Alias for [roomsize] */
@StrudelDsl
val rsize by dslFunction { args -> args.toPattern(roomSizeMutation) }

// Delay delay() ///////////////////////////////////////////////////////////////////////////////////////////////////////

private val delayMutation = voiceModifier {
    copy(delay = it?.asDoubleOrNull())
}

@StrudelDsl
val StrudelPattern.delay by dslPatternExtension { p, args ->
    p.applyParam(args, delayMutation) { source, control -> source.delayMutation(control.delay) }
}

@StrudelDsl
val delay by dslFunction { args -> args.toPattern(delayMutation) }

// Delay delaytime() ///////////////////////////////////////////////////////////////////////////////////////////////////////

private val delayTimeMutation = voiceModifier {
    copy(delayTime = it?.asDoubleOrNull())
}

@StrudelDsl
val StrudelPattern.delaytime by dslPatternExtension { p, args ->
    p.applyParam(args, delayTimeMutation) { source, control -> source.delayTimeMutation(control.delayTime) }
}

@StrudelDsl
val delaytime by dslFunction { args -> args.toPattern(delayTimeMutation) }

// Delay delayfeedback() ///////////////////////////////////////////////////////////////////////////////////////////////////////

private val delayFeedbackMutation = voiceModifier {
    copy(delayFeedback = it?.asDoubleOrNull())
}

@StrudelDsl
val StrudelPattern.delayfeedback by dslPatternExtension { p, args ->
    p.applyParam(args, delayFeedbackMutation) { source, control -> source.delayFeedbackMutation(control.delayFeedback) }
}

@StrudelDsl
val delayfeedback by dslFunction { args -> args.toPattern(delayFeedbackMutation) }

// Routing orbit() /////////////////////////////////////////////////////////////////////////////////////////////////////

private val orbitMutation = voiceModifier {
    copy(orbit = it?.asIntOrNull())
}

@StrudelDsl
val StrudelPattern.orbit by dslPatternExtension { p, args ->
    p.applyParam(args, orbitMutation) { source, control -> source.orbitMutation(control.orbit) }
}

@StrudelDsl
val orbit by dslFunction { args -> args.toPattern(orbitMutation) }

// Context scale() /////////////////////////////////////////////////////////////////////////////////////////////////////

private val scaleMutation = voiceModifier { scale ->
    val newScale = scale?.toString()?.cleanScaleName()
    copy(scale = newScale).resolveNote()
}

@StrudelDsl
val StrudelPattern.scale by dslPatternExtension { p, args ->
    p.applyParam(args, scaleMutation) { source, control -> source.scaleMutation(control.scale) }
}

@StrudelDsl
val scale by dslFunction { args -> args.toPattern(scaleMutation) }

// vibrato() ///////////////////////////////////////////////////////////////////////////////////////////////////////////

private val vibratoMutation = voiceModifier {
    copy(vibrato = it?.asDoubleOrNull())
}

/** Sets the vibrato frequency (speed) in Hz. */
@StrudelDsl
val StrudelPattern.vibrato by dslPatternExtension { p, args ->
    p.applyParam(args, vibratoMutation) { source, control -> source.vibratoMutation(control.vibrato) }
}

/** Sets the vibrato frequency (speed) in Hz. */
@StrudelDsl
val vibrato by dslFunction { args -> args.toPattern(vibratoMutation) }

/** Alias for [vibrato] */
@StrudelDsl
val StrudelPattern.vib by dslPatternExtension { p, args -> p.vibrato(args) }

/** Alias for [vibrato] */
@StrudelDsl
val vib by dslFunction { args -> args.toPattern(vibratoMutation) }

// vibratoMod() ////////////////////////////////////////////////////////////////////////////////////////////////////////

private val vibratoModMutation = voiceModifier {
    copy(vibratoMod = it?.asDoubleOrNull())
}

/** Sets the vibratoMod frequency (speed) in Hz. */
@StrudelDsl
val StrudelPattern.vibratoMod by dslPatternExtension { p, args ->
    p.applyParam(args, vibratoModMutation) { source, control -> source.vibratoModMutation(control.vibratoMod) }
}

/** Sets the vibratoMod frequency (speed) in Hz. */
@StrudelDsl
val vibratoMod by dslFunction { args -> args.toPattern(vibratoModMutation) }

/** Alias for [vibratoMod] */
val StrudelPattern.vibmod by dslPatternExtension { p, args -> p.vibratoMod(args) }

/** Alias for [vibratoMod] */
val vibmod by dslFunction { args -> args.toPattern(vibratoModMutation) }

// add() ///////////////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Adds the given amount to the pattern's value.
 *
 * This is commonly used to transpose note numbers or modify continuous signals.
 *
 * Example: n("0 2").add("5") -> n("5 7")
 */
@StrudelDsl
val StrudelPattern.add: DslPatternMethod by dslPatternExtension { source, args ->
    val controlPattern = args.toPattern {
        copy(value = it?.asVoiceValue())
    }

    ControlPattern(
        source = source,
        control = controlPattern,
        mapper = { it }, // No mapping needed for the control data itself
        combiner = { srcData, ctrlData ->
            val amount = ctrlData.value
            // Add to 'value' if present
            val newValue = srcData.value?.plus(ctrlData.value)

//            // Add to 'soundIndex' if present
//            val newSoundIndex = if (srcData.soundIndex != null) {
//                srcData.soundIndex!! + amount.toInt()
//            } else {
//                null
//            }

            srcData.copy(
                value = newValue ?: srcData.value,
//                soundIndex = newSoundIndex ?: srcData.soundIndex
            )
        }
    )
}

// accelerate() ////////////////////////////////////////////////////////////////////////////////////////////////////////

private val accelerateMutation = voiceModifier {
    copy(accelerate = it?.asDoubleOrNull())
}

@StrudelDsl
val StrudelPattern.accelerate by dslPatternExtension { p, args ->
    p.applyParam(args, accelerateMutation) { source, control -> source.accelerateMutation(control.accelerate) }
}

@StrudelDsl
val accelerate by dslFunction { args -> args.toPattern(accelerateMutation) }
