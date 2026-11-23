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

/** Every group of [look] as a show starts: the fog high, the reflections on. */
private fun startsWith(look: LookFlip): List<LookGroup> =
    LookGroup.all(look = look, fog = FogQuality.High, reflections = ReflectionQuality.On)

/** The group labelled [label]. */
private fun List<LookGroup>.named(label: String): LookGroup = single { it.label == label }

class LookGroupSpec : StringSpec({

    "every variant with its key, the whole look first, then as the keys turn them over" {
        startsWith(TODAY).map { "${it.label} (${it.key})" } shouldBe listOf(
            "Look (x)", "Stage (m)", "Floor (p)", "Haze (h)", "Eyes (i)", "Skin (l)", "World (n)", "Ghosts (null)",
            "Underworld (v)", "Ripple (b)", "Blur (c)", "Stone (k)", "Outline (j)", "Around (null)",
        )
    }

    "each option labelled by its variant's word with a capital, the fence as the line says it" {
        startsWith(TODAY).map { group -> group.options.map { it.label } } shouldBe listOf(
            listOf("Today", "New"),
            listOf("Wall", "Fence"),
            listOf("Plain", "Mirror"),
            listOf("Whole", "Parts"),
            listOf("Face", "Shadow"),
            listOf("Suit", "Lacquer"),
            listOf("Off", "Day", "Twilight", "Night", "Ember", "Ghosts", "Drift"),
            listOf("Cold", "Red", "Green"),
            listOf("Off", "On"),
            listOf("None", "Faint", "Gentle", "Strong"),
            listOf("Crisp", "Soft", "Stone"),
            listOf("Plain", "Marble"),
            listOf("Off", "On"),
            listOf("Turns", "Forest", "Ruins", "Stones", "Off"),
        )
    }

    "the one in use is marked, one in each group" {
        fun marked(look: LookFlip) = startsWith(look).map { group ->
            group.options.filter { it.marked }.map { it.label }
        }

        marked(TODAY) shouldBe listOf(
            listOf("Today"), listOf("Wall"), listOf("Plain"), listOf("Whole"), listOf("Face"), listOf("Suit"), listOf("Off"),
            listOf("Cold"), listOf("Off"), listOf("Gentle"), listOf("Crisp"), listOf("Marble"), listOf("Off"), listOf("Off"),
        )
        marked(NEW) shouldBe listOf(
            listOf("New"), listOf("Fence"), listOf("Mirror"), listOf("Parts"), listOf("Shadow"), listOf("Lacquer"),
            listOf("Drift"), listOf("Cold"), listOf("Off"), listOf("Gentle"), listOf("Crisp"), listOf("Marble"), listOf("Off"),
            listOf("Turns"),
        )
        marked(NEW.tune(NEW.shown.copy(world = WorldLook.Night, ghosts = GhostLight.Red, stone = StoneLook.Plain))).let {
            it[6] shouldBe listOf("Night")
            it[7] shouldBe listOf("Red")
            it[11] shouldBe listOf("Plain")
        }
    }

    "a variant's option chooses the variants shown with that one changed, the rest as they are; none flips" {
        val groups = startsWith(NEW)
        val night = groups.named("World").options.single { it.label == "Night" }

        night.to shouldBe LookVariants.NEW.copy(world = WorldLook.Night)
        night.flips shouldBe false
        groups.named("Stage").options.single { it.label == "Wall" }.to shouldBe LookVariants.NEW.copy(stage = StageLook.Wall)
        groups.named("Blur").options.single { it.label == "Stone" }.to shouldBe LookVariants.NEW.copy(blur = BlurLook.Stone)
        groups.named("Eyes").options.single { it.label == "Face" }.to shouldBe LookVariants.NEW.copy(eyes = EyesLook.Face)
        groups.named("Around").options.single { it.label == "Ruins" }.to shouldBe LookVariants.NEW.copy(around = AroundLook.Ruins)
        // the marked one chooses what is shown already
        groups.drop(1).forEach { group -> group.options.single { it.marked }.to shouldBe LookVariants.NEW }
        groups.drop(1).flatMap { it.options }.none { it.flips } shouldBe true
    }

    "the whole look: the side not shown flips to it, as it was last tuned; the side shown stays" {
        val today = startsWith(TODAY).named("Look").options

        today.map { it.flips } shouldBe listOf(false, true)
        today.map { it.to } shouldBe listOf(LookVariants(), LookVariants.NEW)

        val tuned = NEW.tune(NEW.shown.copy(world = WorldLook.Night))
        val new = startsWith(tuned).named("Look").options

        new.map { it.flips } shouldBe listOf(true, false)
        new.map { it.to } shouldBe listOf(LookVariants(), LookVariants.NEW.copy(world = WorldLook.Night))
    }

    "today: the haze and the mirror's looks do nothing, and say why; the rest shows" {
        val held = startsWith(TODAY).associate { it.label to it.held }

        held shouldBe mapOf(
            "Look" to null,
            "Stage" to null,
            "Floor" to null,
            "Haze" to "with the fence only",
            "Eyes" to null,
            "Skin" to null,
            "World" to null,
            "Ghosts" to "in the ghosts world only",
            "Underworld" to "with the mirror floor only",
            "Ripple" to "with the mirror floor only",
            "Blur" to "with the mirror floor only",
            "Stone" to "with the mirror floor only",
            "Outline" to null,
            "Around" to null,
        )
    }

    "the new look with the reflections on: everything shows but the ghosts' colour, at every fog but the haze at fog off" {
        for (fog in FogQuality.entries) {
            val held = LookGroup.all(look = NEW, fog = fog, reflections = ReflectionQuality.On).mapNotNull { it.held }
            val ghosts = "in the ghosts world only"

            held shouldBe if (fog == FogQuality.Off) listOf("no haze at fog off", ghosts) else listOf(ghosts)
        }
    }

    "the ghosts' colour has no key, shows only in the ghosts world, and its options choose the colour" {
        val ghosts = startsWith(NEW.tune(NEW.shown.copy(world = WorldLook.Ghosts))).named("Ghosts")

        ghosts.key shouldBe null
        ghosts.held shouldBe null
        ghosts.options.single { it.label == "Green" }.to shouldBe LookVariants.NEW.copy(world = WorldLook.Ghosts, ghosts = GhostLight.Green)
    }

    "the new look with the reflections off: the floor and the mirror's looks say so; the haze still parts" {
        val held = LookGroup.all(look = NEW, fog = FogQuality.High, reflections = ReflectionQuality.Off).associate { it.label to it.held }

        held.filterValues { it != null } shouldBe mapOf(
            "Ghosts" to "in the ghosts world only",
            "Floor" to "reflections off",
            "Underworld" to "reflections off",
            "Ripple" to "reflections off",
            "Blur" to "reflections off",
            "Stone" to "reflections off",
        )
    }

    "fog off with the reflections on: no haze to part, and the mirror still shows" {
        val held = LookGroup.all(look = NEW, fog = FogQuality.Off, reflections = ReflectionQuality.On).associate { it.label to it.held }

        held["Haze"] shouldBe "no haze at fog off"
        held["Floor"] shouldBe null
        held["Stone"] shouldBe null
    }

    "the floor with the reflections off says why even where the plain floor is in use: the mirror would not be drawn" {
        LookGroup.all(look = TODAY, fog = FogQuality.High, reflections = ReflectionQuality.Off).named("Floor").held shouldBe
            "reflections off"
        LookGroup.all(look = TODAY, fog = FogQuality.Low, reflections = ReflectionQuality.On).named("Floor").held shouldBe null
    }
})
