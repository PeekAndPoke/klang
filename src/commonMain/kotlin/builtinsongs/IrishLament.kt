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
let blockfloete = Osc.register("blockfloete",
    Osc.sine().mul(0.55)
        .plus(Osc.triangle().mul(0.22))
        .plus(Osc.saw().mul(0.01).lowpass(2500))
        .plus(Osc.sine().detune(12).mul(0.04))
        .plus(Osc.sine().detune(19.02).mul(0.02).adsr(0.001, 0.15, 0.0, 0.02))
        .plus(Osc.sine().detune(24).mul(0.015).adsr(0.001, 0.1, 0.0, 0.01))
        .plus(Osc.pinknoise().mul(0.12).lowpass(4000).highpass(800).adsr(0.003, 0.05, 0.0, 0.005))
        .plus(Osc.perlin(10).mul(0.035).lowpass(3500).highpass(1000))
        .plus(Osc.whitenoise().mul(0.08).highpass(4000).lowpass(8000).adsr(0.001, 0.03, 0.0, 0.001))
        .lowpass(2800, 0.8).highpass(150).warmth(2500)
        .vibrato(5, 0.003)
        .analog(0.06)
        .pitchEnvelope(0.5, 0.005, 0.03)
        .adsr(0.015, 0.08, 0.7, 0.08)
)

// Guitar — more sustain and release
let fingerpick = Osc.register("fingerpick",
    Osc.pluck(Osc.freq(), 0.99, 0.45, 0.5)
        .plus(Osc.sine().mul(0.12))
        .plus(Osc.sine().detune(-12).mul(0.03))
        .lowpass(2800)
        .highpass(120)
        .adsr(0.003, 0.6, 0.2, 0.4)
)

// Pizzicato contrabass
let contrabass = Osc.register("contrabass",
  Osc.pluck(Osc.freq(), 0.995, 0.25, 0.55, 0.05)
    .pitchEnvelope(0.5, 0.003, 0.02)
    .plus(Osc.pluck(Osc.freq(), 0.995, 0.25, 0.55, 0.05).detune(0.05).mul(0.15))
    .plus(Osc.sine().detune(0.01).lowpass(200).mul(0.3).adsr(0.005, 0.6, 0.0, 0.15))
    .plus(Osc.triangle().lowpass(1200).mul(0.15).adsr(0.005, 0.3, 0.0, 0.05))
    .plus(Osc.brownnoise().lowpass(600).mul(0.06).adsr(0.001, 0.04, 0.0, 0.01))
    .plus(Osc.crackle(0.03).lowpass(1000).highpass(100).mul(0.008))
    .lowpass(Osc.constant(300).plus(Osc.constant(1200).adsr(0.005, 0.2, 0.0, 0.05)))
    .highpass(30).warmth(600).analog(0.04)
    .adsr(0.005, 0.5, 0.0, 0.15)
)

// ── Part 1: Opening lament (Dm - Gm - C - F) ───────────────────────
let melody1 = note("<[d4 f4 e4 d4] [c4 a4 g4 f4] [d4 g4 f4 e4] [c4 bb4 a4 g4] [d5 c5 bb4 a4] [g4 e4 d4 f4] [a4 g4 f4 e4] [d4 c4 d4 ~]>")
    .sound(blockfloete).gain(0.28).orbit(0).room(0.1).rsize(3)
let guitar1 = note("<[d3 ~ a3 ~ d4 ~ f4 ~] [g2 ~ d3 ~ g3 ~ bb3 ~] [c3 ~ g3 ~ c4 ~ e4 ~] [f2 ~ c3 ~ f3 ~ a3 ~] [d3 ~ a3 ~ d4 ~ f4 ~] [g2 ~ d3 ~ g3 ~ bb3 ~] [c3 ~ g3 ~ c4 ~ e4 ~] [f2 ~ c3 ~ f3 ~ a3 ~]>").fast(2)
    .sound(fingerpick).gain(0.3).adsr("0.003:0.6:0.2:0.4").legato(0.8).orbit(1).room(0.25).rsize(4)
let bass1 = note("<[d2@2 ~ ~] [g2@2 ~ ~] [c2@2 ~ ~] [f2@2 ~ ~] [d2@2 ~ ~] [g2@2 ~ ~] [c2@2 ~ ~] [f2@2 ~ ~]>")
    .sound(contrabass).gain(0.25).adsr("0.005:0.5:0.0:0.15").legato(0.9).orbit(2).room(0.08).rsize(2)

// ── Part 2: Intensified (Am - Dm - Bb - C), higher melody ──────────
let melody2 = note("<[a5 c6 b5 a5] [d6 c6 a5 g5] [bb5 a5 g5 f5] [g5 e5 c5 a5] [a5 c6 b5 a5] [d6 c6 a5 g5] [bb5 a5 g5 f5] [g5 f5 e5 d5]>")
    .sound(blockfloete).gain(0.28).orbit(0).room(0.1).rsize(3)
let guitar2 = note("<[a3 e4 c4 a3 e4 c4 a3 e4] [d4 a4 f4 d4 a4 f4 d4 a4] [bb3 f4 d4 bb3 f4 d4 bb3 f4] [c4 g4 e4 c4 g4 e4 c4 g4] [a3 e4 c4 a3 e4 c4 a3 e4] [d4 a4 f4 d4 a4 f4 d4 a4] [bb3 f4 d4 bb3 f4 d4 bb3 f4] [c4 g4 e4 c4 g4 e4 c4 g4]>").fast(2)
    .sound(fingerpick).gain(0.3).adsr("0.003:0.6:0.2:0.4").legato(0.8).orbit(1).room(0.25).rsize(4)
let bass2 = note("<[a2@2 ~ ~] [d2@2 ~ ~] [bb2@2 ~ ~] [c2@2 ~ ~] [a2@2 ~ ~] [d2@2 ~ ~] [bb2@2 ~ ~] [c2@2 ~ ~]>")
    .sound(contrabass).gain(0.25).adsr("0.005:0.5:0.0:0.15").legato(0.9).orbit(2).room(0.08).rsize(2)

// ── Part 3: Emotional peak ──────────────────────────────────────────
let melody3a = note("<[d6 f6 g6 f6] [e6 c6 a5 g5] [d6 c6 d6 f6] [a5 g5 f5 e5] [d5 a4 d5 f5] [c5 g4 bb4 a4] [d5 c5 a4 g4] [d4@2 ~ ~]>")
    .sound(blockfloete).gain(0.28).orbit(0).room(0.1).rsize(3)
let melody3b = note("<[f5 d6 e6 d6] [c6 a5 f5 e5] [f5 e5 f5 a5] [f5 e5 d5 c5] [f4 f4 f4 d5] [a4 e4 g4 f4] [f4 e4 f4 e4] [f4@2 ~ ~]>")
    .sound(blockfloete).gain(0.18).orbit(0).room(0.1).rsize(3)
let melody3 = stack(melody3a, melody3b)
// Guitar: half-speed arpeggios
let guitar3arp = note("<[g3 d4 bb3 g3] [f3 c4 a3 f3] [bb2 f3 d3 bb2] [c3 g3 e3 c3] [d3 a3 f3 d3] [d3 a3 f3 d3] [a2 e3 c3 a2] [d3 a3 f3 d3]>")
    .sound(fingerpick).gain(0.2).adsr("0.003:0.6:0.2:0.4").legato(0.8).orbit(1).room(0.25).rsize(4)
// Guitar melody — half speed, panned left
let guitar3mel = note("<[g4@2 f4@2] [f4@2 e4@2] [bb4@2 a4@2] [c5@2 g4@2] [a4@2 g4@2] [g4@2 f4@2] [c4@2 bb3@2] [d4@3 ~]>")
    .sound(fingerpick).gain(0.2).adsr("0.003:0.6:0.2:0.4").legato(1.0).orbit(1).room(0.25).rsize(4).pan(0.3)
let guitar3 = stack(guitar3arp, guitar3mel)
let bass3 = note("<[g2@2 ~ ~] [f2@2 ~ ~] [bb2@2 ~ ~] [c2@2 ~ ~] [d2@2 ~ ~] [g2@2 ~ ~] [c2@2 ~ ~] [d2@3 ~ ~]>")
    .sound(contrabass).gain(0.25).adsr("0.005:0.5:0.0:0.15").legato(0.9).orbit(2).room(0.08).rsize(2)

// ── Assemble ────────────────────────────────────────────────────────
let part1 = stack(melody1, guitar1, bass1)
let part2 = stack(melody2, guitar2, bass2)
let part3 = stack(melody3, guitar3, bass3)

arrange([8, part1], [8, part2], [8, part3], [8, part2], [8, part3], [8, part2]).room("0.15:10")

// Composed by: Claude, Gemini, Motör, peekandpoke
            """,
)
