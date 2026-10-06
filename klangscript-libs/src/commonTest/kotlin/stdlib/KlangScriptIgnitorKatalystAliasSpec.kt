/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystParam
import io.peekandpoke.klang.script.generated.generatedStdlibDocs
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.BoundNativeMethod
import io.peekandpoke.klang.script.runtime.NativeObjectValue
import io.peekandpoke.klang.script.runtime.RuntimeValue
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangProperty

/**
 * The short names `Ign` and `Kat` and the `Katalyst.slot` accessor (the Ignitor/Katalyst naming, plan section 7.1,
 * "Alias specs"). Runs on the JVM and on JS.
 *
 * `Ign` IS `Ignitor` and `Kat` IS `Katalyst`, for EVERY member: the member table is read from the generated docs
 * registry, not written by hand, so a member added later is covered without touching this spec. The slot rows pin the
 * same-instance rule of both slot accessors: the script door and the Kotlin door hand back one object.
 */
class KlangScriptIgnitorKatalystAliasSpec : StringSpec({

    fun eval(code: String): RuntimeValue {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        return engine.execute(code)
    }

    fun native(code: String): Any? = eval(code).shouldBeInstanceOf<NativeObjectValue<*>>().value

    /** Every member the docs registry lists on the object whose script name is [objectName]. */
    fun membersOf(objectName: String): List<String> = generatedStdlibDocs.values
        .filter { symbol ->
            symbol.variants.any { decl ->
                (decl is KlangCallable && decl.receiver?.simpleName == objectName) ||
                        (decl is KlangProperty && decl.owner?.simpleName == objectName)
            }
        }
        .map { it.name }
        .sorted()

    /** `alias.member` and `full.member` resolve to the same registration: the same method of the same object, or the same value. */
    fun sameMember(alias: String, full: String, member: String, target: Any) {
        val short = eval("$alias.$member")
        val long = eval("$full.$member")

        when (long) {
            is BoundNativeMethod -> {
                val s = short.shouldBeInstanceOf<BoundNativeMethod>()
                s.methodName shouldBe long.methodName
                s.receiver.value shouldBeSameInstanceAs target
                long.receiver.value shouldBeSameInstanceAs target
                s.paramSpecs shouldBe long.paramSpecs
            }

            is NativeObjectValue<*> -> short.shouldBeInstanceOf<NativeObjectValue<*>>().value shouldBeSameInstanceAs long.value

            else -> short shouldBe long
        }
    }

    "every Ignitor member is reachable as Ign.<member>, the same registration" {
        val members = membersOf("Ignitor")

        // Guard the table: an empty or truncated listing would pass every row below vacuously.
        members.size shouldBeGreaterThanOrEqual 25
        members shouldContainAll listOf("sine", "saw", "supersaw", "param", "slot", "silence")

        members.forEach { member ->
            withClue("Ign.$member") { sameMember("Ign", "Ignitor", member, KlangScriptIgnitor) }
        }
    }

    "every Katalyst member is reachable as Kat.<member>, the same registration" {
        val members = membersOf("Katalyst")

        members shouldContainAll listOf("build", "classic", "param", "slot")

        members.forEach { member ->
            withClue("Kat.$member") { sameMember("Kat", "Katalyst", member, KlangScriptKatalyst) }
        }
    }

    "Kat(k => ...) is Katalyst(k => ...), and Kat() the empty chain" {
        native("Kat(k => k.reverb(0.2))") shouldBe native("Katalyst(k => k.reverb(0.2))")
        native("Kat(k => k.classic().gain(1.4))") shouldBe native("Katalyst(k => k.classic().gain(1.4))")
        native("Kat()") shouldBe KatalystDsl(emptyList())
    }

    "Ign.slot.lpf.freq is Ignitor.slot.lpf.freq is IgnitorDsl.Slots.lpf.freq" {
        native("Ign.slot.lpf.freq") shouldBeSameInstanceAs IgnitorDsl.Slots.lpf.freq
        native("Ignitor.slot.lpf.freq") shouldBeSameInstanceAs IgnitorDsl.Slots.lpf.freq
    }

    "every Katalyst.slot knob is Kat.slot's and KatalystDsl.Slots', the same instance" {
        val s = KatalystDsl.Slots

        val knobs: List<Pair<String, KatalystParam>> = listOf(
            "body.material" to s.body.material, "body.wet" to s.body.wet, "body.floor" to s.body.floor,
            "vowel.vowel" to s.vowel.vowel, "vowel.wet" to s.vowel.wet, "vowel.floor" to s.vowel.floor,
            "delay.wet" to s.delay.wet, "delay.time" to s.delay.time, "delay.feedback" to s.delay.feedback,
            "delay.cap" to s.delay.cap,
            "reverb.wet" to s.reverb.wet, "reverb.size" to s.reverb.size, "reverb.lowpass" to s.reverb.lowpass,
            "phaser.rate" to s.phaser.rate, "phaser.wet" to s.phaser.wet, "phaser.center" to s.phaser.center,
            "phaser.sweep" to s.phaser.sweep, "phaser.floor" to s.phaser.floor,
            "compressor.threshold" to s.compressor.threshold, "compressor.ratio" to s.compressor.ratio,
            "compressor.knee" to s.compressor.knee, "compressor.attack" to s.compressor.attack,
            "compressor.release" to s.compressor.release,
            "gain.gain" to s.gain.gain,
            "duck.orbit" to s.duck.orbit, "duck.depth" to s.duck.depth, "duck.attack" to s.duck.attack,
        )

        knobs.size shouldBe 27

        knobs.forEach { (path, kotlin) ->
            withClue(path) {
                // The path IS the slot name: one word end to end, `Katalyst.slot.reverb.wet` writes `reverb.wet`.
                kotlin.name shouldBe path
                native("Katalyst.slot.$path") shouldBeSameInstanceAs kotlin
                native("Kat.slot.$path") shouldBeSameInstanceAs kotlin
            }
        }
    }
})
