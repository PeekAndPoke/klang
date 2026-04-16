package io.peekandpoke.klang.script

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.script.FunctionBuilderTestFixtures.IgnitorLike
import io.peekandpoke.klang.script.builder.createFunction
import io.peekandpoke.klang.script.runtime.KlangScriptArgumentError
import io.peekandpoke.klang.script.runtime.NumberValue
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
        val engine = klangScript {
            createFunction("filter")
                .withParam<Double>("cutoff")
                .withOptionalParam<Double>("q") { 1.0 }
                .body { cutoff: Double, q: Double -> cutoff + q }
        }
        (engine.execute("filter(800, 0.5)") as NumberValue).value shouldBe 800.5
    }

    "required + optional: positional call omits optional → default fires" {
        val engine = klangScript {
            createFunction("filter")
                .withParam<Double>("cutoff")
                .withOptionalParam<Double>("q") { 1.0 }
                .body { cutoff: Double, q: Double -> cutoff + q }
        }
        (engine.execute("filter(800)") as NumberValue).value shouldBe 801.0
    }

    "required + optional: named call any order" {
        val engine = klangScript {
            createFunction("filter")
                .withParam<Double>("cutoff")
                .withOptionalParam<Double>("q") { 1.0 }
                .body { cutoff: Double, q: Double -> cutoff + q }
        }
        (engine.execute("filter(q = 0.5, cutoff = 800)") as NumberValue).value shouldBe 800.5
    }

    "required + optional: named call omits optional → default fires" {
        val engine = klangScript {
            createFunction("filter")
                .withParam<Double>("cutoff")
                .withOptionalParam<Double>("q") { 1.0 }
                .body { cutoff: Double, q: Double -> cutoff + q }
        }
        (engine.execute("filter(cutoff = 800)") as NumberValue).value shouldBe 801.0
    }

    "missing required parameter (named) → KlangScriptArgumentError" {
        val engine = klangScript {
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
        val engine = klangScript {
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
        val engine = klangScript {
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
        val engine = klangScript {
            registerObject("Ignitor", IgnitorLike) {}
            createFunction("describe")
                .withReceiver<IgnitorLike>()
                .withParam<String>("label")
                .body { rcv: IgnitorLike, label: String -> "$rcv:$label" }
        }
        (engine.execute("Ignitor.describe(\"hi\")") as StringValue).value shouldBe "[IgnitorLike]:hi"
    }

    "withReceiver: named call binds by name on the script-visible param" {
        val engine = klangScript {
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
        val engine = klangScript {
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
        val engine = klangScript {
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
        val engine = klangScript {
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
        val engine = klangScript {
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
        val engine = klangScript {
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
        val engine = klangScript {
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
        val engine = klangScript {
            createFunction("answer").body { 42.0 }
        }
        (engine.execute("answer()") as NumberValue).value shouldBe 42.0
    }
})
