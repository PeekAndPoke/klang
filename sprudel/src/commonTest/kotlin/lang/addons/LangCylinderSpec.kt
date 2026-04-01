package io.peekandpoke.klang.sprudel.lang.addons

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.dslInterfaceTests
import io.peekandpoke.klang.sprudel.lang.note
import io.peekandpoke.klang.sprudel.lang.orbit

class LangCylinderSpec : StringSpec({

    "cylinder dsl interface" {
        dslInterfaceTests(
            "pattern.cylinder(index)" to note("a").cylinder(2),
            "string.cylinder(index)" to "a".cylinder(2),
        ) { _, events ->
            events.size shouldBe 1
            events[0].data.cylinder shouldBe 2
        }
    }

    "cylinder() sets same orbit as orbit()" {
        val withCylinder = note("a b c").cylinder("0 1 2")
        val withOrbit = note("a b c").orbit("0 1 2")

        val cylinderEvents = withCylinder.queryArc(0.0, 12.0)
        val orbitEvents = withOrbit.queryArc(0.0, 12.0)

        cylinderEvents.size shouldBe orbitEvents.size

        for (i in cylinderEvents.indices) {
            cylinderEvents[i].data.cylinder shouldBe orbitEvents[i].data.cylinder
        }
    }

    "cylinder() as PatternMapperFn works same as orbit()" {
        val withCylinder = note("a b").cylinder(1)
        val withOrbit = note("a b").orbit(1)

        val cylinderEvents = withCylinder.queryArc(0.0, 12.0)
        val orbitEvents = withOrbit.queryArc(0.0, 12.0)

        cylinderEvents.size shouldBe orbitEvents.size
        cylinderEvents[0].data.cylinder shouldBe 1
        orbitEvents[0].data.cylinder shouldBe 1
    }
})
