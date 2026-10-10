# Open questions for the maintainer

A running log while the coordinator works in a loop (maintainer, 2026-10-07: "keep a log of the blockers, I will
answer them in one go"). Newest at the bottom of each part. When a question is answered, its answer moves into the
task file it belongs to, and the question leaves this list.

The questions are numbered, so an answer can be as short as "Q3: yes, Q7: keep".

Branch: `pitch-pipeline` (from `main` at `662aa8db`, v0.6.0; `katalyst-distort` merged in as `aab25677`; v0.6.1). The open engine items that are not questions: [`engine-follow-ups.md`](engine-follow-ups.md).

---

# Part 1: Blocking (work waits on the answer)

---

## Q33. Accelerate: compose it as D11 says, or keep the node (composition 7e)

Your D11 (2026-10-09): "if we can represent them through other primitives, they should leave, same as the tremolo
node did", so accelerate was to become `pitchModSemitones(semitones * progress)`. The spike
(`tmp/reviews/pp-7e-report.md`) built it and measured it against today's node:

```
note("c3").s("saw").accelerate(12).release(1)
// sound: the same apart from a rounding of about 8e-8 cents (Kokon's strike moves at -148 dB)
// cost:  about +0.7 us per accelerate voice per block, while gliding AND while holding the target
//        (1.2 to 1.5x on V8, 1.4 to 2x on the JVM); today one multiply per frame, composed three passes per frame
```

Two smaller differences, only for a script-door accelerate with a SIGNAL amount: the amount is read per sample (like
the vibrato after 7b), and an infinite sample pushes the pitch to the ceiling or freezes the source where today it
plays no glide. Options: (a) keep the node for now, as the pitch envelope did (Q32; the work stops here until you
say); (b) compose it and accept the cost (about 0.03 % of a block per voice) and the infinite-sample clause. The
pitch envelope and accelerate are the same question, so one answer can cover both. Recommendation: (a), revisit if a
cheaper "settled block" path ever exists for other reasons.

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

Three more behaviours neither Q14 nor Q28 named, measured in the third review (`tmp/reviews/small-r3.md`), as the
code now does them:
1. A soloed tail past its window does not hold the background down: the rest comes back after the window, while the
   soloed pad rings on at full level (as before Q14).
2. A soloed voice evicted from the solo tracker's 32 entries stays protected (it is the voice's own flag now).
3. A ringing soloed voice of the WEAKER of two solos plays at full level, like every protected voice.

Say if any of these should be different.

## Q30. Sprudel `fm` without an envelope keeps its depth through the release tail (pitch step 4)

Source: the step 4 worker, before any code changed (`tmp/reviews/pp-step4-report.md`). The plan said the new
`fm.release` slot at 0.0 is "today's sound". That holds for an enveloped `fm`, not for one without an envelope.
Decided by default under D3 ("FM moves onto the node's law, proven by listening pairs"):

```
note("c3").s("sine").fm(300, 1.4).release(0.5)
// before: the FM depth drops to 0 at the first block after the gate; the 0.5 s tail is the plain sine (-19.2 dB diff)
// after:  the FM depth stays full through the tail, as the Ignitor door's fm always did
```

The strip ran its FM envelope on every voice with the release fixed at 0, so the modulation stopped at the gate (the
block-framing ledger's E10/E11 defect). The node runs an envelope only when one is written. An enveloped `fm` with
release 0 still stops at the gate, now exactly on the gate frame, and the new `fm(release = ...)` can ramp it. No
song uses sprudel's `fm`. A listening pair joins `tmp/listening/pp-step4/`. A "no" means a law change on the `Fm` node
for every envelope-free fm, or a switch only `classic()` sets.

## Q31. `s("sgpad").fm(...)` loses its pitch (pitch step 4; your 3b call, now with numbers)

Your 3b decision: the one shape the engine cannot process (an fm above a forking `detune`) "stays quiet and recorded"
for `s("sgpad").fm(...)`, decided when it happens on a real song. Step 4 kept that. Review round 1 measured how it
sounds, and it is worse than the earlier +0.6 dB suggested:

```
note("c3").s("sgpad").fm(150, 1.5)
// before (v0.6.1): one FM for both saw layers, the pad's pitch intact (2.6 % of the energy off the harmonic grid)
// after:  one modulator serves both layers and jumps a block of phase at every block boundary:
//         95 % of the energy off the grid, an inharmonic comb about 18.75 Hz apart, the pitch is gone,
//         and the output depends on the block size (+2.8 dB between blocks of 128 and 64)
```

`sgpad` is the only built-in with this shape (`(Saw() + Saw().detune(0.1)) / 2`); no song uses sprudel's `fm`. A
listening pair is in `tmp/listening/pp-step4/`. Options: (a) keep it as decided, recorded; (b) rebuild `sgpad` so it
reads one pitch per FM (the author rule, `x.fm(...) + x.fm(...).detune(...)`, does not fit a slot-fed classic stage, so
this would need a look); (c) the build-time diagnostic task (`fm-above-forking-detune-diagnostic.md`) also covers a
sprudel door over a built-in. Recommendation: (a) for now, and decide when a song wants it.

## Q32. The pitch envelope stays a node (composition 7d, D10's fallback)

Your D10: compose the pitch envelope the tremolo's way, "the spike confirms the same sound and cost first". The spike
(`tmp/reviews/pp-7d-report.md`) did not confirm the cost, so the node stays, by D10's own rule:

```
s("saw").penv(12, 0.01, 0.1, 0)        // the sound: identical when composed (with an adsr that may go below 0)
// the cost once the envelope has settled (most of every note): today one ratio per block,
// composed one envelope step and one exponential per sample: about 2x on V8 (+1 us per voice per block),
// 1.7 to 2.6x on the JVM
```

A script-door penv with SIGNAL knobs would also sound different composed (a moving amount read per sample, like the
vibrato after 7b). Options: (a) keep the node (decided by default); (b) compose it anyway and accept about 1 us per
settled penv voice per block (0.04 % of a block per voice); (c) compose it and move the node's two "settled" shortcuts
into the envelope host (the code moves rather than goes). Recommendation: (a). Say if you prefer (b) or (c).

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
