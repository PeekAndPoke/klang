package io.peekandpoke.klang.tones.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class CoreTest : StringSpec({
    "fillStr" {
        fillStr("#", 5) shouldBe "#####"
        fillStr("b", 0) shouldBe ""
        fillStr("a", 3) shouldBe "aaa"
    }
})
