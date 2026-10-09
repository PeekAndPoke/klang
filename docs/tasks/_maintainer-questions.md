# Open questions for the maintainer

A running log while the coordinator works in a loop (maintainer, 2026-10-07: "keep a log of the blockers, I will
answer them in one go"). Newest at the bottom of each part. When a question is answered, its answer moves into the
task file it belongs to, and the question leaves this list.

The questions are numbered, so an answer can be as short as "Q3: yes, Q7: keep".

Branch: `engine-pass-1` (from `main` at `7b04120c`, v0.5.5; merged as PR #85, v0.6.0). The open engine items that are not questions: [`engine-follow-ups.md`](engine-follow-ups.md).

---

# Part 1: Blocking (work waits on the answer)

Nothing open (Q21 and Q22 answered 2026-10-08).

---

# Part 2: Decided by default, please confirm

Work went ahead with the conservative choice. A "no" here means a small follow-up change.

Nothing open (Q6 to Q11 answered 2026-10-08).

---

# Part 3: For later (not blocking anything now)

## Q25. Sprudel's `analog(amount)`: the word `amount` again

Source: the slot-rename map (`tmp/reviews/slot-rename-map.md`, Q-D). Not blocking.

Q21 decided that after the renames `amount` means only the distort drive (`distort(amount = ...)`). Sprudel's
`analog` door still names its one parameter `amount`:

```
note("c3").s("saw").analog(amount = 4)   // today
note("c3").s("saw").analog(4)            // positional, unchanged either way
```

`analog` is now a character scale (Q22). Options: keep `amount` (it is a one-knob door, where the name is rarely
written), or rename the parameter, for example `analog(character = 4)`. Recommendation: rename to `character`, with the
other renames, for one meaning per word. Say if you prefer to keep it. (`penv(amount)` is renamed by the pitch pipeline
to `penv(semitones)`, already decided.)


## Q23. `variants` with plain numbers?

Source: [`20261009-engine-tidy-up.md`](../tasks-archive/2026-10/20261009-engine-tidy-up.md) step 13, your D10 rule ("a plain number everywhere a constant value
is accepted"). Not blocking.

**Today.** `Ign.variants(...)` takes only Ignitor children. Your strict-argument decision of 2026-10-06 made
`Ignitor.variants(1, Ign.sine())` a type error on every platform (`StrictArgumentConversionSpec`), where before it
silently built a broken tree.

**What D10 would allow.** Numbers as children, converted to constants at the door, as every other door does:

```
Ign.saw().lowpass(Ign.variants(400, 1200, 3000))   // :n picks the cutoff per note
Ign.variants(1, Ign.sine())                        // a constant 1 or a sine, picked per note
```

The engine already plays such a tree. Recommendation: yes, numbers become `Constant` at the door (the same strict
conversion, now with one more accepted type); the spec row changes from "type error" to "a number child is a
constant". Say no if the 2026-10-06 rule was meant to keep `variants` Ignitor-only.

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

## Q12. Solo ramp times

**Today:** 1.5 s in and out (a cubic swell) with a 2 s hold.

**Example.** `solo("<1 0>")` is meant as a toggle every cycle. At cps 1 it never settles: the swell is still moving
when the next cycle flips it.

**Recommendation from the review:** one ramp of about 0.1 to 0.25 s both ways, and a hold of `max(ramp, 0.5 s)`. Kept
at 1.5 s until you decide; the KDoc now says that it fades.

## Q13. Pitch pipeline, the composition block (D8 to D11)

Source: §8. Step 2 of the pitch plan, recommendations as written there:
- **D8:** a name for the new semitone pitch primitive. `pitchMod` stays the linear one; the suggestion is
  `pitchSemitones`.
- **D9:** no vibrato `range` and no vibrato `phase` on sprudel for now.
- **D10 (pitch):** compose the pitch envelope through `adsr` only after a spike shows it matches.
- **D11:** accelerate and FM stay nodes.

## Q14. A soloed release tail beside another solo

**What happens.** Protection now ends 2 s after the source's last solo event. A long release is therefore ducked
mid-tail if another solo is live.

**Example.**

```
note("c3").sound(myPad).release(5).solo()   // myPad: any instrument with a long tail
s("bd*4").solo()
```

The pad's tail is ducked from about 2 s after its last event, measured -23.6 to -40.8 dB at 4 s. The old code let it
ring at full level. The click is fixed (a 128-frame ramp).

Should a tail stay protected until its voice ends?

## Q15. Helper merges that change behaviour

Source: [`utils-home-pass.md`](utils-home-pass.md), "Left for a decision". Four small steps:
- **`Environment.loadLibrary`'s own Levenshtein folded into `suggestNames`.** Example: the "did you mean ...?" text for
  a misspelt library name changes wording to match the other suggestions.
- **`MnRenderer.renderNumber` swapped for `formatAsIntOrDouble`.** Example: a number above `Int.MAX_VALUE` (2147483647)
  is clamped when rendered today; after the swap it renders as written.
- **One home in `common` for the four text-position helpers.**
- **A small class for the tracked-timeouts code** shared by `MnEditorBase` and `NoteStaffEditor`.

The coordinator would do all four as small reviewed steps unless you say otherwise.

## Q16. The oscillators do not use PolyBLEP

Found in tidy-up step 1. `/code-style` §9, `CREDITS.MD` and `klang-music-writing/ref/ignitor-reference.md` say the
oscillators use PolyBLEP. They use finite-slope flanks instead, and the unused `polyBlep` helper was deleted.

**Example.** `CREDITS.MD` lists "PolyBLEP (Välimäki et al.): band-limited oscillator anti-aliasing", but the saw
actually ramps its reset over a few samples (a finite-slope flank), with no PolyBLEP correction.

The coordinator would correct the texts to say what the code does: those three, the in-app Credits page, the Zawtooth
KDoc, and three `IgnitorsTest` row names. §9 is a rule text, so it waits for your word.

## Q17. `WarehouseStats.reverbFailures` / `reverbDropped`

Found in tidy-up step 1. These are wire fields the UI never shows; the warehouse panel shows their delay-ring twins.

**Example.** When the reverb shelf runs dry, the panel shows nothing; when the ring shelf runs dry, it shows a count.

Show them in the warehouse panel, or drop them?

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
