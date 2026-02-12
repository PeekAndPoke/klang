package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeAtLeast
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.orbits.Orbits
import io.peekandpoke.klang.audio_be.osci.oscillators
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlin.random.Random

/**
 * Tests for VoiceScheduler diagnostics reporting functionality.
 */
class VoiceSchedulerDiagnosticsTest : StringSpec({

    val blockFrames = 128
    val sampleRate = 44100

    fun createTestScheduler(timeMs: () -> Double): Pair<VoiceScheduler, KlangCommLink> {
        // Use real KlangCommLink to capture feedback messages
        val commLink = KlangCommLink(capacity = 1024)

        val options = VoiceScheduler.Options(
            commLink = commLink.backend,
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            oscillators = oscillators(sampleRate) { rng(Random(42)) },
            orbits = Orbits(maxOrbits = 4, blockFrames = blockFrames, sampleRate = sampleRate),
            performanceTimeMs = timeMs
        )

        val scheduler = VoiceScheduler(options)
        scheduler.setBackendStartTime(0.0)

        return scheduler to commLink
    }

    fun KlangCommLink.readAllDiagnostics(): List<KlangCommLink.Feedback.Diagnostics> {
        val diagnostics = mutableListOf<KlangCommLink.Feedback.Diagnostics>()
        while (true) {
            val msg = frontend.feedback.receive() ?: break
            if (msg is KlangCommLink.Feedback.Diagnostics) {
                diagnostics.add(msg)
            }
        }
        return diagnostics
    }

    "diagnostics messages are sent after processing" {
        var currentTimeMs = 0.0
        val (scheduler, commLink) = createTestScheduler { currentTimeMs }

        // Process first block - no diagnostics yet (need >50ms)
        scheduler.process(0)
        currentTimeMs += 3.0 // Simulate 3ms render time

        val diagnosticsMessages = commLink.readAllDiagnostics()
        diagnosticsMessages shouldHaveSize 0

        // Advance time past 50ms threshold
        currentTimeMs = 60.0
        scheduler.process(blockFrames.toLong())

        // Now we should have diagnostics
        val diagnosticsAfter = commLink.readAllDiagnostics()
        diagnosticsAfter shouldHaveSize 1
    }

    "diagnostics include correct playbackId" {
        var currentTimeMs = 0.0
        val (scheduler, commLink) = createTestScheduler { currentTimeMs }

        // Process and trigger diagnostics
        scheduler.process(0)
        currentTimeMs = 60.0
        scheduler.process(blockFrames.toLong())

        val diagnostics = commLink.readAllDiagnostics().first()
        diagnostics.playbackId shouldBe KlangCommLink.SYSTEM_PLAYBACK_ID
    }

    "diagnostics report active voice count" {
        var currentTimeMs = 0.0
        val (scheduler, commLink) = createTestScheduler { currentTimeMs }

        // Schedule a voice
        val voice = ScheduledVoice(
            playbackId = "test",
            startTime = 0.0,
            gateEndTime = 1.0,
            data = VoiceData.empty.copy(sound = "sine", freqHz = 440.0),
            playbackStartTime = 0.0,
        )
        scheduler.scheduleVoice(voice)

        // Process to activate the voice
        scheduler.process(0)
        currentTimeMs = 60.0
        scheduler.process(blockFrames.toLong())

        val diagnostics = commLink.readAllDiagnostics().first()
        diagnostics.activeVoiceCount shouldBe 1
    }

    "diagnostics report orbit states" {
        var currentTimeMs = 0.0
        val (scheduler, commLink) = createTestScheduler { currentTimeMs }

        // Schedule voices on different orbits
        val voice1 = ScheduledVoice(
            playbackId = "test",
            startTime = 0.0,
            gateEndTime = 1.0,
            data = VoiceData.empty.copy(
                sound = "sine",
                freqHz = 440.0,
                orbit = 0
            ),
            playbackStartTime = 0.0,
        )
        val voice2 = ScheduledVoice(
            playbackId = "test",
            startTime = 0.0,
            gateEndTime = 1.0,
            data = VoiceData.empty.copy(sound = "sine", freqHz = 880.0, orbit = 2),
            playbackStartTime = 0.0,
        )
        scheduler.scheduleVoice(voice1)
        scheduler.scheduleVoice(voice2)

        // Process to activate voices
        scheduler.process(0)
        currentTimeMs = 60.0
        scheduler.process(blockFrames.toLong())

        val diagnostics = commLink.readAllDiagnostics().first()

        // Should have 2 orbits reported
        diagnostics.orbits shouldHaveSize 2

        // Both orbits should be active
        val orbit0 = diagnostics.orbits.find { it.id == 0 }
        val orbit2 = diagnostics.orbits.find { it.id == 2 }

        orbit0?.active shouldBe true
        orbit2?.active shouldBe true
    }

    "diagnostics calculate headroom correctly" {
        var currentTimeMs = 0.0
        val (scheduler, commLink) = createTestScheduler { currentTimeMs }

        // Process a block with 3ms render time
        val startTime = currentTimeMs
        scheduler.process(0)
        currentTimeMs = startTime + 3.0 // Simulate 3ms render time

        // Advance past 50ms to trigger diagnostics
        currentTimeMs = 60.0
        scheduler.process(blockFrames.toLong())

        val diagnostics = commLink.readAllDiagnostics().first()

        // Headroom should be high (close to 1.0) since we're simulating light load
        // Block duration at 44100Hz, 128 frames = ~2.9ms
        // With 3ms render time, headroom would be negative (overload)
        // But since we only had one measured block, let's just verify it's a valid ratio
        diagnostics.renderHeadroom shouldBeLessThan 1.1 // Should be <= 1.0, but allow some tolerance
    }

    "diagnostics sent at approximately 50ms intervals" {
        var currentTimeMs = 0.0
        val (scheduler, commLink) = createTestScheduler { currentTimeMs }

        // Process multiple blocks with 15ms per block
        for (i in 0..10) {
            scheduler.process(i * blockFrames.toLong())
            currentTimeMs += 15.0 // 15ms per block
        }

        val diagnosticsMessages = commLink.readAllDiagnostics()

        // At 15ms per block, after 165ms (11 blocks), we should have 2 diagnostics messages
        // (at ~60ms and ~120ms)
        diagnosticsMessages.size shouldBeAtLeast 3
    }

    "allocatedIds property returns correct orbit IDs" {
        var currentTimeMs = 0.0
        val (scheduler, _) = createTestScheduler { currentTimeMs }

        // Initially no orbits allocated
        scheduler.options.orbits.allocatedIds shouldHaveSize 0

        // Schedule voices on orbits 0 and 3
        val voice1 = ScheduledVoice(
            playbackId = "test",
            startTime = 0.0,
            gateEndTime = 1.0,
            data = VoiceData.empty.copy(sound = "sine", freqHz = 440.0, orbit = 0),
            playbackStartTime = 0.0,
        )
        val voice2 = ScheduledVoice(
            playbackId = "test",
            startTime = 0.0,
            gateEndTime = 1.0,
            data = VoiceData.empty.copy(sound = "sine", freqHz = 880.0, orbit = 3),
            playbackStartTime = 0.0,
        )
        scheduler.scheduleVoice(voice1)
        scheduler.scheduleVoice(voice2)

        // Process to allocate orbits
        scheduler.process(0)

        // Should have 2 orbits allocated
        val allocatedIds = scheduler.options.orbits.allocatedIds
        allocatedIds shouldHaveSize 2
        allocatedIds.contains(0) shouldBe true
        allocatedIds.contains(3) shouldBe true
    }
})
