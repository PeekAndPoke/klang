package io.peekandpoke.klang.audio_be.exciter

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

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

        var firstRef: FloatArray? = null
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

        var firstRef: FloatArray? = null
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
                a.fill(1.0f)
                b.fill(2.0f)

                // Verify they are independent
                for (i in 0 until blockFrames) {
                    a[i] shouldBe 1.0f
                    b[i] shouldBe 2.0f
                }
            }
        }
    }
})
