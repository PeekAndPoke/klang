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

## Q28. Solo protects the SOLOED voice for its whole life (refining your Q14)

Source: the small-items review (`tmp/reviews/small-r1-B.md`). Taken literally ("any of its voices"), Q14 kept a source
protected for as long as it played, even after its solo was taken away: `solo(1)` edited to `solo(0)` left the pad at
full level for all 16 s. Decided by default: a voice is kept only if it was soloed itself.

```
note("c3").sound(myPad).release(5).solo()   // the soloed pad rings at full level to its end   (Q14)
// edit to .solo(0): the pad is ducked again 2 s after the last solo event, as before Q14
```

A note that started BEFORE its source was soloed is not kept either (it drops back 2 s after the solo's last event).
Say if you meant otherwise.

---

# Part 3: For later (not blocking anything now)

## Q29. `duty(amount)`: the word `amount` once more

From the small-items batch. `amount` is the distort drive only (Q21), but the pulse width door still says
`duty(amount)`. Decided 2026-10-09: rename it later, not in v0.6.1. Proposal for then: `duty(width)` (0 to 1, the
share of the cycle that is high); positional `duty(0.3)` unchanged.

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

## Q13. The `progress()` signal (pitch plan §7f; not blocking, the composition block comes after the pipeline)

Decided so far (2026-10-09): public, 0 at the onset, 1.0 at the gate close, growing on past 1 so release-tail effects
can be built; a foundation for tweens and general curves (one curve vocabulary with the `adsr` stages and the
signal-graph tweens). Accelerate clamps it at 1 inside. Open:

1. **After the gate: gate lengths, or seconds too?** In gate lengths, a 0.1 s note with a 2 s release reaches 21 at
   its end, a 2 s note reaches 2: a tail effect runs 20 times faster on staccato notes. A seconds-based `sinceGate()`
   (0 while the gate is open, then 0.5 after half a second ...) fits the units rule. Both?
2. **Live MIDI notes:** a held key has no known gate length, so progress stays about 0 (the far horizon). At
   note-off: (a) stay as decided in August (no tail effects on live notes), (b) jump to 1.0 and grow on (accelerate
   would jump at the release), or (c) rely on `sinceGate()`, which starts counting at the real note-off for every
   note.
3. **How a curve is written** on a signal: `Ign.progress().curve("scurve")`, `.ease("scurve")`, or a curve argument
   on `progress()` itself?

```
Ign.saw().lowpass(Ign.progress().sub(1).clamp(0, 1).curve("scurve").range(4000, 600))   // the tail darkens, eased
```

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
