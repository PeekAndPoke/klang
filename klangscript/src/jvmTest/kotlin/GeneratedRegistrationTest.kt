package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.script.generated.generatedStdlibDocs
import io.peekandpoke.klang.script.generated.registerStdlibGenerated
import io.peekandpoke.klang.script.runtime.NumberValue

class GeneratedRegistrationTest : StringSpec({

    fun createEngineWithGeneratedMath(): KlangScriptEngine {
        return klangScript {
            registerStdlibGenerated()
        }
    }

    "Math.sqrt via generated registration" {
        val engine = createEngineWithGeneratedMath()
        val result = engine.execute("Math.sqrt(16)")
        (result as NumberValue).value shouldBe (4.0 plusOrMinus 0.001)
    }

    "Math.abs via generated registration" {
        val engine = createEngineWithGeneratedMath()
        val result = engine.execute("Math.abs(-5)")
        (result as NumberValue).value shouldBe (5.0 plusOrMinus 0.001)
    }

    "Math.floor via generated registration" {
        val engine = createEngineWithGeneratedMath()
        val result = engine.execute("Math.floor(3.7)")
        (result as NumberValue).value shouldBe (3.0 plusOrMinus 0.001)
    }

    "Math.ceil via generated registration" {
        val engine = createEngineWithGeneratedMath()
        val result = engine.execute("Math.ceil(3.2)")
        (result as NumberValue).value shouldBe (4.0 plusOrMinus 0.001)
    }

    "Math.min via generated registration" {
        val engine = createEngineWithGeneratedMath()
        val result = engine.execute("Math.min(3, 7)")
        (result as NumberValue).value shouldBe (3.0 plusOrMinus 0.001)
    }

    "Math.max via generated registration" {
        val engine = createEngineWithGeneratedMath()
        val result = engine.execute("Math.max(3, 7)")
        (result as NumberValue).value shouldBe (7.0 plusOrMinus 0.001)
    }

    "Math.pow via generated registration" {
        val engine = createEngineWithGeneratedMath()
        val result = engine.execute("Math.pow(2, 10)")
        (result as NumberValue).value shouldBe (1024.0 plusOrMinus 0.001)
    }

    "Math.sin via generated registration" {
        val engine = createEngineWithGeneratedMath()
        val result = engine.execute("Math.sin(0)")
        (result as NumberValue).value shouldBe (0.0 plusOrMinus 0.001)
    }

    "generated docs contain all Math methods" {
        generatedStdlibDocs.keys shouldNotBe emptySet<String>()

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
        sqrtDoc.variants.size shouldBe 1

        val callable = sqrtDoc.variants[0] as io.peekandpoke.klang.script.types.KlangCallable
        callable.receiver?.simpleName shouldBe "Math"
        callable.params.size shouldBe 1
        callable.params[0].name shouldBe "x"
        callable.params[0].type.simpleName shouldBe "Number"
        callable.returnType?.simpleName shouldBe "Number"
        callable.samples.size shouldBe 1
    }
})
