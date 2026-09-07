/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

// sprudel/src/commonTest/kotlin/lang/LangDynamicsSpec.kt
package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangDynamicsSpec : StringSpec({

    // ---- gain() -----------------------------------------------------------------------------------------

    "gain dsl interface" {
        dslInterfaceTests(
            "pattern.gain(amount)" to note("a").gain(0.5),
            "script pattern.gain(amount)" to SprudelPattern.compile("""note("a").gain(0.5)"""),
            "string.gain(amount)" to "a".gain(0.5),
            "script string.gain(amount)" to SprudelPattern.compile(""""a".gain(0.5)"""),
            "gain(amount) via apply" to note("a").apply(gain(0.5)),
            "script gain(amount) via apply" to SprudelPattern.compile("""note("a").apply(gain(0.5))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "apply(gain().gain()) chains two gain mappers" {
        // Second gain(0.8) overrides the first gain(0.5)
        val p = note("a b").apply(gain(0.5).gain(0.8))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.gain shouldBe 0.8
        events[1].data.gain shouldBe 0.8
    }

    "apply(gain().pan()) chains gain and pan mappers" {
        val p = note("a").apply(gain(0.5).pan(0.25))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.gain shouldBe 0.5
        events[0].data.pan shouldBe 0.25
    }

    "script apply(gain()) works in compiled code" {
        val p = SprudelPattern.compile("""note("a").apply(gain(0.5))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.gain shouldBe 0.5
    }

    // ---- pan() ------------------------------------------------------------------------------------------

    "pan dsl interface" {
        dslInterfaceTests(
            "pattern.pan(amount)" to note("a").pan(0.5),
            "script pattern.pan(amount)" to SprudelPattern.compile("""note("a").pan(0.5)"""),
            "string.pan(amount)" to "a".pan(0.5),
            "script string.pan(amount)" to SprudelPattern.compile(""""a".pan(0.5)"""),
            "pan(amount) via apply" to note("a").apply(pan(0.5)),
            "script pan(amount) via apply" to SprudelPattern.compile("""note("a").apply(pan(0.5))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "script apply(pan()) works in compiled code" {
        val p = SprudelPattern.compile("""note("a").apply(pan(0.75))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.pan shouldBe 0.75
    }

    // ---- velocity() / vel() -----------------------------------------------------------------------------

    "velocity dsl interface" {
        dslInterfaceTests(
            "pattern.velocity(amount)" to note("a").velocity(0.8),
            "script pattern.velocity(amount)" to SprudelPattern.compile("""note("a").velocity(0.8)"""),
            "string.velocity(amount)" to "a".velocity(0.8),
            "script string.velocity(amount)" to SprudelPattern.compile(""""a".velocity(0.8)"""),
            "velocity(amount) via apply" to note("a").apply(velocity(0.8)),
            "script velocity(amount) via apply" to SprudelPattern.compile("""note("a").apply(velocity(0.8))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "vel dsl interface" {
        dslInterfaceTests(
            "pattern.vel(amount)" to note("a").vel(0.8),
            "script pattern.vel(amount)" to SprudelPattern.compile("""note("a").vel(0.8)"""),
            "string.vel(amount)" to "a".vel(0.8),
            "script string.vel(amount)" to SprudelPattern.compile(""""a".vel(0.8)"""),
            "vel(amount) via apply" to note("a").apply(vel(0.8)),
            "script vel(amount) via apply" to SprudelPattern.compile("""note("a").apply(vel(0.8))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "apply(velocity().gain()) chains velocity and gain mappers" {
        val p = note("a").apply(velocity(0.6).gain(0.8))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.velocity shouldBe 0.6
        events[0].data.gain shouldBe 0.8
    }

    "script apply(velocity()) works in compiled code" {
        val p = SprudelPattern.compile("""note("a").apply(velocity(0.6))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.velocity shouldBe 0.6
    }

    // ---- postgain() -------------------------------------------------------------------------------------

    "postgain dsl interface" {
        dslInterfaceTests(
            "pattern.postgain(amount)" to note("a").postgain(1.5),
            "script pattern.postgain(amount)" to SprudelPattern.compile("""note("a").postgain(1.5)"""),
            "string.postgain(amount)" to "a".postgain(1.5),
            "script string.postgain(amount)" to SprudelPattern.compile(""""a".postgain(1.5)"""),
            "postgain(amount) via apply" to note("a").apply(postgain(1.5)),
            "script postgain(amount) via apply" to SprudelPattern.compile("""note("a").apply(postgain(1.5))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "script apply(postgain()) works in compiled code" {
        val p = SprudelPattern.compile("""note("a").apply(postgain(1.5))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.postGain shouldBe 1.5
    }

    // ---- compressor() / comp() --------------------------------------------------------------------------

    "compressor dsl interface" {
        dslInterfaceTests(
            "pattern.compressor(params)" to note("a").compressor(-20, 4, 3, 0.03, 0.1),
            "script pattern.compressor(params)" to
                    SprudelPattern.compile("""note("a").compressor(-20, 4, 3, 0.03, 0.1)"""),
            "string.compressor(params)" to "a".compressor(-20, 4, 3, 0.03, 0.1),
            "script string.compressor(params)" to
                    SprudelPattern.compile(""""a".compressor(-20, 4, 3, 0.03, 0.1)"""),
            "compressor(params) via apply" to note("a").apply(compressor(-20, 4, 3, 0.03, 0.1)),
            "script compressor(params) via apply" to
                    SprudelPattern.compile("""note("a").apply(compressor(-20, 4, 3, 0.03, 0.1))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "comp dsl interface" {
        dslInterfaceTests(
            "pattern.comp(params)" to note("a").comp(-20, 4),
            "script pattern.comp(params)" to SprudelPattern.compile("""note("a").comp(-20, 4)"""),
            "string.comp(params)" to "a".comp(-20, 4),
            "script string.comp(params)" to SprudelPattern.compile(""""a".comp(-20, 4)"""),
            "comp(params) via apply" to note("a").apply(comp(-20, 4)),
            "script comp(params) via apply" to SprudelPattern.compile("""note("a").apply(comp(-20, 4))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "script apply(compressor()) works in compiled code" {
        val p = SprudelPattern.compile("""note("a").apply(compressor(-20, 4, 3, 0.03, 0.1))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.compressorThreshold shouldBe -20.0
        events[0].data.compressorRatio shouldBe 4.0
        events[0].data.compressorKnee shouldBe 3.0
        events[0].data.compressorAttack shouldBe 0.03
        events[0].data.compressorRelease shouldBe 0.1
    }

    // ---- unison() / uni() -------------------------------------------------------------------------------

    "unison dsl interface" {
        dslInterfaceTests(
            "pattern.unison(voices)" to note("a").unison(5),
            "script pattern.unison(voices)" to SprudelPattern.compile("""note("a").unison(5)"""),
            "string.unison(voices)" to "a".unison(5),
            "script string.unison(voices)" to SprudelPattern.compile(""""a".unison(5)"""),
            "unison(voices) via apply" to note("a").apply(unison(5)),
            "script unison(voices) via apply" to SprudelPattern.compile("""note("a").apply(unison(5))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "uni dsl interface" {
        dslInterfaceTests(
            "pattern.uni(voices)" to note("a").uni(5),
            "script pattern.uni(voices)" to SprudelPattern.compile("""note("a").uni(5)"""),
            "string.uni(voices)" to "a".uni(5),
            "script string.uni(voices)" to SprudelPattern.compile(""""a".uni(5)"""),
            "uni(voices) via apply" to note("a").apply(uni(5)),
            "script uni(voices) via apply" to SprudelPattern.compile("""note("a").apply(uni(5))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "apply(unison().unison(spread = ...)) chains the voices and spread slots" {
        val p = note("a").apply(unison(5).unison(spread = 0.3))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.oscParams?.get("voices") shouldBe 5.0
        events[0].data.oscParams?.get("spread") shouldBe 0.3
    }

    "script apply(unison()) works in compiled code" {
        val p = SprudelPattern.compile("""note("a").apply(unison(5))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.oscParams?.get("voices") shouldBe 5.0
    }

    // ---- detune() ---------------------------------------------------------------------------------------

    "unison(spread = ...) dsl interface" {
        dslInterfaceTests(
            "pattern.unison(spread = amount)" to note("a").unison(spread = 0.3),
            "script pattern.unison(spread = amount)" to SprudelPattern.compile("""note("a").unison(spread = 0.3)"""),
            "string.unison(spread = amount)" to "a".unison(spread = 0.3),
            "script string.unison(spread = amount)" to SprudelPattern.compile(""""a".unison(spread = 0.3)"""),
            "unison(spread = amount) via apply" to note("a").apply(unison(spread = 0.3)),
            "script unison(spread = amount) via apply" to SprudelPattern.compile("""note("a").apply(unison(spread = 0.3))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "script apply(unison(spread = ...)) works in compiled code" {
        val p = SprudelPattern.compile("""note("a").apply(unison(spread = 0.3))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.oscParams?.get("spread") shouldBe 0.3
    }

    // ---- unison(spread = ...) ---------------------------------------------------------------------------------------

    "unison(spread = ...) dsl interface, the second value set" {
        dslInterfaceTests(
            "pattern.unison(pan = amount)" to note("a").unison(pan = 0.8),
            "script pattern.unison(pan = amount)" to SprudelPattern.compile("""note("a").unison(pan = 0.8)"""),
            "string.unison(pan = amount)" to "a".unison(pan = 0.8),
            "script string.unison(pan = amount)" to SprudelPattern.compile(""""a".unison(pan = 0.8)"""),
            "unison(pan = amount) via apply" to note("a").apply(unison(pan = 0.8)),
            "script unison(spread = amount) via apply" to SprudelPattern.compile("""note("a").apply(unison(pan = 0.8))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "apply(unison().unison(spread = ..., pan = ...)) chains three mappers" {
        val p = note("a").apply(unison(5).unison(spread = 0.3, pan = 0.8))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.oscParams?.get("voices") shouldBe 5.0
        events[0].data.oscParams?.get("spread") shouldBe 0.3
        events[0].data.oscParams?.get("panSpread") shouldBe 0.8
    }

    "script apply(unison(pan = ...)) works in compiled code" {
        val p = SprudelPattern.compile("""note("a").apply(unison(pan = 0.8))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.oscParams?.get("panSpread") shouldBe 0.8
    }

    // ---- density() / d() --------------------------------------------------------------------------------

    "density dsl interface" {
        dslInterfaceTests(
            "pattern.density(amount)" to note("a").density(40),
            "script pattern.density(amount)" to SprudelPattern.compile("""note("a").density(40)"""),
            "string.density(amount)" to "a".density(40),
            "script string.density(amount)" to SprudelPattern.compile(""""a".density(40)"""),
            "density(amount) via apply" to note("a").apply(density(40)),
            "script density(amount) via apply" to SprudelPattern.compile("""note("a").apply(density(40))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "d dsl interface" {
        dslInterfaceTests(
            "pattern.d(amount)" to note("a").d(40),
            "script pattern.d(amount)" to SprudelPattern.compile("""note("a").d(40)"""),
            "string.d(amount)" to "a".d(40),
            "script string.d(amount)" to SprudelPattern.compile(""""a".d(40)"""),
            "d(amount) via apply" to note("a").apply(d(40)),
            "script d(amount) via apply" to SprudelPattern.compile("""note("a").apply(d(40))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "script apply(density()) works in compiled code" {
        val p = SprudelPattern.compile("""note("a").apply(density(40))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.oscParams?.get("density") shouldBe 40.0
    }

    // ---- adsr(attack = ...) ---------------------------------------------------------------------------------------

    "attack dsl interface" {
        dslInterfaceTests(
            "pattern.adsr(attack = time)" to note("a").adsr(attack = 0.01),
            "script pattern.adsr(attack = time)" to SprudelPattern.compile("""note("a").adsr(attack = 0.01)"""),
            "string.adsr(attack = time)" to "a".adsr(attack = 0.01),
            "script string.adsr(attack = time)" to SprudelPattern.compile(""""a".adsr(attack = 0.01)"""),
            "adsr(attack = time) via apply" to note("a").apply(adsr(attack = 0.01)),
            "script adsr(attack = time) via apply" to SprudelPattern.compile("""note("a").apply(adsr(attack = 0.01))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "apply(adsr(attack = ...).adsr(decay = ..., sustain = ..., release = ...)) chains all ADSR mappers" {
        val p = note("a").apply(adsr(attack = 0.01).adsr(decay = 0.2, sustain = 0.7, release = 0.5))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.attack shouldBe 0.01
        events[0].data.decay shouldBe 0.2
        events[0].data.sustain shouldBe 0.7
        events[0].data.release shouldBe 0.5
    }

    "script apply(adsr(attack = ...)) works in compiled code" {
        val p = SprudelPattern.compile("""note("a").apply(adsr(attack = 0.01))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.attack shouldBe 0.01
    }

    // ---- adsr(decay = ...) ----------------------------------------------------------------------------------------

    "decay dsl interface" {
        dslInterfaceTests(
            "pattern.adsr(decay = time)" to note("a").adsr(decay = 0.2),
            "script pattern.adsr(decay = time)" to SprudelPattern.compile("""note("a").adsr(decay = 0.2)"""),
            "string.adsr(decay = time)" to "a".adsr(decay = 0.2),
            "script string.adsr(decay = time)" to SprudelPattern.compile(""""a".adsr(decay = 0.2)"""),
            "adsr(decay = time) via apply" to note("a").apply(adsr(decay = 0.2)),
            "script adsr(decay = time) via apply" to SprudelPattern.compile("""note("a").apply(adsr(decay = 0.2))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "script apply(adsr(decay = ...)) works in compiled code" {
        val p = SprudelPattern.compile("""note("a").apply(adsr(decay = 0.2))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.decay shouldBe 0.2
    }

    // ---- adsr(sustain = ...) --------------------------------------------------------------------------------------

    "sustain dsl interface" {
        dslInterfaceTests(
            "pattern.adsr(sustain = level)" to note("a").adsr(sustain = 0.7),
            "script pattern.adsr(sustain = level)" to SprudelPattern.compile("""note("a").adsr(sustain = 0.7)"""),
            "string.adsr(sustain = level)" to "a".adsr(sustain = 0.7),
            "script string.adsr(sustain = level)" to SprudelPattern.compile(""""a".adsr(sustain = 0.7)"""),
            "adsr(sustain = level) via apply" to note("a").apply(adsr(sustain = 0.7)),
            "script adsr(sustain = level) via apply" to SprudelPattern.compile("""note("a").apply(adsr(sustain = 0.7))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "script apply(adsr(sustain = ...)) works in compiled code" {
        val p = SprudelPattern.compile("""note("a").apply(adsr(sustain = 0.7))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.sustain shouldBe 0.7
    }

    // ---- adsr(release = ...) --------------------------------------------------------------------------------------

    "release dsl interface" {
        dslInterfaceTests(
            "pattern.adsr(release = time)" to note("a").adsr(release = 0.5),
            "script pattern.adsr(release = time)" to SprudelPattern.compile("""note("a").adsr(release = 0.5)"""),
            "string.adsr(release = time)" to "a".adsr(release = 0.5),
            "script string.adsr(release = time)" to SprudelPattern.compile(""""a".adsr(release = 0.5)"""),
            "adsr(release = time) via apply" to note("a").apply(adsr(release = 0.5)),
            "script adsr(release = time) via apply" to SprudelPattern.compile("""note("a").apply(adsr(release = 0.5))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "script apply(adsr(release = ...)) works in compiled code" {
        val p = SprudelPattern.compile("""note("a").apply(adsr(release = 0.5))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.release shouldBe 0.5
    }

    // ---- adsr() -----------------------------------------------------------------------------------------

    "adsr dsl interface" {
        dslInterfaceTests(
            "pattern.adsr(params)" to note("a").adsr(0.01, 0.2, 0.7, 0.5),
            "script pattern.adsr(params)" to SprudelPattern.compile("""note("a").adsr(0.01, 0.2, 0.7, 0.5)"""),
            "string.adsr(params)" to "a".adsr(0.01, 0.2, 0.7, 0.5),
            "script string.adsr(params)" to SprudelPattern.compile(""""a".adsr(0.01, 0.2, 0.7, 0.5)"""),
            "adsr(params) via apply" to note("a").apply(adsr(0.01, 0.2, 0.7, 0.5)),
            "script adsr(params) via apply" to
                    SprudelPattern.compile("""note("a").apply(adsr(0.01, 0.2, 0.7, 0.5))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "apply(gain().adsr()) chains gain and adsr mappers" {
        val p = note("a").apply(gain(0.8).adsr(0.01, 0.2, 0.7, 0.5))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.gain shouldBe 0.8
        events[0].data.attack shouldBe 0.01
        events[0].data.decay shouldBe 0.2
        events[0].data.sustain shouldBe 0.7
        events[0].data.release shouldBe 0.5
    }

    "script apply(adsr()) works in compiled code" {
        val p = SprudelPattern.compile("""note("a").apply(adsr(0.01, 0.2, 0.7, 0.5))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.attack shouldBe 0.01
        events[0].data.decay shouldBe 0.2
        events[0].data.sustain shouldBe 0.7
        events[0].data.release shouldBe 0.5
    }

    // ---- orbit() / o() ----------------------------------------------------------------------------------

    "orbit dsl interface" {
        dslInterfaceTests(
            "pattern.orbit(index)" to note("a").orbit(2),
            "script pattern.orbit(index)" to SprudelPattern.compile("""note("a").orbit(2)"""),
            "string.orbit(index)" to "a".orbit(2),
            "script string.orbit(index)" to SprudelPattern.compile(""""a".orbit(2)"""),
            "orbit(index) via apply" to note("a").apply(orbit(2)),
            "script orbit(index) via apply" to SprudelPattern.compile("""note("a").apply(orbit(2))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "o dsl interface" {
        dslInterfaceTests(
            "pattern.o(index)" to note("a").o(2),
            "script pattern.o(index)" to SprudelPattern.compile("""note("a").o(2)"""),
            "string.o(index)" to "a".o(2),
            "script string.o(index)" to SprudelPattern.compile(""""a".o(2)"""),
            "o(index) via apply" to note("a").apply(o(2)),
            "script o(index) via apply" to SprudelPattern.compile("""note("a").apply(o(2))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "apply(gain().orbit()) chains gain and orbit mappers" {
        val p = note("a").apply(gain(0.8).orbit(2))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.gain shouldBe 0.8
        events[0].data.cylinder shouldBe 2
    }

    "script apply(orbit()) works in compiled code" {
        val p = SprudelPattern.compile("""note("a").apply(orbit(2))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.cylinder shouldBe 2
    }

    // ---- duck() ---------------------------------------------------------------------------

    "duck() dsl interface" {
        dslInterfaceTests(
            "pattern.duck(index)" to note("a").duck(1),
            "script pattern.duck(index)" to SprudelPattern.compile("""note("a").duck(1)"""),
            "string.duck(index)" to "a".duck(1),
            "script string.duck(index)" to SprudelPattern.compile(""""a".duck(1)"""),
            "duck(index) via apply" to note("a").apply(duck(1)),
            "script duck(index) via apply" to SprudelPattern.compile("""note("a").apply(duck(1))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "apply(duck().duck(depth = ...)) chains the orbit and depth slots" {
        val p = note("a").apply(duck(1).duck(depth = 0.8))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.duckCylinder shouldBe 1
        events[0].data.duckDepth shouldBe 0.8
    }

    "script apply(duck()) works in compiled code" {
        val p = SprudelPattern.compile("""note("a").apply(duck(1))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.duckCylinder shouldBe 1
    }

    // ---- duck(attack = ...) -----------------------------------------------------------------------

    "duck(attack = ...) dsl interface" {
        dslInterfaceTests(
            "pattern.duck(attack = time)" to note("a").duck(attack = 0.2),
            "script pattern.duck(attack = time)" to SprudelPattern.compile("""note("a").duck(attack = 0.2)"""),
            "string.duck(attack = time)" to "a".duck(attack = 0.2),
            "script string.duck(attack = time)" to SprudelPattern.compile(""""a".duck(attack = 0.2)"""),
            "duck(attack = time) via apply" to note("a").apply(duck(attack = 0.2)),
            "script duck(attack = time) via apply" to
                    SprudelPattern.compile("""note("a").apply(duck(attack = 0.2))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "apply(duck().duck(attack = ..., depth = ...)) chains the attack and depth slots" {
        val p = note("a").apply(duck(1).duck(attack = 0.2, depth = 0.8))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.duckCylinder shouldBe 1
        events[0].data.duckAttack shouldBe 0.2
        events[0].data.duckDepth shouldBe 0.8
    }

    "script apply(duck(attack = ...)) works in compiled code" {
        val p = SprudelPattern.compile("""note("a").apply(duck(attack = 0.2))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.duckAttack shouldBe 0.2
    }

    // ---- duck(depth = ...) ------------------------------------------------------------------------------------

    "duck(depth = ...) dsl interface" {
        dslInterfaceTests(
            "pattern.duck(depth = amount)" to note("a").duck(depth = 0.8),
            "script pattern.duck(depth = amount)" to SprudelPattern.compile("""note("a").duck(depth = 0.8)"""),
            "string.duck(depth = amount)" to "a".duck(depth = 0.8),
            "script string.duck(depth = amount)" to SprudelPattern.compile(""""a".duck(depth = 0.8)"""),
            "duck(depth = amount) via apply" to note("a").apply(duck(depth = 0.8)),
            "script duck(depth = amount) via apply" to SprudelPattern.compile("""note("a").apply(duck(depth = 0.8))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "script apply(duck(depth = ...)) works in compiled code" {
        val p = SprudelPattern.compile("""note("a").apply(duck(depth = 0.8))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.duckDepth shouldBe 0.8
    }
})
