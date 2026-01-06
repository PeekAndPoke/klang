package io.peekandpoke.klang.script

import kotlin.reflect.KClass

fun KClass<*>.getUniqueClassName() = (simpleName ?: "Unknown") + "_${hashCode()}"
