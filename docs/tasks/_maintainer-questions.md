# Open questions for the maintainer

A running log while the coordinator works in a loop (maintainer, 2026-10-07: "keep a log of the blockers, I will
answer them in one go"). Newest at the bottom of each part. When a question is answered, its answer moves into the
task file it belongs to, and the question leaves this list.

The questions are numbered, so an answer can be as short as "Q3: yes, Q7: keep".

Branch: `engine-pass-1` (from `main` at `7b04120c`, v0.5.5).

---

# Part 1: Blocking (work waits on the answer)

## Q1. Pitch pipeline, D6: do bare instruments lose the pitch doors?

Source: [`pitch-pipeline-into-the-tree.md`](pitch-pipeline-into-the-tree.md) §8. Blocks the pitch pipeline from step 1
(penv) onward.

**What changes.** Today sprudel's `vib`, `penv`, `accelerate` and `fm` are applied by the voice strip to EVERY voice.
The pipeline moves them into `classic()` as stages. From then on, they reach only an instrument that ends in
`classic()`, just as every other sprudel door already does.

**Example.**

```
let lead = Ign.saw().lowpass(1200).adsr(0.01, 0.2, 0.6, 0.3)             // no .classic()
let pad  = Ign.saw().lowpass(1200).adsr(0.01, 0.2, 0.6, 0.3).classic()

note("c3 e3 g3").sound(lead).vib(5, 0.3)   // today: vibrato. After: no vibrato.
note("c3 e3 g3").sound(pad).vib(5, 0.3)    // today and after: vibrato.
```

The same holds for `.penv(...)`, `.accelerate(...)` and `.fm(...)` on `lead`. Built-in sounds (`s("sawtooth")` and
friends) end in `classic()`, so they keep every door. No song in the corpus is affected.

**Options.**
- (a) **Accept** (recommended). One rule for every door. To keep the vibrato, add `.classic()` to `lead`.
- (b) A helper that places only the four pitch stages, such as `pitchDoors()`. This is a new surface on two doors
  that no current song needs.

## Q2. Pitch pipeline, D2: one accelerate base for both doors

Source: §8. Blocks step 3.

**What differs today.** The sprudel `accelerate` door glides from the onset to the end of the release tail. The
Ignitor node `accelerate` glides from the onset to the gate close and then holds.

**Example.** A 1 s note with a 2 s release, gliding up an octave:

```
note("c4").accelerate(12).adsr(0.01, 0.1, 0.8, 2.0)                       // sprudel door
let riser = Ign.saw().accelerate(12).adsr(0.01, 0.1, 0.8, 2.0).classic()  // Ignitor node
```

- **sprudel door:** at 1 s (gate close) only part of the way up, and +12 exactly when the tail ends at 3 s.
- **Ignitor node:** +12 at 1 s, then holds +12 through the release.

**Options.**
- (a) **The sprudel door's base for both** (recommended). Every song hears it today, and the glide lands exactly where
  the voice ends. `riser` changes sound: it reaches +12 at the end of the tail. No song uses the Ignitor `accelerate`.
- (b) The gate for both. Every song's `accelerate` changes; Kokon's `strike` (a long release) changes the most.

## Q3. Pitch pipeline, D1: does a pitch door bend an FM modulator?

Source: §8. Blocks step 4 (FM), together with Q4.

**What changes.** Today the strip's pitch modulation also bends an `fm` node's modulator, so carrier and modulator
move together and the ratio stays harmonic. A pitch node in the tree only bends the carrier, and the new `classic()`
stages will do the same.

**Example.**

```
let bell = Ign.sine().fm(Ign.sine(), 3.5, 400).adsr(0.001, 1.0, 0.0, 1.0).classic()
note("c5").sound(bell).vib(6, 0.5)
```

- **Today:** the whole bell wobbles in pitch, and its timbre stays the same.
- **After (a):** the carrier wobbles and the modulator holds still. The ratio drifts around 3.5 with the vibrato, so
  the timbre shimmers slightly while it wobbles.

**Options.**
- (a) **Accept the tree's rule now** (recommended). No song in the corpus has this shape. Later, a small
  "bend the whole operator" item can be tried by ear.
- (b) The FM node builds its modulator under the outer pitch modulation (one line). This also changes authored trees
  that put their own pitch node above an `fm`.
- (c) A root scope node that sets pitch modulation for the whole subtree. It would be bit-identical, but it is a second
  mechanism.

## Q4. Pitch pipeline, D3: FM moves onto the node's law, and sprudel's `fm` gets a `release`

Source: §8. Blocks step 4, together with Q3.

**What changes.**
1. **Per-sample envelope.** Today sprudel's FM index envelope is held per 128-frame block (about 2.9 ms steps). The
   Ignitor FM node runs it per sample. Moving the door onto the node makes the envelope smooth (ledger E11). This is a
   sound change, proven by listening pairs rather than bit-identity.
2. **A `release` on the sprudel door.** Sprudel's `fm` has attack, decay and sustain, but no release; the Ignitor door
   has one.

**Example.**

```
note("c4").fm(env = 4, h = 2, attack = 0.002, decay = 0.1)   // the fast attack becomes a smooth ramp instead of a short staircase
note("c4").fm(env = 3, h = 2, release = 0.3)                 // new: the brightness fades over 0.3 s after the note ends
```

The default `release = 0.0` keeps today's sound.

**Recommendation:** yes to both.

## Q5. Engine tidy-up, D10: move the test-only overloads out of main code?

Source: [`engine-tidy-up.md`](engine-tidy-up.md), audit E. Blocks only the overloads half of step 13. The other half
(the chain's stage accessors moved to a test helper) goes ahead.

**What it is.** About 84 Kotlin overloads of the Ignitor extensions exist only so that specs can write plain numbers.
There are also a few test seams (`*ForTest`, `currentState`, `installed*`, ...).

**Example.**

```kotlin
// main code today has both:
fun Ignitor.lowpass(cutoffHz: Ignitor, q: Ignitor, ...): Ignitor   // what the engine builds
fun Ignitor.lowpass(cutoffHz: Double, q: Double = 0.707, ...)      // only specs call this one
```

**Options.**
- (a) **Move the `Double` overloads to the test sources** (recommended). Main code then carries one spelling per node,
  which is also what a Zig port has to mirror. Each overload is checked first: one of them, `Ignitor.mul(Double)`, is a
  real production door and stays.
- (b) Keep them as a test convenience.

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

## Q7. An empty `variants()` on a Katalyst bus knob reads 0.0

Done in the same fix.

**What changed.** Before, a bus knob (for example a reverb's `wet`) fed with an `Ign.variants()` that has
no children threw an error. The error was caught, and the knob fell back to its default. Now it reads 0.0, so "an
empty variants is silence" holds everywhere.

**Example.** `reverb(wet = Ign.variants())`: before, the default wet; now, no reverb.

Say if a bus knob should keep its default instead.

## Q8. Solo defaults

Source: [`bugfix-solo-rests-and-amount.md`](bugfix-solo-rests-and-amount.md).

**What was kept as it was:**
- the ramps: 1.5 s in and out, 2 s hold;
- the tails: under `solo(1.0)` the cylinder tails decay naturally;
- several solos: the strongest wins;
- the solo id: one per call site.

**Examples.**

```
s("bd*4").solo()                          // others drop to 5 % (the default amount 0.95)
s("bd*4").solo(1.0)                       // others are silent; a reverb tail already ringing decays naturally
s("bd*4").solo(0.5), s("hh*8").solo(0.9)  // the 0.9 wins: everything else plays at 10 %
```

**Two small differences from before:**
- a source that solos again keeps the latest amount and the later end;
- protection ends 2 s after the source's last solo EVENT, not its last voice. A soloed release tail longer than 2 s is
  therefore ducked, but only if another solo is live. See also Q14.

## Q9. Pitch pipeline, D4, D5, D7

Source: §8.

- **D4, slot names follow sprudel's readers.** The new `classic()` slots are called `fm.h`, `fm.env` and
  `vibrato.depth`, as the sprudel doors spell them, while the Ignitor nodes call the same knobs `ratio`, `depth` and
  `semitones`. Example: `note("c4").fm(h = 2)` writes the slot `fm.h`, which feeds the node's `ratio`. The asymmetry
  is recorded; a rename would be its own task.
- **D5, each door's wire fields go in its own step.** Example: when vibrato moves into `classic()`, the fields
  `vibrato` and `vibratoMod` leave the wire in that same step, not at the very end. A field with no reader invites a
  second producer. This is the one deviation from the task text.
- **D7, `voices/strip/` dissolves into `voices/`** at the end (flat directories), rather than being renamed.

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
