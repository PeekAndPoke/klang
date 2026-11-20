/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.Switches

/** The look as made shown, the new look kept for the key x. */
private val TODAY = LookFlip.of(Switches.parse(""))

/** The new look shown, the look as made kept. */
private val NEW = LookFlip.of(Switches.parse("?look=new"))

/** The group labelled [label]. */
private fun List<LookGroup>.named(label: String): LookGroup = single { it.label == label }

class LookGroupSpec : StringSpec({

    "every variant with its key, the whole look first, then as the keys turn them over" {
        LookGroup.all(look = TODAY, fog = FogQuality.High).map { "${it.label} (${it.key})" } shouldBe listOf(
            "Look (x)", "Stage (m)", "Floor (p)", "Haze (h)", "Eyes (i)", "Skin (l)", "World (n)", "Underworld (v)",
            "Ripple (b)", "Reflection (c)", "Stone (k)",
        )
    }

    "each option labelled by its variant's word with a capital, the fence as the line says it" {
        LookGroup.all(look = TODAY, fog = FogQuality.High).map { group -> group.options.map { it.label } } shouldBe listOf(
            listOf("Today", "New"),
            listOf("Wall", "Fence"),
            listOf("Plain", "Mirror"),
            listOf("Whole", "Parts"),
            listOf("Face", "Shadow"),
            listOf("Suit", "Lacquer"),
            listOf("Off", "Day", "Night", "Ember", "Drift"),
            listOf("Off", "On"),
            listOf("None", "Faint", "Gentle", "Strong"),
            listOf("Crisp", "Soft", "Stone"),
            listOf("Plain", "Marble"),
        )
    }

    "the one in use is marked, one in each group" {
        fun marked(look: LookFlip) = LookGroup.all(look = look, fog = FogQuality.High).map { group ->
            group.options.filter { it.marked }.map { it.label }
        }

        marked(TODAY) shouldBe listOf(
            listOf("Today"), listOf("Wall"), listOf("Plain"), listOf("Whole"), listOf("Face"), listOf("Suit"), listOf("Off"),
            listOf("Off"), listOf("Gentle"), listOf("Crisp"), listOf("Plain"),
        )
        marked(NEW) shouldBe listOf(
            listOf("New"), listOf("Fence"), listOf("Mirror"), listOf("Parts"), listOf("Shadow"), listOf("Lacquer"),
            listOf("Drift"), listOf("Off"), listOf("Gentle"), listOf("Crisp"), listOf("Plain"),
        )
        marked(NEW.tune(NEW.shown.copy(world = WorldLook.Night, stone = StoneLook.Marble))).let {
            it[6] shouldBe listOf("Night")
            it[10] shouldBe listOf("Marble")
        }
    }

    "a variant's option chooses the variants shown with that one changed, the rest as they are; none flips" {
        val groups = LookGroup.all(look = NEW, fog = FogQuality.High)
        val night = groups.named("World").options.single { it.label == "Night" }

        night.to shouldBe LookVariants.NEW.copy(world = WorldLook.Night)
        night.flips shouldBe false
        groups.named("Stage").options.single { it.label == "Wall" }.to shouldBe LookVariants.NEW.copy(stage = StageLook.Wall)
        groups.named("Reflection").options.single { it.label == "Stone" }.to shouldBe LookVariants.NEW.copy(blur = BlurLook.Stone)
        groups.named("Eyes").options.single { it.label == "Face" }.to shouldBe LookVariants.NEW.copy(eyes = EyesLook.Face)
        // the marked one chooses what is shown already
        groups.drop(1).forEach { group -> group.options.single { it.marked }.to shouldBe LookVariants.NEW }
        groups.drop(1).flatMap { it.options }.none { it.flips } shouldBe true
    }

    "the whole look: the side not shown flips to it, as it was last tuned; the side shown stays" {
        val today = LookGroup.all(look = TODAY, fog = FogQuality.High).named("Look").options

        today.map { it.flips } shouldBe listOf(false, true)
        today.map { it.to } shouldBe listOf(LookVariants(), LookVariants.NEW)

        val tuned = NEW.tune(NEW.shown.copy(world = WorldLook.Night))
        val new = LookGroup.all(look = tuned, fog = FogQuality.High).named("Look").options

        new.map { it.flips } shouldBe listOf(true, false)
        new.map { it.to } shouldBe listOf(LookVariants(), LookVariants.NEW.copy(world = WorldLook.Night))
    }

    "today: the haze and the mirror's looks do nothing, and say why; the rest shows" {
        val held = LookGroup.all(look = TODAY, fog = FogQuality.High).associate { it.label to it.held }

        held shouldBe mapOf(
            "Look" to null,
            "Stage" to null,
            "Floor" to null,
            "Haze" to "with the fence only",
            "Eyes" to null,
            "Skin" to null,
            "World" to null,
            "Underworld" to "with the mirror floor only",
            "Ripple" to "with the mirror floor only",
            "Reflection" to "with the mirror floor only",
            "Stone" to "with the mirror floor only",
        )
    }

    "the new look at fog high: everything shows" {
        LookGroup.all(look = NEW, fog = FogQuality.High).mapNotNull { it.held } shouldBe emptyList()
    }

    "the new look at fog low: no mirror, so the floor and the mirror's looks say so; the haze still parts" {
        val held = LookGroup.all(look = NEW, fog = FogQuality.Low).associate { it.label to it.held }

        held.filterValues { it != null } shouldBe mapOf(
            "Floor" to "no mirror at fog low",
            "Underworld" to "no mirror at fog low",
            "Ripple" to "no mirror at fog low",
            "Reflection" to "no mirror at fog low",
            "Stone" to "no mirror at fog low",
        )
    }

    "fog off: no haze to part either" {
        val held = LookGroup.all(look = NEW, fog = FogQuality.Off).associate { it.label to it.held }

        held["Haze"] shouldBe "no haze at fog off"
        held["Floor"] shouldBe "no mirror at fog off"
        held["Stone"] shouldBe "no mirror at fog off"
    }

    "the floor at fog low says why even where the plain floor is in use: the mirror would not be drawn" {
        LookGroup.all(look = TODAY, fog = FogQuality.Low).named("Floor").held shouldBe "no mirror at fog low"
    }
})
