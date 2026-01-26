@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.StrudelVoiceValue
import io.peekandpoke.klang.strudel.pattern.ControlPattern
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
fun StrudelVoiceData.resolveNote(newIndex: Int? = null): StrudelVoiceData {
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

private fun applyScale(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return if (args.isEmpty()) {
        // TODO: test this
        source.reinterpretVoice {
            it.scaleMutation(it.value?.asString).resolveNote()
        }
    } else {
        source.applyControlFromParams(args, scaleMutation) { src, ctrl ->
            src.copy(scale = ctrl.scale).resolveNote()
        }
    }
}

@StrudelDsl
val scale by dslFunction { args, /* callInfo */ _ -> args.toPattern(scaleMutation) }

@StrudelDsl
val StrudelPattern.scale by dslPatternExtension { p, args, /* callInfo */ _ -> applyScale(p, args) }

@StrudelDsl
val String.scale by dslStringExtension { p, args, callInfo -> p.scale(args, callInfo) }

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

private fun applyNote(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return if (args.isEmpty()) {
        // TODO: test this
        source.reinterpretVoice {
            it.resolveNote().copy(soundIndex = null, value = null)
        }
    } else {
        source.applyControlFromParams(args, noteMutation) { src, ctrl ->
            src.noteMutation(
                ctrl.note ?: ctrl.value?.asString
            )
        }
    }
}

/** Creates a pattern with notes */
@StrudelDsl
val note by dslFunction { args, /* callInfo */ _ -> args.toPattern(noteMutation).note() }

/** Modifies the notes of a pattern */
@StrudelDsl
val StrudelPattern.note by dslPatternExtension { p, args, /* callInfo */ _ -> applyNote(p, args) }

/** Modifies the notes of a pattern defined by a string */
@StrudelDsl
val String.note by dslStringExtension { p, args, callInfo -> p.note(args, callInfo) }

// -- n() --------------------------------------------------------------------------------------------------------------

private val nMutation = voiceModifier {
    copy(
        soundIndex = it?.asIntOrNull() ?: soundIndex,
        gain = gain ?: 1.0,
    )
}

private fun applyN(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return if (args.isEmpty()) {
        // TODO: test this
        source.reinterpretVoice {
            it.copy(
                soundIndex = it.soundIndex ?: it.value?.asInt,
                gain = it.gain ?: 1.0,
                value = null,
            )
        }
    } else {
        source.applyControlFromParams(args, nMutation) { src, ctrl ->
            src.nMutation(
                ctrl.soundIndex ?: ctrl.value?.asInt
            )
        }
    }
}

/** Sets the note number or sample index */
@StrudelDsl
val n by dslFunction { args, /* callInfo */ _ -> args.toPattern(nMutation).n() }

/** Sets the note number or sample index */
@StrudelDsl
val StrudelPattern.n by dslPatternExtension { p, args, /* callInfo */ _ -> applyN(p, args) }

/** Sets the note number or sample index on a string pattern */
@StrudelDsl
val String.n by dslStringExtension { p, args, callInfo -> p.n(args, callInfo) }

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

private fun applySound(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return if (args.isEmpty()) {
        // TODO: test this
        source.reinterpretVoice {
            it.soundMutation(it.value?.asString)
        }
    } else {
        source.applyControlFromParams(args, soundMutation) { src, ctrl ->
            src.copy(
                sound = ctrl.sound ?: src.sound,
                soundIndex = ctrl.soundIndex ?: src.soundIndex,
//                gain = ctrl.gain ?: src.gain,
            )
        }
    }
}

/** Modifies the sounds of a pattern */
@StrudelDsl
val StrudelPattern.sound by dslPatternExtension { p, args, /* callInfo */ _ -> applySound(p, args) }

/** Creates a pattern with sounds */
@StrudelDsl
val sound by dslFunction { args, /* callInfo */ _ -> args.toPattern(soundMutation) }

/** Modifies the sounds of a pattern defined by a string */
@StrudelDsl
val String.sound by dslStringExtension { p, args, callInfo -> p.sound(args, callInfo) }

/** Alias for [sound] */
@StrudelDsl
val StrudelPattern.s by dslPatternExtension { p, args, callInfo -> p.sound(args, callInfo) }

/** Alias for [sound] */
@StrudelDsl
val s by dslFunction { args, callInfo -> sound(args, callInfo) }

/** Alias for [sound] on a string */
@StrudelDsl
val String.s by dslStringExtension { p, args, callInfo -> p.sound(args, callInfo) }

// -- bank() -----------------------------------------------------------------------------------------------------------

private val bankMutation = voiceModifier {
    copy(bank = it?.toString())
}

private fun applyBank(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return if (args.isEmpty()) {
        // TODO: test this
        source.reinterpretVoice {
            it.bankMutation(it.value?.asString)
        }
    } else {
        source.applyControlFromParams(args, bankMutation) { src, ctrl ->
            src.bankMutation(ctrl.bank ?: src.bank)
        }
    }
}

/** Modifies the banks of a pattern */
@StrudelDsl
val StrudelPattern.bank by dslPatternExtension { p, args, /* callInfo */ _ -> applyBank(p, args) }

/** Creates a pattern with banks */
@StrudelDsl
val bank by dslFunction { args, /* callInfo */ _ -> args.toPattern(bankMutation) }

/** Modifies the banks of a pattern defined by a string */
@StrudelDsl
val String.bank by dslStringExtension { p, args, callInfo -> p.bank(args, callInfo) }

// -- legato() / clip() ------------------------------------------------------------------------------------------------

private val legatoMutation = voiceModifier {
    copy(legato = it?.asDoubleOrNull())
}

private fun applyLegato(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = legatoMutation,
        getValue = { legato },
        setValue = { v, _ -> copy(legato = v) },
    )
}

/** Creates a pattern with legatos */
@StrudelDsl
val legato by dslFunction { args, /* callInfo */ _ -> args.toPattern(legatoMutation) }

/** Modifies the legatos of a pattern */
@StrudelDsl
val StrudelPattern.legato by dslPatternExtension { p, args, /* callInfo */ _ -> applyLegato(p, args) }

/** Modifies the legatos of a pattern defined by a string */
@StrudelDsl
val String.legato by dslStringExtension { p, args, callInfo -> p.legato(args, callInfo) }

/** Alias for [legato] */
@StrudelDsl
val clip by dslFunction { args, callInfo -> legato(args, callInfo) }

/** Alias for [legato] */
@StrudelDsl
val StrudelPattern.clip by dslPatternExtension { p, args, callInfo -> p.legato(args, callInfo) }

/** Alias for [legato] on a string */
@StrudelDsl
val String.clip by dslStringExtension { p, args, callInfo -> p.legato(args, callInfo) }

// -- vibrato() --------------------------------------------------------------------------------------------------------

private val vibratoMutation = voiceModifier {
    copy(vibrato = it?.asDoubleOrNull())
}

private fun applyVibrato(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = vibratoMutation,
        getValue = { vibrato },
        setValue = { v, _ -> copy(vibrato = v) },
    )
}

/** Sets the vibrato frequency (speed) in Hz. */
@StrudelDsl
val vibrato by dslFunction { args, /* callInfo */ _ -> args.toPattern(vibratoMutation) }


/** Sets the vibrato frequency (speed) in Hz. */
@StrudelDsl
val StrudelPattern.vibrato by dslPatternExtension { p, args, /* callInfo */ _ -> applyVibrato(p, args) }

/** Sets the vibrato frequency (speed) in Hz on a string. */
@StrudelDsl
val String.vibrato by dslStringExtension { p, args, callInfo -> p.vibrato(args, callInfo) }

/** Alias for [vibrato] */
@StrudelDsl
val vib by dslFunction { args, /* callInfo */ _ -> args.toPattern(vibratoMutation) }

/** Alias for [vibrato] */
@StrudelDsl
val StrudelPattern.vib by dslPatternExtension { p, args, callInfo -> p.vibrato(args, callInfo) }

/** Alias for [vibrato] on a string */
@StrudelDsl
val String.vib by dslStringExtension { p, args, callInfo -> p.vibrato(args, callInfo) }

// -- vibratoMod() -----------------------------------------------------------------------------------------------------

private val vibratoModMutation = voiceModifier {
    copy(vibratoMod = it?.asDoubleOrNull())
}

private fun applyVibratoMod(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = vibratoModMutation,
        getValue = { vibratoMod },
        setValue = { v, _ -> copy(vibratoMod = v) },
    )
}

/** Sets the vibratoMod depth (amplitude). */
@StrudelDsl
val vibratoMod by dslFunction { args, /* callInfo */ _ -> args.toPattern(vibratoModMutation) }

/** Sets the vibratoMod depth (amplitude). */
@StrudelDsl
val StrudelPattern.vibratoMod by dslPatternExtension { p, args, /* callInfo */ _ -> applyVibratoMod(p, args) }

/** Sets the vibratoMod depth (amplitude) on a string. */
@StrudelDsl
val String.vibratoMod by dslStringExtension { p, args, callInfo -> p.vibratoMod(args, callInfo) }

/** Alias for [vibratoMod] */
@StrudelDsl
val vibmod by dslFunction { args, callInfo -> vibratoMod(args, callInfo) }

/** Alias for [vibratoMod] */
@StrudelDsl
val StrudelPattern.vibmod by dslPatternExtension { p, args, callInfo -> p.vibratoMod(args, callInfo) }


/** Alias for [vibratoMod] on a string */
@StrudelDsl
val String.vibmod by dslStringExtension { p, args, callInfo -> p.vibratoMod(args, callInfo) }

// -- accelerate() -----------------------------------------------------------------------------------------------------

private val accelerateMutation = voiceModifier {
    copy(accelerate = it?.asDoubleOrNull())
}

private fun applyAccelerate(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = accelerateMutation,
        getValue = { accelerate },
        setValue = { v, _ -> copy(accelerate = v) },
    )
}

@StrudelDsl
val accelerate by dslFunction { args, /* callInfo */ _ -> args.toPattern(accelerateMutation) }

@StrudelDsl
val StrudelPattern.accelerate by dslPatternExtension { p, args, /* callInfo */ _ -> applyAccelerate(p, args) }

@StrudelDsl
val String.accelerate by dslStringExtension { p, args, callInfo -> p.accelerate(args, callInfo) }

// -- transpose() ------------------------------------------------------------------------------------------------------

/**
 * Applies transposition logic to a StrudelVoiceData instance.
 * Accepts either a numeric value (semitones) or a string (interval name).
 */
fun StrudelVoiceData.transpose(amount: Any?): StrudelVoiceData {
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

        is StrudelVoiceValue -> return transpose(amount.asDouble ?: amount.asString)

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

private fun applyTranspose(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    // We use defaultModifier for args because we just want the 'value'
    val controlPattern = args.toPattern(defaultModifier)

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
 * Top-level transpose function.
 */
@StrudelDsl
val transpose by dslFunction { args, /* callInfo */ _ ->
    val source = args.lastOrNull()?.value as? StrudelPattern

    if (args.size >= 2 && source != null) {
        applyTranspose(source, args.dropLast(1))
    } else {
        // When used as a source (e.g. transpose(12)), it creates a pattern of values
        args.toPattern(defaultModifier)
    }
}

/** Transposes the pattern by a number of semitones */
@StrudelDsl
val StrudelPattern.transpose by dslPatternExtension { p, args, /* callInfo */ _ -> applyTranspose(p, args) }

/** Transposes a pattern defined by a string */
@StrudelDsl
val String.transpose by dslStringExtension { p, args, callInfo -> p.transpose(args, callInfo) }
