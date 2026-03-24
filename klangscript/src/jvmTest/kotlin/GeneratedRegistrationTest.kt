package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.script.generated.generatedStdlibDocs
import io.peekandpoke.klang.script.generated.registerStdlibGenerated
import io.peekandpoke.klang.script.runtime.ArrayValue
import io.peekandpoke.klang.script.runtime.BooleanValue
import io.peekandpoke.klang.script.runtime.NumberValue
import io.peekandpoke.klang.script.runtime.StringValue
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangCodeSampleType

class GeneratedRegistrationTest : StringSpec({

    fun engine(): KlangScriptEngine = klangScript {
        registerStdlibGenerated()
    }

    // ── Math @Object methods ────────────────────────────────────────────

    "Math.sqrt via generated registration" {
        val result = engine().execute("Math.sqrt(16)")
        (result as NumberValue).value shouldBe (4.0 plusOrMinus 0.001)
    }

    "Math.abs via generated registration" {
        val result = engine().execute("Math.abs(-5)")
        (result as NumberValue).value shouldBe (5.0 plusOrMinus 0.001)
    }

    "Math.floor via generated registration" {
        val result = engine().execute("Math.floor(3.7)")
        (result as NumberValue).value shouldBe (3.0 plusOrMinus 0.001)
    }

    "Math.ceil via generated registration" {
        val result = engine().execute("Math.ceil(3.2)")
        (result as NumberValue).value shouldBe (4.0 plusOrMinus 0.001)
    }

    "Math.round via generated registration" {
        val result = engine().execute("Math.round(3.5)")
        (result as NumberValue).value shouldBe (4.0 plusOrMinus 0.001)
    }

    "Math.min via generated registration" {
        val result = engine().execute("Math.min(3, 7)")
        (result as NumberValue).value shouldBe (3.0 plusOrMinus 0.001)
    }

    "Math.max via generated registration" {
        val result = engine().execute("Math.max(3, 7)")
        (result as NumberValue).value shouldBe (7.0 plusOrMinus 0.001)
    }

    "Math.pow via generated registration" {
        val result = engine().execute("Math.pow(2, 10)")
        (result as NumberValue).value shouldBe (1024.0 plusOrMinus 0.001)
    }

    "Math.sin via generated registration" {
        val result = engine().execute("Math.sin(0)")
        (result as NumberValue).value shouldBe (0.0 plusOrMinus 0.001)
    }

    "Math.cos via generated registration" {
        val result = engine().execute("Math.cos(0)")
        (result as NumberValue).value shouldBe (1.0 plusOrMinus 0.001)
    }

    "Math.tan via generated registration" {
        val result = engine().execute("Math.tan(0)")
        (result as NumberValue).value shouldBe (0.0 plusOrMinus 0.001)
    }

    // ── String @TypeExtensions methods ──────────────────────────────────

    "String.length via generated registration" {
        val result = engine().execute("\"hello\".length()")
        (result as NumberValue).value shouldBe (5.0 plusOrMinus 0.001)
    }

    "String.charAt via generated registration" {
        val result = engine().execute("\"hello\".charAt(1)")
        (result as StringValue).value shouldBe "e"
    }

    "String.toUpperCase via generated registration" {
        val result = engine().execute("\"hello\".toUpperCase()")
        (result as StringValue).value shouldBe "HELLO"
    }

    "String.split via generated registration" {
        val result = engine().execute("\"a,b,c\".split(\",\").size()")
        (result as NumberValue).value shouldBe (3.0 plusOrMinus 0.001)
    }

    "String.replace via generated registration" {
        val result = engine().execute("\"hello world\".replace(\"world\", \"klang\")")
        (result as StringValue).value shouldBe "hello klang"
    }

    "String.repeat via generated registration" {
        val result = engine().execute("\"ha\".repeat(3)")
        (result as StringValue).value shouldBe "hahaha"
    }

    "String.startsWith via generated registration" {
        val result = engine().execute("\"hello\".startsWith(\"hel\")")
        (result as BooleanValue).value shouldBe true
    }

    "String.indexOf via generated registration" {
        val result = engine().execute("\"hello\".indexOf(\"ll\")")
        (result as NumberValue).value shouldBe (2.0 plusOrMinus 0.001)
    }

    // ── Array @TypeExtensions methods ───────────────────────────────────

    "Array.size via generated registration" {
        val result = engine().execute("[1, 2, 3].size()")
        (result as NumberValue).value shouldBe (3.0 plusOrMinus 0.001)
    }

    "Array.first via generated registration" {
        val result = engine().execute("[10, 20, 30].first()")
        (result as NumberValue).value shouldBe (10.0 plusOrMinus 0.001)
    }

    "Array.last via generated registration" {
        val result = engine().execute("[10, 20, 30].last()")
        (result as NumberValue).value shouldBe (30.0 plusOrMinus 0.001)
    }

    "Array.reversed via generated registration" {
        val result = engine().execute("[1, 2, 3].reversed().joinToString(\", \")")
        (result as StringValue).value shouldBe "3, 2, 1"
    }

    "Array.contains via generated registration" {
        val result = engine().execute("[1, 2, 3].contains(2)")
        (result as BooleanValue).value shouldBe true
    }

    "Array.isEmpty via generated registration" {
        val result = engine().execute("[].isEmpty()")
        (result as BooleanValue).value shouldBe true
    }

    "Array.joinToString via generated registration" {
        val result = engine().execute("[1, 2, 3].joinToString(\" - \")")
        (result as StringValue).value shouldBe "1 - 2 - 3"
    }

    // ── Number/Boolean @TypeExtensions ──────────────────────────────────

    "Number.toString via generated registration" {
        val result = engine().execute("(42).toString()")
        (result as StringValue).value shouldBe "42"
    }

    "Number.toString via generated registration - decimal" {
        val result = engine().execute("(3.14).toString()")
        (result as StringValue).value shouldBe "3.14"
    }

    // ── Object raw-args extension methods ───────────────────────────────

    "Object.keys via generated registration" {
        val result = engine().execute("""Object.keys({ a: 1, b: 2 })""")
        result shouldBe ArrayValue(mutableListOf(StringValue("a"), StringValue("b")))
    }

    "Object.values via generated registration" {
        val result = engine().execute("""Object.values({ x: 10, y: 20 })""")
        result shouldBe ArrayValue(mutableListOf(NumberValue(10.0), NumberValue(20.0)))
    }

    "Object.entries via generated registration" {
        val result = engine().execute("""Object.entries({ k: 1 }).size()""")
        (result as NumberValue).value shouldBe (1.0 plusOrMinus 0.001)
    }

    // ── Docs: Math ──────────────────────────────────────────────────────

    "generated docs contain all Math methods" {
        val expectedMethods = setOf("sqrt", "abs", "floor", "ceil", "round", "sin", "cos", "tan", "min", "max", "pow")
        for (method in expectedMethods) {
            generatedStdlibDocs[method] shouldNotBe null
        }
    }

    "generated docs have correct structure for sqrt" {
        val sqrtDoc = generatedStdlibDocs["sqrt"]!!
        sqrtDoc.name shouldBe "sqrt"
        sqrtDoc.category shouldBe "math"
        sqrtDoc.library shouldBe "stdlib"
        sqrtDoc.tags shouldBe listOf("arithmetic", "calculation")
        sqrtDoc.variants shouldHaveSize 1

        val callable = sqrtDoc.variants[0] as KlangCallable
        callable.receiver?.simpleName shouldBe "Math"
        callable.params shouldHaveSize 1
        callable.params[0].name shouldBe "x"
        callable.params[0].type.simpleName shouldBe "Number"
        callable.returnType?.simpleName shouldBe "Number"
        callable.samples shouldHaveSize 1
        callable.samples[0].code shouldBe "Math.sqrt(16)  // 4.0"
        callable.samples[0].type shouldBe KlangCodeSampleType.EXECUTABLE
    }

    "generated docs for min have two params" {
        val minDoc = generatedStdlibDocs["min"]!!
        val callable = minDoc.variants[0] as KlangCallable
        callable.params shouldHaveSize 2
        callable.params[0].name shouldBe "a"
        callable.params[1].name shouldBe "b"
    }

    // ── Docs: String @TypeExtensions ────────────────────────────────────

    "generated docs contain String extension methods" {
        val expectedMethods = setOf(
            "length", "charAt", "substring", "indexOf", "split",
            "toUpperCase", "toLowerCase", "trim", "startsWith", "endsWith", "replace",
            "slice", "concat", "repeat"
        )
        for (method in expectedMethods) {
            generatedStdlibDocs[method] shouldNotBe null
        }
    }

    "generated docs for String.length has String receiver and no script params" {
        val doc = generatedStdlibDocs["length"]!!
        val callable = doc.variants[0] as KlangCallable
        callable.receiver?.simpleName shouldBe "String"
        // 'self' param is excluded from docs — no script-visible params
        callable.params shouldHaveSize 0
    }

    "generated docs for String.charAt has one script param (index)" {
        val doc = generatedStdlibDocs["charAt"]!!
        val callable = doc.variants[0] as KlangCallable
        callable.receiver?.simpleName shouldBe "String"
        callable.params shouldHaveSize 1
        callable.params[0].name shouldBe "index"
    }

    // ── Docs: Array @TypeExtensions ─────────────────────────────────────

    "generated docs contain Array extension methods" {
        val expectedMethods = setOf(
            "size", "first", "last", "add", "reversed",
            "joinToString", "contains", "isEmpty", "isNotEmpty"
        )
        for (method in expectedMethods) {
            generatedStdlibDocs[method] shouldNotBe null
        }
    }

    // ── Docs: Object raw-args ───────────────────────────────────────────

    "generated docs contain Object methods" {
        for (method in listOf("keys", "values", "entries")) {
            generatedStdlibDocs[method] shouldNotBe null
        }
    }

    "generated docs for Object.keys has Object receiver and no internal params" {
        val doc = generatedStdlibDocs["keys"]!!
        val callable = doc.variants[0] as KlangCallable
        callable.receiver?.simpleName shouldBe "Object"
        // Raw-args internal params (List<RuntimeValue>, SourceLocation?) are excluded from docs
        callable.params shouldHaveSize 0
    }

    "generated docs for Object.keys has executable sample" {
        val doc = generatedStdlibDocs["keys"]!!
        val callable = doc.variants[0] as KlangCallable
        callable.samples shouldHaveSize 1
        callable.samples[0].type shouldBe KlangCodeSampleType.EXECUTABLE
    }
})
