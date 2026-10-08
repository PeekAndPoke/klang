# Open questions for the maintainer

A running log while the coordinator works in a loop (maintainer, 2026-10-07: "keep a log of the blockers, I will
answer them in one go"). Newest at the bottom of each part. When a question is answered, its answer moves into the
task file it belongs to, and the question leaves this list.

The questions are numbered, so an answer can be as short as "Q3: yes, Q7: keep".

Branch: `engine-pass-1` (from `main` at `7b04120c`, v0.5.5).

---

# Part 1: Blocking (work waits on the answer)

## Q21. The `classic()` slot names, pulled ahead (your rule from Q9a, applied to every slot)

Source: [`classic-slot-names-check.md`](classic-slot-names-check.md); the full table is `tmp/reviews/classic-slot-naming-table.md`.
Blocks only the renames themselves; the pitch pipeline goes ahead with its decided names.

**The short version.** Of the 60 slots `classic()` places, 40 already carry one word on the slot, the Ignitor door and
the sprudel door. The 27 Katalyst bus slots are all aligned. No song, tutorial or Lexikon page writes a `classic()`
slot as a string, so most of this costs code, not music. Ten small decisions, each with a recommendation:

**1. The envelope words on the Ignitor side.** The slots and sprudel say `attack, decay, sustain, release`; the
Ignitor doors and nodes say `attackSec, decaySec, sustainLevel, releaseSec`. This is the one real split (20 classic
slots, and 8 of the new pitch slots).

```
Ign.saw().adsr(attackSec = 0.01, decaySec = 0.3, sustainLevel = 0.5, releaseSec = 0.2)   // today
Ign.saw().adsr(attack = 0.01, decay = 0.3, sustain = 0.5, release = 0.2)                 // proposed
Ign.saw().adsr(0.01, 0.3, 0.5, 0.2)                                                      // unchanged
```

Recommendation: rename the Ignitor side (also inside the filter, pitch and FM builders, the node fields and the wire).
The Katalyst compressor already says `attack`/`release` in seconds, and the limiter's `thresholdDb` became
`threshold` the same way ("the unit lives in the KDoc"). No song changes; about 300 lines of main code and 600 of tests.

**2. `amount` on crush and coarse.** The value of `crush` IS the bit depth (fewer bits, harsher), and `coarse`'s is a
sample-hold factor. `amount` hides both.

```
.crush(amount = 6)    becomes   .crush(bits = 6)
.coarse(amount = 2)   becomes   .coarse(factor = 2)
.crush(6)             unchanged
```

Recommendation: yes (unit-honest, like `penv.semitones`). Afterwards `amount` means only the distort drive. Cost: 6
calls in 4 songs, 2 in `FrozenSongs.kt`, 9 places in the whitepaper, 5 lines in the skill refs. For coarse, the
alternatives are `divisor` or `hold`.

**3. `distort.amount`: keep.** `drive` would collide with the Ignitor's own `drive` stage and the songs' own
`Ign.param("drive")`. Recommendation: keep.

**4. The filter `env`: keep.** Example: `lpf(freq = 300, env = 24)`. `env` is the Ignitor knob's name, it has one
meaning once `fm.env` is gone, and `lpf.semitones` would read like a filter tuning. A rename would touch 23 song, 9
tutorial and 10 doc call sites. Recommendation: keep.

**5. `tremolo.depth`: keep, and write down what "depth" means:** how far a modulation swings, in the stage's unit
(tremolo and duck 0 to 1, FM in Hz). Recommendation: keep.

**6. `declickSeconds` becomes `declick`, flat.** Example: `ignp("declickSeconds", 0.002)` becomes
`ignp("declick", 0.002)`. No user call sites. Recommendation: yes, together with 1.

**7. Two source slots that mean something else, as a later follow-up.** The pluck's `decay` is a loop feedback
coefficient (0.9 to 0.999), not seconds, and brown noise's `depth` is a per-sample leak. Proposed later: `feedback`
and `leak`. Example of the hazard: a song that declares its own `Ign.param("decay")` for an envelope and also uses a
default `Ign.pluck()` would feed its envelope value into the string's feedback. No song does this today.
Recommendation: a filed follow-up, not this pass.

**8. The Katalyst under the same rule.** One sentence for both: `<sprudel door>.<engine door knob>`. All 27 already
fit, so no renames. `vowel.vowel` and `gain.gain` stay (a name knob is named after its stage).
Recommendation: yes, record it.

**9. `analog`: one name, two scales.** On an oscillator it is drift in cents; on `classic()`'s filters it is a 0 to 10
character. Example: `analog(4)` is mild drift but heavy filter character. Recommendation: keep one name and record the
asymmetry with its reason (the old strip did the same). The alternative is two names.

**10. The one-knob slots stay flat:** `onepole`, `pregain`, and the sample slots `begin`, `end`, `speed`, `loop`.
Recommendation: yes.

**Found while checking (the coordinator fixes these without waiting):** the Lexikon's Crush entry is backwards ("higher
= fewer bits"; the value is the bit depth); the Lexikon's Phaser entry names a `depth` knob that is `wet`; the
KlangScript `Ignitor.slot` lacks 8 slots the Kotlin side has (a two-door gap); a stale KDoc in
`KlangScriptIgnitorSlots`; three comments still say "named after sprudel's readers".

---

# Part 2: Decided by default, please confirm

Work went ahead with the conservative choice. A "no" here means a small follow-up change.

Nothing open (Q6 to Q11 answered 2026-10-08).

---

# Part 3: For later (not blocking anything now)

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
