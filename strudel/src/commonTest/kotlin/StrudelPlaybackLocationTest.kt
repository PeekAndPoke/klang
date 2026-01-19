package io.peekandpoke.klang.strudel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.script.ast.SourceLocation
import io.peekandpoke.klang.strudel.lang.parser.parseMiniNotation
import io.peekandpoke.klang.strudel.pattern.AtomicPattern

/**
 * Tests for source location tracking through the StrudelPlayback event callback
 */
class StrudelPlaybackLocationTest : StringSpec({

    "StrudelPlayback callback receives events with sourceLocations" {
        var callbackFired = false
        var receivedEvent: StrudelPatternEvent? = null
        var receivedTime: Double? = null

        val baseLocation = SourceLocation(source = "test.klang", line = 1, column = 10)

        val pattern = parseMiniNotation("bd", baseLocation) { text, sourceLocations ->
            AtomicPattern(VoiceData.empty.copy(note = text), sourceLocations)
        }

        // We can't easily test the full StrudelPlayback without mocking the audio system,
        // but we can test that the pattern produces events with locations
        val events = pattern.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].sourceLocations shouldNotBe null
        events[0].sourceLocations?.outermost?.source shouldBe "test.klang"
        events[0].sourceLocations?.outermost?.line shouldBe 1
        events[0].sourceLocations?.outermost?.column shouldBe 10
    }

    "Events from sequence pattern preserve individual atom locations" {
        val baseLocation = SourceLocation(source = "test.klang", line = 1, column = 10)

        val pattern = parseMiniNotation("bd hh sd", baseLocation) { text, sourceLocations ->
            AtomicPattern(VoiceData.empty.copy(note = text), sourceLocations)
        }

        val events = pattern.queryArc(0.0, 1.0)

        events.size shouldBe 3

        // bd at column 10
        events[0].sourceLocations?.outermost?.column shouldBe 10
        // hh at column 13
        events[1].sourceLocations?.outermost?.column shouldBe 13
        // sd at column 16
        events[2].sourceLocations?.outermost?.column shouldBe 16
    }

    "Events from stacked patterns preserve individual locations" {
        val baseLocation = SourceLocation(source = "test.klang", line = 1, column = 10)

        val pattern1 = parseMiniNotation("bd", baseLocation) { text, sourceLocations ->
            AtomicPattern(VoiceData.empty.copy(note = text), sourceLocations)
        }

        val baseLocation2 = SourceLocation(source = "test.klang", line = 2, column = 5)
        val pattern2 = parseMiniNotation("hh", baseLocation2) { text, sourceLocations ->
            AtomicPattern(VoiceData.empty.copy(note = text), sourceLocations)
        }

        val stacked = io.peekandpoke.klang.strudel.lang.stack(pattern1, pattern2)

        val events = stacked.queryArc(0.0, 1.0)

        events.size shouldBe 2

        // Find bd and hh events (order might vary)
        val bdEvent = events.find { it.data.note == "bd" }
        val hhEvent = events.find { it.data.note == "hh" }

        bdEvent shouldNotBe null
        hhEvent shouldNotBe null

        bdEvent?.sourceLocations?.outermost?.line shouldBe 1
        bdEvent?.sourceLocations?.outermost?.column shouldBe 10

        hhEvent?.sourceLocations?.outermost?.line shouldBe 2
        hhEvent?.sourceLocations?.outermost?.column shouldBe 5
    }

    "Euclidean rhythm preserves inner pattern location" {
        val baseLocation = SourceLocation(source = "test.klang", line = 1, column = 10)

        val pattern = parseMiniNotation("bd(3,8)", baseLocation) { text, sourceLocations ->
            AtomicPattern(VoiceData.empty.copy(note = text), sourceLocations)
        }

        val events = pattern.queryArc(0.0, 1.0)

        // Should have 3 events (3 pulses in the euclidean rhythm)
        events.size shouldBe 3

        // All events should point to the same "bd" source location
        events.all { it.sourceLocations?.outermost?.column == 10 } shouldBe true
    }
})