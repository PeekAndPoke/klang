@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.sprudel.*
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.AtomicPattern
import io.peekandpoke.klang.sprudel.pattern.BindPattern
import io.peekandpoke.klang.sprudel.pattern.ControlPattern
import io.peekandpoke.klang.sprudel.pattern.ReinterpretPattern.Companion.reinterpretVoice
import io.peekandpoke.klang.sprudel.pattern.StackPattern
import io.peekandpoke.klang.tones.Tones
import io.peekandpoke.klang.tones.chord.Chord
import io.peekandpoke.klang.tones.distance.Distance
import io.peekandpoke.klang.tones.interval.Interval
import io.peekandpoke.klang.tones.midi.Midi
import io.peekandpoke.klang.tones.note.Note
import io.peekandpoke.klang.tones.scale.Scale
import kotlin.math.pow

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in SprudelRegistry.
 */
var sprudelLangTonalInit = false

/** Cleans up the scale name */
fun String.cleanScaleName() = replace(":", " ").replace("_", " ")

/**
 * Resolves the note and frequency based on the index and the current scale.
 *
 * @param newIndex An optional new index to force (e.g. from n("0")).
 *                 If null, it tries to use existing soundIndex or interpret value as index.
 */
fun SprudelVoiceData.resolveNote(newIndex: Int? = null): SprudelVoiceData {
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
            soundIndex = null, // sound-index was consumed
            value = null,
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
        soundIndex = n ?: soundIndex,
    )
}

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Note / Sound / Tonal
// ///

// -- scale() ----------------------------------------------------------------------------------------------------------

fun applyScale(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { scaleName ->
        copy(scale = scaleName?.cleanScaleName()).resolveNote()
    }
}

internal val SprudelPattern._scale by dslPatternExtension { p, args, /* callInfo */ _ -> applyScale(p, args) }
internal val String._scale by dslStringExtension { p, args, callInfo -> p._scale(args, callInfo) }
internal val _scale by dslPatternMapper { args, callInfo -> { p -> p._scale(args, callInfo) } }
internal val PatternMapperFn._scale by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_scale(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the musical scale context for resolving note indices to note names.
 *
 * When a scale is set, numeric values passed to [n] are resolved against the scale's note
 * list using `Scale.steps()`. Scale names use the format `"root:mode"` or `"root mode"`,
 * e.g. `"c4:major"` or `"c4 minor"`. If no scale is set, numeric indices map to semitones.
 * When called with no argument, reinterprets the current event value as a scale name.
 *
 * @param name The scale name in `"root:mode"` or `"root mode"` format, e.g. `"c4:major"`.
 * @param-tool name SprudelScaleEditor
 * @return A pattern with the scale context applied to each event.
 *
 * ```KlangScript
 * n("0 1 2 3").scale("c4:major")          // C4, D4, E4, F4
 * ```
 *
 * ```KlangScript
 * n("0 2 4").scale("<c4:major a3:minor>")  // alternates scale per cycle
 * ```
 *
 * @category tonal
 * @tags scale, pitch, musical scale, mode, tuning
 */
@SprudelDsl
fun SprudelPattern.scale(name: PatternLike? = null): SprudelPattern =
    this._scale(listOfNotNull(name).asSprudelDslArgs())

/** Applies scale context to a string pattern; numeric values are resolved to scale notes. */
@SprudelDsl
fun String.scale(name: PatternLike? = null): SprudelPattern =
    this._scale(listOfNotNull(name).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the scale context for resolving note indices.
 *
 * ```KlangScript
 * n("0 1 2").apply(scale("c4:major"))     // mapper form: C4, D4, E4
 * ```
 *
 * @category tonal
 * @tags scale, pitch, musical scale, mode, tuning
 */
@SprudelDsl
fun scale(name: PatternLike? = null): PatternMapperFn = _scale(listOfNotNull(name).asSprudelDslArgs())

/** Chains a scale operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.scale(name: PatternLike? = null): PatternMapperFn =
    this._scale(listOfNotNull(name).asSprudelDslArgs())

// -- note() -----------------------------------------------------------------------------------------------------------

private val noteMutation = voiceModifier { input ->
    input?.toString()?.let { newNote ->
        copy(
            note = newNote,
            freqHz = Tones.noteToFreq(newNote),
            value = null,
        )
    } ?: this
}

fun applyNote(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return if (args.isEmpty()) {
        source.reinterpretVoice {
            it.resolveNote().copy(soundIndex = null, value = null)
        }
    } else {
        source._applyControlFromParams(args, noteMutation) { src, ctrl ->
            src.noteMutation(
                ctrl.note ?: ctrl.value?.asString
            )
        }
    }
}

internal val _note by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(noteMutation).note() }

internal val SprudelPattern._note by dslPatternExtension { p, args, /* callInfo */ _ -> applyNote(p, args) }

internal val String._note by dslStringExtension { p, args, callInfo -> p._note(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Creates a pattern of musical notes from a mini-notation string or sequence of note names.
 *
 * Note values can be scientific notation (`"c4"`, `"d#3"`, `"bb2"`), MIDI note numbers, or
 * numeric indices resolved via the active [scale] context.
 *
 * ```KlangScript
 * note("c4 e4 g4")          // arpeggiate a C major chord
 * ```
 *
 * ```KlangScript
 * note("<c3 e3 g3> b2")     // alternating chord tones with a bass note
 * ```
 *
 * @param note The note pattern in mini-notation, e.g. `"c4 e4 g4"`.
 * @param-tool note SprudelNoteEditor
 * @category tonal
 * @tags note, pitch, frequency, MIDI, note name, pattern-creator
 */
@SprudelDsl
fun note(vararg note: PatternLike): SprudelPattern = _note(note.toList().asSprudelDslArgs())

/** Reinterprets the current value of this pattern as a note name. */
@SprudelDsl
fun SprudelPattern.note(): SprudelPattern = this._note(emptyList())

/** Applies note values from arguments (or reinterprets current value as a note name). */
@SprudelDsl
fun SprudelPattern.note(noteName: PatternLike): SprudelPattern = this._note(listOf(noteName).asSprudelDslArgs())

/** Applies note values to a string pattern. */
@SprudelDsl
fun String.note(noteName: PatternLike): SprudelPattern = this._note(listOf(noteName).asSprudelDslArgs())

// -- n() --------------------------------------------------------------------------------------------------------------

private val nMutation = voiceModifier {
    copy(
        soundIndex = it?.asIntOrNull() ?: soundIndex,
        value = null,
    )
}

fun applyN(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return if (args.isEmpty()) {
        // TODO: test this
        source.reinterpretVoice {
            it.copy(
                soundIndex = it.soundIndex ?: it.value?.asInt,
                value = null,
            )
        }
    } else {
        source._applyControlFromParams(args, nMutation) { src, ctrl ->
            src.nMutation(
                ctrl.soundIndex ?: ctrl.value?.asInt
            )
        }
    }
}

internal val _n by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(nMutation).n() }

internal val SprudelPattern._n by dslPatternExtension { p, args, /* callInfo */ _ -> applyN(p, args) }

internal val String._n by dslStringExtension { p, args, callInfo -> p._n(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the sound index on this pattern.
 *
 * When param [index] is null, the sequence values will be reinterpreted as sound index.
 *
 * ```KlangScript
 * n("0 2 4").scale("c4:major").note()   // indices 0, 2, 4 → C4, E4, G4
 * ```
 *
 * ```KlangScript
 * s("hh").n("0 1 2")                    // selects different hh samples by index
 * ```
 *
 * @param index The sound index to set, or null to reparse sequence values as sound index.
 * @param-tool index SprudelScaleDegreeEditor
 *
 * @category tonal
 * @tags n, note number, sample index, pitch index, pattern-creator
 */
@SprudelDsl
fun SprudelPattern.n(index: PatternLike? = null): SprudelPattern =
    this._n(listOfNotNull(index).asSprudelDslArgs())

/** Sets the sound index on this string pattern. */
@SprudelDsl
fun String.n(index: PatternLike? = null): SprudelPattern =
    this._n(listOfNotNull(index).asSprudelDslArgs())

/**
 * Creates a pattern of sound indices.
 *
 * @param index The scale degree index pattern in mini-notation, e.g. `"0 1 2 3"`.
 * @param-tool index SprudelScaleDegreeEditor
 */
@SprudelDsl
fun n(index: PatternLike): SprudelPattern = _n(listOf(index).asSprudelDslArgs())

// -- sound() / s() ----------------------------------------------------------------------------------------------------

private val soundMutation = voiceModifier {
    if (it == null) return@voiceModifier this

    val split = it.toString().split(":")

    copy(
        sound = split.getOrNull(0),
        // Preserve existing index if the string doesn't specify one.
        soundIndex = split.getOrNull(1)?.toIntOrNull() ?: soundIndex,
        // Preserve existing gain if the string doesn't specify one.
        gain = split.getOrNull(2)?.toDoubleOrNull() ?: gain,
        // clear the value
        value = null,
    )
}

fun applySound(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return if (args.isEmpty()) {
        // TODO: test this
        source.reinterpretVoice {
            it.soundMutation(it.value?.asString)
        }
    } else {
        source._applyControlFromParams(args, soundMutation) { src, ctrl ->
            src.copy(
                sound = ctrl.sound ?: src.sound,
                soundIndex = ctrl.soundIndex ?: src.soundIndex,
            )
        }
    }
}

internal val SprudelPattern._sound by dslPatternExtension { p, args, /* callInfo */ _ -> applySound(p, args) }

internal val _sound by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(soundMutation).sound() }

internal val String._sound by dslStringExtension { p, args, callInfo -> p._sound(args, callInfo) }

internal val SprudelPattern._s by dslPatternExtension { p, args, callInfo -> p._sound(args, callInfo) }

internal val _s by dslPatternFunction { args, callInfo -> _sound(args, callInfo) }

internal val String._s by dslStringExtension { p, args, callInfo -> p._sound(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Creates a pattern selecting a sound (instrument or sample bank) by name.
 *
 * Each event's value selects the instrument or sample bank used during playback.
 * The format `"name:index"` also sets the sample index, e.g. `"bd:2"` selects sample 2
 * from the `bd` bank.
 *
 * ```KlangScript
 * sound("bd sd hh")  // basic drum pattern
 * ```
 *
 * ```KlangScript
 * sound("bd bd bd bd ").n("0 1 2 3")  // changes the sound variants
 * ```
 *
 * @param name The sound/sample name pattern in mini-notation, e.g. `"bd sd hh"`.
 * @param-tool name SprudelSampleSequenceEditor
 * @alias s
 * @category tonal
 * @tags sound, sample, instrument, s, pattern-creator
 */
@SprudelDsl
fun SprudelPattern.sound(name: PatternLike): SprudelPattern = this._sound(listOf(name).asSprudelDslArgs())

/**
 * Reinterprets sequence values as sounds.
 *
 * ```KlangScript
 * seq("bd hh sd hh").sound()  // interprets the sequence values as sounds
 * ```
 */
@SprudelDsl
fun SprudelPattern.sound(): SprudelPattern = this._sound(emptyList())

/** Modifies the sounds of a string pattern. */
@SprudelDsl
fun String.sound(name: PatternLike): SprudelPattern = this._sound(listOf(name).asSprudelDslArgs())

/** Reinterprets sequence values as sounds. */
@SprudelDsl
fun String.sound(): SprudelPattern = this._sound(emptyList())

/**
 * Creates a pattern of sounds.
 *
 * @param name The sound/sample name pattern in mini-notation, e.g. `"bd sd hh"`.
 * @param-tool name SprudelSampleSequenceEditor
 */
@SprudelDsl
fun sound(name: PatternLike): SprudelPattern = _sound(listOf(name).asSprudelDslArgs())

/** Alias for [sound]. Creates a pattern selecting a sound (instrument or sample bank) by name.
 *
 * Each event's value selects the instrument or sample bank used during playback.
 * The format `"name:index"` also sets the sample index, e.g. `"bd:2"` selects sample 2
 * from the `bd` bank.
 *
 * ```KlangScript
 * sound("bd sd hh")  // basic drum pattern
 * ```
 *
 * ```KlangScript
 * sound("bd bd bd bd ").n("0 1 2 3")  // changes the sound variants
 * ```
 *
 * @param name The sound/sample name pattern in mini-notation, e.g. `"bd sd hh"`.
 * @param-tool name SprudelSampleSequenceEditor
 * @alias sound
 * @category tonal
 * @tags sound, sample, instrument, s, pattern-creator
 */
@SprudelDsl
fun SprudelPattern.s(name: PatternLike): SprudelPattern = this._s(listOf(name).asSprudelDslArgs())

/** Alias for [sound]. Reinterprets sequence values as sounds. */
@SprudelDsl
fun SprudelPattern.s(): SprudelPattern = this._s(emptyList())

/** Alias for [sound] on a string pattern. */
@SprudelDsl
fun String.s(name: PatternLike): SprudelPattern = this._s(listOf(name).asSprudelDslArgs())

/** Alias for [sound] on a string pattern. */
@SprudelDsl
fun String.s(): SprudelPattern = this._s(emptyList())

/**
 * Alias for [sound]. Creates a sound pattern.
 *
 * @param name The sound/sample name pattern in mini-notation, e.g. `"bd sd hh"`.
 * @param-tool name SprudelSampleSequenceEditor
 */
@SprudelDsl
fun s(name: PatternLike): SprudelPattern = _s(listOf(name).asSprudelDslArgs())

// -- bank() -----------------------------------------------------------------------------------------------------------

fun applyBank(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { bankName ->
        copy(bank = bankName)
    }
}

internal val SprudelPattern._bank by dslPatternExtension { p, args, /* callInfo */ _ -> applyBank(p, args) }
internal val String._bank by dslStringExtension { p, args, callInfo -> p._bank(args, callInfo) }
internal val _bank by dslPatternMapper { args, callInfo -> { p -> p._bank(args, callInfo) } }
internal val PatternMapperFn._bank by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_bank(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the sample bank for each event, overriding which collection of samples is used.
 *
 * The bank determines where samples are loaded from, independently of the sound name.
 * Useful when you want to switch sample collections without changing sound identifiers.
 * When called with no argument, reinterprets the current event value as a bank name.
 *
 * ```KlangScript
 * s("bd sd hh").bank("RolandTR808")     // load all sounds from the TR-808 bank
 * ```
 *
 * ```KlangScript
 * s("bd sd").bank("<TR808 TR909>")      // alternate sample banks each cycle
 * ```
 *
 * @category tonal
 * @tags bank, sample bank, instrument
 */
@SprudelDsl
fun SprudelPattern.bank(name: PatternLike? = null): SprudelPattern =
    this._bank(listOfNotNull(name).asSprudelDslArgs())

/** Sets the sample bank on a string pattern. */
@SprudelDsl
fun String.bank(name: PatternLike? = null): SprudelPattern =
    this._bank(listOfNotNull(name).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the sample bank for each event.
 * When called with no argument, reinterprets the current event value as a bank name.
 *
 * ```KlangScript
 * s("bd sd").apply(bank("RolandTR808"))   // mapper form
 * ```
 *
 * @category tonal
 * @tags bank, sample bank, instrument
 */
@SprudelDsl
fun bank(name: PatternLike? = null): PatternMapperFn = _bank(listOfNotNull(name).asSprudelDslArgs())

/** Chains a bank operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.bank(name: PatternLike? = null): PatternMapperFn =
    this._bank(listOfNotNull(name).asSprudelDslArgs())

// -- legato() / clip() ------------------------------------------------------------------------------------------------

fun applyLegato(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args) { amount ->
        copy(legato = amount)
    }
}

internal val SprudelPattern._legato by dslPatternExtension { p, args, /* callInfo */ _ -> applyLegato(p, args) }
internal val String._legato by dslStringExtension { p, args, callInfo -> p._legato(args, callInfo) }
internal val _legato by dslPatternMapper { args, callInfo -> { p -> p._legato(args, callInfo) } }
internal val PatternMapperFn._legato by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_legato(args, callInfo))
}

internal val SprudelPattern._clip by dslPatternExtension { p, args, callInfo -> p._legato(args, callInfo) }
internal val String._clip by dslStringExtension { p, args, callInfo -> p._legato(args, callInfo) }
internal val _clip by dslPatternMapper { args, callInfo -> { p -> p._legato(args, callInfo) } }
internal val PatternMapperFn._clip by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_legato(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the legato (duration scaling) factor for events in this pattern.
 *
 * A legato of `1.0` fills the full event duration; values above `1.0` create overlapping
 * notes (true legato), while values below `1.0` create staccato-like gaps between notes.
 * When called with no argument, reinterprets the current event value as a legato amount.
 *
 * ```KlangScript
 * note("c3 e3 g3").legato(1.5)   // notes overlap slightly (legato)
 * ```
 *
 * ```KlangScript
 * note("c3 e3 g3").legato(0.5)   // notes are shorter (staccato)
 * ```
 *
 * @alias clip
 * @category tonal
 * @tags legato, clip, duration, sustain, staccato
 */
@SprudelDsl
fun SprudelPattern.legato(amount: PatternLike? = null): SprudelPattern =
    this._legato(listOfNotNull(amount).asSprudelDslArgs())

/** Sets the legato (duration scaling) factor on a string pattern. */
@SprudelDsl
fun String.legato(amount: PatternLike? = null): SprudelPattern =
    this._legato(listOfNotNull(amount).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the legato factor for each event.
 * When called with no argument, reinterprets the current event value as a legato amount.
 *
 * ```KlangScript
 * note("c3 e3").apply(legato(1.5))   // mapper form
 * ```
 *
 * @alias clip
 * @category tonal
 * @tags legato, clip, duration, sustain, staccato
 */
@SprudelDsl
fun legato(amount: PatternLike? = null): PatternMapperFn = _legato(listOfNotNull(amount).asSprudelDslArgs())

/** Chains a legato operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.legato(amount: PatternLike? = null): PatternMapperFn =
    this._legato(listOfNotNull(amount).asSprudelDslArgs())

/** Alias for [legato] on this pattern. Sets the duration scaling factor. */
@SprudelDsl
fun SprudelPattern.clip(amount: PatternLike? = null): SprudelPattern =
    this._clip(listOfNotNull(amount).asSprudelDslArgs())

/** Alias for [legato] on a string pattern. */
@SprudelDsl
fun String.clip(amount: PatternLike? = null): SprudelPattern =
    this._clip(listOfNotNull(amount).asSprudelDslArgs())

/**
 * Alias for [legato]. Returns a [PatternMapperFn] that sets the legato factor for each event.
 *
 * ```KlangScript
 * note("c3 e3").apply(clip(0.5))   // mapper form
 * ```
 *
 * @alias legato
 * @category tonal
 * @tags legato, clip, duration, sustain, staccato
 */
@SprudelDsl
fun clip(amount: PatternLike? = null): PatternMapperFn = _clip(listOfNotNull(amount).asSprudelDslArgs())

/** Chains a clip operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.clip(amount: PatternLike? = null): PatternMapperFn =
    this._clip(listOfNotNull(amount).asSprudelDslArgs())

// -- vibrato() --------------------------------------------------------------------------------------------------------

fun applyVibrato(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args) { hz ->
        copy(vibrato = hz)
    }
}

internal val SprudelPattern._vibrato by dslPatternExtension { p, args, /* callInfo */ _ -> applyVibrato(p, args) }
internal val String._vibrato by dslStringExtension { p, args, callInfo -> p._vibrato(args, callInfo) }
internal val _vibrato by dslPatternMapper { args, callInfo -> { p -> p._vibrato(args, callInfo) } }
internal val PatternMapperFn._vibrato by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_vibrato(args, callInfo))
}

internal val SprudelPattern._vib by dslPatternExtension { p, args, callInfo -> p._vibrato(args, callInfo) }
internal val String._vib by dslStringExtension { p, args, callInfo -> p._vibrato(args, callInfo) }
internal val _vib by dslPatternMapper { args, callInfo -> { p -> p._vibrato(args, callInfo) } }
internal val PatternMapperFn._vib by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_vibrato(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the vibrato frequency (oscillation speed) in Hz.
 *
 * Vibrato is a periodic pitch modulation applied to a note. Higher values create faster
 * vibrato; lower values create a slower, wider wobble. Use [vibratoMod] to set the depth.
 * When called with no argument, reinterprets the current event value as a vibrato rate.
 *
 * ```KlangScript
 * note("c4 e4 g4").vibrato(5)      // 5 Hz vibrato (moderate speed)
 * ```
 *
 * ```KlangScript
 * note("c4").vibrato("<2 8>")       // alternating slow/fast vibrato per cycle
 * ```
 *
 * @alias vib
 * @category tonal
 * @tags vibrato, vib, pitch modulation, oscillation, LFO
 */
@SprudelDsl
fun SprudelPattern.vibrato(hz: PatternLike? = null): SprudelPattern =
    this._vibrato(listOfNotNull(hz).asSprudelDslArgs())

/** Sets the vibrato frequency (speed) in Hz on a string pattern. */
@SprudelDsl
fun String.vibrato(hz: PatternLike? = null): SprudelPattern =
    this._vibrato(listOfNotNull(hz).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the vibrato frequency in Hz.
 * When called with no argument, reinterprets the current event value as a vibrato rate.
 *
 * ```KlangScript
 * note("c4").apply(vibrato(5))   // mapper form
 * ```
 *
 * @alias vib
 * @category tonal
 * @tags vibrato, vib, pitch modulation, oscillation, LFO
 */
@SprudelDsl
fun vibrato(hz: PatternLike? = null): PatternMapperFn = _vibrato(listOfNotNull(hz).asSprudelDslArgs())

/** Chains a vibrato operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.vibrato(hz: PatternLike? = null): PatternMapperFn =
    this._vibrato(listOfNotNull(hz).asSprudelDslArgs())

/** Alias for [vibrato] on this pattern. Sets the vibrato frequency in Hz. */
@SprudelDsl
fun SprudelPattern.vib(hz: PatternLike? = null): SprudelPattern =
    this._vib(listOfNotNull(hz).asSprudelDslArgs())

/** Alias for [vibrato] on a string pattern. */
@SprudelDsl
fun String.vib(hz: PatternLike? = null): SprudelPattern =
    this._vib(listOfNotNull(hz).asSprudelDslArgs())

/**
 * Alias for [vibrato]. Returns a [PatternMapperFn] that sets the vibrato frequency in Hz.
 *
 * ```KlangScript
 * note("c4").apply(vib(5))   // mapper form
 * ```
 *
 * @alias vibrato
 * @category tonal
 * @tags vibrato, vib, pitch modulation, oscillation, LFO
 */
@SprudelDsl
fun vib(hz: PatternLike? = null): PatternMapperFn = _vib(listOfNotNull(hz).asSprudelDslArgs())

/** Chains a vib operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.vib(hz: PatternLike? = null): PatternMapperFn =
    this._vib(listOfNotNull(hz).asSprudelDslArgs())

// -- vibratoMod() -----------------------------------------------------------------------------------------------------

fun applyVibratoMod(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args) { depth ->
        copy(vibratoMod = depth)
    }
}

internal val SprudelPattern._vibratoMod by dslPatternExtension { p, args, /* callInfo */ _ -> applyVibratoMod(p, args) }
internal val String._vibratoMod by dslStringExtension { p, args, callInfo -> p._vibratoMod(args, callInfo) }
internal val _vibratoMod by dslPatternMapper { args, callInfo -> { p -> p._vibratoMod(args, callInfo) } }
internal val PatternMapperFn._vibratoMod by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_vibratoMod(args, callInfo))
}

internal val SprudelPattern._vibmod by dslPatternExtension { p, args, callInfo -> p._vibratoMod(args, callInfo) }
internal val String._vibmod by dslStringExtension { p, args, callInfo -> p._vibratoMod(args, callInfo) }
internal val _vibmod by dslPatternMapper { args, callInfo -> { p -> p._vibratoMod(args, callInfo) } }
internal val PatternMapperFn._vibmod by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_vibratoMod(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the vibrato depth (amplitude of pitch oscillation) in semitones.
 *
 * Controls how many semitones the vibrato deviates from the base pitch. Higher values
 * create wider, more pronounced pitch wobble. Use [vibrato] to set the speed.
 * When called with no argument, reinterprets the current event value as a vibrato depth.
 *
 * ```KlangScript
 * note("c4 e4").vibratoMod(0.5)       // half-semitone vibrato depth
 * ```
 *
 * ```KlangScript
 * note("c4").vibratoMod("<0.2 1>")    // alternating subtle/wide vibrato depth
 * ```
 *
 * @alias vibmod
 * @category tonal
 * @tags vibratoMod, vibmod, vibrato depth, pitch modulation
 */
@SprudelDsl
fun SprudelPattern.vibratoMod(depth: PatternLike? = null): SprudelPattern =
    this._vibratoMod(listOfNotNull(depth).asSprudelDslArgs())

/** Sets the vibrato depth on a string pattern. */
@SprudelDsl
fun String.vibratoMod(depth: PatternLike? = null): SprudelPattern =
    this._vibratoMod(listOfNotNull(depth).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the vibrato depth in semitones.
 * When called with no argument, reinterprets the current event value as a vibrato depth.
 *
 * ```KlangScript
 * note("c4").apply(vibratoMod(0.5))   // mapper form
 * ```
 *
 * @alias vibmod
 * @category tonal
 * @tags vibratoMod, vibmod, vibrato depth, pitch modulation
 */
@SprudelDsl
fun vibratoMod(depth: PatternLike? = null): PatternMapperFn = _vibratoMod(listOfNotNull(depth).asSprudelDslArgs())

/** Chains a vibratoMod operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.vibratoMod(depth: PatternLike? = null): PatternMapperFn =
    this._vibratoMod(listOfNotNull(depth).asSprudelDslArgs())

/** Alias for [vibratoMod] on this pattern. Sets the vibrato depth. */
@SprudelDsl
fun SprudelPattern.vibmod(depth: PatternLike? = null): SprudelPattern =
    this._vibmod(listOfNotNull(depth).asSprudelDslArgs())

/** Alias for [vibratoMod] on a string pattern. */
@SprudelDsl
fun String.vibmod(depth: PatternLike? = null): SprudelPattern =
    this._vibmod(listOfNotNull(depth).asSprudelDslArgs())

/**
 * Alias for [vibratoMod]. Returns a [PatternMapperFn] that sets the vibrato depth.
 *
 * ```KlangScript
 * note("c4").apply(vibmod(0.5))   // mapper form
 * ```
 *
 * @alias vibratoMod
 * @category tonal
 * @tags vibratoMod, vibmod, vibrato depth, pitch modulation
 */
@SprudelDsl
fun vibmod(depth: PatternLike? = null): PatternMapperFn = _vibmod(listOfNotNull(depth).asSprudelDslArgs())

/** Chains a vibmod operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.vibmod(depth: PatternLike? = null): PatternMapperFn =
    this._vibmod(listOfNotNull(depth).asSprudelDslArgs())

// -- pattack() / patt() -----------------------------------------------------------------------------------------------

fun applyPAttack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args) { seconds -> copy(pAttack = seconds) }
}

internal val SprudelPattern._pattack by dslPatternExtension { p, args, /* callInfo */ _ -> applyPAttack(p, args) }
internal val String._pattack by dslStringExtension { p, args, callInfo -> p._pattack(args, callInfo) }
internal val _pattack by dslPatternMapper { args, callInfo -> { p -> p._pattack(args, callInfo) } }
internal val PatternMapperFn._pattack by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_pattack(args, callInfo))
}

internal val SprudelPattern._patt by dslPatternExtension { p, args, callInfo -> p._pattack(args, callInfo) }
internal val String._patt by dslStringExtension { p, args, callInfo -> p._pattack(args, callInfo) }
internal val _patt by dslPatternMapper { args, callInfo -> { p -> p._pattack(args, callInfo) } }
internal val PatternMapperFn._patt by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_pattack(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the pitch envelope attack time in seconds.
 *
 * The pitch envelope shapes how the pitch changes over a note's duration. The attack
 * phase determines how quickly the pitch rises from its anchor to the target pitch.
 * Use with [penv], [pdecay], [prelease], [pcurve], and [panchor].
 * When called with no argument, reinterprets the current event value as an attack time.
 *
 * ```KlangScript
 * note("c4 e4").pattack(0.1).penv(12)   // pitch rises over 100 ms by 12 semitones
 * ```
 *
 * ```KlangScript
 * note("c4").pattack("<0.01 0.5>")       // fast vs slow pitch attack per cycle
 * ```
 *
 * @alias patt
 * @category tonal
 * @tags pattack, patt, pitch envelope, attack, envelope
 */
@SprudelDsl
fun SprudelPattern.pattack(seconds: PatternLike? = null): SprudelPattern =
    this._pattack(listOfNotNull(seconds).asSprudelDslArgs())

/** Sets the pitch envelope attack time on a string pattern. */
@SprudelDsl
fun String.pattack(seconds: PatternLike? = null): SprudelPattern =
    this._pattack(listOfNotNull(seconds).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the pitch envelope attack time.
 *
 * ```KlangScript
 * note("c4").apply(pattack(0.1))   // mapper form
 * ```
 *
 * @alias patt
 * @category tonal
 * @tags pattack, patt, pitch envelope, attack, envelope
 */
@SprudelDsl
fun pattack(seconds: PatternLike? = null): PatternMapperFn = _pattack(listOfNotNull(seconds).asSprudelDslArgs())

/** Chains a pattack operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.pattack(seconds: PatternLike? = null): PatternMapperFn =
    this._pattack(listOfNotNull(seconds).asSprudelDslArgs())

/** Alias for [pattack] on this pattern. */
@SprudelDsl
fun SprudelPattern.patt(seconds: PatternLike? = null): SprudelPattern =
    this._patt(listOfNotNull(seconds).asSprudelDslArgs())

/** Alias for [pattack] on a string pattern. */
@SprudelDsl
fun String.patt(seconds: PatternLike? = null): SprudelPattern =
    this._patt(listOfNotNull(seconds).asSprudelDslArgs())

/** Alias for [pattack]. Returns a [PatternMapperFn] that sets the pitch envelope attack time. */
@SprudelDsl
fun patt(seconds: PatternLike? = null): PatternMapperFn = _patt(listOfNotNull(seconds).asSprudelDslArgs())

/** Chains a patt operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.patt(seconds: PatternLike? = null): PatternMapperFn =
    this._patt(listOfNotNull(seconds).asSprudelDslArgs())

// -- pdecay() / pdec() ------------------------------------------------------------------------------------------------

fun applyPDecay(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args) { seconds -> copy(pDecay = seconds) }
}

internal val SprudelPattern._pdecay by dslPatternExtension { p, args, /* callInfo */ _ -> applyPDecay(p, args) }
internal val String._pdecay by dslStringExtension { p, args, callInfo -> p._pdecay(args, callInfo) }
internal val _pdecay by dslPatternMapper { args, callInfo -> { p -> p._pdecay(args, callInfo) } }
internal val PatternMapperFn._pdecay by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_pdecay(args, callInfo))
}

internal val SprudelPattern._pdec by dslPatternExtension { p, args, callInfo -> p._pdecay(args, callInfo) }
internal val String._pdec by dslStringExtension { p, args, callInfo -> p._pdecay(args, callInfo) }
internal val _pdec by dslPatternMapper { args, callInfo -> { p -> p._pdecay(args, callInfo) } }
internal val PatternMapperFn._pdec by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_pdecay(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the pitch envelope decay time in seconds.
 *
 * After the attack phase, the pitch envelope decays towards the sustain level. The decay
 * time determines how quickly this transition happens.
 * Use with [pattack], [penv], [prelease], [pcurve], and [panchor].
 * When called with no argument, reinterprets the current event value as a decay time.
 *
 * ```KlangScript
 * note("c4 e4").pdecay(0.2).penv(12)   // pitch decays over 200 ms
 * ```
 *
 * ```KlangScript
 * note("c4").pdecay("<0.05 0.5>")       // short vs long decay per cycle
 * ```
 *
 * @alias pdec
 * @category tonal
 * @tags pdecay, pdec, pitch envelope, decay, envelope
 */
@SprudelDsl
fun SprudelPattern.pdecay(seconds: PatternLike? = null): SprudelPattern =
    this._pdecay(listOfNotNull(seconds).asSprudelDslArgs())

/** Sets the pitch envelope decay time on a string pattern. */
@SprudelDsl
fun String.pdecay(seconds: PatternLike? = null): SprudelPattern =
    this._pdecay(listOfNotNull(seconds).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the pitch envelope decay time.
 *
 * ```KlangScript
 * note("c4").apply(pdecay(0.2))   // mapper form
 * ```
 *
 * @alias pdec
 * @category tonal
 * @tags pdecay, pdec, pitch envelope, decay, envelope
 */
@SprudelDsl
fun pdecay(seconds: PatternLike? = null): PatternMapperFn = _pdecay(listOfNotNull(seconds).asSprudelDslArgs())

/** Chains a pdecay operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.pdecay(seconds: PatternLike? = null): PatternMapperFn =
    this._pdecay(listOfNotNull(seconds).asSprudelDslArgs())

/** Alias for [pdecay] on this pattern. */
@SprudelDsl
fun SprudelPattern.pdec(seconds: PatternLike? = null): SprudelPattern =
    this._pdec(listOfNotNull(seconds).asSprudelDslArgs())

/** Alias for [pdecay] on a string pattern. */
@SprudelDsl
fun String.pdec(seconds: PatternLike? = null): SprudelPattern =
    this._pdec(listOfNotNull(seconds).asSprudelDslArgs())

/** Alias for [pdecay]. Returns a [PatternMapperFn] that sets the pitch envelope decay time. */
@SprudelDsl
fun pdec(seconds: PatternLike? = null): PatternMapperFn = _pdec(listOfNotNull(seconds).asSprudelDslArgs())

/** Chains a pdec operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.pdec(seconds: PatternLike? = null): PatternMapperFn =
    this._pdec(listOfNotNull(seconds).asSprudelDslArgs())

// -- prelease() / prel() ----------------------------------------------------------------------------------------------

fun applyPRelease(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args) { seconds -> copy(pRelease = seconds) }
}

internal val SprudelPattern._prelease by dslPatternExtension { p, args, /* callInfo */ _ -> applyPRelease(p, args) }
internal val String._prelease by dslStringExtension { p, args, callInfo -> p._prelease(args, callInfo) }
internal val _prelease by dslPatternMapper { args, callInfo -> { p -> p._prelease(args, callInfo) } }
internal val PatternMapperFn._prelease by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_prelease(args, callInfo))
}

internal val SprudelPattern._prel by dslPatternExtension { p, args, callInfo -> p._prelease(args, callInfo) }
internal val String._prel by dslStringExtension { p, args, callInfo -> p._prelease(args, callInfo) }
internal val _prel by dslPatternMapper { args, callInfo -> { p -> p._prelease(args, callInfo) } }
internal val PatternMapperFn._prel by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_prelease(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the pitch envelope release time in seconds.
 *
 * The release phase determines how quickly the pitch envelope returns to its resting state
 * after the note ends. Use with [pattack], [pdecay], [penv], [pcurve], and [panchor].
 * When called with no argument, reinterprets the current event value as a release time.
 *
 * ```KlangScript
 * note("c4 e4").prelease(0.3).penv(12)  // pitch releases over 300 ms
 * ```
 *
 * ```KlangScript
 * note("c4").prelease("<0.1 1.0>")       // short vs long release per cycle
 * ```
 *
 * @alias prel
 * @category tonal
 * @tags prelease, prel, pitch envelope, release, envelope
 */
@SprudelDsl
fun SprudelPattern.prelease(seconds: PatternLike? = null): SprudelPattern =
    this._prelease(listOfNotNull(seconds).asSprudelDslArgs())

/** Sets the pitch envelope release time on a string pattern. */
@SprudelDsl
fun String.prelease(seconds: PatternLike? = null): SprudelPattern =
    this._prelease(listOfNotNull(seconds).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the pitch envelope release time.
 *
 * ```KlangScript
 * note("c4").apply(prelease(0.3))   // mapper form
 * ```
 *
 * @alias prel
 * @category tonal
 * @tags prelease, prel, pitch envelope, release, envelope
 */
@SprudelDsl
fun prelease(seconds: PatternLike? = null): PatternMapperFn = _prelease(listOfNotNull(seconds).asSprudelDslArgs())

/** Chains a prelease operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.prelease(seconds: PatternLike? = null): PatternMapperFn =
    this._prelease(listOfNotNull(seconds).asSprudelDslArgs())

/** Alias for [prelease] on this pattern. */
@SprudelDsl
fun SprudelPattern.prel(seconds: PatternLike? = null): SprudelPattern =
    this._prel(listOfNotNull(seconds).asSprudelDslArgs())

/** Alias for [prelease] on a string pattern. */
@SprudelDsl
fun String.prel(seconds: PatternLike? = null): SprudelPattern =
    this._prel(listOfNotNull(seconds).asSprudelDslArgs())

/** Alias for [prelease]. Returns a [PatternMapperFn] that sets the pitch envelope release time. */
@SprudelDsl
fun prel(seconds: PatternLike? = null): PatternMapperFn = _prel(listOfNotNull(seconds).asSprudelDslArgs())

/** Chains a prel operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.prel(seconds: PatternLike? = null): PatternMapperFn =
    this._prel(listOfNotNull(seconds).asSprudelDslArgs())

// -- penv() / pamt() --------------------------------------------------------------------------------------------------

fun applyPEnv(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args) { semitones -> copy(pEnv = semitones) }
}

internal val SprudelPattern._penv by dslPatternExtension { p, args, /* callInfo */ _ -> applyPEnv(p, args) }
internal val String._penv by dslStringExtension { p, args, callInfo -> p._penv(args, callInfo) }
internal val _penv by dslPatternMapper { args, callInfo -> { p -> p._penv(args, callInfo) } }
internal val PatternMapperFn._penv by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_penv(args, callInfo))
}

internal val SprudelPattern._pamt by dslPatternExtension { p, args, callInfo -> p._penv(args, callInfo) }
internal val String._pamt by dslStringExtension { p, args, callInfo -> p._penv(args, callInfo) }
internal val _pamt by dslPatternMapper { args, callInfo -> { p -> p._penv(args, callInfo) } }
internal val PatternMapperFn._pamt by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_penv(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the pitch envelope depth (amount) in semitones.
 *
 * Determines how far the pitch deviates from the base note during the envelope cycle.
 * Positive values raise the pitch; negative values lower it.
 * Use with [pattack], [pdecay], [prelease], [pcurve], and [panchor].
 * When called with no argument, reinterprets the current event value as an envelope depth.
 *
 * ```KlangScript
 * note("c4").penv(12).pattack(0.1)   // 1-octave pitch rise over 100 ms
 * ```
 *
 * ```KlangScript
 * note("c4").penv(-7).pdecay(0.2)    // pitch falls a perfect fifth then decays
 * ```
 *
 * @alias pamt
 * @category tonal
 * @tags penv, pamt, pitch envelope, depth, semitones, envelope
 */
@SprudelDsl
fun SprudelPattern.penv(semitones: PatternLike? = null): SprudelPattern =
    this._penv(listOfNotNull(semitones).asSprudelDslArgs())

/** Sets the pitch envelope depth (in semitones) on a string pattern. */
@SprudelDsl
fun String.penv(semitones: PatternLike? = null): SprudelPattern =
    this._penv(listOfNotNull(semitones).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the pitch envelope depth in semitones.
 *
 * ```KlangScript
 * note("c4").apply(penv(12))   // mapper form
 * ```
 *
 * @alias pamt
 * @category tonal
 * @tags penv, pamt, pitch envelope, depth, semitones, envelope
 */
@SprudelDsl
fun penv(semitones: PatternLike? = null): PatternMapperFn = _penv(listOfNotNull(semitones).asSprudelDslArgs())

/** Chains a penv operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.penv(semitones: PatternLike? = null): PatternMapperFn =
    this._penv(listOfNotNull(semitones).asSprudelDslArgs())

/** Alias for [penv] on this pattern. */
@SprudelDsl
fun SprudelPattern.pamt(semitones: PatternLike? = null): SprudelPattern =
    this._pamt(listOfNotNull(semitones).asSprudelDslArgs())

/** Alias for [penv] on a string pattern. */
@SprudelDsl
fun String.pamt(semitones: PatternLike? = null): SprudelPattern =
    this._pamt(listOfNotNull(semitones).asSprudelDslArgs())

/** Alias for [penv]. Returns a [PatternMapperFn] that sets the pitch envelope depth. */
@SprudelDsl
fun pamt(semitones: PatternLike? = null): PatternMapperFn = _pamt(listOfNotNull(semitones).asSprudelDslArgs())

/** Chains a pamt operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.pamt(semitones: PatternLike? = null): PatternMapperFn =
    this._pamt(listOfNotNull(semitones).asSprudelDslArgs())

// -- pcurve() / pcrv() ------------------------------------------------------------------------------------------------

fun applyPCurve(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args) { curve -> copy(pCurve = curve) }
}

internal val SprudelPattern._pcurve by dslPatternExtension { p, args, /* callInfo */ _ -> applyPCurve(p, args) }
internal val String._pcurve by dslStringExtension { p, args, callInfo -> p._pcurve(args, callInfo) }
internal val _pcurve by dslPatternMapper { args, callInfo -> { p -> p._pcurve(args, callInfo) } }
internal val PatternMapperFn._pcurve by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_pcurve(args, callInfo))
}

internal val SprudelPattern._pcrv by dslPatternExtension { p, args, callInfo -> p._pcurve(args, callInfo) }
internal val String._pcrv by dslStringExtension { p, args, callInfo -> p._pcurve(args, callInfo) }
internal val _pcrv by dslPatternMapper { args, callInfo -> { p -> p._pcurve(args, callInfo) } }
internal val PatternMapperFn._pcrv by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_pcurve(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the pitch envelope curve shape.
 *
 * Controls the curvature of the pitch envelope segments. A value of `0` gives a linear
 * curve; positive values create logarithmic curves; negative values create exponential ones.
 * When called with no argument, reinterprets the current event value as a curve shape.
 *
 * ```KlangScript
 * note("c4").pcurve(2).penv(12).pattack(0.2)   // logarithmic pitch rise
 * ```
 *
 * ```KlangScript
 * note("c4").pcurve(-2).penv(12).pattack(0.2)  // exponential pitch rise
 * ```
 *
 * @alias pcrv
 * @category tonal
 * @tags pcurve, pcrv, pitch envelope, curve, shape, envelope
 */
@SprudelDsl
fun SprudelPattern.pcurve(curve: PatternLike? = null): SprudelPattern =
    this._pcurve(listOfNotNull(curve).asSprudelDslArgs())

/** Sets the pitch envelope curve shape on a string pattern. */
@SprudelDsl
fun String.pcurve(curve: PatternLike? = null): SprudelPattern =
    this._pcurve(listOfNotNull(curve).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the pitch envelope curve shape.
 *
 * ```KlangScript
 * note("c4").apply(pcurve(2))   // mapper form
 * ```
 *
 * @alias pcrv
 * @category tonal
 * @tags pcurve, pcrv, pitch envelope, curve, shape, envelope
 */
@SprudelDsl
fun pcurve(curve: PatternLike? = null): PatternMapperFn = _pcurve(listOfNotNull(curve).asSprudelDslArgs())

/** Chains a pcurve operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.pcurve(curve: PatternLike? = null): PatternMapperFn =
    this._pcurve(listOfNotNull(curve).asSprudelDslArgs())

/** Alias for [pcurve] on this pattern. */
@SprudelDsl
fun SprudelPattern.pcrv(curve: PatternLike? = null): SprudelPattern =
    this._pcrv(listOfNotNull(curve).asSprudelDslArgs())

/** Alias for [pcurve] on a string pattern. */
@SprudelDsl
fun String.pcrv(curve: PatternLike? = null): SprudelPattern =
    this._pcrv(listOfNotNull(curve).asSprudelDslArgs())

/** Alias for [pcurve]. Returns a [PatternMapperFn] that sets the pitch envelope curve shape. */
@SprudelDsl
fun pcrv(curve: PatternLike? = null): PatternMapperFn = _pcrv(listOfNotNull(curve).asSprudelDslArgs())

/** Chains a pcrv operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.pcrv(curve: PatternLike? = null): PatternMapperFn =
    this._pcrv(listOfNotNull(curve).asSprudelDslArgs())

// -- panchor() / panc() -----------------------------------------------------------------------------------------------

fun applyPAnchor(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args) { anchor -> copy(pAnchor = anchor) }
}

internal val SprudelPattern._panchor by dslPatternExtension { p, args, /* callInfo */ _ -> applyPAnchor(p, args) }
internal val String._panchor by dslStringExtension { p, args, callInfo -> p._panchor(args, callInfo) }
internal val _panchor by dslPatternMapper { args, callInfo -> { p -> p._panchor(args, callInfo) } }
internal val PatternMapperFn._panchor by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_panchor(args, callInfo))
}

internal val SprudelPattern._panc by dslPatternExtension { p, args, callInfo -> p._panchor(args, callInfo) }
internal val String._panc by dslStringExtension { p, args, callInfo -> p._panchor(args, callInfo) }
internal val _panc by dslPatternMapper { args, callInfo -> { p -> p._panchor(args, callInfo) } }
internal val PatternMapperFn._panc by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_panchor(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the pitch envelope anchor point.
 *
 * The anchor determines the relative position within the note duration where the pitch
 * envelope reaches its peak (or trough). `0` anchors at the start; `1` at the end.
 * When called with no argument, reinterprets the current event value as an anchor point.
 *
 * ```KlangScript
 * note("c4").panchor(0).penv(12)   // pitch peaks at note start
 * ```
 *
 * ```KlangScript
 * note("c4").panchor(1).penv(12)   // pitch peaks at note end
 * ```
 *
 * @alias panc
 * @category tonal
 * @tags panchor, panc, pitch envelope, anchor, envelope
 */
@SprudelDsl
fun SprudelPattern.panchor(anchor: PatternLike? = null): SprudelPattern =
    this._panchor(listOfNotNull(anchor).asSprudelDslArgs())

/** Sets the pitch envelope anchor point on a string pattern. */
@SprudelDsl
fun String.panchor(anchor: PatternLike? = null): SprudelPattern =
    this._panchor(listOfNotNull(anchor).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the pitch envelope anchor point.
 *
 * ```KlangScript
 * note("c4").apply(panchor(0))   // mapper form
 * ```
 *
 * @alias panc
 * @category tonal
 * @tags panchor, panc, pitch envelope, anchor, envelope
 */
@SprudelDsl
fun panchor(anchor: PatternLike? = null): PatternMapperFn = _panchor(listOfNotNull(anchor).asSprudelDslArgs())

/** Chains a panchor operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.panchor(anchor: PatternLike? = null): PatternMapperFn =
    this._panchor(listOfNotNull(anchor).asSprudelDslArgs())

/** Alias for [panchor] on this pattern. */
@SprudelDsl
fun SprudelPattern.panc(anchor: PatternLike? = null): SprudelPattern =
    this._panc(listOfNotNull(anchor).asSprudelDslArgs())

/** Alias for [panchor] on a string pattern. */
@SprudelDsl
fun String.panc(anchor: PatternLike? = null): SprudelPattern =
    this._panc(listOfNotNull(anchor).asSprudelDslArgs())

/** Alias for [panchor]. Returns a [PatternMapperFn] that sets the pitch envelope anchor point. */
@SprudelDsl
fun panc(anchor: PatternLike? = null): PatternMapperFn = _panc(listOfNotNull(anchor).asSprudelDslArgs())

/** Chains a panc operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.panc(anchor: PatternLike? = null): PatternMapperFn =
    this._panc(listOfNotNull(anchor).asSprudelDslArgs())

// -- accelerate() -----------------------------------------------------------------------------------------------------

fun applyAccelerate(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args) { amount ->
        copy(accelerate = amount)
    }
}

internal val SprudelPattern._accelerate by dslPatternExtension { p, args, /* callInfo */ _ -> applyAccelerate(p, args) }
internal val String._accelerate by dslStringExtension { p, args, callInfo -> p._accelerate(args, callInfo) }
internal val _accelerate by dslPatternMapper { args, callInfo -> { p -> p._accelerate(args, callInfo) } }
internal val PatternMapperFn._accelerate by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_accelerate(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the playback acceleration (pitch ramp) for each event.
 *
 * Controls a continuous pitch change during sample playback. Positive values pitch up over
 * the event's duration; negative values pitch down. Useful for creating pitched percussion
 * or sweep effects. When called with no argument, reinterprets the current event value as
 * an acceleration amount.
 *
 * ```KlangScript
 * s("cr").accelerate(2)              // crash pitches up during playback
 * ```
 *
 * ```KlangScript
 * s("hh").accelerate("<0 -2 2>")     // alternate: no ramp, down, up per cycle
 * ```
 *
 * @category tonal
 * @tags accelerate, pitch ramp, pitch bend, playback speed
 */
@SprudelDsl
fun SprudelPattern.accelerate(amount: PatternLike? = null): SprudelPattern =
    this._accelerate(listOfNotNull(amount).asSprudelDslArgs())

/** Sets the playback acceleration on a string pattern. */
@SprudelDsl
fun String.accelerate(amount: PatternLike? = null): SprudelPattern =
    this._accelerate(listOfNotNull(amount).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the playback acceleration (pitch ramp).
 * When called with no argument, reinterprets the current event value as an acceleration amount.
 *
 * ```KlangScript
 * s("hh").apply(accelerate(2))   // mapper form
 * ```
 *
 * @category tonal
 * @tags accelerate, pitch ramp, pitch bend, playback speed
 */
@SprudelDsl
fun accelerate(amount: PatternLike? = null): PatternMapperFn = _accelerate(listOfNotNull(amount).asSprudelDslArgs())

/** Chains an accelerate operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.accelerate(amount: PatternLike? = null): PatternMapperFn =
    this._accelerate(listOfNotNull(amount).asSprudelDslArgs())

// -- transpose() ------------------------------------------------------------------------------------------------------

/**
 * Applies transposition logic to a StrudelVoiceData instance.
 * Accepts either a numeric value (semitones) or a string (interval name).
 */
fun SprudelVoiceData.transpose(amount: Any?): SprudelVoiceData {
    val semitones: Int
    val intervalName: String

    when (amount) {
        is Rational -> {
            semitones = amount.toInt()
            intervalName = ""
        }

        is Number -> {
            semitones = amount.toInt()
            intervalName = ""
        }

        is String -> {
            // Try to parse as number first
            val d = amount.toDoubleOrNull()
            if (d != null) {
                semitones = d.toInt()
                intervalName = ""
            } else {
                semitones = 0
                intervalName = amount
            }
        }

        is SprudelVoiceValue -> return transpose(amount.asDouble ?: amount.asString)

        else -> return this
    }

//    if (semitones == 0 && intervalName.isEmpty()) return this

    val currentNoteName = note ?: value?.asString ?: ""

    // Strategy 1: Interval arithmetic (Music Theory)
    // We prioritize this to preserve enharmonic correctness (e.g. C3 + 7 semitones -> G3)
    if (currentNoteName.isNotEmpty()) {
        val interval = intervalName.ifEmpty { Interval.fromSemitones(semitones) }
        // Use Distance.transpose(String, String) directly to avoid import/type mismatch issues
        val newNoteName = Distance.transpose(currentNoteName, interval)

        if (newNoteName.isNotEmpty()) {
            return copy(
                note = newNoteName,
                freqHz = Tones.noteToFreq(newNoteName),
                value = null, // clear the value ... it was consumed
            )
        }
    }

    // Strategy 2: Frequency shifting (Physics)
    // Fallback if we only have frequency or an invalid note name.
    val currentFreq = freqHz ?: Tones.noteToFreq(currentNoteName)
    if (currentFreq <= 0.0) {
        return this.copy(
            value = null, // clear the value ... it was consumed
        )
    }

    val effectiveSemitones = if (intervalName.isNotEmpty()) {
        val i = Interval.get(intervalName)
        if (!i.empty) i.semitones else 0
    } else {
        semitones
    }

    val newFreq = currentFreq * 2.0.pow(effectiveSemitones.toDouble() / 12.0)

    // Best effort to name the note from the new frequency
    val newMidi = Midi.freqToMidi(newFreq)
    val newNote = Midi.midiToNoteName(newMidi, sharps = true)

    return copy(
        note = newNote,
        freqHz = newFreq,
        value = null, // clear the value ... it was consumed
    )
}

fun applyTranspose(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    // We use defaultModifier for args because we just want the 'value'
    val controlPattern = args.toPattern(voiceValueModifier)

    return ControlPattern(
        source = source,
        control = controlPattern,
        mapper = { it }, // No mapping needed
        combiner = { srcData, ctrlData ->
            // Extract the raw value from control data
            // This can be a Number (semitones), String (interval like "1P"), or VoiceValue
            val amount = ctrlData.value ?: return@ControlPattern srcData

            // Apply transpose with the raw amount
            srcData.transpose(amount)
        }
    )
}

internal val SprudelPattern._transpose by dslPatternExtension { p, args, /* callInfo */ _ -> applyTranspose(p, args) }

internal val String._transpose by dslStringExtension { p, args, callInfo -> p._transpose(args, callInfo) }

internal val _transpose by dslPatternMapper { args, callInfo -> { p -> p._transpose(args, callInfo) } }

internal val PatternMapperFn._transpose by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_transpose(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Transposes a pattern by a number of semitones or an interval name.
 *
 * Shifts all note pitches by the given amount. Numeric arguments are treated as semitones;
 * string arguments can be interval names (e.g. `"P5"` for a perfect fifth).
 *
 * ```KlangScript
 * note("c4 e4 g4").transpose(7)       // transpose up a perfect fifth
 * ```
 *
 * ```KlangScript
 * note("c4 e4").transpose("<0 12>")   // alternate: no transpose vs octave up per cycle
 * ```
 *
 * @param amount The amount to transpose by, either as a number of semitones or an interval name.
 *
 * @category tonal
 * @tags transpose, pitch shift, semitones, interval, pitch
 */
@SprudelDsl
fun SprudelPattern.transpose(amount: PatternLike): SprudelPattern =
    this._transpose(listOf(amount).asSprudelDslArgs())

/** Transposes a string pattern by a number of semitones or interval name. */
@SprudelDsl
fun String.transpose(amount: PatternLike): SprudelPattern = this._transpose(listOf(amount).asSprudelDslArgs())

/** Returns a [PatternMapperFn] that transposes each event by the given semitones or interval name. */
@SprudelDsl
fun transpose(amount: PatternLike): PatternMapperFn = _transpose(listOf(amount).asSprudelDslArgs())

/** Chains a transpose step onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.transpose(amount: PatternLike): PatternMapperFn =
    this._transpose(listOf(amount).asSprudelDslArgs())

// -- freq() -----------------------------------------------------------------------------------------------------------

fun applyFreq(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args) { v -> copy(freqHz = v) }
}

internal val SprudelPattern._freq by dslPatternExtension { p, args, _ -> applyFreq(p, args) }

internal val String._freq by dslStringExtension { p, args, callInfo -> p._freq(args, callInfo) }

internal val _freq by dslPatternMapper { args, callInfo -> { p -> p._freq(args, callInfo) } }

internal val PatternMapperFn._freq by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_freq(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the playback frequency in Hz directly, bypassing note name resolution.
 *
 * Overrides the computed frequency for each event. Useful for precise tuning or
 * microtonal work where standard note names are insufficient. When called with no argument,
 * reinterprets the current event value as a frequency in Hz.
 *
 * ```KlangScript
 * freq("440 550 660")          // A4, roughly C#5, roughly E5 by raw Hz
 * ```
 *
 * ```KlangScript
 * note("c4 e4").freq(432)      // force all events to 432 Hz
 * ```
 *
 * @category tonal
 * @tags freq, frequency, Hz, pitch, tuning
 */
@SprudelDsl
fun freq(hz: PatternLike? = null): PatternMapperFn = _freq(listOfNotNull(hz).asSprudelDslArgs())

/** Sets the playback frequency in Hz on this pattern. */
@SprudelDsl
fun SprudelPattern.freq(hz: PatternLike? = null): SprudelPattern =
    this._freq(listOfNotNull(hz).asSprudelDslArgs())

/** Sets the playback frequency in Hz on a string pattern. */
@SprudelDsl
fun String.freq(hz: PatternLike? = null): SprudelPattern = this._freq(listOfNotNull(hz).asSprudelDslArgs())

/** Chains a freq step onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.freq(hz: PatternLike? = null): PatternMapperFn =
    this._freq(listOfNotNull(hz).asSprudelDslArgs())

// -- scaleTranspose() -------------------------------------------------------------------------------------------------

/**
 * Transposes notes by a number of scale degrees within the active scale.
 * If no scale is set, falls back to chromatic transposition.
 */
fun SprudelVoiceData.scaleTranspose(steps: Int): SprudelVoiceData {
    val currentScale = scale?.cleanScaleName()

    // If no scale is set, fallback to chromatic transposition
    if (currentScale.isNullOrEmpty()) {
        return transpose(steps)
    }

    val currentNote = note ?: value?.asString ?: return this
    if (currentNote.isEmpty()) return this

    try {
        val scaleObj = Scale.get(currentScale)
        if (scaleObj.empty) return transpose(steps)

        val scaleNotes = scaleObj.notes
        if (scaleNotes.isEmpty()) return transpose(steps)

        // Find current note in scale (ignoring octave for matching)
        val currentNoteObj = Note.get(currentNote)
        val currentChroma = currentNoteObj.chroma

        // Find the scale degree of the current note
        val currentDegree = scaleNotes.indexOfFirst { scaleDegreeNote ->
            Note.get(scaleDegreeNote).chroma == currentChroma
        }

        if (currentDegree < 0) {
            // Note not in scale, fallback to chromatic
            return transpose(steps)
        }

        if (steps == 0) {
            return this
        }

        // Calculate new degree with octave wrapping
        val scaleSize = scaleNotes.size
        val newDegree = (currentDegree + steps).mod(scaleSize)
        val octaveShift = (currentDegree + steps).floorDiv(scaleSize)

        // Get base note from scale
        val baseNewNote = scaleNotes[newDegree]

        // Apply octave shift
        val newNoteObj = Note.get(baseNewNote)
        val currentOctave = currentNoteObj.oct ?: newNoteObj.oct ?: 3
        val newOctave = currentOctave + octaveShift

        // Construct final note with octave
        val finalNote = newNoteObj.pc + newOctave.toString()

        return copy(
            note = finalNote,
            freqHz = Tones.noteToFreq(finalNote),
        )
    } catch (_: Exception) {
        // On any error, fallback to chromatic transposition
        return transpose(steps)
    }
}

fun applyScaleTranspose(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    // Ensure we parse the argument as mini-notation if it's a string, to get a pattern of values
    // This allows scaleTranspose("0 1 2") to work as a control pattern
    val controlPattern = args.toPattern(voiceValueModifier)

    return ControlPattern(
        source = source,
        control = controlPattern,
        mapper = { it },
        combiner = { srcData, ctrlData ->
            // If control pattern has no value, assume 0 steps (identity)
            val steps = ctrlData.value?.asInt ?: 0
            srcData.scaleTranspose(steps)
        }
    )
}

internal val SprudelPattern._scaleTranspose by dslPatternExtension { p, args, _ -> applyScaleTranspose(p, args) }

internal val String._scaleTranspose by dslStringExtension { p, args, callInfo -> p._scaleTranspose(args, callInfo) }

internal val _scaleTranspose by dslPatternMapper { args, callInfo -> { p -> p._scaleTranspose(args, callInfo) } }

internal val PatternMapperFn._scaleTranspose by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_scaleTranspose(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Transposes notes by a number of scale degrees within the active [scale].
 *
 * Unlike [transpose] which shifts by semitones, `scaleTranspose` steps through the notes of
 * the current scale context. Falls back to chromatic (semitone) transposition when no scale
 * is active.
 *
 * ```KlangScript
 * n("0 2 4").scale("c4:major").note().scaleTranspose(1)  // shift up 1 scale degree
 * ```
 *
 * ```KlangScript
 * note("c4 e4 g4").scale("c4:major").scaleTranspose(-2)  // shift down 2 scale degrees
 * ```
 *
 * @category tonal
 * @tags scaleTranspose, scale degrees, pitch, transpose
 */
@SprudelDsl
fun SprudelPattern.scaleTranspose(steps: PatternLike): SprudelPattern =
    this._scaleTranspose(listOf(steps).asSprudelDslArgs())

/** Transposes a string pattern by a number of scale degrees within the active scale. */
@SprudelDsl
fun String.scaleTranspose(steps: PatternLike): SprudelPattern = this._scaleTranspose(listOf(steps).asSprudelDslArgs())

/** Returns a [PatternMapperFn] that transposes each event by the given number of scale degrees. */
@SprudelDsl
fun scaleTranspose(steps: PatternLike): PatternMapperFn = _scaleTranspose(listOf(steps).asSprudelDslArgs())

/** Chains a scaleTranspose step onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.scaleTranspose(steps: PatternLike): PatternMapperFn =
    this._scaleTranspose(listOf(steps).asSprudelDslArgs())

// -- chord() ----------------------------------------------------------------------------------------------------------

private val chordMutation = voiceModifier { chordName ->
    val name = chordName?.toString() ?: return@voiceModifier this

    // Set the chord property
    // We also set the note to the chord root/tonic to ensure it plays something meaningful if voicing is not used
    // and to provide a base for rootNotes()
    val chordObj = Chord.get(name)
    val root = if (!chordObj.empty) chordObj.tonic ?: chordObj.root else null

    if (root != null) {
        copy(chord = name, note = root, freqHz = Tones.noteToFreq(root))
    } else {
        copy(chord = name)
    }
}

// REMOVED expandChordToVoiceData and applyChord with BindPattern
// Instead, chord() is now a simple property setter pattern

internal val _chord by dslPatternFunction { args, _ ->
    args.toPattern(chordMutation)
}

internal val SprudelPattern._chord by dslPatternExtension { p, args, _ ->
    p._applyControlFromParams(args, chordMutation) { src, ctrl ->
        src.chordMutation(ctrl.chord ?: ctrl.value?.asString)
    }
}

internal val String._chord by dslStringExtension { p, args, callInfo -> p._chord(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the chord name for each event, establishing harmonic context for [voicing].
 *
 * Chord names follow the format `"root quality"` (e.g. `"C"`, `"Am"`, `"Gmaj7"`).
 * The chord root is also set as the event's note. Use [voicing] to expand into voiced
 * notes, or [rootNotes] to extract just the bass note.
 *
 * ```KlangScript
 * chord("<C Am F G>").voicing()         // voiced I-vi-IV-V
 * ```
 *
 * ```KlangScript
 * chord("<Cmaj7 Am7>").voicing()        // jazzy chord alternation per cycle
 * ```
 *
 * @category tonal
 * @tags chord, harmony, chords, voicing, progression
 */
@SprudelDsl
fun chord(name: PatternLike): SprudelPattern = _chord(listOf(name).asSprudelDslArgs())

/** Sets the chord name on this pattern for use with [voicing] and [rootNotes]. */
@SprudelDsl
fun SprudelPattern.chord(name: PatternLike): SprudelPattern = this._chord(listOf(name).asSprudelDslArgs())

/** Sets the chord name on a string pattern. */
@SprudelDsl
fun String.chord(name: PatternLike): SprudelPattern = this._chord(listOf(name).asSprudelDslArgs())

// -- rootNotes() ------------------------------------------------------------------------------------------------------

/**
 * Extracts the root note from a chord pattern.
 * If octave is specified, forces the root to that octave.
 */
fun SprudelVoiceData.extractRootNote(octave: Int? = null): SprudelVoiceData {
    // With the new chord() implementation, 'note' is already set to root.
    // But we might want to force octave or handle cases where note was changed.

    val chordName = chord ?: return this

    try {
        val chordObj = Chord.get(chordName)
        if (chordObj.empty) return this

        val tonic = chordObj.tonic
        if (tonic.isNullOrEmpty()) return this

        val rootNote = if (octave != null) {
            // Force specific octave
            val rootPc = Note.get(tonic).pc
            rootPc + octave.toString()
        } else {
            // Use tonic as-is, or add default octave if missing
            val rootNoteObj = Note.get(tonic)
            if (rootNoteObj.oct != null) {
                tonic
            } else {
                rootNoteObj.pc + "4" // Default to octave 4
            }
        }

        return copy(
            note = rootNote,
            freqHz = Tones.noteToFreq(rootNote),
        )
    } catch (_: Exception) {
        return this
    }
}

fun applyRootNotes(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val octave = args.firstOrNull()?.value?.asIntOrNull()

    // No need for distinct() anymore since chord() doesn't expand
    return source.reinterpretVoice { voiceData ->
        voiceData.extractRootNote(octave)
    }
}

internal val SprudelPattern._rootNotes by dslPatternExtension { p, args, _ -> applyRootNotes(p, args) }

internal val String._rootNotes by dslStringExtension { p, args, callInfo -> p._rootNotes(args, callInfo) }

internal val _rootNotes by dslPatternMapper { args, callInfo -> { p -> p._rootNotes(args, callInfo) } }

internal val PatternMapperFn._rootNotes by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_rootNotes(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Extracts the root (bass) note from a chord pattern.
 *
 * Given a pattern with chord names set via [chord], `rootNotes` produces events carrying
 * only the chord root note. An optional integer argument forces the root to a specific octave.
 *
 * ```KlangScript
 * chord("C:major Am:minor F:major").rootNotes()   // root notes: C, A, F
 * ```
 *
 * ```KlangScript
 * chord("Cmaj7 Am7 Fmaj7").rootNotes(3)           // roots forced to octave 3
 * ```
 *
 * @category tonal
 * @tags rootNotes, chord root, bass, harmony
 */
@SprudelDsl
fun SprudelPattern.rootNotes(): SprudelPattern = this._rootNotes(emptyList())

/** Extracts root notes from chord events in this pattern, forcing to the given octave. */
@SprudelDsl
fun SprudelPattern.rootNotes(octave: PatternLike): SprudelPattern =
    this._rootNotes(listOf(octave).asSprudelDslArgs())

/** Extracts root notes from chord events in a string pattern. */
@SprudelDsl
fun String.rootNotes(): SprudelPattern = this._rootNotes(emptyList())

/** Extracts root notes from chord events in a string pattern, forcing to the given octave. */
@SprudelDsl
fun String.rootNotes(octave: PatternLike): SprudelPattern = this._rootNotes(listOf(octave).asSprudelDslArgs())

/** Returns a [PatternMapperFn] that extracts root notes from chord events. */
@SprudelDsl
fun rootNotes(): PatternMapperFn = _rootNotes(emptyList())

/** Returns a [PatternMapperFn] that extracts root notes from chord events, forcing to the given octave. */
@SprudelDsl
fun rootNotes(octave: PatternLike): PatternMapperFn = _rootNotes(listOf(octave).asSprudelDslArgs())

/** Chains a rootNotes step onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.rootNotes(): PatternMapperFn = this._rootNotes(emptyList())

/** Chains a rootNotes step (with forced octave) onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.rootNotes(octave: PatternLike): PatternMapperFn =
    this._rootNotes(listOf(octave).asSprudelDslArgs())

// -- voicing() --------------------------------------------------------------------------------------------------------

/**
 * Helper to get voiced notes for a chord.
 * Attempts to use voice leading, then falls back to intelligent chord structure preservation.
 */
internal fun getVoicedNotes(
    chordName: String,
    range: List<String>,
    lastVoicing: List<String>,
): List<String> {
    try {
        // 1. Try strict voice leading within range using the library
        // This handles cases where we want specific smooth transitions
        val voicing = io.peekandpoke.klang.tones.voicing.Voicing.get(
            chord = chordName,
            range = range,
            lastVoicing = lastVoicing
        )

        if (voicing.isNotEmpty()) {
            return voicing
        }

        // 2. Fallback: intelligent default voicing
        // If the library couldn't find a voicing (e.g. strict range or unknown chord shape in dictionary),
        // we construct the chord manually from its intervals.
        val chordObj = Chord.get(chordName)
        val tonic = chordObj.tonic

        if (!chordObj.empty && !tonic.isNullOrEmpty()) {
            // Chord.notes returns flat pitch classes (e.g. "C", "E"), which loses inversion info.
            // Also, we cannot reliably parse octaves from chord names (e.g. "C2" is C sus2, not C octave 2).
            // So we default to Octave 4 as the center.
            val root = tonic + "4"

            // By transposing intervals from "C4" (or whatever tonic is + 4), we get correct relative pitches
            // and preserve inversions defined in the Chord definition.
            return chordObj.intervals.map { interval ->
                Distance.transpose(root, interval)
            }
        }

        // 3. Last resort: raw notes forced to octave 4
        // This ensures that valid chords (where intervals might be missing for some reason) still play
        // in a hearable range, instead of defaulting to octave 0/1.
        if (chordObj.notes.isNotEmpty()) {
            return chordObj.notes.map { it + "4" }
        }

        return emptyList()
    } catch (_: Exception) {
        return emptyList()
    }
}

/**
 * Applies voice leading to chord patterns.
 * Uses the Tones library's Voicing module for smooth transitions between chords.
 */
fun applyVoicing(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    // Parse optional range arguments
    val rangeArgs = args.filter { it.value is String }
    val range = when {
        rangeArgs.size >= 2 -> listOf(
            rangeArgs[0].value.toString(),
            rangeArgs[1].value.toString()
        )

        else -> listOf("C3", "C5") // Default range
    }

    // Track last voicing for voice leading
    var lastVoicing: List<String> = emptyList()

    // No need to collapse to rootSource anymore

    // Use BindPattern to expand events with voice leading
    return BindPattern(source) { event ->
        val chordName = event.data.chord

        if (chordName == null) {
            // No chord, return unchanged
            AtomicPattern(data = event.data, sourceLocations = event.sourceLocations)
        } else {
            // Get voicing (either from voice leading or fallback)
            val voicedNotes = getVoicedNotes(chordName, range, lastVoicing)

            if (voicedNotes.isEmpty()) {
                // No voicing and no default notes (invalid chord?), return unchanged (root)
                AtomicPattern(data = event.data, sourceLocations = event.sourceLocations)
            } else {
                // Update last voicing for next iteration
                // We update it even with fallback notes to reset the voice leading context
                lastVoicing = voicedNotes

                // Create stack pattern with the voicing notes
                val voicedEvents = voicedNotes.map { noteName ->
                    AtomicPattern(
                        data = event.data.copy(
                            note = noteName,
                            freqHz = Tones.noteToFreq(noteName),
                            chord = null, // Do not preserve chord property to match JS behavior
                            gain = event.data.gain,
                        ),
                        sourceLocations = event.sourceLocations
                    )
                }
                StackPattern(voicedEvents)
            }
        }
    }
}

internal val SprudelPattern._voicing by dslPatternExtension { p, args, _ -> applyVoicing(p, args) }

internal val String._voicing by dslStringExtension { p, args, callInfo -> p._voicing(args, callInfo) }

internal val _voicing by dslPatternMapper { args, callInfo -> { p -> p._voicing(args, callInfo) } }

internal val PatternMapperFn._voicing by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_voicing(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Expands chord patterns into voiced notes using voice leading.
 *
 * Converts each event carrying a chord name (set via [chord]) into a stack of notes that
 * form the chord, applying smooth voice leading to minimise large jumps between chords.
 * An optional pair of note-string arguments sets the register range (default `"C3"` to `"C5"`).
 *
 * ```KlangScript
 * chord("C:major Am:minor F:major G:major").voicing()         // voiced I-vi-IV-V
 * ```
 *
 * ```KlangScript
 * chord("Cmaj7 Am7 Fmaj7").voicing("C3", "C5")  // voiced within C3–C5 range
 * ```
 *
 * @category tonal
 * @tags voicing, voice leading, chord, harmony
 */
@SprudelDsl
fun SprudelPattern.voicing(): SprudelPattern = this._voicing(emptyList())

/** Expands chord events in this pattern into voiced notes within the given range. */
@SprudelDsl
fun SprudelPattern.voicing(low: String, high: String): SprudelPattern =
    this._voicing(listOf(low, high).asSprudelDslArgs())

/** Expands chord events in a string pattern into voiced notes with voice leading. */
@SprudelDsl
fun String.voicing(): SprudelPattern = this._voicing(emptyList())

/** Expands chord events in a string pattern into voiced notes within the given range. */
@SprudelDsl
fun String.voicing(low: String, high: String): SprudelPattern =
    this._voicing(listOf(low, high).asSprudelDslArgs())

/** Returns a [PatternMapperFn] that applies voicing to chord events. */
@SprudelDsl
fun voicing(): PatternMapperFn = _voicing(emptyList())

/** Returns a [PatternMapperFn] that applies voicing to chord events within the given range. */
@SprudelDsl
fun voicing(low: String, high: String): PatternMapperFn = _voicing(listOf(low, high).asSprudelDslArgs())

/** Chains a voicing step onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.voicing(): PatternMapperFn = this._voicing(emptyList())

/** Chains a voicing step onto this [PatternMapperFn], within the given range. */
@SprudelDsl
fun PatternMapperFn.voicing(low: String, high: String): PatternMapperFn =
    this._voicing(listOf(low, high).asSprudelDslArgs())
