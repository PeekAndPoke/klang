@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelVoiceValue
import io.peekandpoke.klang.strudel.StrudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.liftValue
import io.peekandpoke.klang.strudel.pattern.ControlPattern
import io.peekandpoke.klang.strudel.pattern.ReinterpretPattern.Companion.reinterpretVoice

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangArithmeticInit = false

/**
 * Helper for applying binary operations to patterns.
 */
internal fun applyBinaryOp(
    source: StrudelPattern,
    args: List<StrudelDslArg<Any?>>,
    op: (StrudelVoiceValue, StrudelVoiceValue) -> StrudelVoiceValue?,
): StrudelPattern {
    // We use defaultModifier for args because we just want the 'value'
    val controlPattern = args.toPattern(voiceValueModifier)

    return ControlPattern(
        source = source,
        control = controlPattern,
        mapper = { it }, // No mapping needed
        combiner = { srcData, ctrlData ->
            val amount = ctrlData.value
            val srcValue = srcData.value

            if (amount == null || srcValue == null) {
                srcData
            } else {
                val newValue = op(srcValue, amount)
                srcData.copy(value = newValue)
            }
        }
    )
}

// Helper for arithmetic operations that modify the 'value' field
fun applyArithmetic(
    source: StrudelPattern,
    args: List<StrudelDslArg<Any?>>,
    op: (StrudelVoiceValue, StrudelVoiceValue) -> StrudelVoiceValue?,
): StrudelPattern {
    if (args.isEmpty()) return source

    // 1. Convert args to a control pattern
    // Note: We use defaultModifier because we just want the raw values (numbers/strings)
    val control = args.toPattern(voiceValueModifier)

    // 2. Lift: Intersection of source + control
    // We use liftValue because arithmetic works on StrudelVoiceValue objects
    return source.liftValue(control) { controlVal, pattern ->
        // 3. Map events: Apply the operation to the source value
        pattern.reinterpretVoice { voiceData ->
            val sourceVal = voiceData.value ?: return@reinterpretVoice voiceData
            // Execute the operation (e.g. source + control)
            val newVal = op(sourceVal, controlVal)
            // apply new val
            voiceData.copy(value = newVal)
        }
    }
}

/**
 * Helper for applying unary operations to patterns.
 */
fun applyUnaryOp(
    source: StrudelPattern,
    op: (StrudelVoiceValue) -> StrudelVoiceValue?,
): StrudelPattern {
    // Unary ops (like log2) apply directly to the source values without a control pattern
    return source.reinterpretVoice { srcData ->
        val srcValue = srcData.value

        if (srcValue == null) {
            srcData
        } else {
            val newValue = op(srcValue)
            srcData.copy(value = newValue)
        }
    }
}

// -- add() ------------------------------------------------------------------------------------------------------------

/**
 * Adds the given amount to the pattern's value.
 * Example: n("0 2").add("5") -> n("5 7")
 */
@StrudelDsl
val StrudelPattern.add by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyArithmetic(p, args) { a, b -> a + b }
}

/** Adds the given amount to the pattern's value on a string. */
@StrudelDsl
val String.add by dslStringExtension { p, args, callInfo -> p.add(args, callInfo) }

/**
 * Top-level add function. Produces silence
 */
@StrudelDsl
val add by dslFunction { _, _ -> silence }

// -- sub() ------------------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.sub by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyArithmetic(p, args) { a, b -> a - b }
}

@StrudelDsl
val String.sub by dslStringExtension { p, args, callInfo -> p.sub(args, callInfo) }

@StrudelDsl
val sub by dslFunction { _, _ -> silence }

// -- mul() ------------------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.mul by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyArithmetic(p, args) { a, b -> a * b }
}

@StrudelDsl
val String.mul by dslStringExtension { p, args, callInfo -> p.mul(args, callInfo) }

@StrudelDsl
val mul by dslFunction { _, _ -> silence }

// -- div() ------------------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.div by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyArithmetic(p, args) { a, b -> a / b }
}

@StrudelDsl
val String.div by dslStringExtension { p, args, callInfo -> p.div(args, callInfo) }

@StrudelDsl
val div by dslFunction { _, _ -> silence }

// -- mod() ------------------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.mod by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyArithmetic(p, args) { a, b -> a % b }
}

@StrudelDsl
val String.mod by dslStringExtension { p, args, callInfo -> p.mod(args, callInfo) }

@StrudelDsl
val mod by dslFunction { _, _ -> silence }

// -- pow() ------------------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.pow by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyArithmetic(p, args) { a, b -> a pow b }
}

@StrudelDsl
val String.pow by dslStringExtension { p, args, callInfo -> p.pow(args, callInfo) }

@StrudelDsl
val pow by dslFunction { _, _ -> silence }

// -- band() (Bitwise AND) ---------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.band by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyArithmetic(p, args) { a, b -> a band b }
}

@StrudelDsl
val String.band by dslStringExtension { p, args, callInfo -> p.band(args, callInfo) }

@StrudelDsl
val band by dslFunction { _, _ -> silence }

// -- bor() (Bitwise OR) -----------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.bor by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyArithmetic(p, args) { a, b -> a bor b }
}

@StrudelDsl
val String.bor by dslStringExtension { p, args, callInfo -> p.bor(args, callInfo) }

@StrudelDsl
val bor by dslFunction { _, _ -> silence }

// -- bxor() (Bitwise XOR) ---------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.bxor by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyArithmetic(p, args) { a, b -> a bxor b }
}

@StrudelDsl
val String.bxor by dslStringExtension { p, args, callInfo -> p.bxor(args, callInfo) }

@StrudelDsl
val bxor by dslFunction { _, _ -> silence }

// -- blshift() (Bitwise Left Shift) -----------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.blshift by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyArithmetic(p, args) { a, b -> a shl b }
}

@StrudelDsl
val String.blshift by dslStringExtension { p, args, callInfo -> p.blshift(args, callInfo) }

@StrudelDsl
val blshift by dslFunction { _, _ -> silence }

// -- brshift() (Bitwise Right Shift) ----------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.brshift by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyArithmetic(p, args) { a, b -> a shr b }
}

@StrudelDsl
val String.brshift by dslStringExtension { p, args, callInfo -> p.brshift(args, callInfo) }

@StrudelDsl
val brshift by dslFunction { _, _ -> silence }

// -- log2() -----------------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.log2 by dslPatternExtension { p, /* args */ _, /* callInfo */ _ ->
    applyUnaryOp(p) { it.log2() }
}

@StrudelDsl
val String.log2 by dslStringExtension { p, args, callInfo -> p.log2(args, callInfo) }

@StrudelDsl
val log2 by dslFunction { _, _ -> silence }

// -- lt() (Less Than) -------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.lt by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyArithmetic(p, args) { a, b -> a lt b }
}

@StrudelDsl
val String.lt by dslStringExtension { p, args, callInfo -> p.lt(args, callInfo) }

@StrudelDsl
val lt by dslFunction { _, _ -> silence }

// -- gt() (Greater Than) ----------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.gt by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyArithmetic(p, args) { a, b -> a gt b }
}

@StrudelDsl
val String.gt by dslStringExtension { p, args, callInfo -> p.gt(args, callInfo) }

@StrudelDsl
val gt by dslFunction { _, _ -> silence }

// -- lte() (Less Than or Equal) ---------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.lte by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyArithmetic(p, args) { a, b -> a lte b }
}

@StrudelDsl
val String.lte by dslStringExtension { p, args, callInfo -> p.lte(args, callInfo) }

@StrudelDsl
val lte by dslFunction { _, _ -> silence }

// -- gte() (Greater Than or Equal) ------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.gte by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyArithmetic(p, args) { a, b -> a gte b }
}

@StrudelDsl
val String.gte by dslStringExtension { p, args, callInfo -> p.gte(args, callInfo) }

@StrudelDsl
val gte by dslFunction { _, _ -> silence }

// -- eq() (Equal) -----------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.eq by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyArithmetic(p, args) { a, b -> a eq b }
}

@StrudelDsl
val String.eq by dslStringExtension { p, args, callInfo -> p.eq(args, callInfo) }

@StrudelDsl
val eq by dslFunction { _, _ -> silence }

// -- eqt() (Truthiness Equal) -----------------------------------------------------------------------------------------

/** Truthiness equality comparison */
@StrudelDsl
val StrudelPattern.eqt by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyArithmetic(p, args) { a, b -> a eqt b }
}

/** Truthiness equality comparison on a string */
@StrudelDsl
val String.eqt by dslStringExtension { p, args, callInfo -> p.eqt(args, callInfo) }

/** Truthiness equality comparison function */
@StrudelDsl
val eqt by dslFunction { _, _ -> silence }

// -- ne() (Not Equal) -------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.ne by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyArithmetic(p, args) { a, b -> a ne b }
}

@StrudelDsl
val String.ne by dslStringExtension { p, args, /* callInfo */ _ -> p.ne(args) }

@StrudelDsl
val ne by dslFunction { _, _ -> silence }

// -- net() (Truthiness Not Equal) -------------------------------------------------------------------------------------

/** Truthiness inequality comparison */
@StrudelDsl
val StrudelPattern.net by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyArithmetic(p, args) { a, b -> a net b }
}

/** Truthiness inequality comparison on a string */
@StrudelDsl
val String.net by dslStringExtension { p, args, callInfo -> p.net(args, callInfo) }

/** Truthiness inequality comparison function */
@StrudelDsl
val net by dslFunction { _, _ -> silence }

// -- and() (Logical AND) ----------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.and by dslPatternExtension { source, args, /* callInfo */ _ ->
    applyArithmetic(source, args) { a, b -> a and b }
}

@StrudelDsl
val String.and by dslStringExtension { p, args, callInfo -> p.and(args, callInfo) }

@StrudelDsl
val and by dslFunction { _, _ -> silence }

// -- or() (Logical OR) ------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.or by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyArithmetic(p, args) { a, b -> a or b }
}

@StrudelDsl
val String.or by dslStringExtension { p, args, callInfo -> p.or(args, callInfo) }

@StrudelDsl
val or by dslFunction { _, _ -> silence }

// -- round() ----------------------------------------------------------------------------------------------------------

/**
 * Rounds all numerical values to the nearest integer.
 */
@StrudelDsl
val StrudelPattern.round by dslPatternExtension { p, /* args */ _, /* callInfo */ _ ->
    applyUnaryOp(p) { v ->
        val d = v.asDouble
        if (d != null) kotlin.math.round(d).asVoiceValue() else v
    }
}

@StrudelDsl
val String.round by dslStringExtension { p, /* args */ _, /* callInfo */ _ -> p.round() }

// -- floor() ----------------------------------------------------------------------------------------------------------

/**
 * Floors all numerical values to the nearest lower integer.
 * E.g. 3.7 becomes 3, and -4.2 becomes -5.
 */
@StrudelDsl
val StrudelPattern.floor by dslPatternExtension { p, /* args */ _, /* callInfo */ _ ->
    applyUnaryOp(p) { v ->
        val d = v.asDouble
        if (d != null) kotlin.math.floor(d).asVoiceValue() else v
    }
}

@StrudelDsl
val String.floor by dslStringExtension { p, /* args */ _, /* callInfo */ _ -> p.floor() }

// -- ceil() -----------------------------------------------------------------------------------------------------------

/**
 * Ceils all numerical values to the nearest higher integer.
 * E.g. 3.2 becomes 4, and -4.2 becomes -4.
 */
@StrudelDsl
val StrudelPattern.ceil by dslPatternExtension { p, /* args */ _, /* callInfo */ _ ->
    applyUnaryOp(p) { v ->
        val d = v.asDouble
        if (d != null) kotlin.math.ceil(d).asVoiceValue() else v
    }
}

@StrudelDsl
val String.ceil by dslStringExtension { p, /* args */ _, /* callInfo */ _ -> p.ceil() }

