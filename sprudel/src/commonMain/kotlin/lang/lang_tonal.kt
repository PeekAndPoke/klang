@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel.SprudelVoiceValue
import io.peekandpoke.klang.sprudel._applyControlFromParams
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel._liftOrReinterpretStringField
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.AtomicPattern
import io.peekandpoke.klang.sprudel.pattern.ControlPattern
import io.peekandpoke.klang.sprudel.pattern.ReinterpretPattern.Companion.reinterpretVoice
import io.peekandpoke.klang.sprudel.pattern.StackPattern
import io.peekandpoke.klang.sprudel.sampleAt
import io.peekandpoke.klang.tones.Tones
import io.peekandpoke.klang.tones.chord.Chord
import io.peekandpoke.klang.tones.distance.Distance
import io.peekandpoke.klang.tones.interval.Interval
import io.peekandpoke.klang.tones.midi.Midi
import io.peekandpoke.klang.tones.note.Note
import io.peekandpoke.klang.tones.scale.Scale
import kotlin.math.pow

/**
 * Accessing this property forces the initialization of this file's class.
 */
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

private fun applyScale(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { scaleName ->
        copy(scale = scaleName?.cleanScaleName()).resolveNote()
    }
}

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
 * ```KlangScript(Playable)
 * n("0 1 2 3").scale("c4:major")          // C4, D4, E4, F4
 * ```
 *
 * ```KlangScript(Playable)
 * n("0 2 4").scale("<c4:major a3:minor>")  // alternates scale per cycle
 * ```
 *
 * @category tonal
 * @tags scale, pitch, musical scale, mode, tuning
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.scale(name: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyScale(this, listOfNotNull(name).asSprudelDslArgs(callInfo))

/** Applies scale context to a string pattern; numeric values are resolved to scale notes. */
@SprudelDsl
@KlangScript.Function
fun String.scale(name: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).scale(name, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the scale context for resolving note indices.
 *
 * ```KlangScript(Playable)
 * n("0 1 2").apply(scale("c4:major"))     // mapper form: C4, D4, E4
 * ```
 *
 * @category tonal
 * @tags scale, pitch, musical scale, mode, tuning
 */
@SprudelDsl
@KlangScript.Function
fun scale(name: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.scale(name, callInfo) }

/** Chains a scale operation onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.scale(name: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.scale(name, callInfo) }

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

private fun applyNote(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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

/**
 * Creates a pattern of musical notes from a mini-notation string or sequence of note names.
 *
 * Note values can be scientific notation (`"c4"`, `"d#3"`, `"bb2"`), MIDI note numbers, or
 * numeric indices resolved via the active [scale] context.
 *
 * ```KlangScript(Playable)
 * note("c4 e4 g4")          // arpeggiate a C major chord
 * ```
 *
 * ```KlangScript(Playable)
 * note("<c3 e3 g3> b2")     // alternating chord tones with a bass note
 * ```
 *
 * @param note The note pattern in mini-notation, e.g. `"c4 e4 g4"`.
 * @param-tool note SprudelNoteEditor
 * @category tonal
 * @tags note, pitch, frequency, MIDI, note name, pattern-creator
 */
@SprudelDsl
@KlangScript.Function
fun note(vararg note: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    note.toList().asSprudelDslArgs(callInfo).toPattern(noteMutation).note(callInfo = callInfo)

/** Applies note values from arguments (or reinterprets current value as a note name). */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.note(noteName: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyNote(this, listOfNotNull(noteName).asSprudelDslArgs(callInfo))

/** Applies note values to a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.note(noteName: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).note(noteName, callInfo)

// -- n() --------------------------------------------------------------------------------------------------------------

private val nMutation = voiceModifier {
    copy(
        soundIndex = it?.asIntOrNull() ?: soundIndex,
        value = null,
    )
}

private fun applyN(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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

/**
 * Sets the sound index on this pattern.
 *
 * When param [index] is null, the sequence values will be reinterpreted as sound index.
 *
 * ```KlangScript(Playable)
 * n("0 2 4").scale("c4:major").note()   // indices 0, 2, 4 → C4, E4, G4
 * ```
 *
 * ```KlangScript(Playable)
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
@KlangScript.Function
fun SprudelPattern.n(index: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyN(this, listOfNotNull(index).asSprudelDslArgs(callInfo))

/** Sets the sound index on this string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.n(index: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).n(index, callInfo)

/**
 * Creates a pattern of sound indices.
 *
 * @param index The scale degree index pattern in mini-notation, e.g. `"0 1 2 3"`.
 * @param-tool index SprudelScaleDegreeEditor
 */
@SprudelDsl
@KlangScript.Function
fun n(index: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    listOf(index).asSprudelDslArgs(callInfo).toPattern(nMutation).n(callInfo = callInfo)

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

private fun applySound(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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

/**
 * Creates a pattern selecting a sound (instrument or sample bank) by name.
 *
 * Each event's value selects the instrument or sample bank used during playback.
 * The format `"name:index"` also sets the sample index, e.g. `"bd:2"` selects sample 2
 * from the `bd` bank.
 *
 * When [name] is omitted, reinterprets the current event values as sound names.
 *
 * ```KlangScript(Playable)
 * sound("bd sd hh")  // basic drum pattern
 * ```
 *
 * ```KlangScript(Playable)
 * sound("bd bd bd bd ").n("0 1 2 3")  // changes the sound variants
 * ```
 *
 * ```KlangScript(Playable)
 * seq("bd hh sd hh").sound()  // interprets the sequence values as sounds
 * ```
 *
 * @param name The sound/sample name pattern in mini-notation, e.g. `"bd sd hh"`.
 * @param-tool name SprudelSampleSequenceEditor
 * @alias s
 * @category tonal
 * @tags sound, sample, instrument, s, pattern-creator
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.sound(name: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySound(this, listOfNotNull(name).asSprudelDslArgs(callInfo))

/**
 * Modifies or reinterprets the sounds of a string pattern.
 *
 * @param name The sound/sample name pattern in mini-notation, e.g. `"bd sd hh"`.
 * @return A new pattern with the specified sounds applied.
 * @category tonal
 * @tags sound, sample, instrument, s, pattern-creator
 */
@SprudelDsl
@KlangScript.Function
fun String.sound(name: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sound(name, callInfo)

/**
 * Creates a pattern of sounds.
 *
 * @param name The sound/sample name pattern in mini-notation, e.g. `"bd sd hh"`.
 * @param-tool name SprudelSampleSequenceEditor
 */
@SprudelDsl
@KlangScript.Function
fun sound(name: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    listOf(name).asSprudelDslArgs(callInfo).toPattern(soundMutation).sound(callInfo = callInfo)

/** Alias for [sound]. Creates a pattern selecting a sound (instrument or sample bank) by name.
 *
 * Each event's value selects the instrument or sample bank used during playback.
 * The format `"name:index"` also sets the sample index, e.g. `"bd:2"` selects sample 2
 * from the `bd` bank.
 *
 * When [name] is omitted, reinterprets the current event values as sound names.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh")  // basic drum pattern
 * ```
 *
 * ```KlangScript(Playable)
 * seq("bd hh sd hh").s()  // interprets the sequence values as sounds
 * ```
 *
 * @param name The sound/sample name pattern in mini-notation, e.g. `"bd sd hh"`.
 * @param-tool name SprudelSampleSequenceEditor
 * @alias sound
 * @category tonal
 * @tags sound, sample, instrument, s, pattern-creator
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.s(name: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.sound(name, callInfo)

/** Alias for [sound] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.s(name: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).s(name, callInfo)

/**
 * Alias for [sound]. Creates a sound pattern.
 *
 * @param name The sound/sample name pattern in mini-notation, e.g. `"bd sd hh"`.
 * @param-tool name SprudelSampleSequenceEditor
 */
@SprudelDsl
@KlangScript.Function
fun s(name: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    sound(name, callInfo)

// -- bank() -----------------------------------------------------------------------------------------------------------

private fun applyBank(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { bankName ->
        copy(bank = bankName)
    }
}

/**
 * Sets the sample bank for each event, overriding which collection of samples is used.
 *
 * The bank determines where samples are loaded from, independently of the sound name.
 * Useful when you want to switch sample collections without changing sound identifiers.
 * When called with no argument, reinterprets the current event value as a bank name.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh").bank("RolandTR808")     // load all sounds from the TR-808 bank
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").bank("<TR808 TR909>")      // alternate sample banks each cycle
 * ```
 *
 * @param name The sample bank name, e.g. `"RolandTR808"`. Default: none (uses default bank).
 * @category tonal
 * @tags bank, sample bank, instrument
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.bank(name: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyBank(this, listOfNotNull(name).asSprudelDslArgs(callInfo))

/** Sets the sample bank on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.bank(name: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bank(name, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the sample bank for each event.
 * When called with no argument, reinterprets the current event value as a bank name.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(bank("RolandTR808"))   // mapper form
 * ```
 *
 * @category tonal
 * @tags bank, sample bank, instrument
 */
@SprudelDsl
@KlangScript.Function
fun bank(name: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.bank(name, callInfo) }

/** Chains a bank operation onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.bank(name: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bank(name, callInfo) }

// -- legato() / clip() ------------------------------------------------------------------------------------------------

private fun applyLegato(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args) { amount ->
        copy(legato = amount)
    }
}

/**
 * Sets the legato (duration scaling) factor for events in this pattern.
 *
 * A legato of `1.0` fills the full event duration; values above `1.0` create overlapping
 * notes (true legato), while values below `1.0` create staccato-like gaps between notes.
 * When called with no argument, reinterprets the current event value as a legato amount.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").legato(1.5)   // notes overlap slightly (legato)
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").legato(0.5)   // notes are shorter (staccato)
 * ```
 *
 * @param amount Duration scaling factor. 1.0 = fill event slot exactly, 0.5 = staccato (half length),
 *   1.5 = overlapping (legato), 2.0 = double length. Default: 1.0. Typical range: 0.1–2.0.
 * @alias clip
 * @category tonal
 * @tags legato, clip, duration, sustain, staccato
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.legato(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyLegato(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/** Sets the legato (duration scaling) factor on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.legato(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).legato(amount, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the legato factor for each event.
 * When called with no argument, reinterprets the current event value as a legato amount.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(legato(1.5))   // mapper form
 * ```
 *
 * @alias clip
 * @category tonal
 * @tags legato, clip, duration, sustain, staccato
 */
@SprudelDsl
@KlangScript.Function
fun legato(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.legato(amount, callInfo) }

/** Chains a legato operation onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.legato(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.legato(amount, callInfo) }

/** Alias for [legato] on this pattern. Sets the duration scaling factor. */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.clip(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.legato(amount, callInfo)

/** Alias for [legato] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.clip(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).legato(amount, callInfo)

/**
 * Alias for [legato]. Returns a [PatternMapperFn] that sets the legato factor for each event.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(clip(0.5))   // mapper form
 * ```
 *
 * @alias legato
 * @category tonal
 * @tags legato, clip, duration, sustain, staccato
 */
@SprudelDsl
@KlangScript.Function
fun clip(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.legato(amount, callInfo) }

/** Chains a clip operation onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.clip(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.legato(amount, callInfo) }

// -- vibrato() --------------------------------------------------------------------------------------------------------

private fun applyVibrato(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args) { hz ->
        copy(vibrato = hz)
    }
}

/**
 * Sets the vibrato rate (oscillation speed) in Hz.
 *
 * Vibrato is a periodic pitch modulation applied to a note. Higher values create faster
 * vibrato; lower values create a slower wobble. Use [vibratoMod] to set the depth in semitones.
 * Default rate is 5 Hz when [vibratoMod] is set but [vibrato] is not.
 *
 * Both the sprudel DSL and the Ignitor DSL use the same units:
 * rate in Hz, depth in semitones.
 *
 * ```KlangScript(Playable)
 * note("c4 e4 g4").vibrato(5).vibratoMod(0.5)  // 5 Hz, ±0.5 semitone
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").vibrato("<2 8>")       // alternating slow/fast vibrato per cycle
 * ```
 *
 * @param hz Vibrato LFO rate in Hz. 0.0 = no vibrato, 3.0 = gentle, 5.0 = standard,
 *   8.0+ = fast. Default: 5.0 Hz (when vibratoMod is set). Typical range: 1.0–10.0.
 * @alias vib
 * @category tonal
 * @tags vibrato, vib, pitch modulation, oscillation, LFO
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.vibrato(hz: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyVibrato(this, listOfNotNull(hz).asSprudelDslArgs(callInfo))

/** Sets the vibrato frequency (speed) in Hz on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.vibrato(hz: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).vibrato(hz, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the vibrato frequency in Hz.
 * When called with no argument, reinterprets the current event value as a vibrato rate.
 *
 * ```KlangScript(Playable)
 * note("c4").apply(vibrato(5))   // mapper form
 * ```
 *
 * @alias vib
 * @category tonal
 * @tags vibrato, vib, pitch modulation, oscillation, LFO
 */
@SprudelDsl
@KlangScript.Function
fun vibrato(hz: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.vibrato(hz, callInfo) }

/** Chains a vibrato operation onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.vibrato(hz: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.vibrato(hz, callInfo) }

/** Alias for [vibrato] on this pattern. Sets the vibrato frequency in Hz. */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.vib(hz: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.vibrato(hz, callInfo)

/** Alias for [vibrato] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.vib(hz: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).vibrato(hz, callInfo)

/**
 * Alias for [vibrato]. Returns a [PatternMapperFn] that sets the vibrato frequency in Hz.
 *
 * ```KlangScript(Playable)
 * note("c4").apply(vib(5))   // mapper form
 * ```
 *
 * @alias vibrato
 * @category tonal
 * @tags vibrato, vib, pitch modulation, oscillation, LFO
 */
@SprudelDsl
@KlangScript.Function
fun vib(hz: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.vibrato(hz, callInfo) }

/** Chains a vib operation onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.vib(hz: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.vibrato(hz, callInfo) }

// -- vibratoMod() -----------------------------------------------------------------------------------------------------

private fun applyVibratoMod(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args) { depth ->
        copy(vibratoMod = depth)
    }
}

/**
 * Sets the vibrato depth (amplitude of pitch oscillation) in semitones.
 *
 * Controls how many semitones the vibrato deviates from the base pitch. Higher values
 * create wider, more pronounced pitch wobble. Use [vibrato] to set the rate in Hz.
 *
 * Both the sprudel DSL and the Ignitor DSL use semitones for depth.
 * Internally converted to a frequency ratio via `depth / 12.0`.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").vibratoMod(0.5)       // ±0.5 semitone pitch deviation
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").vibratoMod("<0.2 1>")    // alternating subtle/wide vibrato depth
 * ```
 *
 * @param depth Vibrato depth in semitones. 0.0 = no vibrato, 0.2 = subtle,
 *   0.5 = standard, 1.0+ = wide wobble. Default: 0.0. Typical range: 0.1–2.0.
 * @alias vibmod
 * @category tonal
 * @tags vibratoMod, vibmod, vibrato depth, pitch modulation
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.vibratoMod(depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyVibratoMod(this, listOfNotNull(depth).asSprudelDslArgs(callInfo))

/** Sets the vibrato depth on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.vibratoMod(depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).vibratoMod(depth, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the vibrato depth in semitones.
 * When called with no argument, reinterprets the current event value as a vibrato depth.
 *
 * ```KlangScript(Playable)
 * note("c4").apply(vibratoMod(0.5))   // mapper form
 * ```
 *
 * @alias vibmod
 * @category tonal
 * @tags vibratoMod, vibmod, vibrato depth, pitch modulation
 */
@SprudelDsl
@KlangScript.Function
fun vibratoMod(depth: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.vibratoMod(depth, callInfo) }

/** Chains a vibratoMod operation onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.vibratoMod(depth: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.vibratoMod(depth, callInfo) }

/** Alias for [vibratoMod] on this pattern. Sets the vibrato depth. */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.vibmod(depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.vibratoMod(depth, callInfo)

/** Alias for [vibratoMod] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.vibmod(depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).vibratoMod(depth, callInfo)

/**
 * Alias for [vibratoMod]. Returns a [PatternMapperFn] that sets the vibrato depth.
 *
 * ```KlangScript(Playable)
 * note("c4").apply(vibmod(0.5))   // mapper form
 * ```
 *
 * @alias vibratoMod
 * @category tonal
 * @tags vibratoMod, vibmod, vibrato depth, pitch modulation
 */
@SprudelDsl
@KlangScript.Function
fun vibmod(depth: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.vibratoMod(depth, callInfo) }

/** Chains a vibmod operation onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.vibmod(depth: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.vibratoMod(depth, callInfo) }

// -- pattack() / patt() -----------------------------------------------------------------------------------------------

private fun applyPAttack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args) { seconds -> copy(pAttack = seconds) }
}

/**
 * Sets the pitch envelope attack time in seconds.
 *
 * The pitch envelope shapes how the pitch changes over a note's duration. The attack
 * phase determines how quickly the pitch rises from its anchor to the target pitch.
 * Use with [penv], [pdecay], [prelease], [pcurve], and [panchor].
 * When called with no argument, reinterprets the current event value as an attack time.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").pattack(0.1).penv(12)   // pitch rises over 100 ms by 12 semitones
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").pattack("<0.01 0.5>")       // fast vs slow pitch attack per cycle
 * ```
 *
 * @param seconds Pitch envelope attack time in seconds. 0.01 = instant, 0.1 = snappy,
 *   0.5+ = slow sweep. Default: 0.0. Typical range: 0.001–2.0.
 * @alias patt
 * @category tonal
 * @tags pattack, patt, pitch envelope, attack, envelope
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.pattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyPAttack(this, listOfNotNull(seconds).asSprudelDslArgs(callInfo))

/** Sets the pitch envelope attack time on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.pattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pattack(seconds, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the pitch envelope attack time.
 *
 * ```KlangScript(Playable)
 * note("c4").apply(pattack(0.1))   // mapper form
 * ```
 *
 * @alias patt
 * @category tonal
 * @tags pattack, patt, pitch envelope, attack, envelope
 */
@SprudelDsl
@KlangScript.Function
fun pattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.pattack(seconds, callInfo) }

/** Chains a pattack operation onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.pattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pattack(seconds, callInfo) }

/** Alias for [pattack] on this pattern. */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.patt(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.pattack(seconds, callInfo)

/** Alias for [pattack] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.patt(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pattack(seconds, callInfo)

/** Alias for [pattack]. Returns a [PatternMapperFn] that sets the pitch envelope attack time. */
@SprudelDsl
@KlangScript.Function
fun patt(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.pattack(seconds, callInfo) }

/** Chains a patt operation onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.patt(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pattack(seconds, callInfo) }

// -- pdecay() / pdec() ------------------------------------------------------------------------------------------------

private fun applyPDecay(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args) { seconds -> copy(pDecay = seconds) }
}

/**
 * Sets the pitch envelope decay time in seconds.
 *
 * After the attack phase, the pitch envelope decays towards the sustain level. The decay
 * time determines how quickly this transition happens.
 * Use with [pattack], [penv], [prelease], [pcurve], and [panchor].
 * When called with no argument, reinterprets the current event value as a decay time.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").pdecay(0.2).penv(12)   // pitch decays over 200 ms
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").pdecay("<0.05 0.5>")       // short vs long decay per cycle
 * ```
 *
 * @param seconds Pitch envelope decay time in seconds. 0.05 = snappy, 0.2 = moderate,
 *   1.0+ = long sweep. Default: 0.0. Typical range: 0.01–5.0.
 * @alias pdec
 * @category tonal
 * @tags pdecay, pdec, pitch envelope, decay, envelope
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.pdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyPDecay(this, listOfNotNull(seconds).asSprudelDslArgs(callInfo))

/** Sets the pitch envelope decay time on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.pdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pdecay(seconds, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the pitch envelope decay time.
 *
 * ```KlangScript(Playable)
 * note("c4").apply(pdecay(0.2))   // mapper form
 * ```
 *
 * @alias pdec
 * @category tonal
 * @tags pdecay, pdec, pitch envelope, decay, envelope
 */
@SprudelDsl
@KlangScript.Function
fun pdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.pdecay(seconds, callInfo) }

/** Chains a pdecay operation onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.pdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pdecay(seconds, callInfo) }

/** Alias for [pdecay] on this pattern. */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.pdec(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.pdecay(seconds, callInfo)

/** Alias for [pdecay] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.pdec(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pdecay(seconds, callInfo)

/** Alias for [pdecay]. Returns a [PatternMapperFn] that sets the pitch envelope decay time. */
@SprudelDsl
@KlangScript.Function
fun pdec(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.pdecay(seconds, callInfo) }

/** Chains a pdec operation onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.pdec(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pdecay(seconds, callInfo) }

// -- prelease() / prel() ----------------------------------------------------------------------------------------------

private fun applyPRelease(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args) { seconds -> copy(pRelease = seconds) }
}

/**
 * Sets the pitch envelope release time in seconds.
 *
 * The release phase determines how quickly the pitch envelope returns to its resting state
 * after the note ends. Use with [pattack], [pdecay], [penv], [pcurve], and [panchor].
 * When called with no argument, reinterprets the current event value as a release time.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").prelease(0.3).penv(12)  // pitch releases over 300 ms
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").prelease("<0.1 1.0>")       // short vs long release per cycle
 * ```
 *
 * @param seconds Pitch envelope release time in seconds. How quickly pitch returns after note-off. 0.01 = instant, 0.3 = gradual. Default: 0.0. Typical range: 0.001–5.0.
 *
 * @alias prel
 * @category tonal
 * @tags prelease, prel, pitch envelope, release, envelope
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.prelease(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyPRelease(this, listOfNotNull(seconds).asSprudelDslArgs(callInfo))

/** Sets the pitch envelope release time on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.prelease(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).prelease(seconds, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the pitch envelope release time.
 *
 * ```KlangScript(Playable)
 * note("c4").apply(prelease(0.3))   // mapper form
 * ```
 *
 * @alias prel
 * @category tonal
 * @tags prelease, prel, pitch envelope, release, envelope
 */
@SprudelDsl
@KlangScript.Function
fun prelease(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.prelease(seconds, callInfo) }

/** Chains a prelease operation onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.prelease(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.prelease(seconds, callInfo) }

/** Alias for [prelease] on this pattern. */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.prel(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.prelease(seconds, callInfo)

/** Alias for [prelease] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.prel(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).prelease(seconds, callInfo)

/** Alias for [prelease]. Returns a [PatternMapperFn] that sets the pitch envelope release time. */
@SprudelDsl
@KlangScript.Function
fun prel(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.prelease(seconds, callInfo) }

/** Chains a prel operation onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.prel(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.prelease(seconds, callInfo) }

// -- penv() / pamt() --------------------------------------------------------------------------------------------------

private fun applyPEnv(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args) { semitones -> copy(pEnv = semitones) }
}

/**
 * Sets the pitch envelope depth (amount) in semitones.
 *
 * Determines how far the pitch deviates from the base note during the envelope cycle.
 * Positive values raise the pitch; negative values lower it.
 * Use with [pattack], [pdecay], [prelease], [pcurve], and [panchor].
 * When called with no argument, reinterprets the current event value as an envelope depth.
 *
 * ```KlangScript(Playable)
 * note("c4").penv(12).pattack(0.1)   // 1-octave pitch rise over 100 ms
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").penv(-7).pdecay(0.2)    // pitch falls a perfect fifth then decays
 * ```
 *
 * @param semitones Pitch envelope depth in semitones. How far pitch deviates. 12 = one octave up, -12 = one octave down, 0 = no pitch envelope. Default: 0.0. Range: -24 to 24.
 *
 * @alias pamt
 * @category tonal
 * @tags penv, pamt, pitch envelope, depth, semitones, envelope
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.penv(semitones: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyPEnv(this, listOfNotNull(semitones).asSprudelDslArgs(callInfo))

/** Sets the pitch envelope depth (in semitones) on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.penv(semitones: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).penv(semitones, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the pitch envelope depth in semitones.
 *
 * ```KlangScript(Playable)
 * note("c4").apply(penv(12))   // mapper form
 * ```
 *
 * @alias pamt
 * @category tonal
 * @tags penv, pamt, pitch envelope, depth, semitones, envelope
 */
@SprudelDsl
@KlangScript.Function
fun penv(semitones: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.penv(semitones, callInfo) }

/** Chains a penv operation onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.penv(semitones: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.penv(semitones, callInfo) }

/** Alias for [penv] on this pattern. */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.pamt(semitones: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.penv(semitones, callInfo)

/** Alias for [penv] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.pamt(semitones: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).penv(semitones, callInfo)

/** Alias for [penv]. Returns a [PatternMapperFn] that sets the pitch envelope depth. */
@SprudelDsl
@KlangScript.Function
fun pamt(semitones: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.penv(semitones, callInfo) }

/** Chains a pamt operation onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.pamt(semitones: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.penv(semitones, callInfo) }

// -- pcurve() / pcrv() ------------------------------------------------------------------------------------------------

private fun applyPCurve(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args) { curve -> copy(pCurve = curve) }
}

/**
 * Sets the pitch envelope curve shape.
 *
 * Controls the curvature of the pitch envelope segments. A value of `0` gives a linear
 * curve; positive values create logarithmic curves; negative values create exponential ones.
 * When called with no argument, reinterprets the current event value as a curve shape.
 *
 * ```KlangScript(Playable)
 * note("c4").pcurve(2).penv(12).pattack(0.2)   // logarithmic pitch rise
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").pcurve(-2).penv(12).pattack(0.2)  // exponential pitch rise
 * ```
 *
 * @param curve Envelope curve shape. 1.0 = linear, <1.0 = concave (fast start, slow end), >1.0 = convex (slow start, fast end). Default: 1.0. Typical range: 0.5–2.0.
 *
 * @alias pcrv
 * @category tonal
 * @tags pcurve, pcrv, pitch envelope, curve, shape, envelope
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.pcurve(curve: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyPCurve(this, listOfNotNull(curve).asSprudelDslArgs(callInfo))

/** Sets the pitch envelope curve shape on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.pcurve(curve: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pcurve(curve, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the pitch envelope curve shape.
 *
 * ```KlangScript(Playable)
 * note("c4").apply(pcurve(2))   // mapper form
 * ```
 *
 * @alias pcrv
 * @category tonal
 * @tags pcurve, pcrv, pitch envelope, curve, shape, envelope
 */
@SprudelDsl
@KlangScript.Function
fun pcurve(curve: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.pcurve(curve, callInfo) }

/** Chains a pcurve operation onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.pcurve(curve: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pcurve(curve, callInfo) }

/** Alias for [pcurve] on this pattern. */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.pcrv(curve: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.pcurve(curve, callInfo)

/** Alias for [pcurve] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.pcrv(curve: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pcurve(curve, callInfo)

/** Alias for [pcurve]. Returns a [PatternMapperFn] that sets the pitch envelope curve shape. */
@SprudelDsl
@KlangScript.Function
fun pcrv(curve: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.pcurve(curve, callInfo) }

/** Chains a pcrv operation onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.pcrv(curve: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pcurve(curve, callInfo) }

// -- panchor() / panc() -----------------------------------------------------------------------------------------------

private fun applyPAnchor(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args) { anchor -> copy(pAnchor = anchor) }
}

/**
 * Sets the pitch envelope anchor point.
 *
 * The anchor determines the relative position within the note duration where the pitch
 * envelope reaches its peak (or trough). `0` anchors at the start; `1` at the end.
 * When called with no argument, reinterprets the current event value as an anchor point.
 *
 * ```KlangScript(Playable)
 * note("c4").panchor(0).penv(12)   // pitch peaks at note start
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4").panchor(1).penv(12)   // pitch peaks at note end
 * ```
 *
 * @param anchor Sustain pitch offset. -1.0 to 1.0. 0.0 = pitch returns to original note, other values offset the sustain pitch. Default: 0.0.
 *
 * @alias panc
 * @category tonal
 * @tags panchor, panc, pitch envelope, anchor, envelope
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.panchor(anchor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyPAnchor(this, listOfNotNull(anchor).asSprudelDslArgs(callInfo))

/** Sets the pitch envelope anchor point on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.panchor(anchor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).panchor(anchor, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the pitch envelope anchor point.
 *
 * ```KlangScript(Playable)
 * note("c4").apply(panchor(0))   // mapper form
 * ```
 *
 * @alias panc
 * @category tonal
 * @tags panchor, panc, pitch envelope, anchor, envelope
 */
@SprudelDsl
@KlangScript.Function
fun panchor(anchor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.panchor(anchor, callInfo) }

/** Chains a panchor operation onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.panchor(anchor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.panchor(anchor, callInfo) }

/** Alias for [panchor] on this pattern. */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.panc(anchor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.panchor(anchor, callInfo)

/** Alias for [panchor] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.panc(anchor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).panchor(anchor, callInfo)

/** Alias for [panchor]. Returns a [PatternMapperFn] that sets the pitch envelope anchor point. */
@SprudelDsl
@KlangScript.Function
fun panc(anchor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.panchor(anchor, callInfo) }

/** Chains a panc operation onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.panc(anchor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.panchor(anchor, callInfo) }

// -- accelerate() -----------------------------------------------------------------------------------------------------

private fun applyAccelerate(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args) { amount ->
        copy(accelerate = amount)
    }
}

/**
 * Sets the playback acceleration (pitch ramp) for each event.
 *
 * Controls a continuous pitch change during sample playback. Positive values pitch up over
 * the event's duration; negative values pitch down. Useful for creating pitched percussion
 * or sweep effects. When called with no argument, reinterprets the current event value as
 * an acceleration amount.
 *
 * ```KlangScript(Playable)
 * s("cr").accelerate(2)              // crash pitches up during playback
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh").accelerate("<0 -2 2>")     // alternate: no ramp, down, up per cycle
 * ```
 *
 * @param amount Pitch bend over the voice's duration. 0.0 = no bend, positive = pitch rises, negative = pitch falls. Default: 0.0. Typical range: -1.0 to 1.0.
 *
 * @category tonal
 * @tags accelerate, pitch ramp, pitch bend, playback speed
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.accelerate(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyAccelerate(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/** Sets the playback acceleration on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.accelerate(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).accelerate(amount, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the playback acceleration (pitch ramp).
 * When called with no argument, reinterprets the current event value as an acceleration amount.
 *
 * ```KlangScript(Playable)
 * s("hh").apply(accelerate(2))   // mapper form
 * ```
 *
 * @category tonal
 * @tags accelerate, pitch ramp, pitch bend, playback speed
 */
@SprudelDsl
@KlangScript.Function
fun accelerate(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.accelerate(amount, callInfo) }

/** Chains an accelerate operation onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.accelerate(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.accelerate(amount, callInfo) }

// -- transpose() ------------------------------------------------------------------------------------------------------

/**
 * Applies transposition logic to a SprudelVoiceData instance.
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

private fun applyTranspose(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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

/**
 * Transposes a pattern by a number of semitones or an interval name.
 *
 * Shifts all note pitches by the given amount. Numeric arguments are treated as semitones;
 * string arguments can be interval names (e.g. `"P5"` for a perfect fifth).
 *
 * ```KlangScript(Playable)
 * note("c4 e4 g4").transpose(7)       // transpose up a perfect fifth
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4 e4").transpose("<0 12>")   // alternate: no transpose vs octave up per cycle
 * ```
 *
 * @param amount The amount to transpose by, either as a number of semitones or an interval name.
 *
 * @category tonal
 * @tags transpose, pitch shift, semitones, interval, pitch
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.transpose(amount: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyTranspose(this, listOf(amount).asSprudelDslArgs(callInfo))

/** Transposes a string pattern by a number of semitones or interval name. */
@SprudelDsl
@KlangScript.Function
fun String.transpose(amount: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).transpose(amount, callInfo)

/** Returns a [PatternMapperFn] that transposes each event by the given semitones or interval name. */
@SprudelDsl
@KlangScript.Function
fun transpose(amount: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.transpose(amount, callInfo) }

/** Chains a transpose step onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.transpose(amount: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.transpose(amount, callInfo) }

// -- freq() -----------------------------------------------------------------------------------------------------------

private fun applyFreq(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args) { v -> copy(freqHz = v) }
}

/**
 * Sets the playback frequency in Hz directly, bypassing note name resolution.
 *
 * Overrides the computed frequency for each event. Useful for precise tuning or
 * microtonal work where standard note names are insufficient. When called with no argument,
 * reinterprets the current event value as a frequency in Hz.
 *
 * ```KlangScript(Playable)
 * freq("440 550 660")          // A4, roughly C#5, roughly E5 by raw Hz
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4 e4").freq(432)      // force all events to 432 Hz
 * ```
 *
 * @param hz Frequency in Hz. Directly sets the pitch, bypassing note name resolution. 440 = A4, 261.63 = C4. Default: determined by note(). Range: 20–20000.
 *
 * @category tonal
 * @tags freq, frequency, Hz, pitch, tuning
 */
@SprudelDsl
@KlangScript.Function
fun freq(hz: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.freq(hz, callInfo) }

/** Sets the playback frequency in Hz on this pattern. */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.freq(hz: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyFreq(this, listOfNotNull(hz).asSprudelDslArgs(callInfo))

/** Sets the playback frequency in Hz on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.freq(hz: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).freq(hz, callInfo)

/** Chains a freq step onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.freq(hz: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.freq(hz, callInfo) }

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

private fun applyScaleTranspose(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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

/**
 * Transposes notes by a number of scale degrees within the active [scale].
 *
 * Unlike [transpose] which shifts by semitones, `scaleTranspose` steps through the notes of
 * the current scale context. Falls back to chromatic (semitone) transposition when no scale
 * is active.
 *
 * ```KlangScript(Playable)
 * n("0 2 4").scale("c4:major").note().scaleTranspose(1)  // shift up 1 scale degree
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4 e4 g4").scale("c4:major").scaleTranspose(-2)  // shift down 2 scale degrees
 * ```
 *
 * @param steps Number of scale steps to transpose. 1 = next scale note up, -1 = previous. Integer values. Default: 0. Range: any integer.
 *
 * @category tonal
 * @tags scaleTranspose, scale degrees, pitch, transpose
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.scaleTranspose(steps: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyScaleTranspose(this, listOf(steps).asSprudelDslArgs(callInfo))

/** Transposes a string pattern by a number of scale degrees within the active scale. */
@SprudelDsl
@KlangScript.Function
fun String.scaleTranspose(steps: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).scaleTranspose(steps, callInfo)

/** Returns a [PatternMapperFn] that transposes each event by the given number of scale degrees. */
@SprudelDsl
@KlangScript.Function
fun scaleTranspose(steps: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.scaleTranspose(steps, callInfo) }

/** Chains a scaleTranspose step onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.scaleTranspose(steps: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.scaleTranspose(steps, callInfo) }

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

private fun applyChordCreate(args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return args.toPattern(chordMutation)
}

private fun applyChordExtension(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, chordMutation) { src, ctrl ->
        src.chordMutation(ctrl.chord ?: ctrl.value?.asString)
    }
}

/**
 * Sets the chord name for each event, establishing harmonic context for [voicing].
 *
 * Chord names follow the format `"root quality"` (e.g. `"C"`, `"Am"`, `"Gmaj7"`).
 * The chord root is also set as the event's note. Use [voicing] to expand into voiced
 * notes, or [rootNotes] to extract just the bass note.
 *
 * ```KlangScript(Playable)
 * chord("<C Am F G>").voicing()         // voiced I-vi-IV-V
 * ```
 *
 * ```KlangScript(Playable)
 * chord("<Cmaj7 Am7>").voicing()        // jazzy chord alternation per cycle
 * ```
 *
 * @param name Chord type name, e.g. "major", "minor", "7", "m7", "dim", "aug". 112 types available. Default: none.
 *
 * @category tonal
 * @tags chord, harmony, chords, voicing, progression
 */
@SprudelDsl
@KlangScript.Function
fun chord(name: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyChordCreate(listOf(name).asSprudelDslArgs(callInfo))

/** Sets the chord name on this pattern for use with [voicing] and [rootNotes]. */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.chord(name: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyChordExtension(this, listOf(name).asSprudelDslArgs(callInfo))

/** Sets the chord name on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.chord(name: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).chord(name, callInfo)

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

private fun applyRootNotes(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val octave = args.firstOrNull()?.value?.asIntOrNull()

    // No need for distinct() anymore since chord() doesn't expand
    return source.reinterpretVoice { voiceData ->
        voiceData.extractRootNote(octave)
    }
}

/**
 * Extracts the root (bass) note from a chord pattern.
 *
 * Given a pattern with chord names set via [chord], `rootNotes` produces events carrying
 * only the chord root note. An optional integer argument forces the root to a specific octave.
 *
 * ```KlangScript(Playable)
 * chord("C:major Am:minor F:major").rootNotes()   // root notes: C, A, F
 * ```
 *
 * ```KlangScript(Playable)
 * chord("Cmaj7 Am7 Fmaj7").rootNotes(3)           // roots forced to octave 3
 * ```
 *
 * @param octave Root note extraction mode or offset. Default: extracts the root note from the current chord.
 *
 * @category tonal
 * @tags rootNotes, chord root, bass, harmony
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.rootNotes(octave: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyRootNotes(this, listOfNotNull(octave).asSprudelDslArgs(callInfo))

/** Extracts root notes from chord events in a string pattern, forcing to the given octave. */
@SprudelDsl
@KlangScript.Function
fun String.rootNotes(octave: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).rootNotes(octave, callInfo)

/** Returns a [PatternMapperFn] that extracts root notes from chord events, forcing to the given octave. */
@SprudelDsl
@KlangScript.Function
fun rootNotes(octave: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.rootNotes(octave, callInfo) }

/** Chains a rootNotes step (with forced octave) onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.rootNotes(octave: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.rootNotes(octave, callInfo) }

// -- voicing() --------------------------------------------------------------------------------------------------------

/**
 * Helper to get voiced notes for a chord, picking the [rank]-th candidate.
 *
 * Delegates to [io.peekandpoke.klang.tones.voicing.Voicing.getRanked] which guarantees ≥1
 * voicing for any chord string [io.peekandpoke.klang.tones.chord.Chord.get] can parse, by
 * falling back to chord-from-intervals at octave 4 when the dictionary search is empty.
 *
 * @param rank 0 = best, 1 = second-best, etc. Clamped into [0, ranked.lastIndex].
 */
internal fun getVoicedNotes(
    chordName: String,
    range: List<String>,
    lastVoicing: List<String>,
    rank: Int = 0,
): List<String> = try {
    val ranked = io.peekandpoke.klang.tones.voicing.Voicing.getRanked(
        chord = chordName,
        range = range,
        lastVoicing = lastVoicing,
    )
    if (ranked.isEmpty()) emptyList() else ranked[rank.coerceIn(0, ranked.lastIndex)]
} catch (_: Exception) {
    emptyList()
}

/**
 * Applies voice leading to chord patterns.
 *
 * Uses the Tones library's Voicing module for smooth transitions between chords. The optional
 * [lowPattern] / [highPattern] / [rankPattern] are sampled per source event to vary the voicing
 * range and pick the Nth-best candidate.
 *
 * Inlines BindPattern's intersection logic so we can sample the control patterns with the live
 * [SprudelPattern.QueryContext] at each source event's onset.
 */
private fun applyVoicing(
    source: SprudelPattern,
    lowPattern: SprudelPattern? = null,
    highPattern: SprudelPattern? = null,
    rankPattern: SprudelPattern? = null,
): SprudelPattern {
    return object : SprudelPattern {
        override val weight: Double get() = source.weight
        override val numSteps: Rational? get() = source.numSteps
        override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()

        override fun queryArcContextual(
            from: Rational,
            to: Rational,
            ctx: SprudelPattern.QueryContext,
        ): List<SprudelPatternEvent> {
            val outerEvents = source.queryArcContextual(from, to, ctx)
            val result = mutableListOf<SprudelPatternEvent>()

            // Voice-leading state shared across the source events in this query.
            var lastVoicing: List<String> = emptyList()

            for (outerEvent in outerEvents) {
                val intersectStart = maxOf(from, outerEvent.part.begin)
                val intersectEnd = minOf(to, outerEvent.part.end)
                if (intersectEnd <= intersectStart) continue

                val chordName = outerEvent.data.chord

                val innerPattern: SprudelPattern = if (chordName == null) {
                    AtomicPattern(data = outerEvent.data, sourceLocations = outerEvent.sourceLocations)
                } else {
                    val sampleTime = outerEvent.whole.begin

                    val low = lowPattern?.sampleAt(sampleTime, ctx)?.data?.value?.asString ?: "C3"
                    val high = highPattern?.sampleAt(sampleTime, ctx)?.data?.value?.asString ?: "C5"
                    val range = listOf(low, high)

                    val rankInt = rankPattern
                        ?.sampleAt(sampleTime, ctx)?.data?.value?.asDouble?.toInt()
                        ?: 0

                    val voicedNotes = getVoicedNotes(chordName, range, lastVoicing, rankInt)

                    if (voicedNotes.isEmpty()) {
                        AtomicPattern(data = outerEvent.data, sourceLocations = outerEvent.sourceLocations)
                    } else {
                        lastVoicing = voicedNotes
                        StackPattern(
                            voicedNotes.map { noteName ->
                                AtomicPattern(
                                    data = outerEvent.data.copy(
                                        note = noteName,
                                        freqHz = Tones.noteToFreq(noteName),
                                        chord = null,
                                    ),
                                    sourceLocations = outerEvent.sourceLocations,
                                )
                            }
                        )
                    }
                }

                val innerEvents = innerPattern.queryArcContextual(intersectStart, intersectEnd, ctx)
                for (innerEvent in innerEvents) {
                    val clippedPart = innerEvent.part.clipTo(outerEvent.part)
                    if (clippedPart != null) {
                        result.add(innerEvent.copy(part = clippedPart))
                    }
                }
            }

            return result
        }
    }
}

/** Converts a [rank] [PatternLike] argument into a control [SprudelPattern], or null if no rank is given. */
private fun toControlPattern(value: PatternLike?, callInfo: CallInfo?): SprudelPattern? =
    value?.let { listOf<Any?>(it).asSprudelDslArgs(callInfo).toPattern() }

/**
 * Expands chord patterns into voiced notes using voice leading.
 *
 * Converts each event carrying a chord name (set via [chord]) into a stack of notes that
 * form the chord, applying smooth voice leading to minimise large jumps between chords.
 *
 * All three parameters are optional and accept any [PatternLike] — constants, mininotation
 * patterns, or continuous control patterns. They are sampled per chord event, so range and
 * rank can vary in time.
 *
 * - `rank` picks which candidate voicing to use: `0` (default) is the best fit by voice
 *   leading, `1` is the second-best, etc. Out-of-range values clamp to the last available
 *   candidate; non-integer values are floored.
 * - `low` and `high` set the search-range bottom and top as note-name strings (`"C3"`,
 *   `"E5"`, …). When omitted, the default range is `"C3"` to `"C5"`.
 *
 * ```KlangScript(Playable)
 * chord("C:major Am:minor F:major G:major").voicing()                                // best voicing (rank = 0), default range
 * ```
 *
 * ```KlangScript(Playable)
 * chord("C:major Am:minor F:major G:major").voicing(rank = 1)                        // second-best every event
 * ```
 *
 * ```KlangScript(Playable)
 * chord("<C Am F G>").voicing(rank = "<0 1 0 2>")                                    // rank varies per cycle
 * ```
 *
 * ```KlangScript(Playable)
 * chord("<C Am F G>").voicing(rank = sine.range(0, 3).segment(4))                    // rank from a control pattern
 * ```
 *
 * ```KlangScript(Playable)
 * chord("Cmaj7 Am7 Fmaj7").voicing(low = "C3", high = "C5")                          // explicit fixed range
 * ```
 *
 * ```KlangScript(Playable)
 * chord("<C F G C>").voicing(low = "<C3 D3 E3 F3>", high = "<C5 D5 E5 F5>")          // range slides up per cycle
 * ```
 *
 * ```KlangScript(Playable)
 * chord("Dm7 G7").voicing(rank = 1, low = "C3", high = "C5")                         // second-best, in C3–C5
 * ```
 *
 * @param rank Which candidate voicing to pick: `0` = best (default), `1` = second-best, etc. Floored, then clamped to the candidate count. [PatternLike], sampled per event.
 * @param low Bottom of the voicing range as a note name (e.g. `"C3"`). [PatternLike], sampled per event. Default: `"C3"`.
 * @param high Top of the voicing range as a note name (e.g. `"C5"`). [PatternLike], sampled per event. Default: `"C5"`.
 *
 * @category tonal
 * @tags voicing, voice leading, chord, harmony, rank, range
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.voicing(
    rank: PatternLike? = null,
    low: PatternLike? = null,
    high: PatternLike? = null,
    callInfo: CallInfo? = null,
): SprudelPattern =
    applyVoicing(
        source = this,
        lowPattern = toControlPattern(low, callInfo),
        highPattern = toControlPattern(high, callInfo),
        rankPattern = toControlPattern(rank, callInfo),
    )

/**
 * Expands chord events in a string pattern into voiced notes with voice leading.
 *
 * See [SprudelPattern.voicing] for the meaning of [rank], [low], and [high].
 */
@SprudelDsl
@KlangScript.Function
fun String.voicing(
    rank: PatternLike? = null,
    low: PatternLike? = null,
    high: PatternLike? = null,
    callInfo: CallInfo? = null,
): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).voicing(rank, low, high, callInfo)

/**
 * Returns a [PatternMapperFn] that applies voicing to chord events.
 *
 * See [SprudelPattern.voicing] for the meaning of [rank], [low], and [high].
 */
@SprudelDsl
@KlangScript.Function
fun voicing(
    rank: PatternLike? = null,
    low: PatternLike? = null,
    high: PatternLike? = null,
    callInfo: CallInfo? = null,
): PatternMapperFn =
    { p -> p.voicing(rank, low, high, callInfo) }

/**
 * Chains a voicing step onto this [PatternMapperFn].
 *
 * See [SprudelPattern.voicing] for the meaning of [rank], [low], and [high].
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.voicing(
    rank: PatternLike? = null,
    low: PatternLike? = null,
    high: PatternLike? = null,
    callInfo: CallInfo? = null,
): PatternMapperFn =
    this.chain { p -> p.voicing(rank, low, high, callInfo) }
