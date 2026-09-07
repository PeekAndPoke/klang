/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.sprudel.SprudelPattern

/**
 * Retired single-slot doors (`docs/tasks/sprudel-field-accessors.md`). Neither the member nor the
 * top-level form may dispatch; if one resolves again, an old door crept back in.
 *
 * - 2026-09-07, envelope: `adsr(attack = ...)` sets a slot and `adsr.attack` reads it.
 * - 2026-09-07, effects: `room`, `delay`, `phaser`, `tremolo`, `distort`, `crush` and `coarse`
 *   are objects with named slots; the per-knob doors and their aliases went with them.
 * - 2026-09-07, filters: `lpf`, `hpf`, `bpf`, `notch` are objects with the resonance, cascade,
 *   envelope depth and envelope stages as slots; `adsrCurves` carries the setter only and the
 *   singular `adsrCurve` is gone from every surface (the ignitor door is guarded in
 *   `LangAdsrCurveDefaultSpec`).
 * - 2026-09-07, batch G: `compressor`, `unison`, `duck`, `vibrato`, `penv`, `fm`, `vowel`, `body`
 *   are objects with named slots; `comp`, `uni`, `vib`, `pamt` stay as their aliases.
 */
class LangRetiredDoorsSpec : StringSpec({
    val envelope = listOf("attack", "decay", "sustain", "release")
    val effects = listOf(
        "roomWet", "roomsize", "rsize", "sz", "size", "roomfade", "rfade", "roomlp", "rlp", "roomdim", "rdim",
        "delayWet", "delaytime", "delayfeedback", "delayfb", "dfb", "delaycap", "dcap",
        "ph", "phaserWet", "phasercenter", "phc", "phasersweep", "phs", "phaserFloor",
        "tremolosync", "tremsync", "tremolodepth", "tremdepth", "tremoloskew", "tremskew", "tremolophase", "tremphase",
        "tremoloshape", "tremshape",
        "dist", "distos", "distortOversampling", "distortshape", "distshape", "dshape",
        "crushos", "crushOversampling", "coarseos", "coarseOversampling",
    )

    val filters = listOf(
        "lpq", "lpx", "lpe", "lpadsr", "hpq", "hpx", "hpe", "hpadsr", "bpq", "bpe", "bpadsr",
        "notchf", "nresonance", "nres", "notchq", "ntq", "ntf", "nfadsr",
        "nfattack", "nfa", "nfdecay", "nfd", "nfsustain", "nfs", "nfrelease", "nfr", "nfenv", "nfe",
        "adsrCurve",
    )

    val batchG = listOf(
        "vibratoMod", "pattack", "patt", "pdecay", "pdec", "prelease", "prel", "pcurve", "pcrv", "panchor", "panc",
        "fmenv", "fmmod", "fmh", "fmattack", "fmatt", "fmdecay", "fmdec", "fmsustain", "fmsus",
        "duckorbit", "duckattack", "duckatt", "duckdepth", "voices", "spread", "panSpread",
        "vowelWet", "vowelFloor", "bodyWet", "bodyFloor",
    )

    (envelope + effects + filters + batchG).forEach { name ->
        "retired door '$name' fails as a member call" {
            val error = shouldThrowAny { SprudelPattern.compile("""note("c4").$name(0.5)""") }
            withClue("error should name the missing method") { (error.message ?: "") shouldContain name }
        }

        "retired door '$name' fails as a top-level call" {
            val error = shouldThrowAny { SprudelPattern.compile("""note("c4").apply($name(0.5))""") }
            withClue("error should name the missing function") { (error.message ?: "") shouldContain name }
        }
    }
})
