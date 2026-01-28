@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangEffectsInit = false

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Effects
// ///

// -- distort() --------------------------------------------------------------------------------------------------------

private val distortMutation = voiceModifier {
    copy(distort = it?.asDoubleOrNull())
}

private fun applyDistort(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = distortMutation,
        getValue = { distort },
        setValue = { v, _ -> copy(distort = v) },
    )
}

@StrudelDsl
val StrudelPattern.distort by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyDistort(p, args)
}

@StrudelDsl
val distort by dslFunction { args, /* callInfo */ _ -> args.toPattern(distortMutation) }

@StrudelDsl
val String.distort by dslStringExtension { p, args, /* callInfo */ _ ->
    applyDistort(p, args)
}

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

private fun applyCrush(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = crushMutation,
        getValue = { crush },
        setValue = { v, _ -> copy(crush = v) },
    )
}

@StrudelDsl
val StrudelPattern.crush by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyCrush(p, args)
}

@StrudelDsl
val crush by dslFunction { args, /* callInfo */ _ -> args.toPattern(crushMutation) }

@StrudelDsl
val String.crush by dslStringExtension { p, args, /* callInfo */ _ ->
    applyCrush(p, args)
}

// -- coarse() ---------------------------------------------------------------------------------------------------------

private val coarseMutation = voiceModifier {
    copy(coarse = it?.asDoubleOrNull())
}

private fun applyCoarse(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = coarseMutation,
        getValue = { coarse },
        setValue = { v, _ -> copy(coarse = v) },
    )
}

@StrudelDsl
val StrudelPattern.coarse by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyCoarse(p, args)
}

@StrudelDsl
val coarse by dslFunction { args, /* callInfo */ _ -> args.toPattern(coarseMutation) }

@StrudelDsl
val String.coarse by dslStringExtension { p, args, /* callInfo */ _ ->
    applyCoarse(p, args)
}

// -- room() -----------------------------------------------------------------------------------------------------------

private val roomMutation = voiceModifier {
    copy(room = it?.asDoubleOrNull())
}

private fun applyRoom(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = roomMutation,
        getValue = { room },
        setValue = { v, _ -> copy(room = v) },
    )
}

@StrudelDsl
val StrudelPattern.room by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyRoom(p, args)
}

@StrudelDsl
val room by dslFunction { args, /* callInfo */ _ -> args.toPattern(roomMutation) }

@StrudelDsl
val String.room by dslStringExtension { p, args, /* callInfo */ _ ->
    applyRoom(p, args)
}

// -- roomsize() / rsize() ---------------------------------------------------------------------------------------------

private val roomSizeMutation = voiceModifier {
    copy(roomSize = it?.asDoubleOrNull())
}

private fun applyRoomSize(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = roomSizeMutation,
        getValue = { roomSize },
        setValue = { v, _ -> copy(roomSize = v) },
    )
}

@StrudelDsl
val StrudelPattern.roomsize by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyRoomSize(p, args)
}

@StrudelDsl
val roomsize by dslFunction { args, /* callInfo */ _ -> args.toPattern(roomSizeMutation) }

@StrudelDsl
val String.roomsize by dslStringExtension { p, args, /* callInfo */ _ ->
    applyRoomSize(p, args)
}

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

// -- delay() ----------------------------------------------------------------------------------------------------------

private val delayMutation = voiceModifier {
    copy(delay = it?.asDoubleOrNull())
}

private fun applyDelay(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = delayMutation,
        getValue = { delay },
        setValue = { v, _ -> copy(delay = v) },
    )
}

@StrudelDsl
val StrudelPattern.delay by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyDelay(p, args)
}

@StrudelDsl
val delay by dslFunction { args, /* callInfo */ _ -> args.toPattern(delayMutation) }

@StrudelDsl
val String.delay by dslStringExtension { p, args, /* callInfo */ _ ->
    applyDelay(p, args)
}

// -- delaytime() ------------------------------------------------------------------------------------------------------

private val delayTimeMutation = voiceModifier {
    copy(delayTime = it?.asDoubleOrNull())
}

private fun applyDelayTime(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = delayTimeMutation,
        getValue = { delayTime },
        setValue = { v, _ -> copy(delayTime = v) },
    )
}

@StrudelDsl
val StrudelPattern.delaytime by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyDelayTime(p, args)
}

@StrudelDsl
val delaytime by dslFunction { args, /* callInfo */ _ -> args.toPattern(delayTimeMutation) }

@StrudelDsl
val String.delaytime by dslStringExtension { p, args, /* callInfo */ _ ->
    applyDelayTime(p, args)
}

// -- delayfeedback() --------------------------------------------------------------------------------------------------

private val delayFeedbackMutation = voiceModifier {
    copy(delayFeedback = it?.asDoubleOrNull())
}

private fun applyDelayFeedback(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = delayFeedbackMutation,
        getValue = { delayFeedback },
        setValue = { v, _ -> copy(delayFeedback = v) },
    )
}

@StrudelDsl
val StrudelPattern.delayfeedback by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyDelayFeedback(p, args)
}

@StrudelDsl
val delayfeedback by dslFunction { args, /* callInfo */ _ -> args.toPattern(delayFeedbackMutation) }

@StrudelDsl
val String.delayfeedback by dslStringExtension { p, args, /* callInfo */ _ ->
    applyDelayFeedback(p, args)
}

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

private val phaserMutation = voiceModifier {
    copy(phaser = it?.asDoubleOrNull())
}

private fun applyPhaser(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = phaserMutation,
        getValue = { phaser },
        setValue = { v, _ -> copy(phaser = v) },
    )
}

@StrudelDsl
val StrudelPattern.phaser by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyPhaser(p, args)
}

@StrudelDsl
val phaser by dslFunction { args, /* callInfo */ _ -> args.toPattern(phaserMutation) }

@StrudelDsl
val String.phaser by dslStringExtension { p, args, /* callInfo */ _ ->
    applyPhaser(p, args)
}

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

private val phaserDepthMutation = voiceModifier {
    copy(phaserDepth = it?.asDoubleOrNull())
}

private fun applyPhaserDepth(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = phaserDepthMutation,
        getValue = { phaserDepth },
        setValue = { v, _ -> copy(phaserDepth = v) },
    )
}

@StrudelDsl
val StrudelPattern.phaserdepth by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyPhaserDepth(p, args)
}

@StrudelDsl
val phaserdepth by dslFunction { args, /* callInfo */ _ -> args.toPattern(phaserDepthMutation) }

@StrudelDsl
val String.phaserdepth by dslStringExtension { p, args, /* callInfo */ _ ->
    applyPhaserDepth(p, args)
}

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

private val phaserCenterMutation = voiceModifier {
    copy(phaserCenter = it?.asDoubleOrNull())
}

private fun applyPhaserCenter(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = phaserCenterMutation,
        getValue = { phaserCenter },
        setValue = { v, _ -> copy(phaserCenter = v) },
    )
}

@StrudelDsl
val StrudelPattern.phasercenter by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyPhaserCenter(p, args)
}

@StrudelDsl
val phasercenter by dslFunction { args, /* callInfo */ _ -> args.toPattern(phaserCenterMutation) }

@StrudelDsl
val String.phasercenter by dslStringExtension { p, args, /* callInfo */ _ ->
    applyPhaserCenter(p, args)
}

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

private val phaserSweepMutation = voiceModifier {
    copy(phaserSweep = it?.asDoubleOrNull())
}

private fun applyPhaserSweep(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = phaserSweepMutation,
        getValue = { phaserSweep },
        setValue = { v, _ -> copy(phaserSweep = v) },
    )
}

@StrudelDsl
val StrudelPattern.phasersweep by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyPhaserSweep(p, args)
}

@StrudelDsl
val phasersweep by dslFunction { args, /* callInfo */ _ -> args.toPattern(phaserSweepMutation) }

@StrudelDsl
val String.phasersweep by dslStringExtension { p, args, /* callInfo */ _ ->
    applyPhaserSweep(p, args)
}

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

private val tremoloSyncMutation = voiceModifier {
    copy(tremoloSync = it?.asDoubleOrNull())
}

private fun applyTremoloSync(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = tremoloSyncMutation,
        getValue = { tremoloSync },
        setValue = { v, _ -> copy(tremoloSync = v) },
    )
}

@StrudelDsl
val StrudelPattern.tremolosync by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTremoloSync(p, args)
}

@StrudelDsl
val tremolosync by dslFunction { args, /* callInfo */ _ -> args.toPattern(tremoloSyncMutation) }

@StrudelDsl
val String.tremolosync by dslStringExtension { p, args, /* callInfo */ _ ->
    applyTremoloSync(p, args)
}

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

private val tremoloDepthMutation = voiceModifier {
    copy(tremoloDepth = it?.asDoubleOrNull())
}

private fun applyTremoloDepth(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = tremoloDepthMutation,
        getValue = { tremoloDepth },
        setValue = { v, _ -> copy(tremoloDepth = v) },
    )
}

@StrudelDsl
val StrudelPattern.tremolodepth by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTremoloDepth(p, args)
}

@StrudelDsl
val tremolodepth by dslFunction { args, /* callInfo */ _ -> args.toPattern(tremoloDepthMutation) }

@StrudelDsl
val String.tremolodepth by dslStringExtension { p, args, /* callInfo */ _ ->
    applyTremoloDepth(p, args)
}

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

private val tremoloSkewMutation = voiceModifier {
    copy(tremoloSkew = it?.asDoubleOrNull())
}

private fun applyTremoloSkew(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = tremoloSkewMutation,
        getValue = { tremoloSkew },
        setValue = { v, _ -> copy(tremoloSkew = v) },
    )
}

@StrudelDsl
val StrudelPattern.tremoloskew by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTremoloSkew(p, args)
}

@StrudelDsl
val tremoloskew by dslFunction { args, /* callInfo */ _ -> args.toPattern(tremoloSkewMutation) }

@StrudelDsl
val String.tremoloskew by dslStringExtension { p, args, /* callInfo */ _ ->
    applyTremoloSkew(p, args)
}

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

private val tremoloPhaseMutation = voiceModifier {
    copy(tremoloPhase = it?.asDoubleOrNull())
}

private fun applyTremoloPhase(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = tremoloPhaseMutation,
        getValue = { tremoloPhase },
        setValue = { v, _ -> copy(tremoloPhase = v) },
    )
}

@StrudelDsl
val StrudelPattern.tremolophase by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTremoloPhase(p, args)
}

@StrudelDsl
val tremolophase by dslFunction { args, /* callInfo */ _ -> args.toPattern(tremoloPhaseMutation) }

@StrudelDsl
val String.tremolophase by dslStringExtension { p, args, /* callInfo */ _ ->
    applyTremoloPhase(p, args)
}

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

private val tremoloShapeMutation = voiceModifier { shape ->
    val newShape = shape?.toString()?.lowercase()
    copy(tremoloShape = newShape)
}

private fun applyTremoloShape(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyControlFromParams(args, tremoloShapeMutation) { src, ctrl ->
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
val String.tremoloshape by dslStringExtension { p, args, /* callInfo */ _ ->
    applyTremoloShape(p, args)
}

/** Alias for [tremoloshape] */
@StrudelDsl
val StrudelPattern.tremshape by dslPatternExtension { p, args, callInfo -> p.tremoloshape(args, callInfo) }

/** Alias for [tremoloshape] */
@StrudelDsl
val tremshape by dslFunction { args, callInfo -> tremoloshape(args, callInfo) }

/** Alias for [tremoloshape] on a string */
@StrudelDsl
val String.tremshape by dslStringExtension { p, args, callInfo -> p.tremoloshape(args, callInfo) }
