@file:Suppress("DuplicatedCode", "ObjectPropertyName")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel._applyControlFromParams
import io.peekandpoke.klang.strudel._liftNumericField
import io.peekandpoke.klang.strudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangDynamicsInit = false

// -- gain() -----------------------------------------------------------------------------------------------------------

private val gainMutation = voiceModifier { copy(gain = it?.asDoubleOrNull()) }

fun applyGain(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, gainMutation)
}

internal val StrudelPattern._gain by dslPatternExtension { p, args, /* callInfo */ _ -> applyGain(p, args) }

internal val String._gain by dslStringExtension { p, args, callInfo -> p._gain(args, callInfo) }

internal val _gain by dslPatternMapper { args, callInfo -> { p -> p._gain(args, callInfo) } }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the gain (volume multiplier) for each event in the pattern.
 *
 * Values below 1 reduce volume; above 1 amplify. Accepts control patterns for per-event modulation.
 *
 * ```KlangScript
 * s("bd sd hh cp").gain(0.5)              // all hits at half volume
 * ```
 *
 * ```KlangScript
 * s("bd*4").gain("<0.2 0.5 0.8 1.0>")    // different gain each cycle
 * ```
 *
 * @param amount The control value to use for gain.
 *
 * @category dynamics
 * @tags gain, volume, amplitude, dynamics
 */
@StrudelDsl
fun StrudelPattern.gain(amount: PatternLike? = null): StrudelPattern =
    this._gain(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the gain for each event.
 *
 * ```KlangScript
 * "bd*4".gain("0.2 0.5 0.8 1.0").s()    // different gain each beat
 * ```
 *
 * @param amount The control value to use for gain.
 */
@StrudelDsl
fun String.gain(amount: PatternLike? = null): StrudelPattern =
    this._gain(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a [PatternMapper] that sets the gain for each event in a pattern.
 *
 * ```KlangScript
 * s("hh hh hh hh").apply(gain("1.0 0.75 0.5 0.25"))
 * ```
 *
 * @param amount The control value to use for gain.
 */
@StrudelDsl
fun gain(amount: PatternLike? = null): PatternMapper = _gain(listOfNotNull(amount).asStrudelDslArgs())

// -- pan() ------------------------------------------------------------------------------------------------------------

private val panMutation = voiceModifier { copy(pan = it?.asDoubleOrNull()) }

fun applyPan(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretNumericalField(args, panMutation)
}

internal val StrudelPattern._pan by dslPatternExtension { p, args, /* callInfo */ _ -> applyPan(p, args) }

internal val String._pan by dslStringExtension { p, args, callInfo -> p._pan(args, callInfo) }

internal val _pan by dslPatternMapper { args, callInfo -> { p -> p._pan(args, callInfo) } }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the stereo panning position for each event (0 = full left, 0.5 = centre, 1 = full right).
 *
 * Accepts control patterns or continuous patterns for animated panning effects.
 *
 * ```KlangScript
 * s("bd sd").pan(0.25)                   // slightly left
 * ```
 *
 * ```KlangScript
 * s("bd hh sd cp").pan("0 0.33 0.66 1")  // left to right
 * ```
 *
 * ```KlangScript
 * s("hh*8").pan(sine.range(0, 1))        // smooth left-right sweep
 * ```
 *
 * @param amount The panning position for each event, ranging from 0 (full left) to 1 (full right).
 *
 * @category dynamics
 * @tags pan, stereo, panning, position
 */
@StrudelDsl
fun StrudelPattern.pan(amount: PatternLike? = null): StrudelPattern =
    this._pan(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the stereo panning position.
 *
 * ```KlangScript
 * "bd hh sd cp".pan("0 0.33 0.66 1").s()  // left to right
 * ```
 *
 * @param amount The panning position for each event, ranging from 0 (full left) to 1 (full right).
 */
@StrudelDsl
fun String.pan(amount: PatternLike? = null): StrudelPattern =
    this._pan(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the stereo panning position.
 *
 * ```KlangScript
 * s("bd hh sd cp").apply(pan("0 0.33 0.66 1"))  // left to right
 * ```
 */
@StrudelDsl
fun pan(amount: PatternLike? = null): PatternMapper =
    _pan(listOfNotNull(amount).asStrudelDslArgs())


// -- velocity() / vel() -----------------------------------------------------------------------------------------------

private val velocityMutation = voiceModifier { copy(velocity = it?.asDoubleOrNull()) }

fun applyVelocity(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, velocityMutation)
}

internal val _velocity by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(velocityMutation) }
internal val StrudelPattern._velocity by dslPatternExtension { p, args, /* callInfo */ _ -> applyVelocity(p, args) }
internal val String._velocity by dslStringExtension { p, args, callInfo -> p._velocity(args, callInfo) }

internal val _vel by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(velocityMutation) }
internal val StrudelPattern._vel by dslPatternExtension { p, args, /* callInfo */ _ -> applyVelocity(p, args) }
internal val String._vel by dslStringExtension { p, args, callInfo -> p._vel(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the velocity (MIDI-style volume scaling, 0–1) for each event in the pattern.
 *
 * Unlike `gain`, velocity is typically used as a MIDI note velocity or soft scaling factor.
 *
 * ```KlangScript
 * note("c d e f").velocity(0.8)               // slightly softer notes
 * ```
 *
 * ```KlangScript
 * note("c*4").velocity("<0.3 0.6 0.9 1.0>")  // crescendo pattern
 * ```
 *
 * @alias vel
 * @category dynamics
 * @tags velocity, vel, volume, midi, dynamics
 */
@StrudelDsl
fun velocity(amount: PatternLike): StrudelPattern = _velocity(listOf(amount).asStrudelDslArgs())

/** Sets the velocity for each event in this pattern. */
@StrudelDsl
fun StrudelPattern.velocity(amount: PatternLike): StrudelPattern = this._velocity(listOf(amount).asStrudelDslArgs())

/** Parses this string as a pattern and sets the velocity for each event. */
@StrudelDsl
fun String.velocity(amount: PatternLike): StrudelPattern = this._velocity(listOf(amount).asStrudelDslArgs())

/**
 * Alias for [velocity]. Sets the velocity (MIDI-style volume scaling, 0–1) for each event.
 *
 * ```KlangScript
 * note("c d e f").vel(0.8)               // slightly softer notes
 * ```
 *
 * ```KlangScript
 * note("c*4").vel("<0.3 0.6 0.9 1.0>")  // crescendo pattern
 * ```
 *
 * @alias velocity
 * @category dynamics
 * @tags vel, velocity, volume, midi, dynamics
 */
@StrudelDsl
fun vel(amount: PatternLike): StrudelPattern = _vel(listOf(amount).asStrudelDslArgs())

/** Alias for [velocity]. Sets the velocity for each event in this pattern. */
@StrudelDsl
fun StrudelPattern.vel(amount: PatternLike): StrudelPattern = this._vel(listOf(amount).asStrudelDslArgs())

/** Alias for [velocity]. Parses this string as a pattern and sets the velocity. */
@StrudelDsl
fun String.vel(amount: PatternLike): StrudelPattern = this._vel(listOf(amount).asStrudelDslArgs())

// -- postgain() -------------------------------------------------------------------------------------------------------

private val postgainMutation = voiceModifier { copy(postGain = it?.asDoubleOrNull()) }

fun applyPostgain(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, postgainMutation)
}

internal val _postgain by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(postgainMutation) }
internal val StrudelPattern._postgain by dslPatternExtension { p, args, /* callInfo */ _ -> applyPostgain(p, args) }
internal val String._postgain by dslStringExtension { p, args, callInfo -> p._postgain(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the post-gain (applied after voice processing) for each event in the pattern.
 *
 * Unlike `gain` which is applied before synthesis, `postgain` is a final output multiplier.
 *
 * ```KlangScript
 * s("bd sd").postgain(1.5)                    // amplify after processing
 * ```
 *
 * ```KlangScript
 * s("hh*8").postgain(rand.range(0.5, 1.0))   // random post-gain per hit
 * ```
 *
 * @category dynamics
 * @tags postgain, gain, volume, post-processing
 */
@StrudelDsl
fun postgain(amount: PatternLike): StrudelPattern = _postgain(listOf(amount).asStrudelDslArgs())

/** Sets the post-gain for each event in this pattern. */
@StrudelDsl
fun StrudelPattern.postgain(amount: PatternLike): StrudelPattern = this._postgain(listOf(amount).asStrudelDslArgs())

/** Parses this string as a pattern and sets the post-gain for each event. */
@StrudelDsl
fun String.postgain(amount: PatternLike): StrudelPattern = this._postgain(listOf(amount).asStrudelDslArgs())

// -- compressor() / comp() --------------------------------------------------------------------------------------------

private val compressorMutation = voiceModifier { shape -> copy(compressor = shape?.toString()) }

fun applyCompressor(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._applyControlFromParams(args, compressorMutation) { src, ctrl ->
        src.copy(compressor = ctrl.compressor)
    }
}

internal val _compressor by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(compressorMutation) }
internal val StrudelPattern._compressor by dslPatternExtension { p, args, /* callInfo */ _ -> applyCompressor(p, args) }
internal val String._compressor by dslStringExtension { p, args, callInfo -> p._compressor(args, callInfo) }

internal val _comp by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(compressorMutation) }
internal val StrudelPattern._comp by dslPatternExtension { p, args, /* callInfo */ _ -> applyCompressor(p, args) }
internal val String._comp by dslStringExtension { p, args, callInfo -> p._comp(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets dynamic range compression parameters as a colon-separated string
 * `"threshold:ratio:knee:attack:release"`.
 *
 * ```KlangScript
 * s("bd sd").compressor("-20:4:3:0.01:0.3")                        // standard compression
 * ```
 *
 * ```KlangScript
 * s("bd*4").compressor("<-10:2:1:0.01:0.1 -30:8:5:0.005:0.5>")   // alternate settings
 * ```
 *
 * @alias comp
 * @category dynamics
 * @tags compressor, comp, compression, threshold, ratio, dynamics
 */
@StrudelDsl
fun compressor(params: PatternLike): StrudelPattern = _compressor(listOf(params).asStrudelDslArgs())

/** Sets dynamic range compression parameters for this pattern. */
@StrudelDsl
fun StrudelPattern.compressor(params: PatternLike): StrudelPattern =
    this._compressor(listOf(params).asStrudelDslArgs())

/** Parses this string as a pattern and sets dynamic range compression parameters. */
@StrudelDsl
fun String.compressor(params: PatternLike): StrudelPattern = this._compressor(listOf(params).asStrudelDslArgs())

/**
 * Alias for [compressor]. Sets dynamic range compression parameters as a colon-separated string
 * `"threshold:ratio:knee:attack:release"`.
 *
 * ```KlangScript
 * s("bd sd").comp("-20:4:3:0.01:0.3")                        // standard compression
 * ```
 *
 * ```KlangScript
 * s("bd*4").comp("<-10:2:1:0.01:0.1 -30:8:5:0.005:0.5>")   // alternate settings
 * ```
 *
 * @alias compressor
 * @category dynamics
 * @tags comp, compressor, compression, threshold, ratio, dynamics
 */
@StrudelDsl
fun comp(params: PatternLike): StrudelPattern = _comp(listOf(params).asStrudelDslArgs())

/** Alias for [compressor]. Sets dynamic range compression parameters for this pattern. */
@StrudelDsl
fun StrudelPattern.comp(params: PatternLike): StrudelPattern = this._comp(listOf(params).asStrudelDslArgs())

/** Alias for [compressor]. Parses this string as a pattern and sets compression parameters. */
@StrudelDsl
fun String.comp(params: PatternLike): StrudelPattern = this._comp(listOf(params).asStrudelDslArgs())

// -- unison() / uni() -------------------------------------------------------------------------------------------------

private val unisonMutation = voiceModifier { copy(voices = it?.asDoubleOrNull()) }

private fun applyUnison(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, unisonMutation)
}

internal val _unison by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(unisonMutation) }
internal val StrudelPattern._unison by dslPatternExtension { p, args, /* callInfo */ _ -> applyUnison(p, args) }
internal val String._unison by dslStringExtension { p, args, callInfo -> p._unison(args, callInfo) }

internal val _uni by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(unisonMutation) }
internal val StrudelPattern._uni by dslPatternExtension { p, args, /* callInfo */ _ -> applyUnison(p, args) }
internal val String._uni by dslStringExtension { p, args, callInfo -> p._uni(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the number of unison voices for oscillator stacking effects (e.g. supersaw).
 *
 * Higher values produce a thicker, chorus-like sound. Use with `detune` and `spread`
 * to control the detuning and panning spread of the voices.
 *
 * ```KlangScript
 * note("c3").s("sawtooth").unison(5)               // 5 stacked sawtooth oscillators
 * ```
 *
 * ```KlangScript
 * note("c3 e3 g3").s("sine").unison(3).detune(10)  // triple unison with spread
 * ```
 *
 * @alias uni
 * @category dynamics
 * @tags unison, uni, voices, stacking, supersaw
 */
@StrudelDsl
fun unison(voices: PatternLike): StrudelPattern = _unison(listOf(voices).asStrudelDslArgs())

/** Sets the number of unison voices for this pattern. */
@StrudelDsl
fun StrudelPattern.unison(voices: PatternLike): StrudelPattern = this._unison(listOf(voices).asStrudelDslArgs())

/** Parses this string as a pattern and sets the number of unison voices. */
@StrudelDsl
fun String.unison(voices: PatternLike): StrudelPattern = this._unison(listOf(voices).asStrudelDslArgs())

/**
 * Alias for [unison]. Sets the number of unison voices for oscillator stacking effects.
 *
 * ```KlangScript
 * note("c3").s("sawtooth").uni(5)               // 5 stacked sawtooth oscillators
 * ```
 *
 * ```KlangScript
 * note("c3 e3 g3").s("sine").uni(3).detune(10)  // triple unison with spread
 * ```
 *
 * @alias unison
 * @category dynamics
 * @tags uni, unison, voices, stacking, supersaw
 */
@StrudelDsl
fun uni(voices: PatternLike): StrudelPattern = _uni(listOf(voices).asStrudelDslArgs())

/** Alias for [unison]. Sets the number of unison voices for this pattern. */
@StrudelDsl
fun StrudelPattern.uni(voices: PatternLike): StrudelPattern = this._uni(listOf(voices).asStrudelDslArgs())

/** Alias for [unison]. Parses this string as a pattern and sets the number of unison voices. */
@StrudelDsl
fun String.uni(voices: PatternLike): StrudelPattern = this._uni(listOf(voices).asStrudelDslArgs())

// -- detune() ---------------------------------------------------------------------------------------------------------

private val detuneMutation = voiceModifier { copy(freqSpread = it?.asDoubleOrNull()) }

private fun applyDetune(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, detuneMutation)
}

internal val _detune by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(detuneMutation) }
internal val StrudelPattern._detune by dslPatternExtension { p, args, /* callInfo */ _ -> applyDetune(p, args) }
internal val String._detune by dslStringExtension { p, args, callInfo -> p._detune(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the oscillator frequency spread in cents for unison/supersaw effects.
 *
 * Controls how much each unison voice is detuned from the base pitch. Use with `unison`
 * to set the number of voices. Higher values produce a wider, more detuned sound.
 *
 * ```KlangScript
 * note("c3").s("sawtooth").unison(5).detune(20)   // 5 voices spread ±20 cents
 * ```
 *
 * ```KlangScript
 * note("c3*4").detune("<5 10 20 40>")             // escalating detune each beat
 * ```
 *
 * @category dynamics
 * @tags detune, spread, unison, cents, supersaw
 */
@StrudelDsl
fun detune(amount: PatternLike): StrudelPattern = _detune(listOf(amount).asStrudelDslArgs())

/** Sets the oscillator frequency spread for this pattern. */
@StrudelDsl
fun StrudelPattern.detune(amount: PatternLike): StrudelPattern = this._detune(listOf(amount).asStrudelDslArgs())

/** Parses this string as a pattern and sets the oscillator frequency spread. */
@StrudelDsl
fun String.detune(amount: PatternLike): StrudelPattern = this._detune(listOf(amount).asStrudelDslArgs())

// -- spread() ---------------------------------------------------------------------------------------------------------

private val spreadMutation = voiceModifier { copy(panSpread = it?.asDoubleOrNull()) }

private fun applySpread(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, spreadMutation)
}

internal val _spread by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(spreadMutation) }
internal val StrudelPattern._spread by dslPatternExtension { p, args, /* callInfo */ _ -> applySpread(p, args) }
internal val String._spread by dslStringExtension { p, args, callInfo -> p._spread(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the stereo pan spread for unison/supersaw voices (0 = mono, 1 = full stereo spread).
 *
 * Controls how widely the unison voices are spread across the stereo field. Use with
 * `unison` to set the number of voices.
 *
 * ```KlangScript
 * note("c3").s("sawtooth").unison(5).spread(0.8)   // wide stereo spread
 * ```
 *
 * ```KlangScript
 * note("c3*4").spread("<0.2 0.5 0.8 1.0>")         // gradually widen each beat
 * ```
 *
 * @category dynamics
 * @tags spread, pan, stereo, unison, supersaw
 */
@StrudelDsl
fun spread(amount: PatternLike): StrudelPattern = _spread(listOf(amount).asStrudelDslArgs())

/** Sets the stereo pan spread for unison voices in this pattern. */
@StrudelDsl
fun StrudelPattern.spread(amount: PatternLike): StrudelPattern = this._spread(listOf(amount).asStrudelDslArgs())

/** Parses this string as a pattern and sets the stereo pan spread for unison voices. */
@StrudelDsl
fun String.spread(amount: PatternLike): StrudelPattern = this._spread(listOf(amount).asStrudelDslArgs())

// -- density() / d() --------------------------------------------------------------------------------------------------

private val densityMutation = voiceModifier { copy(density = it?.asDoubleOrNull()) }

private fun applyDensity(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, densityMutation)
}

internal val _density by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(densityMutation) }
internal val StrudelPattern._density by dslPatternExtension { p, args, /* callInfo */ _ -> applyDensity(p, args) }
internal val String._density by dslStringExtension { p, args, callInfo -> p._density(args, callInfo) }

internal val _d by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(densityMutation) }
internal val StrudelPattern._d by dslPatternExtension { p, args, /* callInfo */ _ -> applyDensity(p, args) }
internal val String._d by dslStringExtension { p, args, callInfo -> p._d(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the oscillator density for supersaw or noise density for dust/crackle generators.
 *
 * For supersaw: controls how tightly packed the oscillators are.
 * For noise generators (e.g. `dust`): controls the number of events per second.
 *
 * ```KlangScript
 * s("dust").density(40)                            // 40 noise events per second
 * ```
 *
 * ```KlangScript
 * note("c3").s("sawtooth").unison(7).density(0.5)  // tight supersaw
 * ```
 *
 * @alias d
 * @category dynamics
 * @tags density, d, supersaw, dust, noise
 */
@StrudelDsl
fun density(amount: PatternLike): StrudelPattern = _density(listOf(amount).asStrudelDslArgs())

/** Sets the oscillator or noise density for this pattern. */
@StrudelDsl
fun StrudelPattern.density(amount: PatternLike): StrudelPattern = this._density(listOf(amount).asStrudelDslArgs())

/** Parses this string as a pattern and sets the oscillator or noise density. */
@StrudelDsl
fun String.density(amount: PatternLike): StrudelPattern = this._density(listOf(amount).asStrudelDslArgs())

/**
 * Alias for [density]. Sets the oscillator density for supersaw or noise density for dust/crackle.
 *
 * ```KlangScript
 * s("dust").d(40)                            // 40 noise events per second
 * ```
 *
 * ```KlangScript
 * note("c3").s("sawtooth").unison(7).d(0.5)  // tight supersaw
 * ```
 *
 * @alias density
 * @category dynamics
 * @tags d, density, supersaw, dust, noise
 */
@StrudelDsl
fun d(amount: PatternLike): StrudelPattern = _d(listOf(amount).asStrudelDslArgs())

/** Alias for [density]. Sets the oscillator or noise density for this pattern. */
@StrudelDsl
fun StrudelPattern.d(amount: PatternLike): StrudelPattern = this._d(listOf(amount).asStrudelDslArgs())

/** Alias for [density]. Parses this string as a pattern and sets the oscillator or noise density. */
@StrudelDsl
fun String.d(amount: PatternLike): StrudelPattern = this._d(listOf(amount).asStrudelDslArgs())

// -- ADSR attack() ----------------------------------------------------------------------------------------------------

private val attackMutation = voiceModifier { copy(attack = it?.asDoubleOrNull()) }

private fun applyAttack(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, attackMutation)
}

internal val _attack by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(attackMutation) }
internal val StrudelPattern._attack by dslPatternExtension { p, args, /* callInfo */ _ -> applyAttack(p, args) }
internal val String._attack by dslStringExtension { p, args, callInfo -> p._attack(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the ADSR envelope attack time in seconds for synthesised notes.
 *
 * Controls how quickly the note rises from silence to full volume at the start.
 * Short values produce a sharp, percussive onset; longer values create a gradual fade-in.
 *
 * ```KlangScript
 * note("c3 e3 g3").s("sine").attack(0.01)     // sharp attack
 * ```
 *
 * ```KlangScript
 * note("c3*4").attack("<0.01 0.1 0.5 1.0>")   // varying attacks
 * ```
 *
 * @category dynamics
 * @tags attack, adsr, envelope, fade-in
 */
@StrudelDsl
fun attack(time: PatternLike): StrudelPattern = _attack(listOf(time).asStrudelDslArgs())

/** Sets the ADSR envelope attack time for this pattern. */
@StrudelDsl
fun StrudelPattern.attack(time: PatternLike): StrudelPattern = this._attack(listOf(time).asStrudelDslArgs())

/** Parses this string as a pattern and sets the ADSR envelope attack time. */
@StrudelDsl
fun String.attack(time: PatternLike): StrudelPattern = this._attack(listOf(time).asStrudelDslArgs())

// -- ADSR decay() -----------------------------------------------------------------------------------------------------

private val decayMutation = voiceModifier { copy(decay = it?.asDoubleOrNull()) }

private fun applyDecay(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, decayMutation)
}

internal val _decay by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(decayMutation) }
internal val StrudelPattern._decay by dslPatternExtension { p, args, /* callInfo */ _ -> applyDecay(p, args) }
internal val String._decay by dslStringExtension { p, args, callInfo -> p._decay(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the ADSR envelope decay time in seconds for synthesised notes.
 *
 * Controls how quickly the volume falls from its peak to the sustain level after the attack phase.
 *
 * ```KlangScript
 * note("c3 e3").s("sawtooth").decay(0.2)       // short decay
 * ```
 *
 * ```KlangScript
 * note("c3*4").decay("<0.05 0.2 0.5 1.0>")    // varying decays
 * ```
 *
 * @category dynamics
 * @tags decay, adsr, envelope
 */
@StrudelDsl
fun decay(time: PatternLike): StrudelPattern = _decay(listOf(time).asStrudelDslArgs())

/** Sets the ADSR envelope decay time for this pattern. */
@StrudelDsl
fun StrudelPattern.decay(time: PatternLike): StrudelPattern = this._decay(listOf(time).asStrudelDslArgs())

/** Parses this string as a pattern and sets the ADSR envelope decay time. */
@StrudelDsl
fun String.decay(time: PatternLike): StrudelPattern = this._decay(listOf(time).asStrudelDslArgs())

// -- ADSR sustain() ---------------------------------------------------------------------------------------------------

private val sustainMutation = voiceModifier { copy(sustain = it?.asDoubleOrNull()) }

private fun applySustain(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, sustainMutation)
}

internal val _sustain by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(sustainMutation) }
internal val StrudelPattern._sustain by dslPatternExtension { p, args, /* callInfo */ _ -> applySustain(p, args) }
internal val String._sustain by dslStringExtension { p, args, callInfo -> p._sustain(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the ADSR envelope sustain level (0–1) for synthesised notes.
 *
 * The sustain level is held while the note is pressed, after the attack and decay phases.
 * `0` = silence after decay; `1` = hold at full peak level.
 *
 * ```KlangScript
 * note("c3 e3").s("sine").sustain(0.7)        // 70% sustain level
 * ```
 *
 * ```KlangScript
 * note("c3*4").sustain("<0 0.3 0.7 1.0>")    // varying sustain
 * ```
 *
 * @category dynamics
 * @tags sustain, adsr, envelope, hold
 */
@StrudelDsl
fun sustain(level: PatternLike): StrudelPattern = _sustain(listOf(level).asStrudelDslArgs())

/** Sets the ADSR envelope sustain level for this pattern. */
@StrudelDsl
fun StrudelPattern.sustain(level: PatternLike): StrudelPattern = this._sustain(listOf(level).asStrudelDslArgs())

/** Parses this string as a pattern and sets the ADSR envelope sustain level. */
@StrudelDsl
fun String.sustain(level: PatternLike): StrudelPattern = this._sustain(listOf(level).asStrudelDslArgs())

// -- ADSR release() ---------------------------------------------------------------------------------------------------

private val releaseMutation = voiceModifier { copy(release = it?.asDoubleOrNull()) }

private fun applyRelease(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, releaseMutation)
}

internal val _release by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(releaseMutation) }
internal val StrudelPattern._release by dslPatternExtension { p, args, /* callInfo */ _ -> applyRelease(p, args) }
internal val String._release by dslStringExtension { p, args, callInfo -> p._release(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the ADSR envelope release time in seconds for synthesised notes.
 *
 * Controls how long the note takes to fade to silence after a note-off event.
 * Short values produce an abrupt cut; longer values create a smooth fade-out.
 *
 * ```KlangScript
 * note("c3 e3 g3").s("sine").release(0.5)     // half-second release
 * ```
 *
 * ```KlangScript
 * note("c3*4").release("<0.1 0.3 0.8 2.0>")  // varying releases
 * ```
 *
 * @category dynamics
 * @tags release, adsr, envelope, fade-out
 */
@StrudelDsl
fun release(time: PatternLike): StrudelPattern = _release(listOf(time).asStrudelDslArgs())

/** Sets the ADSR envelope release time for this pattern. */
@StrudelDsl
fun StrudelPattern.release(time: PatternLike): StrudelPattern = this._release(listOf(time).asStrudelDslArgs())

/** Parses this string as a pattern and sets the ADSR envelope release time. */
@StrudelDsl
fun String.release(time: PatternLike): StrudelPattern = this._release(listOf(time).asStrudelDslArgs())

// -- ADSR adsr() ------------------------------------------------------------------------------------------------------

private val adsrMutation = voiceModifier {
    val parts = it?.toString()?.split(":")
        ?.mapNotNull { d -> d.toDoubleOrNull() } ?: emptyList()

    copy(
        attack = parts.getOrNull(0) ?: attack,
        decay = parts.getOrNull(1) ?: decay,
        sustain = parts.getOrNull(2) ?: sustain,
        release = parts.getOrNull(3) ?: release,
    )
}

private fun applyAdsr(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._applyControlFromParams(args, adsrMutation) { src, ctrl ->
        src.copy(
            attack = ctrl.attack ?: src.attack,
            decay = ctrl.decay ?: src.decay,
            sustain = ctrl.sustain ?: src.sustain,
            release = ctrl.release ?: src.release,
        )
    }
}

internal val _adsr by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(adsrMutation) }
internal val StrudelPattern._adsr by dslPatternExtension { p, args, /* callInfo */ _ -> applyAdsr(p, args) }
internal val String._adsr by dslStringExtension { p, args, callInfo -> p._adsr(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets all four ADSR envelope parameters at once via a colon-separated string
 * `"attack:decay:sustain:release"`.
 *
 * Each field is a number: attack/decay/release in seconds, sustain in 0–1 range.
 * Missing trailing fields keep their previous values.
 *
 * ```KlangScript
 * note("c3 e3 g3").s("sine").adsr("0.01:0.2:0.7:0.5")          // standard ADSR
 * ```
 *
 * ```KlangScript
 * note("c3*4").adsr("<0.01:0.1:0.5:0.2 0.5:0.5:0.8:1.0>")     // alternate envelopes
 * ```
 *
 * @category dynamics
 * @tags adsr, attack, decay, sustain, release, envelope
 */
@StrudelDsl
fun adsr(params: PatternLike): StrudelPattern = _adsr(listOf(params).asStrudelDslArgs())

/** Sets all four ADSR envelope parameters for this pattern via a colon-separated string. */
@StrudelDsl
fun StrudelPattern.adsr(params: PatternLike): StrudelPattern = this._adsr(listOf(params).asStrudelDslArgs())

/** Parses this string as a pattern and sets all ADSR envelope parameters. */
@StrudelDsl
fun String.adsr(params: PatternLike): StrudelPattern = this._adsr(listOf(params).asStrudelDslArgs())

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Routing
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// -- orbit() / o() ----------------------------------------------------------------------------------------------------

private val orbitMutation = voiceModifier {
    copy(orbit = it?.asIntOrNull())
}

private fun applyOrbit(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, orbitMutation)
}

internal val _orbit by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(orbitMutation) }
internal val StrudelPattern._orbit by dslPatternExtension { p, args, /* callInfo */ _ -> applyOrbit(p, args) }
internal val String._orbit by dslStringExtension { p, args, callInfo -> p._orbit(args, callInfo) }

internal val _o by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(orbitMutation) }
internal val StrudelPattern._o by dslPatternExtension { p, args, /* callInfo */ _ -> applyOrbit(p, args) }
internal val String._o by dslStringExtension { p, args, callInfo -> p._o(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Routes the pattern to an audio output orbit (channel group) for independent effect processing.
 *
 * Each orbit can have its own reverb, delay, and other effects applied independently.
 * Use different orbit numbers to send patterns to different effect buses.
 *
 * ```KlangScript
 * s("bd sd").orbit(1)                           // send drums to orbit 1
 * ```
 *
 * ```KlangScript
 * note("c3 e3").orbit(2).room(0.8).roomsize(4)  // melodic line on orbit 2 with reverb
 * ```
 *
 * @alias o
 * @category dynamics
 * @tags orbit, o, routing, effects, bus, channel
 */
@StrudelDsl
fun orbit(index: PatternLike): StrudelPattern = _orbit(listOf(index).asStrudelDslArgs())

/** Routes this pattern to the given audio output orbit. */
@StrudelDsl
fun StrudelPattern.orbit(index: PatternLike): StrudelPattern = this._orbit(listOf(index).asStrudelDslArgs())

/** Parses this string as a pattern and routes it to the given audio output orbit. */
@StrudelDsl
fun String.orbit(index: PatternLike): StrudelPattern = this._orbit(listOf(index).asStrudelDslArgs())

/**
 * Alias for [orbit]. Routes the pattern to an audio output orbit for independent effect processing.
 *
 * ```KlangScript
 * s("bd sd").o(1)                       // send drums to orbit 1
 * ```
 *
 * ```KlangScript
 * note("c3 e3").o(2).room(0.8)          // melodic line on orbit 2 with reverb
 * ```
 *
 * @alias orbit
 * @category dynamics
 * @tags o, orbit, routing, effects, bus, channel
 */
@StrudelDsl
fun o(index: PatternLike): StrudelPattern = _o(listOf(index).asStrudelDslArgs())

/** Alias for [orbit]. Routes this pattern to the given audio output orbit. */
@StrudelDsl
fun StrudelPattern.o(index: PatternLike): StrudelPattern = this._o(listOf(index).asStrudelDslArgs())

/** Alias for [orbit]. Parses this string as a pattern and routes it to the given orbit. */
@StrudelDsl
fun String.o(index: PatternLike): StrudelPattern = this._o(listOf(index).asStrudelDslArgs())

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Ducking / Sidechain
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// -- duckorbit() / duck() ---------------------------------------------------------------------------------------------

private val duckOrbitMutation = voiceModifier {
    copy(duckOrbit = it?.asIntOrNull())
}

private fun applyDuckOrbit(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, duckOrbitMutation)
}

internal val _duckorbit by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(duckOrbitMutation) }
internal val StrudelPattern._duckorbit by dslPatternExtension { p, args, /* callInfo */ _ -> applyDuckOrbit(p, args) }
internal val String._duckorbit by dslStringExtension { p, args, callInfo -> p._duckorbit(args, callInfo) }

internal val _duck by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(duckOrbitMutation) }
internal val StrudelPattern._duck by dslPatternExtension { p, args, /* callInfo */ _ -> applyDuckOrbit(p, args) }
internal val String._duck by dslStringExtension { p, args, callInfo -> p._duck(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the target orbit to listen to for sidechain ducking.
 *
 * The pattern's volume is reduced when audio is detected on the specified orbit.
 * Use with `duckdepth` to set the attenuation amount and `duckattack` for the recovery time.
 *
 * ```KlangScript
 * s("bd*4").orbit(1)                              // kick drum on orbit 1
 * ```
 *
 * ```KlangScript
 * note("c3 e3 g3").duckorbit(1).duckdepth(0.8)   // duck when kick plays on orbit 1
 * ```
 *
 * @alias duck
 * @category dynamics
 * @tags duckorbit, duck, sidechain, ducking, dynamics
 */
@StrudelDsl
fun duckorbit(orbitIndex: PatternLike): StrudelPattern = _duckorbit(listOf(orbitIndex).asStrudelDslArgs())

/** Sets the sidechain source orbit for ducking this pattern. */
@StrudelDsl
fun StrudelPattern.duckorbit(orbitIndex: PatternLike): StrudelPattern =
    this._duckorbit(listOf(orbitIndex).asStrudelDslArgs())

/** Parses this string as a pattern and sets the sidechain source orbit for ducking. */
@StrudelDsl
fun String.duckorbit(orbitIndex: PatternLike): StrudelPattern =
    this._duckorbit(listOf(orbitIndex).asStrudelDslArgs())

/**
 * Alias for [duckorbit]. Sets the target orbit to listen to for sidechain ducking.
 *
 * ```KlangScript
 * stack(
 *   s("bd*4").orbit(0),                               // kick drum on orbit 0
 *   note("c3 e3").orbit(1).duck(0).duckdepth(1.0),    // duck when kick plays on orbit 1
 * )
 * ```
 *
 * @alias duckorbit
 * @category dynamics
 * @tags duck, duckorbit, sidechain, ducking, dynamics
 */
@StrudelDsl
fun duck(orbitIndex: PatternLike): StrudelPattern = _duck(listOf(orbitIndex).asStrudelDslArgs())

/** Alias for [duckorbit]. Sets the sidechain source orbit for ducking this pattern. */
@StrudelDsl
fun StrudelPattern.duck(orbitIndex: PatternLike): StrudelPattern = this._duck(listOf(orbitIndex).asStrudelDslArgs())

/** Alias for [duckorbit]. Parses this string as a pattern and sets the sidechain source orbit. */
@StrudelDsl
fun String.duck(orbitIndex: PatternLike): StrudelPattern = this._duck(listOf(orbitIndex).asStrudelDslArgs())

// -- duckattack() / duckatt() -----------------------------------------------------------------------------------------

private val duckAttackMutation = voiceModifier { copy(duckAttack = it?.asDoubleOrNull()) }

private fun applyDuckAttack(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, duckAttackMutation)
}

internal val _duckattack by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(duckAttackMutation) }
internal val StrudelPattern._duckattack by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyDuckAttack(p, args)
}
internal val String._duckattack by dslStringExtension { p, args, callInfo -> p._duckattack(args, callInfo) }

internal val _duckatt by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(duckAttackMutation) }
internal val StrudelPattern._duckatt by dslPatternExtension { p, args, /* callInfo */ _ -> applyDuckAttack(p, args) }
internal val String._duckatt by dslStringExtension { p, args, callInfo -> p._duckatt(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the duck release (return-to-normal) time in seconds for sidechain ducking.
 *
 * Controls how quickly the ducked pattern returns to its full volume after the sidechain
 * trigger stops. Shorter values snap back quickly; longer values create a pumping effect.
 *
 * ```KlangScript
 * note("c3 e3").duck(1).duckdepth(0.8).duckattack(0.2)   // 200 ms recovery
 * ```
 *
 * ```KlangScript
 * note("c3*4").duckattack("<0.05 0.1 0.3 0.5>")          // varying recovery times
 * ```
 *
 * @alias duckatt
 * @category dynamics
 * @tags duckattack, duckatt, sidechain, ducking, release, dynamics
 */
@StrudelDsl
fun duckattack(time: PatternLike): StrudelPattern = _duckattack(listOf(time).asStrudelDslArgs())

/** Sets the duck release time for this pattern. */
@StrudelDsl
fun StrudelPattern.duckattack(time: PatternLike): StrudelPattern = this._duckattack(listOf(time).asStrudelDslArgs())

/** Parses this string as a pattern and sets the duck release time. */
@StrudelDsl
fun String.duckattack(time: PatternLike): StrudelPattern = this._duckattack(listOf(time).asStrudelDslArgs())

/**
 * Alias for [duckattack]. Sets the duck release (return-to-normal) time in seconds.
 *
 * ```KlangScript
 * note("c3 e3").duck(1).duckdepth(0.8).duckatt(0.2)   // 200 ms recovery
 * ```
 *
 * ```KlangScript
 * note("c3*4").duckatt("<0.05 0.1 0.3 0.5>")          // varying recovery times
 * ```
 *
 * @alias duckattack
 * @category dynamics
 * @tags duckatt, duckattack, sidechain, ducking, release, dynamics
 */
@StrudelDsl
fun duckatt(time: PatternLike): StrudelPattern = _duckatt(listOf(time).asStrudelDslArgs())

/** Alias for [duckattack]. Sets the duck release time for this pattern. */
@StrudelDsl
fun StrudelPattern.duckatt(time: PatternLike): StrudelPattern = this._duckatt(listOf(time).asStrudelDslArgs())

/** Alias for [duckattack]. Parses this string as a pattern and sets the duck release time. */
@StrudelDsl
fun String.duckatt(time: PatternLike): StrudelPattern = this._duckatt(listOf(time).asStrudelDslArgs())

// -- duckdepth() ------------------------------------------------------------------------------------------------------

private val duckDepthMutation = voiceModifier { copy(duckDepth = it?.asDoubleOrNull()) }

private fun applyDuckDepth(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, duckDepthMutation)
}

internal val _duckdepth by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(duckDepthMutation) }
internal val StrudelPattern._duckdepth by dslPatternExtension { p, args, /* callInfo */ _ -> applyDuckDepth(p, args) }
internal val String._duckdepth by dslStringExtension { p, args, callInfo -> p._duckdepth(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the ducking depth (0.0 = no ducking, 1.0 = full silence) for sidechain ducking.
 *
 * Controls how much the pattern is attenuated when the sidechain trigger fires.
 * Use with `duckorbit` to set the sidechain source and `duckattack` for recovery time.
 *
 * ```KlangScript
 * note("c3 e3").duck(1).duckdepth(0.8)           // 80% attenuation on sidechain
 * ```
 *
 * ```KlangScript
 * note("c3*4").duckdepth("<0.3 0.6 0.9 1.0>")   // escalating ducking depth
 * ```
 *
 * @category dynamics
 * @tags duckdepth, sidechain, ducking, attenuation, dynamics
 */
@StrudelDsl
fun duckdepth(amount: PatternLike): StrudelPattern = _duckdepth(listOf(amount).asStrudelDslArgs())

/** Sets the ducking depth for this pattern. */
@StrudelDsl
fun StrudelPattern.duckdepth(amount: PatternLike): StrudelPattern = this._duckdepth(listOf(amount).asStrudelDslArgs())

/** Parses this string as a pattern and sets the ducking depth. */
@StrudelDsl
fun String.duckdepth(amount: PatternLike): StrudelPattern = this._duckdepth(listOf(amount).asStrudelDslArgs())
