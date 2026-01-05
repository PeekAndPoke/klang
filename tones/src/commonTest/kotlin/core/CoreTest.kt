package io.peekandpoke.klang.tones.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class CoreTest : StringSpec({
    "fillStr" {
        Core.fillStr("#", 5) shouldBe "#####"
        Core.fillStr("b", 0) shouldBe ""
        Core.fillStr("a", 3) shouldBe "aaa"
    }
})
