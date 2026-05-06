@file:KlangScript.Library(KlangScriptLibraries.STDLIB)

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

/**
 * The mathematical constant π (pi), the ratio of a circle's circumference to its diameter.
 *
 * Equivalent to `Math.PI`. Provided as a top-level constant for convenience.
 *
 * ```KlangScript(Executable)
 * PI  // 3.141592653589793
 * ```
 *
 * @category math
 * @tags constant, math, pi
 */
@KlangScript.Property
val PI: Double = kotlin.math.PI

/**
 * Euler's number e, the base of the natural logarithm.
 *
 * Equivalent to `Math.E`. Provided as a top-level constant for convenience.
 *
 * ```KlangScript(Executable)
 * E  // 2.718281828459045
 * ```
 *
 * @category math
 * @tags constant, math, euler
 */
@KlangScript.Property
val E: Double = kotlin.math.E
