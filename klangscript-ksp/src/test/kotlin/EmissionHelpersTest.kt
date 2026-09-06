/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.ksp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * The two emission helpers a nullable function-typed parameter (`configure: ((B) -> B)? = null`,
 * the configure-lambda door shape) depends on. Before these, the arity-dispatch bridge emitted
 * `Function1?::class` (not a type) and `as (((B) -> B)?)?` (doubled nullability).
 */
class EmissionHelpersTest : StringSpec({

    "class literal name strips the nullable suffix" {
        classLiteralTypeName("Function1?") shouldBe "Function1"
    }

    "class literal name leaves a non-null type alone" {
        classLiteralTypeName("Double") shouldBe "Double"
    }

    "cast suffix adds ? once for a plain nullable type" {
        castSuffix("Double", isNullable = true) shouldBe " as Double?"
    }

    "cast suffix does not double the ? of an already-nullable function type" {
        castSuffix("((Any) -> Any)?", isNullable = true) shouldBe " as ((Any) -> Any)?"
    }

    "cast suffix is empty for the redundant Any? cast" {
        castSuffix("Any", isNullable = true) shouldBe ""
    }

    "cast suffix for a non-null function type" {
        castSuffix("((Any) -> Any)", isNullable = false) shouldBe " as ((Any) -> Any)"
    }
})
