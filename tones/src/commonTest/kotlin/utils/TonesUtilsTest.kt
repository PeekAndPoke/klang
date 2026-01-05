package io.peekandpoke.klang.tones.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TonesUtilsTest : StringSpec({
    "fillStr" {
        TonesUtils.fillStr("#", 5) shouldBe "#####"
        TonesUtils.fillStr("b", 0) shouldBe ""
        TonesUtils.fillStr("a", 3) shouldBe "aaa"
    }
})
