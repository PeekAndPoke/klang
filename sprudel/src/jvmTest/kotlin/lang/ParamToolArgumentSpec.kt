/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.ast.MemberAccess
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.generated.generatedSprudelDocs
import io.peekandpoke.klang.script.generated.generatedStdlibDocs
import io.peekandpoke.klang.script.intel.AnalyzedAst
import io.peekandpoke.klang.script.intel.ArgumentBinding
import io.peekandpoke.klang.script.intel.argumentAt
import io.peekandpoke.klang.script.types.KlangParam

/**
 * Which parameter, and so which `@param-tool` tools, the editor finds under the cursor, against the
 * real generated registries. [argumentAt] is the resolution `findCallArgAtAst` in klangscript-ui
 * calls; the text-scanner fallback calls the same `bindArgument` under it.
 *
 * The registry is built the way the editor builds it: the stdlib first, then sprudel. So the
 * Katalyst `body(wet, material, configure)` and `gain(gain)` come before sprudel's variants.
 *
 * A named argument binds by its name, not by its position: after 3d(iii) made `body` wet-first,
 * `body(material = "wood", wet = 0.4)` put `material` at position 0, where the door has `wet`, and
 * the body tool vanished from "wood". And a call whose receiver type is unknown (`x => x.body(...)`)
 * must still find the variant that carries the tool.
 */
class ParamToolArgumentSpec : StringSpec({

    val registry = KlangDocsRegistry().apply {
        registerAll(generatedStdlibDocs)
        registerAll(generatedSprudelDocs)
    }

    /**
     * The binding of the argument starting at [needle], the way the editor resolves it. [typed]
     * says whether a member call's receiver type is known, and is asserted; a plain call has none.
     */
    fun bindingAt(code: String, needle: String, typed: Boolean = true): ArgumentBinding? {
        val analysis = AnalyzedAst.build(code, registry)
        val pos = code.indexOf(needle)
        withClue("needle '$needle' in '$code'") { pos shouldBeGreaterThanOrEqual 0 }

        val site = analysis.astIndex.callArgAt(pos).shouldNotBeNull()
        val callee = site.call.callee

        if (callee is MemberAccess) {
            val receiverType = analysis.typeOf(callee.obj)
            withClue("receiver of ${site.functionName} typed") { (receiverType != null) shouldBe typed }
        }

        return analysis.argumentAt(pos)?.binding
    }

    fun paramAt(code: String, needle: String, typed: Boolean = true): KlangParam? =
        bindingAt(code, needle, typed)?.param

    "body: a named material finds the body tool wherever it stands" {
        val code = """note("c3").s("saw").body(material = "wood", wet = 0.4)"""

        val material = paramAt(code, "\"wood\"").shouldNotBeNull()
        material.name shouldBe "material"
        material.uitools shouldBe listOf("SprudelBodyEditor", "SprudelBodySequenceEditor")

        paramAt(code, "0.4").shouldNotBeNull().name shouldBe "wet"
    }

    "body: the positional material, second after wet, finds the body tool" {
        val code = """note("c3").s("saw").body(0.4, "wood")"""

        paramAt(code, "\"wood\"").shouldNotBeNull().name shouldBe "material"
        paramAt(code, "0.4").shouldNotBeNull().name shouldBe "wet"
    }

    "body: the wet named first does not borrow the body tool" {
        val code = """note("c3").s("saw").body(wet = 0.3, material = "oak")"""

        paramAt(code, "0.3").shouldNotBeNull().uitools shouldBe emptyList()
        paramAt(code, "\"oak\"").shouldNotBeNull().name shouldBe "material"
    }

    "phaser: a named rate finds the phaser tool at position 0" {
        val code = """note("c3").phaser(rate = 2, wet = 0.5)"""

        val rate = paramAt(code, "2,").shouldNotBeNull()
        rate.name shouldBe "rate"
        rate.uitools shouldBe listOf("SprudelPhaserEditor", "SprudelPhaserSequenceEditor")
    }

    "lpf: a named q alone finds the resonance tool, not the cutoff tool" {
        val q = paramAt("""note("c3").lpf(q = 5)""", "5").shouldNotBeNull()

        q.name shouldBe "q"
        q.uitools shouldBe listOf("SprudelLpResonanceEditor", "SprudelLpResonanceSequenceEditor")
    }

    "an unknown argument name binds to nothing rather than to its position" {
        paramAt("""note("c3").body(stuff = "wood")""", "\"wood\"").shouldBeNull()
    }

    // ── Receiver type unknown: inside `x => x...` nothing types `x` ──────────────────────────────

    val guitarShape = """
        export guitar3_shape = x => x.pregain(guitarDyna.fast(2)).sound(guitar).adsrOff().unison(voices = 19, spread = 0.10)
          .oscp("decay", guitarDecay)
          .clip(guitarClip.fast(2)).pan(1.0).body(material = "oak", wet = 0.3)
    """.trimIndent()

    "untyped receiver: the body tool shows on the named material in the song's lambda shape" {
        val material = paramAt(guitarShape, "\"oak\"", typed = false).shouldNotBeNull()

        material.name shouldBe "material"
        material.uitools shouldBe listOf("SprudelBodyEditor", "SprudelBodySequenceEditor")
    }

    "untyped receiver: gain finds its tool too (the Katalyst gain comes first as well)" {
        val amount = paramAt("""export s = x => x.gain(0.5)""", "0.5", typed = false).shouldNotBeNull()

        amount.uitools shouldBe listOf("SprudelGainEditor", "SprudelGainSequenceEditor")
    }

    "untyped receiver: variants that disagree on the argument's param make it scalar-only" {
        // sprudel tremolo(depth, ...) against the Ignitor's tremolo(rate, ...)
        val depth = bindingAt("""let wob = o => o.tremolo(5, 0.3)""", "5,", typed = false).shouldNotBeNull()

        depth.param.name shouldBe "depth"
        depth.wholeCall shouldBe false
    }

    "untyped receiver: variants that agree by name keep the whole-call edit" {
        val material = bindingAt(guitarShape, "\"oak\"", typed = false).shouldNotBeNull()

        material.wholeCall shouldBe true
    }

    "typed receiver: the whole-call edit stays allowed" {
        bindingAt("""note("c3").tremolo(5, 0.3)""", "5,").shouldNotBeNull().wholeCall shouldBe true
    }

    "plain call without a top-level tooled variant: pan inside superimpose finds its tool" {
        val amount = paramAt("""note("c3").superimpose(pan(0.7))""", "0.7").shouldNotBeNull()

        amount.uitools shouldBe listOf("SprudelPanEditor", "SprudelPanSequenceEditor")
    }

    "typed receiver, the song's gain (control): the receiver picks the sprudel variant" {
        val amount = paramAt("""note("c3").scale("e3:minor").gain(0.190)""", "0.190").shouldNotBeNull()

        amount.uitools shouldBe listOf("SprudelGainEditor", "SprudelGainSequenceEditor")
    }
})
