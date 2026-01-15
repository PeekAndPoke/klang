@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.pattern.ReinterpretPattern.Companion.reinterpretVoice
import io.peekandpoke.klang.tones.Tones
import io.peekandpoke.klang.tones.distance.Distance
import io.peekandpoke.klang.tones.interval.Interval
import io.peekandpoke.klang.tones.midi.Midi
import io.peekandpoke.klang.tones.scale.Scale
import kotlin.math.pow

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangTonalInit = false

/** Cleans up the scale name */
fun String.cleanScaleName() = replace(":", " ")

/** Capitalizes the first character of the string */
fun String.ucFirst() = replaceFirstChar { it.uppercaseChar() }

/**
 * Resolves the note and frequency based on the index and the current scale.
 *
 * @param newIndex An optional new index to force (e.g. from n("0")).
 *                 If null, it tries to use existing soundIndex or interpret value as index.
 */
fun VoiceData.resolveNote(newIndex: Int? = null): VoiceData {
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
            gain = gain ?: 1.0,
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
    val fallbackNote = (note ?: value?.asString)?.ucFirst()

    return copy(
        note = fallbackNote,
        freqHz = Tones.noteToFreq(fallbackNote ?: ""),
        gain = gain ?: 1.0,
        soundIndex = n ?: soundIndex,
    )
}

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Note / Sound / Tonal
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

    return source.applyControlFromParams(args, scaleMutation) { src, ctrl ->
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
    input?.toString()?.let { newNote ->
        copy(
            note = newNote,
            freqHz = Tones.noteToFreq(newNote),
            gain = gain ?: 1.0,
        )
    } ?: this
}

private fun applyNote(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return if (args.isEmpty()) {
        source.reinterpretVoice { it.resolveNote() }
    } else {
        source.applyControlFromParams(args, noteMutation) { src, ctrl -> src.noteMutation(ctrl.note) }
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
    copy(
        soundIndex = it?.asIntOrNull() ?: soundIndex,
        gain = gain ?: 1.0,
    )
}

private fun applyN(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return if (args.isEmpty()) {
        source.reinterpretVoice { it.resolveNote() }
    } else {
        source.applyControlFromParams(args, nMutation) { src, ctrl -> src.resolveNote(ctrl.soundIndex) }
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
        // Preserve existing index if the string doesn't specify one.
        soundIndex = split.getOrNull(1)?.toIntOrNull() ?: soundIndex,
        // Preserve existing gain if the string doesn't specify one.
        gain = split.getOrNull(2)?.toDoubleOrNull() ?: gain ?: 1.0,
    )
}

private fun applySound(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return if (args.isEmpty()) {
        source.reinterpretVoice { it.soundMutation(it.value?.asString) }
    } else {
        source.applyControlFromParams(args, soundMutation) { src, ctrl ->
            src.copy(
                sound = ctrl.sound ?: src.sound,
                soundIndex = ctrl.soundIndex ?: src.soundIndex,
                gain = ctrl.gain ?: src.gain,
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
    return source.applyControlFromParams(args, bankMutation) { src, ctrl -> src.bankMutation(ctrl.bank) }
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
    return source.applyNumericalParam(
        args = args,
        modify = legatoMutation,
        getValue = { legato },
        setValue = { v, _ -> copy(legato = v) },
    )
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
    return source.applyNumericalParam(
        args = args,
        modify = vibratoMutation,
        getValue = { vibrato },
        setValue = { v, _ -> copy(vibrato = v) },
    )
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
    return source.applyNumericalParam(
        args = args,
        modify = vibratoModMutation,
        getValue = { vibratoMod },
        setValue = { v, _ -> copy(vibratoMod = v) },
    )
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
    return source.applyNumericalParam(
        args = args,
        modify = accelerateMutation,
        getValue = { accelerate },
        setValue = { v, _ -> copy(accelerate = v) },
    )
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

// -- transpose() ------------------------------------------------------------------------------------------------------

/**
 * Applies transposition logic to a VoiceData instance.
 * Accepts either a numeric value (semitones) or a string (interval name).
 */
fun VoiceData.transpose(amount: Any?): VoiceData {
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

        is io.peekandpoke.klang.audio_bridge.VoiceValue -> return transpose(amount.asDouble ?: amount.asString)
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
                gain = gain ?: 1.0,
            )
        }
    }

    // Strategy 2: Frequency shifting (Physics)
    // Fallback if we only have frequency or an invalid note name.
    val currentFreq = freqHz ?: Tones.noteToFreq(currentNoteName)
    if (currentFreq <= 0.0) {
        return this.copy(
            value = null, // clear the value ... it was consumed
            gain = gain ?: 1.0,
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
        gain = gain ?: 1.0,
    )
}

private fun applyTranspose(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    // We use defaultModifier for args because we just want the 'value'
    val controlPattern = args.toPattern(defaultModifier)

    return io.peekandpoke.klang.strudel.pattern.ControlPattern(
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

/** Transposes the pattern by a number of semitones */
@StrudelDsl
val StrudelPattern.transpose by dslPatternExtension { p, args ->
    applyTranspose(p, args)
}

/** Transposes a pattern defined by a string */
@StrudelDsl
val String.transpose by dslStringExtension { p, args ->
    applyTranspose(p, args)
}

/**
 * Top-level transpose function.
 */
@StrudelDsl
val transpose by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyTranspose(source, args.dropLast(1))
    } else {
        // When used as a source (e.g. transpose(12)), it creates a pattern of values
        args.toPattern(defaultModifier)
    }
}
