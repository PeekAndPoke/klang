@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.pattern.EmptyPattern

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangArithmeticInit = false

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Arithmetic
// ///

// NOTE: applyBinaryOp and applyUnaryOp are already in lang_helpers.kt, so we don't include them here

// -- add() ------------------------------------------------------------------------------------------------------------

/**
 * Adds the given amount to the pattern's value.
 * Example: n("0 2").add("5") -> n("5 7")
 */
@StrudelDsl
val StrudelPattern.add by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a + b }
}

/** Adds the given amount to the pattern's value on a string. */
@StrudelDsl
val String.add by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a + b }
}

/**
 * Top-level add function.
 * Usage: add(amount, pattern) -> adds amount to pattern
 * Usage: add(value) -> creates a pattern with that value
 */
@StrudelDsl
val add by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        val amountArgs = args.dropLast(1)
        applyBinaryOp(source, amountArgs) { a, b -> a + b }
    } else {
        args.toPattern(defaultModifier)
    }
}

// -- sub() ------------------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.sub by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a - b }
}

@StrudelDsl
val String.sub by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a - b }
}

@StrudelDsl
val sub by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a - b }
    } else {
        EmptyPattern // sub() as a source doesn't make much sense without arguments
    }
}

// -- mul() ------------------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.mul by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a * b }
}

@StrudelDsl
val String.mul by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a * b }
}

@StrudelDsl
val mul by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a * b }
    } else {
        EmptyPattern
    }
}

// -- div() ------------------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.div by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a / b }
}

@StrudelDsl
val String.div by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a / b }
}

@StrudelDsl
val div by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a / b }
    } else {
        EmptyPattern
    }
}

// -- mod() ------------------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.mod by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a % b }
}

@StrudelDsl
val String.mod by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a % b }
}

@StrudelDsl
val mod by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a % b }
    } else {
        EmptyPattern
    }
}

// -- pow() ------------------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.pow by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a pow b }
}

@StrudelDsl
val String.pow by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a pow b }
}

@StrudelDsl
val pow by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a pow b }
    } else {
        EmptyPattern
    }
}

// -- band() (Bitwise AND) ---------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.band by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a band b }
}

@StrudelDsl
val String.band by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a band b }
}

@StrudelDsl
val band by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a band b }
    } else {
        EmptyPattern
    }
}

// -- bor() (Bitwise OR) -----------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.bor by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a bor b }
}

@StrudelDsl
val String.bor by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a bor b }
}

@StrudelDsl
val bor by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a bor b }
    } else {
        EmptyPattern
    }
}

// -- bxor() (Bitwise XOR) ---------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.bxor by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a bxor b }
}

@StrudelDsl
val String.bxor by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a bxor b }
}

@StrudelDsl
val bxor by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a bxor b }
    } else {
        EmptyPattern
    }
}

// -- blshift() (Bitwise Left Shift) -----------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.blshift by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a shl b }
}

@StrudelDsl
val String.blshift by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a shl b }
}

@StrudelDsl
val blshift by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a shl b }
    } else {
        EmptyPattern
    }
}

// -- brshift() (Bitwise Right Shift) ----------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.brshift by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a shr b }
}

@StrudelDsl
val String.brshift by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a shr b }
}

@StrudelDsl
val brshift by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a shr b }
    } else {
        EmptyPattern
    }
}

// -- log2() -----------------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.log2 by dslPatternExtension { source, _ ->
    applyUnaryOp(source) { it.log2() }
}

@StrudelDsl
val String.log2 by dslStringExtension { source, _ ->
    applyUnaryOp(source) { it.log2() }
}

@StrudelDsl
val log2 by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (source != null) {
        applyUnaryOp(source) { it.log2() }
    } else {
        EmptyPattern
    }
}

// -- lt() (Less Than) -------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.lt by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a lt b }
}

@StrudelDsl
val String.lt by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a lt b }
}

@StrudelDsl
val lt by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a lt b }
    } else {
        EmptyPattern
    }
}

// -- gt() (Greater Than) ----------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.gt by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a gt b }
}

@StrudelDsl
val String.gt by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a gt b }
}

@StrudelDsl
val gt by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a gt b }
    } else {
        EmptyPattern
    }
}

// -- lte() (Less Than or Equal) ---------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.lte by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a lte b }
}

@StrudelDsl
val String.lte by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a lte b }
}

@StrudelDsl
val lte by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a lte b }
    } else {
        EmptyPattern
    }
}

// -- gte() (Greater Than or Equal) ------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.gte by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a gte b }
}

@StrudelDsl
val String.gte by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a gte b }
}

@StrudelDsl
val gte by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a gte b }
    } else {
        EmptyPattern
    }
}

// -- eq() (Equal) -----------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.eq by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a eq b }
}

@StrudelDsl
val String.eq by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a eq b }
}

@StrudelDsl
val eq by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a eq b }
    } else {
        EmptyPattern
    }
}

// -- eqt() (Truthiness Equal) -----------------------------------------------------------------------------------------

/** Truthiness equality comparison */
@StrudelDsl
val StrudelPattern.eqt by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a eqt b }
}

/** Truthiness equality comparison on a string */
@StrudelDsl
val String.eqt by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a eqt b }
}

/** Truthiness equality comparison function */
@StrudelDsl
val eqt by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a eqt b }
    } else {
        EmptyPattern
    }
}

// -- ne() (Not Equal) -------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.ne by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a ne b }
}

@StrudelDsl
val String.ne by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a ne b }
}

@StrudelDsl
val ne by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a ne b }
    } else {
        EmptyPattern
    }
}

// -- net() (Truthiness Not Equal) -------------------------------------------------------------------------------------

/** Truthiness inequality comparison */
@StrudelDsl
val StrudelPattern.net by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a net b }
}

/** Truthiness inequality comparison on a string */
@StrudelDsl
val String.net by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a net b }
}

/** Truthiness inequality comparison function */
@StrudelDsl
val net by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a net b }
    } else {
        EmptyPattern
    }
}

// -- and() (Logical AND) ----------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.and by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a and b }
}

@StrudelDsl
val String.and by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a and b }
}

@StrudelDsl
val and by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a and b }
    } else {
        EmptyPattern
    }
}

// -- or() (Logical OR) ------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.or by dslPatternExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a or b }
}

@StrudelDsl
val String.or by dslStringExtension { source, args ->
    applyBinaryOp(source, args) { a, b -> a or b }
}

@StrudelDsl
val or by dslFunction { args ->
    val source = args.lastOrNull() as? StrudelPattern
    if (args.size >= 2 && source != null) {
        applyBinaryOp(source, args.dropLast(1)) { a, b -> a or b }
    } else {
        EmptyPattern
    }
}
