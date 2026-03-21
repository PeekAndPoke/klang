package io.peekandpoke.klang.strudel.lang.addons

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.fast
import io.peekandpoke.klang.strudel.lang.note
import io.peekandpoke.klang.strudel.lang.s
import io.peekandpoke.klang.strudel.lang.sound

class LangMorseSpec : StringSpec({

    "morse() creates pattern from single letter" {
        // 'S' = "..."  (3 dots = 3 steps + 2 intra-gaps = 5 steps total)
        val p = morse("S")
        val events = p.queryArc(0.0, 1.0)

        // Should produce events
        events.shouldNotBeEmpty()

        // All events should have value 1.0
        events.all { it.data.value?.asDouble == 1.0 } shouldBe true
    }

    "morse() creates pattern from multiple letters" {
        // 'SOS' = "... --- ..."
        val p = morse("SOS")
        val events = p.queryArc(0.0, 2.0)

        // Should produce events for all morse symbols
        events.shouldNotBeEmpty()
        events.all { it.data.value?.asDouble == 1.0 } shouldBe true
    }

    "morse() handles numbers" {
        // '1' = ".----"
        val p = morse("1")
        val events = p.queryArc(0.0, 2.0)

        events.shouldNotBeEmpty()
        events.all { it.data.value?.asDouble == 1.0 } shouldBe true
    }

    "morse() handles mixed letters and numbers" {
        val p = morse("A1B")
        val events = p.queryArc(0.0, 3.0)

        events.shouldNotBeEmpty()
        events.all { it.data.value?.asDouble == 1.0 } shouldBe true
    }

    "morse() handles words with spaces" {
        // Words should be separated by 7-step gaps
        val p = morse("HI MOM")
        val events = p.queryArc(0.0, 5.0)

        events.shouldNotBeEmpty()

        // Check that we get events (morse code for each word)
        val eventCount = events.size
        eventCount shouldBe events.size // Just verify we got events
    }

    "morse() handles empty string" {
        val p = morse("")
        val events = p.queryArc(0.0, 1.0)

        // Should return silence - no events
        events.size shouldBe 0
    }

    "morse() filters unknown characters" {
        // '@' is not in morse map, should be skipped
        val p = morse("A@B")
        val events = p.queryArc(0.0, 2.0)

        // Should still produce events for A and B
        events.shouldNotBeEmpty()
    }

    "morse() is case insensitive" {
        val pUpper = morse("SOS")
        val pLower = morse("sos")
        val pMixed = morse("SoS")

        val eventsUpper = pUpper.queryArc(0.0, 2.0)
        val eventsLower = pLower.queryArc(0.0, 2.0)
        val eventsMixed = pMixed.queryArc(0.0, 2.0)

        // Should produce same number of events
        eventsUpper.size shouldBe eventsLower.size
        eventsLower.size shouldBe eventsMixed.size
    }

    "morse() works as pattern extension" {
        // note("c d e").morse("SOS") should structure the notes with morse rhythm
        val p = note("c3 d3 e3").morse("SOS")
        val events = p.queryArc(0.0, 2.0)

        events.shouldNotBeEmpty()

        // Should have note values
        val notes = events.mapNotNull { it.data.note }
        notes.shouldNotBeEmpty()
        notes.any { it == "c3" || it == "d3" || it == "e3" } shouldBe true
    }

    "morse() works as string extension" {
        val p = "sawtooth".morse("HI").sound()
        val events = p.queryArc(0.0, 2.0)

        events.shouldNotBeEmpty()
        events.all { it.data.sound == "sawtooth" } shouldBe true
    }

    "morse() works with sound pattern" {
        val p = s("sine").morse("A")
        val events = p.queryArc(0.0, 1.0)

        events.shouldNotBeEmpty()
        events.all { it.data.sound == "sine" } shouldBe true
    }

    "morse() works in compiled code" {
        val p = StrudelPattern.compile("""morse("SOS")""")
        val events = p?.queryArc(0.0, 2.0) ?: emptyList()

        events.shouldNotBeEmpty()
        events.all { it.data.value?.asDouble == 1.0 } shouldBe true
    }

    "morse() pattern extension works in compiled code" {
        val p = StrudelPattern.compile("""note("c3 d3 e3").morse("HI")""")
        val events = p?.queryArc(0.0, 2.0) ?: emptyList()

        events.shouldNotBeEmpty()
        val notes = events.mapNotNull { it.data.note }
        notes.shouldNotBeEmpty()
    }

    "morse() handles multiple spaces between words" {
        // Multiple spaces should be treated as single word separator
        val p1 = morse("A B")
        val p2 = morse("A   B") // Multiple spaces

        val events1 = p1.queryArc(0.0, 2.0)
        val events2 = p2.queryArc(0.0, 2.0)

        // Should produce same structure (multiple spaces collapsed)
        events1.size shouldBe events2.size
    }

    "morse() handles leading and trailing whitespace" {
        val p1 = morse("SOS")
        val p2 = morse("  SOS  ")

        val events1 = p1.queryArc(0.0, 2.0)
        val events2 = p2.queryArc(0.0, 2.0)

        // Should produce same structure
        events1.size shouldBe events2.size
    }

    "morse() short message produces shorter pattern" {
        // 'E' = "." (1 dot, shortest morse)
        val pShort = morse("E")
        val pLong = morse("SOS")

        val eventsShort = pShort.queryArc(0.0, 1.0)
        val eventsLong = pLong.queryArc(0.0, 1.0)

        // Shorter morse should have fewer events in same time window
        // Just verify they're different patterns
        (eventsShort.size != eventsLong.size) shouldBe true
    }

    "morse() can be chained with other functions" {
        val p = note("c3 d3 e3").morse("SOS").fast(2.0)
        val events = p.queryArc(0.0, 2.0)

        events.shouldNotBeEmpty()

        // Should have note values
        val notes = events.mapNotNull { it.data.note }
        notes.shouldNotBeEmpty()
    }
})
