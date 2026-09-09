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

    "arity check: a door without script parameters refuses any argument" {
        // `checkArgsSize` only rejects too few arguments, so `expected = 0` let 3.14159.round(2) drop the 2
        arityCheck("round", emptyList(), 0) shouldBe "checkNoArgs(fn = \"round\", args = args, location = loc)"
    }

    "arity check: a door with parameters checks the required count" {
        arityCheck("clamp", listOf("lo", "hi"), 2) shouldBe
            "checkArgsSize(fn = \"clamp\", args = args, expected = 2, location = loc)"
    }

    // ===== ownerReference =====

    "owner reference: an imported owner is spelled by its simple name" {
        ownerReference(
            ownerFqcn = "io.peekandpoke.klang.script.stdlib.KlangScriptStringExtensions",
            importedFqcns = setOf("io.peekandpoke.klang.script.stdlib.KlangScriptStringExtensions"),
            localNames = emptySet(),
        ) shouldBe "KlangScriptStringExtensions"
    }

    "owner reference: two imports sharing a simple name both stay qualified" {
        val imports = setOf(
            "io.peekandpoke.klang.script.stdlib.Osc",
            "io.peekandpoke.klang.sprudel.lang.Osc",
        )

        ownerReference("io.peekandpoke.klang.script.stdlib.Osc", imports, emptySet()) shouldBe
            "io.peekandpoke.klang.script.stdlib.Osc"

        ownerReference("io.peekandpoke.klang.sprudel.lang.Osc", imports, emptySet()) shouldBe
            "io.peekandpoke.klang.sprudel.lang.Osc"
    }

    "owner reference: an owner the file does not import stays qualified" {
        ownerReference(
            ownerFqcn = "io.peekandpoke.klang.script.stdlib.KlangScriptMath",
            importedFqcns = setOf("io.peekandpoke.klang.script.stdlib.KlangScriptOsc"),
            localNames = emptySet(),
        ) shouldBe "io.peekandpoke.klang.script.stdlib.KlangScriptMath"
    }

    "owner reference: an unimported owner whose simple name IS imported, from elsewhere" {
        // The dangerous shape: exactly one import spells `Osc`, but it is the other one. Shortening
        // here would compile and call into the wrong class.
        ownerReference(
            ownerFqcn = "io.peekandpoke.klang.sprudel.lang.Osc",
            importedFqcns = setOf("io.peekandpoke.klang.script.stdlib.Osc"),
            localNames = emptySet(),
        ) shouldBe "io.peekandpoke.klang.sprudel.lang.Osc"
    }

    "owner reference: a name the generated body binds itself stays qualified" {
        // sprudel's `object vowel` has a `vowel` parameter, so the body holds `val vowel = ...`
        // and a shortened `vowel.invoke(...)` would resolve to that local instead of the object.
        ownerReference(
            ownerFqcn = "io.peekandpoke.klang.sprudel.lang.vowel",
            importedFqcns = setOf("io.peekandpoke.klang.sprudel.lang.vowel"),
            localNames = GENERATED_LOCAL_NAMES + setOf("vowel", "wet", "floor"),
        ) shouldBe "io.peekandpoke.klang.sprudel.lang.vowel"
    }

    "owner reference: an identifier the generated bodies always bind stays qualified" {
        ownerReference(
            ownerFqcn = "io.peekandpoke.klang.sprudel.lang.args",
            importedFqcns = setOf("io.peekandpoke.klang.sprudel.lang.args"),
            localNames = GENERATED_LOCAL_NAMES,
        ) shouldBe "io.peekandpoke.klang.sprudel.lang.args"
    }

    "owner reference: a name with no package is already simple" {
        ownerReference("Standalone", setOf("Standalone"), emptySet()) shouldBe "Standalone"
    }
})
