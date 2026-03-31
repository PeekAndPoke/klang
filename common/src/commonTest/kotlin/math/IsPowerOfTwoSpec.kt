package io.peekandpoke.klang.common.math

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class IsPowerOfTwoSpec : StringSpec({

    "powers of two return true" {
        listOf(
            1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 65536, 1_048_576,
            1 shl 30  // 1,073,741,824 — largest power of two that fits in Int
        ).forEach { n ->
            n.isPowerOfTwo() shouldBe true
        }
    }

    "non-powers of two return false" {
        listOf(3, 5, 6, 7, 9, 10, 12, 15, 17, 18, 24, 100, 1000, 1023, 1025).forEach { n ->
            n.isPowerOfTwo() shouldBe false
        }
    }

    "zero returns false" {
        0.isPowerOfTwo() shouldBe false
    }

    "negative numbers return false" {
        listOf(-1, -2, -4, -8, -16, -1024, Int.MIN_VALUE).forEach { n ->
            n.isPowerOfTwo() shouldBe false
        }
    }

    "Int.MAX_VALUE is not a power of two" {
        // 2^31 - 1 = 0x7FFFFFFF — all bits set except sign, not a power of two
        Int.MAX_VALUE.isPowerOfTwo() shouldBe false
    }
})
