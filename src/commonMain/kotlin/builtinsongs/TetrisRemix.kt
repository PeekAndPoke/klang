/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("unused")

package io.peekandpoke.klang.builtinsongs

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Song

/**
 * Dub-style remix of [tetrisSong].
 *
 * Reuses the iconic Korobeiniki melody and bass line by importing
 * `leadPattern` and `bassPattern` from `peekandpoke/tetris`, but applies a
 * very different sound design: deep echo, big reverb, low-cut, half-speed
 * lead, sparse one-drop drum kit and an off-beat skank stab.
 *
 * First Klangbuch entry to demonstrate cross-song imports — proves the
 * named-parts contract end-to-end.
 */
internal val tetrisRemixSong = Song(
    id = "${BuiltInSongs.PREFIX}-synthris-echo",
    title = "Remix: Echo um Echo",
    rpm = 30.0,
    icon = "headphones",
    code = """
import * from "stdlib"
import * from "sprudel"
// Here we import parts of the original Tetris, so we can remix them
import { leadPattern, bassPattern, sub as tetrisSub } from "peekandpoke/tetris"

// ── Lead: the Korobeiniki melody, drowned in echo ──────────────────────
// Half-speed, tri-y, big reverb.
export lead = note(leadPattern).slow(2)
    .sound("supersquare").adsr(0.025, 0.12, 0.5, 0.2).gain(0.5).unison(spread = 0.3)
    .hpf(500).lpf(freq = 1600, q = 2.5, env = 12)
    .room(0.8, 5, 0.1).pan(0.5).superimpose(x => x.sound("brown").gain(0.35))
    .vibrato(rate = "2.1".add(berlin2.mul(0.1)), depth = 0.03)
    .orbit(1).postgain(0.25)  // .solo()
    .filterWhen(x => x >= 16)

// ── Bass: the original bass line, slowed and sub-heavy ──────────────────
export bass = note(bassPattern).slow(2).struct("[x!16]")
    .sound("supersaw").unison(voices = 32, spread = 0.15).onepole(23846).clip(0.95)
    .gain(0.4).adsr(0.01, 0.2, 0.3, 0.1).pan(0.5).superimpose(pan(0.1).transpose(12), pan(0.9).transpose(12))
    .hpf(300).lpf(freq = 1150, q = 1.3, env = 19).distort(0.8, "soft", 2).postgain(0.115).pipeline("pedal")
    .orbit(2)  // .solo()

// ── Drum kit: dub one-drop ──────────────────────────────────────────────
// Kick on 1 only, snare on 3, hat on offbeats. Lots of space.
export kick  = sound("[bd ~ ~ ~]!4").orbit(3).gain(0.7).hpf(100).adsr(0.02, 0.18, 0.0, 0.05) // . solo()
export hat   = sound("[hh ~ hh ~]!4").orbit(4).gain(0.6).hpf(6000).adsr(0.01, 0.01, 0.5, 0.02) // .solo()
export snare = sound("[~ ~ sd sd ~ ~ sd ~]!2").orbit(5).gain(0.475).hpf(200).adsr(0.02, 0.12, 0.0, 0.05).room(wet = 0.4, size = 6)

// ── Skank: off-beat reggae chord stab ──────────────────────────────────
// The Bring-It-Together; off-beat = 16th-note 2 of every 4-step cycle.
export skank = chord("<Am Em F Am <Em Em Dm G#> Em Am <G E F G>>").voicing()
    .struct("[~ x]!4").legato(1.0)
    .sound("supertri").unison(voices = 6, spread = 0.05)
    .gain(0.90).adsr(0.02, 0.2, 0.0, 0.1).postgain(1.6)
    .hpf(1000).lpf(freq = 8000, env = 21.7, attack = 0.02, decay = 0.1, sustain = 0.0, release = 0.1).onepole(22309)
    .pan(0.2).superimpose(pan(0.8))
    .orbit(6).room(wet = 0.5, size = 5)  // . solo()
    .filterWhen(x => x >= 8)

export sub = tetrisSub.struct("x!1 [~!1 x!1?] x!5 ~!1").orbit(7).clip(0.8).distort(0.3)
  .lpf(attack = 0.005, decay = 3.0, sustain = 0.0, release = 0.2).hpf(60).lpf(freq = 300, env = 48, attack = 0.005, decay = 1.0, sustain = 0.0, release = 0.2).postgain(0.34) // .solo()

// ── Song: dub plate with broad reverb tail and gentle bus compression ──
export song = stack(kick, snare, hat, skank, lead, bass, sub)
  .compressor(-6, 2, 6, 0.02, 0.05).analog(3.5)

// Composed by: peekandpoke + Claude (echo of Korobeiniki, by way of King Tubby)








    """,
)
