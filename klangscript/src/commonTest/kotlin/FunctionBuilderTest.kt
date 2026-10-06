/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.script.FunctionBuilderTestFixtures.IgnitorLike
import io.peekandpoke.klang.script.builder.createFunction
import io.peekandpoke.klang.script.runtime.KlangScriptArgumentError
import io.peekandpoke.klang.script.runtime.NumberValue
import io.peekandpoke.klang.script.runtime.ParamSpec
import io.peekandpoke.klang.script.runtime.StringValue

/** Top-level fixtures — Kotlin disallows nested named objects inside StringSpec init. */
private object FunctionBuilderTestFixtures {
    object IgnitorLike {
        override fun toString() = "[IgnitorLike]"
    }
}

/**
 * Phase 4 — end-to-end coverage for the new createFunction builder.
 *
 * Exercises:
 *   - Top-level function registration with required, optional, and vararg slots.
 *   - Receiver-bound methods (`withReceiver<T>()`) on a registered native object.
 *   - Both call styles (positional and named) end-to-end.
 *   - Defaults invoked when the named call omits an optional slot.
 *   - Vararg payload via positional tail and via named array.
 *   - Error paths: unknown name, missing required, mixing, vararg-not-array.
 */
class FunctionBuilderTest : StringSpec({

    "required + optional: positional call uses both supplied" {
        val engine = klangScriptEngine {
            createFunction("filter")
                .withParam<Double>("cutoff")
                .withOptionalParam<Double>("q") { 1.0 }
                .body { cutoff: Double, q: Double -> cutoff + q }
        }
        (engine.execute("filter(800, 0.5)") as NumberValue).value shouldBe 800.5
    }

    "required + optional: positional call omits optional → default fires" {
        val engine = klangScriptEngine {
            createFunction("filter")
                .withParam<Double>("cutoff")
                .withOptionalParam<Double>("q") { 1.0 }
                .body { cutoff: Double, q: Double -> cutoff + q }
        }
        (engine.execute("filter(800)") as NumberValue).value shouldBe 801.0
    }

    "required + optional: named call any order" {
        val engine = klangScriptEngine {
            createFunction("filter")
                .withParam<Double>("cutoff")
                .withOptionalParam<Double>("q") { 1.0 }
                .body { cutoff: Double, q: Double -> cutoff + q }
        }
        (engine.execute("filter(q = 0.5, cutoff = 800)") as NumberValue).value shouldBe 800.5
    }

    "required + optional: named call omits optional → default fires" {
        val engine = klangScriptEngine {
            createFunction("filter")
                .withParam<Double>("cutoff")
                .withOptionalParam<Double>("q") { 1.0 }
                .body { cutoff: Double, q: Double -> cutoff + q }
        }
        (engine.execute("filter(cutoff = 800)") as NumberValue).value shouldBe 801.0
    }

    "missing required parameter (named) → KlangScriptArgumentError" {
        val engine = klangScriptEngine {
            createFunction("filter")
                .withParam<Double>("cutoff")
                .withOptionalParam<Double>("q") { 1.0 }
                .body { cutoff: Double, q: Double -> cutoff + q }
        }
        val err = shouldThrow<KlangScriptArgumentError> {
            engine.execute("filter(q = 0.5)")
        }
        err.message!! shouldContain "missing required parameter"
        err.message!! shouldContain "cutoff"
    }

    "unknown named parameter → KlangScriptArgumentError lists expected" {
        val engine = klangScriptEngine {
            createFunction("filter")
                .withParam<Double>("cutoff")
                .body { cutoff: Double -> cutoff }
        }
        val err = shouldThrow<KlangScriptArgumentError> {
            engine.execute("filter(nope = 1)")
        }
        err.message!! shouldContain "unknown parameter 'nope'"
        err.message!! shouldContain "cutoff"
    }

    "default thunk runs lazily (only when arg is omitted)" {
        var thunkCalls = 0
        val engine = klangScriptEngine {
            createFunction("snd")
                .withOptionalParam<Double>("level") { thunkCalls++; 0.5 }
                .body { level: Double -> level }
        }

        engine.execute("snd(0.9)")    // supplied → no thunk
        thunkCalls shouldBe 0

        engine.execute("snd()")       // omitted → thunk fires
        thunkCalls shouldBe 1

        engine.execute("snd()")       // again
        thunkCalls shouldBe 2
    }

    // ── Receiver-bound methods ────────────────────────────────────────────────

    "withReceiver: positional call routes through extension method" {
        val engine = klangScriptEngine {
            registerObject("Ignitor", IgnitorLike) {}
            createFunction("describe")
                .withReceiver<IgnitorLike>()
                .withParam<String>("label")
                .body { rcv: IgnitorLike, label: String -> "$rcv:$label" }
        }
        (engine.execute("Ignitor.describe(\"hi\")") as StringValue).value shouldBe "[IgnitorLike]:hi"
    }

    "withReceiver: named call binds by name on the script-visible param" {
        val engine = klangScriptEngine {
            registerObject("Ignitor", IgnitorLike) {}
            createFunction("describe")
                .withReceiver<IgnitorLike>()
                .withParam<String>("label")
                .body { rcv: IgnitorLike, label: String -> "$rcv:$label" }
        }
        (engine.execute("Ignitor.describe(label = \"hello\")") as StringValue).value shouldBe "[IgnitorLike]:hello"
    }

    // ── Vararg ────────────────────────────────────────────────────────────────

    "vararg: positional tail absorbed into List" {
        val engine = klangScriptEngine {
            createFunction("stack")
                .withParam<String>("orbit")
                .withVararg<String>("samples")
                .body { orbit: String, samples: List<String> ->
                    "$orbit:${samples.joinToString(",")}"
                }
        }
        (engine.execute("stack(\"d1\", \"bd\", \"sd\", \"hh\")") as StringValue).value shouldBe "d1:bd,sd,hh"
    }

    "vararg: named call passes an array literal" {
        val engine = klangScriptEngine {
            createFunction("stack")
                .withParam<String>("orbit")
                .withVararg<String>("samples")
                .body { orbit: String, samples: List<String> ->
                    "$orbit:${samples.joinToString(",")}"
                }
        }
        (engine.execute("stack(orbit = \"d1\", samples = [\"bd\", \"sd\"])") as StringValue).value shouldBe "d1:bd,sd"
    }

    "vararg: positional call with empty tail → empty List" {
        val engine = klangScriptEngine {
            createFunction("stack")
                .withParam<String>("orbit")
                .withVararg<String>("samples")
                .body { orbit: String, samples: List<String> ->
                    "$orbit:${samples.size}"
                }
        }
        (engine.execute("stack(\"d1\")") as StringValue).value shouldBe "d1:0"
    }

    "vararg: named call omits the vararg slot → empty List default" {
        val engine = klangScriptEngine {
            createFunction("stack")
                .withParam<String>("orbit")
                .withVararg<String>("samples")
                .body { orbit: String, samples: List<String> ->
                    "$orbit:${samples.size}"
                }
        }
        (engine.execute("stack(orbit = \"d1\")") as StringValue).value shouldBe "d1:0"
    }

    "vararg: named call with non-array value → strict error" {
        val engine = klangScriptEngine {
            createFunction("stack")
                .withParam<String>("orbit")
                .withVararg<String>("samples")
                .body { _: String, _: List<String> -> "ok" }
        }
        val err = shouldThrow<KlangScriptArgumentError> {
            engine.execute("stack(orbit = \"d1\", samples = \"bd\")")
        }
        err.message!! shouldContain "vararg parameter 'samples' requires an array"
    }

    // ── Cross-cutting ─────────────────────────────────────────────────────────

    "mixing positional and named at call site is rejected" {
        val engine = klangScriptEngine {
            createFunction("filter")
                .withParam<Double>("cutoff")
                .withOptionalParam<Double>("q") { 1.0 }
                .body { _: Double, _: Double -> 0.0 }
        }
        val err = shouldThrow<KlangScriptArgumentError> {
            engine.execute("filter(800, q = 0.5)")
        }
        err.message!! shouldContain "all positional or all named"
    }

    "no params, no receiver: zero-arity body works" {
        val engine = klangScriptEngine {
            createFunction("answer").body { 42.0 }
        }
        (engine.execute("answer()") as NumberValue).value shouldBe 42.0
    }

    // ── Regression coverage for the four code-review fixes ───────────────────

    "regression: zero-param builder rejects extra positional arguments" {
        val engine = klangScriptEngine {
            createFunction("answer").body { 42.0 }
        }
        val err = shouldThrow<KlangScriptArgumentError> {
            engine.execute("answer(99, 100)")
        }
        err.message!! shouldContain "expects no arguments"
    }

    "regression: zero-param builder rejects single extra positional argument" {
        val engine = klangScriptEngine {
            createFunction("answer").body { 42.0 }
        }
        val err = shouldThrow<KlangScriptArgumentError> {
            engine.execute("answer(99)")
        }
        err.message!! shouldContain "expects no arguments"
    }

    "regression: convertSlot rejects null for non-nullable param" {
        // Phase 4 builder param types are constrained <reified T : Any>, so all
        // declared params are non-nullable. Passing null should error cleanly,
        // not NPE inside the body.
        val engine = klangScriptEngine {
            createFunction("double")
                .withParam<Double>("x")
                .body { x: Double -> x * 2 }
        }
        val err = shouldThrow<KlangScriptArgumentError> {
            engine.execute("double(null)")
        }
        err.message!! shouldContain "not nullable"
    }

    "an optional parameter spec without a default thunk is refused at construction" {
        // Every omitted optional is filled from its thunk on every script call. Before 2026-10-06 a
        // KSP door with a non-literal default produced such a spec and relied on an arity dispatch in
        // its body; the processor now refuses that default, so a thunkless optional spec is a bug.
        val err = shouldThrow<IllegalArgumentException> {
            ParamSpec(name = "q", kotlinType = Double::class, isOptional = true)
        }
        err.message shouldBe "ParamSpec 'q' is optional but has no default thunk"
    }

    // ── Reviewer-flagged coverage gaps ───────────────────────────────────────

    "vararg + receiver combined: positional tail works" {
        val engine = klangScriptEngine {
            registerObject("Ignitor", IgnitorLike) {}
            createFunction("stack")
                .withReceiver<IgnitorLike>()
                .withParam<String>("orbit")
                .withVararg<String>("samples")
                .body { rcv: IgnitorLike, orbit: String, samples: List<String> ->
                    "$rcv:$orbit:${samples.joinToString(",")}"
                }
        }
        (engine.execute("Ignitor.stack(\"d1\", \"bd\", \"sd\")") as StringValue).value shouldBe "[IgnitorLike]:d1:bd,sd"
    }

    "vararg + receiver combined: named call with array" {
        val engine = klangScriptEngine {
            registerObject("Ignitor", IgnitorLike) {}
            createFunction("stack")
                .withReceiver<IgnitorLike>()
                .withParam<String>("orbit")
                .withVararg<String>("samples")
                .body { rcv: IgnitorLike, orbit: String, samples: List<String> ->
                    "$rcv:$orbit:${samples.joinToString(",")}"
                }
        }
        (engine.execute("Ignitor.stack(orbit = \"d1\", samples = [\"bd\", \"sd\"])") as StringValue).value shouldBe "[IgnitorLike]:d1:bd,sd"
    }

    "nullable rejection error message includes function name, not param name" {
        val engine = klangScriptEngine {
            createFunction("double")
                .withParam<Double>("x")
                .body { x: Double -> x * 2 }
        }
        val err = shouldThrow<KlangScriptArgumentError> {
            engine.execute("double(null)")
        }
        err.functionName shouldBe "double"
        err.message!! shouldContain "not nullable"
    }

    "vararg non-array rejection error message includes function name" {
        val engine = klangScriptEngine {
            createFunction("stack")
                .withParam<String>("orbit")
                .withVararg<String>("samples")
                .body { _: String, _: List<String> -> "ok" }
        }
        val err = shouldThrow<KlangScriptArgumentError> {
            engine.execute("stack(orbit = \"d1\", samples = \"bd\")")
        }
        err.functionName shouldBe "stack"
        err.message!! shouldContain "vararg parameter 'samples'"
    }
})
