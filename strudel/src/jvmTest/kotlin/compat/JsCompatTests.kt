package io.peekandpoke.klang.strudel.compat

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.graal.GraalStrudelCompiler
import org.junit.jupiter.api.fail

class JsCompatTests : StringSpec() {

    val songs = JsCompatTestData.songs

    init {
        songs.forEachIndexed { index, (name, code) ->

            "Song ${index + 1}: $name" {

                val graalPattern = withClue("Compiling song '$name' with GraalVM") {
                    val result = try {
                        val graalCompiler = GraalStrudelCompiler()
                        graalCompiler.compile(code).await()
                    } catch (e: Throwable) {
                        fail("Failed to compile song '$name' with GraalVM", e)
                    }

                    result.shouldNotBeNull()
                }


                val nativePattern = withClue("Compiling song '$name' natively") {
                    StrudelPattern.compile(code)
                        ?: fail("Failed to compile song '$name' natively")
                }

                val graalArc = graalPattern.queryArc(0.0, 16.0)
                val nativeArc = nativePattern.queryArc(0.0, 16.0)

                assertSoftly {
                    withClue("Number of events must match") {
                        graalArc.size shouldBe nativeArc.size
                    }

                    val zippedArc = graalArc.zip(nativeArc)

                    zippedArc.forEachIndexed { index, (graal, native) ->
                        withClue("Event $index must be equal") {
                            graal shouldBe native
                        }
                    }
                }
            }
        }
    }
}
