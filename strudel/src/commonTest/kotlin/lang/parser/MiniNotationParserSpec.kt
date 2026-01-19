package io.peekandpoke.klang.strudel.lang.parser

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.lang.note

class MiniNotationParserSpec : StringSpec() {

    fun parse(input: String) = parseMiniNotation(input) { text, _ -> note(text) }

    init {
        "Parsing a single atom 'c3'" {
            val pattern = parse("c3")
            val events = pattern.queryArc(0.0, 1.0)

            events.size shouldBe 1
            with(events[0]) {
                begin.toDouble() shouldBe 0.0
                end.toDouble() shouldBe 1.0
                data.note shouldBeEqualIgnoringCase "c3"
            }
        }

        "Parsing a sequence 'c3 e3'" {
            val pattern = parse("c3 e3")
            val events = pattern.queryArc(0.0, 1.0)

            events.size shouldBe 2

            with(events[0]) {
                begin.toDouble() shouldBe 0.0
                end.toDouble() shouldBe 0.5
                data.note shouldBeEqualIgnoringCase "c3"
            }

            with(events[1]) {
                begin.toDouble() shouldBe 0.5
                end.toDouble() shouldBe 1.0
                data.note shouldBeEqualIgnoringCase "e3"
            }
        }

        "Parsing a sequence with rest 'c3 ~'" {
            val pattern = parse("c3 ~")
            val events = pattern.queryArc(0.0, 1.0)

            events.size shouldBe 1

            with(events[0]) {
                begin.toDouble() shouldBe 0.0
                end.toDouble() shouldBe 0.5
                data.note shouldBeEqualIgnoringCase "c3"
            }
        }

        "Parsing a stack 'c3, e3'" {
            val pattern = parse("c3, e3")
            val events = pattern.queryArc(0.0, 1.0).sortedBy { it.data.note }

            events.size shouldBe 2

            // Both should occupy the full cycle
            with(events[0]) {
                data.note shouldBeEqualIgnoringCase "c3"
                begin.toDouble() shouldBe 0.0
                end.toDouble() shouldBe 1.0
            }

            with(events[1]) {
                data.note shouldBeEqualIgnoringCase "e3"
                begin.toDouble() shouldBe 0.0
                end.toDouble() shouldBe 1.0
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
                data.note shouldBeEqualIgnoringCase "c3"
                begin.toDouble() shouldBe 0.0
                end.toDouble() shouldBe 0.25
            }
            with(events[1]) {
                data.note shouldBeEqualIgnoringCase "e3"
                begin.toDouble() shouldBe 0.25
                end.toDouble() shouldBe 0.5
            }
            with(events[2]) {
                data.note shouldBeEqualIgnoringCase "g3"
                begin.toDouble() shouldBe 0.5
                end.toDouble() shouldBe 1.0
            }
        }

        "Parsing speed modifiers 'c3*2'" {
            // c3*2 -> play c3 twice in one cycle
            val pattern = parse("c3*2")
            val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

            events.size shouldBe 2

            with(events[0]) {
                data.note shouldBeEqualIgnoringCase "c3"
                begin.toDouble() shouldBe 0.0
                end.toDouble() shouldBe 0.5
            }
            with(events[1]) {
                data.note shouldBeEqualIgnoringCase "c3"
                begin.toDouble() shouldBe 0.5
                end.toDouble() shouldBe 1.0
            }
        }

        "Parsing speed modifiers 'c3/2'" {
            // c3/2 -> c3 stretched to 2 cycles.
            val pattern = parse("c3/2")
            val events = pattern.queryArc(0.0, 1.0)

            events.size shouldBe 1
            with(events[0]) {
                data.note shouldBeEqualIgnoringCase "c3"
                begin.toDouble() shouldBe 0.0
                end.toDouble() shouldBe 2.0 // It's a 2-cycle event
            }
        }

        "Parsing alternation '<c3 e3>'" {
            // <c3 e3> -> c3 in cycle 0, e3 in cycle 1
            val pattern = parse("<c3 e3>")

            val events0 = pattern.queryArc(0.0, 1.0)
            events0.size shouldBe 1
            events0[0].data.note shouldBeEqualIgnoringCase "c3"

            val events1 = pattern.queryArc(1.0, 2.0)
            events1.size shouldBe 1
            events1[0].data.note shouldBeEqualIgnoringCase "e3"
        }

        "Parsing complex structure '[c3, e3*2]'" {
            // Stack of c3 (0..1) and e3*2 (0..0.5, 0.5..1)
            val pattern = parse("[c3, e3*2]")
            val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

            events.size shouldBe 3

            events.count { it.data.note?.lowercase() == "c3" } shouldBe 1
            events.count { it.data.note?.lowercase() == "e3" } shouldBe 2
        }

        "Parsing basic weight 'e@2 a'" {
            // e has weight 2, a has weight 1 (default)
            // Total weight = 3
            // e gets 2/3 (0.0 to 0.667), a gets 1/3 (0.667 to 1.0)
            val pattern = parse("e@2 a")
            val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

            events.size shouldBe 2

            with(events[0]) {
                data.note shouldBeEqualIgnoringCase "e"
                begin.toDouble() shouldBe 0.0
                end.toDouble() shouldBe ((2.0 / 3.0) plusOrMinus EPSILON)
            }

            with(events[1]) {
                data.note shouldBeEqualIgnoringCase "a"
                begin.toDouble() shouldBe ((2.0 / 3.0) plusOrMinus EPSILON)
                end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
            }
        }

        "Parsing multiple weights 'a b@3 c'" {
            // Total weight = 1 + 3 + 1 = 5
            // a gets 1/5, b gets 3/5, c gets 1/5
            val pattern = parse("a b@3 c")
            val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

            events.size shouldBe 3

            with(events[0]) {
                data.note shouldBeEqualIgnoringCase "a"
                begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
                end.toDouble() shouldBe (0.2 plusOrMinus EPSILON)
            }

            with(events[1]) {
                data.note shouldBeEqualIgnoringCase "b"
                begin.toDouble() shouldBe (0.2 plusOrMinus EPSILON)
                end.toDouble() shouldBe (0.8 plusOrMinus EPSILON)
            }

            with(events[2]) {
                data.note shouldBeEqualIgnoringCase "c"
                begin.toDouble() shouldBe (0.8 plusOrMinus EPSILON)
                end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
            }
        }

        "Parsing equal weights 'a@2 b@2'" {
            // Both have weight 2, should be equal distribution (like 'a b')
            val pattern = parse("a@2 b@2")
            val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

            events.size shouldBe 2

            with(events[0]) {
                data.note shouldBeEqualIgnoringCase "a"
                begin.toDouble() shouldBe 0.0
                end.toDouble() shouldBe 0.5
            }

            with(events[1]) {
                data.note shouldBeEqualIgnoringCase "b"
                begin.toDouble() shouldBe 0.5
                end.toDouble() shouldBe 1.0
            }
        }

        "Parsing weight with other modifiers 'c3@2*2'" {
            // Weight applied first, then speed modifier
            val pattern = parse("c3@2*2")
            // c3@2 in a sequence would take 2/3 time, but here it's alone so takes full cycle
            // Then *2 makes it play twice
            val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

            events.size shouldBe 2
            events.forEach {
                it.data.note shouldBeEqualIgnoringCase "c3"
            }
        }

        "Parsing sound with index 'bd:1'" {
            val pattern = parse("bd:1")
            val events = pattern.queryArc(0.0, 1.0)

            events.size shouldBe 1
            with(events[0]) {
                data.note shouldBeEqualIgnoringCase "bd"
                data.soundIndex shouldBe 1
            }
        }

        "Parsing sound with index and gain 'bd:1:0.5'" {
            val pattern = parse("bd:1:0.5")
            val events = pattern.queryArc(0.0, 1.0)

            events.size shouldBe 1
            with(events[0]) {
                data.note shouldBeEqualIgnoringCase "bd"
                data.soundIndex shouldBe 1
                data.gain shouldBe 0.5
            }
        }

        "Parsing sound with index and gain and modifiers 'bd:1:0.5*2'" {
            val pattern = parse("bd:1:0.5*2")
            val events = pattern.queryArc(0.0, 1.0).sortedBy { it.begin }

            events.size shouldBe 2
            with(events[0]) {
                data.note shouldBeEqualIgnoringCase "bd"
                data.soundIndex shouldBe 1
                data.gain shouldBe 0.5
            }
            with(events[1]) {
                data.note shouldBeEqualIgnoringCase "bd"
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

        // ===== Source Location Tracking Tests =====

        "Single atom tracks source location" {
            val baseLocation = io.peekandpoke.klang.script.ast.SourceLocation(
                source = "test.klang",
                line = 1,
                column = 10
            )

            val pattern = parseMiniNotation("bd", baseLocation) { text, sourceLocations ->
                io.peekandpoke.klang.strudel.pattern.AtomicPattern(
                    io.peekandpoke.klang.audio_bridge.VoiceData.empty.copy(note = text),
                    sourceLocations
                )
            }

            val events = pattern.queryArc(0.0, 1.0)
            events.size shouldBe 1

            val atomPattern = pattern as? io.peekandpoke.klang.strudel.pattern.AtomicPattern
            atomPattern shouldNotBe null
            atomPattern?.sourceLocations shouldNotBe null
            atomPattern?.sourceLocations?.outermost?.source shouldBe "test.klang"
            atomPattern?.sourceLocations?.outermost?.line shouldBe 1
            atomPattern?.sourceLocations?.outermost?.column shouldBe 10  // baseColumn + 0 (start of "bd")
        }

        "Sequence preserves individual atom locations" {
            val baseLocation = io.peekandpoke.klang.script.ast.SourceLocation(
                source = "test.klang",
                line = 1,
                column = 10
            )

            val pattern = parseMiniNotation("bd hh", baseLocation) { text, sourceLocations ->
                io.peekandpoke.klang.strudel.pattern.AtomicPattern(
                    io.peekandpoke.klang.audio_bridge.VoiceData.empty.copy(note = text),
                    sourceLocations
                )
            }

            val events = pattern.queryArc(0.0, 1.0)
            events.size shouldBe 2

            // First event should have location at column 10 (start of "bd")
            events[0].sourceLocations?.outermost?.column shouldBe 10

            // Second event should have location at column 13 (start of "hh")
            events[1].sourceLocations?.outermost?.column shouldBe 13
        }

        "Nested groups preserve atom locations" {
            val baseLocation = io.peekandpoke.klang.script.ast.SourceLocation(
                source = "test.klang",
                line = 1,
                column = 10
            )

            val pattern = parseMiniNotation("[bd hh]", baseLocation) { text, sourceLocations ->
                io.peekandpoke.klang.strudel.pattern.AtomicPattern(
                    io.peekandpoke.klang.audio_bridge.VoiceData.empty.copy(note = text),
                    sourceLocations
                )
            }

            val events = pattern.queryArc(0.0, 1.0)
            events.size shouldBe 2

            // First event "bd" is at position 1 inside the brackets (column 10 + 1)
            events[0].sourceLocations?.outermost?.column shouldBe 11

            // Second event "hh" is at position 4 inside the brackets (column 10 + 4)
            events[1].sourceLocations?.outermost?.column shouldBe 14
        }

        "Alternation preserves atom locations" {
            val baseLocation = io.peekandpoke.klang.script.ast.SourceLocation(
                source = "test.klang",
                line = 1,
                column = 10
            )

            val pattern = parseMiniNotation("<bd hh>", baseLocation) { text, sourceLocations ->
                io.peekandpoke.klang.strudel.pattern.AtomicPattern(
                    io.peekandpoke.klang.audio_bridge.VoiceData.empty.copy(note = text),
                    sourceLocations
                )
            }

            // Query multiple cycles to see both alternations
            val events0 = pattern.queryArc(0.0, 1.0)
            val events1 = pattern.queryArc(1.0, 2.0)

            // Cycle 0 should play "bd" at column 11
            events0[0].sourceLocations?.outermost?.column shouldBe 11

            // Cycle 1 should play "hh" at column 14
            events1[0].sourceLocations?.outermost?.column shouldBe 14
        }

        "Euclidean rhythm preserves inner pattern location" {
            val baseLocation = io.peekandpoke.klang.script.ast.SourceLocation(
                source = "test.klang",
                line = 1,
                column = 10
            )

            val pattern = parseMiniNotation("bd(3,8)", baseLocation) { text, sourceLocations ->
                io.peekandpoke.klang.strudel.pattern.AtomicPattern(
                    io.peekandpoke.klang.audio_bridge.VoiceData.empty.copy(note = text),
                    sourceLocations
                )
            }

            val events = pattern.queryArc(0.0, 1.0)

            // All events should come from "bd" which starts at column 10
            events.all { it.sourceLocations?.outermost?.column == 10 } shouldBe true
        }

        "Weighted pattern preserves atom location" {
            val baseLocation = io.peekandpoke.klang.script.ast.SourceLocation(
                source = "test.klang",
                line = 1,
                column = 10
            )

            val pattern = parseMiniNotation("bd@3", baseLocation) { text, sourceLocations ->
                io.peekandpoke.klang.strudel.pattern.AtomicPattern(
                    io.peekandpoke.klang.audio_bridge.VoiceData.empty.copy(note = text),
                    sourceLocations
                )
            }

            val events = pattern.queryArc(0.0, 1.0)
            events[0].sourceLocations?.outermost?.column shouldBe 10
        }

        "Fast/slow modifiers preserve atom location" {
            val baseLocation = io.peekandpoke.klang.script.ast.SourceLocation(
                source = "test.klang",
                line = 1,
                column = 10
            )

            val pattern = parseMiniNotation("bd*2", baseLocation) { text, sourceLocations ->
                io.peekandpoke.klang.strudel.pattern.AtomicPattern(
                    io.peekandpoke.klang.audio_bridge.VoiceData.empty.copy(note = text),
                    sourceLocations
                )
            }

            val events = pattern.queryArc(0.0, 1.0)
            // Both fast repetitions should point to the same "bd" at column 10
            events.all { it.sourceLocations?.outermost?.column == 10 } shouldBe true
        }

        "Multiline notation tracks line numbers" {
            val baseLocation = io.peekandpoke.klang.script.ast.SourceLocation(
                source = "test.klang",
                line = 5,
                column = 0
            )

            // Mini-notation doesn't actually support multi-line strings,
            // but we can test that the base location is preserved
            val pattern = parseMiniNotation("bd hh", baseLocation) { text, sourceLocations ->
                io.peekandpoke.klang.strudel.pattern.AtomicPattern(
                    io.peekandpoke.klang.audio_bridge.VoiceData.empty.copy(note = text),
                    sourceLocations
                )
            }

            val events = pattern.queryArc(0.0, 1.0)

            // All locations should be on line 5
            events.all { it.sourceLocations?.outermost?.line == 5 } shouldBe true
        }

        "Null baseLocation creates atoms without source locations" {
            val pattern = parseMiniNotation("bd hh", null) { text, sourceLocations ->
                io.peekandpoke.klang.strudel.pattern.AtomicPattern(
                    io.peekandpoke.klang.audio_bridge.VoiceData.empty.copy(note = text),
                    sourceLocations
                )
            }

            val events = pattern.queryArc(0.0, 1.0)

            // Should have events but no source locations
            events.size shouldBe 2
            events[0].sourceLocations shouldBe null
            events[1].sourceLocations shouldBe null
        }
    }
}
