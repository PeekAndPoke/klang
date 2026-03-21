package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.StrudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangComparisonAndLogicSpec : StringSpec({

    // -- lt() ---------------------------------------------------------------------------------------------------------

    "lt() checks less than" {
        // 1 < 2 -> 1, 2 < 2 -> 0, 3 < 2 -> 0
        val p = seq("1 2 3").lt("2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 1
        events[1].data.value?.asInt shouldBe 0
        events[2].data.value?.asInt shouldBe 0
    }

    "lt() works as top-level PatternMapper" {
        val p = seq("1 3").apply(lt("2"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1  // 1<2 → 1
        events[1].data.value?.asInt shouldBe 0  // 3<2 → 0
    }

    "lt() works as string extension" {
        val p = "1 3".lt("2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1
        events[1].data.value?.asInt shouldBe 0
    }

    "lt dsl interface" {
        val pat = "1 3"
        val ctrl = "2"

        dslInterfaceTests(
            "pattern.lt(ctrl)" to seq(pat).lt(ctrl),
            "script pattern.lt(ctrl)" to StrudelPattern.compile("""seq("$pat").lt("$ctrl")"""),
            "string.lt(ctrl)" to pat.lt(ctrl),
            "script string.lt(ctrl)" to StrudelPattern.compile(""""$pat".lt("$ctrl")"""),
            "lt(ctrl)" to seq(pat).apply(lt(ctrl)),
            "script lt(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(lt("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asInt shouldBe 1  // 1 < 2 → 1
            events[1].data.value?.asInt shouldBe 0  // 3 < 2 → 0
        }
    }

    // -- gt() ---------------------------------------------------------------------------------------------------------

    "gt() checks greater than" {
        val p = seq("1 2 3").gt("2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 0
        events[1].data.value?.asInt shouldBe 0
        events[2].data.value?.asInt shouldBe 1
    }

    "gt() works as top-level PatternMapper" {
        val p = seq("1 3").apply(gt("2"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 0  // 1>2 → 0
        events[1].data.value?.asInt shouldBe 1  // 3>2 → 1
    }

    "gt() works as string extension" {
        val p = "1 3".gt("2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 0
        events[1].data.value?.asInt shouldBe 1
    }

    "gt dsl interface" {
        val pat = "1 3"
        val ctrl = "2"

        dslInterfaceTests(
            "pattern.gt(ctrl)" to seq(pat).gt(ctrl),
            "script pattern.gt(ctrl)" to StrudelPattern.compile("""seq("$pat").gt("$ctrl")"""),
            "string.gt(ctrl)" to pat.gt(ctrl),
            "script string.gt(ctrl)" to StrudelPattern.compile(""""$pat".gt("$ctrl")"""),
            "gt(ctrl)" to seq(pat).apply(gt(ctrl)),
            "script gt(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(gt("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asInt shouldBe 0  // 1 > 2 → 0
            events[1].data.value?.asInt shouldBe 1  // 3 > 2 → 1
        }
    }

    // -- lte() --------------------------------------------------------------------------------------------------------

    "lte() checks less than or equal" {
        val p = seq("1 2 3").lte("2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 1
        events[1].data.value?.asInt shouldBe 1
        events[2].data.value?.asInt shouldBe 0
    }

    "lte() works as top-level PatternMapper" {
        val p = seq("2 3").apply(lte("2"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1  // 2<=2 → 1
        events[1].data.value?.asInt shouldBe 0  // 3<=2 → 0
    }

    "lte() works as string extension" {
        val p = "2 3".lte("2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1
        events[1].data.value?.asInt shouldBe 0
    }

    "lte dsl interface" {
        val pat = "2 3"
        val ctrl = "2"

        dslInterfaceTests(
            "pattern.lte(ctrl)" to seq(pat).lte(ctrl),
            "script pattern.lte(ctrl)" to StrudelPattern.compile("""seq("$pat").lte("$ctrl")"""),
            "string.lte(ctrl)" to pat.lte(ctrl),
            "script string.lte(ctrl)" to StrudelPattern.compile(""""$pat".lte("$ctrl")"""),
            "lte(ctrl)" to seq(pat).apply(lte(ctrl)),
            "script lte(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(lte("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asInt shouldBe 1  // 2 <= 2 → 1
            events[1].data.value?.asInt shouldBe 0  // 3 <= 2 → 0
        }
    }

    // -- gte() --------------------------------------------------------------------------------------------------------

    "gte() checks greater than or equal" {
        val p = seq("1 2 3").gte("2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 0
        events[1].data.value?.asInt shouldBe 1
        events[2].data.value?.asInt shouldBe 1
    }

    "gte() works as top-level PatternMapper" {
        val p = seq("1 2").apply(gte("2"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 0  // 1>=2 → 0
        events[1].data.value?.asInt shouldBe 1  // 2>=2 → 1
    }

    "gte() works as string extension" {
        val p = "1 2".gte("2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 0
        events[1].data.value?.asInt shouldBe 1
    }

    "gte dsl interface" {
        val pat = "1 2"
        val ctrl = "2"

        dslInterfaceTests(
            "pattern.gte(ctrl)" to seq(pat).gte(ctrl),
            "script pattern.gte(ctrl)" to StrudelPattern.compile("""seq("$pat").gte("$ctrl")"""),
            "string.gte(ctrl)" to pat.gte(ctrl),
            "script string.gte(ctrl)" to StrudelPattern.compile(""""$pat".gte("$ctrl")"""),
            "gte(ctrl)" to seq(pat).apply(gte(ctrl)),
            "script gte(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(gte("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asInt shouldBe 0  // 1 >= 2 → 0
            events[1].data.value?.asInt shouldBe 1  // 2 >= 2 → 1
        }
    }

    // -- eq() ---------------------------------------------------------------------------------------------------------

    "eq() checks equality" {
        val p = seq("1 2 3").eq("2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 0
        events[1].data.value?.asInt shouldBe 1
        events[2].data.value?.asInt shouldBe 0
    }

    "eq() works as top-level PatternMapper" {
        val p = seq("2 3").apply(eq("2"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1  // 2==2 → 1
        events[1].data.value?.asInt shouldBe 0  // 3==2 → 0
    }

    "eq() works as string extension" {
        val p = "2 3".eq("2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1
        events[1].data.value?.asInt shouldBe 0
    }

    "eq dsl interface" {
        val pat = "2 3"
        val ctrl = "2"

        dslInterfaceTests(
            "pattern.eq(ctrl)" to seq(pat).eq(ctrl),
            "script pattern.eq(ctrl)" to StrudelPattern.compile("""seq("$pat").eq("$ctrl")"""),
            "string.eq(ctrl)" to pat.eq(ctrl),
            "script string.eq(ctrl)" to StrudelPattern.compile(""""$pat".eq("$ctrl")"""),
            "eq(ctrl)" to seq(pat).apply(eq(ctrl)),
            "script eq(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(eq("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asInt shouldBe 1  // 2 == 2 → 1
            events[1].data.value?.asInt shouldBe 0  // 3 == 2 → 0
        }
    }

    // -- eqt() --------------------------------------------------------------------------------------------------------

    "eqt() checks truthiness equality" {
        // 0 (falsy), 1 (truthy), 2 (truthy) vs 1 (truthy)
        val p = seq("0 1 2").eqt("1")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 0 // falsy vs truthy -> 0
        events[1].data.value?.asInt shouldBe 1 // truthy vs truthy -> 1
        events[2].data.value?.asInt shouldBe 1 // truthy vs truthy -> 1
    }

    "eqt() works as top-level PatternMapper" {
        val p = seq("0 5").apply(eqt("1"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 0  // 0 (falsy) ~= 1 (truthy) → 0
        events[1].data.value?.asInt shouldBe 1  // 5 (truthy) ~= 1 (truthy) → 1
    }

    "eqt() works as string extension" {
        val p = "0 5".eqt("1")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 0
        events[1].data.value?.asInt shouldBe 1
    }

    "eqt dsl interface" {
        val pat = "0 5"
        val ctrl = "1"

        dslInterfaceTests(
            "pattern.eqt(ctrl)" to seq(pat).eqt(ctrl),
            "script pattern.eqt(ctrl)" to StrudelPattern.compile("""seq("$pat").eqt("$ctrl")"""),
            "string.eqt(ctrl)" to pat.eqt(ctrl),
            "script string.eqt(ctrl)" to StrudelPattern.compile(""""$pat".eqt("$ctrl")"""),
            "eqt(ctrl)" to seq(pat).apply(eqt(ctrl)),
            "script eqt(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(eqt("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asInt shouldBe 0  // 0 (falsy) ~= 1 (truthy) → 0
            events[1].data.value?.asInt shouldBe 1  // 5 (truthy) ~= 1 (truthy) → 1
        }
    }

    // -- ne() ---------------------------------------------------------------------------------------------------------

    "ne() checks inequality" {
        val p = seq("1 2 3").ne("2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 1
        events[1].data.value?.asInt shouldBe 0
        events[2].data.value?.asInt shouldBe 1
    }

    "ne() works as top-level PatternMapper" {
        val p = seq("2 3").apply(ne("2"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 0  // 2!=2 → 0
        events[1].data.value?.asInt shouldBe 1  // 3!=2 → 1
    }

    "ne() works as string extension" {
        val p = "2 3".ne("2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 0
        events[1].data.value?.asInt shouldBe 1
    }

    "ne dsl interface" {
        val pat = "2 3"
        val ctrl = "2"

        dslInterfaceTests(
            "pattern.ne(ctrl)" to seq(pat).ne(ctrl),
            "script pattern.ne(ctrl)" to StrudelPattern.compile("""seq("$pat").ne("$ctrl")"""),
            "string.ne(ctrl)" to pat.ne(ctrl),
            "script string.ne(ctrl)" to StrudelPattern.compile(""""$pat".ne("$ctrl")"""),
            "ne(ctrl)" to seq(pat).apply(ne(ctrl)),
            "script ne(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(ne("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asInt shouldBe 0  // 2 != 2 → 0
            events[1].data.value?.asInt shouldBe 1  // 3 != 2 → 1
        }
    }

    // -- net() --------------------------------------------------------------------------------------------------------

    "net() checks truthiness inequality" {
        // 0 (falsy), 1 (truthy), 2 (truthy) vs 0 (falsy)
        val p = seq("0 1 2").net("0")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
        events[0].data.value?.asInt shouldBe 0 // falsy vs falsy -> 0
        events[1].data.value?.asInt shouldBe 1 // truthy vs falsy -> 1
        events[2].data.value?.asInt shouldBe 1 // truthy vs falsy -> 1
    }

    "net() works as top-level PatternMapper" {
        val p = seq("0 5").apply(net("0"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 0  // 0 (falsy) ~!= 0 (falsy) → 0
        events[1].data.value?.asInt shouldBe 1  // 5 (truthy) ~!= 0 (falsy) → 1
    }

    "net() works as string extension" {
        val p = "0 5".net("0")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 0
        events[1].data.value?.asInt shouldBe 1
    }

    "net dsl interface" {
        val pat = "0 5"
        val ctrl = "0"

        dslInterfaceTests(
            "pattern.net(ctrl)" to seq(pat).net(ctrl),
            "script pattern.net(ctrl)" to StrudelPattern.compile("""seq("$pat").net("$ctrl")"""),
            "string.net(ctrl)" to pat.net(ctrl),
            "script string.net(ctrl)" to StrudelPattern.compile(""""$pat".net("$ctrl")"""),
            "net(ctrl)" to seq(pat).apply(net(ctrl)),
            "script net(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(net("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asInt shouldBe 0  // 0 (falsy) ~!= 0 (falsy) → 0
            events[1].data.value?.asInt shouldBe 1  // 5 (truthy) ~!= 0 (falsy) → 1
        }
    }

    // -- and() --------------------------------------------------------------------------------------------------------

    "and() performs logical AND" {
        // 0 and 5 -> 0 (falsy)
        // 1 and 5 -> 5 (truthy -> returns other)
        val p = seq("0 1").and("5")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 0
        events[1].data.value?.asInt shouldBe 5
    }

    "and() works as top-level PatternMapper" {
        val p = seq("0 5").apply(and("10"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 0   // 0&&10 → 0
        events[1].data.value?.asInt shouldBe 10  // 5&&10 → 10
    }

    "and() works as string extension" {
        val p = "0 5".and("10")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 0
        events[1].data.value?.asInt shouldBe 10
    }

    "and dsl interface" {
        val pat = "0 5"
        val ctrl = "10"

        dslInterfaceTests(
            "pattern.and(ctrl)" to seq(pat).and(ctrl),
            "script pattern.and(ctrl)" to StrudelPattern.compile("""seq("$pat").and("$ctrl")"""),
            "string.and(ctrl)" to pat.and(ctrl),
            "script string.and(ctrl)" to StrudelPattern.compile(""""$pat".and("$ctrl")"""),
            "and(ctrl)" to seq(pat).apply(and(ctrl)),
            "script and(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(and("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asInt shouldBe 0   // 0 && 10 → 0
            events[1].data.value?.asInt shouldBe 10  // 5 && 10 → 10
        }
    }

    // -- or() ---------------------------------------------------------------------------------------------------------

    "or() performs logical OR" {
        // 0 or 5 -> 5 (falsy -> returns other)
        // 1 or 5 -> 1 (truthy -> returns self)
        val p = seq("0 1").or("5")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 5
        events[1].data.value?.asInt shouldBe 1
    }

    "or() works as top-level PatternMapper" {
        val p = seq("0 5").apply(or("10"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 10  // 0||10 → 10
        events[1].data.value?.asInt shouldBe 5   // 5||10 → 5
    }

    "or() works as string extension" {
        val p = "0 5".or("10")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 10
        events[1].data.value?.asInt shouldBe 5
    }

    "or dsl interface" {
        val pat = "0 5"
        val ctrl = "10"

        dslInterfaceTests(
            "pattern.or(ctrl)" to seq(pat).or(ctrl),
            "script pattern.or(ctrl)" to StrudelPattern.compile("""seq("$pat").or("$ctrl")"""),
            "string.or(ctrl)" to pat.or(ctrl),
            "script string.or(ctrl)" to StrudelPattern.compile(""""$pat".or("$ctrl")"""),
            "or(ctrl)" to seq(pat).apply(or(ctrl)),
            "script or(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(or("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asInt shouldBe 10  // 0 || 10 → 10
            events[1].data.value?.asInt shouldBe 5   // 5 || 10 → 5
        }
    }
})
