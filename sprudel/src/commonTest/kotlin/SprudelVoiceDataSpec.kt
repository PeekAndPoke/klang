/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.SoundValue

class SprudelVoiceDataSpec : StringSpec({

    "empty companion object has all fields null" {
        val empty = createSprudelVoiceData { }

        empty.note shouldBe null
        empty.freqHz shouldBe null
        empty.gain shouldBe null
        empty.attack shouldBe null
        empty.cutoff shouldBe null
        empty.resonance shouldBe null
        empty.value shouldBe null
    }

    "clone() copies every field (guards the hand-written clone against dropped/swapped fields)" {
        // Every field set to a distinct non-default value. clone() must reproduce all of them; a
        // dropped field would clone to null and a swap would mismatch — either fails data-class equals.
        val populated = populatedVoiceData(0)

        val cloned = populated.clone()

        cloned shouldBe populated
        (cloned === populated) shouldBe false
    }

    "clone() gives the clone its OWN copy of every group and both param maps: a write through the clone never reaches the source" {
        // The doors write in place into the group or bag the event owns, so a clone that shared one would let one
        // event's write land on another's: the aliasing bug class of `VoiceDataAliasingSpec`, per instance here.
        // `cloned shouldBe populated` above cannot see a shared group, the data-class equality is by value.
        val populated = populatedVoiceData(0)

        val cloned = populated.clone()

        cloned.mutableParts().zip(populated.mutableParts()).forEach { (c, p) ->
            withClue(c.first) {
                p.second shouldNotBe null
                c.second shouldNotBeSameInstanceAs p.second
            }
        }

        cloned.writeEveryPart()

        populated shouldBe populatedVoiceData(0)
    }

    "merge() and mergeFrom() hand out no group or param map of either operand, on every branch of the merge helpers" {
        // The three branches of each `mergeSvd*` helper and of the two param-bag merges: base null (the over side is
        // copied), over null (the base side is copied), both set (a fresh group). Returning the operand's own
        // instance on either of the first two is the aliasing a `_liftData` control event spreads over every
        // source event it covers.
        fun empty() = createSprudelVoiceData { }
        fun full0() = populatedVoiceData(0)
        fun full1() = populatedVoiceData(1000)

        val cases = listOf(
            Triple("left empty (base null)", ::empty, ::full1),
            Triple("right empty (over null)", ::full0, ::empty),
            Triple("both set", ::full0, ::full1),
        )

        for ((name, left, right) in cases) {
            withClue("merge(), $name") {
                val l = left()
                val r = right()
                val merged = l.merge(r)

                merged.shouldOwnNothingOf(l)
                merged.shouldOwnNothingOf(r)

                merged.writeEveryPart()

                l shouldBe left()
                r shouldBe right()
            }

            withClue("mergeFrom(), $name") {
                val target = left()
                val r = right()

                target.mergeFrom(r)

                target.shouldOwnNothingOf(r)

                target.writeEveryPart()

                r shouldBe right()
            }
        }
    }

    "mergeFrom() matches merge() (guards the in-place merge against the copy-based merge)" {
        // Two fully-populated instances with distinct values. With every field of `b` non-null, the
        // copy-based merge() takes all of b's values (patternId stays a's). mergeFrom() must reproduce
        // that exactly — a dropped/wrong field would diverge from the merge() oracle.
        val a = populatedVoiceData(0)
        val b = populatedVoiceData(1000)

        val viaMerge = a.merge(b)
        val viaMergeFrom = a.clone().also { it.mergeFrom(b) }

        viaMergeFrom shouldBe viaMerge
    }

    "merge() completeness has an INDEPENDENT oracle for the phaser group (incl. phaserFloor)" {
        // The parity row above compares mergeFrom against merge, but both flow through the
        // SAME mergeSvdPhaser helper — a field dropped from the helper changes both sides
        // identically and stays green. These rows pin the helper against the inputs.
        val a = populatedVoiceData(0)
        val b = populatedVoiceData(1000)
        val merged = a.merge(b)
        merged.phaserRate shouldBe b.phaserRate
        merged.phaserDepth shouldBe b.phaserDepth
        merged.phaserCenter shouldBe b.phaserCenter
        merged.phaserSweep shouldBe b.phaserSweep
        merged.phaserFloor shouldBe b.phaserFloor

        // and the base side survives an empty over side (the `?: base` half)
        val kept = a.merge(createSprudelVoiceData { })
        kept.phaserFloor shouldBe a.phaserFloor
    }

    "merge() completeness has an INDEPENDENT oracle for the pitch envelope group (sustain and the curves)" {
        // As the phaser row: mergeFrom and merge share mergeSvdPitchEnv, so only the inputs can see a field
        // the helper drops, swaps or takes from the wrong side.
        val a = populatedVoiceData(0)
        val b = populatedVoiceData(1000)

        withClue("the seeds give every curve a different value on each side") {
            a.pAttackCurve shouldNotBe b.pAttackCurve
            a.pDecayCurve shouldNotBe b.pDecayCurve
            a.pReleaseCurve shouldNotBe b.pReleaseCurve
        }

        val merged = a.merge(b)

        merged.pAttack shouldBe b.pAttack
        merged.pDecay shouldBe b.pDecay
        merged.pSustain shouldBe b.pSustain
        merged.pRelease shouldBe b.pRelease
        merged.pEnv shouldBe b.pEnv
        merged.pAttackCurve shouldBe b.pAttackCurve
        merged.pDecayCurve shouldBe b.pDecayCurve
        merged.pReleaseCurve shouldBe b.pReleaseCurve

        // and the base side survives an over side with an empty pitch envelope group (the `?: base` half)
        val kept = a.merge(createSprudelVoiceData { pEnv = 5.0 })

        kept.pEnv shouldBe 5.0
        kept.pAttack shouldBe a.pAttack
        kept.pDecay shouldBe a.pDecay
        kept.pSustain shouldBe a.pSustain
        kept.pRelease shouldBe a.pRelease
        kept.pAttackCurve shouldBe a.pAttackCurve
        kept.pDecayCurve shouldBe a.pDecayCurve
        kept.pReleaseCurve shouldBe a.pReleaseCurve
    }

    "merge() completeness has an INDEPENDENT oracle for the four filter groups' curves" {
        // mergeFrom and merge share mergeSvdFilter, so only the inputs can see a curve the helper drops, swaps or
        // takes from the wrong side. Read through the flat accessors, one filter group at a time.
        val a = populatedVoiceData(0)
        val b = populatedVoiceData(1000)

        fun curves(d: SprudelVoiceData): List<AdsrCurve?> = listOf(
            d.lpAttackCurve, d.lpDecayCurve, d.lpReleaseCurve,
            d.hpAttackCurve, d.hpDecayCurve, d.hpReleaseCurve,
            d.bpAttackCurve, d.bpDecayCurve, d.bpReleaseCurve,
            d.nfAttackCurve, d.nfDecayCurve, d.nfReleaseCurve,
        )

        withClue("the seeds give every curve a different value on each side") {
            curves(a).zip(curves(b)).forEach { (x, y) -> x shouldNotBe y }
        }

        curves(a.merge(b)) shouldBe curves(b)

        // and the base side survives an over side whose filter groups hold a cutoff only (the `?: base` half)
        val kept = a.merge(
            createSprudelVoiceData {
                cutoff = 1.0; hcutoff = 2.0; bandf = 3.0; notchf = 4.0
            },
        )

        curves(kept) shouldBe curves(a)
    }

    "merge() completeness has an INDEPENDENT oracle for the amplitude envelope's curves" {
        // The same gap the filter row closes, found by its mutation run: mergeSvdAdsr's three curve lines
        // could be dropped, swapped or read base-first with every row green, because both seeds carried the
        // same fixed curves.
        val a = populatedVoiceData(0)
        val b = populatedVoiceData(1000)

        fun curves(d: SprudelVoiceData): List<AdsrCurve?> = listOf(d.attackCurve, d.decayCurve, d.releaseCurve)

        withClue("the seeds give every curve a different value on each side") {
            curves(a).zip(curves(b)).forEach { (x, y) -> x shouldNotBe y }
        }

        curves(a.merge(b)) shouldBe curves(b)
        curves(a.merge(createSprudelVoiceData { attack = 1.0 })) shouldBe curves(a)
    }

    "can create SprudelVoiceData with basic fields" {
        val data = createSprudelVoiceData {
            note = "c4"
            freqHz = 440.0
            gain = 0.8
        }

        data.note shouldBe "c4"
        data.freqHz shouldBe 440.0
        data.gain shouldBe 0.8
    }

    // The voice doors cross the wire as `classic()` slot keys (phase 3 step 8); the rules of the translation
    // are pinned in `ClassicSlotParamsSpec`. These rows keep the per-door shape of the old typed rows.

    "toVoiceData() sends the ADSR fields as the adsr.* slots, and no typed envelope" {
        val data = createSprudelVoiceData {
            attack = 0.01
            decay = 0.1
            sustain = 0.7
            release = 0.3
        }

        val voiceData = data.toVoiceData()

        voiceData.oscParams shouldBe mapOf("adsr.attack" to 0.01, "adsr.decay" to 0.1, "adsr.sustain" to 0.7, "adsr.release" to 0.3)
    }

    "toVoiceData() sends the LPF fields as the lpf.* slots" {
        val data = createSprudelVoiceData {
            cutoff = 1000.0
            resonance = 1.5
        }

        val voiceData = data.toVoiceData()

        voiceData.oscParams shouldBe mapOf("lpf.freq" to 1000.0, "lpf.q" to 1.5)
    }

    "toVoiceData() sends the HPF fields as the hpf.* slots" {
        val data = createSprudelVoiceData {
            hcutoff = 500.0
            hresonance = 2.0
        }

        val voiceData = data.toVoiceData()

        voiceData.oscParams shouldBe mapOf("hpf.freq" to 500.0, "hpf.q" to 2.0)
    }

    "toVoiceData() sends the BPF fields as the bpf.* slots (no passes)" {
        val data = createSprudelVoiceData {
            bandf = 750.0
            bandq = 1.2
        }

        val voiceData = data.toVoiceData()

        voiceData.oscParams shouldBe mapOf("bpf.freq" to 750.0, "bpf.q" to 1.2)
    }

    "toVoiceData() sends the Notch fields as the notch.* slots (no passes)" {
        val data = createSprudelVoiceData {
            notchf = 600.0
            nresonance = 0.8
        }

        val voiceData = data.toVoiceData()

        voiceData.oscParams shouldBe mapOf("notch.freq" to 600.0, "notch.q" to 0.8)
    }

    "toVoiceData() sends every filter kind under its own slots, each with its own resonance" {
        val data = createSprudelVoiceData {
            cutoff = 1000.0
            resonance = 1.5
            hcutoff = 500.0
            hresonance = 2.0
            bandf = 750.0
            bandq = 1.2
        }

        val voiceData = data.toVoiceData()

        voiceData.oscParams shouldBe mapOf(
            "hpf.freq" to 500.0, "hpf.q" to 2.0,
            "bpf.freq" to 750.0, "bpf.q" to 1.2,
            "lpf.freq" to 1000.0, "lpf.q" to 1.5,
        )
    }

    "toVoiceData() sends the four voice filters as slots and the vowel field as nothing (the vowel door writes its slot)" {
        val data = createSprudelVoiceData {
            cutoff = 1000.0
            hcutoff = 500.0
            bandf = 750.0
            notchf = 600.0
            vowel = "a"
        }

        val voiceData = data.toVoiceData()

        voiceData.oscParams shouldBe mapOf("lpf.freq" to 1000.0, "hpf.freq" to 500.0, "bpf.freq" to 750.0, "notch.freq" to 600.0)
        voiceData.katalystParams shouldBe null
    }

    "toVoiceData() sends no resonance the pattern did not write: the slot's default (0.707) builds it" {
        val data = createSprudelVoiceData {
            cutoff = 1000.0
            resonance = null // No resonance specified
        }

        val voiceData = data.toVoiceData()

        voiceData.oscParams?.get("lpf.freq") shouldBe 1000.0
        voiceData.oscParams?.containsKey("lpf.q") shouldBe false // the classic() slot default, 0.707 (LangDefaultQSpec)
    }

    "toVoiceData() maps all basic fields correctly" {
        val data = createSprudelVoiceData {
            note = "c4"
            freqHz = 440.0
            scale = "major"
            gain = 0.8
            legato = 0.9
            bank = "MPC60"
            sound = SoundValue.Named("bd")
            soundIndex = 2
            oscParams = paramBagOf("density" to 0.5, "panSpread" to 0.3, "spread" to 0.1, "voices" to 3.0)
            accelerate = 0.05
            vibrato = 0.2
            vibratoMod = 0.4
            distort = 0.3
            coarse = 1.0
            crush = 4.0
            cylinder = 1
            pan = 0.5
            katalystParams = paramBagOf(
                "delay.wet" to 0.3, "delay.time" to 0.25, "delay.feedback" to 0.5,
                "reverb.wet" to 0.7, "reverb.size" to 5.0,
            )
            begin = 0.0
            end = 1.0
            speed = 1.0
            loop = true
            cut = 1
        }

        val voiceData = data.toVoiceData()

        voiceData.note shouldBe "c4"
        voiceData.freqHz shouldBe 440.0
        voiceData.gain shouldBe 0.8
        voiceData.legato shouldBe 0.9
        voiceData.bank shouldBe "MPC60"
        voiceData.sound shouldBe "bd"
        voiceData.soundIndex shouldBe 2
        voiceData.oscParams?.get("density") shouldBe 0.5
        voiceData.oscParams?.get("panSpread") shouldBe 0.3
        voiceData.oscParams?.get("spread") shouldBe 0.1
        voiceData.oscParams?.get("voices") shouldBe 3.0
        voiceData.accelerate shouldBe 0.05
        voiceData.vibrato shouldBe 0.2
        voiceData.vibratoMod shouldBe 0.4
        voiceData.oscParams?.get("distort.amount") shouldBe 0.3
        voiceData.oscParams?.get("coarse.amount") shouldBe 1.0
        voiceData.oscParams?.get("crush.amount") shouldBe 4.0
        voiceData.cylinder shouldBe 1
        voiceData.pan shouldBe 0.5
        voiceData.katalystParams?.get("delay.wet") shouldBe 0.3
        voiceData.katalystParams?.get("delay.time") shouldBe 0.25
        voiceData.katalystParams?.get("delay.feedback") shouldBe 0.5
        voiceData.katalystParams?.get("reverb.wet") shouldBe 0.7
        voiceData.katalystParams?.get("reverb.size") shouldBe 5.0
        voiceData.oscParams?.get("begin") shouldBe 0.0
        voiceData.oscParams?.get("end") shouldBe 1.0
        voiceData.oscParams?.get("speed") shouldBe 1.0
        voiceData.oscParams?.get("loop") shouldBe 1.0
        voiceData.cut shouldBe 1
    }

    "toVoiceData() carries the fm group, solo and the pattern id (as sourceId): the wire fields no door spec reads" {
        val wire = createSprudelVoiceData {
            fmh = 2.1; fmAttack = 0.011; fmDecay = 0.012; fmSustain = 0.51; fmEnv = 3.3
            solo = 0.7; patternId = "pid-1"
        }.toVoiceData()

        wire.fmh shouldBe 2.1
        wire.fmAttack shouldBe 0.011
        wire.fmDecay shouldBe 0.012
        wire.fmSustain shouldBe 0.51
        wire.fmEnv shouldBe 3.3
        wire.solo shouldBe 0.7
        wire.sourceId shouldBe "pid-1"
    }

    "a script call stamps all its events with one sourceId, from its location: one per call, the same on every compile" {
        // The backend's solo and cut logic groups voices by this id (`VoiceSchedulerSoloCutSpec`), so it must not
        // move when the same code is compiled again, and two calls must not share one.
        val code = """stack(note("c3 e3"), sound("bd hh"))"""

        fun idsByCall(): Pair<Set<String?>, Set<String?>> {
            val events = SprudelPattern.compile(code)!!.queryArc(0.0, 1.0)
            val (notes, sounds) = events.partition { it.data.note != null }

            return notes.map { it.data.toVoiceData().sourceId }.toSet() to sounds.map { it.data.toVoiceData().sourceId }.toSet()
        }

        val (noteIds, soundIds) = idsByCall()

        noteIds.size shouldBe 1
        soundIds.size shouldBe 1
        noteIds.single() shouldNotBe null
        noteIds shouldNotBe soundIds
        idsByCall() shouldBe (noteIds to soundIds)
    }

    "copy() creates new instance with updated fields" {
        val original = createSprudelVoiceData {
            note = "c4"
            gain = 0.8
        }

        val modified = original.copy(
            note = "d4",
            freqHz = 440.0
        )

        // Original unchanged
        original.note shouldBe "c4"
        original.gain shouldBe 0.8
        original.freqHz shouldBe null

        // Modified has changes
        modified.note shouldBe "d4"
        modified.gain shouldBe 0.8
        modified.freqHz shouldBe 440.0
    }
})

/**
 * Builds a [SprudelVoiceData] with EVERY field set to a distinct non-null value, offset by [seed] so
 * two instances can be made fully distinct. Used to guard `clone()` and `mergeFrom()` completeness:
 * every field is exercised, so a dropped or swapped field fails data-class equality. Every group and
 * both param maps exist, so the aliasing rows have an instance of each to check.
 */
private fun populatedVoiceData(seed: Int): SprudelVoiceData {
    val b = seed.toDouble()
    return createSprudelVoiceData {
        note = "note$seed"; freqHz = b + 1; scale = "scale$seed"; chord = "chord$seed"
        gain = b + 2; legato = b + 3; velocity = b + 4
        bank = "bank$seed"; sound = SoundValue.Named("snd$seed"); soundIndex = seed + 6
        oscParams = paramBagOf("k$seed" to b + 7)
        katalystParams = paramBagOf("reverb.size" to b + 7.5, "room$seed" to b + 7.6)
        attack = b + 8; decay = b + 9; sustain = b + 10; release = b + 11
        // Seed-picked like the pitch and filter curves, so a merge row sees each amplitude curve differ per side.
        attackCurve = AdsrCurve.entries[(seed + 9) % 6]; decayCurve = AdsrCurve.entries[(seed + 10) % 6]
        releaseCurve = AdsrCurve.entries[(seed + 11) % 6]
            adsrOn = false
        accelerate = b + 12; vibrato = b + 13; vibratoMod = b + 14
        pAttack = b + 15; pDecay = b + 16; pRelease = b + 17; pEnv = b + 18; pSustain = b + 19
        // Picked from the seed, so the two sides of a merge row differ in every curve (seed 0: Linear, Square,
        // Cube; seed 1000: InvSquare, Exponential, Linear) and a dropped or swapped curve merge shows.
        pAttackCurve = AdsrCurve.entries[seed % 6]
        pDecayCurve = AdsrCurve.entries[(seed + 1) % 6]
        pReleaseCurve = AdsrCurve.entries[(seed + 2) % 6]
        fmh = b + 21; fmAttack = b + 22; fmDecay = b + 23; fmSustain = b + 24; fmEnv = b + 25
        distort = b + 26; distortShape = "ds$seed"; distortOversample = seed + 27
        coarse = b + 28; coarseOversample = seed + 29; crush = b + 30; crushOversample = seed + 31
        phaserRate = b + 32; phaserDepth = b + 33; phaserCenter = b + 34; phaserSweep = b + 35; phaserFloor = b + 35.5
        tremoloRate = b + 36; tremoloDepth = b + 37
        tremoloShape = "ts$seed"
        cutoff = b + 43; resonance = b + 44; hcutoff = b + 45; hresonance = b + 46; lpPasses = b + 46.2; hpPasses = b + 46.4
        bandf = b + 47; bandq = b + 48; notchf = b + 49; nresonance = b + 50
        lpattack = b + 51; lpdecay = b + 52; lpsustain = b + 53; lprelease = b + 54; lpenv = b + 55
        hpattack = b + 56; hpdecay = b + 57; hpsustain = b + 58; hprelease = b + 59; hpenv = b + 60
        bpattack = b + 61; bpdecay = b + 62; bpsustain = b + 63; bprelease = b + 64; bpenv = b + 65
        nfattack = b + 66; nfdecay = b + 67; nfsustain = b + 68; nfrelease = b + 69; nfenv = b + 70
        // The filter curves, picked from the seed like the pitch curves, and each filter offset by one more, so the
        // two sides of a merge differ in every curve and no two filters share a curve at the same stage.
        lpAttackCurve = AdsrCurve.entries[(seed + 3) % 6]; lpDecayCurve = AdsrCurve.entries[(seed + 4) % 6]
        lpReleaseCurve = AdsrCurve.entries[(seed + 5) % 6]
        hpAttackCurve = AdsrCurve.entries[(seed + 4) % 6]; hpDecayCurve = AdsrCurve.entries[(seed + 5) % 6]
        hpReleaseCurve = AdsrCurve.entries[(seed + 6) % 6]
        bpAttackCurve = AdsrCurve.entries[(seed + 5) % 6]; bpDecayCurve = AdsrCurve.entries[(seed + 6) % 6]
        bpReleaseCurve = AdsrCurve.entries[(seed + 7) % 6]
        nfAttackCurve = AdsrCurve.entries[(seed + 6) % 6]; nfDecayCurve = AdsrCurve.entries[(seed + 7) % 6]
        nfReleaseCurve = AdsrCurve.entries[(seed + 8) % 6]
        cylinder = seed + 71; pan = b + 72
        begin = b + 81; end = b + 82; speed = b + 83; unit = "u$seed"; loop = true; cut = seed + 84
        vowel = "v$seed"; vowelMix = b + 85; vowelFloor = b + 85.5
        body = "bo$seed"; bodyMix = b + 86; bodyFloor = b + 86.5
        solo = b + 88; patternId = "pid$seed"
        value = SprudelVoiceValue.Num(b + 87)
        tags = setOf("t$seed")
        tweaks = listOf("tw$seed")
        cull = b + 94
    }
}

/** No group or param map of [other] is one of this instance's own: compared part by part, by identity. */
private fun SprudelVoiceData.shouldOwnNothingOf(other: SprudelVoiceData) {
    mutableParts().zip(other.mutableParts()).forEach { (mine, theirs) ->
        if (mine.second != null) {
            withClue(mine.first) { mine.second shouldNotBeSameInstanceAs theirs.second }
        }
    }
}
