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
        InvokeShape.problems("invoke", isOperator = true, insideObjectClass = true, invokeCountInClass = 1).shouldBeEmpty()
    }

    "the script symbol is __invoke__, the Kotlin function stays Kotlin's invoke" {
        KlangScript.Invoke.NAME shouldBe "__invoke__"
        InvokeShape.KOTLIN_OPERATOR shouldBe "invoke"
        // The internal symbol is no Kotlin function name an @Invoke may sit on
        InvokeShape.problems(KlangScript.Invoke.NAME, isOperator = true, insideObjectClass = true, invokeCountInClass = 1) shouldHaveSize 1
    }

    "outside an @Object class (a @TypeExtensions class included: the call form is documented on the object)" {
        val problems = InvokeShape.problems("invoke", isOperator = true, insideObjectClass = false, invokeCountInClass = 1)
        problems shouldHaveSize 1
        problems.single() shouldContain "must be declared inside an @KlangScript.Object class"
    }

    "a function that is not named invoke" {
        val problems = InvokeShape.problems("call", isOperator = true, insideObjectClass = true, invokeCountInClass = 1)
        problems shouldHaveSize 1
        problems.single() shouldContain "'operator fun invoke'"
        problems.single() shouldContain "'call'"
    }

    "a function without the operator modifier" {
        val problems = InvokeShape.problems("invoke", isOperator = false, insideObjectClass = true, invokeCountInClass = 1)
        problems shouldHaveSize 1
        problems.single() shouldContain "operator fun"
    }

    "two call forms in one class" {
        val problems = InvokeShape.problems("invoke", isOperator = true, insideObjectClass = true, invokeCountInClass = 2)
        problems shouldHaveSize 1
        problems.single() shouldContain "no overloads"
    }

    "a @Method that would register as the call form, spelled __invoke__ or invoke, is refused, any other name passes" {
        InvokeShape.methodNameProblem("invoke", "invoke").shouldNotBeNull() shouldContain "@KlangScript.Invoke"
        InvokeShape.methodNameProblem("call", "invoke").shouldNotBeNull() shouldContain "'call'"
        InvokeShape.methodNameProblem("call", "__invoke__").shouldNotBeNull() shouldContain "'__invoke__'"
        InvokeShape.methodNameProblem("build", "build").shouldBeNull()
    }

    "a @Method spelled like any operator symbol (__x__) is refused" {
        InvokeShape.methodNameProblem("add", "__plus__").shouldNotBeNull() shouldContain "operator symbol"
        InvokeShape.methodNameProblem("under", "__").shouldBeNull()
        InvokeShape.methodNameProblem("under", "_private_").shouldBeNull()
        InvokeShape.isOperatorShaped("__plus__") shouldBe true
        InvokeShape.isOperatorShaped("____") shouldBe false
    }

    "every problem is reported, not only the first" {
        InvokeShape.problems("call", isOperator = false, insideObjectClass = false, invokeCountInClass = 3) shouldHaveSize 4
    }
})
