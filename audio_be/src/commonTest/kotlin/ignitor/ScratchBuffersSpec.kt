package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer

class ScratchBuffersSpec : StringSpec({

    val blockFrames = 128

    "use returns a buffer of correct size" {
        val scratch = ScratchBuffers(blockFrames)

        scratch.use { buf ->
            buf.size shouldBe blockFrames
        }
    }

    "nested use calls return different buffers" {
        val scratch = ScratchBuffers(blockFrames)

        scratch.use { outer ->
            scratch.use { inner ->
                (outer !== inner) shouldBe true
            }
        }
    }

    "buffer is released after use block - subsequent use gets same buffer back" {
        val scratch = ScratchBuffers(blockFrames)

        var firstRef: AudioBuffer? = null
        scratch.use { buf ->
            firstRef = buf
        }

        scratch.use { buf ->
            (buf === firstRef) shouldBe true
        }
    }

    "pool grows when depth exceeds initial capacity" {
        val initialCapacity = 2
        val scratch = ScratchBuffers(blockFrames, initialCapacity)

        // Nest deeper than initialCapacity — pool must grow
        scratch.use { a ->
            scratch.use { b ->
                scratch.use { c ->
                    a.size shouldBe blockFrames
                    b.size shouldBe blockFrames
                    c.size shouldBe blockFrames
                    // All three must be distinct instances
                    (a !== b) shouldBe true
                    (b !== c) shouldBe true
                    (a !== c) shouldBe true
                }
            }
        }
    }

    "reset resets the stack pointer - next use gets first buffer" {
        val scratch = ScratchBuffers(blockFrames)

        var firstRef: AudioBuffer? = null
        scratch.use { buf ->
            firstRef = buf
        }

        // Advance the pointer by one more use/release cycle
        scratch.use { _ -> }

        scratch.reset()

        scratch.use { buf ->
            (buf === firstRef) shouldBe true
        }
    }

    "buffer contents are independent - writing to one does not affect another" {
        val scratch = ScratchBuffers(blockFrames)

        scratch.use { a ->
            scratch.use { b ->
                // Fill a with 1.0, b with 2.0
                a.fill(1.0)
                b.fill(2.0)

                // Verify they are independent
                for (i in 0 until blockFrames) {
                    a[i] shouldBe 1.0
                    b[i] shouldBe 2.0
                }
            }
        }
    }

    // ── useDouble tests ─────────────────────────────────────────────────────────

    "useDouble returns a DoubleArray of correct size" {
        val scratch = ScratchBuffers(blockFrames)

        scratch.useDouble { buf ->
            buf.size shouldBe blockFrames
        }
    }

    "nested useDouble calls return different buffers" {
        val scratch = ScratchBuffers(blockFrames)

        scratch.useDouble { outer ->
            scratch.useDouble { inner ->
                (outer !== inner) shouldBe true
            }
        }
    }

    "useDouble buffer is released after use block" {
        val scratch = ScratchBuffers(blockFrames)

        var firstRef: DoubleArray? = null
        scratch.useDouble { buf ->
            firstRef = buf
        }

        scratch.useDouble { buf ->
            (buf === firstRef) shouldBe true
        }
    }

    "useDouble and use pools are independent" {
        val scratch = ScratchBuffers(blockFrames)

        scratch.use { floatBuf ->
            scratch.useDouble { doubleBuf ->
                floatBuf.size shouldBe blockFrames
                doubleBuf.size shouldBe blockFrames
            }
        }
    }

    // ── oversample tests ────────────────────────────────────────────────────────

    "oversample(1) returns same instance" {
        val scratch = ScratchBuffers(blockFrames)
        (scratch.oversample(1) === scratch) shouldBe true
    }

    "oversample(0) returns same instance" {
        val scratch = ScratchBuffers(blockFrames)
        (scratch.oversample(0) === scratch) shouldBe true
    }

    "oversample(-1) returns same instance" {
        val scratch = ScratchBuffers(blockFrames)
        (scratch.oversample(-1) === scratch) shouldBe true
    }

    "oversample(2) returns ScratchBuffers with 2x blockFrames" {
        val scratch = ScratchBuffers(blockFrames)
        val os = scratch.oversample(2)

        os.use { buf ->
            buf.size shouldBe blockFrames * 2
        }
    }

    "oversample(4) returns ScratchBuffers with 4x blockFrames" {
        val scratch = ScratchBuffers(blockFrames)
        val os = scratch.oversample(4)

        os.use { buf ->
            buf.size shouldBe blockFrames * 4
        }
    }

    "oversample returns cached instance for same factor" {
        val scratch = ScratchBuffers(blockFrames)
        val os1 = scratch.oversample(2)
        val os2 = scratch.oversample(2)
        (os1 === os2) shouldBe true
    }

    "oversample returns different instances for different factors" {
        val scratch = ScratchBuffers(blockFrames)
        val os2 = scratch.oversample(2)
        val os4 = scratch.oversample(4)
        (os2 !== os4) shouldBe true
    }

    "oversampled ScratchBuffers also supports useDouble" {
        val scratch = ScratchBuffers(blockFrames)
        val os = scratch.oversample(2)

        os.useDouble { buf ->
            buf.size shouldBe blockFrames * 2
        }
    }
})
