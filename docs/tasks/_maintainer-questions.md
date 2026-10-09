# Open questions for the maintainer

A running log while the coordinator works in a loop (maintainer, 2026-10-07: "keep a log of the blockers, I will
answer them in one go"). Newest at the bottom of each part. When a question is answered, its answer moves into the
task file it belongs to, and the question leaves this list.

The questions are numbered, so an answer can be as short as "Q3: yes, Q7: keep".

Branch: `engine-pass-1` (from `main` at `7b04120c`, v0.5.5; merged as PR #85, v0.6.0). The open engine items that are not questions: [`engine-follow-ups.md`](engine-follow-ups.md).

---

# Part 1: Blocking (work waits on the answer)

---

# Part 2: Decided by default, please confirm

Work went ahead with the conservative choice. A "no" here means a small follow-up change.

---

# Part 3: For later (not blocking anything now)

## Q24. A third Kotlin spelling of the Ignitor doors: keep it as engine shorthand, or grow it?

Source: the step 13 table and its review (reviewer B corrected the first version of this question). Not blocking.

**What exists.** Two Kotlin surfaces build the same `IgnitorDsl` trees:
- the **Kotlin door** in `klangscript-libs` (`KlangScriptIgnitorExtensions`, the twin of the KlangScript door by
  `/dsl-design` §3): every value is `IgnitorDslLike`, so a number, a signal, or a mix of both fits anywhere;
- the **`audio_bridge` extensions** (`fun IgnitorDsl.vibrato(rate: Double, semitones: Double)` and friends), used by
  engine code, `classic()`, the defaults and specs. They have limits the doors do not:
  - eleven of them take only numbers (`detune`, `drive`, `distort`, `crush`, `coarse`, `phaser`, `tremolo`,
    `shimmer`, `vibrato`, `accelerate`, `fm`), so a modulated vibrato rate does not fit;
  - the arithmetic ones (step 13) and the filters take all numbers or all signals, not a mix:
    `lowpass(lfo, q = 4.0)` or `clamp(0.0, env)` need `IgnitorDsl.Constant(...)` spelled out;
  - `phaser` and `shimmer` have no `floor`, which the script builders have.

```kotlin
// klangscript-libs Kotlin door: works
KlangScriptIgnitor.vibrato(self = osc, rate = perlin, semitones = 0.3)
// audio_bridge extension: only numbers
osc.vibrato(rate = 5.0, semitones = 0.3)
```

**The question.** Growing the `audio_bridge` extensions to full parity would make them a third complete spelling of
every door (one word per concept says no). **Recommendation: keep them as engine-internal shorthand, record their
limits in their file header, and point authors (Kotlin included) at the `klangscript-libs` door.** The alternative is
to align them fully. No song is affected either way (songs are KlangScript).

## Q13. Pitch pipeline, the composition block (D8 to D11)

Source: §8. Step 2 of the pitch plan, recommendations as written there:
- **D8:** a name for the new semitone pitch primitive. `pitchMod` stays the linear one; the suggestion is
  `pitchSemitones`.
- **D9:** no vibrato `range` and no vibrato `phase` on sprudel for now.
- **D10 (pitch):** compose the pitch envelope through `adsr` only after a spike shows it matches.
- **D11:** accelerate and FM stay nodes.

## Q18. Named arguments in data tables

From the `/code-style` §24 pass. The vowel and body-material tables stay positional (`VowelBands.b`, 180 rows;
`BodyMaterials.m`, 120 rows), because the helper declared right above each table names the columns, and naming every
cell would triple each row.

**Example.**

```kotlin
private fun b(freq: Double, db: Double, q: Double) = ...   // the column order, declared once
b(1040.0, -7.0, 70.0)                                      // today
b(freq = 1040.0, db = -7.0, q = 70.0)                      // if named
```

Add a "data table" exception to §24, or name them too (one more script run)?

## Q19. Engine disposal order

Found in tidy-up step 9. When several engines go idle in the same block, the order they are disposed in decides which
units land on top of the warehouse's last-in-first-out shelves, and so which reverb or delay ring a later rent gets.

**Example.** Two songs stop in the same block. Today song A's reverb is shelved first, so the next song to start gets
song B's reverb unit. Disposing in render order could hand it A's instead. The units are equivalent, but a render is
then not bit-identical to before.

Step 9 keeps today's order, at the cost of one extra list in the dispatcher. Keep it, or simplify?

## Q20. Frozen songs

Source: [`song-orbit-ownership-review.md`](song-orbit-ownership-review.md).

**Example.** `strangerThings_2026_07_03` (`FrozenSongs.kt`) and `derSchmetterling_2026_09_16` (`FrozenPieces.kt`) have
lines that share an orbit and fight over its effects, the same bug fixed in the live songs.

Fix their shared-orbit conflicts, or keep them as snapshots of the old sound?
