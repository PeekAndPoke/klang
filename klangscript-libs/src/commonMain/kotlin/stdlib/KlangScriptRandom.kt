/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries
import kotlin.random.Random

/**
 * Random object for KlangScript: random numbers drawn when the code runs.
 *
 * Exposed as `Random` in KlangScript, with the functions of Kotlin's `Random`. KlangScript has no overloads, so the
 * forms of one Kotlin function are one method here whose arguments may be left out: `nextInt()`, `nextInt(until)`,
 * `nextInt(from, until)`. A value is drawn once, where the code runs it: `seed(Random.nextInt())` gives a pattern
 * one seed for as long as it lives, and another one the next time the code is run.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.Object("Random")
internal object KlangScriptRandom {
    override fun toString(): String = "[Random object]"

    /**
     * Returns a random whole number.
     *
     * Without arguments any whole number of 32 bits, also a negative one. With one argument a number from 0 up
     * to, but not including, that argument. With two arguments a number from the first up to, but not including,
     * the second. A named `until` alone (`nextInt(until = 6)`) is the form with one argument. As in Kotlin, a range
     * that holds no number is an error.
     *
     * ```KlangScript(Executable)
     * Random.nextInt(0, 100)  // a whole number from 0 to 99
     * ```
     *
     * ```KlangScript(Executable)
     * Random.nextInt(6)  // a whole number from 0 to 5
     * ```
     *
     * @param from With two arguments: the lowest number. Alone: the end of the range, which starts at 0.
     * @param until The end of the range; the number is always below it.
     * @return A random whole number
     * @category random
     * @tags random, integer
     */
    @KlangScript.Method
    fun nextInt(from: Int? = null, until: Int? = null): Int = when {
        from != null && until != null -> Random.nextInt(from, until)
        from != null -> Random.nextInt(from)
        until != null -> Random.nextInt(until)
        else -> Random.nextInt()
    }

    /**
     * Returns a random number with a fraction.
     *
     * Without arguments a number from 0 up to, but not including, 1. With one argument a number from 0 up to,
     * but not including, that argument. With two arguments a number from the first up to, but not including, the
     * second. A named `until` alone is the form with one argument. As in Kotlin, a range that holds no number is an
     * error.
     *
     * ```KlangScript(Executable)
     * Random.nextDouble()  // a number from 0 to just below 1
     * ```
     *
     * ```KlangScript(Executable)
     * Random.nextDouble(0.5, 2.0)  // a number from 0.5 to just below 2
     * ```
     *
     * @param from With two arguments: the lowest number. Alone: the end of the range, which starts at 0.
     * @param until The end of the range; the number is always below it.
     * @return A random number
     * @category random
     * @tags random, fraction
     */
    @KlangScript.Method
    fun nextDouble(from: Double? = null, until: Double? = null): Double = when {
        from != null && until != null -> Random.nextDouble(from, until)
        from != null -> Random.nextDouble(from)
        until != null -> Random.nextDouble(until)
        else -> Random.nextDouble()
    }

    /**
     * Returns `true` or `false`, each as likely as the other.
     *
     * ```KlangScript(Executable)
     * Random.nextBoolean()  // true or false
     * ```
     *
     * @return A random boolean
     * @category random
     * @tags random, boolean
     */
    @KlangScript.Method
    fun nextBoolean(): Boolean = Random.nextBoolean()
}
