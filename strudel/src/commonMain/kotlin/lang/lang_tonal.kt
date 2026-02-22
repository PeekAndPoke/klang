@file:Suppress("DuplicatedCode", "ObjectPropertyName")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.*
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs
import io.peekandpoke.klang.strudel.math.Rational
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

private val scaleMutation = voiceModifier { scale ->
    val newScale = scale?.toString()?.cleanScaleName()
    copy(scale = newScale).resolveNote()
}

fun applyScale(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
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

internal val _scale by dslFunction { args, /* callInfo */ _ -> args.toPattern(scaleMutation) }

internal val StrudelPattern._scale by dslPatternExtension { p, args, /* callInfo */ _ -> applyScale(p, args) }

internal val String._scale by dslStringExtension { p, args, callInfo -> p._scale(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the musical scale context for resolving note indices to note names.
 *
 * When a scale is set, numeric values passed to [n] are resolved against the scale's note
 * list using `Scale.steps()`. Scale names use the format `"root:mode"` or `"root mode"`,
 * e.g. `"c4:major"` or `"c4 minor"`. If no scale is set, numeric indices map to semitones.
 *
 * ```KlangScript
 * n("0 1 2 3").scale("c4:major").note()          // C4, D4, E4, F4
 * ```
 *
 * ```KlangScript
 * n("0 2 4").scale("<c4:major a3:minor>").note()  // alternates scale per cycle
 * ```
 *
 * @category tonal
 * @tags scale, pitch, musical scale, mode, tuning
 */
@StrudelDsl
fun scale(name: PatternLike): StrudelPattern = _scale(listOf(name).asStrudelDslArgs())

/** Applies scale context to this pattern; numeric event values are resolved to scale notes. */
@StrudelDsl
fun StrudelPattern.scale(name: PatternLike): StrudelPattern = this._scale(listOf(name).asStrudelDslArgs())

/** Applies scale context to a string pattern; numeric values are resolved to scale notes. */
@StrudelDsl
fun String.scale(name: PatternLike): StrudelPattern = this._scale(listOf(name).asStrudelDslArgs())

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

fun applyNote(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
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

internal val _note by dslFunction { args, /* callInfo */ _ -> args.toPattern(noteMutation).note() }

internal val StrudelPattern._note by dslPatternExtension { p, args, /* callInfo */ _ -> applyNote(p, args) }

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
 * @category tonal
 * @tags note, pitch, frequency, MIDI, note name
 */
@StrudelDsl
fun note(vararg note: PatternLike): StrudelPattern = _note(note.toList().asStrudelDslArgs())

/** Reinterprets the current value of this pattern as a note name. */
@StrudelDsl
fun StrudelPattern.note(): StrudelPattern = this._note(emptyList())

/** Applies note values from arguments (or reinterprets current value as a note name). */
@StrudelDsl
fun StrudelPattern.note(noteName: PatternLike): StrudelPattern = this._note(listOf(noteName).asStrudelDslArgs())

/** Applies note values to a string pattern. */
@StrudelDsl
fun String.note(noteName: PatternLike): StrudelPattern = this._note(listOf(noteName).asStrudelDslArgs())

// -- n() --------------------------------------------------------------------------------------------------------------

private val nMutation = voiceModifier {
    copy(
        soundIndex = it?.asIntOrNull() ?: soundIndex,
        value = null,
    )
}

fun applyN(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
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

internal val _n by dslFunction { args, /* callInfo */ _ -> args.toPattern(nMutation).n() }

internal val StrudelPattern._n by dslPatternExtension { p, args, /* callInfo */ _ -> applyN(p, args) }

internal val String._n by dslStringExtension { p, args, callInfo -> p._n(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the sound index on this pattern.
 *
 * When param [n] is null, the sequence values will be reinterpreted as sound index.
 *
 * ```KlangScript
 * n("0 2 4").scale("c4:major").note()   // indices 0, 2, 4 → C4, E4, G4
 * ```
 *
 * ```KlangScript
 * s("hh").n("0 1 2")                    // selects different hh samples by index
 * ```
 *
 * @param n The sound index to set, or null to reparse sequence values as sound index.
 *
 * @category tonal
 * @tags n, note number, sample index, pitch index
 */
@StrudelDsl
fun StrudelPattern.n(index: PatternLike? = null): StrudelPattern =
    this._n(listOfNotNull(index).asStrudelDslArgs())

/** Sets the sound index on this string pattern. */
@StrudelDsl
fun String.n(index: PatternLike): StrudelPattern = this._n(listOf(index).asStrudelDslArgs())

/**
 * Creates a pattern of sound indices.
 */
@StrudelDsl
fun n(index: PatternLike): StrudelPattern = _n(listOf(index).asStrudelDslArgs())

// -- sound() / s() ----------------------------------------------------------------------------------------------------

private val soundMutation = voiceModifier {
    val split = it?.toString()?.split(":") ?: emptyList()

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

fun applySound(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
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

internal val StrudelPattern._sound by dslPatternExtension { p, args, /* callInfo */ _ -> applySound(p, args) }

internal val _sound by dslFunction { args, /* callInfo */ _ -> args.toPattern(soundMutation) }

internal val String._sound by dslStringExtension { p, args, callInfo -> p._sound(args, callInfo) }

internal val StrudelPattern._s by dslPatternExtension { p, args, callInfo -> p._sound(args, callInfo) }

internal val _s by dslFunction { args, callInfo -> _sound(args, callInfo) }

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
 * @alias s
 * @category tonal
 * @tags sound, sample, instrument, s
 */
@StrudelDsl
fun StrudelPattern.sound(name: PatternLike): StrudelPattern = this._sound(listOf(name).asStrudelDslArgs())

/**
 * Reinterprets sequence values as sounds.
 *
 * ```KlangScript
 * seq("bd hh sd hh").sound()  // interprets the sequence values as sounds
 * ```
 */
@StrudelDsl
fun StrudelPattern.sound(): StrudelPattern = this._sound(emptyList())

/** Modifies the sounds of a string pattern. */
@StrudelDsl
fun String.sound(name: PatternLike): StrudelPattern = this._sound(listOf(name).asStrudelDslArgs())

/** Reinterprets sequence values as sounds. */
@StrudelDsl
fun String.sound(): StrudelPattern = this._sound(emptyList())

/** Creates a pattern of sounds */
@StrudelDsl
fun sound(name: PatternLike): StrudelPattern = _sound(listOf(name).asStrudelDslArgs())

/** Alias for [sound]. Sets the sound/instrument on this pattern. */
@StrudelDsl
fun StrudelPattern.s(name: PatternLike): StrudelPattern = this._s(listOf(name).asStrudelDslArgs())

/** Alias for [sound]. Reinterprets sequence values as sounds. */
@StrudelDsl
fun StrudelPattern.s(): StrudelPattern = this._s(emptyList())

/** Alias for [sound] on a string pattern. */
@StrudelDsl
fun String.s(name: PatternLike): StrudelPattern = this._s(listOf(name).asStrudelDslArgs())

/** Alias for [sound] on a string pattern. */
@StrudelDsl
fun String.s(): StrudelPattern = this._s(emptyList())

/** Alias for [sound]. Creates a sound pattern. */
@StrudelDsl
fun s(name: PatternLike): StrudelPattern = _s(listOf(name).asStrudelDslArgs())


// -- bank() -----------------------------------------------------------------------------------------------------------

private val bankMutation = voiceModifier {
    copy(bank = it?.toString())
}

fun applyBank(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
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

internal val StrudelPattern._bank by dslPatternExtension { p, args, /* callInfo */ _ -> applyBank(p, args) }

internal val _bank by dslFunction { args, /* callInfo */ _ -> args.toPattern(bankMutation) }

internal val String._bank by dslStringExtension { p, args, callInfo -> p._bank(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the sample bank for each event, overriding which collection of samples is used.
 *
 * The bank determines where samples are loaded from, independently of the sound name.
 * Useful when you want to switch sample collections without changing sound identifiers.
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
@StrudelDsl
fun StrudelPattern.bank(name: PatternLike? = null): StrudelPattern =
    this._bank(listOf(name).asStrudelDslArgs())

/** Sets the sample bank on a string pattern. */
@StrudelDsl
fun String.bank(name: PatternLike): StrudelPattern = this._bank(listOf(name).asStrudelDslArgs())

/** Sets the sample bank on this pattern. */
@StrudelDsl
fun bank(name: PatternLike): StrudelPattern = _bank(listOf(name).asStrudelDslArgs())

// -- legato() / clip() ------------------------------------------------------------------------------------------------

private val legatoMutation = voiceModifier { copy(legato = it?.asDoubleOrNull()) }

fun applyLegato(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, legatoMutation)
}

internal val _legato by dslFunction { args, /* callInfo */ _ -> args.toPattern(legatoMutation) }

internal val StrudelPattern._legato by dslPatternExtension { p, args, /* callInfo */ _ -> applyLegato(p, args) }

internal val String._legato by dslStringExtension { p, args, callInfo -> p._legato(args, callInfo) }

internal val _clip by dslFunction { args, callInfo -> _legato(args, callInfo) }

internal val StrudelPattern._clip by dslPatternExtension { p, args, callInfo -> p._legato(args, callInfo) }

internal val String._clip by dslStringExtension { p, args, callInfo -> p._legato(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the legato (duration scaling) factor for events in this pattern.
 *
 * A legato of `1.0` fills the full event duration; values above `1.0` create overlapping
 * notes (true legato), while values below `1.0` create staccato-like gaps between notes.
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
@StrudelDsl
fun legato(amount: PatternLike): StrudelPattern = _legato(listOf(amount).asStrudelDslArgs())

/** Sets the legato (duration scaling) factor on this pattern. */
@StrudelDsl
fun StrudelPattern.legato(amount: PatternLike): StrudelPattern = this._legato(listOf(amount).asStrudelDslArgs())

/** Sets the legato (duration scaling) factor on a string pattern. */
@StrudelDsl
fun String.legato(amount: PatternLike): StrudelPattern = this._legato(listOf(amount).asStrudelDslArgs())

/** Alias for [legato]. Sets the duration scaling factor. */
@StrudelDsl
fun clip(amount: PatternLike): StrudelPattern = _clip(listOf(amount).asStrudelDslArgs())

/** Alias for [legato]. Sets the duration scaling factor on this pattern. */
@StrudelDsl
fun StrudelPattern.clip(amount: PatternLike): StrudelPattern = this._clip(listOf(amount).asStrudelDslArgs())

/** Alias for [legato] on a string pattern. */
@StrudelDsl
fun String.clip(amount: PatternLike): StrudelPattern = this._clip(listOf(amount).asStrudelDslArgs())

// -- vibrato() --------------------------------------------------------------------------------------------------------

private val vibratoMutation = voiceModifier { copy(vibrato = it?.asDoubleOrNull()) }

fun applyVibrato(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, vibratoMutation)
}

internal val _vibrato by dslFunction { args, /* callInfo */ _ -> args.toPattern(vibratoMutation) }

internal val StrudelPattern._vibrato by dslPatternExtension { p, args, /* callInfo */ _ -> applyVibrato(p, args) }

internal val String._vibrato by dslStringExtension { p, args, callInfo -> p._vibrato(args, callInfo) }

internal val _vib by dslFunction { args, /* callInfo */ _ -> args.toPattern(vibratoMutation) }

internal val StrudelPattern._vib by dslPatternExtension { p, args, callInfo -> p._vibrato(args, callInfo) }

internal val String._vib by dslStringExtension { p, args, callInfo -> p._vibrato(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the vibrato frequency (oscillation speed) in Hz.
 *
 * Vibrato is a periodic pitch modulation applied to a note. Higher values create faster
 * vibrato; lower values create a slower, wider wobble. Use [vibratoMod] to set the depth.
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
@StrudelDsl
fun vibrato(hz: PatternLike): StrudelPattern = _vibrato(listOf(hz).asStrudelDslArgs())

/** Sets the vibrato frequency (speed) in Hz on this pattern. */
@StrudelDsl
fun StrudelPattern.vibrato(hz: PatternLike): StrudelPattern = this._vibrato(listOf(hz).asStrudelDslArgs())

/** Sets the vibrato frequency (speed) in Hz on a string pattern. */
@StrudelDsl
fun String.vibrato(hz: PatternLike): StrudelPattern = this._vibrato(listOf(hz).asStrudelDslArgs())

/** Alias for [vibrato]. Sets vibrato frequency in Hz. */
@StrudelDsl
fun vib(hz: PatternLike): StrudelPattern = _vib(listOf(hz).asStrudelDslArgs())

/** Alias for [vibrato]. Sets vibrato frequency in Hz on this pattern. */
@StrudelDsl
fun StrudelPattern.vib(hz: PatternLike): StrudelPattern = this._vib(listOf(hz).asStrudelDslArgs())

/** Alias for [vibrato] on a string pattern. */
@StrudelDsl
fun String.vib(hz: PatternLike): StrudelPattern = this._vib(listOf(hz).asStrudelDslArgs())

// -- vibratoMod() -----------------------------------------------------------------------------------------------------

private val vibratoModMutation = voiceModifier { copy(vibratoMod = it?.asDoubleOrNull()) }

fun applyVibratoMod(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, vibratoModMutation)
}

internal val _vibratoMod by dslFunction { args, /* callInfo */ _ -> args.toPattern(vibratoModMutation) }

internal val StrudelPattern._vibratoMod by dslPatternExtension { p, args, /* callInfo */ _ -> applyVibratoMod(p, args) }

internal val String._vibratoMod by dslStringExtension { p, args, callInfo -> p._vibratoMod(args, callInfo) }

internal val _vibmod by dslFunction { args, callInfo -> _vibratoMod(args, callInfo) }

internal val StrudelPattern._vibmod by dslPatternExtension { p, args, callInfo -> p._vibratoMod(args, callInfo) }

internal val String._vibmod by dslStringExtension { p, args, callInfo -> p._vibratoMod(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the vibrato depth (amplitude of pitch oscillation) in semitones.
 *
 * Controls how many semitones the vibrato deviates from the base pitch. Higher values
 * create wider, more pronounced pitch wobble. Use [vibrato] to set the speed.
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
@StrudelDsl
fun vibratoMod(depth: PatternLike): StrudelPattern = _vibratoMod(listOf(depth).asStrudelDslArgs())

/** Sets the vibrato depth on this pattern. */
@StrudelDsl
fun StrudelPattern.vibratoMod(depth: PatternLike): StrudelPattern = this._vibratoMod(listOf(depth).asStrudelDslArgs())

/** Sets the vibrato depth on a string pattern. */
@StrudelDsl
fun String.vibratoMod(depth: PatternLike): StrudelPattern = this._vibratoMod(listOf(depth).asStrudelDslArgs())

/** Alias for [vibratoMod]. Sets vibrato depth. */
@StrudelDsl
fun vibmod(depth: PatternLike): StrudelPattern = _vibmod(listOf(depth).asStrudelDslArgs())

/** Alias for [vibratoMod]. Sets vibrato depth on this pattern. */
@StrudelDsl
fun StrudelPattern.vibmod(depth: PatternLike): StrudelPattern = this._vibmod(listOf(depth).asStrudelDslArgs())

/** Alias for [vibratoMod] on a string pattern. */
@StrudelDsl
fun String.vibmod(depth: PatternLike): StrudelPattern = this._vibmod(listOf(depth).asStrudelDslArgs())

// -- pattack() / patt() -----------------------------------------------------------------------------------------------

private val pAttackMutation = voiceModifier { copy(pAttack = it?.asDoubleOrNull()) }

fun applyPAttack(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, pAttackMutation)
}

internal val _pattack by dslFunction { args, /* callInfo */ _ -> args.toPattern(pAttackMutation) }

internal val StrudelPattern._pattack by dslPatternExtension { p, args, /* callInfo */ _ -> applyPAttack(p, args) }

internal val String._pattack by dslStringExtension { p, args, _ -> applyPAttack(p, args) }

internal val StrudelPattern._patt by dslPatternExtension { p, args, callInfo -> p._pattack(args, callInfo) }

internal val _patt by dslFunction { args, callInfo -> _pattack(args, callInfo) }

internal val String._patt by dslStringExtension { p, args, callInfo -> p._pattack(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the pitch envelope attack time in seconds.
 *
 * The pitch envelope shapes how the pitch changes over a note's duration. The attack
 * phase determines how quickly the pitch rises from its anchor to the target pitch.
 * Use with [penv], [pdecay], [prelease], [pcurve], and [panchor].
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
@StrudelDsl
fun pattack(seconds: PatternLike): StrudelPattern = _pattack(listOf(seconds).asStrudelDslArgs())

/** Sets the pitch envelope attack time on this pattern. */
@StrudelDsl
fun StrudelPattern.pattack(seconds: PatternLike): StrudelPattern = this._pattack(listOf(seconds).asStrudelDslArgs())

/** Sets the pitch envelope attack time on a string pattern. */
@StrudelDsl
fun String.pattack(seconds: PatternLike): StrudelPattern = this._pattack(listOf(seconds).asStrudelDslArgs())

/** Alias for [pattack]. Sets pitch envelope attack time on this pattern. */
@StrudelDsl
fun StrudelPattern.patt(seconds: PatternLike): StrudelPattern = this._patt(listOf(seconds).asStrudelDslArgs())

/** Alias for [pattack]. Sets pitch envelope attack time. */
@StrudelDsl
fun patt(seconds: PatternLike): StrudelPattern = _patt(listOf(seconds).asStrudelDslArgs())

/** Alias for [pattack] on a string pattern. */
@StrudelDsl
fun String.patt(seconds: PatternLike): StrudelPattern = this._patt(listOf(seconds).asStrudelDslArgs())

// -- pdecay() / pdec() ------------------------------------------------------------------------------------------------

private val pDecayMutation = voiceModifier { copy(pDecay = it?.asDoubleOrNull()) }

fun applyPDecay(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, pDecayMutation)
}

internal val _pdecay by dslFunction { args, /* callInfo */ _ -> args.toPattern(pDecayMutation) }

internal val StrudelPattern._pdecay by dslPatternExtension { p, args, /* callInfo */ _ -> applyPDecay(p, args) }

internal val String._pdecay by dslStringExtension { p, args, _ -> applyPDecay(p, args) }

internal val StrudelPattern._pdec by dslPatternExtension { p, args, callInfo -> p._pdecay(args, callInfo) }

internal val _pdec by dslFunction { args, callInfo -> _pdecay(args, callInfo) }

internal val String._pdec by dslStringExtension { p, args, callInfo -> p._pdecay(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the pitch envelope decay time in seconds.
 *
 * After the attack phase, the pitch envelope decays towards the sustain level. The decay
 * time determines how quickly this transition happens.
 * Use with [pattack], [penv], [prelease], [pcurve], and [panchor].
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
@StrudelDsl
fun pdecay(seconds: PatternLike): StrudelPattern = _pdecay(listOf(seconds).asStrudelDslArgs())

/** Sets the pitch envelope decay time on this pattern. */
@StrudelDsl
fun StrudelPattern.pdecay(seconds: PatternLike): StrudelPattern = this._pdecay(listOf(seconds).asStrudelDslArgs())

/** Sets the pitch envelope decay time on a string pattern. */
@StrudelDsl
fun String.pdecay(seconds: PatternLike): StrudelPattern = this._pdecay(listOf(seconds).asStrudelDslArgs())

/** Alias for [pdecay]. Sets pitch envelope decay time on this pattern. */
@StrudelDsl
fun StrudelPattern.pdec(seconds: PatternLike): StrudelPattern = this._pdec(listOf(seconds).asStrudelDslArgs())

/** Alias for [pdecay]. Sets pitch envelope decay time. */
@StrudelDsl
fun pdec(seconds: PatternLike): StrudelPattern = _pdec(listOf(seconds).asStrudelDslArgs())

/** Alias for [pdecay] on a string pattern. */
@StrudelDsl
fun String.pdec(seconds: PatternLike): StrudelPattern = this._pdec(listOf(seconds).asStrudelDslArgs())

// -- prelease() / prel() ----------------------------------------------------------------------------------------------

private val pReleaseMutation = voiceModifier { copy(pRelease = it?.asDoubleOrNull()) }

fun applyPRelease(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, pReleaseMutation)
}

internal val _prelease by dslFunction { args, /* callInfo */ _ -> args.toPattern(pReleaseMutation) }

internal val StrudelPattern._prelease by dslPatternExtension { p, args, /* callInfo */ _ -> applyPRelease(p, args) }

internal val String._prelease by dslStringExtension { p, args, _ -> applyPRelease(p, args) }

internal val StrudelPattern._prel by dslPatternExtension { p, args, callInfo -> p._prelease(args, callInfo) }

internal val _prel by dslFunction { args, callInfo -> _prelease(args, callInfo) }

internal val String._prel by dslStringExtension { p, args, callInfo -> p._prelease(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the pitch envelope release time in seconds.
 *
 * The release phase determines how quickly the pitch envelope returns to its resting state
 * after the note ends. Use with [pattack], [pdecay], [penv], [pcurve], and [panchor].
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
@StrudelDsl
fun prelease(seconds: PatternLike): StrudelPattern = _prelease(listOf(seconds).asStrudelDslArgs())

/** Sets the pitch envelope release time on this pattern. */
@StrudelDsl
fun StrudelPattern.prelease(seconds: PatternLike): StrudelPattern = this._prelease(listOf(seconds).asStrudelDslArgs())

/** Sets the pitch envelope release time on a string pattern. */
@StrudelDsl
fun String.prelease(seconds: PatternLike): StrudelPattern = this._prelease(listOf(seconds).asStrudelDslArgs())

/** Alias for [prelease]. Sets pitch envelope release time on this pattern. */
@StrudelDsl
fun StrudelPattern.prel(seconds: PatternLike): StrudelPattern = this._prel(listOf(seconds).asStrudelDslArgs())

/** Alias for [prelease]. Sets pitch envelope release time. */
@StrudelDsl
fun prel(seconds: PatternLike): StrudelPattern = _prel(listOf(seconds).asStrudelDslArgs())

/** Alias for [prelease] on a string pattern. */
@StrudelDsl
fun String.prel(seconds: PatternLike): StrudelPattern = this._prel(listOf(seconds).asStrudelDslArgs())

// -- penv() / pamt() --------------------------------------------------------------------------------------------------

private val pEnvMutation = voiceModifier { copy(pEnv = it?.asDoubleOrNull()) }

fun applyPEnv(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, pEnvMutation)
}

internal val _penv by dslFunction { args, /* callInfo */ _ -> args.toPattern(pEnvMutation) }

internal val StrudelPattern._penv by dslPatternExtension { p, args, /* callInfo */ _ -> applyPEnv(p, args) }

internal val String._penv by dslStringExtension { p, args, _ -> applyPEnv(p, args) }

internal val StrudelPattern._pamt by dslPatternExtension { p, args, callInfo -> p._penv(args, callInfo) }

internal val _pamt by dslFunction { args, callInfo -> _penv(args, callInfo) }

internal val String._pamt by dslStringExtension { p, args, callInfo -> p._penv(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the pitch envelope depth (amount) in semitones.
 *
 * Determines how far the pitch deviates from the base note during the envelope cycle.
 * Positive values raise the pitch; negative values lower it.
 * Use with [pattack], [pdecay], [prelease], [pcurve], and [panchor].
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
@StrudelDsl
fun penv(semitones: PatternLike): StrudelPattern = _penv(listOf(semitones).asStrudelDslArgs())

/** Sets the pitch envelope depth (in semitones) on this pattern. */
@StrudelDsl
fun StrudelPattern.penv(semitones: PatternLike): StrudelPattern = this._penv(listOf(semitones).asStrudelDslArgs())

/** Sets the pitch envelope depth (in semitones) on a string pattern. */
@StrudelDsl
fun String.penv(semitones: PatternLike): StrudelPattern = this._penv(listOf(semitones).asStrudelDslArgs())

/** Alias for [penv]. Sets pitch envelope depth on this pattern. */
@StrudelDsl
fun StrudelPattern.pamt(semitones: PatternLike): StrudelPattern = this._pamt(listOf(semitones).asStrudelDslArgs())

/** Alias for [penv]. Sets pitch envelope depth. */
@StrudelDsl
fun pamt(semitones: PatternLike): StrudelPattern = _pamt(listOf(semitones).asStrudelDslArgs())

/** Alias for [penv] on a string pattern. */
@StrudelDsl
fun String.pamt(semitones: PatternLike): StrudelPattern = this._pamt(listOf(semitones).asStrudelDslArgs())

// -- pcurve() / pcrv() ------------------------------------------------------------------------------------------------

private val pCurveMutation = voiceModifier { copy(pCurve = it?.asDoubleOrNull()) }

fun applyPCurve(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, pCurveMutation)
}

internal val _pcurve by dslFunction { args, /* callInfo */ _ -> args.toPattern(pCurveMutation) }

internal val StrudelPattern._pcurve by dslPatternExtension { p, args, /* callInfo */ _ -> applyPCurve(p, args) }

internal val String._pcurve by dslStringExtension { p, args, _ -> applyPCurve(p, args) }

internal val StrudelPattern._pcrv by dslPatternExtension { p, args, callInfo -> p._pcurve(args, callInfo) }

internal val _pcrv by dslFunction { args, callInfo -> _pcurve(args, callInfo) }

internal val String._pcrv by dslStringExtension { p, args, callInfo -> p._pcurve(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the pitch envelope curve shape.
 *
 * Controls the curvature of the pitch envelope segments. A value of `0` gives a linear
 * curve; positive values create logarithmic curves; negative values create exponential ones.
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
@StrudelDsl
fun pcurve(curve: PatternLike): StrudelPattern = _pcurve(listOf(curve).asStrudelDslArgs())

/** Sets the pitch envelope curve shape on this pattern. */
@StrudelDsl
fun StrudelPattern.pcurve(curve: PatternLike): StrudelPattern = this._pcurve(listOf(curve).asStrudelDslArgs())

/** Sets the pitch envelope curve shape on a string pattern. */
@StrudelDsl
fun String.pcurve(curve: PatternLike): StrudelPattern = this._pcurve(listOf(curve).asStrudelDslArgs())

/** Alias for [pcurve]. Sets pitch envelope curve shape on this pattern. */
@StrudelDsl
fun StrudelPattern.pcrv(curve: PatternLike): StrudelPattern = this._pcrv(listOf(curve).asStrudelDslArgs())

/** Alias for [pcurve]. Sets pitch envelope curve shape. */
@StrudelDsl
fun pcrv(curve: PatternLike): StrudelPattern = _pcrv(listOf(curve).asStrudelDslArgs())

/** Alias for [pcurve] on a string pattern. */
@StrudelDsl
fun String.pcrv(curve: PatternLike): StrudelPattern = this._pcrv(listOf(curve).asStrudelDslArgs())

// -- panchor() / panc() -----------------------------------------------------------------------------------------------

private val pAnchorMutation = voiceModifier { copy(pAnchor = it?.asDoubleOrNull()) }

fun applyPAnchor(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, pAnchorMutation)
}

internal val _panchor by dslFunction { args, /* callInfo */ _ -> args.toPattern(pAnchorMutation) }

internal val StrudelPattern._panchor by dslPatternExtension { p, args, /* callInfo */ _ -> applyPAnchor(p, args) }

internal val String._panchor by dslStringExtension { p, args, _ -> applyPAnchor(p, args) }

internal val StrudelPattern._panc by dslPatternExtension { p, args, callInfo -> p._panchor(args, callInfo) }

internal val _panc by dslFunction { args, callInfo -> _panchor(args, callInfo) }

internal val String._panc by dslStringExtension { p, args, callInfo -> p._panchor(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the pitch envelope anchor point.
 *
 * The anchor determines the relative position within the note duration where the pitch
 * envelope reaches its peak (or trough). `0` anchors at the start; `1` at the end.
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
@StrudelDsl
fun panchor(anchor: PatternLike): StrudelPattern = _panchor(listOf(anchor).asStrudelDslArgs())

/** Sets the pitch envelope anchor point on this pattern. */
@StrudelDsl
fun StrudelPattern.panchor(anchor: PatternLike): StrudelPattern = this._panchor(listOf(anchor).asStrudelDslArgs())

/** Sets the pitch envelope anchor point on a string pattern. */
@StrudelDsl
fun String.panchor(anchor: PatternLike): StrudelPattern = this._panchor(listOf(anchor).asStrudelDslArgs())

/** Alias for [panchor]. Sets pitch envelope anchor point on this pattern. */
@StrudelDsl
fun StrudelPattern.panc(anchor: PatternLike): StrudelPattern = this._panc(listOf(anchor).asStrudelDslArgs())

/** Alias for [panchor]. Sets pitch envelope anchor point. */
@StrudelDsl
fun panc(anchor: PatternLike): StrudelPattern = _panc(listOf(anchor).asStrudelDslArgs())

/** Alias for [panchor] on a string pattern. */
@StrudelDsl
fun String.panc(anchor: PatternLike): StrudelPattern = this._panc(listOf(anchor).asStrudelDslArgs())

// -- accelerate() -----------------------------------------------------------------------------------------------------

private val accelerateMutation = voiceModifier { copy(accelerate = it?.asDoubleOrNull()) }

fun applyAccelerate(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, accelerateMutation)
}

internal val _accelerate by dslFunction { args, /* callInfo */ _ -> args.toPattern(accelerateMutation) }

internal val StrudelPattern._accelerate by dslPatternExtension { p, args, /* callInfo */ _ -> applyAccelerate(p, args) }

internal val String._accelerate by dslStringExtension { p, args, callInfo -> p._accelerate(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the playback acceleration (pitch ramp) for each event.
 *
 * Controls a continuous pitch change during sample playback. Positive values pitch up over
 * the event's duration; negative values pitch down. Useful for creating pitched percussion
 * or sweep effects.
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
@StrudelDsl
fun accelerate(amount: PatternLike): StrudelPattern = _accelerate(listOf(amount).asStrudelDslArgs())

/** Sets the playback acceleration (pitch ramp) on this pattern. */
@StrudelDsl
fun StrudelPattern.accelerate(amount: PatternLike): StrudelPattern = this._accelerate(listOf(amount).asStrudelDslArgs())

/** Sets the playback acceleration on a string pattern. */
@StrudelDsl
fun String.accelerate(amount: PatternLike): StrudelPattern = this._accelerate(listOf(amount).asStrudelDslArgs())

// -- transpose() ------------------------------------------------------------------------------------------------------

/**
 * Applies transposition logic to a StrudelVoiceData instance.
 * Accepts either a numeric value (semitones) or a string (interval name).
 */
fun StrudelVoiceData.transpose(amount: Any?): StrudelVoiceData {
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

fun applyTranspose(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
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

internal val _transpose by dslFunction { args, /* callInfo */ _ ->
    val source = args.lastOrNull()?.value as? StrudelPattern

    if (args.size >= 2 && source != null) {
        applyTranspose(source, args.dropLast(1))
    } else {
        // When used as a source (e.g. transpose(12)), it creates a pattern of values
        args.toPattern(voiceValueModifier)
    }
}

internal val StrudelPattern._transpose by dslPatternExtension { p, args, /* callInfo */ _ -> applyTranspose(p, args) }

internal val String._transpose by dslStringExtension { p, args, callInfo -> p._transpose(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Transposes a pattern by a number of semitones or an interval name.
 *
 * Shifts all note pitches by the given amount. Numeric arguments are treated as semitones;
 * string arguments can be interval names (e.g. `"P5"` for a perfect fifth). When used as
 * a top-level function, the last argument is the source pattern.
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
@StrudelDsl
fun StrudelPattern.transpose(amount: PatternLike): StrudelPattern =
    this._transpose(listOf(amount).asStrudelDslArgs())

/** Transposes a string pattern by a number of semitones or interval name. */
@StrudelDsl
fun String.transpose(amount: PatternLike): StrudelPattern = this._transpose(listOf(amount).asStrudelDslArgs())

/**
 * Transposes a string pattern by a number of semitones or interval name.
 *
 * @param amount The amount to transpose by, either as a number of semitones or an interval name.
 * @param pattern The source pattern.
 */
@StrudelDsl
fun transpose(amount: PatternLike, pattern: PatternLike): StrudelPattern =
    _transpose(listOf(amount, pattern).asStrudelDslArgs())

// -- freq() -----------------------------------------------------------------------------------------------------------

private val freqMutation = voiceModifier { copy(freqHz = it?.asDoubleOrNull()) }

fun applyFreq(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {

    return if (args.isEmpty()) {
        source.reinterpretVoice {
            it.freqMutation(it.value)
        }
    } else {
        source._applyControlFromParams(args, freqMutation) { src, ctrl ->
            src.freqMutation(
                ctrl.freqHz ?: ctrl.value
            )
        }
    }
}

internal val _freq by dslFunction { args, /* callInfo */ _ -> args.toPattern(freqMutation) }

internal val StrudelPattern._freq by dslPatternExtension { p, args, /* callInfo */ _ -> applyFreq(p, args) }

internal val String._freq by dslStringExtension { p, args, callInfo -> p._freq(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the playback frequency in Hz directly, bypassing note name resolution.
 *
 * Overrides the computed frequency for each event. Useful for precise tuning or
 * microtonal work where standard note names are insufficient.
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
@StrudelDsl
fun freq(hz: PatternLike): StrudelPattern = _freq(listOf(hz).asStrudelDslArgs())

/** Sets the playback frequency in Hz on this pattern. */
@StrudelDsl
fun StrudelPattern.freq(hz: PatternLike): StrudelPattern = this._freq(listOf(hz).asStrudelDslArgs())

/** Sets the playback frequency in Hz on a string pattern. */
@StrudelDsl
fun String.freq(hz: PatternLike): StrudelPattern = this._freq(listOf(hz).asStrudelDslArgs())

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
        )
    } catch (_: Exception) {
        // On any error, fallback to chromatic transposition
        return transpose(steps)
    }
}

fun applyScaleTranspose(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
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

internal val _scaleTranspose by dslFunction { args, _ ->
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

internal val StrudelPattern._scaleTranspose by dslPatternExtension { p, args, _ -> applyScaleTranspose(p, args) }

internal val String._scaleTranspose by dslStringExtension { p, args, callInfo -> p._scaleTranspose(args, callInfo) }

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
@StrudelDsl
fun scaleTranspose(steps: PatternLike): StrudelPattern = _scaleTranspose(listOf(steps).asStrudelDslArgs())

/** Transposes this pattern by a number of scale degrees within the active scale. */
@StrudelDsl
fun StrudelPattern.scaleTranspose(steps: PatternLike): StrudelPattern =
    this._scaleTranspose(listOf(steps).asStrudelDslArgs())

/** Transposes a string pattern by a number of scale degrees within the active scale. */
@StrudelDsl
fun String.scaleTranspose(steps: PatternLike): StrudelPattern = this._scaleTranspose(listOf(steps).asStrudelDslArgs())

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

internal val _chord by dslFunction { args, _ ->
    args.toPattern(chordMutation)
}

internal val StrudelPattern._chord by dslPatternExtension { p, args, _ ->
    p._applyControlFromParams(args, chordMutation) { src, ctrl ->
        src.chordMutation(ctrl.chord ?: ctrl.value?.asString)
    }
}

internal val String._chord by dslStringExtension { p, args, callInfo -> p._chord(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the chord name for each event, establishing harmonic context for [voicing].
 *
 * Chord names follow the format `"root quality"` (e.g. `"C major"`, `"Am"`, `"Gmaj7"`).
 * The chord root is also set as the event's note. Use [voicing] to expand into voiced
 * notes, or [rootNotes] to extract just the bass note.
 *
 * ```KlangScript
 * chord("C:major Am:minor F:major G:major").voicing()  // I-vi-IV-V with voice leading
 * ```
 *
 * ```KlangScript
 * chord("<Cmaj7 Am7>").voicing()                       // jazzy chord alternation per cycle
 * ```
 *
 * @category tonal
 * @tags chord, harmony, chords, voicing, progression
 */
@StrudelDsl
fun chord(name: PatternLike): StrudelPattern = _chord(listOf(name).asStrudelDslArgs())

/** Sets the chord name on this pattern for use with [voicing] and [rootNotes]. */
@StrudelDsl
fun StrudelPattern.chord(name: PatternLike): StrudelPattern = this._chord(listOf(name).asStrudelDslArgs())

/** Sets the chord name on a string pattern. */
@StrudelDsl
fun String.chord(name: PatternLike): StrudelPattern = this._chord(listOf(name).asStrudelDslArgs())

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
        )
    } catch (_: Exception) {
        return this
    }
}

fun applyRootNotes(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val octave = args.firstOrNull()?.value?.asIntOrNull()

    // No need for distinct() anymore since chord() doesn't expand
    return source.reinterpretVoice { voiceData ->
        voiceData.extractRootNote(octave)
    }
}

internal val _rootNotes by dslFunction { args, _ ->
    // When used standalone, just returns a pattern that will extract roots when applied
    args.toPattern(voiceValueModifier)
}

internal val StrudelPattern._rootNotes by dslPatternExtension { p, args, _ -> applyRootNotes(p, args) }

internal val String._rootNotes by dslStringExtension { p, args, callInfo -> p._rootNotes(args, callInfo) }

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
@StrudelDsl
fun StrudelPattern.rootNotes(): StrudelPattern = this._rootNotes(emptyList())

/** Extracts root notes from chord events in this pattern, forcing to the given octave. */
@StrudelDsl
fun StrudelPattern.rootNotes(octave: PatternLike): StrudelPattern =
    this._rootNotes(listOf(octave).asStrudelDslArgs())

/** Extracts root notes from chord events in a string pattern. */
@StrudelDsl
fun String.rootNotes(): StrudelPattern = this._rootNotes(emptyList())

/** Extracts root notes from chord events in a string pattern, forcing to the given octave. */
@StrudelDsl
fun String.rootNotes(octave: PatternLike): StrudelPattern = this._rootNotes(listOf(octave).asStrudelDslArgs())

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
fun applyVoicing(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
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

internal val _voicing by dslFunction { args, _ ->
    // When used standalone, creates a modifier pattern
    args.toPattern(voiceValueModifier)
}

internal val StrudelPattern._voicing by dslPatternExtension { p, args, _ -> applyVoicing(p, args) }

internal val String._voicing by dslStringExtension { p, args, callInfo -> p._voicing(args, callInfo) }

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
@StrudelDsl
fun StrudelPattern.voicing(): StrudelPattern = this._voicing(emptyList())

/** Expands chord events in this pattern into voiced notes within the given range. */
@StrudelDsl
fun StrudelPattern.voicing(low: String, high: String): StrudelPattern =
    this._voicing(listOf(low, high).asStrudelDslArgs())

/** Expands chord events in a string pattern into voiced notes with voice leading. */
@StrudelDsl
fun String.voicing(): StrudelPattern = this._voicing(emptyList())

/** Expands chord events in a string pattern into voiced notes within the given range. */
@StrudelDsl
fun String.voicing(low: String, high: String): StrudelPattern =
    this._voicing(listOf(low, high).asStrudelDslArgs())
