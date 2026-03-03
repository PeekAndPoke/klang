package io.peekandpoke.klang.blocks.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ImportStatementRoundTripTest : StringSpec({

    "import wildcard round-trips" {
        roundTrip("""import * from "strudel"""").shouldRoundTripWithCode()
    }

    "import wildcard with namespace alias round-trips" {
        roundTrip("""import * as S from "strudel"""").shouldRoundTripWithCode()
    }

    "import named bindings round-trips" {
        // Code gen produces {name1, name2} — no spaces around braces
        roundTrip("""import {note, sound} from "strudel"""").shouldRoundTripWithCode()
    }

    "import followed by chain statement round-trips" {
        roundTrip("import * from \"strudel\"\nsound(\"bd\")").shouldRoundTripWithCode()
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
