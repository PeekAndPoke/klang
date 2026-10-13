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
door: `kotlin-typed-doors.md`), Q28 (solo: the latest value wins at once, engine
follow-up 43).

---

# Part 1: Blocking (work waits on the answer)

None open.

---

# Part 2: Decided by default, please confirm

Work went ahead with the conservative choice. A "no" here means a small follow-up change.

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

---

# Part 3: For later (not blocking anything now)
