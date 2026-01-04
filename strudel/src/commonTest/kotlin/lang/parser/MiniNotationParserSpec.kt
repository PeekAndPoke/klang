package io.peekandpoke.klang.strudel.lang.parser

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.lang.note

class MiniNotationParserSpec : StringSpec() {

    fun parse(input: String) = MiniNotationParser(input) { note(it) }.parse()

    init {
        "Parsing a single atom 'c3'" {
            val pattern = parse("c3")
            val events = pattern.queryArc(0.0, 1.0)

            events.size shouldBe 1
            with(events[0]) {
                begin shouldBe 0.0
                end shouldBe 1.0
                data.note shouldBe "c3"
            }
        }

        "Parsing a sequence 'c3 e3'" {
            val pattern = parse("c3 e3")
            val events = pattern.queryArc(0.0, 1.0)

            events.size shouldBe 2

            with(events[0]) {
                begin shouldBe 0.0
                end shouldBe 0.5
                data.note shouldBe "c3"
            }

            with(events[1]) {
                begin shouldBe 0.5
                end shouldBe 1.0
                data.note shouldBe "e3"
            }
        }

        "Parsing a sequence with rest 'c3 ~'" {
            val pattern = parse("c3 ~")
            val events = pattern.queryArc(0.0, 1.0)

            events.size shouldBe 1

            with(events[0]) {
                begin shouldBe 0.0
                end shouldBe 0.5
                data.note shouldBe "c3"
            }
        }

        "Parsing a stack 'c3, e3'" {
            val pattern = parse("c3, e3")
            val events = pattern.queryArc(0.0, 1.0).sortedBy { it.data.note }

            events.size shouldBe 2

            // Both should occupy the full cycle
            with(events[0]) {
                data.note shouldBe "c3"
                begin shouldBe 0.0
                end shouldBe 1.0
            }

            with(events[1]) {
                data.note shouldBe "e3"
                begin shouldBe 0.0
                end shouldBe 1.0
            }
        }

        "Parsing a nested group '[c3 e3] g3'" {
            // [c3 e3] takes first half (0..0.5)
            // g3 takes second half (0.5..1)
            // Inside [c3 e3]: c3 (0..0.25), e3 (0.25..0.5)

            val pattern = parse("[c3 e3] g3")
            val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

            events.size shouldBe 3

            with(events[0]) {
                data.note shouldBe "c3"
                begin shouldBe 0.0
                end shouldBe 0.25
            }
            with(events[1]) {
                data.note shouldBe "e3"
                begin shouldBe 0.25
                end shouldBe 0.5
            }
            with(events[2]) {
                data.note shouldBe "g3"
                begin shouldBe 0.5
                end shouldBe 1.0
            }
        }

        "Parsing speed modifiers 'c3*2'" {
            // c3*2 -> play c3 twice in one cycle
            val pattern = parse("c3*2")
            val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

            events.size shouldBe 2

            with(events[0]) {
                data.note shouldBe "c3"
                begin shouldBe 0.0
                end shouldBe 0.5
            }
            with(events[1]) {
                data.note shouldBe "c3"
                begin shouldBe 0.5
                end shouldBe 1.0
            }
        }

        "Parsing speed modifiers 'c3/2'" {
            // c3/2 -> c3 stretched to 2 cycles.
            val pattern = parse("c3/2")
            val events = pattern.queryArc(0.0, 1.0)

            events.size shouldBe 1
            with(events[0]) {
                data.note shouldBe "c3"
                begin shouldBe 0.0
                end shouldBe 2.0 // It's a 2-cycle event
            }
        }

        "Parsing alternation '<c3 e3>'" {
            // <c3 e3> -> c3 in cycle 0, e3 in cycle 1
            val pattern = parse("<c3 e3>")

            val events0 = pattern.queryArc(0.0, 1.0)
            events0.size shouldBe 1
            events0[0].data.note shouldBe "c3"

            val events1 = pattern.queryArc(1.0, 2.0)
            events1.size shouldBe 1
            events1[0].data.note shouldBe "e3"
        }

        "Parsing complex structure '[c3, e3*2]'" {
            // Stack of c3 (0..1) and e3*2 (0..0.5, 0.5..1)
            val pattern = parse("[c3, e3*2]")
            val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

            events.size shouldBe 3

            events.count { it.data.note == "c3" } shouldBe 1
            events.count { it.data.note == "e3" } shouldBe 2
        }
    }
}
