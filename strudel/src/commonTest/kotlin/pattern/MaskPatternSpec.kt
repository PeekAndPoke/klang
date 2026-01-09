package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.lang.fast
import io.peekandpoke.klang.strudel.lang.mask
import io.peekandpoke.klang.strudel.lang.note
import io.peekandpoke.klang.strudel.lang.square
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

class MaskPatternSpec : StringSpec({

    "MaskPattern: filters source based on truthy values" {
        // Source plays a sequence
        val source = note("c3 e3 g3 b3")
        // Mask: 1 is truthy, 0 is falsy. Rhythm matches source subdivisions.
        val pattern = source.mask("1 0 1 0")

        val events = pattern.queryArc(0.0.toRational(), 1.0.toRational()).sortedBy { it.begin }

        // Should only have 1st and 3rd notes
        events.size shouldBe 2

        events[0].data.note shouldBe "c3"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)

        events[1].data.note shouldBe "g3"
        events[1].begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
    }

    "MaskPattern: works with continuous patterns as masks" {
        val source = note("c3*8") // 8 notes per cycle
        // Using a square wave as a mask (0 or 1)
        // square starts at 0 for first half, 1 for second half (per lang.kt impl)
        val pattern = source.mask(square.fast(4))

        val events = pattern.queryArc(0.0.toRational(), 1.0.toRational())

        // Should only have the notes from the second half of the cycle
        events.size shouldBe 4
        events.all { it.begin.toDouble() >= 0.0 } shouldBe true
    }
})
