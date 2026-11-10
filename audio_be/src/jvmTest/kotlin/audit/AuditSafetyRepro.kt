/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.audit

import io.kotest.core.spec.style.StringSpec
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.HouseStage
import io.peekandpoke.klang.audio_be.PlaybackEngineDispatcher
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.CylinderSwapRig
import io.peekandpoke.klang.audio_be.filters.LowPassHighPassFilters
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.buildExciter
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_be.probes.probeOutputFile
import io.peekandpoke.klang.audio_be.probes.writeProbeFile
import io.peekandpoke.klang.audio_be.voices.PlaybackCtx
import io.peekandpoke.klang.audio_be.voices.VoiceFactory
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleMetadata
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.classic
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.common.infra.toKlangMap
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The disproof pass of the engine audit (KLANG-00420), cluster "safety": bad numbers, huge values, crashes. Each test
 * MEASURES one finding (or a group) in the real engine and prints what it found; it asserts nothing about the engine's
 * values (a later fix turns a row into a guard). The report is `docs/audio-audit/2026-11-10/audit/disproof-safety.md`.
 *
 * **Out of the normal run**, like the probes: every test is disabled unless the environment holds `KLANG_PROBE=audit`.
 * Run it under the build lock:
 *
 *     KLANG_PROBE=audit console/with-build-lock.sh ./gradlew :audio_be:jvmTest --rerun \
 *         --tests io.peekandpoke.klang.audio_be.audit.AuditSafetyRepro
 *
 * The lines go to `<build dir>/probes/audit-safety.txt` ([probeOutputFile]), one block per test, each line starting
 * with the finding id.
 */
class AuditSafetyRepro : StringSpec({

    val requested = System.getenv("KLANG_PROBE") == "audit"
    val report = ArrayList<String>()

    fun line(text: String) {
        println(text)
        report.add(text)
    }

    fun flush() {
        writeProbeFile(target = probeOutputFile("audit-safety.txt"), text = report.joinToString(separator = "\n", postfix = "\n"), what = "audit safety")
    }

    // ── L1-01 L6-02 L6-01 L7b-01 L8-04: the NaN pan and the duck, end to end ─────────────────────────────────────

    "L1-01 L6-02 L6-01 L7b-01 L8-04: a NaN pan on a duck's source cylinder, through the dispatcher and the house stage"
        .config(enabled = requested) {
            line("== L1-01 L6-02 L6-01 L7b-01 L8-04: NaN pan, duck, house stage (dispatcher at $SR Hz, two playbacks)")

            for (scenario in nanDuckScenarios()) {
                val rig = DispatcherRig()
                val watch = MixWatch()
                val render = OutputTrace(seconds = scenario.seconds)

                for ((pid, voices) in scenario.playbacks) {
                    rig.send(KlangCommLink.Cmd.ScheduleVoices(playbackId = pid, voices = voices))
                }

                var lastActiveBassBlock = -1

                for (b in 0 until render.blocks) {
                    val out = rig.block()

                    render.take(block = b, out = out)
                    watch.observe(rig = rig, block = b, pids = scenario.playbacks.map { it.first })

                    val bassCylinder = rig.dispatcher.engine("song")?.cylinders?.cylinders?.firstOrNull { it.id == 0 }

                    if (bassCylinder != null && bassCylinder.isActive) {
                        lastActiveBassBlock = b
                    }
                }

                line(
                    "L6-01 [${scenario.name}] out: zero frames ${render.zeroFrames} (${fmt(render.zeroFrames / SR.toDouble())} s), " +
                        "longest exact-zero run ${render.longestRunText()}, non-finite out ${render.nonFinite}, rail (|x| = 1) ${render.rail}, " +
                        "largest step in the 2 blocks after the run ${fmt(render.stepAfterRun())}",
                )
                line("L6-01 [${scenario.name}] NaN in the cylinder mixes (first..last block, in s): ${watch.text()}")
                line("L6-01 [${scenario.name}] song's cylinder 0 last active at ${fmt((lastActiveBassBlock + 1) * BF / SR.toDouble())} s; ${scenario.note}")
            }

            flush()
        }

    // ── L7b-01: the latch across a chain swap ─────────────────────────────────────────────────────────────────────

    "L7b-01: the duck's latch across a chain swap, onto a ducking chain and onto one without a duck".config(enabled = requested) {
        line("== L7b-01: the latch across a swap (CylinderSwapRig, $SR Hz)")

        for (target in listOf("duckB", "dry")) {
            val rig = CylinderSwapRig(sampleRate = SR)

            rig.registry.register("duckA", KatalystDsl.of(duckStage(depth = 0.8)))
            rig.registry.register("duckB", KatalystDsl.of(KatalystStageDsl.Gain(gain = k(1.0)), duckStage(depth = 0.5)))
            rig.registry.register("dry", CylinderSwapRig.dryChain(1.0))
            rig.cylinder.requestChain("duckA")
            rig.render(blocks = 40, level = 0.5, sidechainLevel = 0.3)

            val nanBefore = countNan(rig.block(level = 0.5, sidechainLevel = 0.3))

            rig.block(level = 0.5, sidechainLevel = Double.NaN)

            var nanBlocksAfterPoison = 0

            repeat(40) {
                if (countNan(rig.block(level = 0.5, sidechainLevel = 0.3)) > 0) {
                    nanBlocksAfterPoison++
                }
            }

            rig.cylinder.requestChain(target)

            var lastNanBlock = -1
            val blocks = 600

            for (b in 0 until blocks) {
                if (countNan(rig.block(level = 0.5, sidechainLevel = 0.3)) > 0) {
                    lastNanBlock = b
                }
            }

            val verdict = when {
                lastNanBlock == blocks - 1 -> "NaN to the end of the run ($blocks blocks after the request): the latch CARRIED over"
                lastNanBlock < 0 -> "finite from the first block after the request"
                else -> "finite from block ${lastNanBlock + 1} after the request (${fmt((lastNanBlock + 1) * BF / SR.toDouble())} s; the fade is ${rig.fadeBlocks} blocks)"
            }

            line("L7b-01 [swap duckA -> $target] NaN samples before the poison $nanBefore; NaN blocks in the 40 clean blocks after one NaN sidechain block: $nanBlocksAfterPoison/40; after the swap: $verdict")
        }

        flush()
    }

    // ── L6-07: the cull and a voice that is NaN ───────────────────────────────────────────────────────────────────

    "L6-07: the cull and a voice that is NaN from its first sample, from mid-gate, from its release".config(enabled = requested) {
        line("== L6-07: VoiceTestHelpers.createVoice, gate 0.05 s, release tail 5 s, default cull, $SR Hz")

        val gateFrames = (0.05 * SR).toInt()
        val endFrames = gateFrames + 5 * SR

        val cases = listOf(
            "NaN from frame 0" to PoisonAfter(cleanFrames = 0, after = Double.NaN),
            "0.5 then NaN from mid-gate" to PoisonAfter(cleanFrames = gateFrames / 2, after = Double.NaN),
            "0.5 then NaN from 0.1 s into the release" to PoisonAfter(cleanFrames = gateFrames + SR / 10, after = Double.NaN),
            "control: 0.5 then exact 0 from 0.1 s into the release" to PoisonAfter(cleanFrames = gateFrames + SR / 10, after = 0.0),
            "control: 0 from frame 0 (never heard)" to PoisonAfter(cleanFrames = 0, after = 0.0),
        )

        for ((name, signal) in cases) {
            val voice = VoiceTestHelpers.createVoice(
                startFrame = 0.0,
                gateEndFrame = gateFrames.toDouble(),
                endFrame = endFrames.toDouble(),
                sampleRate = SR,
                blockFrames = BF,
                signal = signal,
            )
            val ctx = VoiceTestHelpers.createContext(blockStart = 0.0, blockFrames = BF, sampleRate = SR)
            var block = 0

            while (block * BF < endFrames + 4 * BF) {
                ctx.blockStart = (block * BF).toDouble()

                val alive = voice.render(ctx)

                ctx.cylinders.clearAll()
                block++

                if (!alive) {
                    break
                }
            }

            val endSec = block * BF / SR.toDouble()

            line("L6-07 [$name] the voice ended after ${fmt(endSec)} s (scheduled end ${fmt(endFrames / SR.toDouble())} s), culled=${voice.culled}")
        }

        flush()
    }

    // ── L1-02: a sample voice with a non-finite or zero pitch ─────────────────────────────────────────────────────

    "L1-02: a sample voice with a NaN, infinite or zero pitch".config(enabled = requested) {
        line("== L1-02: sample voices through the dispatcher (a 1 s 0.5 sine sample at 261.63 Hz, and the same with pitchHz 0)")

        val cases = listOf(
            Triple("tone", 261.63, "control: freq = the sample's pitch"),
            Triple("tone", Double.NaN, "freqHz NaN"),
            Triple("tone", Double.POSITIVE_INFINITY, "freqHz +Inf"),
            Triple("tone", 0.0, "freqHz 0 on a sample with pitch 261.63"),
            Triple("tone0", 0.0, "freqHz 0 on a sample whose pitchHz is 0"),
            Triple("tone0", 440.0, "freqHz 440 on a sample whose pitchHz is 0"),
        )

        for ((sound, freq, label) in cases) {
            val rig = DispatcherRig()

            rig.send(sampleComplete(sound = "tone", pitchHz = 261.63, pcm = sinePcm(seconds = 1.0, hz = 261.63)))
            rig.send(sampleComplete(sound = "tone0", pitchHz = 0.0, pcm = sinePcm(seconds = 1.0, hz = 261.63)))
            rig.send(
                KlangCommLink.Cmd.ScheduleVoices(
                    playbackId = "song",
                    voices = listOf(scheduled(pid = "song", sound = sound, freqHz = freq, startSec = 0.1, gateEndSec = 0.5)),
                ),
            )

            val watch = MixWatch()
            val trace = OutputTrace(seconds = 0.8)

            for (b in 0 until trace.blocks) {
                trace.take(block = b, out = rig.block())
                watch.observe(rig = rig, block = b, pids = listOf("song"))
            }

            val engine = rig.dispatcher.engine("song")

            line(
                "L1-02 [$label] NaN in the mix: ${watch.text()}; output peak ${fmt(trace.peak)}, exact-zero frames in 0.11..0.5 s ${trace.zeroBetween(0.11, 0.5)}; " +
                    "soundless ${engine?.scheduler?.soundlessVoiceCount()}",
            )
        }

        flush()
    }

    // ── L1-04 L1-05: non-finite start times ───────────────────────────────────────────────────────────────────────

    "L1-04 L1-05: non-finite start times in the scheduler".config(enabled = requested) {
        line("== L1-04: one batch of starts 5.0, NaN, 9.0, 0.6 (0.2 s gates), dispatcher at $SR Hz")

        for (withNan in listOf(true, false)) {
            val rig = DispatcherRig()
            val starts = if (withNan) listOf(5.0, Double.NaN, 9.0, 0.6) else listOf(5.0, 9.0, 0.6)

            rig.send(
                KlangCommLink.Cmd.ScheduleVoices(
                    playbackId = "song",
                    voices = starts.map { scheduled(pid = "song", sound = "sine", freqHz = 440.0, startSec = it, gateEndSec = it + 0.2) },
                ),
            )

            val trace = OutputTrace(seconds = 5.5)
            var activeAt07 = -1

            for (b in 0 until trace.blocks) {
                trace.take(block = b, out = rig.block())

                if (b == (0.7 * SR).toInt() / BF) {
                    activeAt07 = rig.dispatcher.engine("song")?.scheduler?.getActiveVoiceCount() ?: -1
                }
            }

            line(
                "L1-04 [${if (withNan) "with the NaN start" else "control, no NaN"}] active voices at 0.7 s: $activeAt07, RMS 0.62..0.78 s ${fmt(trace.rms(0.62, 0.78))}, " +
                    "dropped by 5.5 s: ${rig.dispatcher.engine("song")?.scheduler?.droppedVoiceCount()}",
            )
        }

        line("== L1-05: a non-finite playbackStartTime on a playback's first voice")

        for (epoch in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0)) {
            val rig = DispatcherRig()

            rig.send(
                KlangCommLink.Cmd.ScheduleVoices(
                    playbackId = "song",
                    voices = listOf(
                        scheduled(pid = "song", sound = "sine", freqHz = 440.0, startSec = 0.2, gateEndSec = 0.4, playbackStartTime = epoch),
                        scheduled(pid = "song", sound = "sine", freqHz = 660.0, startSec = 0.3, gateEndSec = 0.5),
                    ),
                ),
            )

            val trace = OutputTrace(seconds = 1.0)

            for (b in 0 until trace.blocks) {
                trace.take(block = b, out = rig.block())
            }

            val anchored = rig.feedback.filterIsInstance<KlangCommLink.Feedback.PlaybackAnchored>().map { it.epochSec }

            line(
                "L1-05 [playbackStartTime $epoch] anchored epochs $anchored, RMS 0.2..0.5 s ${fmt(trace.rms(0.2, 0.5))}, " +
                    "dropped ${rig.dispatcher.engine("song")?.scheduler?.droppedVoiceCount()}",
            )
        }

        flush()
    }

    // ── L2-04 L8-03 L9-01: the hunt for a throw site, part 1 ─────────────────────────────────────────────────────

    "L2-04 L8-03 L9-01: a gate that ends before the onset, over every built-in sound and authored note clocks".config(enabled = requested) {
        line("== L2-04: VoiceFactory.makeVoice + Voice.render (the scheduler's promotion path), adsr.release 0, $SR Hz")

        val registry = huntRegistry()
        val rig = HuntRig(registry)
        val names = registry.names().sorted()
        val windows = listOf(100 to -24, 127 to -1, 127 to -100, 64 to -63, 10 to -24)
        val throws = ArrayList<String>()
        var renders = 0
        var negativeWindows = 0

        for (name in names) {
            for (freq in listOf(220.0, 30000.0, Double.NaN)) {
                for ((onset, gate) in windows) {
                    val result = renderNegativeWindow(rig = rig, sound = name, freqHz = freq, onsetOffset = onset, gateFrames = gate)

                    renders++

                    if (result.window < 0) {
                        negativeWindows++
                    }

                    if (result.thrown != null) {
                        throws.add("$name freq=$freq onset+$onset gate$gate window ${result.window}: ${result.thrown}")
                    }
                }
            }
        }

        line("L2-04 voices rendered $renders over ${names.size} sounds; with a negative window in their first block: $negativeWindows; throws: ${throws.size}")

        val bySite = throws.groupBy { it.substringAfter(": ") }

        for ((site, cases) in bySite) {
            line("L2-04 THROW ${cases.size}x $site; e.g. ${cases.take(4).joinToString(" | ") { it.substringBefore(": ") }}")
        }

        val throwingSounds = throws.map { it.substringBefore(" freq=") + " " + it.substringAfter("freq=").substringBefore(" ") }.distinct()

        line("L2-04 sound and freq pairs that threw: ${throwingSounds.joinToString(", ")}")

        // Through the LIVE dispatcher, beside a second playback: the built-in `silence` (the dispatcher knows it), and an
        // authored Progress tree registered as a song would register it (Cmd.RegisterIgnitor)
        val liveCases = listOf(
            "silence (built-in), adsr.release 0" to null,
            "audit_progress (RegisterIgnitor)" to IgnitorRegistry.builtInVoice(IgnitorDsl.Times(IgnitorDsl.Sine(), IgnitorDsl.Progress)),
        )

        for ((label, dsl) in liveCases) {
            val live = DispatcherRig()
            val sound = if (dsl == null) "silence" else "audit_progress"

            live.send(KlangCommLink.Cmd.ScheduleVoices(playbackId = "other", voices = listOf(scheduled(pid = "other", sound = "sine", freqHz = 440.0, startSec = 0.0, gateEndSec = 2.0))))

            if (dsl != null) {
                live.send(KlangCommLink.Cmd.RegisterIgnitor(playbackId = "song", name = sound, dsl = dsl))
            }

            val start = (1280 + 100 + 0.25) / SR
            val gateEnd = (1280 + 100 - 24 + 0.25) / SR

            live.send(
                KlangCommLink.Cmd.ScheduleVoices(
                    playbackId = "song",
                    voices = listOf(
                        scheduled(pid = "song", sound = sound, freqHz = 220.0, startSec = start, gateEndSec = gateEnd, ignitorParams = mapOf("adsr.release" to 0.0)),
                    ),
                ),
            )

            val outcomes = ArrayList<String>()
            var heardAfter = 0.0

            for (b in 0 until 40) {
                val thrown = catching { live.block() }

                if (thrown != null) {
                    outcomes.add("block $b THREW $thrown (active voices of the song after it: ${live.dispatcher.engine("song")?.scheduler?.getActiveVoiceCount()})")
                } else if (b > 12) {
                    heardAfter = maxOf(heardAfter, live.out.left.maxOf { abs(it) })
                }
            }

            line(
                "L8-03 L9-01 [$label] through PlaybackEngineDispatcher.renderBlock beside a 440 Hz playback: " +
                    "${if (outcomes.isEmpty()) "no throw" else outcomes.joinToString("; ")}; the other playback's peak in blocks 13..39 when the dispatcher is called again: ${fmt(heardAfter)}",
            )
        }

        flush()
    }

    // ── L8-03 L9-01: the hunt, part 2: every slot at extreme values ───────────────────────────────────────────────

    "L8-03 L9-01: every Ignitor slot at extreme values over every built-in sound".config(enabled = requested) {
        line("== L8-03 L9-01: every slot of every built-in at NaN, +-Inf, +-1e308, -1, 0, 1e9, 1e-300 (makeVoice + 6 blocks)")

        val registry = huntRegistry()
        val names = registry.names().sorted()
        val values = listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1e308, -1e308, -1.0, 0.0, 1e9, 1e-300)
        val throws = ArrayList<String>()
        val nanFromFinite = ArrayList<String>()
        var renders = 0

        for (name in names) {
            val dsl = registry.get(name) ?: continue
            val params = ArrayList<IgnitorDsl.Param>()
            val rig = HuntRig(registry)

            dsl.collectParams(params)

            for (slot in params.map { it.name }.distinct()) {
                for (value in values) {
                    val result = renderSlot(rig = rig, sound = name, slot = slot, value = value)

                    renders++

                    if (result.thrown != null) {
                        throws.add("$name $slot=$value: ${result.thrown}")
                    }

                    if (value.isFinite() && result.nanBlocks > 0) {
                        nanFromFinite.add("$name $slot=$value (${result.nanBlocks} NaN blocks)")
                    }
                }
            }
        }

        line("L9-01 slot renders $renders; throws ${throws.size}")

        for ((site, cases) in throws.groupBy { it.substringAfter(": ") }) {
            line("L9-01 THROW ${cases.size}x $site; e.g. ${cases.take(4).joinToString(" | ") { it.substringBefore(": ") }}")
        }

        line("L6-01 NaN in the cylinder mix from a FINITE slot value: ${nanFromFinite.size} cases")

        for (case in nanFromFinite.take(60)) {
            line("L6-01 NaN source: $case")
        }

        flush()
    }

    // ── L8-03 L9-01: the hunt, part 3: Katalyst chains and cylinder slots ─────────────────────────────────────────

    "L8-03 L9-01: Katalyst chains and cylinder slots with odd arguments, through the dispatcher".config(enabled = requested) {
        line("== L8-03 L9-01: authored chains (cylinder and master) and the 27 cylinder slots at odd values, dispatcher at $SR Hz")

        val values = listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1e308, -1e308, -1.0, 0.0, 1e9)
        val chains = oddChains(values)
        val throws = ArrayList<String>()
        val nonFinite = ArrayList<String>()
        var runs = 0
        var rig = DispatcherRig()

        for ((label, dsl) in chains) {
            for (position in listOf("cylinder", "master")) {
                val pid = "p$runs"
                val result = runChainScenario(rig = rig, pid = pid, dsl = dsl, position = position)

                runs++

                if (result.thrown != null) {
                    throws.add("$position $label: ${result.thrown}")
                    rig = DispatcherRig()
                } else if (result.nonFiniteBlocks > 0) {
                    nonFinite.add("$position $label (${result.nonFiniteBlocks} blocks with a non-finite cylinder sample, output zero frames ${result.zeroFrames})")
                }
            }
        }

        val slotBases = cylinderSlotBases()

        for ((key, base) in slotBases) {
            for (value in values) {
                val pid = "p$runs"
                val result = runSlotScenario(rig = rig, pid = pid, katalystParams = base + (key to value))

                runs++

                if (result.thrown != null) {
                    throws.add("slot $key=$value: ${result.thrown}")
                    rig = DispatcherRig()
                } else if (value.isFinite() && result.nonFiniteBlocks > 0) {
                    nonFinite.add("slot $key=$value (${result.nonFiniteBlocks} blocks)")
                }
            }
        }

        line("L9-01 chain and slot runs $runs; throws ${throws.size}")

        for ((site, cases) in throws.groupBy { it.substringAfter(": ") }) {
            line("L9-01 THROW ${cases.size}x $site; e.g. ${cases.take(5).joinToString(" | ") { it.substringBefore(": ") }}")
        }

        line("L6-01 chains or finite slot values that put a non-finite sample in a cylinder mix: ${nonFinite.size}")

        for (case in nonFinite.take(80)) {
            line("L6-01 non-finite: $case")
        }

        flush()
    }

    // ── L8-03 L9-01: the hunt, part 4: odd samples ────────────────────────────────────────────────────────────────

    "L8-03 L9-01: odd samples and odd sample slots on the wire".config(enabled = requested) {
        line("== L8-03 L9-01: Sample.Complete and the sample slots at odd values, dispatcher at $SR Hz")

        val cases = ArrayList<Pair<String, () -> List<KlangCommLink.Cmd>>>()

        fun sampleCase(label: String, sample: MonoSamplePcm, pitchHz: Double = 261.63, slots: Map<String, Double>? = null) {
            cases.add(
                label to {
                    listOf(
                        KlangCommLink.Cmd.Sample.Complete(req = request("odd"), note = null, pitchHz = pitchHz, sample = sample),
                        KlangCommLink.Cmd.ScheduleVoices(
                            playbackId = "song",
                            voices = listOf(scheduled(pid = "song", sound = "odd", freqHz = 261.63, startSec = 0.05, gateEndSec = 0.3, ignitorParams = slots)),
                        ),
                    )
                },
            )
        }

        sampleCase("pcm size 0", MonoSamplePcm(sampleRate = SR, pcm = DoubleArray(0)))
        sampleCase("pcm size 1", MonoSamplePcm(sampleRate = SR, pcm = DoubleArray(1) { 0.5 }))
        sampleCase("pcm size 2", MonoSamplePcm(sampleRate = SR, pcm = DoubleArray(2) { 0.5 }))
        sampleCase("sampleRate 0", MonoSamplePcm(sampleRate = 0, pcm = sinePcm(0.5, 261.63)))
        sampleCase("sampleRate -48000", MonoSamplePcm(sampleRate = -SR, pcm = sinePcm(0.5, 261.63)))
        sampleCase("pitchHz NaN", MonoSamplePcm(sampleRate = SR, pcm = sinePcm(0.5, 261.63)), pitchHz = Double.NaN)
        sampleCase("pitchHz -1", MonoSamplePcm(sampleRate = SR, pcm = sinePcm(0.5, 261.63)), pitchHz = -1.0)
        sampleCase("pcm holds NaN", MonoSamplePcm(sampleRate = SR, pcm = DoubleArray(SR / 2) { if (it == 100) Double.NaN else 0.5 }))
        sampleCase(
            "meta loop NaN..1",
            MonoSamplePcm(sampleRate = SR, pcm = sinePcm(0.5, 261.63), meta = SampleMetadata(anchor = 0.0, loop = SampleMetadata.LoopRange(startSec = Double.NaN, endSec = 1.0), adsr = null)),
        )
        sampleCase(
            "meta loop 0.2..0.1 (inverted)",
            MonoSamplePcm(sampleRate = SR, pcm = sinePcm(0.5, 261.63), meta = SampleMetadata(anchor = 0.0, loop = SampleMetadata.LoopRange(startSec = 0.2, endSec = 0.1), adsr = null)),
        )

        for (slot in listOf("begin", "end", "speed", "loop")) {
            for (value in listOf(-1e308, -1.0, 0.0, 0.999999, 1e308, 2.0)) {
                sampleCase("slot $slot=$value", MonoSamplePcm(sampleRate = SR, pcm = sinePcm(0.5, 261.63)), slots = mapOf(slot to value, "loop" to 1.0).let { if (slot == "loop") mapOf(slot to value) else it })
            }
        }

        sampleCase("begin 0.9 end 0.1 loop 1", MonoSamplePcm(sampleRate = SR, pcm = sinePcm(0.5, 261.63)), slots = mapOf("begin" to 0.9, "end" to 0.1, "loop" to 1.0))
        sampleCase("begin 0.5 end 0.5 loop 1", MonoSamplePcm(sampleRate = SR, pcm = sinePcm(0.5, 261.63)), slots = mapOf("begin" to 0.5, "end" to 0.5, "loop" to 1.0))

        val throws = ArrayList<String>()

        for ((label, commands) in cases) {
            val rig = DispatcherRig()
            var thrown: String? = null

            for (cmd in commands()) {
                thrown = thrown ?: catching { rig.send(cmd) }
            }

            for (b in 0 until (0.5 * SR).toInt() / BF) {
                thrown = thrown ?: catching { rig.block() }
            }

            if (thrown != null) {
                throws.add("$label: $thrown")
            }
        }

        // A chunk whose offset runs past the announced size: not song input (the frontend's uploader writes it), listed for the record
        val chunkRig = DispatcherRig()
        val chunkThrow = catching {
            chunkRig.send(
                KlangCommLink.Cmd.Sample.Chunk(
                    req = request("chunk"), note = null, pitchHz = 261.63, sampleRate = SR, meta = SampleMetadata.default,
                    totalSize = 100, isLastChunk = true, chunkOffset = 90, data = DoubleArray(20),
                ),
            )
        }

        line("L9-01 odd sample cases ${cases.size}; throws ${throws.size}${throws.joinToString("") { "\nL9-01 THROW $it" }}")
        line("L9-01 internal only (the uploader's message, not song input): a Sample.Chunk with offset 90 + 20 frames into a 100-frame sample: ${chunkThrow ?: "no throw"}")

        flush()
    }

    // ── L4-01: the saturating SVF ─────────────────────────────────────────────────────────────────────────────────

    "L4-01: the saturating SVF, how long it holds after one sample of size V".config(enabled = requested) {
        line("== L4-01 hold: one input sample of V at frame 0, then silence; frames until |out| stays under 1e-3; lowpass q 0.707 (cap 120 s)")

        for (fc in listOf(1000.0, 8000.0)) {
            for (analog in listOf(0.0, 1.0)) {
                val cells = ArrayList<String>()

                for (v in listOf(1.0, 1e2, 1e3, 1e4, 2e4, 3e4, 1e5)) {
                    val tree = IgnitorDsl.Lowpass(inner = IgnitorDsl.Sample, freq = c(fc), q = c(0.707), analog = c(analog))
                    val settled = settle(tree = tree, source = FunctionSource { n -> if (n == 0) v else 0.0 }, inputEndFrame = 1, floor = 1e-3, capSeconds = 120.0)

                    cells.add("V=${fmtE(v)}: ${settled.text()}")
                }

                line("L4-01 hold lowpass fc=$fc analog=$analog: ${cells.joinToString("; ")}")
            }
        }

        line("== L4-01 the auditor's case: a 10 ms DC step of V, then silence; lowpass 1000 Hz q 0.707; frames until |out| stays under 1e-3")

        for (analog in listOf(0.0, 1.0)) {
            val cells = ArrayList<String>()

            for (v in listOf(1.0, 1e2, 1e3, 1e4)) {
                val tree = IgnitorDsl.Lowpass(inner = IgnitorDsl.Sample, freq = c(1000.0), q = c(0.707), analog = c(analog))
                val stepFrames = SR / 100
                val settled = settle(tree = tree, source = FunctionSource { n -> if (n < stepFrames) v else 0.0 }, inputEndFrame = stepFrames, floor = 1e-3, capSeconds = 120.0)

                cells.add("V=${fmtE(v)}: ${settled.text()}")
            }

            line("L4-01 step analog=$analog: ${cells.joinToString("; ")}")
        }

        flush()
    }

    "L4-01: the saturating SVF under ordinary loud signals at its resonance".config(enabled = requested) {
        line("== L4-01 reach: 1 s of a periodic input at level L, then silence; frames until |out| stays under 1e-5 (cap 30 s), analog against 0")

        val shapes = listOf("sine at fc", "naive saw at fc/3")
        val worst = ArrayList<Pair<Double, String>>()

        for (mode in listOf("lowpass", "highpass")) {
            for (fc in listOf(1000.0, 8000.0, 16000.0)) {
                for (q in listOf(0.707, 5.0, 20.0, 200.0)) {
                    for (shape in shapes) {
                        val cells = ArrayList<String>()

                        for (level in listOf(1.0, 4.0, 16.0)) {
                            val tails = ArrayList<String>()
                            var linearTail = 0

                            for (analog in listOf(0.0, 1.0, 5.0, 15.0)) {
                                val tree = if (mode == "lowpass") {
                                    IgnitorDsl.Lowpass(inner = IgnitorDsl.Sample, freq = c(fc), q = c(q), analog = c(analog))
                                } else {
                                    IgnitorDsl.Highpass(inner = IgnitorDsl.Sample, freq = c(fc), q = c(q), analog = c(analog))
                                }
                                val f0 = if (shape == "sine at fc") fc else fc / 3.0
                                val source = FunctionSource { n ->
                                    if (n >= SR) {
                                        0.0
                                    } else if (shape == "sine at fc") {
                                        level * sin(2.0 * PI * f0 * n / SR)
                                    } else {
                                        val x = f0 * n / SR

                                        level * (2.0 * (x - floor(x)) - 1.0)
                                    }
                                }
                                val settled = settle(tree = tree, source = source, inputEndFrame = SR, floor = 1e-5, capSeconds = 30.0)

                                if (analog == 0.0) {
                                    linearTail = settled.frames
                                } else {
                                    val ratio = if (settled.frames < 0) Double.POSITIVE_INFINITY else settled.frames.toDouble() / maxOf(1, linearTail)

                                    worst.add(ratio to "$mode fc=$fc q=$q $shape L=$level analog=$analog: ${settled.text()} (linear ${linearTail} frames)")
                                }

                                tails.add("a$analog ${settled.short()}")
                            }

                            cells.add("L=$level [${tails.joinToString(", ")}]")
                        }

                        line("L4-01 reach $mode fc=$fc q=$q $shape: ${cells.joinToString("; ")}")
                    }
                }
            }
        }

        val top = worst.sortedByDescending { it.first }.take(8)

        for ((ratio, text) in top) {
            line("L4-01 reach, longest tails re the linear filter: x${if (ratio.isInfinite()) "inf" else fmt(ratio)} $text")
        }

        flush()
    }

    // ── N1 (new): the analog SVF near fs/3 ───────────────────────────────────────────────────────────────────────

    "N1 (new) L4-01: the analog SVF near fs/3 keeps oscillating after its input".config(enabled = requested) {
        line("== N1: one SVF node, 0.5 s of input then 10 s of silence; RMS of the last 0.5 s (0 = it settled)")

        for (mode in listOf("lowpass", "highpass")) {
            for (analog in listOf(0.0, 1.0, 3.0, 5.0, 15.0)) {
                val cells = ArrayList<String>()

                for (fc in listOf(8000.0, 12000.0, 15900.0, 15990.0, 16000.0, 16010.0, 16100.0, 20000.0)) {
                    val out = renderTree(tree = svf(mode = mode, fc = fc, q = 200.0, analog = analog), frames = SR / 2 + 10 * SR) { n ->
                        if (n < SR / 2) 0.1 * sin(2.0 * PI * fc * n / SR) else 0.0
                    }

                    cells.add("${fc.toInt()}:${fmtE(tailRms(out = out, seconds = 0.5, sampleRate = SR))}")
                }

                line("N1 [$mode q=200 analog=$analog, input a 0.1 sine at fc, 48 kHz] tail RMS by fc: ${cells.joinToString(" ")}")
            }
        }

        for (q in listOf(50.0, 100.0, 150.0, 200.0)) {
            val cells = ArrayList<String>()

            for (analog in listOf(3.0, 5.0, 10.0, 15.0)) {
                val out = renderTree(tree = svf(mode = "lowpass", fc = 16000.0, q = q, analog = analog), frames = SR / 2 + 10 * SR) { n ->
                    if (n < SR / 2) 0.1 * sin(2.0 * PI * 16000.0 * n / SR) else 0.0
                }

                cells.add("a$analog:${fmtE(tailRms(out = out, seconds = 0.5, sampleRate = SR))}")
            }

            line("N1 [lowpass fc=16000 q=$q] tail RMS by analog: ${cells.joinToString(" ")}")
        }

        val rate441 = 44_100
        val at441 = renderTree(tree = svf(mode = "lowpass", fc = 14700.0, q = 200.0, analog = 5.0), frames = rate441 / 2 + 10 * rate441, sampleRate = rate441) { n ->
            if (n < rate441 / 2) 0.1 * sin(2.0 * PI * 14700.0 * n / rate441) else 0.0
        }

        line("N1 [lowpass fc=14700 (fs/3) q=200 analog=5 at 44.1 kHz] tail RMS ${fmtE(tailRms(out = at441, seconds = 0.5, sampleRate = rate441))}")

        val noise = Random(11)
        val programs = listOf(
            "sine 110 Hz 0.5" to { n: Int -> 0.5 * sin(2.0 * PI * 110.0 * n / SR) },
            "naive saw 110 Hz 0.5" to { n: Int ->
                val x = 110.0 * n / SR

                0.5 * (2.0 * (x - floor(x)) - 1.0)
            },
            "white noise 0.3" to { _: Int -> 0.3 * (2.0 * noise.nextDouble() - 1.0) },
            "one impulse of 0.05" to { n: Int -> if (n == 0) 0.05 else 0.0 },
        )

        for ((label, program) in programs) {
            val out = renderTree(tree = svf(mode = "lowpass", fc = 16000.0, q = 200.0, analog = 5.0), frames = SR / 2 + 10 * SR) { n ->
                if (n < SR / 2) program(n) else 0.0
            }

            line("N1 [lowpass fc=16000 q=200 analog=5, input: $label for 0.5 s] tail RMS ${fmtE(tailRms(out = out, seconds = 0.5, sampleRate = SR))}")
        }

        // Inside a built-in voice: classic()'s lowpass at 16 kHz, q 200, analog 5, against analog 0; the 16 kHz tone in the
        // cylinder mix over 1.0 to 1.9 s of a 2 s note (a single-bin DFT), and the RMS of the whole voice there
        val registry = IgnitorRegistry().apply { registerDefaults() }

        for (sound in listOf("saw", "sine", "supersaw", "square", "white")) {
            val cells = ArrayList<String>()

            for (analog in listOf(0.0, 5.0)) {
                val mix = renderVoiceMix(
                    registry = registry,
                    sound = sound,
                    freqHz = 110.0,
                    seconds = 2.2,
                    gateSeconds = 2.0,
                    ignitorParams = mapOf("lpf.freq" to 16000.0, "lpf.q" to 200.0, "analog" to analog),
                )
                val from = SR
                val to = (1.9 * SR).toInt()

                cells.add("analog $analog: 16 kHz amplitude ${fmtE(toneAmplitude(mix = mix, hz = 16000.0, from = from, to = to))}, RMS ${fmt(rmsOf(mix = mix, from = from, to = to))}")
            }

            line("N1 [built-in $sound at 110 Hz, lpf 16000 q 200, a 2 s note] ${cells.joinToString("; ")}")
        }

        flush()
    }

    "N1 (new): the fs/3 oscillation inside a voice whose input falls silent before the note ends".config(enabled = requested) {
        line("== N1 reach: through the dispatcher, lpf.freq 16000, lpf.q 200, analog 0 against 5")

        val noise = Random(5)
        val burst = DoubleArray(SR / 10) { 0.3 * (2.0 * noise.nextDouble() - 1.0) }

        for (analog in listOf(0.0, 5.0)) {
            val slots = mapOf("lpf.freq" to 16000.0, "lpf.q" to 200.0, "analog" to analog)
            val cases = listOf(
                Triple("a 0.1 s noise sample in a 1 s note", scheduled(pid = "song", sound = "burst", freqHz = 261.63, startSec = 0.0, gateEndSec = 1.0, ignitorParams = slots), 0.3 to 0.9),
                Triple("a pluck at 220 Hz, 2 s note", scheduled(pid = "song", sound = "pluck", freqHz = 220.0, startSec = 0.0, gateEndSec = 2.0, ignitorParams = slots), 1.0 to 1.9),
            )

            for ((label, voice, window) in cases) {
                val rig = DispatcherRig()

                rig.send(sampleComplete(sound = "burst", pitchHz = 261.63, pcm = burst))
                rig.send(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(voice)))

                val trace = OutputTrace(seconds = 2.5)

                for (b in 0 until trace.blocks) {
                    trace.take(block = b, out = rig.block())
                }

                val (from, to) = window
                val amplitude = toneAmplitude(mix = trace.left, hz = 16000.0, from = (from * SR).toInt(), to = (to * SR).toInt())

                line("N1 reach [analog $analog, $label] output over ${fmt(from)}..${fmt(to)} s: 16 kHz amplitude ${fmtE(amplitude)}, RMS ${fmt(trace.rms(from, to))}")
            }
        }

        flush()
    }

    // ── L1-02: oscillator voices with a non-finite or zero pitch ──────────────────────────────────────────────────

    "L1-02: every built-in oscillator with a NaN, infinite, zero or negative freqHz".config(enabled = requested) {
        line("== L1-02: every built-in through makeVoice + 20 blocks (gate 10 blocks); blocks whose cylinder mix held a NaN")

        val registry = IgnitorRegistry().apply { registerDefaults() }
        val names = registry.names().sorted()

        for (freq in listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -220.0)) {
            val withNan = ArrayList<String>()
            var silent = 0

            for (name in names) {
                val mix = renderVoiceMix(registry = registry, sound = name, freqHz = freq, seconds = 20.0 * BF / SR, gateSeconds = 10.0 * BF / SR, ignitorParams = null)
                val nan = mix.count { it.isNaN() }

                if (nan > 0) {
                    withNan.add("$name ($nan NaN samples)")
                } else if (mix.all { it == 0.0 }) {
                    silent++
                }
            }

            line("L1-02 [oscillators, freqHz $freq] ${names.size} sounds: NaN in the mix from ${withNan.size}${if (withNan.isEmpty()) "" else ": " + withNan.joinToString(", ")}; exact silence from $silent")
        }

        flush()
    }

    // ── L8-01 L8-02: the house stage under huge values ────────────────────────────────────────────────────────────

    "L8-01: the house stage after one block of a huge value on a 0.3 sine".config(enabled = requested) {
        line("== L8-01: HouseStage alone, 0.3 sine at 220 Hz, one block (128 frames) at frame 4800 replaced by sine + V; compared with the clean run")

        val seconds = 4.0
        val control = runHouse(seconds = seconds) { 0.0 }

        for (v in listOf(1e15, 1e6, 100.0, 10.0, 2.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val poisoned = runHouse(seconds = seconds) { n -> if (n in 4800 until 4928) v else 0.0 }
            val cmp = compareRuns(poisoned = poisoned, control = control, from = 4800)

            line("L8-01 [V=${fmtE(v)}] ${cmp.text()}")
        }

        flush()
    }

    "L8-02: the house stage under a huge constant with music under it".config(enabled = requested) {
        line("== L8-02: HouseStage alone, 0.3 sine at 220 Hz plus a constant C from 0.5 s to the end (4 s); read over 3..4 s")

        val seconds = 4.0
        val control = runHouse(seconds = seconds) { 0.0 }
        val from = 3 * SR
        val to = 4 * SR

        for (constant in listOf(1e6, 1e15, 1e17)) {
            val poisoned = runHouse(seconds = seconds) { n -> if (n >= SR / 2) constant else 0.0 }
            var errorSq = 0.0
            var controlSq = 0.0
            var zeros = 0
            var peak = 0.0
            val distinct = HashSet<Double>()

            for (i in from until to) {
                val e = poisoned[i] - control[i]

                errorSq += e * e
                controlSq += control[i] * control[i]
                peak = maxOf(peak, abs(poisoned[i]))
                distinct.add(poisoned[i])

                if (poisoned[i] == 0.0) {
                    zeros++
                }
            }

            val errorDb = 10.0 * log10(errorSq / controlSq)
            val cmp = compareRuns(poisoned = poisoned, control = control, from = SR / 2)

            line(
                "L8-02 [C=${fmtE(constant)}] over 3..4 s: error re the music ${fmt(errorDb)} dB, peak ${fmt(peak)}, exact zeros $zeros/${to - from}, " +
                    "distinct output values ${distinct.size}; from the onset: ${cmp.text()}",
            )
        }

        flush()
    }

    // ── L7a-03: a leaving chain that sounds on silence ────────────────────────────────────────────────────────────

    "L7a-03: a leaving chain that sounds on silence, after the edit that removed it".config(enabled = requested) {
        line("== L7a-03: CylinderSwapRig at $SR Hz, the chain on silence, then swapped for a dry chain; how long the leaving chain's constant stays")

        val chains = listOf(
            "pow(-1)" to KatalystDsl.of(KatalystStageDsl.Pow(exp = k(-1.0))),
            "plus(0.2)" to KatalystDsl.of(KatalystStageDsl.Plus(other = k(0.2))),
        )

        for ((label, dsl) in chains) {
            val rig = CylinderSwapRig(sampleRate = SR)

            rig.registry.register("bad", dsl)
            rig.registry.register("dry", CylinderSwapRig.dryChain(1.0))
            rig.cylinder.requestChain("bad")
            rig.render(blocks = 100, level = 0.0)

            val held = rig.block(level = 0.0)[64]

            rig.cylinder.requestChain("dry")

            var lastLoud = -1
            var lastFull = -1
            val blocks = (30.0 * SR / BF).toInt()

            for (b in 0 until blocks) {
                val out = rig.block(level = 0.0)

                for (i in 0 until BF) {
                    val a = abs(out[i])

                    if (!(a < 1e-6)) {
                        lastLoud = b * BF + i
                    }

                    if (a >= 0.5 * abs(held)) {
                        lastFull = b * BF + i
                    }
                }
            }

            line(
                "L7a-03 [$label] the chain's output on silence ${fmtE(held)}; after the request: at full level (>= half) until ${fmt((lastFull + 1) / SR.toDouble())} s, " +
                    "above 1e-6 until ${fmt((lastLoud + 1) / SR.toDouble())} s (run of 30 s)",
            )
        }

        flush()
    }

    // ── L4-11 L4-06 L4-07 L4-08: one bad sample through single stages ─────────────────────────────────────────────

    "L4-11 L4-06 L4-07 L4-08: drive, shimmer, coarse, the adsr and the dc blocker under one bad sample".config(enabled = requested) {
        line("== L4-11: the bare drive node over a 0.5 sine for 0.1 s, then 0.1 s of exact silence")

        for (amount in listOf(100.0, 256.0, 257.0, 300.0)) {
            val tree = IgnitorDsl.Drive(inner = IgnitorDsl.Sample, amount = c(amount))
            val out = renderTree(tree = tree, frames = SR / 5) { n -> if (n < SR / 10) 0.5 * sin(2.0 * PI * 220.0 * n / SR) else 0.0 }
            val nan = out.count { it.isNaN() }
            val inf = out.count { it.isInfinite() }

            line("L4-11 [drive amount $amount] NaN samples $nan, infinite samples $inf of ${out.size}; first NaN at frame ${out.indexOfFirst { it.isNaN() }}")
        }

        line("== L4-06: shimmer(wet 1) over a 0.5 sine for 0.5 s with one NaN at frame 4800, then 3 s of silence")

        for (wet in listOf(1.0, 0.5)) {
            val tree = IgnitorDsl.Shimmer(inner = IgnitorDsl.Sample, wet = c(wet))
            val out = renderTree(tree = tree, frames = (3.5 * SR).toInt()) { n -> poisonedSine(n = n, bad = Double.NaN, until = SR / 2) }
            val bad = out.indices.filter { !out[it].isFinite() }

            line("L4-06 [shimmer wet $wet] non-finite output samples ${bad.size}; at frames ${bad.take(12)}${if (bad.size > 12) " ..." else ""}")
        }

        line("== L4-07: classic() coarse over a 0.5 sine with one +Inf at frame 4800")

        for (factor in listOf(4.0, 4800.0)) {
            val out = renderTree(tree = IgnitorDsl.Sample.classic(), params = mapOf("adsr.on" to 0.0, "coarse.factor" to factor), frames = SR) { n ->
                poisonedSine(n = n, bad = Double.POSITIVE_INFINITY, until = SR / 2)
            }

            line("L4-07 [coarse factor $factor] infinite output samples ${out.count { it.isInfinite() }}, NaN ${out.count { it.isNaN() }}")
        }

        line("== L4-08: one bad sample at frame 0 (where an envelope's gain is exactly 0) and at frame 4800")

        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY)) {
            for (frame in listOf(0, 4800)) {
                val adsr = renderTree(tree = IgnitorDsl.Adsr(inner = IgnitorDsl.Sample), frames = SR) { n -> if (n == frame) bad else 0.5 * sin(2.0 * PI * 220.0 * n / SR) }
                val drive = renderTree(tree = IgnitorDsl.Drive(inner = IgnitorDsl.Sample, amount = c(0.5)), frames = SR) { n -> if (n == frame) bad else 0.5 * sin(2.0 * PI * 220.0 * n / SR) }

                line(
                    "L4-08 [$bad at frame $frame] bare Adsr: ${describeBad(adsr)}; bare Drive(0.5): ${describeBad(drive)}",
                )
            }

            val dc = LowPassHighPassFilters.DcBlocker(coefficient = 0.999)
            val buffer = DoubleArray(BF) { if (it == 10) bad else 0.3 }

            dc.process(buffer = buffer, offset = 0, length = BF)

            val next = DoubleArray(BF) { 0.3 }

            dc.process(buffer = next, offset = 0, length = BF)

            line("L4-08 [$bad] DcBlocker(0.999): non-finite out in the poisoned block ${buffer.count { !it.isFinite() }} (frames ${buffer.indices.filter { !buffer[it].isFinite() }}), in the next block ${next.count { !it.isFinite() }}")
        }

        flush()
    }
})

// ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
// The rigs
// ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════

private const val SR: Int = 48_000
private const val BF: Int = 128

private fun fmt(x: Double): String = String.format(Locale.ROOT, "%.4f", x)

private fun fmtE(x: Double): String = if (x.isFinite()) String.format(Locale.ROOT, "%.3g", x) else x.toString()

private fun k(value: Double): KatalystKnobDsl = KatalystKnobDsl.Constant(value)

private fun c(value: Double): IgnitorDsl = IgnitorDsl.Constant(value)

private fun duckStage(depth: Double): KatalystStageDsl = KatalystStageDsl.Duck(cylinder = k(0.0), depth = k(depth), release = k(0.05))

private fun countNan(block: DoubleArray): Int = block.count { it.isNaN() }

/** The live dispatcher (with its house stage) driven block by block from frame 0: the hosts' pump without a host. */
private class DispatcherRig {
    val link = KlangCommLink(capacity = 8192)
    val dispatcher: PlaybackEngineDispatcher = PlaybackEngineDispatcher.create(
        sampleRate = SR,
        blockFrames = BF,
        commLink = link.backend,
        performanceTimeMs = { 0.0 },
    ).also { it.setBackendStartTime(0.0) }
    val out = StereoBuffer(BF)
    var cursor = 0.0
    val feedback = ArrayList<KlangCommLink.Feedback>()

    fun send(cmd: KlangCommLink.Cmd) {
        dispatcher.handle(cmd)
    }

    fun block(): StereoBuffer {
        try {
            dispatcher.renderBlock(cursorFrame = cursor, out = out)
        } finally {
            // A host that survived a throw would render the NEXT block, as the dispatcher's own `finally` assumes
            cursor += BF
        }

        while (true) {
            val f = link.frontend.feedback.receive() ?: break

            if (f !is KlangCommLink.Feedback.Diagnostics) {
                feedback.add(f)
            }
        }

        return out
    }
}

private fun scheduled(
    pid: String,
    sound: String,
    freqHz: Double?,
    startSec: Double,
    gateEndSec: Double,
    cylinder: Int = 0,
    pan: Double? = null,
    gain: Double? = null,
    ignitorParams: Map<String, Double>? = null,
    katalystParams: Map<String, Double>? = null,
    katalyst: String? = null,
    master: String? = null,
    playbackStartTime: Double = 0.0,
): ScheduledVoice = ScheduledVoice(
    playbackId = pid,
    startTime = startSec,
    gateEndTime = gateEndSec,
    playbackStartTime = playbackStartTime,
    data = VoiceData.empty.copy(
        sound = sound,
        freqHz = freqHz,
        gain = gain,
        cylinder = cylinder,
        pan = pan,
        ignitorParams = ignitorParams?.toKlangMap(),
        katalystParams = katalystParams?.toKlangMap(),
        katalyst = katalyst,
        master = master,
    ),
)

/** Per cylinder of each watched playback: the first and last block whose mix held a NaN. */
private class MixWatch {
    private val first = LinkedHashMap<String, Int>()
    private val last = LinkedHashMap<String, Int>()

    fun observe(rig: DispatcherRig, block: Int, pids: List<String>) {
        for (pid in pids) {
            val engine = rig.dispatcher.engine(pid) ?: continue

            for (cylinder in engine.cylinders.cylinders) {
                val mix = cylinder.mixBuffer

                if (mix.left.any { it.isNaN() } || mix.right.any { it.isNaN() }) {
                    val key = "$pid/cyl${cylinder.id}"

                    first.getOrPut(key) { block }
                    last[key] = block
                }
            }
        }
    }

    fun text(): String = if (first.isEmpty()) {
        "none"
    } else {
        first.keys.joinToString(", ") { "$it ${fmt(first.getValue(it) * BF / SR.toDouble())}..${fmt((last.getValue(it) + 1) * BF / SR.toDouble())}" }
    }
}

/** The engine's output (left), kept whole, with the facts the NaN rows read off it. */
private class OutputTrace(seconds: Double) {
    val blocks: Int = (seconds * SR).toInt() / BF
    val left = DoubleArray(blocks * BF)
    private val zero = BooleanArray(blocks * BF)
    var nonFinite = 0
    var rail = 0
    var peak = 0.0

    fun take(block: Int, out: StereoBuffer) {
        for (i in 0 until BF) {
            val l = out.left[i]
            val r = out.right[i]
            val at = block * BF + i

            left[at] = l
            zero[at] = l == 0.0 && r == 0.0

            if (!l.isFinite() || !r.isFinite()) {
                nonFinite++
            }

            if (abs(l) >= 1.0 || abs(r) >= 1.0) {
                rail++
            }

            if (l.isFinite()) {
                peak = maxOf(peak, abs(l))
            }
        }
    }

    /** Exact-zero frames from 0.1 s on (the house limiter's 5 ms lookahead makes the first frames zero). */
    val zeroFrames: Int get() = (SR / 10 until zero.size).count { zero[it] }

    fun zeroBetween(fromSec: Double, toSec: Double): Int = ((fromSec * SR).toInt() until minOf(zero.size, (toSec * SR).toInt())).count { zero[it] }

    private fun longestRun(): IntRange? {
        var best: IntRange? = null
        var start = -1

        for (i in SR / 10..zero.size) {
            val z = i < zero.size && zero[i]

            if (z && start < 0) {
                start = i
            }

            if (!z && start >= 0) {
                if (best == null || i - start > best.last - best.first + 1) {
                    best = start until i
                }

                start = -1
            }
        }

        return best
    }

    fun longestRunText(): String {
        val run = longestRun() ?: return "none"
        val endText = if (run.last == zero.size - 1) "the end of the render" else "${fmt((run.last + 1) / SR.toDouble())} s"

        return "${fmt(run.first / SR.toDouble())} s .. $endText (${fmt((run.last - run.first + 1) / SR.toDouble())} s)"
    }

    fun stepAfterRun(): Double {
        val run = longestRun() ?: return 0.0
        var step = 0.0
        val from = run.last + 1

        for (i in from until minOf(left.size, from + 2 * BF)) {
            step = maxOf(step, abs(left[i] - left[i - 1]))
        }

        return step
    }

    fun rms(fromSec: Double, toSec: Double): Double {
        val from = (fromSec * SR).toInt()
        val to = minOf(left.size, (toSec * SR).toInt())
        var sum = 0.0

        for (i in from until to) {
            sum += left[i] * left[i]
        }

        return sqrt(sum / maxOf(1, to - from))
    }
}

private class NanDuckScenario(
    val name: String,
    val playbacks: List<Pair<String, List<ScheduledVoice>>>,
    val note: String,
    val seconds: Double = 7.0,
)

/**
 * The kick (cylinder 1, 110 Hz sine, onset 0.5 s, gate 0.1 s) with [kickPan]; the bass (cylinder 0, 55 Hz saw, a note
 * every 0.25 s until [bassUntil]) ducking from cylinder 1 at depth 0.8, the duck switched off from [duckOffFrom] on;
 * optionally a pad on cylinder 2 that ducks from cylinder 1 too; and a second playback, a 440 Hz sine from 0 to 6 s.
 */
private fun nanDuckScenario(name: String, kickPan: Double?, bassUntil: Double, duckOffFrom: Double = 99.0, pad: Boolean = false, note: String): NanDuckScenario {
    val duck = mapOf("duck.cylinder" to 1.0, "duck.depth" to 0.8, "duck.release" to 0.2)
    val song = ArrayList<ScheduledVoice>()

    song.add(scheduled(pid = "song", sound = "sine", freqHz = 110.0, startSec = 0.5, gateEndSec = 0.6, cylinder = 1, pan = kickPan))

    var t = 0.0

    while (t < bassUntil - 1e-9) {
        song.add(
            scheduled(
                pid = "song", sound = "saw", freqHz = 55.0, startSec = t, gateEndSec = t + 0.25, cylinder = 0, gain = 0.5,
                katalystParams = if (t < duckOffFrom) duck else null,
            ),
        )

        if (pad) {
            song.add(scheduled(pid = "song", sound = "triangle", freqHz = 220.0, startSec = t, gateEndSec = t + 0.25, cylinder = 2, gain = 0.3, katalystParams = duck))
        }

        t += 0.25
    }

    val other = listOf(scheduled(pid = "other", sound = "sine", freqHz = 440.0, startSec = 0.0, gateEndSec = 6.0, gain = 0.3))

    return NanDuckScenario(name = name, playbacks = listOf("song" to song, "other" to other), note = note)
}

private fun nanDuckScenarios(): List<NanDuckScenario> = listOf(
    nanDuckScenario(name = "control: pan 0.5, bass to 3 s", kickPan = 0.5, bassUntil = 3.0, note = "control"),
    nanDuckScenario(name = "NaN pan, bass to 3 s", kickPan = Double.NaN, bassUntil = 3.0, note = "the kick's voice ends at 0.65 s (gate 0.6 + release 0.05); the bass's last note ends at 3.05 s"),
    nanDuckScenario(name = "NaN pan, bass to 1.5 s", kickPan = Double.NaN, bassUntil = 1.5, note = "the bass's last note ends at 1.55 s"),
    nanDuckScenario(name = "NaN pan, duck off from 1.5 s, bass to 3 s", kickPan = Double.NaN, bassUntil = 3.0, duckOffFrom = 1.5, note = "the bass notes from 1.5 s carry no duck slots"),
    nanDuckScenario(name = "NaN pan, no duck at all", kickPan = Double.NaN, bassUntil = 3.0, duckOffFrom = 0.0, note = "nobody ducks"),
    nanDuckScenario(name = "NaN pan, two cylinders duck from 1", kickPan = Double.NaN, bassUntil = 3.0, pad = true, note = "cylinders 0 and 2 both duck from 1"),
)

/** A test instrument: [cleanFrames] frames of 0.5, then [after] for ever. */
private class PoisonAfter(private val cleanFrames: Int, private val after: Double) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        for (i in ctx.offset until ctx.windowEnd) {
            val frame = ctx.voiceElapsedFrames + (i - ctx.offset)

            buffer[i] = if (frame < cleanFrames) 0.5 else after
        }
    }
}

/**
 * A test source that plays [sampleAt] from its own frame counter, one window per call: the role of `ArrayIgnitor`
 * (commonTest), computed instead of stored, so a 120 s hold measurement does not allocate a 120 s array per case.
 */
private class FunctionSource(private val sampleAt: (Int) -> Double) : Ignitor {
    private var frame = 0

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        for (i in ctx.offset until ctx.windowEnd) {
            buffer[i] = sampleAt(frame)
            frame++
        }
    }
}

private fun sinePcm(seconds: Double, hz: Double): DoubleArray = DoubleArray((seconds * SR).toInt()) { 0.5 * sin(2.0 * PI * hz * it / SR) }

private fun request(sound: String) = SampleRequest(bank = null, sound = sound, index = null, note = null)

private fun sampleComplete(sound: String, pitchHz: Double, pcm: DoubleArray) =
    KlangCommLink.Cmd.Sample.Complete(req = request(sound), note = null, pitchHz = pitchHz, sample = MonoSamplePcm(sampleRate = SR, pcm = pcm))

/** The first frame of the stack inside Klang, for a throw's site. */
private fun describe(t: Throwable): String {
    val site = t.stackTrace.firstOrNull { it.className.startsWith("io.peekandpoke.klang") && !it.className.contains(".audit.") }

    return "${t.javaClass.simpleName}(${t.message?.take(100)}) at ${site?.className?.substringAfterLast('.')}.${site?.methodName}:${site?.lineNumber}"
}

private fun catching(block: () -> Unit): String? = try {
    block()
    null
} catch (t: Throwable) {
    describe(t)
}

// ── The throw hunt ─────────────────────────────────────────────────────────────────────────────────────────────────

/** The built-ins plus authored note-clock and constant instruments, each through `classic()` as a built-in is. */
private fun huntRegistry(): IgnitorRegistry = IgnitorRegistry().apply {
    registerDefaults()
    register("audit_progress", IgnitorRegistry.builtInVoice(IgnitorDsl.Times(IgnitorDsl.Sine(), IgnitorDsl.Progress)))
    register("audit_progress_bare", IgnitorRegistry.builtInVoice(IgnitorDsl.Progress))
    register("audit_elapsed", IgnitorRegistry.builtInVoice(IgnitorDsl.Times(IgnitorDsl.Sine(), IgnitorDsl.Elapsed)))
    register("audit_constant", IgnitorRegistry.builtInVoice(IgnitorDsl.Constant(0.25)))
    register("audit_freq", IgnitorRegistry.builtInVoice(IgnitorDsl.Freq))
    register("audit_silent_sine", IgnitorRegistry.builtInVoice(IgnitorDsl.Sine(fundamental = IgnitorDsl.Constant(0.0))))
}

private class HuntResult(val window: Int, val thrown: String?, val nanBlocks: Int = 0)

/** The scheduler's promotion path without the scheduler: one factory, one playback context, one render context, reused. */
private class HuntRig(registry: IgnitorRegistry) {
    val factory = VoiceFactory(sampleRate = SR, blockFrames = BF, scratchBuffers = ScratchBuffers(BF))
    val playback = PlaybackCtx(playbackId = "hunt", ignitorRegistry = registry, phasePools = PhasePools(Random(1)))
    val ctx = VoiceTestHelpers.createContext(blockStart = 1280.0, blockFrames = BF, sampleRate = SR)
}

/** One voice with its onset [onsetOffset] frames into block 10 and its gate [gateFrames] after the onset (negative: before it). */
private fun renderNegativeWindow(rig: HuntRig, sound: String, freqHz: Double, onsetOffset: Int, gateFrames: Int): HuntResult {
    val onset = 1280 + onsetOffset
    val scheduled = ScheduledVoice(
        playbackId = "hunt",
        startTime = (onset + 0.25) / SR,
        gateEndTime = (onset + gateFrames + 0.25) / SR,
        playbackStartTime = 0.0,
        data = VoiceData.empty.copy(sound = sound, freqHz = freqHz, ignitorParams = mapOf("adsr.release" to 0.0).toKlangMap()),
    )
    var window = 0
    val thrown = catching {
        val voice = rig.factory.makeVoice(scheduled = scheduled, backendStartTimeSec = 0.0, playbackCtx = rig.playback, getSample = { null })
            ?: return@catching

        window = (voice.endFrame - voice.startFrame).toInt()

        for (b in 10 until 14) {
            rig.ctx.blockStart = (b * BF).toDouble()
            voice.render(rig.ctx)
            rig.ctx.cylinders.clearAll()
        }
    }

    return HuntResult(window = window, thrown = thrown)
}

/** One voice of [sound] with ONE slot at [value], gate 2 blocks, rendered for 6 blocks; counts blocks whose cylinder mix held a NaN. */
private fun renderSlot(rig: HuntRig, sound: String, slot: String, value: Double): HuntResult {
    val scheduled = ScheduledVoice(
        playbackId = "hunt",
        startTime = (1280 + 3 + 0.25) / SR,
        gateEndTime = (1280 + 3 + 2 * BF + 0.25) / SR,
        playbackStartTime = 0.0,
        data = VoiceData.empty.copy(sound = sound, freqHz = 220.0, ignitorParams = mapOf(slot to value).toKlangMap()),
    )
    var nanBlocks = 0
    val thrown = catching {
        val voice = rig.factory.makeVoice(scheduled = scheduled, backendStartTimeSec = 0.0, playbackCtx = rig.playback, getSample = { null })
            ?: return@catching

        for (b in 10 until 16) {
            rig.ctx.blockStart = (b * BF).toDouble()
            voice.render(rig.ctx)

            for (cylinder in rig.ctx.cylinders.cylinders) {
                if (cylinder.mixBuffer.left.any { it.isNaN() }) {
                    nanBlocks++
                }
            }

            rig.ctx.cylinders.clearAll()
        }
    }

    rig.ctx.cylinders.clearAll()

    return HuntResult(window = 0, thrown = thrown, nanBlocks = nanBlocks)
}

private class ChainResult(val thrown: String?, val nonFiniteBlocks: Int, val zeroFrames: Int)

/** An authored chain on a saw voice's cylinder 0 (or as its playback's master), a sine on cylinder 1 as a duck's source; 0.35 s. */
private fun runChainScenario(rig: DispatcherRig, pid: String, dsl: KatalystDsl, position: String): ChainResult {
    var thrown = catching { rig.send(KlangCommLink.Cmd.RegisterKatalyst(playbackId = pid, name = "k", dsl = dsl)) }
    val voices = listOf(
        scheduled(
            pid = pid, sound = "saw", freqHz = 110.0, startSec = 0.01, gateEndSec = 0.2, cylinder = 0,
            katalyst = if (position == "cylinder") "k" else null, master = if (position == "master") "k" else null,
        ),
        scheduled(pid = pid, sound = "sine", freqHz = 220.0, startSec = 0.01, gateEndSec = 0.2, cylinder = 1),
    )

    thrown = thrown ?: catching { rig.send(KlangCommLink.Cmd.ScheduleVoices(playbackId = pid, voices = voices)) }

    var nonFiniteBlocks = 0
    var zeroFrames = 0

    for (b in 0 until (0.35 * SR).toInt() / BF) {
        if (thrown != null) {
            break
        }

        thrown = catching {
            val out = rig.block()
            val engine = rig.dispatcher.engine(pid)

            if (engine != null && engine.cylinders.cylinders.any { cyl -> cyl.mixBuffer.left.any { !it.isFinite() } || cyl.mixBuffer.right.any { !it.isFinite() } }) {
                nonFiniteBlocks++
            }

            zeroFrames += out.left.count { it == 0.0 }
        }
    }

    catching { rig.dispatcher.cleanupHard(pid) }

    return ChainResult(thrown = thrown, nonFiniteBlocks = nonFiniteBlocks, zeroFrames = zeroFrames)
}

/** A saw on cylinder 0 carrying [katalystParams] (the slots a sprudel bus door writes), a sine on cylinder 1; 0.35 s. */
private fun runSlotScenario(rig: DispatcherRig, pid: String, katalystParams: Map<String, Double>): ChainResult {
    val voices = listOf(
        scheduled(pid = pid, sound = "saw", freqHz = 110.0, startSec = 0.01, gateEndSec = 0.2, cylinder = 0, katalystParams = katalystParams),
        scheduled(pid = pid, sound = "sine", freqHz = 220.0, startSec = 0.01, gateEndSec = 0.2, cylinder = 1),
    )
    var thrown = catching { rig.send(KlangCommLink.Cmd.ScheduleVoices(playbackId = pid, voices = voices)) }
    var nonFiniteBlocks = 0

    for (b in 0 until (0.35 * SR).toInt() / BF) {
        if (thrown != null) {
            break
        }

        thrown = catching {
            rig.block()

            val engine = rig.dispatcher.engine(pid)

            if (engine != null && engine.cylinders.cylinders.any { cyl -> cyl.mixBuffer.left.any { !it.isFinite() } }) {
                nonFiniteBlocks++
            }
        }
    }

    catching { rig.dispatcher.cleanupHard(pid) }

    return ChainResult(thrown = thrown, nonFiniteBlocks = nonFiniteBlocks, zeroFrames = 0)
}

/** Every Katalyst stage with each knob in turn at each of [values] (the others at their defaults), and the non-knob arguments at odd values. */
private fun oddChains(values: List<Double>): List<Pair<String, KatalystDsl>> {
    val out = ArrayList<Pair<String, KatalystDsl>>()

    fun add(label: String, vararg stages: KatalystStageDsl) {
        out.add(label to KatalystDsl.of(*stages))
    }

    for (v in values) {
        val x = k(v)

        add("body material=$v", KatalystStageDsl.Body(material = x))
        add("body wet=$v", KatalystStageDsl.Body(material = k(1.0), wet = x))
        add("body floor=$v", KatalystStageDsl.Body(material = k(1.0), floor = x))
        add("vowel vowel=$v", KatalystStageDsl.Vowel(vowel = x))
        add("vowel wet=$v", KatalystStageDsl.Vowel(vowel = k(1.0), wet = x))
        add("vowel floor=$v", KatalystStageDsl.Vowel(vowel = k(1.0), floor = x))
        add("delay wet=$v", KatalystStageDsl.Delay(wet = x))
        add("delay time=$v", KatalystStageDsl.Delay(time = x))
        add("delay feedback=$v", KatalystStageDsl.Delay(feedback = x))
        add("delay cap=$v", KatalystStageDsl.Delay(cap = x))
        add("reverb wet=$v", KatalystStageDsl.Reverb(wet = x))
        add("reverb size=$v", KatalystStageDsl.Reverb(size = x))
        add("reverb lowpass=$v", KatalystStageDsl.Reverb(lowpass = x))
        add("phaser rate=$v", KatalystStageDsl.Phaser(rate = x))
        add("phaser wet=$v", KatalystStageDsl.Phaser(wet = x))
        add("phaser center=$v", KatalystStageDsl.Phaser(center = x))
        add("phaser sweep=$v", KatalystStageDsl.Phaser(sweep = x))
        add("phaser floor=$v", KatalystStageDsl.Phaser(floor = x))
        add("compressor threshold=$v", KatalystStageDsl.Compressor(threshold = x))
        add("compressor ratio=$v", KatalystStageDsl.Compressor(ratio = x))
        add("compressor knee=$v", KatalystStageDsl.Compressor(knee = x))
        add("compressor attack=$v", KatalystStageDsl.Compressor(attack = x))
        add("compressor release=$v", KatalystStageDsl.Compressor(release = x))
        add("compressor lookahead=$v", KatalystStageDsl.Compressor(lookahead = v))
        add("compressor lookahead=0.01 attack=$v", KatalystStageDsl.Compressor(lookahead = 0.01, attack = x))
        add("distort amount=$v", KatalystStageDsl.Distort(amount = x))
        add("duck cylinder=$v", KatalystStageDsl.Duck(cylinder = x, depth = k(0.8)))
        add("duck depth=$v", KatalystStageDsl.Duck(cylinder = k(1.0), depth = x))
        add("duck release=$v", KatalystStageDsl.Duck(cylinder = k(1.0), depth = k(0.8), release = x))
        add("gain=$v", KatalystStageDsl.Gain(gain = x))
        add("plus $v", KatalystStageDsl.Plus(other = x))
        add("minus $v", KatalystStageDsl.Minus(other = x))
        add("times $v", KatalystStageDsl.Times(other = x))
        add("div $v", KatalystStageDsl.Div(other = x))
        add("pow $v", KatalystStageDsl.Pow(exp = x))
        add("min $v", KatalystStageDsl.Min(other = x))
        add("max $v", KatalystStageDsl.Max(other = x))
        add("eq lowpass freq=$v", KatalystStageDsl.Eq(sections = listOf(IgnitorDsl.EqSection.Lowpass(freq = c(v)))))
        add("eq highpass q=$v", KatalystStageDsl.Eq(sections = listOf(IgnitorDsl.EqSection.Highpass(q = c(v)))))
    }

    for (shape in listOf(-1, 99, Int.MAX_VALUE, Int.MIN_VALUE)) {
        add("distort shape=$shape", KatalystStageDsl.Distort(shape = shape))
    }

    for (factor in listOf(-5, 0, 1, 3, 1000, Int.MAX_VALUE, Int.MIN_VALUE)) {
        add("oversampleBy $factor", KatalystStageDsl.OversampleBy(factor = factor, body = KatalystDsl.of(KatalystStageDsl.Distort())))
        add("oversampleAtLeast $factor", KatalystStageDsl.OversampleAtLeast(factor = factor, body = KatalystDsl.of(KatalystStageDsl.Distort())))
    }

    add("abs", KatalystStageDsl.Abs)
    add("parallel, no branches", KatalystStageDsl.Parallel(branches = emptyList()))
    add("parallel, one empty branch", KatalystStageDsl.Parallel(branches = listOf(KatalystDsl(emptyList()))))
    add("empty chain")

    return out
}

/** Per cylinder slot: the slots that switch its stage on, so the odd value lands on a running stage. */
private fun cylinderSlotBases(): List<Pair<String, Map<String, Double>>> {
    val body = mapOf("body.material" to 1.0, "body.wet" to 0.5)
    val vowel = mapOf("vowel.vowel" to 1.0, "vowel.wet" to 0.5)
    val delay = mapOf("delay.time" to 0.25, "delay.wet" to 0.5, "delay.feedback" to 0.5)
    val reverb = mapOf("reverb.size" to 0.5, "reverb.wet" to 0.5)
    val phaser = mapOf("phaser.wet" to 0.5, "phaser.rate" to 1.0)
    val compressor = mapOf("compressor.threshold" to -20.0)
    val duck = mapOf("duck.cylinder" to 1.0, "duck.depth" to 0.8)

    return listOf(
        "body.material" to body, "body.wet" to body, "body.floor" to body,
        "vowel.vowel" to vowel, "vowel.wet" to vowel, "vowel.floor" to vowel,
        "delay.wet" to delay, "delay.time" to delay, "delay.feedback" to delay, "delay.cap" to delay,
        "reverb.wet" to reverb, "reverb.size" to reverb, "reverb.lowpass" to reverb,
        "phaser.rate" to phaser, "phaser.wet" to phaser, "phaser.center" to phaser, "phaser.sweep" to phaser, "phaser.floor" to phaser,
        "compressor.threshold" to compressor, "compressor.ratio" to compressor, "compressor.knee" to compressor,
        "compressor.attack" to compressor, "compressor.release" to compressor,
        "gain.gain" to emptyMap(),
        "duck.cylinder" to duck, "duck.depth" to duck, "duck.release" to duck,
    )
}

// ── The SVF and single stages ──────────────────────────────────────────────────────────────────────────────────────

private class Settled(val frames: Int, val capped: Boolean, val peak: Double, val nonFinite: Int) {
    fun text(): String = if (capped) "NOT settled by the cap (peak ${fmtE(peak)})" else "$frames frames (${fmt(frames / SR.toDouble())} s, peak ${fmtE(peak)})"

    fun short(): String = if (capped) "never" else "${fmt(frames / SR.toDouble())}s"
}

/**
 * Renders [tree] over [source] (as its sample leaf) in whole blocks the way `ignitorProbeStage` does, until the output has
 * stayed under [floor] for half a second past [inputEndFrame], or [capSeconds]. Reports the frames from [inputEndFrame]
 * until the output is under [floor] for good.
 */
private fun settle(tree: IgnitorDsl, source: Ignitor, inputEndFrame: Int, floor: Double, capSeconds: Double): Settled {
    val built = tree.buildExciter(ignitorParams = null, random = Random(7), freqHz = 220.0, sampleRate = SR, blockFrames = BF, sampleSource = source)
    val gate = maxOf(1, inputEndFrame)
    val ctx = IgniteContext(sampleRate = SR, voiceDurationFrames = gate, gateEndFrame = gate, scratchBuffers = ScratchBuffers(BF), random = Random(7))
    val buffer = AudioBuffer(BF)
    val cap = (capSeconds * SR).toInt()
    val quiet = SR / 2
    var at = 0
    var lastLoud = -1
    var peak = 0.0
    var nonFinite = 0

    while (at < cap) {
        ctx.voiceElapsedFrames = at
        ctx.updateOffsetAndLength(offset = 0, length = BF)
        built.ignitor.generate(buffer, 220.0, ctx)

        for (i in 0 until BF) {
            val x = buffer[i]
            val a = abs(x)

            if (!x.isFinite()) {
                nonFinite++
            } else if (a > peak) {
                peak = a
            }

            // NaN-guard: a NaN is not under the floor, it counts as loud
            if (!(a < floor)) {
                lastLoud = at + i
            }
        }

        at += BF

        if (at > inputEndFrame && at - maxOf(lastLoud, inputEndFrame) > quiet) {
            break
        }
    }

    val capped = at >= cap && at - lastLoud <= quiet

    return Settled(frames = maxOf(0, lastLoud + 1 - inputEndFrame), capped = capped, peak = peak, nonFinite = nonFinite)
}

/** [tree] over a computed input as its sample leaf, [frames] frames, the gate at the end; the whole output. */
private fun renderTree(tree: IgnitorDsl, frames: Int, params: Map<String, Double>? = null, sampleRate: Int = SR, input: (Int) -> Double): DoubleArray {
    val built = tree.buildExciter(ignitorParams = params?.toKlangMap(), random = Random(7), freqHz = 220.0, sampleRate = sampleRate, blockFrames = BF, sampleSource = FunctionSource(input))
    val ctx = IgniteContext(sampleRate = sampleRate, voiceDurationFrames = frames, gateEndFrame = frames, scratchBuffers = ScratchBuffers(BF), random = Random(7))
    val buffer = AudioBuffer(BF)
    val out = DoubleArray(frames / BF * BF)
    var at = 0

    while (at < out.size) {
        ctx.voiceElapsedFrames = at
        ctx.updateOffsetAndLength(offset = 0, length = BF)
        built.ignitor.generate(buffer, 220.0, ctx)
        buffer.copyInto(destination = out, destinationOffset = at, startIndex = 0, endIndex = BF)
        at += BF
    }

    return out
}

private fun poisonedSine(n: Int, bad: Double, until: Int): Double = when {
    n == 4800 -> bad
    n < until -> 0.5 * sin(2.0 * PI * 220.0 * n / SR)
    else -> 0.0
}

private fun describeBad(out: DoubleArray): String {
    val nan = out.indices.filter { out[it].isNaN() }
    val inf = out.indices.filter { out[it].isInfinite() }

    return "NaN ${nan.size}${if (nan.isNotEmpty()) " at ${nan.take(4)}" else ""}, Inf ${inf.size}${if (inf.isNotEmpty()) " at ${inf.take(4)}" else ""}"
}

private fun svf(mode: String, fc: Double, q: Double, analog: Double): IgnitorDsl = if (mode == "lowpass") {
    IgnitorDsl.Lowpass(inner = IgnitorDsl.Sample, freq = c(fc), q = c(q), analog = c(analog))
} else {
    IgnitorDsl.Highpass(inner = IgnitorDsl.Sample, freq = c(fc), q = c(q), analog = c(analog))
}

/** RMS of the last [seconds] of [out]. */
private fun tailRms(out: DoubleArray, seconds: Double, sampleRate: Int): Double {
    val n = (seconds * sampleRate).toInt()

    return rmsOf(mix = out, from = out.size - n, to = out.size)
}

private fun rmsOf(mix: DoubleArray, from: Int, to: Int): Double {
    var sum = 0.0

    for (i in from until to) {
        sum += mix[i] * mix[i]
    }

    return sqrt(sum / maxOf(1, to - from))
}

/** The amplitude of the [hz] component of [mix] over `[from, to)`, a single-bin DFT. */
private fun toneAmplitude(mix: DoubleArray, hz: Double, from: Int, to: Int): Double {
    var re = 0.0
    var im = 0.0

    for (i in from until to) {
        val w = 2.0 * PI * hz * i / SR

        re += mix[i] * cos(w)
        im -= mix[i] * sin(w)
    }

    return 2.0 * sqrt(re * re + im * im) / (to - from)
}

/** One voice of [sound] through the factory, rendered block by block from frame 0; the left channel of its cylinder's mix. */
private fun renderVoiceMix(registry: IgnitorRegistry, sound: String, freqHz: Double, seconds: Double, gateSeconds: Double, ignitorParams: Map<String, Double>?): DoubleArray {
    val rig = HuntRig(registry)
    val scheduled = ScheduledVoice(
        playbackId = "hunt",
        startTime = 0.25 / SR,
        gateEndTime = gateSeconds + 0.25 / SR,
        playbackStartTime = 0.0,
        data = VoiceData.empty.copy(sound = sound, freqHz = freqHz, ignitorParams = ignitorParams?.toKlangMap()),
    )
    val blocks = (seconds * SR).toInt() / BF
    val out = DoubleArray(blocks * BF)
    val voice = rig.factory.makeVoice(scheduled = scheduled, backendStartTimeSec = 0.0, playbackCtx = rig.playback, getSample = { null }) ?: return out

    for (b in 0 until blocks) {
        rig.ctx.blockStart = (b * BF).toDouble()
        voice.render(rig.ctx)

        val cylinder = rig.ctx.cylinders.cylinders.firstOrNull()

        cylinder?.mixBuffer?.left?.copyInto(destination = out, destinationOffset = b * BF, startIndex = 0, endIndex = BF)
        rig.ctx.cylinders.clearAll()
    }

    return out
}

// ── The house stage ────────────────────────────────────────────────────────────────────────────────────────────────

/** The house stage alone over `0.3 sin(220 Hz) + extra(n)` on both channels; the left output. */
private fun runHouse(seconds: Double, extra: (Int) -> Double): DoubleArray {
    val house = HouseStage(sampleRate = SR, blockFrames = BF)
    val mix = StereoBuffer(BF)
    val out = StereoBuffer(BF)
    val frames = (seconds * SR).toInt() / BF * BF
    val result = DoubleArray(frames)
    var at = 0

    while (at < frames) {
        for (i in 0 until BF) {
            val n = at + i
            val x = 0.3 * sin(2.0 * PI * 220.0 * n / SR) + extra(n)

            mix.left[i] = x
            mix.right[i] = x
        }

        house.process(mix = mix, out = out)
        out.left.copyInto(destination = result, destinationOffset = at, startIndex = 0, endIndex = BF)
        at += BF
    }

    return result
}

private class HouseCompare(val rail: Int, val zeros: Int, val firstDeviation: Int, val recovered: Int, val lastFrame: Int) {
    fun text(): String {
        val rec = if (recovered < 0) "not within the run" else "${recovered - firstDeviation} frames after the first deviation (${fmt((recovered - firstDeviation) / SR.toDouble())} s)"

        return "rail samples (|out| = 1) $rail, exact zeros $zeros, first deviation at frame $firstDeviation, back within 0.003 of the clean run for good: $rec"
    }
}

/** [poisoned] against [control] from frame [from] on: the rail and zero counts, and where it is back within 1 percent of the music's 0.3. */
private fun compareRuns(poisoned: DoubleArray, control: DoubleArray, from: Int): HouseCompare {
    var rail = 0
    var zeros = 0
    var firstDev = -1
    var lastDev = -1

    for (i in from until poisoned.size) {
        val p = poisoned[i]

        if (abs(p) >= 1.0) {
            rail++
        }

        if (p == 0.0 && control[i] != 0.0) {
            zeros++
        }

        // NaN-guard: a non-finite output is a deviation
        if (!(abs(p - control[i]) < 0.003)) {
            lastDev = i

            if (firstDev < 0) {
                firstDev = i
            }
        }
    }

    val recovered = if (lastDev == poisoned.size - 1) -1 else lastDev + 1

    return HouseCompare(rail = rail, zeros = zeros, firstDeviation = firstDev, recovered = if (firstDev < 0) firstDev else recovered, lastFrame = poisoned.size)
}
