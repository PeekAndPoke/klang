/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.generated.generatedSprudelDocs
import io.peekandpoke.klang.script.generated.generatedStdlibDocs
import io.peekandpoke.klang.script.types.KlangCallable

/**
 * The pin of the parameter parity audit (KLANG-00417, step 4): a sprudel door and its twin of the same name on the
 * Ignitor (`IgnitorDsl`, and the builders whose knobs share a door's name, `adsr`, `range`, `analog`) or on the Katalyst
 * (`KatalystBuilder`, and its knobs' `KatalystKnobDsl`) take each parameter they share AT THE SAME POSITION, so a positional call means the same thing
 * in a pattern and in an instrument or a chain. The tremolo was the case that escaped (sprudel's depth first against
 * the Ignitor's rate first, B2); this spec compares every such pair the two generated registries have.
 *
 * A door may have knobs its twin lacks, after the shared ones (sprudel's `delay(..., cap)`); `callInfo` and
 * `configure` are not knobs. It pins the ORDER only: the words are the parity specs' business. A deliberate difference
 * is listed in [recorded] per door and twin receiver, with the twin's order it expects and its reason (the list in
 * `/dsl-design` section 4); anything else that disagrees fails here, and so does a recorded entry that drifted.
 */
class DoorOrderParitySpec : StringSpec({

    /**
     * Deliberate order differences, each recorded in `/dsl-design` section 4: (door, twin receiver) to the twin's knob
     * order this exception expects. A twin that drifts from it fails, as does a waived pair that no longer differs.
     */
    val recorded = mapOf(
        // sprudel leads with the depth; the Ignitor door leads with the modulator (its structure) and puts the
        // ratio first; the words agree since pitch pipeline step 4 (D4), and no positional sprudel call moved.
        ("fm" to "IgnitorDsl") to listOf("modulator", "ratio", "depth"),
    )

    val twinReceivers = setOf(
        "IgnitorDsl", "KatalystBuilder", "KatalystKnobDsl",
        // the builders whose knobs share a door's name (`adsr`, `range`, `analog`)
        "FilterBuilder", "BandFilterBuilder", "PitchEnvelopeBuilder", "FmBuilder", "TremoloBuilder", "VibratoBuilder",
    )
    val notKnobs = setOf("callInfo", "configure")

    fun KlangCallable.knobNames(): List<String> = params.map { it.name }.filter { it !in notKnobs }

    fun sprudelDoor(name: String): KlangCallable? =
        generatedSprudelDocs[name]?.variants?.filterIsInstance<KlangCallable>()
            ?.firstOrNull { it.receiver?.simpleName == "SprudelPattern" }

    fun twins(name: String): List<KlangCallable> =
        generatedStdlibDocs[name]?.variants?.filterIsInstance<KlangCallable>()
            ?.filter { it.receiver?.simpleName in twinReceivers }
            .orEmpty()

    val pairs: List<Pair<KlangCallable, KlangCallable>> = generatedSprudelDocs.keys.sorted().flatMap { name ->
        val door = sprudelDoor(name) ?: return@flatMap emptyList()

        twins(name).map { door to it }
    }

    "the registries have pairs to compare (the tremolo, the phaser, the delay, the compressor ...)" {
        pairs.size shouldBeGreaterThan 5
        pairs.map { it.first.name }.toSet() shouldContainAll listOf("tremolo", "phaser", "delay", "compressor", "adsr")
    }

    "every sprudel door and its Ignitor or Katalyst twin take their shared parameters in the same order" {
        val disagreements = pairs.mapNotNull { (door, twin) ->
            val doorKnobs = door.knobNames()
            val twinKnobs = twin.knobNames()
            val shared = doorKnobs.filter { it in twinKnobs }
            val samePositions = shared.all { doorKnobs.indexOf(it) == twinKnobs.indexOf(it) }
            val waived = recorded[door.name to twin.receiver?.simpleName]

            when {
                waived != null && twinKnobs != waived ->
                    "${door.name}: the recorded ${twin.receiver?.simpleName} order $waived drifted to $twinKnobs"
                waived != null || samePositions -> null
                else -> "${door.name}: sprudel $doorKnobs, ${twin.receiver?.simpleName} $twinKnobs"
            }
        }

        withClue(disagreements.joinToString("\n")) { disagreements.shouldBeEmpty() }
    }

    "every recorded exception still is one (a fixed order leaves the list)" {
        recorded.keys.forEach { (name, receiver) ->
            val door = sprudelDoor(name)
            val twin = twins(name).firstOrNull { it.receiver?.simpleName == receiver }
            val differs = door != null && twin != null && door.knobNames().filter { it in twin.knobNames() }
                .any { door.knobNames().indexOf(it) != twin.knobNames().indexOf(it) }

            withClue("$name on $receiver") { differs shouldBe true }
        }
    }
})
