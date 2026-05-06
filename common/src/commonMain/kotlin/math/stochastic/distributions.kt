package io.peekandpoke.klang.common.math.stochastic

import kotlin.random.Random

fun Distribution.sample(random: Random, from: Double, to: Double): Double {
    val next = sample(random)

    return next * (to - from) + from
}
