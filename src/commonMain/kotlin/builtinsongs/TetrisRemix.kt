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
    .sound("tri").adsr("0.04:0.3:0.3:0.5").gain(0.3)
    .hpf(800).lpf(3500).warmth(0.6)
    .room(0.2).rsize(10).pan(0.35)
    .superimpose(pan(0.65).transpose(12).gain(0.3))
    .orbit(1).postgain(0.4) //  .solo()
    .filterWhen(x => x >= 16)

// ── Bass: the original bass line, slowed and sub-heavy ──────────────────
export bass = note(bassPattern).slow(2).struct("[x!8]")
    .sound("supersaw").unison(16).detune(0.06).warmth(0.5)
    .gain(0.85).adsr("0.01:0.1:0.7:0.1").pan(0.5)
    .hpf(300).lpf(1000).distort("0.4:soft:2").postgain(0.35)
    .orbit(2) // .solo()

// ── Drum kit: dub one-drop ──────────────────────────────────────────────
// Kick on 1 only, snare on 3, hat on offbeats. Lots of space.
export kick  = sound("[bd ~ ~ ~]!4").orbit(3).gain(1.0).hpf(40).adsr("0.02:0.18:0.0:0.05") // . solo()
export hat   = sound("[~ hh ~ hh]!4").orbit(4).gain(0.32).hpf(7000).adsr("0.001:0.02:0.0:0.02")
export snare = sound("[~ ~ sd sd ~ ~ sd ~]!2").orbit(5).gain(0.55).hpf(200).adsr("0.005:0.12:0.0:0.05").room(0.4).rsize(6)

// ── Skank: off-beat reggae chord stab ──────────────────────────────────
// The Bring-It-Together; off-beat = 16th-note 2 of every 4-step cycle.
export skank = chord("<Am Em F Dm Am Em F G7>").voicing()
    .struct("[~ x]!4").legato(1.0)
    .sound("supertri").unison(6).detune(0.10)
    .gain(0.5).adsr("0.02:0.2:0.0:0.05")
    .hpf(400).lpf(3200).pan(0.7)
    .orbit(6).room(0.3).rsize(5) // . solo()
    .filterWhen(x => x >= 8)

export sub = tetrisSub.struct("[x!4]").lpf(300).postgain(0.3)

// ── Song: dub plate with broad reverb tail and gentle bus compression ──
export song = stack(kick, snare, hat, skank, lead, bass, sub).compressor("-10:2:10:0.02:0.25").analog(1.5)

// Composed by: peekandpoke + Claude (echo of Korobeiniki, by way of King Tubby)
            
            """,
)
