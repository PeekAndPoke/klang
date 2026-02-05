@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel._applyControlFromParams
import io.peekandpoke.klang.strudel._liftNumericField

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangEffectsInit = false

// -- distort() --------------------------------------------------------------------------------------------------------

private val distortMutation = voiceModifier { copy(distort = it?.asDoubleOrNull()) }

fun applyDistort(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, distortMutation)
}

@StrudelDsl
val StrudelPattern.distort by dslPatternExtension { p, args, /* callInfo */ _ -> applyDistort(p, args) }

@StrudelDsl
val distort by dslFunction { args, /* callInfo */ _ -> args.toPattern(distortMutation) }

@StrudelDsl
val String.distort by dslStringExtension { p, args, /* callInfo */ _ -> applyDistort(p, args) }

/** Alias for [distort] */
@StrudelDsl
val StrudelPattern.dist by dslPatternExtension { p, args, callInfo -> p.distort(args, callInfo) }

/** Alias for [distort] */
@StrudelDsl
val dist by dslFunction { args, callInfo -> distort(args, callInfo) }

/** Alias for [distort] on a string */
@StrudelDsl
val String.dist by dslStringExtension { p, args, callInfo -> p.distort(args, callInfo) }

// -- crush() ----------------------------------------------------------------------------------------------------------

private val crushMutation = voiceModifier { copy(crush = it?.asDoubleOrNull()) }

fun applyCrush(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, crushMutation)
}

@StrudelDsl
val StrudelPattern.crush by dslPatternExtension { p, args, /* callInfo */ _ -> applyCrush(p, args) }

@StrudelDsl
val crush by dslFunction { args, /* callInfo */ _ -> args.toPattern(crushMutation) }

@StrudelDsl
val String.crush by dslStringExtension { p, args, /* callInfo */ _ -> applyCrush(p, args) }

// -- coarse() ---------------------------------------------------------------------------------------------------------

private val coarseMutation = voiceModifier { copy(coarse = it?.asDoubleOrNull()) }

fun applyCoarse(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, coarseMutation)
}

@StrudelDsl
val StrudelPattern.coarse by dslPatternExtension { p, args, /* callInfo */ _ -> applyCoarse(p, args) }

@StrudelDsl
val coarse by dslFunction { args, /* callInfo */ _ -> args.toPattern(coarseMutation) }

@StrudelDsl
val String.coarse by dslStringExtension { p, args, /* callInfo */ _ -> applyCoarse(p, args) }

// -- room() -----------------------------------------------------------------------------------------------------------

private val roomMutation = voiceModifier { copy(room = it?.asDoubleOrNull()) }

fun applyRoom(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, roomMutation)
}

@StrudelDsl
val StrudelPattern.room by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoom(p, args) }

@StrudelDsl
val room by dslFunction { args, /* callInfo */ _ -> args.toPattern(roomMutation) }

@StrudelDsl
val String.room by dslStringExtension { p, args, /* callInfo */ _ -> applyRoom(p, args) }

// -- roomsize() / rsize() ---------------------------------------------------------------------------------------------

private val roomSizeMutation = voiceModifier { copy(roomSize = it?.asDoubleOrNull()) }

fun applyRoomSize(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, roomSizeMutation)
}

@StrudelDsl
val StrudelPattern.roomsize by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoomSize(p, args) }

@StrudelDsl
val roomsize by dslFunction { args, /* callInfo */ _ -> args.toPattern(roomSizeMutation) }

@StrudelDsl
val String.roomsize by dslStringExtension { p, args, /* callInfo */ _ -> applyRoomSize(p, args) }

/** Alias for [roomsize] */
@StrudelDsl
val StrudelPattern.rsize by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoomSize(p, args) }

/** Alias for [roomsize] */
@StrudelDsl
val rsize by dslFunction { args, /* callInfo */ _ -> args.toPattern(roomSizeMutation) }

/** Alias for [roomsize] on a string */
@StrudelDsl
val String.rsize by dslStringExtension { p, args, /* callInfo */ _ -> applyRoomSize(p, args) }

/** Alias for [roomsize] */
@StrudelDsl
val StrudelPattern.sz by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoomSize(p, args) }

/** Alias for [roomsize] */
@StrudelDsl
val sz by dslFunction { args, /* callInfo */ _ -> args.toPattern(roomSizeMutation) }

/** Alias for [roomsize] on a string */
@StrudelDsl
val String.sz by dslStringExtension { p, args, /* callInfo */ _ -> applyRoomSize(p, args) }

/** Alias for [roomsize] */
@StrudelDsl
val StrudelPattern.size by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoomSize(p, args) }

/** Alias for [roomsize] */
@StrudelDsl
val size by dslFunction { args, /* callInfo */ _ -> args.toPattern(roomSizeMutation) }

/** Alias for [roomsize] on a string */
@StrudelDsl
val String.size by dslStringExtension { p, args, /* callInfo */ _ -> applyRoomSize(p, args) }

// -- roomfade() -------------------------------------------------------------------------------------------------------

private val roomFadeMutation = voiceModifier { copy(roomFade = it?.asDoubleOrNull()) }

fun applyRoomFade(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, roomFadeMutation)
}

/** Sets the reverb fade time */
@StrudelDsl
val StrudelPattern.roomfade by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoomFade(p, args) }

/** Sets the reverb fade time */
@StrudelDsl
val roomfade by dslFunction { args, /* callInfo */ _ -> args.toPattern(roomFadeMutation) }

/** Sets the reverb fade time on a string */
@StrudelDsl
val String.roomfade by dslStringExtension { p, args, /* callInfo */ _ -> applyRoomFade(p, args) }

/** Alias for [roomfade] */
@StrudelDsl
val StrudelPattern.rfade by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoomFade(p, args) }

/** Alias for [roomfade] */
@StrudelDsl
val rfade by dslFunction { args, /* callInfo */ _ -> args.toPattern(roomFadeMutation) }

/** Alias for [roomfade] on a string */
@StrudelDsl
val String.rfade by dslStringExtension { p, args, /* callInfo */ _ -> applyRoomFade(p, args) }

// -- roomlp() ---------------------------------------------------------------------------------------------------------

private val roomLpMutation = voiceModifier { copy(roomLp = it?.asDoubleOrNull()) }

fun applyRoomLp(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, roomLpMutation)
}

/** Sets the reverb lowpass start frequency */
@StrudelDsl
val StrudelPattern.roomlp by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoomLp(p, args) }

/** Sets the reverb lowpass start frequency */
@StrudelDsl
val roomlp by dslFunction { args, /* callInfo */ _ -> args.toPattern(roomLpMutation) }

/** Sets the reverb lowpass start frequency on a string */
@StrudelDsl
val String.roomlp by dslStringExtension { p, args, /* callInfo */ _ -> applyRoomLp(p, args) }

/** Alias for [roomlp] */
@StrudelDsl
val StrudelPattern.rlp by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoomLp(p, args) }

/** Alias for [roomlp] */
@StrudelDsl
val rlp by dslFunction { args, /* callInfo */ _ -> args.toPattern(roomLpMutation) }

/** Alias for [roomlp] on a string */
@StrudelDsl
val String.rlp by dslStringExtension { p, args, /* callInfo */ _ -> applyRoomLp(p, args) }

// -- roomdim() --------------------------------------------------------------------------------------------------------

private val roomDimMutation = voiceModifier { copy(roomDim = it?.asDoubleOrNull()) }

fun applyRoomDim(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, roomDimMutation)
}

/** Sets the reverb lowpass frequency at -60dB */
@StrudelDsl
val StrudelPattern.roomdim by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoomDim(p, args) }

/** Sets the reverb lowpass frequency at -60dB */
@StrudelDsl
val roomdim by dslFunction { args, /* callInfo */ _ -> args.toPattern(roomDimMutation) }

/** Sets the reverb lowpass frequency at -60dB on a string */
@StrudelDsl
val String.roomdim by dslStringExtension { p, args, /* callInfo */ _ -> applyRoomDim(p, args) }

/** Alias for [roomdim] */
@StrudelDsl
val StrudelPattern.rdim by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoomDim(p, args) }

/** Alias for [roomdim] */
@StrudelDsl
val rdim by dslFunction { args, /* callInfo */ _ -> args.toPattern(roomDimMutation) }

/** Alias for [roomdim] on a string */
@StrudelDsl
val String.rdim by dslStringExtension { p, args, /* callInfo */ _ -> applyRoomDim(p, args) }

// -- iresponse() ------------------------------------------------------------------------------------------------------

private val iResponseMutation = voiceModifier { response -> copy(iResponse = response?.toString()) }

fun applyIResponse(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._applyControlFromParams(args, iResponseMutation) { src, ctrl ->
        src.copy(iResponse = ctrl.iResponse)
    }
}

/** Sets the impulse response sample */
@StrudelDsl
val StrudelPattern.iresponse by dslPatternExtension { p, args, /* callInfo */ _ -> applyIResponse(p, args) }

/** Sets the impulse response sample */
@StrudelDsl
val iresponse by dslFunction { args, /* callInfo */ _ -> args.toPattern(iResponseMutation) }

/** Sets the impulse response sample on a string */
@StrudelDsl
val String.iresponse by dslStringExtension { p, args, /* callInfo */ _ -> applyIResponse(p, args) }

/** Alias for [iresponse] */
@StrudelDsl
val StrudelPattern.ir by dslPatternExtension { p, args, /* callInfo */ _ -> applyIResponse(p, args) }

/** Alias for [iresponse] */
@StrudelDsl
val ir by dslFunction { args, /* callInfo */ _ -> args.toPattern(iResponseMutation) }

/** Alias for [iresponse] on a string */
@StrudelDsl
val String.ir by dslStringExtension { p, args, /* callInfo */ _ -> applyIResponse(p, args) }

// -- delay() ----------------------------------------------------------------------------------------------------------

private val delayMutation = voiceModifier { copy(delay = it?.asDoubleOrNull()) }

fun applyDelay(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, delayMutation)
}

@StrudelDsl
val StrudelPattern.delay by dslPatternExtension { p, args, /* callInfo */ _ -> applyDelay(p, args) }

@StrudelDsl
val delay by dslFunction { args, /* callInfo */ _ -> args.toPattern(delayMutation) }

@StrudelDsl
val String.delay by dslStringExtension { p, args, /* callInfo */ _ -> applyDelay(p, args) }

// -- delaytime() ------------------------------------------------------------------------------------------------------

private val delayTimeMutation = voiceModifier { copy(delayTime = it?.asDoubleOrNull()) }

fun applyDelayTime(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, delayTimeMutation)
}

@StrudelDsl
val StrudelPattern.delaytime by dslPatternExtension { p, args, /* callInfo */ _ -> applyDelayTime(p, args) }

@StrudelDsl
val delaytime by dslFunction { args, /* callInfo */ _ -> args.toPattern(delayTimeMutation) }

@StrudelDsl
val String.delaytime by dslStringExtension { p, args, /* callInfo */ _ -> applyDelayTime(p, args) }

// -- delayfeedback() --------------------------------------------------------------------------------------------------

private val delayFeedbackMutation = voiceModifier { copy(delayFeedback = it?.asDoubleOrNull()) }

fun applyDelayFeedback(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, delayFeedbackMutation)
}

@StrudelDsl
val StrudelPattern.delayfeedback by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyDelayFeedback(p, args)
}

@StrudelDsl
val delayfeedback by dslFunction { args, /* callInfo */ _ -> args.toPattern(delayFeedbackMutation) }

@StrudelDsl
val String.delayfeedback by dslStringExtension { p, args, /* callInfo */ _ -> applyDelayFeedback(p, args) }

/** Alias for [delayfeedback] */
@StrudelDsl
val StrudelPattern.delayfb by dslPatternExtension { p, args, callInfo -> p.delayfeedback(args, callInfo) }

/** Alias for [delayfeedback] */
@StrudelDsl
val delayfb by dslFunction { args, callInfo -> delayfeedback(args, callInfo) }

/** Alias for [delayfeedback] on a string */
@StrudelDsl
val String.delayfb by dslStringExtension { p, args, callInfo -> p.delayfeedback(args, callInfo) }

/** Alias for [delayfeedback] */
@StrudelDsl
val StrudelPattern.dfb by dslPatternExtension { p, args, callInfo -> p.delayfeedback(args, callInfo) }

/** Alias for [delayfeedback] */
@StrudelDsl
val dfb by dslFunction { args, callInfo -> delayfeedback(args, callInfo) }

/** Alias for [delayfeedback] on a string */
@StrudelDsl
val String.dfb by dslStringExtension { p, args, callInfo -> p.delayfeedback(args, callInfo) }

// -- phaser() ---------------------------------------------------------------------------------------------------------

private val phaserMutation = voiceModifier { copy(phaserRate = it?.asDoubleOrNull()) }

fun applyPhaser(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, phaserMutation)
}

@StrudelDsl
val StrudelPattern.phaser by dslPatternExtension { p, args, /* callInfo */ _ -> applyPhaser(p, args) }

@StrudelDsl
val phaser by dslFunction { args, /* callInfo */ _ -> args.toPattern(phaserMutation) }

@StrudelDsl
val String.phaser by dslStringExtension { p, args, /* callInfo */ _ -> applyPhaser(p, args) }

/** Alias for [phaser] */
@StrudelDsl
val StrudelPattern.ph by dslPatternExtension { p, args, callInfo -> p.phaser(args, callInfo) }

/** Alias for [phaser] */
@StrudelDsl
val ph by dslFunction { args, callInfo -> phaser(args, callInfo) }

/** Alias for [phaser] on a string */
@StrudelDsl
val String.ph by dslStringExtension { p, args, callInfo -> p.phaser(args, callInfo) }

// -- phaserdepth() ----------------------------------------------------------------------------------------------------

private val phaserDepthMutation = voiceModifier { copy(phaserDepth = it?.asDoubleOrNull()) }

fun applyPhaserDepth(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, phaserDepthMutation)
}

@StrudelDsl
val StrudelPattern.phaserdepth by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyPhaserDepth(p, args)
}

@StrudelDsl
val phaserdepth by dslFunction { args, /* callInfo */ _ -> args.toPattern(phaserDepthMutation) }

@StrudelDsl
val String.phaserdepth by dslStringExtension { p, args, /* callInfo */ _ -> applyPhaserDepth(p, args) }

/** Alias for [phaserdepth] */
@StrudelDsl
val StrudelPattern.phd by dslPatternExtension { p, args, callInfo -> p.phaserdepth(args, callInfo) }

/** Alias for [phaserdepth] */
@StrudelDsl
val phd by dslFunction { args, callInfo -> phaserdepth(args, callInfo) }

/** Alias for [phaserdepth] on a string */
@StrudelDsl
val String.phd by dslStringExtension { p, args, callInfo -> p.phaserdepth(args, callInfo) }

/** Alias for [phaserdepth] */
@StrudelDsl
val StrudelPattern.phasdp by dslPatternExtension { p, args, callInfo -> p.phaserdepth(args, callInfo) }

/** Alias for [phaserdepth] */
@StrudelDsl
val phasdp by dslFunction { args, callInfo -> phaserdepth(args, callInfo) }

/** Alias for [phaserdepth] on a string */
@StrudelDsl
val String.phasdp by dslStringExtension { p, args, callInfo -> p.phaserdepth(args, callInfo) }

// -- phasercenter() ---------------------------------------------------------------------------------------------------

private val phaserCenterMutation = voiceModifier { copy(phaserCenter = it?.asDoubleOrNull()) }

fun applyPhaserCenter(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, phaserCenterMutation)
}

@StrudelDsl
val StrudelPattern.phasercenter by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyPhaserCenter(p, args)
}

@StrudelDsl
val phasercenter by dslFunction { args, /* callInfo */ _ -> args.toPattern(phaserCenterMutation) }

@StrudelDsl
val String.phasercenter by dslStringExtension { p, args, /* callInfo */ _ -> applyPhaserCenter(p, args) }

/** Alias for [phasercenter] */
@StrudelDsl
val StrudelPattern.phc by dslPatternExtension { p, args, callInfo -> p.phasercenter(args, callInfo) }

/** Alias for [phasercenter] */
@StrudelDsl
val phc by dslFunction { args, callInfo -> phasercenter(args, callInfo) }

/** Alias for [phasercenter] on a string */
@StrudelDsl
val String.phc by dslStringExtension { p, args, callInfo -> p.phasercenter(args, callInfo) }

// -- phasersweep() ----------------------------------------------------------------------------------------------------

private val phaserSweepMutation = voiceModifier { copy(phaserSweep = it?.asDoubleOrNull()) }

fun applyPhaserSweep(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, phaserSweepMutation)
}

@StrudelDsl
val StrudelPattern.phasersweep by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyPhaserSweep(p, args)
}

@StrudelDsl
val phasersweep by dslFunction { args, /* callInfo */ _ -> args.toPattern(phaserSweepMutation) }

@StrudelDsl
val String.phasersweep by dslStringExtension { p, args, /* callInfo */ _ -> applyPhaserSweep(p, args) }

/** Alias for [phasersweep] */
@StrudelDsl
val StrudelPattern.phs by dslPatternExtension { p, args, callInfo -> p.phasersweep(args, callInfo) }

/** Alias for [phasersweep] */
@StrudelDsl
val phs by dslFunction { args, callInfo -> phasersweep(args, callInfo) }

/** Alias for [phasersweep] on a string */
@StrudelDsl
val String.phs by dslStringExtension { p, args, callInfo -> p.phasersweep(args, callInfo) }

// -- tremolosync() ----------------------------------------------------------------------------------------------------

private val tremoloSyncMutation = voiceModifier { copy(tremoloSync = it?.asDoubleOrNull()) }

fun applyTremoloSync(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, tremoloSyncMutation)
}

@StrudelDsl
val StrudelPattern.tremolosync by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTremoloSync(p, args)
}

@StrudelDsl
val tremolosync by dslFunction { args, /* callInfo */ _ -> args.toPattern(tremoloSyncMutation) }

@StrudelDsl
val String.tremolosync by dslStringExtension { p, args, /* callInfo */ _ -> applyTremoloSync(p, args) }

/** Alias for [tremolosync] */
@StrudelDsl
val StrudelPattern.tremsync by dslPatternExtension { p, args, callInfo -> p.tremolosync(args, callInfo) }

/** Alias for [tremolosync] */
@StrudelDsl
val tremsync by dslFunction { args, callInfo -> tremolosync(args, callInfo) }

/** Alias for [tremolosync] on a string */
@StrudelDsl
val String.tremsync by dslStringExtension { p, args, callInfo -> p.tremolosync(args, callInfo) }

// -- tremolodepth() ---------------------------------------------------------------------------------------------------

private val tremoloDepthMutation = voiceModifier { copy(tremoloDepth = it?.asDoubleOrNull()) }

fun applyTremoloDepth(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, tremoloDepthMutation)
}

@StrudelDsl
val StrudelPattern.tremolodepth by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTremoloDepth(p, args)
}

@StrudelDsl
val tremolodepth by dslFunction { args, /* callInfo */ _ -> args.toPattern(tremoloDepthMutation) }

@StrudelDsl
val String.tremolodepth by dslStringExtension { p, args, /* callInfo */ _ -> applyTremoloDepth(p, args) }

/** Alias for [tremolodepth] */
@StrudelDsl
val StrudelPattern.tremdepth by dslPatternExtension { p, args, callInfo -> p.tremolodepth(args, callInfo) }

/** Alias for [tremolodepth] */
@StrudelDsl
val tremdepth by dslFunction { args, callInfo -> tremolodepth(args, callInfo) }

/** Alias for [tremolodepth] on a string */
@StrudelDsl
val String.tremdepth by dslStringExtension { p, args, callInfo -> p.tremolodepth(args, callInfo) }

// -- tremoloskew() ----------------------------------------------------------------------------------------------------

private val tremoloSkewMutation = voiceModifier { copy(tremoloSkew = it?.asDoubleOrNull()) }

fun applyTremoloSkew(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, tremoloSkewMutation)
}

@StrudelDsl
val StrudelPattern.tremoloskew by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTremoloSkew(p, args)
}

@StrudelDsl
val tremoloskew by dslFunction { args, /* callInfo */ _ -> args.toPattern(tremoloSkewMutation) }

@StrudelDsl
val String.tremoloskew by dslStringExtension { p, args, /* callInfo */ _ -> applyTremoloSkew(p, args) }

/** Alias for [tremoloskew] */
@StrudelDsl
val StrudelPattern.tremskew by dslPatternExtension { p, args, callInfo -> p.tremoloskew(args, callInfo) }

/** Alias for [tremoloskew] */
@StrudelDsl
val tremskew by dslFunction { args, callInfo -> tremoloskew(args, callInfo) }

/** Alias for [tremoloskew] on a string */
@StrudelDsl
val String.tremskew by dslStringExtension { p, args, callInfo -> p.tremoloskew(args, callInfo) }

// -- tremolophase() ---------------------------------------------------------------------------------------------------

private val tremoloPhaseMutation = voiceModifier { copy(tremoloPhase = it?.asDoubleOrNull()) }

fun applyTremoloPhase(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, tremoloPhaseMutation)
}

@StrudelDsl
val StrudelPattern.tremolophase by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTremoloPhase(p, args)
}

@StrudelDsl
val tremolophase by dslFunction { args, /* callInfo */ _ -> args.toPattern(tremoloPhaseMutation) }

@StrudelDsl
val String.tremolophase by dslStringExtension { p, args, /* callInfo */ _ -> applyTremoloPhase(p, args) }

/** Alias for [tremolophase] */
@StrudelDsl
val StrudelPattern.tremphase by dslPatternExtension { p, args, callInfo -> p.tremolophase(args, callInfo) }

/** Alias for [tremolophase] */
@StrudelDsl
val tremphase by dslFunction { args, callInfo -> tremolophase(args, callInfo) }

/** Alias for [tremolophase] on a string */
@StrudelDsl
val String.tremphase by dslStringExtension { p, args, callInfo -> p.tremolophase(args, callInfo) }

// -- tremoloshape() ---------------------------------------------------------------------------------------------------

private val tremoloShapeMutation = voiceModifier { shape -> copy(tremoloShape = shape?.toString()?.lowercase()) }

fun applyTremoloShape(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._applyControlFromParams(args, tremoloShapeMutation) { src, ctrl ->
        src.copy(tremoloShape = ctrl.tremoloShape)
    }
}

@StrudelDsl
val StrudelPattern.tremoloshape by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTremoloShape(p, args)
}

@StrudelDsl
val tremoloshape by dslFunction { args, /* callInfo */ _ -> args.toPattern(tremoloShapeMutation) }

@StrudelDsl
val String.tremoloshape by dslStringExtension { p, args, /* callInfo */ _ -> applyTremoloShape(p, args) }

/** Alias for [tremoloshape] */
@StrudelDsl
val StrudelPattern.tremshape by dslPatternExtension { p, args, callInfo -> p.tremoloshape(args, callInfo) }

/** Alias for [tremoloshape] */
@StrudelDsl
val tremshape by dslFunction { args, callInfo -> tremoloshape(args, callInfo) }

/** Alias for [tremoloshape] on a string */
@StrudelDsl
val String.tremshape by dslStringExtension { p, args, callInfo -> p.tremoloshape(args, callInfo) }
