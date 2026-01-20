package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.script.ast.SourceLocation
import io.peekandpoke.klang.strudel.pattern.AtomicPattern

/**
 * Tests for source location tracking from RuntimeValue → Pattern conversion
 */
class LocationTrackingTest : StringSpec({

    "RuntimeValue with location creates AtomicPattern with sourceLocations" {
        val location = SourceLocation(source = "test.klang", line = 1, column = 10)
        val stringValue = StrudelDslArg("bd", location = location)

        val patterns = listOf(stringValue).toListOfPatterns(defaultModifier)

        patterns.size shouldBe 1
        val pattern = patterns[0] as? AtomicPattern
        pattern shouldNotBe null
        pattern?.sourceLocations shouldNotBe null
        // The location should point to the content inside the string (column + 1 to skip quote)
        pattern?.sourceLocations?.outermost?.source shouldBe "test.klang"
        pattern?.sourceLocations?.outermost?.line shouldBe 1
        pattern?.sourceLocations?.outermost?.column shouldBe 11  // 10 + 1 for opening quote
    }

    "NumberValue with location creates AtomicPattern with sourceLocations" {
        val location = SourceLocation(source = "test.klang", line = 2, column = 5)
        val numberValue = StrudelDslArg(440.0, location = location)

        val patterns = listOf(numberValue).toListOfPatterns(defaultModifier)

        patterns.size shouldBe 1
        val pattern = patterns[0] as? AtomicPattern
        pattern shouldNotBe null
        pattern?.sourceLocations shouldNotBe null
        pattern?.sourceLocations?.locations?.size shouldBe 1
        pattern?.sourceLocations?.outermost shouldBe location
    }

    "RuntimeValue without location creates AtomicPattern with null sourceLocations" {
        val stringValue = StrudelDslArg("bd", location = null)

        val patterns = listOf(stringValue).toListOfPatterns(defaultModifier)

        patterns.size shouldBe 1
        val pattern = patterns[0] as? AtomicPattern
        pattern shouldNotBe null
        pattern?.sourceLocations shouldBe null
    }

    "Multiple RuntimeValues preserve individual locations" {
        val loc1 = SourceLocation(source = "test.klang", line = 1, column = 10)
        val loc2 = SourceLocation(source = "test.klang", line = 2, column = 15)

        val values = listOf(
            StrudelDslArg("bd", location = loc1),
            StrudelDslArg("hh", location = loc2)
        )

        val patterns = values.toListOfPatterns(defaultModifier)

        patterns.size shouldBe 2

        val pattern1 = patterns[0] as? AtomicPattern
        pattern1?.sourceLocations?.outermost?.line shouldBe 1
        pattern1?.sourceLocations?.outermost?.column shouldBe 11  // 10 + 1

        val pattern2 = patterns[1] as? AtomicPattern
        pattern2?.sourceLocations?.outermost?.line shouldBe 2
        pattern2?.sourceLocations?.outermost?.column shouldBe 16  // 15 + 1
    }

    "Plain Kotlin values create patterns without locations" {
        val patterns = listOf("bd", 440.0, true)
            .map { StrudelDslArg.of(it) }
            .toListOfPatterns(defaultModifier)

        patterns.size shouldBe 3
        patterns.forEach { pattern ->
            (pattern as? AtomicPattern)?.sourceLocations shouldBe null
        }
    }

    "Nested lists preserve locations" {
        val loc = SourceLocation(source = "test.klang", line = 1, column = 10)
        val stringValue = StrudelDslArg("bd", location = loc)

        val patterns = listOf(
            StrudelDslArg.of(listOf(stringValue))
        ).toListOfPatterns(defaultModifier)

        patterns.size shouldBe 1
        val pattern = patterns[0] as? AtomicPattern
        pattern?.sourceLocations?.outermost?.line shouldBe 1
        pattern?.sourceLocations?.outermost?.column shouldBe 11  // 10 + 1
    }
})
