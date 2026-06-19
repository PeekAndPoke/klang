package io.peekandpoke.klang.audio_be.filters

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.FilterDef
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Guards `createFormant` (the vowel filter): the formants must clearly DOMINATE the dry floor,
 * otherwise the vowel is inaudible (regression: when VOWEL_TAME was too small / VOWEL_FLOOR too
 * high, F1 was only +7 dB over the valley and F2 was below the floor). Also pins that a `body()`
 * after it does not erase the vowel.
 */
class FormantBlendSpec : StringSpec({

    val sr = 44100.0
    val n = 8192

    fun sine(freq: Double) = AudioBuffer(n) { i -> sin(2.0 * PI * freq * i / sr) }
    fun rms(b: AudioBuffer): Double = sqrt(b.fold(0.0) { a, v -> a + v * v } / b.size)

    // Soprano "u": F1 325, F2 700, then steep rolloff.
    val uBands = listOf(
        FilterDef.Formant.Band(325.0, 0.0, 80.0),
        FilterDef.Formant.Band(700.0, -16.0, 90.0),
        FilterDef.Formant.Band(2700.0, -35.0, 120.0),
        FilterDef.Formant.Band(3800.0, -40.0, 130.0),
        FilterDef.Formant.Band(4950.0, -60.0, 140.0),
    )
    val woodModes = listOf(
        FilterDef.Body.Mode(100.0, 3.0, 12.0), FilterDef.Body.Mode(200.0, 2.0, 11.0),
        FilterDef.Body.Mode(300.0, 1.0, 10.0), FilterDef.Body.Mode(430.0, 0.0, 9.0),
        FilterDef.Body.Mode(650.0, -1.0, 8.0), FilterDef.Body.Mode(900.0, -2.0, 7.0),
        FilterDef.Body.Mode(1300.0, -4.0, 6.0), FilterDef.Body.Mode(1900.0, -6.0, 5.0),
    )

    fun gainAt(freq: Double, filters: List<AudioFilter>): Double {
        val buf = sine(freq)
        val inR = rms(buf)
        filters.forEach { it.process(buf, 0, buf.size) }
        return rms(buf) / inR
    }

    "createFormant - vowel formants clearly dominate the floor" {
        val f1 = gainAt(325.0, listOf(LowPassHighPassFilters.createFormant(uBands, 1.0, sr)))
        val f2 = gainAt(700.0, listOf(LowPassHighPassFilters.createFormant(uBands, 1.0, sr)))
        val valley = gainAt(1200.0, listOf(LowPassHighPassFilters.createFormant(uBands, 1.0, sr)))

        f1 shouldBeGreaterThan (valley * 8.0)    // strong F1 (measured ~14×)
        f2 shouldBeGreaterThan (valley * 2.5)    // present F2 (measured ~3.9×)
    }

    "createFormant - a following body() does not erase the vowel" {
        val chain = {
            listOf(
                LowPassHighPassFilters.createFormant(uBands, 1.0, sr),
                LowPassHighPassFilters.createBody(woodModes, 0.5, sr),
            )
        }
        val f1 = gainAt(325.0, chain())
        val valley = gainAt(1200.0, chain())

        f1 shouldBeGreaterThan (valley * 8.0)    // vowel survives the body stage
    }
})
