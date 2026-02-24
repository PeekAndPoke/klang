// strudel/src/commonTest/kotlin/lang/LangDynamicsSpec.kt
package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangDynamicsSpec : StringSpec({

    // ---- gain() -----------------------------------------------------------------------------------------

    "gain dsl interface" {
        dslInterfaceTests(
            "pattern.gain(amount)" to note("a").gain(0.5),
            "script pattern.gain(amount)" to StrudelPattern.compile("""note("a").gain(0.5)"""),
            "string.gain(amount)" to "a".gain(0.5),
            "script string.gain(amount)" to StrudelPattern.compile(""""a".gain(0.5)"""),
            "gain(amount) via apply" to note("a").apply(gain(0.5)),
            "script gain(amount) via apply" to StrudelPattern.compile("""note("a").apply(gain(0.5))"""),
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
        val p = StrudelPattern.compile("""note("a").apply(gain(0.5))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.gain shouldBe 0.5
    }

    // ---- pan() ------------------------------------------------------------------------------------------

    "pan dsl interface" {
        dslInterfaceTests(
            "pattern.pan(amount)" to note("a").pan(0.5),
            "script pattern.pan(amount)" to StrudelPattern.compile("""note("a").pan(0.5)"""),
            "string.pan(amount)" to "a".pan(0.5),
            "script string.pan(amount)" to StrudelPattern.compile(""""a".pan(0.5)"""),
            "pan(amount) via apply" to note("a").apply(pan(0.5)),
            "script pan(amount) via apply" to StrudelPattern.compile("""note("a").apply(pan(0.5))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "script apply(pan()) works in compiled code" {
        val p = StrudelPattern.compile("""note("a").apply(pan(0.75))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.pan shouldBe 0.75
    }

    // ---- velocity() / vel() -----------------------------------------------------------------------------

    "velocity dsl interface" {
        dslInterfaceTests(
            "pattern.velocity(amount)" to note("a").velocity(0.8),
            "script pattern.velocity(amount)" to StrudelPattern.compile("""note("a").velocity(0.8)"""),
            "string.velocity(amount)" to "a".velocity(0.8),
            "script string.velocity(amount)" to StrudelPattern.compile(""""a".velocity(0.8)"""),
            "velocity(amount) via apply" to note("a").apply(velocity(0.8)),
            "script velocity(amount) via apply" to StrudelPattern.compile("""note("a").apply(velocity(0.8))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "vel dsl interface" {
        dslInterfaceTests(
            "pattern.vel(amount)" to note("a").vel(0.8),
            "script pattern.vel(amount)" to StrudelPattern.compile("""note("a").vel(0.8)"""),
            "string.vel(amount)" to "a".vel(0.8),
            "script string.vel(amount)" to StrudelPattern.compile(""""a".vel(0.8)"""),
            "vel(amount) via apply" to note("a").apply(vel(0.8)),
            "script vel(amount) via apply" to StrudelPattern.compile("""note("a").apply(vel(0.8))"""),
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
        val p = StrudelPattern.compile("""note("a").apply(velocity(0.6))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.velocity shouldBe 0.6
    }

    // ---- postgain() -------------------------------------------------------------------------------------

    "postgain dsl interface" {
        dslInterfaceTests(
            "pattern.postgain(amount)" to note("a").postgain(1.5),
            "script pattern.postgain(amount)" to StrudelPattern.compile("""note("a").postgain(1.5)"""),
            "string.postgain(amount)" to "a".postgain(1.5),
            "script string.postgain(amount)" to StrudelPattern.compile(""""a".postgain(1.5)"""),
            "postgain(amount) via apply" to note("a").apply(postgain(1.5)),
            "script postgain(amount) via apply" to StrudelPattern.compile("""note("a").apply(postgain(1.5))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "script apply(postgain()) works in compiled code" {
        val p = StrudelPattern.compile("""note("a").apply(postgain(1.5))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.postGain shouldBe 1.5
    }

    // ---- compressor() / comp() --------------------------------------------------------------------------

    "compressor dsl interface" {
        dslInterfaceTests(
            "pattern.compressor(params)" to note("a").compressor("-20:4:3:0.03:0.1"),
            "script pattern.compressor(params)" to
                    StrudelPattern.compile("""note("a").compressor("-20:4:3:0.03:0.1")"""),
            "string.compressor(params)" to "a".compressor("-20:4:3:0.03:0.1"),
            "script string.compressor(params)" to
                    StrudelPattern.compile(""""a".compressor("-20:4:3:0.03:0.1")"""),
            "compressor(params) via apply" to note("a").apply(compressor("-20:4:3:0.03:0.1")),
            "script compressor(params) via apply" to
                    StrudelPattern.compile("""note("a").apply(compressor("-20:4:3:0.03:0.1"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "comp dsl interface" {
        dslInterfaceTests(
            "pattern.comp(params)" to note("a").comp("-20:4"),
            "script pattern.comp(params)" to StrudelPattern.compile("""note("a").comp("-20:4")"""),
            "string.comp(params)" to "a".comp("-20:4"),
            "script string.comp(params)" to StrudelPattern.compile(""""a".comp("-20:4")"""),
            "comp(params) via apply" to note("a").apply(comp("-20:4")),
            "script comp(params) via apply" to StrudelPattern.compile("""note("a").apply(comp("-20:4"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "script apply(compressor()) works in compiled code" {
        val p = StrudelPattern.compile("""note("a").apply(compressor("-20:4:3:0.03:0.1"))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.compressor shouldBe "-20:4:3:0.03:0.1"
    }

    // ---- unison() / uni() -------------------------------------------------------------------------------

    "unison dsl interface" {
        dslInterfaceTests(
            "pattern.unison(voices)" to note("a").unison(5),
            "script pattern.unison(voices)" to StrudelPattern.compile("""note("a").unison(5)"""),
            "string.unison(voices)" to "a".unison(5),
            "script string.unison(voices)" to StrudelPattern.compile(""""a".unison(5)"""),
            "unison(voices) via apply" to note("a").apply(unison(5)),
            "script unison(voices) via apply" to StrudelPattern.compile("""note("a").apply(unison(5))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "uni dsl interface" {
        dslInterfaceTests(
            "pattern.uni(voices)" to note("a").uni(5),
            "script pattern.uni(voices)" to StrudelPattern.compile("""note("a").uni(5)"""),
            "string.uni(voices)" to "a".uni(5),
            "script string.uni(voices)" to StrudelPattern.compile(""""a".uni(5)"""),
            "uni(voices) via apply" to note("a").apply(uni(5)),
            "script uni(voices) via apply" to StrudelPattern.compile("""note("a").apply(uni(5))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "apply(unison().detune()) chains unison and detune mappers" {
        val p = note("a").apply(unison(5).detune(0.3))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.voices shouldBe 5.0
        events[0].data.freqSpread shouldBe 0.3
    }

    "script apply(unison()) works in compiled code" {
        val p = StrudelPattern.compile("""note("a").apply(unison(5))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.voices shouldBe 5.0
    }

    // ---- detune() ---------------------------------------------------------------------------------------

    "detune dsl interface" {
        dslInterfaceTests(
            "pattern.detune(amount)" to note("a").detune(0.3),
            "script pattern.detune(amount)" to StrudelPattern.compile("""note("a").detune(0.3)"""),
            "string.detune(amount)" to "a".detune(0.3),
            "script string.detune(amount)" to StrudelPattern.compile(""""a".detune(0.3)"""),
            "detune(amount) via apply" to note("a").apply(detune(0.3)),
            "script detune(amount) via apply" to StrudelPattern.compile("""note("a").apply(detune(0.3))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "script apply(detune()) works in compiled code" {
        val p = StrudelPattern.compile("""note("a").apply(detune(0.3))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.freqSpread shouldBe 0.3
    }

    // ---- spread() ---------------------------------------------------------------------------------------

    "spread dsl interface" {
        dslInterfaceTests(
            "pattern.spread(amount)" to note("a").spread(0.8),
            "script pattern.spread(amount)" to StrudelPattern.compile("""note("a").spread(0.8)"""),
            "string.spread(amount)" to "a".spread(0.8),
            "script string.spread(amount)" to StrudelPattern.compile(""""a".spread(0.8)"""),
            "spread(amount) via apply" to note("a").apply(spread(0.8)),
            "script spread(amount) via apply" to StrudelPattern.compile("""note("a").apply(spread(0.8))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "apply(unison().detune().spread()) chains three mappers" {
        val p = note("a").apply(unison(5).detune(0.3).spread(0.8))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.voices shouldBe 5.0
        events[0].data.freqSpread shouldBe 0.3
        events[0].data.panSpread shouldBe 0.8
    }

    "script apply(spread()) works in compiled code" {
        val p = StrudelPattern.compile("""note("a").apply(spread(0.8))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.panSpread shouldBe 0.8
    }

    // ---- density() / d() --------------------------------------------------------------------------------

    "density dsl interface" {
        dslInterfaceTests(
            "pattern.density(amount)" to note("a").density(40),
            "script pattern.density(amount)" to StrudelPattern.compile("""note("a").density(40)"""),
            "string.density(amount)" to "a".density(40),
            "script string.density(amount)" to StrudelPattern.compile(""""a".density(40)"""),
            "density(amount) via apply" to note("a").apply(density(40)),
            "script density(amount) via apply" to StrudelPattern.compile("""note("a").apply(density(40))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "d dsl interface" {
        dslInterfaceTests(
            "pattern.d(amount)" to note("a").d(40),
            "script pattern.d(amount)" to StrudelPattern.compile("""note("a").d(40)"""),
            "string.d(amount)" to "a".d(40),
            "script string.d(amount)" to StrudelPattern.compile(""""a".d(40)"""),
            "d(amount) via apply" to note("a").apply(d(40)),
            "script d(amount) via apply" to StrudelPattern.compile("""note("a").apply(d(40))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "script apply(density()) works in compiled code" {
        val p = StrudelPattern.compile("""note("a").apply(density(40))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.density shouldBe 40.0
    }

    // ---- attack() ---------------------------------------------------------------------------------------

    "attack dsl interface" {
        dslInterfaceTests(
            "pattern.attack(time)" to note("a").attack(0.01),
            "script pattern.attack(time)" to StrudelPattern.compile("""note("a").attack(0.01)"""),
            "string.attack(time)" to "a".attack(0.01),
            "script string.attack(time)" to StrudelPattern.compile(""""a".attack(0.01)"""),
            "attack(time) via apply" to note("a").apply(attack(0.01)),
            "script attack(time) via apply" to StrudelPattern.compile("""note("a").apply(attack(0.01))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "apply(attack().decay().sustain().release()) chains all ADSR mappers" {
        val p = note("a").apply(attack(0.01).decay(0.2).sustain(0.7).release(0.5))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.attack shouldBe 0.01
        events[0].data.decay shouldBe 0.2
        events[0].data.sustain shouldBe 0.7
        events[0].data.release shouldBe 0.5
    }

    "script apply(attack()) works in compiled code" {
        val p = StrudelPattern.compile("""note("a").apply(attack(0.01))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.attack shouldBe 0.01
    }

    // ---- decay() ----------------------------------------------------------------------------------------

    "decay dsl interface" {
        dslInterfaceTests(
            "pattern.decay(time)" to note("a").decay(0.2),
            "script pattern.decay(time)" to StrudelPattern.compile("""note("a").decay(0.2)"""),
            "string.decay(time)" to "a".decay(0.2),
            "script string.decay(time)" to StrudelPattern.compile(""""a".decay(0.2)"""),
            "decay(time) via apply" to note("a").apply(decay(0.2)),
            "script decay(time) via apply" to StrudelPattern.compile("""note("a").apply(decay(0.2))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "script apply(decay()) works in compiled code" {
        val p = StrudelPattern.compile("""note("a").apply(decay(0.2))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.decay shouldBe 0.2
    }

    // ---- sustain() --------------------------------------------------------------------------------------

    "sustain dsl interface" {
        dslInterfaceTests(
            "pattern.sustain(level)" to note("a").sustain(0.7),
            "script pattern.sustain(level)" to StrudelPattern.compile("""note("a").sustain(0.7)"""),
            "string.sustain(level)" to "a".sustain(0.7),
            "script string.sustain(level)" to StrudelPattern.compile(""""a".sustain(0.7)"""),
            "sustain(level) via apply" to note("a").apply(sustain(0.7)),
            "script sustain(level) via apply" to StrudelPattern.compile("""note("a").apply(sustain(0.7))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "script apply(sustain()) works in compiled code" {
        val p = StrudelPattern.compile("""note("a").apply(sustain(0.7))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.sustain shouldBe 0.7
    }

    // ---- release() --------------------------------------------------------------------------------------

    "release dsl interface" {
        dslInterfaceTests(
            "pattern.release(time)" to note("a").release(0.5),
            "script pattern.release(time)" to StrudelPattern.compile("""note("a").release(0.5)"""),
            "string.release(time)" to "a".release(0.5),
            "script string.release(time)" to StrudelPattern.compile(""""a".release(0.5)"""),
            "release(time) via apply" to note("a").apply(release(0.5)),
            "script release(time) via apply" to StrudelPattern.compile("""note("a").apply(release(0.5))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "script apply(release()) works in compiled code" {
        val p = StrudelPattern.compile("""note("a").apply(release(0.5))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.release shouldBe 0.5
    }

    // ---- adsr() -----------------------------------------------------------------------------------------

    "adsr dsl interface" {
        dslInterfaceTests(
            "pattern.adsr(params)" to note("a").adsr("0.01:0.2:0.7:0.5"),
            "script pattern.adsr(params)" to StrudelPattern.compile("""note("a").adsr("0.01:0.2:0.7:0.5")"""),
            "string.adsr(params)" to "a".adsr("0.01:0.2:0.7:0.5"),
            "script string.adsr(params)" to StrudelPattern.compile(""""a".adsr("0.01:0.2:0.7:0.5")"""),
            "adsr(params) via apply" to note("a").apply(adsr("0.01:0.2:0.7:0.5")),
            "script adsr(params) via apply" to
                    StrudelPattern.compile("""note("a").apply(adsr("0.01:0.2:0.7:0.5"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "apply(gain().adsr()) chains gain and adsr mappers" {
        val p = note("a").apply(gain(0.8).adsr("0.01:0.2:0.7:0.5"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.gain shouldBe 0.8
        events[0].data.attack shouldBe 0.01
        events[0].data.decay shouldBe 0.2
        events[0].data.sustain shouldBe 0.7
        events[0].data.release shouldBe 0.5
    }

    "script apply(adsr()) works in compiled code" {
        val p = StrudelPattern.compile("""note("a").apply(adsr("0.01:0.2:0.7:0.5"))""")!!
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
            "script pattern.orbit(index)" to StrudelPattern.compile("""note("a").orbit(2)"""),
            "string.orbit(index)" to "a".orbit(2),
            "script string.orbit(index)" to StrudelPattern.compile(""""a".orbit(2)"""),
            "orbit(index) via apply" to note("a").apply(orbit(2)),
            "script orbit(index) via apply" to StrudelPattern.compile("""note("a").apply(orbit(2))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "o dsl interface" {
        dslInterfaceTests(
            "pattern.o(index)" to note("a").o(2),
            "script pattern.o(index)" to StrudelPattern.compile("""note("a").o(2)"""),
            "string.o(index)" to "a".o(2),
            "script string.o(index)" to StrudelPattern.compile(""""a".o(2)"""),
            "o(index) via apply" to note("a").apply(o(2)),
            "script o(index) via apply" to StrudelPattern.compile("""note("a").apply(o(2))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "apply(gain().orbit()) chains gain and orbit mappers" {
        val p = note("a").apply(gain(0.8).orbit(2))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.gain shouldBe 0.8
        events[0].data.orbit shouldBe 2
    }

    "script apply(orbit()) works in compiled code" {
        val p = StrudelPattern.compile("""note("a").apply(orbit(2))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.orbit shouldBe 2
    }

    // ---- duckorbit() / duck() ---------------------------------------------------------------------------

    "duckorbit dsl interface" {
        dslInterfaceTests(
            "pattern.duckorbit(index)" to note("a").duckorbit(1),
            "script pattern.duckorbit(index)" to StrudelPattern.compile("""note("a").duckorbit(1)"""),
            "string.duckorbit(index)" to "a".duckorbit(1),
            "script string.duckorbit(index)" to StrudelPattern.compile(""""a".duckorbit(1)"""),
            "duckorbit(index) via apply" to note("a").apply(duckorbit(1)),
            "script duckorbit(index) via apply" to StrudelPattern.compile("""note("a").apply(duckorbit(1))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "duck dsl interface" {
        dslInterfaceTests(
            "pattern.duck(index)" to note("a").duck(1),
            "script pattern.duck(index)" to StrudelPattern.compile("""note("a").duck(1)"""),
            "string.duck(index)" to "a".duck(1),
            "script string.duck(index)" to StrudelPattern.compile(""""a".duck(1)"""),
            "duck(index) via apply" to note("a").apply(duck(1)),
            "script duck(index) via apply" to StrudelPattern.compile("""note("a").apply(duck(1))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "apply(duck().duckdepth()) chains duck and duckdepth mappers" {
        val p = note("a").apply(duck(1).duckdepth(0.8))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.duckOrbit shouldBe 1
        events[0].data.duckDepth shouldBe 0.8
    }

    "script apply(duck()) works in compiled code" {
        val p = StrudelPattern.compile("""note("a").apply(duck(1))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.duckOrbit shouldBe 1
    }

    // ---- duckattack() / duckatt() -----------------------------------------------------------------------

    "duckattack dsl interface" {
        dslInterfaceTests(
            "pattern.duckattack(time)" to note("a").duckattack(0.2),
            "script pattern.duckattack(time)" to StrudelPattern.compile("""note("a").duckattack(0.2)"""),
            "string.duckattack(time)" to "a".duckattack(0.2),
            "script string.duckattack(time)" to StrudelPattern.compile(""""a".duckattack(0.2)"""),
            "duckattack(time) via apply" to note("a").apply(duckattack(0.2)),
            "script duckattack(time) via apply" to
                    StrudelPattern.compile("""note("a").apply(duckattack(0.2))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "duckatt dsl interface" {
        dslInterfaceTests(
            "pattern.duckatt(time)" to note("a").duckatt(0.2),
            "script pattern.duckatt(time)" to StrudelPattern.compile("""note("a").duckatt(0.2)"""),
            "string.duckatt(time)" to "a".duckatt(0.2),
            "script string.duckatt(time)" to StrudelPattern.compile(""""a".duckatt(0.2)"""),
            "duckatt(time) via apply" to note("a").apply(duckatt(0.2)),
            "script duckatt(time) via apply" to StrudelPattern.compile("""note("a").apply(duckatt(0.2))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "apply(duck().duckattack().duckdepth()) chains ducking mappers" {
        val p = note("a").apply(duck(1).duckattack(0.2).duckdepth(0.8))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.duckOrbit shouldBe 1
        events[0].data.duckAttack shouldBe 0.2
        events[0].data.duckDepth shouldBe 0.8
    }

    "script apply(duckattack()) works in compiled code" {
        val p = StrudelPattern.compile("""note("a").apply(duckattack(0.2))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.duckAttack shouldBe 0.2
    }

    // ---- duckdepth() ------------------------------------------------------------------------------------

    "duckdepth dsl interface" {
        dslInterfaceTests(
            "pattern.duckdepth(amount)" to note("a").duckdepth(0.8),
            "script pattern.duckdepth(amount)" to StrudelPattern.compile("""note("a").duckdepth(0.8)"""),
            "string.duckdepth(amount)" to "a".duckdepth(0.8),
            "script string.duckdepth(amount)" to StrudelPattern.compile(""""a".duckdepth(0.8)"""),
            "duckdepth(amount) via apply" to note("a").apply(duckdepth(0.8)),
            "script duckdepth(amount) via apply" to StrudelPattern.compile("""note("a").apply(duckdepth(0.8))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "script apply(duckdepth()) works in compiled code" {
        val p = StrudelPattern.compile("""note("a").apply(duckdepth(0.8))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.duckDepth shouldBe 0.8
    }
})
