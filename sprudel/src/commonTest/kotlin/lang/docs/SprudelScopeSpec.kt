/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang.docs

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.generated.generatedSprudelDocs
import io.peekandpoke.klang.script.annotations.KlangScope

/**
 * Where each audio setting takes effect, asserted on the GENERATED docs rather than on the KDoc.
 *
 * This is the end of a four-link chain: a `@scope` tag in a KDoc block, parsed by `KDocParser`,
 * emitted by the processor, then merged (a name like `room` is emitted twice, once as a function and
 * once as its accessor object, and `KlangSymbol.mergeWith` keeps whichever side carries the scope).
 * Reading any one link cannot tell you the badge is right; only the merged symbol can, which is what
 * the popup and the library page actually render.
 *
 * The expectations come from the engine, not from a doc: a voice runs its own strip (`Voice.kt`),
 * each orbit owns ONE shared bus configured by its first sounding voice (`Cylinder.kt`), and `room`
 * and `delay` are the pair whose processor is the orbit's while the wet amount is per voice
 * (`SendRenderer.kt`).
 */
class SprudelScopeSpec : StringSpec({

    val expected = mapOf(
        // Per voice: every note carries its own value
        "lpf" to KlangScope.VOICE,
        "hpf" to KlangScope.VOICE,
        "bpf" to KlangScope.VOICE,
        "notch" to KlangScope.VOICE,
        "distort" to KlangScope.VOICE,
        "crush" to KlangScope.VOICE,
        "coarse" to KlangScope.VOICE,
        "gain" to KlangScope.VOICE,
        "pan" to KlangScope.VOICE,
        "postgain" to KlangScope.VOICE,
        "velocity" to KlangScope.VOICE,
        "adsr" to KlangScope.VOICE,
        "unison" to KlangScope.VOICE,
        "fm" to KlangScope.VOICE,
        "analog" to KlangScope.VOICE,
        "vibrato" to KlangScope.VOICE,
        // Per voice, and the one people misfile: it sits in the same file as the bus phaser
        "tremolo" to KlangScope.VOICE,

        // One processor per orbit, settings from the orbit's owning voice
        "body" to KlangScope.ORBIT,
        "vowel" to KlangScope.ORBIT,
        "phaser" to KlangScope.ORBIT,
        "compressor" to KlangScope.ORBIT,
        "duck" to KlangScope.ORBIT,

        // Orbit processor, per-voice send amount
        "room" to KlangScope.ORBIT_SEND,
        "delay" to KlangScope.ORBIT_SEND,

        // The whole playback
        "master" to KlangScope.MASTER,
    )

    "every audio setting reports the scope the engine gives it" {
        expected.forEach { (name, scope) ->
            withClue(name) {
                generatedSprudelDocs[name].shouldNotBeNull().scope shouldBe scope
            }
        }
    }

    "the scope survives the merge of a function and its accessor object" {
        // `room(...)` and `room.wet` are emitted as two symbols under one name. Only one of them
        // needs the tag for the badge to appear, so this row is what proves the merge keeps it.
        listOf("room", "delay", "body", "vowel", "phaser", "compressor", "duck", "tremolo").forEach { name ->
            withClue(name) {
                generatedSprudelDocs[name].shouldNotBeNull().scope.shouldNotBeNull()
            }
        }
    }

    "a new effect cannot ship without a scope" {
        // Routing chooses a bus rather than being one, so it carries no scope on purpose.
        val exempt = setOf("orbit", "o", "cylinder")

        val unscoped = generatedSprudelDocs
            .filterValues { it.category in setOf("effects", "dynamics") && it.scope == null }
            .keys
            .minus(exempt)
            .sorted()

        unscoped.shouldBeEmpty()
    }

    "no scope claims a value the enum does not have" {
        // A misspelled `@scope` is dropped with a build warning, which is easy to miss in a long log.
        generatedSprudelDocs.values.mapNotNull { it.scope }.forEach { scope ->
            withClue(scope.toString()) { KlangScope.ofTag(scope.tag) shouldBe scope }
        }
    }
})
