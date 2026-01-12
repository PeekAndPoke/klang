package io.peekandpoke.klang.strudel.lang.parser

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.lang.note

class MiniNotationParserSpec : StringSpec() {

    fun parse(input: String) = MiniNotationParser(input) { note(it) }.parse()

    init {
        // ... existing code ...

        "Parsing sound with index 'bd:1'" {
            val pattern = parse("bd:1")
            val events = pattern.queryArc(0.0, 1.0)

            events.size shouldBe 1
            with(events[0]) {
                data.note shouldBe "bd"
                data.soundIndex shouldBe 1
            }
        }

        "Parsing sound with index and gain 'bd:1:0.5'" {
            val pattern = parse("bd:1:0.5")
            val events = pattern.queryArc(0.0, 1.0)

            events.size shouldBe 1
            with(events[0]) {
                data.note shouldBe "bd"
                data.soundIndex shouldBe 1
                data.gain shouldBe 0.5
            }
        }

        "Parsing sound with index and gain and modifiers 'bd:1:0.5*2'" {
            val pattern = parse("bd:1:0.5*2")
            val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

            events.size shouldBe 2
            with(events[0]) {
                data.note shouldBe "bd"
                data.soundIndex shouldBe 1
                data.gain shouldBe 0.5
            }
            with(events[1]) {
                data.note shouldBe "bd"
                data.soundIndex shouldBe 1
                data.gain shouldBe 0.5
            }
        }

        "Parsing scale-like string 'C4:minor' (should not split)" {
            val pattern = parse("C4:minor")
            val events = pattern.queryArc(0.0, 1.0)

            events.size shouldBe 1
            with(events[0]) {
                data.note shouldBe "C4:minor"
                data.soundIndex shouldBe null
                data.gain shouldBe 1.0
            }
        }
    }
}
