/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.buildExciter
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrCurves
import io.peekandpoke.klang.audio_bridge.DistortionShapes
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.LfoShapes
import io.peekandpoke.klang.audio_bridge.classic
import io.peekandpoke.klang.audio_bridge.optimize
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull
import kotlin.random.Random

/**
 * `classic()`'s DOOR PARITY, rendered (phase 3 step 5, `docs/tasks/builtin-instruments.md` section 9):
 * one voice per row, the script `Osc.saw().classic()` against the Kotlin `IgnitorDsl.Sawtooth().classic()`,
 * with the slots written through the bag, every slot in turn and a few in combination, compared in RAW
 * BITS. Here because sprudel is the module that has both the script engine and the renderer, so the
 * spec runs on the JVM and on JS.
 *
 * The rows are ENGAGEMENT rows: each renders something other than its BASELINE, the same bag without
 * the row's own knob (a `q` row against the cutoff alone, a `tremolo.skew` row against the tremolo
 * without skew), so each row shows ITS slot engaged and not a companion's. The bit comparison between
 * the doors inside each row cannot fail independently of the tree-equality row, since both doors build
 * one tree and render it with one seed; it is kept as the render-level statement of the parity. The tree-level half of the parity (same tree, same
 * slot objects) is `KlangScriptClassicDoorParitySpec` in klangscript-libs.
 */
class ClassicDoorRenderParitySpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val blocks = 60
    val gateFrames = 5000

    fun render(dsl: IgnitorDsl, bag: Map<String, Double>): DoubleArray {
        val ignitor = dsl.optimize().buildExciter(oscParams = bag, random = Random(7), freqHz = 220.0, sampleRate = sampleRate).ignitor
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = gateFrames,
            gateEndFrame = gateFrames,
            releaseFrames = 0,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = Random(7),
        )
        val buffer = AudioBuffer(blockFrames)
        val out = ArrayList<Double>()

        for (block in 0 until blocks) {
            val offset = if (block == 0) 37 else 0
            val length = blockFrames - offset

            ctx.updateOffsetAndLength(offset, length)
            ignitor.generate(buffer, 220.0, ctx)

            for (i in offset until offset + length) {
                out.add(buffer[i])
            }

            ctx.voiceElapsedFrames += length
        }

        return out.toDoubleArray()
    }

    val script: IgnitorDsl by lazy {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        engine.execute("Osc.saw().classic()").toObjectOrNull<IgnitorDsl>()!!
    }

    val kotlin: IgnitorDsl = IgnitorDsl.Sawtooth().classic()

    /** A row: the slot it engages ([knob], or null for a combination), and the bag that writes it. */
    class Row(val knob: String?, val bag: Map<String, Double>) {
        /** What the row is compared against: the bag WITHOUT its own knob, so the row shows ITS knob engaged. */
        val baseline: Map<String, Double> get() = if (knob == null) emptyMap() else bag - knob
    }

    fun filterRows(door: String, hasPasses: Boolean): List<Row> = buildList {
        val f = "$door.freq" to 900.0

        add(Row("$door.freq", mapOf(f)))
        add(Row("$door.q", mapOf(f, "$door.q" to 4.0)))

        if (hasPasses) {
            add(Row("$door.passes", mapOf(f, "$door.passes" to 2.0)))
        }

        add(Row("$door.env", mapOf(f, "$door.env" to 12.0)))
        add(Row("$door.attack", mapOf(f, "$door.attack" to 0.05)))
        add(Row("$door.decay", mapOf(f, "$door.decay" to 0.3)))
        add(Row("$door.sustain", mapOf(f, "$door.sustain" to 0.3)))
        add(Row("$door.release", mapOf(f, "$door.release" to 0.2)))

        // The curve slots (step 5b (c2)): each against the same full envelope without that one curve.
        val env = mapOf(f, "$door.env" to 12.0, "$door.attack" to 0.05, "$door.decay" to 0.3, "$door.sustain" to 0.3, "$door.release" to 0.2)

        add(Row("${door}Curves.attack", env + ("${door}Curves.attack" to AdsrCurves.indexOf(AdsrCurve.Linear))))
        add(Row("${door}Curves.decay", env + ("${door}Curves.decay" to AdsrCurves.indexOf(AdsrCurve.SCurve))))
        add(Row("${door}Curves.release", env + ("${door}Curves.release" to AdsrCurves.indexOf(AdsrCurve.InvSquare))))
    }

    val trem = mapOf("tremolo.depth" to 0.5, "tremolo.sync" to 5.0)

    val rows: List<Row> = buildList {
        add(Row("crush.amount", mapOf("crush.amount" to 4.0)))
        add(Row("coarse.amount", mapOf("coarse.amount" to 3.0)))
        add(Row("distort.amount", mapOf("distort.amount" to 0.5)))
        add(Row("distort.shape", mapOf("distort.amount" to 0.5, "distort.shape" to DistortionShapes.indexOf("tube"))))
        add(Row("distort.oversample", mapOf("distort.amount" to 0.5, "distort.oversample" to 2.0)))
        addAll(filterRows("hpf", hasPasses = true))
        addAll(filterRows("bpf", hasPasses = false))
        addAll(filterRows("notch", hasPasses = false))
        addAll(filterRows("lpf", hasPasses = true))
        add(Row("analog", mapOf("analog" to 2.0, "lpf.freq" to 900.0)))
        add(Row("tremolo.depth", mapOf("tremolo.depth" to 0.5)))
        add(Row("tremolo.sync", trem))
        add(Row("tremolo.shape", trem + ("tremolo.shape" to LfoShapes.indexOf("square"))))
        add(Row("tremolo.skew", trem + ("tremolo.skew" to 0.4)))
        add(Row("tremolo.phase", trem + ("tremolo.phase" to 0.3)))
        add(Row("adsr.attack", mapOf("adsr.attack" to 0.05)))
        add(Row("adsr.decay", mapOf("adsr.decay" to 0.02, "adsr.sustain" to 0.5)))
        add(Row("adsr.sustain", mapOf("adsr.sustain" to 0.5)))
        add(Row("adsr.release", mapOf("adsr.release" to 0.02)))
        add(Row("adsr.on", mapOf("adsr.on" to 0.0)))
        add(Row("adsrCurves.attack", mapOf("adsr.attack" to 0.05, "adsrCurves.attack" to AdsrCurves.indexOf(AdsrCurve.Linear))))
        add(Row("adsrCurves.decay", mapOf("adsr.sustain" to 0.5, "adsrCurves.decay" to AdsrCurves.indexOf(AdsrCurve.SCurve))))
        add(Row("adsrCurves.release", mapOf("adsrCurves.release" to AdsrCurves.indexOf(AdsrCurve.Square))))
        add(
            Row(
                null,
                mapOf(
                    "crush.amount" to 6.0, "hpf.freq" to 150.0, "bpf.freq" to 1200.0, "bpf.q" to 0.5, "notch.freq" to 3000.0,
                    "lpf.freq" to 2500.0, "lpf.env" to 12.0, "lpf.decay" to 0.2, "lpf.sustain" to 0.2,
                    "tremolo.depth" to 0.3, "tremolo.sync" to 6.0, "adsr.attack" to 0.01, "adsr.sustain" to 0.7,
                ),
            ),
        )
        add(
            Row(
                null,
                mapOf(
                    "distort.amount" to 0.8, "distort.shape" to DistortionShapes.indexOf("fold"), "coarse.amount" to 2.0,
                    "analog" to 1.5, "lpf.freq" to 4000.0, "adsr.on" to 0.0,
                ),
            ),
        )
    }

    "the two doors build the same tree" {
        script shouldBe kotlin
    }

    for (row in rows) {
        val what = row.knob ?: "a combination of ${row.bag.keys.joinToString()}"

        "ENGAGEMENT: $what changes the render, on both doors, in the same raw bits" {
            val k = render(kotlin, row.bag)
            val baseline = render(kotlin, row.baseline)

            withClue("engagement: the row renders something other than its baseline without ${row.knob ?: "any slot"}") {
                k.toList() shouldNotBe baseline.toList()
            }

            // Given the tree row above, this cannot fail on its own (same tree, same seed): it is kept as
            // the render-level statement of the parity, and so a future door that builds a different
            // tree for the same code shows WHICH slot's sound moved.
            val s = render(script, row.bag)
            val mismatch = s.indices.firstOrNull { s[it].toRawBits() != k[it].toRawBits() } ?: -1

            withClue("first mismatching frame, script against Kotlin") { mismatch shouldBe -1 }
        }
    }
})
