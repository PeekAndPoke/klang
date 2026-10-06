/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.ksp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

/**
 * The emission helpers of the generated registration. The class literal and cast rows are what a
 * nullable function-typed parameter (`configure: ((B) -> B)? = null`, the configure-lambda door
 * shape) depends on: before them, the bridge emitted `Function1?::class` (not a type) and
 * `as (((B) -> B)?)?` (doubled nullability). Since 2026-10-06 a door is one call with its default
 * literals pasted (no arity dispatch), and a non-literal default is refused.
 */
class EmissionHelpersTest : StringSpec({

    "class literal name strips the nullable suffix" {
        classLiteralTypeName("Function1?") shouldBe "Function1"
    }

    "class literal name leaves a non-null type alone" {
        classLiteralTypeName("Double") shouldBe "Double"
    }

    "cast suffix adds ? once for a plain nullable type" {
        castSuffix("List<Double>", isNullable = true, classLiteral = "List") shouldBe " as List<Double>?"
    }

    "cast suffix does not double the ? of an already-nullable function type" {
        castSuffix("((Any) -> Any)?", isNullable = true, classLiteral = "Function1") shouldBe " as ((Any) -> Any)?"
    }

    "cast suffix is empty for the redundant Any? cast" {
        castSuffix("Any", isNullable = true, classLiteral = "Any") shouldBe ""
    }

    "cast suffix is empty when a nullable cast type is exactly the class literal (No cast needed)" {
        // `convertArgToKotlin(cls = Number::class)` already returns `Number?` (2026-10-06: 26 warnings)
        castSuffix("Number?", isNullable = true, classLiteral = "Number") shouldBe ""
        castSuffix("Double", isNullable = true, classLiteral = "Double") shouldBe ""
    }

    "cast suffix keeps the non-null cast on the nullable result" {
        castSuffix("Double", isNullable = false, classLiteral = "Double") shouldBe " as Double"
    }

    "cast suffix for a non-null function type" {
        castSuffix("((Any) -> Any)", isNullable = false, classLiteral = "Function1") shouldBe " as ((Any) -> Any)"
    }

    // ===== optional parameters: one call, the default literal pasted =====

    "optional nullable parameter defaulting to null reads through optArg" {
        optionalArgExpression("duck", param("depth", "Any", "Any", isNullable = true, index = 1, default = "null")) shouldBe
            "optArg(args, 1, Any::class, loc)"
    }

    "optional nullable parameter with a non-null default keeps its own default" {
        // `x: Double? = 1.0`: an explicit null must stay null, so no `?: 1.0`
        optionalArgExpression("f", param("x", "Double", "Double?", isNullable = true, index = 0, default = "1.0")) shouldBe
            "if (args.size > 0) convertArgToKotlin(fn = \"f\", args = args, index = 0, cls = Double::class, " +
            "nullable = true, loc = loc) else 1.0"
    }

    "optional non-null parameter casts the converted value and pastes its default" {
        optionalArgExpression("binaryN", param("bits", "Int", "Int", isNullable = false, index = 1, default = "16")) shouldBe
            "if (args.size > 1) convertArgToKotlin(fn = \"binaryN\", args = args, index = 1, cls = Int::class, " +
            "nullable = false, loc = loc) as Int else 16"
    }

    "a door with optional parameters renders one call and no arity dispatch" {
        val rendered = buildString {
            appendConversionsAndCall(
                indent = "",
                scriptName = "duck",
                scriptParams = listOf(
                    param("orbit", "Any", "Any", isNullable = true, index = 0, default = "null"),
                    param("depth", "Any", "Any", isNullable = true, index = 1, default = "null"),
                ),
                fnCall = "duck.invoke",
                selfArg = "",
                hasCallInfo = true,
            )
        }

        rendered shouldBe """
            |checkArgsSize(fn = "duck", args = args, expected = 0, location = loc)
            |wrapAsRuntimeValue(
            |    duck.invoke(
            |        orbit = optArg(args, 0, Any::class, loc),
            |        depth = optArg(args, 1, Any::class, loc),
            |        callInfo = callInfo,
            |    )
            |)
            |""".trimMargin()
    }

    "a call form registers under __invoke__ and names the object in its argument errors" {
        val item = SpecAwareItem(
            scriptName = "__invoke__",
            specsExpr = "specs",
            fnCall = "adsr.invoke",
            selfArg = "",
            scriptParams = listOf(param("attack", "Double", "Double", isNullable = false, index = 0, default = null)),
            receiverCast = null,
            isTopLevel = false,
            errorName = "adsr",
        )
        val rendered = item.renderRegistration()

        rendered shouldContain "name = \"__invoke__\","
        rendered shouldContain "checkArgsSize(fn = \"adsr\""
        rendered shouldContain "convertArgToKotlin(fn = \"adsr\""
        rendered shouldNotContain "fn = \"__invoke__\""
    }

    // ===== the one decision on a door default: paste the literal, or refuse the door =====

    "a safe literal default is pasted, trimmed" {
        decideDefault("lpf", "q", "null") shouldBe DefaultDecision(literal = "null", error = null)
        decideDefault("lpf", "q", " 0.707 ") shouldBe DefaultDecision(literal = "0.707", error = null)
        decideDefault("limiter", "mode", "\"normal\"") shouldBe DefaultDecision(literal = "\"normal\"", error = null)
    }

    "a non-literal default is refused, naming the door and the parameter" {
        decideDefault("vibrato", "rate", "IgnitorDsl.Slots.rate") shouldBe DefaultDecision(
            literal = null,
            error = "KlangScript door 'vibrato': optional parameter 'rate' has the non-literal default " +
                "`IgnitorDsl.Slots.rate`. A script-door default must be a literal (number, string, boolean or null): " +
                "the generated registration pastes it for an omitted argument. Bake the value as a literal, or make " +
                "the parameter's type nullable with `= null`, and resolve the real default in the body.",
        )
    }

    "a default the extractor could not read is refused too" {
        val decision = decideDefault("f", "x", null)

        decision.literal shouldBe null
        decision.error!!.contains("(a default the processor could not read)") shouldBe true
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
            "io.peekandpoke.klang.script.stdlib.Ignitor",
            "io.peekandpoke.klang.sprudel.lang.Ignitor",
        )

        ownerReference("io.peekandpoke.klang.script.stdlib.Ignitor", imports, emptySet()) shouldBe
            "io.peekandpoke.klang.script.stdlib.Ignitor"

        ownerReference("io.peekandpoke.klang.sprudel.lang.Ignitor", imports, emptySet()) shouldBe
            "io.peekandpoke.klang.sprudel.lang.Ignitor"
    }

    "owner reference: an owner the file does not import stays qualified" {
        ownerReference(
            ownerFqcn = "io.peekandpoke.klang.script.stdlib.KlangScriptMath",
            importedFqcns = setOf("io.peekandpoke.klang.script.stdlib.KlangScriptIgnitor"),
            localNames = emptySet(),
        ) shouldBe "io.peekandpoke.klang.script.stdlib.KlangScriptMath"
    }

    "owner reference: an unimported owner whose simple name IS imported, from elsewhere" {
        // The dangerous shape: exactly one import spells `Ignitor`, but it is the other one. Shortening
        // here would compile and call into the wrong class.
        ownerReference(
            ownerFqcn = "io.peekandpoke.klang.sprudel.lang.Ignitor",
            importedFqcns = setOf("io.peekandpoke.klang.script.stdlib.Ignitor"),
            localNames = emptySet(),
        ) shouldBe "io.peekandpoke.klang.sprudel.lang.Ignitor"
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

private fun param(
    name: String,
    kotlinType: String,
    castType: String,
    isNullable: Boolean,
    index: Int,
    default: String?,
) = SpecAwareItem.ResolvedParam(
    name = name,
    kotlinType = kotlinType,
    castType = castType,
    hasDefault = default != null,
    isNullable = isNullable,
    index = index,
    defaultLiteral = default,
)
