# KLANG-00281: Song review: patterns with different bus settings share one orbit

> **Status (2026-10-20): PAUSED.**

Status: **the frozen songs decided (maintainer, 2026-10-13, Q20): fix their shared-orbit conflicts, so they are
reasonable in what they do** (each conflicting pattern its own orbit, or the settings made to agree; the corpus
reference of the two frozen songs is re-accepted after). **The frozen texts: done 2026-10-16** (each section below
says what changed; the 09-25 Der Schmetterling's count-in too). The three frozen corpus rows moved because their
frozen TEXT was edited (Q20), not because the engine changed. The live songs below stay with the maintainer.
The frozen-orbits pairs (`tmp/listening/frozen-orbits/`) heard 2026-10-18: "all well".
First noted 2026-10-07 for the maintainer to review in the song code. Found by voice lifecycle step 5
(KLANG-00279 (voice lifecycle state machine)), measured in review (`tmp/reviews/vl5-r1-B.md`, reviewer B, round 1).

## What this is

An orbit's bus settings (its Katalyst chain: reverb, delay, phaser, body, vowel, compressor) come from ONE voice at
a time, the owner. When two patterns with different bus settings sit on the same orbit, the owner decides which
settings you hear, and the setting flips at note rate between them. The old rule (the first claimer kept the orbit
through its release tail and its silent zombie time) hid much of this behind whichever note claimed first. Step 5
makes only a `Sounding` voice own the orbit, and among those the newest onset wins (maintainer, 2026-10-07), so
these conflicts now show. They are song decisions: give each pattern its own orbit, or make the settings agree.

The lines below are the Kotlin file's lines. Times are from the reviewer's render under the step-5 rule as first
built (first claimer among sounding voices); the newest-onset rule changes which voice wins at each moment, not
which patterns conflict.

## Die Kirschblüte (`src/commonMain/kotlin/builtinsongs/Sakura.kt`)

- **Orbit 0:** the koto melody `:70-76` (`.orbit(0)`, `body(material = "mahogany")` on `:75`: material 4, wet 0.5,
  floor 0.4) against the three noise layers `:117-119` (`dust`, `pink`, `brown`), which name no orbit and so land
  on orbit 0, with no body.
- **What you hear:** the noise layers own orbit 0 about 90 % of the time, so the koto's mahogany body is mostly
  off. First conflict at 0.833 s (cycle 0.375).
- **Likely fix:** put the noise layers on their own orbit.

## Seltsamere Dinge (`src/commonMain/kotlin/builtinsongs/StrangerThings.kt`)

- **Orbits 3 and 4:** the bass `:48-51` (orbit 3) and its octave copy `:52-54` (orbit 4), with
  `.vowel(vowel = "e o e i a u".slow(24), wet = 0.40)` on `:55`. Each orbit on its own, no conflict between
  patterns: the vowel changes per note, and each note's vowel now applies at its own onset instead of at the end of
  the previous note's 2.75 s release (0.57 s earlier on orbit 3, 1.48 s on orbit 4).
- **Nothing to fix:** the rule doing what it should. Listen once to confirm the earlier vowel changes sound right.

## The Synthsale Piper's Last Rave (`src/commonMain/kotlin/builtinsongs/IrishLamentTechno.kt`)

- **Orbit 5:** the pad `:73-77` (`phaser(wet = saw.range(0.3, 0.6).slow(16))` per chord, `reverb(0.4, 6)`), and
  later the stabs `:150-153` (reverb 0.4/6) against the Tetris bassline `:197-205` (phaser 0.25, sweep 500,
  center 3500, no reverb) at 233.7 s (cycle 148).
- **Orbit 1:** the hat `:47` (no reverb) against the clap `:56` (`reverb(wet = 0.2, size = 3)`), `oh` `:57`, `rim`
  `:58`. The hat's 8ths take the orbit at each note, so the clap's reverb is on only 2.6 % of the time (7.7 % under
  the old rule).
- **Orbit 3:** the hit stab `:99-106` (release 10 s, `reverb(wet = 0.4, size = 5)`) against the sub `:138-139`
  (no reverb), from 132.6 s (cycle 84).
- **Orbit 2:** the crash `:98` (reverb 0.25/4) against the hit hat `:109`, from 127.9 s (cycle 81).
- **Orbit 6:** the Tetris bassline `:157-166` (phaser) against the stabs `:207-212` (reverb 0.4/6), from 233.7 s.
- **Likely fix:** separate orbits for the hat and the clap, the stab and the sub, the bassline and the stabs.

## frozen strangerThings_2026_07_03 (`src/jvmMain/kotlin/FrozenSongs.kt`, string from `:536`)

- **Orbit 1:** the melody `:556-561` (`body(material = "wood")`) and its superimposed octave copy `:562-564`
  (`body(material = "glass")`): two different bodies on one orbit, starting on the same frame. Only one body can
  sound; which one is a tie (the old render gave glass 82 % of the time by list-order accident, the first new
  render wood 72 %).
- **Orbit 0:** the shore noise `:590-592` (no body) against the lyrics `:551-554` (`body(material = "membrane")`)
  and the claps `:547-549`.
- **Orbits 2 and 3:** the bass and its octave copy `:566-572` with `vowel(vowel = "i a e".slow(12))` on `:573`: the
  vowel now changes at each note's onset (no conflict, the rule working).
- **Note:** a frozen song is a snapshot; fixing it means deciding whether frozen songs keep their old sound.
- **Done 2026-10-16 (Q20), a free orbit per conflict, each pattern keeps its own sound:**
  - the melody's glass octave (the `superimpose` lambda) now starts with `x.orbit(6)`: wood keeps orbit 1, glass has
    orbit 6;
  - the lyrics moved from `.orbit(0)` to `.orbit(7)`: the claps and the shore noise (both without a body) keep orbit 0
    and agree;
  - a dated comment heads the text. Corpus row `bdc0904443f2e42a` (peak 0.6663) to `f8774ba9e1f50b48` (peak 0.6647),
    re-accepted with `corpus-accept-engine` (the text moved it, not the engine). Listening pair:
    `tmp/listening/frozen-orbits/stranger-things.md`.

## frozen piece derSchmetterling_2026_09_16 (`src/jvmMain/kotlin/FrozenPieces.kt`, string from `:50`)

- **Orbit 3:** guitar 3 `:333-345` (`body(material = "rosewood", wet = 0.3)` on `:339`, the group's
  `reverb(wet = 0.15, size = 3.0)` on `:450`) against the bass `:348-361` (the orbit on `:356`, no body, no
  reverb). 16th-note guitar against bass notes: about 2100 flips of body and reverb.
- **Orbit 7:** the count-in `:432-433` (`reverb(wet = "0.1", size = 0)`) against the hats `:412-416` (group reverb
  0.25/4 and a compressor on `:463`).
- **Note:** check whether the current Der Schmetterling (`DerSchmetterling.kt`) still shares orbit 3 between guitar
  3 and the bass; the current song did not change in the corpus, so it probably does not.
- **Done 2026-10-16 (Q20), a free orbit per conflict:**
  - `bass_arrange` moved from `x.orbit(3)` to `x.orbit(6)`: guitar 3 keeps orbit 3 with its rosewood body and the
    guitar group's reverb;
  - `countin_arrange` moved from `x.orbit(7)` to `x.orbit(10)`: the hats keep orbit 7 with the drum group's reverb and
    compressor;
  - a dated comment under the text's first line. Corpus row `0fc662a01aaecd03` to `e37e6174f55c1ab3` (peak 0.9144
    both), re-accepted with `corpus-accept-engine` (the text moved it, not the engine). The ledger's frozen rows read this text (`SongBenchmarkCases`
    `ledgerFrozen`); they render each piece solo, so the orbit numbers do not move them, and its compile spec stays
    green. Listening pair: `tmp/listening/frozen-orbits/schmetterling.md`.
- **Seen on the way, not changed:** the current Der Schmetterling puts guitar 3 on `x.orbit(2)`, beside guitar 2
  (guitar 2's mahogany body against none): the same kind of conflict, in a live song, so the maintainer's.

## frozen derSchmetterling_2026_09_25 (`src/jvmMain/kotlin/FrozenSongs.kt`, string from `:89`)

- **Orbit 0:** the count-in `:478-479` (`reverb(wet = "0.1", size = 0)`) against the lead `:332`
  (`body(material = "wood", wet = 0.4)`, `reverb(wet = 0.10, size = 1.0)` on `:487`). Found in Q20's review round 1.
  The lead is muted for its first 64 cycles, so in practice the two never sound together.
- **Done 2026-10-16 (Q20, coordinator):** `countin_arrange` moved from `x.orbit(0)` to `x.orbit(11)` (free: the text
  uses 0 to 10), with a dated comment under the text's first line. The rig suite's anchors still match (its compile
  spec green). Corpus row `31108000e4bbdb24` to `b0151ba274832b17` (peak 0.9249 both), re-accepted with
  `corpus-accept-engine`; the text moved it, not the engine. Rendered at 16 bit over all 256 cycles, the two
  versions differ in 327 samples by one LSB, from 138 s on: inaudible. Listening pair:
  `tmp/listening/frozen-orbits/schmetterling-0925-countin.md`.

## The handover itself

Measured clean: bus settings glide (`KNOB_GLIDE_SECONDS`, 50 ms), a reverb switched off drains its room, and body
or vowel changes go through the 20 ms bank crossfade (20 ms then; 50 ms since 2026-10-17). No clicks at a change of owner.

## History

- 2026-10-20: numbered KLANG-00281 (was `docs/tasks/song-orbit-ownership-review.md`).
