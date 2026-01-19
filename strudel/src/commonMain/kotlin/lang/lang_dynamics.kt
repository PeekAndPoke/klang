@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.audio_bridge.AdsrEnvelope
import io.peekandpoke.klang.strudel.StrudelPattern

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangDynamicsInit = false

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Dynamics, Gain, Pan, Envelope ...
// ///

// -- gain() -----------------------------------------------------------------------------------------------------------

private val gainMutation = voiceModifier {
    copy(gain = it?.asDoubleOrNull())
}

private fun applyGain(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = gainMutation,
        getValue = { gain },
        setValue = { v, _ -> copy(gain = v) },
    )
}

/** Modifies the gains of a pattern */
@StrudelDsl
val StrudelPattern.gain by dslPatternExtension { p, args, callInfo ->
    applyGain(p, args)
}

/** Creates a pattern with gains */
@StrudelDsl
val gain by dslFunction { args, callInfo -> args.toPattern(gainMutation) }

/** Modifies the gains of a pattern defined by a string */
@StrudelDsl
val String.gain by dslStringExtension { p, args, callInfo ->
    applyGain(p, args)
}

// -- pan() ------------------------------------------------------------------------------------------------------------

private val panMutation = voiceModifier {
    copy(pan = it?.asDoubleOrNull())
}

private fun applyPan(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = panMutation,
        getValue = { pan },
        setValue = { v, _ -> copy(pan = v) },
    )
}

/** Modifies the pans of a pattern */
@StrudelDsl
val StrudelPattern.pan by dslPatternExtension { p, args, callInfo ->
    applyPan(p, args)
}

/** Creates a pattern with pans */
@StrudelDsl
val pan by dslFunction { args, callInfo -> args.toPattern(panMutation) }

/** Modifies the pans of a pattern defined by a string */
@StrudelDsl
val String.pan by dslStringExtension { p, args, callInfo ->
    applyPan(p, args)
}

// -- unison() ---------------------------------------------------------------------------------------------------------

private val unisonMutation = voiceModifier {
    copy(voices = it?.asDoubleOrNull())
}

private fun applyUnison(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = unisonMutation,
        getValue = { voices },
        setValue = { v, _ -> copy(voices = v) },
    )
}

/** Modifies the voices of a pattern */
@StrudelDsl
val StrudelPattern.unison by dslPatternExtension { p, args, callInfo ->
    applyUnison(p, args)
}

/** Creates a pattern with unison */
@StrudelDsl
val unison by dslFunction { args, callInfo -> args.toPattern(unisonMutation) }

/** Modifies the voices of a pattern defined by a string */
@StrudelDsl
val String.unison by dslStringExtension { p, args, callInfo ->
    applyUnison(p, args)
}

/** Alias for [unison] */
@StrudelDsl
val StrudelPattern.uni by dslPatternExtension { p, args, callInfo -> applyUnison(p, args) }

/** Alias for [unison] */
@StrudelDsl
val uni by dslFunction { args, callInfo -> args.toPattern(unisonMutation) }

/** Alias for [unison] on a string */
@StrudelDsl
val String.uni by dslStringExtension { p, args, callInfo -> applyUnison(p, args) }

// -- detune() ---------------------------------------------------------------------------------------------------------

private val detuneMutation = voiceModifier {
    copy(freqSpread = it?.asDoubleOrNull())
}

private fun applyDetune(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args,
        modify = detuneMutation,
        getValue = { freqSpread },
        setValue = { v, _ -> copy(freqSpread = v) },
    )
}

/** Sets the oscillator frequency spread (for supersaw) */
@StrudelDsl
val StrudelPattern.detune by dslPatternExtension { p, args, callInfo ->
    applyDetune(p, args)
}

/** Sets the oscillator frequency spread (for supersaw) */
@StrudelDsl
val detune by dslFunction { args, callInfo -> args.toPattern(detuneMutation) }

/** Sets the oscillator frequency spread (for supersaw) on a string */
@StrudelDsl
val String.detune by dslStringExtension { p, args, callInfo ->
    applyDetune(p, args)
}

// -- spread() ---------------------------------------------------------------------------------------------------------

private val spreadMutation = voiceModifier {
    copy(panSpread = it?.asDoubleOrNull())
}

private fun applySpread(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = spreadMutation,
        getValue = { panSpread },
        setValue = { v, _ -> copy(panSpread = v) },
    )
}

/** Sets the oscillator pan spread (for supersaw) */
@StrudelDsl
val StrudelPattern.spread by dslPatternExtension { p, args, callInfo ->
    applySpread(p, args)
}

/** Sets the oscillator pan spread (for supersaw) */
@StrudelDsl
val spread by dslFunction { args, callInfo -> args.toPattern(spreadMutation) }

/** Sets the oscillator pan spread (for supersaw) on a string */
@StrudelDsl
val String.spread by dslStringExtension { p, args, callInfo ->
    applySpread(p, args)
}

// -- density() --------------------------------------------------------------------------------------------------------

private val densityMutation = voiceModifier {
    copy(density = it?.asDoubleOrNull())
}

private fun applyDensity(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = densityMutation,
        getValue = { density },
        setValue = { v, _ -> copy(density = v) },
    )
}

/** Sets the oscillator density (for supersaw) */
@StrudelDsl
val StrudelPattern.density by dslPatternExtension { p, args, callInfo ->
    applyDensity(p, args)
}

/** Sets the oscillator density (for supersaw) */
@StrudelDsl
val density by dslFunction { args, callInfo -> args.toPattern(densityMutation) }

/** Sets the oscillator density (for supersaw) on a string */
@StrudelDsl
val String.density by dslStringExtension { p, args, callInfo ->
    applyDensity(p, args)
}

/** Alias for [density] */
@StrudelDsl
val StrudelPattern.d by dslPatternExtension { p, args, callInfo -> applyDensity(p, args) }

/** Alias for [density] */
@StrudelDsl
val d by dslFunction { args, callInfo -> args.toPattern(densityMutation) }

/** Alias for [density] on a string */
@StrudelDsl
val String.d by dslStringExtension { p, args, callInfo -> applyDensity(p, args) }

// -- ADSR attack() ----------------------------------------------------------------------------------------------------

private val attackMutation = voiceModifier {
    copy(adsr = adsr.copy(attack = it?.asDoubleOrNull()))
}

private fun applyAttack(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = attackMutation,
        getValue = { adsr.attack },
        setValue = { v, _ -> copy(adsr = adsr.copy(attack = v)) },
    )
}

/** Sets the note envelope attack */
@StrudelDsl
val StrudelPattern.attack by dslPatternExtension { p, args, callInfo ->
    applyAttack(p, args)
}

/** Sets the note envelope attack */
@StrudelDsl
val attack by dslFunction { args, callInfo -> args.toPattern(attackMutation) }

/** Sets the note envelope attack on a string */
@StrudelDsl
val String.attack by dslStringExtension { p, args, callInfo ->
    applyAttack(p, args)
}

// -- ADSR decay() -----------------------------------------------------------------------------------------------------

private val decayMutation = voiceModifier {
    copy(adsr = adsr.copy(decay = it?.asDoubleOrNull()))
}

private fun applyDecay(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = decayMutation,
        getValue = { adsr.decay },
        setValue = { v, _ -> copy(adsr = adsr.copy(decay = v)) },
    )
}

/** Sets the note envelope decay */
@StrudelDsl
val StrudelPattern.decay by dslPatternExtension { p, args, callInfo ->
    applyDecay(p, args)
}

/** Sets the note envelope decay */
@StrudelDsl
val decay by dslFunction { args, callInfo -> args.toPattern(decayMutation) }

/** Sets the note envelope decay on a string */
@StrudelDsl
val String.decay by dslStringExtension { p, args, callInfo ->
    applyDecay(p, args)
}

// -- ADSR sustain() ---------------------------------------------------------------------------------------------------

private val sustainMutation = voiceModifier {
    copy(adsr = adsr.copy(sustain = it?.asDoubleOrNull()))
}

private fun applySustain(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = sustainMutation,
        getValue = { adsr.sustain },
        setValue = { v, _ -> copy(adsr = adsr.copy(sustain = v)) },
    )
}

/** Sets the note envelope sustain */
@StrudelDsl
val StrudelPattern.sustain by dslPatternExtension { p, args, callInfo ->
    applySustain(p, args)
}

/** Sets the note envelope sustain */
@StrudelDsl
val sustain by dslFunction { args, callInfo -> args.toPattern(sustainMutation) }

/** Sets the note envelope sustain on a string */
@StrudelDsl
val String.sustain by dslStringExtension { p, args, callInfo ->
    applySustain(p, args)
}

// -- ADSR release() ---------------------------------------------------------------------------------------------------

private val releaseMutation = voiceModifier {
    copy(adsr = adsr.copy(release = it?.asDoubleOrNull()))
}

private fun applyRelease(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = releaseMutation,
        getValue = { adsr.release },
        setValue = { v, _ -> copy(adsr = adsr.copy(release = v)) },
    )
}

/** Sets the note envelope release */
@StrudelDsl
val StrudelPattern.release by dslPatternExtension { p, args, callInfo ->
    applyRelease(p, args)
}

/** Sets the note envelope release */
@StrudelDsl
val release by dslFunction { args, callInfo -> args.toPattern(releaseMutation) }

/** Sets the note envelope release on a string */
@StrudelDsl
val String.release by dslStringExtension { p, args, callInfo ->
    applyRelease(p, args)
}

// -- ADSR adsr() ------------------------------------------------------------------------------------------------------

private val adsrMutation = voiceModifier {
    val parts = it?.toString()?.split(":")
        ?.mapNotNull { d -> d.toDoubleOrNull() } ?: emptyList()

    val newAdsr = AdsrEnvelope(
        attack = parts.getOrNull(0),
        decay = parts.getOrNull(1),
        sustain = parts.getOrNull(2),
        release = parts.getOrNull(3),
    )

    copy(adsr = newAdsr.mergeWith(adsr))
}

private fun applyAdsr(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyControlFromParams(args, adsrMutation) { src, ctrl ->
        src.copy(adsr = ctrl.adsr.mergeWith(src.adsr))
    }
}

/** Sets the note envelope via string or pattern */
@StrudelDsl
val StrudelPattern.adsr by dslPatternExtension { p, args, callInfo ->
    applyAdsr(p, args)
}

/** Sets the note envelope via string or pattern */
@StrudelDsl
val adsr by dslFunction { args, callInfo -> args.toPattern(adsrMutation) }

/** Sets the note envelope via string or pattern on a string */
@StrudelDsl
val String.adsr by dslStringExtension { p, args, callInfo ->
    applyAdsr(p, args)
}

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Routing
// ///

// -- orbit() ----------------------------------------------------------------------------------------------------------

private val orbitMutation = voiceModifier {
    copy(orbit = it?.asIntOrNull())
}

private fun applyOrbit(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = orbitMutation,
        getValue = { orbit?.toDouble() },
        setValue = { v, _ -> copy(orbit = v.toInt()) }
    )
}

@StrudelDsl
val StrudelPattern.orbit by dslPatternExtension { p, args, callInfo ->
    applyOrbit(p, args)
}

@StrudelDsl
val orbit by dslFunction { args, callInfo -> args.toPattern(orbitMutation) }

@StrudelDsl
val String.orbit by dslStringExtension { p, args, callInfo ->
    applyOrbit(p, args)
}
