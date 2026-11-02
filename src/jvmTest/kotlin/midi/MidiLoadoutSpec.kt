/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.midi

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.midi.MidiPlayCode.Sound

/** The MIDI loadout (KLANG-00402, step 5c): the instruments the instrument keys step through, filled by drag and drop. */
class MidiLoadoutSpec : StringSpec({

    val lead = Sound.Own("lead")
    val pad = Sound.Own("pad")
    val bass = Sound.Own("bass")
    val pluck = Sound.BuiltIn("pluck")

    "added at the end, each once, at most its slots" {
        MidiLoadout().add(lead).add(pluck).add(lead).sounds shouldBe listOf(lead, pluck)

        val full = (1..MidiLoadout.MAX).fold(MidiLoadout()) { loadout, i -> loadout.add(Sound.Own("i$i")) }

        withClue("full: no slot for one more") { full.add(lead).sounds.size shouldBe MidiLoadout.MAX }
    }

    "dropped on a slot: a new one in place of the one there; one in another slot swaps; past the end, added or moved" {
        val three = MidiLoadout(listOf(lead, pad, bass))

        withClue("a new one on slot 1: in place of pad") { three.place(1, pluck).sounds shouldBe listOf(lead, pluck, bass) }
        withClue("bass onto slot 0: swapped with lead") { three.place(0, bass).sounds shouldBe listOf(bass, pad, lead) }
        withClue("lead onto slot 2: swapped with bass") { three.place(2, lead).sounds shouldBe listOf(bass, pad, lead) }
        withClue("a new one past the end: added") { three.place(3, pluck).sounds shouldBe listOf(lead, pad, bass, pluck) }
        withClue("one of its own past the end: moved there") { three.place(3, lead).sounds shouldBe listOf(pad, bass, lead) }
        withClue("onto its own slot, in the middle and the first: unchanged") {
            three.place(1, pad) shouldBe three
            three.place(0, lead) shouldBe three
        }
    }

    "a slot removed" {
        MidiLoadout(listOf(lead, pad, bass)).remove(1).sounds shouldBe listOf(lead, bass)
    }

    "the keys step through the loadout's that still are, in its order, round; empty, through all; from one not in it, from the start" {
        val available = listOf(lead, pad, bass, pluck)
        val loadout = MidiLoadout(listOf(pluck, Sound.Own("gone"), lead))

        withClue("its own that still are, in its order") { loadout.stepsThrough(available) shouldBe listOf(pluck, lead) }
        withClue("forward, round") {
            loadout.next(from = pluck, steps = 1, available = available) shouldBe lead
            loadout.next(from = lead, steps = 1, available = available) shouldBe pluck
        }
        withClue("back") { loadout.next(from = pluck, steps = -1, available = available) shouldBe lead }
        withClue("from one not in it: forward to its first, back to its last") {
            loadout.next(from = pad, steps = 1, available = available) shouldBe pluck
            loadout.next(from = pad, steps = -1, available = available) shouldBe lead
        }
        withClue("empty: through all") { MidiLoadout().next(from = lead, steps = 1, available = available) shouldBe pad }
        withClue("nothing at all: none") { MidiLoadout().next(from = null, steps = 1, available = emptyList()) shouldBe null }
    }

    "kept as its codes and read back: a name in quotes a built-in, else a let; empty and doubled codes dropped" {
        val loadout = MidiLoadout(listOf(lead, pluck))

        loadout.codes shouldBe listOf("lead", "\"pluck\"")
        MidiLoadout.ofCodes(loadout.codes) shouldBe loadout
        MidiLoadout.ofCodes(listOf(" ", "lead", "lead", "\"pluck\"")) shouldBe loadout
    }
})
