/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("unused")

package io.peekandpoke.klang.builtinsongs

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Song

internal val irishLamentSong = Song(
    id = "${BuiltInSongs.PREFIX}-the-synthsale-pipers-farewell",
    title = "The Synthsale Piper's Farewell",
    rpm = 25.0,
    icon = "globe europe",
    code = """
import * from "stdlib"
import * from "sprudel"

// ── Instruments ─────────────────────────────────────────────────────

// Blockflöte — more breath presence
let blockfloete =
    Ign.sine().mul(0.55)
        .plus(Ign.tri().mul(0.22))
        .plus(Ign.saw().mul(0.03).lowpass(2500))
        .plus(Ign.sine().detune(12).mul(0.08))
        .plus(Ign.sine().detune(18.99).mul(0.04).adsr(0.001, 0.15, 0.01, 0.02))
        .plus(Ign.sine().detune(24.01).mul(0.02).adsr(0.001, 0.1, 0.01, 0.01))
        .plus(Ign.sine().detune(36.02).mul(0.01).adsr(0.001, 0.1, 0.01, 0.01))
        .plus(Ign.pinknoise().mul(1.62).lowpass(4000).highpass(800).adsr(0.003, 0.05, 0.01, 0.005))
        .plus(Ign.perlin(10).mul(0.035).lowpass(3500).highpass(1000))
        .plus(Ign.whitenoise().mul(0.28).highpass(4000).lowpass(8000).adsr(0.001, 0.03, 0.0, 0.001))
        .lowpass(3500, 0.8).highpass(300).onepole(3500)
        // 7/2 Hz: what was heard when this was tuned; the engine ran a vibrato over the seven pitched layers seven times per block (fixed 2026-10-07)
        .vibrato(7/2, 0.1)
        // NOTE: `.analog(5)` was here and INERT (receiver was the Vibrato wrapper).
        .pitchEnvelope(0.15, x => x.adsr(0.01, 0.03, 0, 0))
        .adsr(0.01, 0.08, 0.5, 0.1)
        .classic()

// Guitar — more sustain and release
let fingerpick =
    Ign.pluck(x => x.feedback(0.99).brightness(0.45).pickPosition(0.5))
        .plus(Ign.sine().mul(0.12))
        .plus(Ign.sine().detune(-12).mul(0.1))
        .lowpass(2800)
        .highpass(120)
        // NOTE: `.analog(3)` sat here and was INERT — the receiver was the Highpass
        // wrapper, and the old generic analog() silently returned it unchanged. Removed
        // rather than moved: putting it on Ign.pluck(...) would ADD drift never heard.
        .adsr(0.003, 0.5, 0.5, 0.5)
        .classic()

// Pizzicato contrabass
let contrabass =
  Ign.pluck(x => x.feedback(0.995).brightness(0.25).pickPosition(0.55).stiffness(0.05))
    .pitchEnvelope(0.5, x => x.adsr(0.003, 0.02, 0, 0))
    .plus(Ign.pluck(x => x.feedback(0.995).brightness(0.25).pickPosition(0.55).stiffness(0.05)).detune(0.05).mul(0.15))
    .plus(Ign.sine().detune(0.01).lowpass(200).mul(0.3).adsr(0.005, 0.6, 0.0, 0.15))
    .plus(Ign.tri().lowpass(1200).mul(0.15).adsr(0.005, 0.3, 0.0, 0.05))
    .plus(Ign.brownnoise().lowpass(600).mul(0.06).adsr(0.001, 0.04, 0.0, 0.01))
    .plus(Ign.crackle(0.03).lowpass(1000).highpass(100).mul(0.008))
    .lowpass(Ign.constant(300).plus(Ign.constant(1200).adsr(0.005, 0.2, 0.0, 0.05)))
    // NOTE: `.analog(2)` was here and INERT (receiver was the OnePoleLowpass wrapper).
    .highpass(30).onepole(600)
    .adsr(0.005, 0.5, 0.0, 0.15)
    .classic()

// ── Part 1: Opening lament (Dm - Gm - C - F) ───────────────────────
let melody1 = note("<[d4 f4 e4 d4] [c4 a4 g4 f4] [d4 g4 f4 e4] [c4 bb4 a4 g4] [d5 c5 bb4 a4] [g4 e4 d4 f4] [a4 g4 f4 e4] [d4 c4 d4 ~]>")
    .sound(blockfloete).adsr(release = 0.1).gain(0.28).orbit(0).reverb(wet = 0.1, size = 3) // every blockfloete use: classic() releases over this, not the flute's own 0.1 s tail; keep them equal
let guitar1 = note("<[d3 ~ a3 ~ d4 ~ f4 ~] [g2 ~ d3 ~ g3 ~ bb3 ~] [c3 ~ g3 ~ c4 ~ e4 ~] [f2 ~ c3 ~ f3 ~ a3 ~] [d3 ~ a3 ~ d4 ~ f4 ~] [g2 ~ d3 ~ g3 ~ bb3 ~] [c3 ~ g3 ~ c4 ~ e4 ~] [f2 ~ c3 ~ f3 ~ a3 ~]>").fast(2)
    .sound(fingerpick).gain(0.3).adsr(0.003, 0.6, 0.2, 0.5).legato(0.8).orbit(1).reverb(wet = 0.25, size = 4) // every fingerpick use: release 0.5, as the guitar's own tail; classic() releases over this, not the tail; keep them equal
let bass1 = note("<[d2@2 ~ ~] [g2@2 ~ ~] [c2@2 ~ ~] [f2@2 ~ ~] [d2@2 ~ ~] [g2@2 ~ ~] [c2@2 ~ ~] [f2@2 ~ ~]>")
    .sound(contrabass).gain(0.4).adsr(0.005, 0.5, 0.0, 0.25).legato(0.9).orbit(2).reverb(wet = 0.08, size = 2)

// ── Part 2: Intensified (Am - Dm - Bb - C), higher melody ──────────
let melody2 = note("<[a5 c6 b5 a5] [d6 c6 a5 g5] [bb5 a5 g5 f5] [g5 e5 c5 a5] [a5 c6 b5 a5] [d6 c6 a5 g5] [bb5 a5 g5 f5] [g5 f5 e5 d5]>")
    .sound(blockfloete).adsr(release = 0.1).gain(0.28).orbit(0).reverb(wet = 0.1, size = 3)
let guitar2 = note("<[a3 e4 c4 a3 e4 c4 a3 e4] [d4 a4 f4 d4 a4 f4 d4 a4] [bb3 f4 d4 bb3 f4 d4 bb3 f4] [c4 g4 e4 c4 g4 e4 c4 g4] [a3 e4 c4 a3 e4 c4 a3 e4] [d4 a4 f4 d4 a4 f4 d4 a4] [bb3 f4 d4 bb3 f4 d4 bb3 f4] [c4 g4 e4 c4 g4 e4 c4 g4]>").fast(2)
    .sound(fingerpick).gain(0.3).adsr(0.003, 0.6, 0.2, 0.5).legato(0.8).orbit(1).reverb(wet = 0.25, size = 4)
let bass2 = note("<[a2@2 ~ ~] [d2@2 ~ ~] [bb2@2 ~ ~] [c2@2 ~ ~] [a2@2 ~ ~] [d2@2 ~ ~] [bb2@2 ~ ~] [c2@2 ~ ~]>")
    .sound(contrabass).gain(0.4).adsr(0.005, 0.5, 0.0, 0.25).legato(0.9).orbit(2).reverb(wet = 0.08, size = 2)

// ── Part 3: Emotional peak ──────────────────────────────────────────
let melody3a = note("<[d6 f6 g6 f6] [e6 c6 a5 g5] [d6 c6 d6 f6] [a5 g5 f5 e5] [d5 a4 d5 f5] [c5 g4 bb4 a4] [d5 c5 a4 g4] [d4@2 ~ ~]>")
    .sound(blockfloete).adsr(release = 0.1).gain(0.28).orbit(0).reverb(wet = 0.1, size = 3)
let melody3b = note("<[f5 d6 e6 d6] [c6 a5 f5 e5] [f5 e5 f5 a5] [f5 e5 d5 c5] [f4 f4 f4 d5] [a4 e4 g4 f4] [f4 e4 f4 e4] [f4@2 ~ ~]>")
    .sound(blockfloete).adsr(release = 0.1).gain(0.18).orbit(0).reverb(wet = 0.1, size = 3)
let melody3 = stack(melody3a, melody3b)
// Guitar: half-speed arpeggios
let guitar3arp = note("<[g3 d4 bb3 g3] [f3 c4 a3 f3] [bb2 f3 d3 bb2] [c3 g3 e3 c3] [d3 a3 f3 d3] [d3 a3 f3 d3] [a2 e3 c3 a2] [d3 a3 f3 d3]>")
    .sound(fingerpick).gain(0.2).adsr(0.003, 0.6, 0.2, 0.5).legato(0.8).orbit(1).reverb(wet = 0.25, size = 4)
// Guitar melody — half speed, panned left
let guitar3mel = note("<[g4@2 f4@2] [f4@2 e4@2] [bb4@2 a4@2] [c5@2 g4@2] [a4@2 g4@2] [g4@2 f4@2] [c4@2 bb3@2] [d4@3 ~]>")
    .sound(fingerpick).gain(0.2).adsr(0.003, 0.6, 0.2, 0.5).legato(1.0).orbit(1).reverb(wet = 0.25, size = 4).pan(0.3)
let guitar3 = stack(guitar3arp, guitar3mel)
let bass3 = note("<[g2@2 ~ ~] [f2@2 ~ ~] [bb2@2 ~ ~] [c2@2 ~ ~] [d2@2 ~ ~] [g2@2 ~ ~] [c2@2 ~ ~] [d2@3 ~ ~]>").struct("x!4")
    .sound(contrabass).gain(0.4).adsr(0.005, 0.5, 0.0, 0.25).legato(0.9).orbit(2).reverb(wet = 0.08, size = 2)

// ── Assemble ────────────────────────────────────────────────────────
let part1 = stack(melody1, guitar1, bass1)
let part2 = stack(melody2, guitar2, bass2)
let part3 = stack(melody3, guitar3, bass3)

arrange([8, part1], [8, part2], [8, part3], [8, part2], [8, part3], [8, part2]).reverb(0.1, 7)

// Composed by: Claude, Gemini, Motor, peekandpoke
            
            
             
            """,
)
