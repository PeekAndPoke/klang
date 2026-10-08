# Open questions for the maintainer

A running log while the coordinator works in a loop (maintainer, 2026-10-07: "keep a log of the blockers, I will
answer them in one go"). Newest at the bottom of each part. When a question is answered, its answer moves into the
task file it belongs to, and the question leaves this list.

The questions are numbered, so an answer can be as short as "Q3: yes, Q7: keep".

Branch: `engine-pass-1` (from `main` at `7b04120c`, v0.5.5).

---

# Part 1: Blocking (work waits on the answer)

Nothing open (Q1 to Q5 and Q9a answered 2026-10-08).

---

# Part 2: Decided by default, please confirm

Work went ahead with the conservative choice. A "no" here means a small follow-up change.

## Q6. Unison voice cap: 64

Done in the empty-variants fix.

**Why.** `voices(1e9)` or an infinite signal allocated without bound on the audio thread at note-on.

**Example.**

```
Ign.supersaw(x => x.voices(1000))   // plays 64 voices
Ign.supersaw(x => x.voices(32))     // unchanged (the largest count in any song, TetrisRemix)
```

A non-finite count reads as 0, which is silence. `/code-style` §21 allows clamping a count. Every built-in sound is
byte-identical.

## Q10. Tidy step 11: the arithmetic types on the wire and `Param` / `Constant` stay separate

Source: [`engine-tidy-up.md`](engine-tidy-up.md) step 11.

**What was done.** Inside the engine, each of the 20 arithmetic laws (plus, times, div, abs, ...) is now written once,
and all nodes share one helper. The wire and the doors are untouched.

**Kept as won't-do:**
- **Collapsing the 20 wire types into `Unary(op)` / `Binary(op)`.** Example: the JSON `{"type":"plus","left":…}`
  would become `{"type":"binary","op":"plus","left":…}`. That changes every JSON tag and the schema hash, touches
  every walker and crosses the min/max guardrail, and a Zig port gains nothing from it.
- **Merging `Param` and `Constant`.** Example: `Ign.param("cutoff", 800)` can be overridden per event and is listed in
  the editor's parameter list; `Ign.constant(800)` is a plain number. The engine also treats them differently on
  purpose in one place (filter `passes`), so the fused and chained doors stay bit-identical.

Say if you want either one anyway.

## Q11. Tidy step 12: body and vowel become one effect

Source: [`engine-tidy-up.md`](engine-tidy-up.md), scope `tmp/reviews/tidy-step12-scope.md`. Not started yet; it starts
after step 11 is committed.

**What changes.** `KatalystBodyEffect` and `KatalystFormantEffect` are token-identical twins today. They become one
effect with a kind (body or vowel), reading the same catalogue tables. Two filter banks are reused instead of building
new ones on every change.

**Example.**

```
note("c3*8").body(material = "<wood glass>")   // every switch today builds 2 new filter banks (about 100 objects);
                                               // after: it reconfigures the bank nobody is hearing. The sound is identical.
```

**What this touches:**
- **Your review guardrail** "body / formant are intentional un-deduped twins: change one, mirror the other" is retired.
  Its reasons (the band kinds share no type) are what the change removes.
- **Your 2026-09-20 rule "a bank never retunes" stays.** A sounding bank never changes. Only a bank nobody hears is
  reset, from zero, which is bit-identical to building it new.
- **Memory:** the two banks are built at the stage's first use, so a `classic` chain that never uses body or vowel
  holds nothing extra.

Say if you want the twins kept.

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
