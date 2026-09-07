/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions", "ClassName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel._applyControlFromParams
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel._mapNumericField
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.ReinterpretPattern.Companion.reinterpretVoice
import io.peekandpoke.klang.tones.Tones
import io.peekandpoke.klang.tones.scale.Scale

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
 * note("c3 e3").legato("0.5 1.5").adsr(release = legato.mul(0.2))                 // release follows legato
 * ```
 *
 * @category tonal
 * @tags legato, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("legato")
object legato : FieldAccessor({ it.legato }) {

    /**
     * Returns a [PatternMapperFn] that sets the legato factor for each event.
     * When called with no argument, reinterprets the current event value as a legato amount.
     *
     * ```KlangScript(Playable)
     * note("c3 e3").apply(legato(1.5))   // mapper form
     * ```
     */
    @KlangScript.Invoke
    operator fun invoke(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.legato(amount, callInfo) }
}

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

/**
 * Alias of [legato]: the same accessor under another name.
 *
 * @category tonal
 * @tags clip, legato, accessor
 */
@KlangScript.Constant
val clip: legato = legato

/** Chains a clip operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.clip(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.legato(amount, callInfo) }

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
 * note("c e g a").bpf(freq).sound("pink").bpf(q = 2.0)   // the wind whistles the melody
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
object freq : FieldAccessor({ it.freqHz }) {

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
    @KlangScript.Invoke
    operator fun invoke(hz: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.freq(hz, callInfo) }
}

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
