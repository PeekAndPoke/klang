/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.lang.adsr
import io.peekandpoke.klang.sprudel.lang.accelerate
import io.peekandpoke.klang.sprudel.lang.add
import io.peekandpoke.klang.sprudel.lang.adsrOff
import io.peekandpoke.klang.sprudel.lang.begin
import io.peekandpoke.klang.sprudel.lang.body
import io.peekandpoke.klang.sprudel.lang.bpf
import io.peekandpoke.klang.sprudel.lang.distort
import io.peekandpoke.klang.sprudel.lang.echo
import io.peekandpoke.klang.sprudel.lang.fm
import io.peekandpoke.klang.sprudel.lang.gain
import io.peekandpoke.klang.sprudel.lang.hpf
import io.peekandpoke.klang.sprudel.lang.jux
import io.peekandpoke.klang.sprudel.lang.loop
import io.peekandpoke.klang.sprudel.lang.lpf
import io.peekandpoke.klang.sprudel.lang.merge
import io.peekandpoke.klang.sprudel.lang.notch
import io.peekandpoke.klang.sprudel.lang.note
import io.peekandpoke.klang.sprudel.lang.ignp
import io.peekandpoke.klang.sprudel.lang.penv
import io.peekandpoke.klang.sprudel.lang.phaser
import io.peekandpoke.klang.sprudel.lang.ply
import io.peekandpoke.klang.sprudel.lang.rev
import io.peekandpoke.klang.sprudel.lang.reverb
import io.peekandpoke.klang.sprudel.lang.seq
import io.peekandpoke.klang.sprudel.lang.sound
import io.peekandpoke.klang.sprudel.lang.stack
import io.peekandpoke.klang.sprudel.lang.superimpose
import io.peekandpoke.klang.sprudel.lang.tremolo
import io.peekandpoke.klang.sprudel.lang.unit
import io.peekandpoke.klang.sprudel.lang.vibrato
import io.peekandpoke.klang.sprudel.lang.vowel
import io.peekandpoke.klang.sprudel.pattern.AtomicPattern

/**
 * The single-owner invariant of [SprudelVoiceData] across a QUERY: every event owns its voice data, and every
 * mutable object inside it (the `Svd*` groups and the two [ParamBag]s), so an in-place door on one event can
 * never reach another.
 *
 * Aliasing is silent: one voice's filter bleeding into its sibling sounds like a wrong note, not a crash. These
 * rows replaced the 2.7 MB wire golden (`MutableVoiceDataGoldenSpec`, test consolidation 2026-09-28), which
 * guarded the same invariant only where a shared object happened to be written twice with different values
 * inside its corpus. Here the oracle is identity: no two events of a query share a data instance, a group or a
 * bag. The per-instance halves (`clone()`, `merge()`, `mergeFrom()` and the `mergeSvd*` helpers) are pinned in
 * [SprudelVoiceDataSpec].
 *
 * Two kinds of row, told apart by mutation (2026-09-28, every row run under each mutant, the full red list read):
 * - **A present hazard**, a row that turns red when today's code drops a clone:
 *   - the leaf atom: AtomicPattern without its clone, or a `clone()` that shares a group;
 *   - ply: AtomicInfinitePattern without its clone, or a `clone()` that shares a group;
 *   - the arithmetic fragments: a shallow `copy()` instead of `clone()`, or a `clone()` that shares a group;
 *   - `adsrOff()` / `loop()` alone: a `mergeSvd*` helper that hands out the control's group;
 *   - MergePattern: AtomicPattern without its clone (the source events it merges into in place are shared).
 * - **A future hazard**, a row no single dropped clone turns red today, because the door chain before the fan-out
 *   already hands every layer fresh data: stack, superimpose, jux, echo, `_liftData` behind a full door chain and
 *   the bare `reverb()`. They pin the invariant against a later cache or fast path that shares one source event
 *   between layers (the constant-control fast path,
 *   `docs/tasks/future/optimize-constant-control-fast-path.md`), and they are cheap.
 */
class VoiceDataAliasingSpec : StringSpec({

    /** Every door group written, so every mutable part of the data exists and can be shared or not. */
    fun SprudelPattern.everyGroup(): SprudelPattern = this
        .adsr(0.01, 0.2, 0.5, 0.3).lpf(800).hpf(100).bpf(900).notch(1500)
        .accelerate(1).vibrato(5, 0.2).penv(12, 0.01).fm(2, 1.5)
        .distort(0.3).tremolo(0.5, 4).begin(0.1)
        .vowel(0.3, "a").body(0.3, "wood").phaser(0.2, 0.5)
        .ignp("voices", 3).reverb(0.2)

    fun source() = note("c3 e3 g3").everyGroup()

    /** A bare leaf over data that carries every group: no door after it, so its own clone is the only one. */
    fun leafWithEveryGroup() = AtomicPattern(source().queryArc(0.0, 1.0).first().data)

    // One row per construct that fans one source event out, or hands a stored instance to more than one event.
    // The KDoc above says which rows guard a present hazard and which a future one.
    val constructs: List<Pair<String, () -> SprudelPattern>> = listOf(
        "the leaf atom (AtomicPattern), queried over four cycles" to { leafWithEveryGroup() },
        "stack of ONE pattern object, twice" to { source().let { stack(it, it) } },
        "superimpose" to { source().superimpose({ it.gain(0.5) }) },
        "jux" to { source().jux({ it.rev() }) },
        "ply (AtomicInfinitePattern)" to { source().ply(3) },
        "echo" to { source().echo(3, 0.125, 0.6) },
        "arithmetic fragments (one source event, three control events)" to { seq("0 2").everyGroup().add("0 7 12") },
        "_liftData behind a full door chain: one control event merged into three source events" to {
            note("c3 e3 g3").adsrOff().loop().unit("c").everyGroup()
        },
        // Each _liftData door LAST in its own chain: a later merge copies every group it passes through
        // (`over == null -> base.copy()`), which would hide the aliasing an earlier merge introduced.
        "_liftData onto events with no adsr group yet" to { note("c3 e3 g3").adsrOff() },
        "_liftData onto events with no sample group yet" to { note("c3 e3 g3").loop() },
        "MergePattern" to { note("c3 e3 g3").merge(sound("saw").everyGroup()) },
        "the bare reverb() reinterpret (its own clone)" to { seq("0.2 0.5").everyGroup().reverb() },
    )

    "no two events of a query share their voice data, a group or a bag" {
        // Every construct runs, and the row fails once with the full list: a mutant's verdict names every row it
        // turns red, which is what the present/future split in the KDoc is read from.
        val shared = constructs.mapNotNull { (name, build) ->
            val events = build().queryArc(0.0, 4.0)

            withClue(name) { events shouldHaveAtLeastSize 2 }

            val owned = events.flatMapIndexed { i, e ->
                e.data.mutableParts().filter { it.second != null }.map { (part, obj) -> Triple(i, part, obj) }
            }

            val firstPair = owned.indices.asSequence()
                .flatMap { a -> (a + 1 until owned.size).asSequence().map { b -> owned[a] to owned[b] } }
                .firstOrNull { (x, y) -> x.third === y.third }

            firstPair?.let { (x, y) -> "$name: event ${x.first}'s ${x.second} is the same instance as event ${y.first}'s ${y.second}" }
        }

        shared shouldBe emptyList()
    }

    "the fan-out rows are not vacuous: their events carry every group and both bags" {
        for ((name, build) in constructs.filter { !it.first.contains("yet") }) {
            withClue(name) {
                val missing = build().queryArc(0.0, 1.0).first().data.mutableParts().filter { it.second == null }.map { it.first }

                missing shouldBe emptyList()
            }
        }
    }

    "a write through one event's groups and bags never reaches its sibling" {
        // The expected values come from a second pattern built and queried on its own, not from `clone()` (the code
        // under test). Its pattern id differs (a Kotlin call without a location draws a fresh one), so the id is
        // left out of the comparison. Every construct runs, and the row fails once with the full list.
        fun SprudelVoiceData.withoutId() = copy(patternId = null)

        val reached = constructs.mapNotNull { (name, build) ->
            val events = build().queryArc(0.0, 4.0)
            val writer = events[0].data
            val sibling = events[1].data
            val expected = build().queryArc(0.0, 4.0)

            writer.writeEveryPart()

            when {
                writer.withoutId() == expected[0].data.withoutId() -> "$name: the write did not land"
                sibling.withoutId() != expected[1].data.withoutId() -> "$name: the write reached the sibling"
                else -> null
            }
        }

        reached shouldBe emptyList()
    }
})

/**
 * Every mutable object a [SprudelVoiceData] owns: the instance itself, the fourteen `Svd*` groups and the two
 * [ParamBag]s. The immutable-replace fields (`tags`, `tweaks`, the sound and the chain values) are shared by
 * design and are not listed. A new group joins this list, [writeEveryPart] and [SprudelVoiceData.clone].
 */
internal fun SprudelVoiceData.mutableParts(): List<Pair<String, Any?>> = listOf(
    "data" to this,
    "adsr" to adsr, "lpf" to lpf, "hpf" to hpf, "bpf" to bpf, "notch" to notch,
    "pitchMod" to pitchMod, "pitchEnv" to pitchEnv, "fm" to fm, "distortion" to distortion,
    "phaser" to phaser, "tremolo" to tremolo, "sample" to sample, "bodyFx" to bodyFx, "vowelFx" to vowelFx,
    "ignitorParams" to ignitorParams, "katalystParams" to katalystParams,
)

/** Writes one sentinel through every group and bag, in place (the flat setters write into an existing group). */
internal fun SprudelVoiceData.writeEveryPart() {
    attack = -1.0; cutoff = -2.0; hcutoff = -3.0; bandf = -4.0; notchf = -5.0
    accelerate = -6.0; pAttack = -7.0; fmh = -8.0; distort = -9.0; phaserRate = -10.0
    tremoloRate = -11.0; begin = -12.0; body = "sentinel"; vowel = "sentinel"
    putIgnitorParam("sentinel", -13.0)
    putKatalystParam("sentinel", -14.0)
}
