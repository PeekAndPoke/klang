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
import io.peekandpoke.klang.sprudel.SprudelVoiceValue
import io.peekandpoke.klang.sprudel._liftOrReinterpretStringField
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.ControlPattern
import io.peekandpoke.klang.tones.Tones
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
