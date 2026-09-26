/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.docs

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangParam
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.script.types.KlangType

/**
 * [KlangCallable.paramForArgument]: the parameter an argument binds to, which is what the editor
 * reads a param's `@param-tool` tools from.
 */
class ParamForArgumentTest : StringSpec({

    /** A two-argument call without a function literal. */
    val noFn = listOf(false, false)

    val any = KlangType(simpleName = "Any")

    val body = KlangCallable(
        name = "body",
        params = listOf(
            KlangParam(name = "wet", type = any),
            KlangParam(name = "material", type = any, uitools = listOf("BodyEditor")),
            KlangParam(name = "floor", type = any),
        ),
    )

    val stack = KlangCallable(
        name = "stack",
        params = listOf(
            KlangParam(name = "first", type = any),
            KlangParam(name = "rest", type = any, isVararg = true),
        ),
    )

    "a positional argument binds by its position" {
        body.paramForArgument(argIndex = 0, argName = null, functionArgs = noFn)?.name shouldBe "wet"
        body.paramForArgument(argIndex = 1, argName = null, functionArgs = noFn)?.name shouldBe "material"
    }

    "a named argument binds by its name, whatever its position" {
        body.paramForArgument(argIndex = 0, argName = "material", functionArgs = noFn)?.name shouldBe "material"
        body.paramForArgument(argIndex = 1, argName = "wet", functionArgs = noFn)?.name shouldBe "wet"
        body.paramForArgument(argIndex = 0, argName = "floor", functionArgs = noFn)?.name shouldBe "floor"
    }

    "a name no parameter has binds to nothing, not to its position" {
        body.paramForArgument(argIndex = 0, argName = "stuff", functionArgs = noFn) shouldBe null
    }

    "a negative position binds to nothing, not to the vararg" {
        stack.paramForArgument(argIndex = -1, argName = null, functionArgs = noFn) shouldBe null
    }

    "a trailing lambda floats to the one function-typed parameter after it, as in the interpreter" {
        val fnType = KlangType(simpleName = "Function1", functionParams = listOf(any))
        val withConfigure = KlangCallable(
            name = "f",
            params = listOf(KlangParam(name = "level", type = any), KlangParam(name = "configure", type = fnType)),
        )

        // f(x => ...)
        withConfigure.paramForArgument(argIndex = 0, argName = null, functionArgs = listOf(true))?.name shouldBe "configure"
        // f(0.5)
        withConfigure.paramForArgument(argIndex = 0, argName = null, functionArgs = listOf(false))?.name shouldBe "level"
    }

    "a vararg parameter list maps positionally: a lambda at 0 binds the first param, no float" {
        val fnType = KlangType(simpleName = "Function1", functionParams = listOf(any))
        val withVararg = KlangCallable(
            name = "g",
            params = listOf(
                KlangParam(name = "a", type = any),
                KlangParam(name = "cb", type = fnType),
                KlangParam(name = "rest", type = any, isVararg = true),
            ),
        )

        withVararg.paramForArgument(argIndex = 0, argName = null, functionArgs = listOf(true))?.name shouldBe "a"
    }

    "positions past the parameters bind to a trailing vararg, and to nothing without one" {
        stack.paramForArgument(argIndex = 5, argName = null, functionArgs = noFn)?.name shouldBe "rest"
        body.paramForArgument(argIndex = 3, argName = null, functionArgs = noFn) shouldBe null
    }

    // An untyped receiver: the variant whose parameter for the argument has tools wins.
    val katalystBody = KlangCallable(
        name = "body",
        receiver = KlangType(simpleName = "KatalystBuilder"),
        params = listOf(KlangParam(name = "wet", type = any), KlangParam(name = "material", type = any)),
    )

    val bodySymbol = KlangSymbol(name = "body", variants = listOf(katalystBody, body), category = "effects")

    "untyped receiver: the tooled variant wins over an earlier tool-less one" {
        bodySymbol.callableForArgument(argIndex = 0, argName = "material", functionArgs = noFn) shouldBe body
        bodySymbol.callableForArgument(argIndex = 1, argName = null, functionArgs = noFn) shouldBe body
    }

    "untyped receiver: with no tooled variant, the first callable" {
        bodySymbol.callableForArgument(argIndex = 0, argName = "wet", functionArgs = noFn) shouldBe katalystBody
    }
})
