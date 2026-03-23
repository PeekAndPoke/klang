package io.peekandpoke.klang.common.math.stochastic

import kotlin.random.Random

sealed interface Distribution {
    fun sample(random: Random): Double
}
