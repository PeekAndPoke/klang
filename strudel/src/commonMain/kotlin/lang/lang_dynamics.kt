@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel._applyControlFromParams
import io.peekandpoke.klang.strudel._liftNumericField

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangDynamicsInit = false

// -- gain() -----------------------------------------------------------------------------------------------------------

private val gainMutation = voiceModifier { copy(gain = it?.asDoubleOrNull()) }

fun applyGain(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, gainMutation)
}

/** Modifies the gains of a pattern */
@StrudelDsl
val StrudelPattern.gain by dslPatternExtension { p, args, /* callInfo */ _ -> applyGain(p, args) }

/** Creates a pattern with gains */
@StrudelDsl
val gain by dslFunction { args, /* callInfo */ _ -> args.toPattern(gainMutation) }

/** Modifies the gains of a pattern defined by a string */
@StrudelDsl
val String.gain by dslStringExtension { p, args, callInfo -> p.gain(args, callInfo) }


// -- pan() ------------------------------------------------------------------------------------------------------------

private val panMutation = voiceModifier { copy(pan = it?.asDoubleOrNull()) }

fun applyPan(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, panMutation)
}

/** Modifies the pans of a pattern */
@StrudelDsl
val StrudelPattern.pan by dslPatternExtension { p, args, /* callInfo */ _ -> applyPan(p, args) }

/** Creates a pattern with pans */
@StrudelDsl
val pan by dslFunction { args, /* callInfo */ _ -> args.toPattern(panMutation) }

/** Modifies the pans of a pattern defined by a string */
@StrudelDsl
val String.pan by dslStringExtension { p, args, callInfo -> p.pan(args, callInfo) }

// -- velocity() -------------------------------------------------------------------------------------------------------

private val velocityMutation = voiceModifier { copy(velocity = it?.asDoubleOrNull()) }

fun applyVelocity(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, velocityMutation)
}

/** Modifies the velocity (volume scaling) of a pattern */
@StrudelDsl
val StrudelPattern.velocity by dslPatternExtension { p, args, /* callInfo */ _ -> applyVelocity(p, args) }

/** Creates a pattern with velocity */
@StrudelDsl
val velocity by dslFunction { args, /* callInfo */ _ -> args.toPattern(velocityMutation) }

/** Modifies the velocity of a pattern defined by a string */
@StrudelDsl
val String.velocity by dslStringExtension { p, args, callInfo -> p.velocity(args, callInfo) }

/** Alias for velocity */
@StrudelDsl
val StrudelPattern.vel by dslPatternExtension { p, args, callInfo -> p.velocity(args, callInfo) }

/** Alias for velocity */
@StrudelDsl
val vel by dslFunction { args, callInfo -> velocity(args, callInfo) }

/** Alias for velocity on a string */
@StrudelDsl
val String.vel by dslStringExtension { p, args, callInfo -> p.velocity(args, callInfo) }

// -- postgain() -------------------------------------------------------------------------------------------------------

private val postgainMutation = voiceModifier { copy(postGain = it?.asDoubleOrNull()) }

fun applyPostgain(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, postgainMutation)
}

/** Modifies the post-gain (applied after voice processing) of a pattern */
@StrudelDsl
val StrudelPattern.postgain by dslPatternExtension { p, args, /* callInfo */ _ -> applyPostgain(p, args) }

/** Creates a pattern with post-gain */
@StrudelDsl
val postgain by dslFunction { args, /* callInfo */ _ -> args.toPattern(postgainMutation) }

/** Modifies the post-gain of a pattern defined by a string */
@StrudelDsl
val String.postgain by dslStringExtension { p, args, callInfo -> p.postgain(args, callInfo) }

// -- compressor() -----------------------------------------------------------------------------------------------------

private val compressorMutation = voiceModifier { shape -> copy(compressor = shape?.toString()) }

fun applyCompressor(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._applyControlFromParams(args, compressorMutation) { src, ctrl ->
        src.copy(compressor = ctrl.compressor)
    }
}

/** Sets dynamic range compression parameters (threshold:ratio:knee:attack:release) */
@StrudelDsl
val StrudelPattern.compressor by dslPatternExtension { p, args, /* callInfo */ _ -> applyCompressor(p, args) }

/** Sets dynamic range compression parameters (threshold:ratio:knee:attack:release) */
@StrudelDsl
val compressor by dslFunction { args, /* callInfo */ _ -> args.toPattern(compressorMutation) }

/** Sets dynamic range compression parameters on a string */
@StrudelDsl
val String.compressor by dslStringExtension { p, args, callInfo -> p.compressor(args, callInfo) }

/** Alias for compressor */
@StrudelDsl
val StrudelPattern.comp by dslPatternExtension { p, args, callInfo -> p.compressor(args, callInfo) }

/** Alias for compressor */
@StrudelDsl
val comp by dslFunction { args, callInfo -> compressor(args, callInfo) }

/** Alias for compressor on a string */
@StrudelDsl
val String.comp by dslStringExtension { p, args, callInfo -> p.compressor(args, callInfo) }

// -- unison() ---------------------------------------------------------------------------------------------------------

private val unisonMutation = voiceModifier { copy(voices = it?.asDoubleOrNull()) }

private fun applyUnison(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, unisonMutation)
}

/** Modifies the voices of a pattern */
@StrudelDsl
val StrudelPattern.unison by dslPatternExtension { p, args, /* callInfo */ _ -> applyUnison(p, args) }

/** Creates a pattern with unison */
@StrudelDsl
val unison by dslFunction { args, /* callInfo */ _ -> args.toPattern(unisonMutation) }

/** Modifies the voices of a pattern defined by a string */
@StrudelDsl
val String.unison by dslStringExtension { p, args, callInfo -> p.unison(args, callInfo) }

/** Alias for [unison] */
@StrudelDsl
val StrudelPattern.uni by dslPatternExtension { p, args, callInfo -> p.unison(args, callInfo) }

/** Alias for [unison] */
@StrudelDsl
val uni by dslFunction { args, callInfo -> unison(args, callInfo) }

/** Alias for [unison] on a string */
@StrudelDsl
val String.uni by dslStringExtension { p, args, callInfo -> p.unison(args, callInfo) }

// -- detune() ---------------------------------------------------------------------------------------------------------

private val detuneMutation = voiceModifier { copy(freqSpread = it?.asDoubleOrNull()) }

private fun applyDetune(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, detuneMutation)
}

/** Sets the oscillator frequency spread (for supersaw) */
@StrudelDsl
val StrudelPattern.detune by dslPatternExtension { p, args, /* callInfo */ _ -> applyDetune(p, args) }

/** Sets the oscillator frequency spread (for supersaw) */
@StrudelDsl
val detune by dslFunction { args, /* callInfo */ _ -> args.toPattern(detuneMutation) }

/** Sets the oscillator frequency spread (for supersaw) on a string */
@StrudelDsl
val String.detune by dslStringExtension { p, args, callInfo -> p.detune(args, callInfo) }

// -- spread() ---------------------------------------------------------------------------------------------------------

private val spreadMutation = voiceModifier { copy(panSpread = it?.asDoubleOrNull()) }

private fun applySpread(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, spreadMutation)
}

/** Sets the oscillator pan spread (for supersaw) */
@StrudelDsl
val StrudelPattern.spread by dslPatternExtension { p, args, /* callInfo */ _ -> applySpread(p, args) }

/** Sets the oscillator pan spread (for supersaw) */
@StrudelDsl
val spread by dslFunction { args, /* callInfo */ _ -> args.toPattern(spreadMutation) }

/** Sets the oscillator pan spread (for supersaw) on a string */
@StrudelDsl
val String.spread by dslStringExtension { p, args, callInfo -> p.spread(args, callInfo) }

// -- density() --------------------------------------------------------------------------------------------------------

private val densityMutation = voiceModifier { copy(density = it?.asDoubleOrNull()) }

private fun applyDensity(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, densityMutation)
}

/** Sets the oscillator density (for supersaw) or noise density (for dust/crackle) */
@StrudelDsl
val StrudelPattern.density by dslPatternExtension { p, args, /* callInfo */ _ -> applyDensity(p, args) }

/** Sets the oscillator density (for supersaw) or noise density (for dust/crackle) */
@StrudelDsl
val density by dslFunction { args, /* callInfo */ _ -> args.toPattern(densityMutation) }

/** Sets the oscillator density (for supersaw) or noise density (for dust/crackle) on a string */
@StrudelDsl
val String.density by dslStringExtension { p, args, callInfo -> p.density(args, callInfo) }

/** Alias for [density] */
@StrudelDsl
val StrudelPattern.d by dslPatternExtension { p, args, callInfo -> p.density(args, callInfo) }

/** Alias for [density] */
@StrudelDsl
val d by dslFunction { args, callInfo -> density(args, callInfo) }

/** Alias for [density] on a string */
@StrudelDsl
val String.d by dslStringExtension { p, args, callInfo -> p.density(args, callInfo) }

// -- ADSR attack() ----------------------------------------------------------------------------------------------------

private val attackMutation = voiceModifier { copy(attack = it?.asDoubleOrNull()) }

private fun applyAttack(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, attackMutation)
}

/** Sets the note envelope attack */
@StrudelDsl
val StrudelPattern.attack by dslPatternExtension { p, args, /* callInfo */ _ -> applyAttack(p, args) }

/** Sets the note envelope attack */
@StrudelDsl
val attack by dslFunction { args, /* callInfo */ _ -> args.toPattern(attackMutation) }

/** Sets the note envelope attack on a string */
@StrudelDsl
val String.attack by dslStringExtension { p, args, callInfo -> p.attack(args, callInfo) }

// -- ADSR decay() -----------------------------------------------------------------------------------------------------

private val decayMutation = voiceModifier { copy(decay = it?.asDoubleOrNull()) }

private fun applyDecay(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, decayMutation)
}

/** Sets the note envelope decay */
@StrudelDsl
val StrudelPattern.decay by dslPatternExtension { p, args, /* callInfo */ _ -> applyDecay(p, args) }

/** Sets the note envelope decay */
@StrudelDsl
val decay by dslFunction { args, /* callInfo */ _ -> args.toPattern(decayMutation) }

/** Sets the note envelope decay on a string */
@StrudelDsl
val String.decay by dslStringExtension { p, args, callInfo -> p.decay(args, callInfo) }

// -- ADSR sustain() ---------------------------------------------------------------------------------------------------

private val sustainMutation = voiceModifier { copy(sustain = it?.asDoubleOrNull()) }

private fun applySustain(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, sustainMutation)
}

/** Sets the note envelope sustain */
@StrudelDsl
val StrudelPattern.sustain by dslPatternExtension { p, args, /* callInfo */ _ -> applySustain(p, args) }

/** Sets the note envelope sustain */
@StrudelDsl
val sustain by dslFunction { args, /* callInfo */ _ -> args.toPattern(sustainMutation) }

/** Sets the note envelope sustain on a string */
@StrudelDsl
val String.sustain by dslStringExtension { p, args, callInfo -> p.sustain(args, callInfo) }

// -- ADSR release() ---------------------------------------------------------------------------------------------------

private val releaseMutation = voiceModifier { copy(release = it?.asDoubleOrNull()) }

private fun applyRelease(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, releaseMutation)
}

/** Sets the note envelope release */
@StrudelDsl
val StrudelPattern.release by dslPatternExtension { p, args, /* callInfo */ _ -> applyRelease(p, args) }

/** Sets the note envelope release */
@StrudelDsl
val release by dslFunction { args, /* callInfo */ _ -> args.toPattern(releaseMutation) }

/** Sets the note envelope release on a string */
@StrudelDsl
val String.release by dslStringExtension { p, args, callInfo -> p.release(args, callInfo) }

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

/** Sets the note envelope via string or pattern */
@StrudelDsl
val StrudelPattern.adsr by dslPatternExtension { p, args, /* callInfo */ _ -> applyAdsr(p, args) }

/** Sets the note envelope via string or pattern */
@StrudelDsl
val adsr by dslFunction { args, /* callInfo */ _ -> args.toPattern(adsrMutation) }

/** Sets the note envelope via string or pattern on a string */
@StrudelDsl
val String.adsr by dslStringExtension { p, args, callInfo -> p.adsr(args, callInfo) }

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Routing
// ///

// -- orbit() ----------------------------------------------------------------------------------------------------------

private val orbitMutation = voiceModifier {
    copy(orbit = it?.asIntOrNull())
}

private fun applyOrbit(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, orbitMutation)
}

@StrudelDsl
val StrudelPattern.orbit by dslPatternExtension { p, args, /* callInfo */ _ -> applyOrbit(p, args) }

@StrudelDsl
val orbit by dslFunction { args, /* callInfo */ _ -> args.toPattern(orbitMutation) }

@StrudelDsl
val String.orbit by dslStringExtension { p, args, callInfo -> p.orbit(args, callInfo) }

/** Alias for [orbit] */
@StrudelDsl
val StrudelPattern.o by dslPatternExtension { p, args, callInfo -> p.orbit(args, callInfo) }

/** Alias for [orbit] */
@StrudelDsl
val o by dslFunction { args, callInfo -> orbit(args, callInfo) }

/** Alias for [orbit] on a string */
@StrudelDsl
val String.o by dslStringExtension { p, args, callInfo -> p.orbit(args, callInfo) }

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Ducking / Sidechain
// ///

// -- duckorbit() / duck() -----------------------------------------------------------------------------------------

private val duckOrbitMutation = voiceModifier {
    copy(duckOrbit = it?.asIntOrNull())
}

private fun applyDuckOrbit(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, duckOrbitMutation)
}

/** Sets the target orbit to listen to for ducking (sidechain source) */
@StrudelDsl
val StrudelPattern.duckorbit by dslPatternExtension { p, args, /* callInfo */ _ -> applyDuckOrbit(p, args) }

/** Sets the target orbit to listen to for ducking (sidechain source) */
@StrudelDsl
val duckorbit by dslFunction { args, /* callInfo */ _ -> args.toPattern(duckOrbitMutation) }

/** Sets the target orbit to listen to for ducking (sidechain source) on a string */
@StrudelDsl
val String.duckorbit by dslStringExtension { p, args, callInfo -> p.duckorbit(args, callInfo) }

/** Alias for [duckorbit] */
@StrudelDsl
val StrudelPattern.duck by dslPatternExtension { p, args, callInfo -> p.duckorbit(args, callInfo) }

/** Alias for [duckorbit] */
@StrudelDsl
val duck by dslFunction { args, callInfo -> duckorbit(args, callInfo) }

/** Alias for [duckorbit] on a string */
@StrudelDsl
val String.duck by dslStringExtension { p, args, callInfo -> p.duckorbit(args, callInfo) }

// -- duckattack() / duckatt() -------------------------------------------------------------------------------------

private val duckAttackMutation = voiceModifier { copy(duckAttack = it?.asDoubleOrNull()) }

private fun applyDuckAttack(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, duckAttackMutation)
}

/** Sets duck return-to-normal time in seconds (attack/release time) */
@StrudelDsl
val StrudelPattern.duckattack by dslPatternExtension { p, args, /* callInfo */ _ -> applyDuckAttack(p, args) }

/** Sets duck return-to-normal time in seconds (attack/release time) */
@StrudelDsl
val duckattack by dslFunction { args, /* callInfo */ _ -> args.toPattern(duckAttackMutation) }

/** Sets duck return-to-normal time in seconds (attack/release time) on a string */
@StrudelDsl
val String.duckattack by dslStringExtension { p, args, callInfo -> p.duckattack(args, callInfo) }

/** Alias for [duckattack] */
@StrudelDsl
val StrudelPattern.duckatt by dslPatternExtension { p, args, callInfo -> p.duckattack(args, callInfo) }

/** Alias for [duckattack] */
@StrudelDsl
val duckatt by dslFunction { args, callInfo -> duckattack(args, callInfo) }

/** Alias for [duckattack] on a string */
@StrudelDsl
val String.duckatt by dslStringExtension { p, args, callInfo -> p.duckattack(args, callInfo) }

// -- duckdepth() --------------------------------------------------------------------------------------------------

private val duckDepthMutation = voiceModifier { copy(duckDepth = it?.asDoubleOrNull()) }

private fun applyDuckDepth(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, duckDepthMutation)
}

/** Sets ducking amount (0.0 = no ducking, 1.0 = full silence) */
@StrudelDsl
val StrudelPattern.duckdepth by dslPatternExtension { p, args, /* callInfo */ _ -> applyDuckDepth(p, args) }

/** Sets ducking amount (0.0 = no ducking, 1.0 = full silence) */
@StrudelDsl
val duckdepth by dslFunction { args, /* callInfo */ _ -> args.toPattern(duckDepthMutation) }

/** Sets ducking amount (0.0 = no ducking, 1.0 = full silence) on a string */
@StrudelDsl
val String.duckdepth by dslStringExtension { p, args, callInfo -> p.duckdepth(args, callInfo) }
