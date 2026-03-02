package io.peekandpoke.klang.blocks.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ImportStatementRoundTripTest : StringSpec({

    "import wildcard round-trips" {
        roundTrip("""import * from "strudel"""").shouldRoundTrip()
    }

    "import wildcard with namespace alias round-trips" {
        roundTrip("""import * as S from "strudel"""").shouldRoundTrip()
    }

    "import named bindings round-trips" {
        // Code gen produces {name1, name2} — no spaces around braces
        roundTrip("""import {note, sound} from "strudel"""").shouldRoundTrip()
    }

    "import followed by chain statement round-trips" {
        roundTrip("import * from \"strudel\"\nsound(\"bd\")").shouldRoundTrip()
    }

    "generated code for import wildcard is correct" {
        val result = roundTrip("""import * from "strudel"""")
        result.generatedCode shouldBe """import * from "strudel""""
    }

    "generated code for namespace alias import is correct" {
        val result = roundTrip("""import * as S from "strudel"""")
        result.generatedCode shouldBe """import * as S from "strudel""""
    }
})
