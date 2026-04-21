package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern

/**
 * Verifies that CallInfo (source locations) flows through DSL functions when
 * called from KlangScript. Each test compiles a script expression and asserts
 * that the resulting events carry non-null source locations — proving the
 * generated bridge constructs and passes CallInfo correctly.
 *
 * Kotlin-side calls pass `callInfo = null` by design, so only script-compiled
 * patterns are tested here.
 */
class CallInfoTest : StringSpec({

    "gain passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").gain("0.5")""")
    }

    "pan passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").pan("0.25")""")
    }

    "velocity passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").velocity("0.5")""")
    }

    "postgain passes CallInfo from KlangScript" {
        assertCallInfoPresent("""seq("0 1").postgain("0.5")""")
    }
})

/**
 * Shared helper: compile a KlangScript expression, query events, and assert
 * that at least one event carries source-location info from CallInfo.
 */
fun assertCallInfoPresent(scriptExpr: String) {
    val pattern = SprudelPattern.compile(scriptExpr)
    pattern.shouldNotBeNull()

    val events = pattern.queryArc(0.0, 1.0)
    events.shouldNotBeEmpty()

    val hasLocations = events.any { it.sourceLocations != null }
    hasLocations shouldBe true
}
