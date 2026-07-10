/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("unused")

package io.peekandpoke.klang.builtinsongs

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Song

internal val sandsturmSong = Song(
    id = "${BuiltInSongs.PREFIX}-synthsturm",
    title = "Synthsturm",
    rpm = 34.0,
    icon = "wind",
    code = """
import * from "stdlib"
import * from "sprudel"

// ── Wüstensturm — a Sandstorm homage ────────────────────────────────
// B minor, ~136 BPM (rpm 34). A gated supersaw lead hammering straight
// 16th-note stabs over a driving four-on-the-floor trance beat. The whole
// kit is a custom Osc ignitor, so it renders offline too.

// ── Instruments (custom ignitors) ───────────────────────────────────
// THE "du-du-du" — the real Sandstorm lead recipe (per Darude/JS16 + JP-8080):
// NOT a supersaw. One saw + a square layered +31 semitones up for the piercing
// top, a resonant lowpass, then HEAVY distortion — the actual signature
// (originally a Cubase distortion plugin overdriving a cheap mixer preamp).
// Long decay + zero sustain; the 16th "gate" comes from mono-style legato.
let lead = Osc.saw().mul(0.5)
    .plus(Osc.square().detune(31).mul(0.5))
    .lowpass(4200, 2.5)
    .distort(1.0, "soft", 4)
    .adsr(0.001, 0.7, 0.0, 0.04)

// The fizzy supersaw PAD = the actual JP-8080 "Sandstorm" boot preset the track
// is named after — bright + wide, sits under the lead as syncopated stabs.
let pad = Osc.supersaw(Osc.freq(), 9, 0.3).analog(0.25)
    .lowpass(Osc.sine(0.1).plus(1).times(1500).plus(2200))
    .adsr(0.008, 0.25, 0.3, 0.2)

// Rolling saw bass — fast filter env (sidechain pump added at pattern level)
let bass = Osc.saw()
    .lowpass(Osc.constant(400).plus(Osc.constant(2200).adsr(0.002, 0.08, 0.0, 0.04)))
    .adsr(0.004, 0.09, 0.0, 0.04)

// Synth kit
let kick = Osc.sine().pitchEnvelope(48, 0.001, 0.05).adsr(0.001, 0.22, 0.0, 0.02)
let hat  = Osc.whitenoise().highpass(8000).adsr(0.001, 0.035, 0.0, 0.02)
let ohat = Osc.whitenoise().highpass(7000).adsr(0.001, 0.12, 0.05, 0.10)
let clap = Osc.whitenoise().bandpass(1600, 2).adsr(0.001, 0.09, 0.0, 0.04)
let riser = Osc.pinknoise().highpass(300)

// ── Patterns ────────────────────────────────────────────────────────
let kickPat = note("a1*4").sound(kick).gain(0.95).orbit(0)
let hatPat  = note("c5*16").sound(hat).gain(0.28).orbit(1)
let ohatPat = note("~ c5 ~ c5 ~ c5 ~ c5").sound(ohat).gain(0.3).orbit(1)
let clapPat = note("~ c4 ~ c4").sound(clap).gain(0.5).orbit(1).room(0.2).rsize(3)

let bassPat = note("<[~ b1 ~ b1 ~ b1 ~ b1] [~ g1 ~ g1 ~ g1 ~ g1] [~ d2 ~ d2 ~ d2 ~ d2] [~ a1 ~ a1 ~ a1 ~ a1]>")
    .sound(bass).legato(0.5).gain(saw.fast(4).range(0.65, 0.42)).orbit(2)

// Syncopated chord stabs — 3-3-2 tresillo gate (hits on 16ths 1,4,7), the
// classic dance-floor pad rhythm. Swap the struct string to taste.
let padPat = chord("<Bm G D A>").voicing().sound(pad).struct("x ~ ~ x ~ ~ x ~")
    .legato(0.6).gain(0.20).orbit(3).room(0.35).rsize(6)

// The gated 16th-note hook (B minor). Each cycle = one bar; <...> rotates the 4 bars.
let leadPat = note(`<[b4 b4 b4 b4 b4 b4 a4 b4 b4 b4 b4 b4 b4 b4 d5 b4]
                     [g4 g4 g4 g4 g4 g4 f#4 g4 g4 g4 g4 g4 g4 g4 b4 g4]
                     [d5 d5 d5 d5 d5 d5 b4 d5 d5 d5 d5 d5 d5 d5 f#5 d5]
                     [a4 a4 a4 a4 a4 a4 f#4 a4 a4 a4 a4 a4 a4 a4 c#5 a4]>`)
    .transpose(-36)
    .sound(lead).legato(0.55).gain(0.4).postgain(0.5).orbit(4)
    .delay(0.14).delaytime(pure(3/16).div(cps)).delayfeedback(0.25).room(0.12).rsize(5)

let riserPat = note("c5").fast(2).sound(riser)
    .lpf(saw.range(300, 6000).slow(8)).gain(saw.range(0.0, 0.22).slow(8)).orbit(5)

// ── Sections ────────────────────────────────────────────────────────
let intro  = stack(kickPat, hatPat)
let groove = stack(kickPat, hatPat, ohatPat, clapPat, bassPat)
let build  = stack(groove, padPat, riserPat)
let drop   = stack(groove, padPat, leadPat)

arrange(
  [8, intro],
  [8, groove],
  [8, build],
  [16, drop],
  [8, groove],
  [8, build],
  [16, drop]
).compressor("-10:2:6:0.01:0.1").room(0.12).rsize(6)

// Inspired by: Darude — Sandstorm
// Composed by: Claude, Motör, peekandpoke
//
// How the "du-du-du" lead was really made (distorted saw, NOT a supersaw):
//   https://www.musicradar.com/artists/when-you-turn-on-the-roland-jp-8080-the-first-sound-that-comes-up-is-called-sandstorm-how-darude-created-the-era-defining-trance-anthem-thats-named-after-a-synth-preset
//   https://www.syntorial.com/preset-recipe/darude-sandstorm-lead/




    
    """,
)
