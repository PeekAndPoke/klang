package io.peekandpoke.klang.audio_fe.samples

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class SoundFontZoneMetadataTest : StringSpec({

    fun zone(
        loopStart: Int = 0,
        loopEnd: Int = 0,
        sampleRate: Int = 22050,
        ahdsr: Boolean = false,
        anchor: Double = 0.0,
    ) = SoundfontIndex.SoundData.Zone(
        midi = 60,
        originalPitch = 6000.0,
        keyRangeLow = 0,
        keyRangeHigh = 127,
        loopStart = loopStart,
        loopEnd = loopEnd,
        coarseTune = 0,
        fineTune = 0,
        sampleRate = sampleRate,
        ahdsr = ahdsr,
        file = "",
        anchor = anchor,
    )

    "no loop when loopStart == loopEnd" {
        val meta = zone(loopStart = 100, loopEnd = 100).getSampleMetadata()
        meta.loop.shouldBeNull()
    }

    "no loop when loop duration < 50ms threshold" {
        // At 22050 Hz, 50ms = 1102.5 frames. Use 1000 frames = ~45ms
        val meta = zone(loopStart = 0, loopEnd = 1000, sampleRate = 22050).getSampleMetadata()
        meta.loop.shouldBeNull()
    }

    "loop present when loop duration >= 50ms" {
        // At 22050 Hz, 50ms = 1102.5 frames. Use 1200 frames = ~54ms
        val meta = zone(loopStart = 100, loopEnd = 1300, sampleRate = 22050).getSampleMetadata()
        meta.loop.shouldNotBeNull()
        // Loop points stored as seconds: 100/22050 ≈ 0.00454, 1300/22050 ≈ 0.05896
        meta.loop!!.startSec shouldBe (100.0 / 22050)
        meta.loop!!.endSec shouldBe (1300.0 / 22050)
    }

    "percussive ADSR when no sustain loop" {
        val meta = zone(loopStart = 0, loopEnd = 0).getSampleMetadata()
        meta.adsr.shouldNotBeNull()
        meta.adsr!!.sustain shouldBe 0.0
        meta.adsr!!.decay shouldBe 0.5
    }

    "sustain ADSR when loop is long enough" {
        val meta = zone(loopStart = 0, loopEnd = 5000, sampleRate = 22050).getSampleMetadata()
        meta.adsr.shouldNotBeNull()
        meta.adsr!!.sustain shouldBe 1.0
        meta.adsr!!.release shouldBe 0.2
    }

    "sustain ADSR when ahdsr flag is set even without loop" {
        val meta = zone(loopStart = 0, loopEnd = 0, ahdsr = true).getSampleMetadata()
        meta.adsr.shouldNotBeNull()
        meta.adsr!!.sustain shouldBe 1.0
    }

    "anchor is passed through" {
        val meta = zone(anchor = 1.5).getSampleMetadata()
        meta.anchor shouldBe 1.5
    }

    "custom minLoopLen threshold is respected" {
        // 500 frames at 22050 Hz = ~22ms. Default threshold (50ms) would reject, custom (20ms) accepts.
        val meta = zone(loopStart = 0, loopEnd = 500, sampleRate = 22050).getSampleMetadata(minLoopLen = 0.02)
        meta.loop.shouldNotBeNull()
    }
})
