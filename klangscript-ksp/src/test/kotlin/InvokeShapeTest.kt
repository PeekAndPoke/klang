/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.ksp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.script.annotations.KlangScript

/**
 * Coverage for [InvokeShape.problems], the placement rules of `@KlangScript.Invoke`.
 */
class InvokeShapeTest : StringSpec({

    "the right shape has no problems" {
        InvokeShape.problems("invoke", isOperator = true, insideRegisteredClass = true, invokeCountInClass = 1).shouldBeEmpty()
    }

    "the annotation name constant is the runtime method name" {
        KlangScript.Invoke.NAME shouldBe "invoke"
        InvokeShape.problems(KlangScript.Invoke.NAME, isOperator = true, insideRegisteredClass = true, invokeCountInClass = 1).shouldBeEmpty()
    }

    "outside an @Object or @TypeExtensions class" {
        val problems = InvokeShape.problems("invoke", isOperator = true, insideRegisteredClass = false, invokeCountInClass = 1)
        problems shouldHaveSize 1
        problems.single() shouldContain "must be declared inside"
    }

    "a function that is not named invoke" {
        val problems = InvokeShape.problems("call", isOperator = true, insideRegisteredClass = true, invokeCountInClass = 1)
        problems shouldHaveSize 1
        problems.single() shouldContain "'operator fun invoke'"
        problems.single() shouldContain "'call'"
    }

    "a function without the operator modifier" {
        val problems = InvokeShape.problems("invoke", isOperator = false, insideRegisteredClass = true, invokeCountInClass = 1)
        problems shouldHaveSize 1
        problems.single() shouldContain "operator fun"
    }

    "two call forms in one class" {
        val problems = InvokeShape.problems("invoke", isOperator = true, insideRegisteredClass = true, invokeCountInClass = 2)
        problems shouldHaveSize 1
        problems.single() shouldContain "no overloads"
    }

    "a @Method that would register as invoke is refused, any other name passes" {
        InvokeShape.methodSpelledInvoke("invoke", "invoke").shouldNotBeNull() shouldContain "@KlangScript.Invoke"
        InvokeShape.methodSpelledInvoke("call", "invoke").shouldNotBeNull() shouldContain "'call'"
        InvokeShape.methodSpelledInvoke("build", "build").shouldBeNull()
    }

    "every problem is reported, not only the first" {
        InvokeShape.problems("call", isOperator = false, insideRegisteredClass = false, invokeCountInClass = 3) shouldHaveSize 4
    }
})
