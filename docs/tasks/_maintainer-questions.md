# Open questions for the maintainer

A running log while the coordinator works in a loop (maintainer, 2026-10-07: "keep a log of the blockers, I will
answer them in one go"). Newest at the bottom of each part. When a question is answered, its answer moves into the
task file it belongs to, and the question leaves this list.

The questions are numbered, so an answer can be as short as "Q3: yes, Q7: keep".

State: `main` after v0.6.5 and the pause housekeeping (2026-10-13); where each stream picks up:
[`in-progress/NEXT-STEPS.md`](in-progress/NEXT-STEPS.md). The open engine items that are not questions:
[`engine-follow-ups.md`](in-progress/engine-follow-ups.md).

---

# Part 0: Before the refill (2026-10-13)

The calls that decide where the next work goes. All answered 2026-10-13: Q34 (the corpus re-accepted; songs move freely: `song-checks-move-freely.md`), Q35 (no ADAA exists, the
link fixed), Q36 (the eight merged branches and the worktree deleted), Q37 (rule 1 stays provisional and is expected to go: the next review loop tries reviewers running mutants again;
rule 2 dropped, the exception documented in its KDoc, and the register says rules are guard-rails), Q38 (after the refill, finish engine
follow-ups 31 to 41, as long as each stays a purely independent performance fix that changes no API), Q39 (fade, do not cut: engine follow-up 42), Q18 (the data tables stay positional, documented at
their helpers), Q19 (the disposal order stays, documented in the dispatcher's KDoc), Q20 (fix the frozen songs'
shared orbits: `song-orbit-ownership-review.md`), Q13 (`progress()` and `elapsed()`, curves as their own future
feature: `pitch-pipeline-into-the-tree.md` 7f, `future/curves.md`), Q33 and Q32 (accelerate and the pitch envelope both stay nodes: pitch 7d, 7e), Q29 (`duty(width)`: engine follow-up 44), Q24 (typed Kotlin doors with `.ign`/`.kat`, KlangScript keeps its own
door: `kotlin-typed-doors.md`), Q30 and Q31 (confirmed as decided by default; FM gets its own round with the
maintainer: `future/fm-revisit.md`), Q28 (solo: the latest value wins at once, engine
follow-up 43).

---

# Part 0b: The C calls you asked details for (2026-10-14)

## Q40 (C3). A NaN inside a moving signal: guard it per sample, or leave it to the author?

Home: [`bugfix-non-finite-pitch-strip-and-signals.md`](bugfix-non-finite-pitch-strip-and-signals.md) §2. A knob
(a number, a slot) is already guarded everywhere: a NaN knob means "off". This is about a SIGNAL that produces a NaN
or an infinity in the middle of a note, sample by sample, on three pitch paths:

```
Ign.saw().pitchModSemitones(Ign.sine(5).div(0))   // a signal that is +-Infinity or NaN at some samples
// today: a NaN or -Infinity sample makes the pitch ratio 0, the saw holds one value (a DC step, silence);
//        +Infinity pushes it to the ceiling. The same on fm's modulator and on pitchMod.
```

Nothing crashes, and only an author's own odd signal gets there. A per-sample guard costs: the `DelayLine` precedent
measured about +30 % on that loop. Options: (a) record "a non-finite signal sample is the author's own" and archive
the task; (b) guard the three paths per sample, measured first. Recommendation: (a).

## Q41 (C5). Two sprudel loose ends: `randL`, and `filter` in scripts

Home: [`sprudel-function-testing.md`](sprudel-function-testing.md).

1. **`randL(n)`** makes a list of n random numbers. Its only example fed a `partials(...)` parameter that does not
   exist anywhere, so nothing can consume the list today:
   ```
   s("saw").partials(randL(8))   // the documented example; there is no partials()
   ```
   Retire `randL`, or keep it until a list-taking parameter exists?
2. **`filter(...)` in KlangScript** takes a predicate over the event, but a script cannot read the event's fields:
   ```
   s("bd sd hh cp").filter(x => x.part.begin < 0.5)   // error: no 'part' on the event
   note("c d e f").filter(x => x.isOnset)             // error: no 'isOnset'
   ```
   Expose a few useful fields to scripts (`part.begin`, `isOnset`, ...), or drop the script door of `filter` (Kotlin
   keeps it)?

Recommendation: retire `randL`; expose `part.begin`, `part.end` and `isOnset`, because `filter` is useful.

## Q42 (C12). Block framing: one more sweep, and one known edge

Home: [`../plans/block-framing-invariance.md`](../plans/block-framing-invariance.md). "Block framing" means: the same
note must sound the same whether its blocks of 128 samples start at frame 0 or at frame 37 (playback starts at an
arbitrary point).

1. **The P2 sweep:** all 78 node types checked as a class (the filter and arithmetic nodes were never audited), first
   only as a list of fix / by design / ask. Wanted, or closed like the backend audit?
2. **E3:** a filter envelope whose attack is shorter than one block (under 2.7 ms) is erased, and its peak height
   depends on where the block starts:
   ```
   note("c3").s("saw").lpf(400).lpenv(4).lpattack(0.001)   // reachable now through classic()'s filter envelope
   // the 1 ms attack lands inside one block: the cutoff jumps instead of sweeping; peak height varies with framing
   ```
   Fix (a sub-block envelope step), accept, or leave it recorded?

Recommendation: 1 later, as part of the performance batch's quiet time; 2 recorded until a song uses a sub-block
filter attack.

## Q43 (C11, second half). The two laws: move them to `audio/ref/`?

`plans/effect-state-machines.md` and `plans/knob-glide.md` are no longer plans but the laws the engine follows
(how a stage switches on and off, how a knob glides). Move both to `audio/ref/` with their links, where the engine's
other references live? Recommendation: yes (knob glide's two ear items now live in
[`by-ear/knob-glide-listening.md`](by-ear/knob-glide-listening.md)).

---

# Part 1: Blocking (work waits on the answer)

None open.

---

# Part 2: Decided by default, please confirm

Work went ahead with the conservative choice. A "no" here means a small follow-up change.

None open.

---

# Part 3: For later (not blocking anything now)

None open.
