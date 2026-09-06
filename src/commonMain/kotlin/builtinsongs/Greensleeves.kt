/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("unused")

package io.peekandpoke.klang.builtinsongs

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Song

/**
 * Greensleeves, the traditional English tune (sixteenth century, public domain), whistled by
 * the wind: pink noise through a bandpass that follows the note (`bpf(freq)`), the first song
 * written for the sprudel field accessors (`docs/tasks/sprudel-field-accessors.md`).
 */
internal val greensleevesSong = Song(
    id = "${BuiltInSongs.PREFIX}-greensleeves",
    title = "Greensleeves",
    rpm = 27.0,
    icon = "leaf",
    code = """
import * from "stdlib"
import * from "sprudel"

// One cycle is one bar of 6/8, counted in twelve sixteenths. The tune is sixteen bars:
// eight of verse, eight of chorus, with the pickup note at the end of the last bar.
let melody = note(`<
  [c5@4 d5@2 e5@3 f5 e5@2] [d5@4 b4@2 g4@3 a4 b4@2] [c5@4 a4@2 a4@3 gs4 a4@2] [b4@4 gs4@2 e4@4 a4@2]
  [c5@4 d5@2 e5@3 f5 e5@2] [d5@4 b4@2 g4@3 a4 b4@2] [c5@3 b4 a4@2 gs4@3 fs4 gs4@2]  [a4@6 ~@6]
  [g5@6 g5@3 fs5 e5@2]     [d5@4 b4@2 g4@3 a4 b4@2] [c5@4 a4@2 a4@3 gs4 a4@2] [b4@4 gs4@2 e4@6]
  [g5@6 g5@3 fs5 e5@2]     [d5@4 b4@2 g4@3 a4 b4@2] [c5@3 b4 a4@2 gs4@3 fs4 gs4@2]  [a4@6 ~@4 a4@2]
>`).late(4)

let harmony = chord(`<Am G Am E  Am G [Am E] Am  C G Am E  C G [Am E] Am>`).late(4)

// The wind whistles: pink noise, a narrow bandpass sitting on the note's own frequency.
// `freq` is read from the event, so the whistle follows whatever pitch the chain has set,
// and the wind is never quite in tune.
let whistleOf = (tune) => tune.s("pink")
  .bpf(freq.mul(perlin.seg(6).range(0.985, 1.015))).bpq(16)
  .adsr(0.08, 0.1, 0.9, 0.3).legato(0.95)

let breathOf = (tune) => tune.s("pink")
  .bpf(freq).bpq(1.5)
  .adsr(0.12, 0.1, 0.8, 0.3).legato(0.95)

// The wind itself: brown noise wandering through a wide band.
let wind = s("brown!2")
  .bpf(perlin.seg(4).range(180, 1100)).bpq(1.5)
  .clip(1.2).adsr("0.4:1:1:0.4")
  .pan(perlin.range(0.3, 0.7).slow(5))

let lute = Osc.pluck().highpass(90).lowpass(3200)
let bass = Osc.triangle().plus(Osc.sine().mul(0.5)).lowpass(400).adsr(0.01, 0.4, 0.5, 0.3)

let verse = (x) => x >= 4 && x < 36
let secondTime = (x) => x >= 20 && x < 36

stack(
  wind.orbit(2).gain(1.5)

  , harmony.voicing().s(lute).struct("x ~ ~ x ~ ~").orbit(0).gain(0.38).pan(0.62)
      .filterWhen(verse)
  , harmony.rootNotes(2).s(bass).struct("x ~ ~ ~ ~ ~").orbit(0).gain(0.32).legato(2).pan(0.5)
      .filterWhen(verse)

  , whistleOf(melody).orbit(1).gain(4.5).pan(0.42).filterWhen(verse)
  , breathOf(melody).orbit(1).gain(1.0).pan(0.58).filterWhen(verse)
  , whistleOf(melody.transpose(-12)).orbit(1).gain(3.0).pan(0.36).filterWhen(secondTime)

  , master(Master(m => m.reverb(r => r.wet(0.14).roomSize(6)).gain(1.6).limiter()))
)
    """,
)
