package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries
import kotlin.math.pow

/**
 * Math object for KlangScript — provides standard math operations.
 *
 * Exposed as `Math` in KlangScript, similar to JavaScript's Math object.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.Object("Math")
internal object KlangScriptMath {
    override fun toString(): String = "[Math object]"

    /**
     * Returns the square root of a number.
     *
     * ```KlangScript(Executable)
     * Math.sqrt(16)  // 4.0
     * ```
     *
     * @param x The number to compute the square root of
     * @return The square root
     * @category math
     * @tags arithmetic, calculation
     */
    @KlangScript.Method
    fun sqrt(x: Double): Double = kotlin.math.sqrt(x)

    /**
     * Returns the absolute value of a number.
     *
     * ```KlangScript(Executable)
     * Math.abs(-5)  // 5.0
     * ```
     *
     * @param x The number
     * @return The absolute value
     * @category math
     * @tags arithmetic
     */
    @KlangScript.Method
    fun abs(x: Double): Double = kotlin.math.abs(x)

    /**
     * Rounds a number down to the nearest integer.
     *
     * ```KlangScript(Executable)
     * Math.floor(3.7)  // 3.0
     * ```
     *
     * @param x The number to floor
     * @return The floored value
     * @category math
     * @tags rounding
     */
    @KlangScript.Method
    fun floor(x: Double): Double = kotlin.math.floor(x)

    /**
     * Rounds a number up to the nearest integer.
     *
     * ```KlangScript(Executable)
     * Math.ceil(3.2)  // 4.0
     * ```
     *
     * @param x The number to ceil
     * @return The ceiling value
     * @category math
     * @tags rounding
     */
    @KlangScript.Method
    fun ceil(x: Double): Double = kotlin.math.ceil(x)

    /**
     * Rounds a number to the nearest integer.
     *
     * ```KlangScript(Executable)
     * Math.round(3.5)  // 4.0
     * ```
     *
     * @param x The number to round
     * @return The rounded value
     * @category math
     * @tags rounding
     */
    @KlangScript.Method
    fun round(x: Double): Double = kotlin.math.round(x)

    /**
     * Returns the sine of an angle in radians.
     *
     * ```KlangScript(Executable)
     * Math.sin(0)  // 0.0
     * ```
     *
     * @param x The angle in radians
     * @return The sine value
     * @category math
     * @tags trigonometry
     */
    @KlangScript.Method
    fun sin(x: Double): Double = kotlin.math.sin(x)

    /**
     * Returns the cosine of an angle in radians.
     *
     * ```KlangScript(Executable)
     * Math.cos(0)  // 1.0
     * ```
     *
     * @param x The angle in radians
     * @return The cosine value
     * @category math
     * @tags trigonometry
     */
    @KlangScript.Method
    fun cos(x: Double): Double = kotlin.math.cos(x)

    /**
     * Returns the tangent of an angle in radians.
     *
     * ```KlangScript(Executable)
     * Math.tan(0)  // 0.0
     * ```
     *
     * @param x The angle in radians
     * @return The tangent value
     * @category math
     * @tags trigonometry
     */
    @KlangScript.Method
    fun tan(x: Double): Double = kotlin.math.tan(x)

    /**
     * Returns the smaller of two numbers.
     *
     * ```KlangScript(Executable)
     * Math.min(3, 7)  // 3.0
     * ```
     *
     * @param a First number
     * @param b Second number
     * @return The smaller value
     * @category math
     * @tags comparison
     */
    @KlangScript.Method
    fun min(a: Double, b: Double): Double = kotlin.math.min(a, b)

    /**
     * Returns the larger of two numbers.
     *
     * ```KlangScript(Executable)
     * Math.max(3, 7)  // 7.0
     * ```
     *
     * @param a First number
     * @param b Second number
     * @return The larger value
     * @category math
     * @tags comparison
     */
    @KlangScript.Method
    fun max(a: Double, b: Double): Double = kotlin.math.max(a, b)

    /**
     * Returns base raised to the power of exp.
     *
     * ```KlangScript(Executable)
     * Math.pow(2, 10)  // 1024.0
     * ```
     *
     * @param base The base number
     * @param exp The exponent
     * @return base raised to exp
     * @category math
     * @tags arithmetic, exponent
     */
    @KlangScript.Method
    fun pow(base: Double, exp: Double): Double = base.pow(exp)
}
