@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.*
import io.peekandpoke.klang.strudel.pattern.AtomicPattern
import io.peekandpoke.klang.strudel.pattern.BindPattern
import io.peekandpoke.klang.strudel.pattern.ControlPattern
import io.peekandpoke.klang.strudel.pattern.ReinterpretPattern.Companion.reinterpretVoice
import io.peekandpoke.klang.strudel.pattern.StackPattern
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
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangTonalInit = false

/** Cleans up the scale name */
fun String.cleanScaleName() = replace(":", " ")

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
    val fallbackNote = note ?: value?.asString

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
        source._applyControlFromParams(args, scaleMutation) { src, ctrl ->
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
        source._applyControlFromParams(args, noteMutation) { src, ctrl ->
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
        source._applyControlFromParams(args, nMutation) { src, ctrl ->
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
        source._applyControlFromParams(args, soundMutation) { src, ctrl ->
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
        source._applyControlFromParams(args, bankMutation) { src, ctrl ->
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
    return source._liftNumericField(args, legatoMutation)
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
    return source._liftNumericField(args, vibratoMutation)
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
    return source._liftNumericField(args, vibratoModMutation)
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

// -- pattack() / patt() -----------------------------------------------------------------------------------------------

private val pAttackMutation = voiceModifier {
    copy(pAttack = it?.asDoubleOrNull())
}

private fun applyPAttack(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, pAttackMutation)
}

/** Sets the pitch envelope attack time. */
@StrudelDsl
val pattack by dslFunction { args, /* callInfo */ _ -> args.toPattern(pAttackMutation) }

/** Sets the pitch envelope attack time. */
@StrudelDsl
val StrudelPattern.pattack by dslPatternExtension { p, args, /* callInfo */ _ -> applyPAttack(p, args) }

/** Sets the pitch envelope attack time on a string. */
@StrudelDsl
val String.pattack by dslStringExtension { p, args, _ -> applyPAttack(p, args) }

/** Alias for [pattack] */
@StrudelDsl
val StrudelPattern.patt by dslPatternExtension { p, args, callInfo -> p.pattack(args, callInfo) }

/** Alias for [pattack] */
@StrudelDsl
val patt by dslFunction { args, callInfo -> pattack(args, callInfo) }

/** Alias for [pattack] on a string */
@StrudelDsl
val String.patt by dslStringExtension { p, args, callInfo -> p.pattack(args, callInfo) }

// -- pdecay() / pdec() ------------------------------------------------------------------------------------------------

private val pDecayMutation = voiceModifier {
    copy(pDecay = it?.asDoubleOrNull())
}

private fun applyPDecay(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, pDecayMutation)
}

/** Sets the pitch envelope decay time. */
@StrudelDsl
val pdecay by dslFunction { args, /* callInfo */ _ -> args.toPattern(pDecayMutation) }

/** Sets the pitch envelope decay time. */
@StrudelDsl
val StrudelPattern.pdecay by dslPatternExtension { p, args, /* callInfo */ _ -> applyPDecay(p, args) }

/** Sets the pitch envelope decay time on a string. */
@StrudelDsl
val String.pdecay by dslStringExtension { p, args, _ -> applyPDecay(p, args) }

/** Alias for [pdecay] */
@StrudelDsl
val StrudelPattern.pdec by dslPatternExtension { p, args, callInfo -> p.pdecay(args, callInfo) }

/** Alias for [pdecay] */
@StrudelDsl
val pdec by dslFunction { args, callInfo -> pdecay(args, callInfo) }

/** Alias for [pdecay] on a string */
@StrudelDsl
val String.pdec by dslStringExtension { p, args, callInfo -> p.pdecay(args, callInfo) }

// -- prelease() / prel() ----------------------------------------------------------------------------------------------

private val pReleaseMutation = voiceModifier {
    copy(pRelease = it?.asDoubleOrNull())
}

private fun applyPRelease(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, pReleaseMutation)
}

/** Sets the pitch envelope release time. */
@StrudelDsl
val prelease by dslFunction { args, /* callInfo */ _ -> args.toPattern(pReleaseMutation) }

/** Sets the pitch envelope release time. */
@StrudelDsl
val StrudelPattern.prelease by dslPatternExtension { p, args, /* callInfo */ _ -> applyPRelease(p, args) }

/** Sets the pitch envelope release time on a string. */
@StrudelDsl
val String.prelease by dslStringExtension { p, args, _ -> applyPRelease(p, args) }

/** Alias for [prelease] */
@StrudelDsl
val StrudelPattern.prel by dslPatternExtension { p, args, callInfo -> p.prelease(args, callInfo) }

/** Alias for [prelease] */
@StrudelDsl
val prel by dslFunction { args, callInfo -> prelease(args, callInfo) }

/** Alias for [prelease] on a string */
@StrudelDsl
val String.prel by dslStringExtension { p, args, callInfo -> p.prelease(args, callInfo) }

// -- penv() / pamt() --------------------------------------------------------------------------------------------------

private val pEnvMutation = voiceModifier {
    copy(pEnv = it?.asDoubleOrNull())
}

private fun applyPEnv(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, pEnvMutation)
}

/** Sets the pitch envelope depth/amount (in semitones). */
@StrudelDsl
val penv by dslFunction { args, /* callInfo */ _ -> args.toPattern(pEnvMutation) }

/** Sets the pitch envelope depth/amount (in semitones). */
@StrudelDsl
val StrudelPattern.penv by dslPatternExtension { p, args, /* callInfo */ _ -> applyPEnv(p, args) }

/** Sets the pitch envelope depth/amount (in semitones) on a string. */
@StrudelDsl
val String.penv by dslStringExtension { p, args, _ -> applyPEnv(p, args) }

/** Alias for [penv] */
@StrudelDsl
val StrudelPattern.pamt by dslPatternExtension { p, args, callInfo -> p.penv(args, callInfo) }

/** Alias for [penv] */
@StrudelDsl
val pamt by dslFunction { args, callInfo -> penv(args, callInfo) }

/** Alias for [penv] on a string */
@StrudelDsl
val String.pamt by dslStringExtension { p, args, callInfo -> p.penv(args, callInfo) }

// -- pcurve() / pcrv() ------------------------------------------------------------------------------------------------

private val pCurveMutation = voiceModifier {
    copy(pCurve = it?.asDoubleOrNull())
}

private fun applyPCurve(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, pCurveMutation)
}

/** Sets the pitch envelope curve shape. */
@StrudelDsl
val pcurve by dslFunction { args, /* callInfo */ _ -> args.toPattern(pCurveMutation) }

/** Sets the pitch envelope curve shape. */
@StrudelDsl
val StrudelPattern.pcurve by dslPatternExtension { p, args, /* callInfo */ _ -> applyPCurve(p, args) }

/** Sets the pitch envelope curve shape on a string. */
@StrudelDsl
val String.pcurve by dslStringExtension { p, args, _ -> applyPCurve(p, args) }

/** Alias for [pcurve] */
@StrudelDsl
val StrudelPattern.pcrv by dslPatternExtension { p, args, callInfo -> p.pcurve(args, callInfo) }

/** Alias for [pcurve] */
@StrudelDsl
val pcrv by dslFunction { args, callInfo -> pcurve(args, callInfo) }

/** Alias for [pcurve] on a string */
@StrudelDsl
val String.pcrv by dslStringExtension { p, args, callInfo -> p.pcurve(args, callInfo) }

// -- panchor() / panc() -----------------------------------------------------------------------------------------------

private val pAnchorMutation = voiceModifier {
    copy(pAnchor = it?.asDoubleOrNull())
}

private fun applyPAnchor(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, pAnchorMutation)
}

/** Sets the pitch envelope anchor point. */
@StrudelDsl
val panchor by dslFunction { args, /* callInfo */ _ -> args.toPattern(pAnchorMutation) }

/** Sets the pitch envelope anchor point. */
@StrudelDsl
val StrudelPattern.panchor by dslPatternExtension { p, args, /* callInfo */ _ -> applyPAnchor(p, args) }

/** Sets the pitch envelope anchor point on a string. */
@StrudelDsl
val String.panchor by dslStringExtension { p, args, _ -> applyPAnchor(p, args) }

/** Alias for [panchor] */
@StrudelDsl
val StrudelPattern.panc by dslPatternExtension { p, args, callInfo -> p.panchor(args, callInfo) }

/** Alias for [panchor] */
@StrudelDsl
val panc by dslFunction { args, callInfo -> panchor(args, callInfo) }

/** Alias for [panchor] on a string */
@StrudelDsl
val String.panc by dslStringExtension { p, args, callInfo -> p.panchor(args, callInfo) }

// -- accelerate() -----------------------------------------------------------------------------------------------------

private val accelerateMutation = voiceModifier {
    copy(accelerate = it?.asDoubleOrNull())
}

private fun applyAccelerate(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, accelerateMutation)
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
 * Top-level transpose function.
 */
@StrudelDsl
val transpose by dslFunction { args, /* callInfo */ _ ->
    val source = args.lastOrNull()?.value as? StrudelPattern

    if (args.size >= 2 && source != null) {
        applyTranspose(source, args.dropLast(1))
    } else {
        // When used as a source (e.g. transpose(12)), it creates a pattern of values
        args.toPattern(voiceValueModifier)
    }
}

/** Transposes the pattern by a number of semitones */
@StrudelDsl
val StrudelPattern.transpose by dslPatternExtension { p, args, /* callInfo */ _ -> applyTranspose(p, args) }

/** Transposes a pattern defined by a string */
@StrudelDsl
val String.transpose by dslStringExtension { p, args, callInfo -> p.transpose(args, callInfo) }

// -- freq() -----------------------------------------------------------------------------------------------------------

private val freqMutation = voiceModifier {
    copy(freqHz = it?.asDoubleOrNull())
}

private fun applyFreq(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, freqMutation)
}

/** Sets the frequency in Hz */
@StrudelDsl
val freq by dslFunction { args, /* callInfo */ _ -> args.toPattern(freqMutation) }

/** Sets the frequency in Hz */
@StrudelDsl
val StrudelPattern.freq by dslPatternExtension { p, args, /* callInfo */ _ -> applyFreq(p, args) }

/** Sets the frequency in Hz on a string */
@StrudelDsl
val String.freq by dslStringExtension { p, args, callInfo -> p.freq(args, callInfo) }

// -- scaleTranspose() -------------------------------------------------------------------------------------------------

/**
 * Transposes notes by a number of scale degrees within the active scale.
 * If no scale is set, falls back to chromatic transposition.
 */
fun StrudelVoiceData.scaleTranspose(steps: Int): StrudelVoiceData {
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
            gain = gain ?: 1.0,
        )
    } catch (_: Exception) {
        // On any error, fallback to chromatic transposition
        return transpose(steps)
    }
}

private fun applyScaleTranspose(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
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

/** Transposes by scale degrees within the active scale */
@StrudelDsl
val scaleTranspose by dslFunction { args, _ ->
    val source = args.lastOrNull()?.value as? StrudelPattern

    if (args.size >= 2 && source != null) {
        applyScaleTranspose(source, args.dropLast(1))
    } else {
        // If used as a source pattern (e.g. scaleTranspose(1)), it acts as a value pattern
        // But usually it's used as a modifier.
        // If args are just numbers, we can return a pattern of numbers?
        // Or better, return a modifier pattern that will be applied later?
        // The current tests seem to expect it to be used as .scaleTranspose(...)

        // If called as `scaleTranspose(1)`, we return a pattern of 1s.
        // This allows `n("0").scaleTranspose(1)` to work via `ControlPattern` mechanism if `n` supports it?
        // But `scaleTranspose` is an extension on `StrudelPattern`.

        args.toPattern(voiceValueModifier)
    }
}

/** Transposes by scale degrees within the active scale */
@StrudelDsl
val StrudelPattern.scaleTranspose by dslPatternExtension { p, args, _ -> applyScaleTranspose(p, args) }

/** Transposes by scale degrees within the active scale */
@StrudelDsl
val String.scaleTranspose by dslStringExtension { p, args, callInfo -> p.scaleTranspose(args, callInfo) }

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

/** Creates a pattern of chords */
@StrudelDsl
val chord by dslFunction { args, _ ->
    args.toPattern(chordMutation)
}

/** Applies chord expansion to a pattern */
@StrudelDsl
val StrudelPattern.chord by dslPatternExtension { p, args, _ ->
    p._applyControlFromParams(args, chordMutation) { src, ctrl ->
        src.chordMutation(ctrl.chord ?: ctrl.value?.asString)
    }
}

/** Applies chord expansion to a string pattern */
@StrudelDsl
val String.chord by dslStringExtension { p, args, callInfo -> p.chord(args, callInfo) }

// -- rootNotes() ------------------------------------------------------------------------------------------------------

/**
 * Extracts the root note from a chord pattern.
 * If octave is specified, forces the root to that octave.
 */
fun StrudelVoiceData.extractRootNote(octave: Int? = null): StrudelVoiceData {
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
            gain = gain ?: 1.0,
        )
    } catch (_: Exception) {
        return this
    }
}

private fun applyRootNotes(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val octave = args.firstOrNull()?.value?.asIntOrNull()

    // No need for distinct() anymore since chord() doesn't expand
    return source.reinterpretVoice { voiceData ->
        voiceData.extractRootNote(octave)
    }
}

/** Extracts root notes from chord patterns */
@StrudelDsl
val rootNotes by dslFunction { args, _ ->
    // When used standalone, just returns a pattern that will extract roots when applied
    args.toPattern(voiceValueModifier)
}

/** Extracts root notes from chord patterns */
@StrudelDsl
val StrudelPattern.rootNotes by dslPatternExtension { p, args, _ -> applyRootNotes(p, args) }

/** Extracts root notes from chord patterns */
@StrudelDsl
val String.rootNotes by dslStringExtension { p, args, callInfo -> p.rootNotes(args, callInfo) }

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
private fun applyVoicing(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
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

/** Applies voice leading to chord patterns */
@StrudelDsl
val voicing by dslFunction { args, _ ->
    // When used standalone, creates a modifier pattern
    args.toPattern(voiceValueModifier)
}

/** Applies voice leading to chord patterns */
@StrudelDsl
val StrudelPattern.voicing by dslPatternExtension { p, args, _ -> applyVoicing(p, args) }

/** Applies voice leading to chord patterns */
@StrudelDsl
val String.voicing by dslStringExtension { p, args, callInfo -> p.voicing(args, callInfo) }
