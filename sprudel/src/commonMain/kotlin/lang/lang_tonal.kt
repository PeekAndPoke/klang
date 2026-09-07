/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.SoundValue
import io.peekandpoke.klang.common.math.CycleTime
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel.SprudelVoiceValue
import io.peekandpoke.klang.sprudel._applyControlFromParams
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel._liftOrReinterpretStringField
import io.peekandpoke.klang.sprudel._mapNumericField
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
 * The `value` field is parsed lazily here (consumed-at-use). `seq("0:1")` stores
 * the raw `"0:1"` string in `value`; only when `.scale(...)` / `.note()` reaches
 * this function do we split it into a scale-step input, an optional `soundIndex`
 * override, and an optional `gain` override. Parsed null parts never overwrite
 * existing voice fields — they're only applied when we have a real value.
 *
 * @param newIndex An optional new index to force (e.g. from n("0")).
 *                 If null, interprets value first (numeric or "step[:variant[:gain]]"
 *                 string), then falls back to existing soundIndex.
 */
fun SprudelVoiceData.resolveNote(newIndex: Int? = null): SprudelVoiceData {
    val effectiveScale = scale?.cleanScaleName()

    // Classify `value` into up to three parts:
    //   step             — scale-step input (or fallback note name)
    //   variantOverride  — explicit `:variant` from "X:Y[:Z]"
    //   gainOverride     — explicit `:gain`    from "X:Y:Z"
    //   fallbackName     — first colon-part, used as note name when no scale resolves
    val rawValue = value?.asString
    val parts = rawValue?.split(":")
    val firstPart = parts?.getOrNull(0)
    val step = firstPart?.toIntOrNull() ?: value?.asInt
    val variantOverride = parts?.getOrNull(1)?.toIntOrNull()
    val gainOverride = parts?.getOrNull(2)?.toDoubleOrNull()
    val fallbackName = firstPart

    val n = newIndex ?: step ?: soundIndex

    // Scale branch: index + scale -> resolve note name.
    if (n != null && !effectiveScale.isNullOrEmpty()) {
        val noteName = Scale.steps(effectiveScale).invoke(n)
        val valueWasStepSource = step != null
        return copy(
            note = noteName,
            freqHz = Tones.noteToFreq(noteName),
            // soundIndex: consumed (cleared) when soundIndex itself was the step source.
            // When value provided the step, leave soundIndex untouched unless a variant
            // override was parsed out of "step:variant".
            soundIndex = if (valueWasStepSource) (variantOverride ?: soundIndex) else null,
            // gain: only updated when a parsed gain override is present; else preserved.
            gain = gainOverride ?: gain,
            value = null,
        )
    }

    // Case A: explicit newIndex, no scale -> set soundIndex.
    if (newIndex != null) {
        return copy(soundIndex = newIndex)
    }

    // Case B: reinterpretation / fallback. Populate note from value if missing.
    val resolvedNote = note ?: fallbackName

    return copy(
        note = resolvedNote,
        freqHz = Tones.noteToFreq(resolvedNote ?: ""),
        soundIndex = variantOverride ?: (n ?: soundIndex),
        gain = gainOverride ?: gain,
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
@KlangScript.Function
fun SprudelPattern.scale(name: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyScale(this, listOfNotNull(name).asSprudelDslArgs(callInfo))

/** Applies scale context to a string pattern; numeric values are resolved to scale notes. */
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
@KlangScript.Function
fun scale(name: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.scale(name, callInfo) }

/** Chains a scale operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.scale(name: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.scale(name, callInfo) }

// -- note() -----------------------------------------------------------------------------------------------------------

private val noteMutation = voiceSetter { input ->
    val raw = input?.toString() ?: return@voiceSetter
    // `name:index[:gain]` form — mirrors soundMutation so `note("a:1")` works the
    // same way as `s("bd:1")` for picking a variant via Osc.variants(...) /
    // sample banks. Only split when [1] parses as an integer; otherwise leave
    // the string intact so non-numeric suffixes like "C4:minor" still resolve
    // through Tones.noteToFreq unchanged.
    val parts = raw.split(":")
    val parsedIndex = parts.getOrNull(1)?.toIntOrNull()
    if (parsedIndex == null) {
        note = raw
        freqHz = Tones.noteToFreq(raw)
        value = null
    } else {
        note = parts[0]
        freqHz = Tones.noteToFreq(parts[0])
        soundIndex = parsedIndex
        gain = parts.getOrNull(2)?.toDoubleOrNull() ?: gain
        value = null
    }
}

private fun applyNote(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return if (args.isEmpty()) {
        // Already-resolved guard: if a previous .scale() / .note() set note and cleared
        // value (e.g. seq("0:1").scale("cm").note()), don't re-run resolveNote — it would
        // treat the surviving soundIndex (variant override) as a fresh scale-step input.
        //
        // Otherwise: if the source carried a `value`, that value was consumed as a
        // scale/note-index proxy and any derived soundIndex is incidental — clear it
        // (strudel-compat). If no value (e.g. `note("c:2")` flowed through noteMutation),
        // preserve the soundIndex.
        source.reinterpretVoice {
            if (it.note != null && it.value == null) {
                it
            } else {
                val hadValue = it.value != null
                val resolved = it.resolveNote()
                if (hadValue) resolved.copy(soundIndex = null, value = null) else resolved.copy(value = null)
            }
        }
    } else {
        source._applyControlFromParams(args, noteMutation) { src, ctrl ->
            // ctrl already passed through noteMutation, which parsed any `name:index:gain`
            // form. Re-running the mutation on ctrl.note would lose those fields, so merge
            // them in directly (mirrors applySound).
            if (ctrl.note != null || ctrl.freqHz != null) {
                src.note = ctrl.note ?: src.note
                src.freqHz = ctrl.freqHz ?: src.freqHz
                src.soundIndex = ctrl.soundIndex ?: src.soundIndex
                src.gain = ctrl.gain ?: src.gain
                src.value = null
                src
            } else {
                // Fallback: ctrl wasn't parsed (e.g. raw value pattern) — interpret its
                // value as the note name and run the parser on it.
                src.noteMutation(ctrl.value?.asString)
            }
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
@KlangScript.Function
fun note(vararg note: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    note.toList().asSprudelDslArgs(callInfo).toPattern(noteMutation).note(callInfo = callInfo)

/** Applies note values from arguments (or reinterprets current value as a note name). */
@KlangScript.Function
fun SprudelPattern.note(noteName: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyNote(this, listOfNotNull(noteName).asSprudelDslArgs(callInfo))

/** Applies note values to a string pattern. */
@KlangScript.Function
fun String.note(noteName: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).note(noteName, callInfo)

// -- n() --------------------------------------------------------------------------------------------------------------

private val nMutation = voiceSetter {
    soundIndex = it?.asIntOrNull() ?: soundIndex
    value = null
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
@KlangScript.Function
fun SprudelPattern.n(index: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyN(this, listOfNotNull(index).asSprudelDslArgs(callInfo))

/** Sets the sound index on this string pattern. */
@KlangScript.Function
fun String.n(index: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).n(index, callInfo)

/**
 * Creates a pattern of sound indices.
 *
 * @param index The scale degree index pattern in mini-notation, e.g. `"0 1 2 3"`.
 * @param-tool index SprudelScaleDegreeEditor
 */
@KlangScript.Function
fun n(index: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    listOf(index).asSprudelDslArgs(callInfo).toPattern(nMutation).n(callInfo = callInfo)

// -- sound() / s() ----------------------------------------------------------------------------------------------------

private val soundMutation = voiceSetter {
    if (it == null) return@voiceSetter

    // An inline ignitor DSL value bypasses the "name:index" string parse and is stored as-is.
    if (it is IgnitorDsl) {
        sound = SoundValue.Osc(it)
        value = null
        return@voiceSetter
    }

    val split = it.toString().split(":")

    sound = split.getOrNull(0)?.let { name -> SoundValue.Named(name) }
    // Preserve existing index if the string doesn't specify one.
    soundIndex = split.getOrNull(1)?.toIntOrNull() ?: soundIndex
    // Preserve existing gain if the string doesn't specify one.
    gain = split.getOrNull(2)?.toDoubleOrNull() ?: gain
    // clear the value
    value = null
}

private fun applySound(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    // An inline ignitor DSL bypasses the mini-notation parse: stamp every source
    // event with SoundValue.Osc(dsl). Replaces any previously-set sound.
    val singleArg = args.singleOrNull()?.value
    if (singleArg is IgnitorDsl) {
        return source.reinterpretVoice { vd -> vd.copy(sound = SoundValue.Osc(singleArg), value = null) }
    }

    return if (args.isEmpty()) {
        // TODO: test this
        source.reinterpretVoice {
            it.soundMutation(it.value?.asString)
        }
    } else {
        source._applyControlFromParams(args, soundMutation) { src, ctrl ->
            src.sound = ctrl.sound ?: src.sound
            src.soundIndex = ctrl.soundIndex ?: src.soundIndex
            src
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
 * @param-tool name SprudelSampleEditor, SprudelSampleSequenceEditor
 * @alias s
 * @category tonal
 * @tags sound, sample, instrument, s, pattern-creator
 */
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
@KlangScript.Function
fun String.sound(name: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sound(name, callInfo)

/**
 * Creates a pattern of sounds.
 *
 * @param name The sound/sample name pattern in mini-notation, e.g. `"bd sd hh"`.
 * @param-tool name SprudelSampleEditor, SprudelSampleSequenceEditor
 */
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
 * @param-tool name SprudelSampleEditor, SprudelSampleSequenceEditor
 * @alias sound
 * @category tonal
 * @tags sound, sample, instrument, s, pattern-creator
 */
@KlangScript.Function
fun SprudelPattern.s(name: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.sound(name, callInfo)

/** Alias for [sound] on a string pattern. */
@KlangScript.Function
fun String.s(name: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).s(name, callInfo)

/**
 * Alias for [sound]. Creates a sound pattern.
 *
 * @param name The sound/sample name pattern in mini-notation, e.g. `"bd sd hh"`.
 * @param-tool name SprudelSampleEditor, SprudelSampleSequenceEditor
 */
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
@KlangScript.Function
fun SprudelPattern.bank(name: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyBank(this, listOfNotNull(name).asSprudelDslArgs(callInfo))

/** Sets the sample bank on a string pattern. */
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
@KlangScript.Function
fun bank(name: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.bank(name, callInfo) }

/** Chains a bank operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.bank(name: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bank(name, callInfo) }

// -- legato() / clip() ------------------------------------------------------------------------------------------------

private val legatoUpdate: SprudelVoiceData.(Double?) -> SprudelVoiceData = { amount ->
    copy(legato = amount)
}

private fun applyLegato(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.legato }, update = legatoUpdate)
    }

    return source._liftOrReinterpretNumericalField(args, legatoUpdate)
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
@KlangScript.Function
fun SprudelPattern.legato(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyLegato(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/** Sets the legato (duration scaling) factor on a string pattern. */
@KlangScript.Function
fun String.legato(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).legato(amount, callInfo)

/**
 * Returns a [PatternMapperFn] for `legato(...)`.
 *
 * Kotlin door only: the script reaches this through `legato(...)`, which is [Legato.invoke].
 */
fun legato(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.legato(amount, callInfo) }

/**
 * The legato (duration scaling) of each event, as a value other setters can read.
 *
 * Bare `legato` reads what the chain has set so far, so it comes after whatever set the field
 * (`legato(...)` or an alias). Call it, `legato(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * Aliases: `clip`.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3 a3").legato(0.8).legato(mul("1 1.5 1 0.5"))               // long, longer, long, short
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").legato("0.5 1.5").release(legato.mul(0.2))                 // release follows legato
 * ```
 *
 * @category tonal
 * @tags legato, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("legato")
object Legato : FieldAccessor({ it.legato }) {

    /**
     * Returns a [PatternMapperFn] that sets the legato factor for each event.
     * When called with no argument, reinterprets the current event value as a legato amount.
     *
     * ```KlangScript(Playable)
     * note("c3 e3").apply(legato(1.5))   // mapper form
     * ```
     */
    @KlangScript.Method(name = "invoke")
    operator fun invoke(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        legato(amount, callInfo)
}

/** The [Legato] accessor as a value, so the Kotlin door reads like the script. */
val legato: Legato = Legato

/** Chains a legato operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.legato(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.legato(amount, callInfo) }

/** Alias for [legato] on this pattern. Sets the duration scaling factor. */
@KlangScript.Function
fun SprudelPattern.clip(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.legato(amount, callInfo)

/** Alias for [legato] on a string pattern. */
@KlangScript.Function
fun String.clip(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).legato(amount, callInfo)

/** Kotlin door only: alias of [legato]; the script reaches it through `clip(...)`. */
fun clip(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.legato(amount, callInfo) }

/**
 * Alias of [legato]: the same accessor under another name.
 *
 * @category tonal
 * @tags clip, legato, accessor
 */
@KlangScript.Constant
val clip: Legato = Legato

/** Chains a clip operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.clip(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.legato(amount, callInfo) }

// -- vibrato() --------------------------------------------------------------------------------------------------------

private val vibratoUpdate: SprudelVoiceData.(Double?) -> SprudelVoiceData = { hz ->
    clone().also { it.vibrato = hz }
}

private fun applyVibrato(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.vibrato }, update = vibratoUpdate)
    }

    return source._liftOrReinterpretNumericalField(args, vibratoUpdate)
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
@KlangScript.Function
fun SprudelPattern.vibrato(hz: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyVibrato(this, listOfNotNull(hz).asSprudelDslArgs(callInfo))

/** Sets the vibrato frequency (speed) in Hz on a string pattern. */
@KlangScript.Function
fun String.vibrato(hz: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).vibrato(hz, callInfo)

/**
 * Returns a [PatternMapperFn] for `vibrato(...)`.
 *
 * Kotlin door only: the script reaches this through `vibrato(...)`, which is [Vibrato.invoke].
 */
fun vibrato(hz: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.vibrato(hz, callInfo) }

/**
 * The vibrato rate of each event in Hz, as a value other setters can read.
 *
 * Bare `vibrato` reads what the chain has set so far, so it comes after whatever set the field
 * (`vibrato(...)` or an alias). Call it, `vibrato(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * Aliases: `vib`.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").vibrato(5).vibratoMod(0.3).vibrato(mul("1 1.5"))           // the second note wobbles faster
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4 e4").vibrato("4 7").vibratoMod(vibrato.div(20))               // faster vibrato, deeper too
 * ```
 *
 * @category tonal
 * @tags vibrato, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("vibrato")
object Vibrato : FieldAccessor({ it.vibrato }) {

    /**
     * Returns a [PatternMapperFn] that sets the vibrato frequency in Hz.
     * When called with no argument, reinterprets the current event value as a vibrato rate.
     *
     * ```KlangScript(Playable)
     * note("c4").apply(vibrato(5))   // mapper form
     * ```
     */
    @KlangScript.Method(name = "invoke")
    operator fun invoke(hz: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        vibrato(hz, callInfo)
}

/** The [Vibrato] accessor as a value, so the Kotlin door reads like the script. */
val vibrato: Vibrato = Vibrato

/** Chains a vibrato operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.vibrato(hz: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.vibrato(hz, callInfo) }

/** Alias for [vibrato] on this pattern. Sets the vibrato frequency in Hz. */
@KlangScript.Function
fun SprudelPattern.vib(hz: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.vibrato(hz, callInfo)

/** Alias for [vibrato] on a string pattern. */
@KlangScript.Function
fun String.vib(hz: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).vibrato(hz, callInfo)

/** Kotlin door only: alias of [vibrato]; the script reaches it through `vib(...)`. */
fun vib(hz: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.vibrato(hz, callInfo) }

/**
 * Alias of [vibrato]: the same accessor under another name.
 *
 * @category tonal
 * @tags vib, vibrato, accessor
 */
@KlangScript.Constant
val vib: Vibrato = Vibrato

/** Chains a vib operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.vib(hz: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.vibrato(hz, callInfo) }

// -- vibratoMod() -----------------------------------------------------------------------------------------------------

private val vibratoModUpdate: SprudelVoiceData.(Double?) -> SprudelVoiceData = { semitones ->
    clone().also { it.vibratoMod = semitones }
}

private fun applyVibratoMod(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.vibratoMod }, update = vibratoModUpdate)
    }

    return source._liftOrReinterpretNumericalField(args, vibratoModUpdate)
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
 * @param semitones Vibrato depth in SEMITONES. 0.0 = no vibrato, 0.2 = subtle,
 *   0.5 = standard, 1.0+ = wide wobble. Default: 0.0. Typical range: 0.1–2.0.
 * @category tonal
 * @tags vibratoMod, vibrato depth, pitch modulation
 */
@KlangScript.Function
fun SprudelPattern.vibratoMod(semitones: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyVibratoMod(this, listOfNotNull(semitones).asSprudelDslArgs(callInfo))

/** Sets the vibrato depth on a string pattern. */
@KlangScript.Function
fun String.vibratoMod(semitones: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).vibratoMod(semitones, callInfo)

/**
 * Returns a [PatternMapperFn] for `vibratoMod(...)`.
 *
 * Kotlin door only: the script reaches this through `vibratoMod(...)`, which is [VibratoMod.invoke].
 */
fun vibratoMod(semitones: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.vibratoMod(semitones, callInfo) }

/**
 * The vibrato depth of each event in semitones, as a value other setters can read.
 *
 * Bare `vibratoMod` reads what the chain has set so far, so it comes after whatever set the field
 * (`vibratoMod(...)` or an alias). Call it, `vibratoMod(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").vibrato(5).vibratoMod(0.3).vibratoMod(mul("1 2"))          // the second note wobbles wider
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4 e4").vibrato(5).vibratoMod("0.2 0.6").pan(vibratoMod)         // deeper vibrato further right
 * ```
 *
 * @category tonal
 * @tags vibratoMod, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("vibratoMod")
object VibratoMod : FieldAccessor({ it.vibratoMod }) {

    /**
     * Returns a [PatternMapperFn] that sets the vibrato depth in semitones.
     * When called with no argument, reinterprets the current event value as a vibrato depth.
     *
     * ```KlangScript(Playable)
     * note("c4").apply(vibratoMod(0.5))   // mapper form
     * ```
     */
    @KlangScript.Method(name = "invoke")
    operator fun invoke(semitones: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        vibratoMod(semitones, callInfo)
}

/** The [VibratoMod] accessor as a value, so the Kotlin door reads like the script. */
val vibratoMod: VibratoMod = VibratoMod

/** Chains a vibratoMod operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.vibratoMod(semitones: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.vibratoMod(semitones, callInfo) }

// -- pattack() / patt() -----------------------------------------------------------------------------------------------

private val pattackUpdate: SprudelVoiceData.(Double?) -> SprudelVoiceData = { seconds -> clone().also { it.pAttack = seconds } }

private fun applyPAttack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.pAttack }, update = pattackUpdate)
    }

    return source._liftOrReinterpretNumericalField(args, pattackUpdate)
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
@KlangScript.Function
fun SprudelPattern.pattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyPAttack(this, listOfNotNull(seconds).asSprudelDslArgs(callInfo))

/** Sets the pitch envelope attack time on a string pattern. */
@KlangScript.Function
fun String.pattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pattack(seconds, callInfo)

/**
 * Returns a [PatternMapperFn] for `pattack(...)`.
 *
 * Kotlin door only: the script reaches this through `pattack(...)`, which is [Pattack.invoke].
 */
fun pattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.pattack(seconds, callInfo) }

/**
 * The pitch envelope attack of each event, as a value other setters can read.
 *
 * Bare `pattack` reads what the chain has set so far, so it comes after whatever set the field
 * (`pattack(...)` or an alias). Call it, `pattack(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * Aliases: `patt`.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").penv(12).pattack(0.1).pattack(mul("1 3"))                  // the second note rises slower
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4 e4").penv(12).pattack("0.05 0.2").pdecay(pattack)             // decay follows attack
 * ```
 *
 * @category tonal
 * @tags pattack, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("pattack")
object Pattack : FieldAccessor({ it.pAttack }) {

    /**
     * Returns a [PatternMapperFn] that sets the pitch envelope attack time.
     *
     * ```KlangScript(Playable)
     * note("c4").apply(pattack(0.1))   // mapper form
     * ```
     */
    @KlangScript.Method(name = "invoke")
    operator fun invoke(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        pattack(seconds, callInfo)
}

/** The [Pattack] accessor as a value, so the Kotlin door reads like the script. */
val pattack: Pattack = Pattack

/** Chains a pattack operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.pattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pattack(seconds, callInfo) }

/** Alias for [pattack] on this pattern. */
@KlangScript.Function
fun SprudelPattern.patt(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.pattack(seconds, callInfo)

/** Alias for [pattack] on a string pattern. */
@KlangScript.Function
fun String.patt(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pattack(seconds, callInfo)

/** Kotlin door only: alias of [pattack]; the script reaches it through `patt(...)`. */
fun patt(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.pattack(seconds, callInfo) }

/**
 * Alias of [pattack]: the same accessor under another name.
 *
 * @category tonal
 * @tags patt, pattack, accessor
 */
@KlangScript.Constant
val patt: Pattack = Pattack

/** Chains a patt operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.patt(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pattack(seconds, callInfo) }

// -- pdecay() / pdec() ------------------------------------------------------------------------------------------------

private val pdecayUpdate: SprudelVoiceData.(Double?) -> SprudelVoiceData = { seconds -> clone().also { it.pDecay = seconds } }

private fun applyPDecay(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.pDecay }, update = pdecayUpdate)
    }

    return source._liftOrReinterpretNumericalField(args, pdecayUpdate)
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
@KlangScript.Function
fun SprudelPattern.pdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyPDecay(this, listOfNotNull(seconds).asSprudelDslArgs(callInfo))

/** Sets the pitch envelope decay time on a string pattern. */
@KlangScript.Function
fun String.pdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pdecay(seconds, callInfo)

/**
 * Returns a [PatternMapperFn] for `pdecay(...)`.
 *
 * Kotlin door only: the script reaches this through `pdecay(...)`, which is [Pdecay.invoke].
 */
fun pdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.pdecay(seconds, callInfo) }

/**
 * The pitch envelope decay of each event, as a value other setters can read.
 *
 * Bare `pdecay` reads what the chain has set so far, so it comes after whatever set the field
 * (`pdecay(...)` or an alias). Call it, `pdecay(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * Aliases: `pdec`.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").penv(12).pdecay(0.2).pdecay(mul("1 2"))                    // the second note falls slower
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4 e4").penv(12).pdecay("0.1 0.4").prelease(pdecay)              // release follows decay
 * ```
 *
 * @category tonal
 * @tags pdecay, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("pdecay")
object Pdecay : FieldAccessor({ it.pDecay }) {

    /**
     * Returns a [PatternMapperFn] that sets the pitch envelope decay time.
     *
     * ```KlangScript(Playable)
     * note("c4").apply(pdecay(0.2))   // mapper form
     * ```
     */
    @KlangScript.Method(name = "invoke")
    operator fun invoke(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        pdecay(seconds, callInfo)
}

/** The [Pdecay] accessor as a value, so the Kotlin door reads like the script. */
val pdecay: Pdecay = Pdecay

/** Chains a pdecay operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.pdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pdecay(seconds, callInfo) }

/** Alias for [pdecay] on this pattern. */
@KlangScript.Function
fun SprudelPattern.pdec(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.pdecay(seconds, callInfo)

/** Alias for [pdecay] on a string pattern. */
@KlangScript.Function
fun String.pdec(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pdecay(seconds, callInfo)

/** Kotlin door only: alias of [pdecay]; the script reaches it through `pdec(...)`. */
fun pdec(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.pdecay(seconds, callInfo) }

/**
 * Alias of [pdecay]: the same accessor under another name.
 *
 * @category tonal
 * @tags pdec, pdecay, accessor
 */
@KlangScript.Constant
val pdec: Pdecay = Pdecay

/** Chains a pdec operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.pdec(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pdecay(seconds, callInfo) }

// -- prelease() / prel() ----------------------------------------------------------------------------------------------

private val preleaseUpdate: SprudelVoiceData.(Double?) -> SprudelVoiceData = { seconds -> clone().also { it.pRelease = seconds } }

private fun applyPRelease(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.pRelease }, update = preleaseUpdate)
    }

    return source._liftOrReinterpretNumericalField(args, preleaseUpdate)
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
@KlangScript.Function
fun SprudelPattern.prelease(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyPRelease(this, listOfNotNull(seconds).asSprudelDslArgs(callInfo))

/** Sets the pitch envelope release time on a string pattern. */
@KlangScript.Function
fun String.prelease(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).prelease(seconds, callInfo)

/**
 * Returns a [PatternMapperFn] for `prelease(...)`.
 *
 * Kotlin door only: the script reaches this through `prelease(...)`, which is [Prelease.invoke].
 */
fun prelease(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.prelease(seconds, callInfo) }

/**
 * The pitch envelope release of each event, as a value other setters can read.
 *
 * Bare `prelease` reads what the chain has set so far, so it comes after whatever set the field
 * (`prelease(...)` or an alias). Call it, `prelease(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * Aliases: `prel`.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").penv(12).prelease(0.3).prelease(mul("1 2"))                // the second note lets go slower
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4 e4").penv(12).prelease("0.1 0.4").pattack(prelease)           // attack follows release
 * ```
 *
 * @category tonal
 * @tags prelease, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("prelease")
object Prelease : FieldAccessor({ it.pRelease }) {

    /**
     * Returns a [PatternMapperFn] that sets the pitch envelope release time.
     *
     * ```KlangScript(Playable)
     * note("c4").apply(prelease(0.3))   // mapper form
     * ```
     */
    @KlangScript.Method(name = "invoke")
    operator fun invoke(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        prelease(seconds, callInfo)
}

/** The [Prelease] accessor as a value, so the Kotlin door reads like the script. */
val prelease: Prelease = Prelease

/** Chains a prelease operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.prelease(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.prelease(seconds, callInfo) }

/** Alias for [prelease] on this pattern. */
@KlangScript.Function
fun SprudelPattern.prel(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.prelease(seconds, callInfo)

/** Alias for [prelease] on a string pattern. */
@KlangScript.Function
fun String.prel(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).prelease(seconds, callInfo)

/** Kotlin door only: alias of [prelease]; the script reaches it through `prel(...)`. */
fun prel(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.prelease(seconds, callInfo) }

/**
 * Alias of [prelease]: the same accessor under another name.
 *
 * @category tonal
 * @tags prel, prelease, accessor
 */
@KlangScript.Constant
val prel: Prelease = Prelease

/** Chains a prel operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.prel(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.prelease(seconds, callInfo) }

// -- penv() / pamt() --------------------------------------------------------------------------------------------------

private val penvUpdate: SprudelVoiceData.(Double?) -> SprudelVoiceData = { semitones -> clone().also { it.pEnv = semitones } }

private fun applyPEnv(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.pEnv }, update = penvUpdate)
    }

    return source._liftOrReinterpretNumericalField(args, penvUpdate)
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
@KlangScript.Function
fun SprudelPattern.penv(semitones: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyPEnv(this, listOfNotNull(semitones).asSprudelDslArgs(callInfo))

/** Sets the pitch envelope depth (in semitones) on a string pattern. */
@KlangScript.Function
fun String.penv(semitones: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).penv(semitones, callInfo)

/**
 * Returns a [PatternMapperFn] for `penv(...)`.
 *
 * Kotlin door only: the script reaches this through `penv(...)`, which is [Penv.invoke].
 */
fun penv(semitones: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.penv(semitones, callInfo) }

/**
 * The pitch envelope depth of each event in semitones, as a value other setters can read.
 *
 * Bare `penv` reads what the chain has set so far, so it comes after whatever set the field
 * (`penv(...)` or an alias). Call it, `penv(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * Aliases: `pamt`.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").pattack(0.1).penv(12).penv(mul("1 -1"))                    // up on the first, down on the second
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4 e4").pattack(0.1).penv("7 12").vibratoMod(penv.div(24))       // deeper sweep, wider vibrato
 * ```
 *
 * @category tonal
 * @tags penv, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("penv")
object Penv : FieldAccessor({ it.pEnv }) {

    /**
     * Returns a [PatternMapperFn] that sets the pitch envelope depth in semitones.
     *
     * ```KlangScript(Playable)
     * note("c4").apply(penv(12))   // mapper form
     * ```
     */
    @KlangScript.Method(name = "invoke")
    operator fun invoke(semitones: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        penv(semitones, callInfo)
}

/** The [Penv] accessor as a value, so the Kotlin door reads like the script. */
val penv: Penv = Penv

/** Chains a penv operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.penv(semitones: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.penv(semitones, callInfo) }

/** Alias for [penv] on this pattern. */
@KlangScript.Function
fun SprudelPattern.pamt(semitones: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.penv(semitones, callInfo)

/** Alias for [penv] on a string pattern. */
@KlangScript.Function
fun String.pamt(semitones: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).penv(semitones, callInfo)

/** Kotlin door only: alias of [penv]; the script reaches it through `pamt(...)`. */
fun pamt(semitones: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.penv(semitones, callInfo) }

/**
 * Alias of [penv]: the same accessor under another name.
 *
 * @category tonal
 * @tags pamt, penv, accessor
 */
@KlangScript.Constant
val pamt: Penv = Penv

/** Chains a pamt operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.pamt(semitones: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.penv(semitones, callInfo) }

// -- pcurve() / pcrv() ------------------------------------------------------------------------------------------------

private val pcurveUpdate: SprudelVoiceData.(Double?) -> SprudelVoiceData = { curve -> clone().also { it.pCurve = curve } }

private fun applyPCurve(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.pCurve }, update = pcurveUpdate)
    }

    return source._liftOrReinterpretNumericalField(args, pcurveUpdate)
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
@KlangScript.Function
fun SprudelPattern.pcurve(curve: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyPCurve(this, listOfNotNull(curve).asSprudelDslArgs(callInfo))

/** Sets the pitch envelope curve shape on a string pattern. */
@KlangScript.Function
fun String.pcurve(curve: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pcurve(curve, callInfo)

/**
 * Returns a [PatternMapperFn] for `pcurve(...)`.
 *
 * Kotlin door only: the script reaches this through `pcurve(...)`, which is [Pcurve.invoke].
 */
fun pcurve(curve: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.pcurve(curve, callInfo) }

/**
 * The pitch envelope curve of each event, as a value other setters can read. Reserved: the
 * engine renders the pitch envelope linearly for now, the value travels but changes nothing.
 *
 * Bare `pcurve` reads what the chain has set so far, so it comes after whatever set the field
 * (`pcurve(...)` or an alias). Call it, `pcurve(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * Aliases: `pcrv`.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").penv(12).pattack(0.2).pcurve(1).pcurve(mul("1 2"))         // reserved, inaudible for now
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4 e4").penv(12).pattack(0.2).pcurve("1 2").panchor(pcurve.sub(1))   // the anchor follows a reserved value
 * ```
 *
 * @category tonal
 * @tags pcurve, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("pcurve")
object Pcurve : FieldAccessor({ it.pCurve }) {

    /**
     * Returns a [PatternMapperFn] that sets the pitch envelope curve shape.
     *
     * ```KlangScript(Playable)
     * note("c4").apply(pcurve(2))   // mapper form
     * ```
     */
    @KlangScript.Method(name = "invoke")
    operator fun invoke(curve: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        pcurve(curve, callInfo)
}

/** The [Pcurve] accessor as a value, so the Kotlin door reads like the script. */
val pcurve: Pcurve = Pcurve

/** Chains a pcurve operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.pcurve(curve: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pcurve(curve, callInfo) }

/** Alias for [pcurve] on this pattern. */
@KlangScript.Function
fun SprudelPattern.pcrv(curve: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.pcurve(curve, callInfo)

/** Alias for [pcurve] on a string pattern. */
@KlangScript.Function
fun String.pcrv(curve: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pcurve(curve, callInfo)

/** Kotlin door only: alias of [pcurve]; the script reaches it through `pcrv(...)`. */
fun pcrv(curve: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.pcurve(curve, callInfo) }

/**
 * Alias of [pcurve]: the same accessor under another name.
 *
 * @category tonal
 * @tags pcrv, pcurve, accessor
 */
@KlangScript.Constant
val pcrv: Pcurve = Pcurve

/** Chains a pcrv operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.pcrv(curve: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pcurve(curve, callInfo) }

// -- panchor() / panc() -----------------------------------------------------------------------------------------------

private val panchorUpdate: SprudelVoiceData.(Double?) -> SprudelVoiceData = { anchor -> clone().also { it.pAnchor = anchor } }

private fun applyPAnchor(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.pAnchor }, update = panchorUpdate)
    }

    return source._liftOrReinterpretNumericalField(args, panchorUpdate)
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
@KlangScript.Function
fun SprudelPattern.panchor(anchor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyPAnchor(this, listOfNotNull(anchor).asSprudelDslArgs(callInfo))

/** Sets the pitch envelope anchor point on a string pattern. */
@KlangScript.Function
fun String.panchor(anchor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).panchor(anchor, callInfo)

/**
 * Returns a [PatternMapperFn] for `panchor(...)`.
 *
 * Kotlin door only: the script reaches this through `panchor(...)`, which is [Panchor.invoke].
 */
fun panchor(anchor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.panchor(anchor, callInfo) }

/**
 * The pitch envelope anchor of each event (the sustain pitch offset: 0 returns to the note, 1
 * holds the full sweep), as a value other setters can read.
 *
 * Bare `panchor` reads what the chain has set so far, so it comes after whatever set the field
 * (`panchor(...)` or an alias). Call it, `panchor(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * Aliases: `panc`.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").penv(12).pattack(0.2).panchor(0).panchor(add("0 1"))      // back to the note, then holds the full sweep
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4 e4").penv(12).pattack(0.2).panchor("0 1").pan(panchor)        // anchor across the stereo field
 * ```
 *
 * @category tonal
 * @tags panchor, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("panchor")
object Panchor : FieldAccessor({ it.pAnchor }) {

    /**
     * Returns a [PatternMapperFn] that sets the pitch envelope anchor point.
     *
     * ```KlangScript(Playable)
     * note("c4").apply(panchor(0))   // mapper form
     * ```
     */
    @KlangScript.Method(name = "invoke")
    operator fun invoke(anchor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        panchor(anchor, callInfo)
}

/** The [Panchor] accessor as a value, so the Kotlin door reads like the script. */
val panchor: Panchor = Panchor

/** Chains a panchor operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.panchor(anchor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.panchor(anchor, callInfo) }

/** Alias for [panchor] on this pattern. */
@KlangScript.Function
fun SprudelPattern.panc(anchor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.panchor(anchor, callInfo)

/** Alias for [panchor] on a string pattern. */
@KlangScript.Function
fun String.panc(anchor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).panchor(anchor, callInfo)

/** Kotlin door only: alias of [panchor]; the script reaches it through `panc(...)`. */
fun panc(anchor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.panchor(anchor, callInfo) }

/**
 * Alias of [panchor]: the same accessor under another name.
 *
 * @category tonal
 * @tags panc, panchor, accessor
 */
@KlangScript.Constant
val panc: Panchor = Panchor

/** Chains a panc operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.panc(anchor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.panchor(anchor, callInfo) }

// -- accelerate() -----------------------------------------------------------------------------------------------------

private val accelerateUpdate: SprudelVoiceData.(Double?) -> SprudelVoiceData = { semitones ->
    clone().also { it.accelerate = semitones }
}

private fun applyAccelerate(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.accelerate }, update = accelerateUpdate)
    }

    return source._liftOrReinterpretNumericalField(args, accelerateUpdate)
}

/**
 * Sets the playback acceleration (pitch ramp) for each event, in SEMITONES over the event's
 * duration: `accelerate(12)` glides one octave up, `accelerate(-12)` one octave down.
 *
 * Controls a continuous pitch change during sample playback. Useful for pitched percussion
 * or sweep effects. When called with no argument, reinterprets the current event value as
 * the semitone amount. (Unit changed from octaves to semitones in the pitch-param
 * unification, 2026-08-24 — old scripts' values are 12× subtler now.)
 *
 * ```KlangScript(Playable)
 * s("cr").accelerate(24)             // crash pitches two octaves up during playback
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh").accelerate("<0 -24 24>")   // alternate: no ramp, down, up per cycle
 * ```
 *
 * @param semitones Pitch bend over the voice's duration in SEMITONES. 0.0 = no bend, +12 = one octave up, -12 = one octave down. Default: 0.0. Typical range: -24 to 24.
 *
 * @category tonal
 * @tags accelerate, pitch ramp, pitch bend, playback speed
 */
@KlangScript.Function
fun SprudelPattern.accelerate(semitones: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyAccelerate(this, listOfNotNull(semitones).asSprudelDslArgs(callInfo))

/** Sets the playback acceleration on a string pattern. */
@KlangScript.Function
fun String.accelerate(semitones: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).accelerate(semitones, callInfo)

/**
 * Returns a [PatternMapperFn] for `accelerate(...)`.
 *
 * Kotlin door only: the script reaches this through `accelerate(...)`, which is [Accelerate.invoke].
 */
fun accelerate(semitones: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.accelerate(semitones, callInfo) }

/**
 * The pitch ramp of each event in semitones, as a value other setters can read.
 *
 * Bare `accelerate` reads what the chain has set so far, so it comes after whatever set the field
 * (`accelerate(...)` or an alias). Call it, `accelerate(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * ```KlangScript(Playable)
 * s("bd*4").accelerate(-2).accelerate(mul("1 2 1 4"))                       // deeper drops on every second hit
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4 e4").accelerate("2 -2").pan(accelerate.mul(0.25).add(0.5))    // up goes right, down goes left
 * ```
 *
 * @category tonal
 * @tags accelerate, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("accelerate")
object Accelerate : FieldAccessor({ it.accelerate }) {

    /**
     * Returns a [PatternMapperFn] that sets the playback acceleration (pitch ramp).
     * When called with no argument, reinterprets the current event value as an acceleration amount.
     *
     * ```KlangScript(Playable)
     * s("hh").apply(accelerate(24))  // mapper form
     * ```
     */
    @KlangScript.Method(name = "invoke")
    operator fun invoke(semitones: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        accelerate(semitones, callInfo)
}

/** The [Accelerate] accessor as a value, so the Kotlin door reads like the script. */
val accelerate: Accelerate = Accelerate

/** Chains an accelerate operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.accelerate(semitones: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.accelerate(semitones, callInfo) }

// -- transpose() ------------------------------------------------------------------------------------------------------

/**
 * Applies transposition logic to a SprudelVoiceData instance.
 * Accepts either a numeric value (semitones) or a string (interval name).
 */
fun SprudelVoiceData.transpose(amount: Any?): SprudelVoiceData {
    val semitones: Int
    val intervalName: String

    when (amount) {
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
@KlangScript.Function
fun SprudelPattern.transpose(amount: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyTranspose(this, listOf(amount).asSprudelDslArgs(callInfo))

/** Transposes a string pattern by a number of semitones or interval name. */
@KlangScript.Function
fun String.transpose(amount: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).transpose(amount, callInfo)

/** Returns a [PatternMapperFn] that transposes each event by the given semitones or interval name. */
@KlangScript.Function
fun transpose(amount: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.transpose(amount, callInfo) }

/** Chains a transpose step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.transpose(amount: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.transpose(amount, callInfo) }

// -- freq() -----------------------------------------------------------------------------------------------------------

private val freqUpdate: SprudelVoiceData.(Double?) -> SprudelVoiceData = { v -> copy(freqHz = v) }

private fun applyFreq(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.freqHz }, update = freqUpdate)
    }

    return source._liftOrReinterpretNumericalField(args, freqUpdate)
}

/**
 * The frequency of each event, as a value other setters can read.
 *
 * Bare `freq` reads the frequency `note()` (or `freq(hz)`) has set so far in the chain; it must
 * therefore come AFTER the note in the chain. Call it, `freq(hz)`, to set the frequency.
 *
 * ```KlangScript(Playable)
 * note("c e g a").bpf(freq).sound("pink").bpq(2.0)   // the wind whistles the melody
 * ```
 *
 * ```KlangScript(Playable)
 * note("c e g a").bpf(freq.mul(2))                     // bandpass one octave above the note
 * ```
 *
 * @category tonal
 * @tags freq, frequency, Hz, pitch, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("freq")
object Freq : FieldAccessor({ it.freqHz }) {

    /**
     * Sets the playback frequency in Hz directly, bypassing note name resolution.
     *
     * Overrides the computed frequency for each event. Useful for precise tuning or
     * microtonal work where standard note names are insufficient. When called with no argument,
     * reinterprets the current event value as a frequency in Hz. A mapper argument is applied to
     * the frequency itself.
     *
     * ```KlangScript(Playable)
     * "440 550 660".freq()         // A4, roughly C#5, roughly E5 by raw Hz
     * ```
     *
     * ```KlangScript(Playable)
     * note("c4 e4").freq(432)      // force all events to 432 Hz
     * ```
     *
     * ```KlangScript(Playable)
     * note("a c e").freq(mul(perlin.seg(4).range(0.95, 1.05)))   // a novice violin player's intonation
     * ```
     *
     * @param hz Frequency in Hz. Directly sets the pitch, bypassing note name resolution. 440 = A4, 261.63 = C4. Default: determined by note(). Range: 20 to 20000. A mapper (`mul(2)`, `add(50)`) is applied to the current frequency.
     */
    @KlangScript.Method(name = "invoke")
    operator fun invoke(hz: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        freq(hz, callInfo)
}

/** The [Freq] accessor as a value, so the Kotlin door reads like the script: `bpf(freq)`. */
val freq: Freq = Freq

/**
 * Returns a [PatternMapperFn] that sets the playback frequency in Hz.
 *
 * Kotlin door only: the script reaches this through `freq(hz)`, which is [Freq.invoke] on the
 * `freq` object.
 *
 * @param hz Frequency in Hz, a control pattern, or a mapper applied to the current frequency.
 */
fun freq(hz: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.freq(hz, callInfo) }

/** Sets the playback frequency in Hz on this pattern. */
@KlangScript.Function
fun SprudelPattern.freq(hz: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyFreq(this, listOfNotNull(hz).asSprudelDslArgs(callInfo))

/** Sets the playback frequency in Hz on a string pattern. */
@KlangScript.Function
fun String.freq(hz: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).freq(hz, callInfo)

/** Chains a freq step onto this [PatternMapperFn]. */
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
@KlangScript.Function
fun SprudelPattern.scaleTranspose(steps: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyScaleTranspose(this, listOf(steps).asSprudelDslArgs(callInfo))

/** Transposes a string pattern by a number of scale degrees within the active scale. */
@KlangScript.Function
fun String.scaleTranspose(steps: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).scaleTranspose(steps, callInfo)

/** Returns a [PatternMapperFn] that transposes each event by the given number of scale degrees. */
@KlangScript.Function
fun scaleTranspose(steps: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.scaleTranspose(steps, callInfo) }

/** Chains a scaleTranspose step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.scaleTranspose(steps: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.scaleTranspose(steps, callInfo) }

// -- chord() ----------------------------------------------------------------------------------------------------------

private val chordMutation = voiceSetter { chordName ->
    val name = chordName?.toString() ?: return@voiceSetter

    // Set the chord property
    // We also set the note to the chord root/tonic to ensure it plays something meaningful if voicing is not used
    // and to provide a base for rootNotes()
    val chordObj = Chord.get(name)
    val root = if (!chordObj.empty) chordObj.tonic ?: chordObj.root else null

    chord = name
    if (root != null) {
        note = root
        freqHz = Tones.noteToFreq(root)
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
@KlangScript.Function
fun chord(name: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyChordCreate(listOf(name).asSprudelDslArgs(callInfo))

/** Sets the chord name on this pattern for use with [voicing] and [rootNotes]. */
@KlangScript.Function
fun SprudelPattern.chord(name: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyChordExtension(this, listOf(name).asSprudelDslArgs(callInfo))

/** Sets the chord name on a string pattern. */
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
@KlangScript.Function
fun SprudelPattern.rootNotes(octave: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyRootNotes(this, listOfNotNull(octave).asSprudelDslArgs(callInfo))

/** Extracts root notes from chord events in a string pattern, forcing to the given octave. */
@KlangScript.Function
fun String.rootNotes(octave: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).rootNotes(octave, callInfo)

/** Returns a [PatternMapperFn] that extracts root notes from chord events, forcing to the given octave. */
@KlangScript.Function
fun rootNotes(octave: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.rootNotes(octave, callInfo) }

/** Chains a rootNotes step (with forced octave) onto this [PatternMapperFn]. */
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
        override val numSteps: Double? get() = source.numSteps
        override fun estimateCycleDuration(): Double = source.estimateCycleDuration()

        override fun queryArcContextual(
            from: CycleTime,
            to: CycleTime,
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

/** Converts a [value] [PatternLike] argument into a control [SprudelPattern], or null if no rank is given. */
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
@KlangScript.Function
fun PatternMapperFn.voicing(
    rank: PatternLike? = null,
    low: PatternLike? = null,
    high: PatternLike? = null,
    callInfo: CallInfo? = null,
): PatternMapperFn =
    this.chain { p -> p.voicing(rank, low, high, callInfo) }
