# Open questions for the maintainer

A running log while the coordinator works in a loop (maintainer, 2026-10-07: "keep a log of the blockers, I will
answer them in one go"). Newest at the bottom of each part. When a question is answered, its answer moves into the
task file it belongs to, and the question leaves this list.

The questions are numbered, so an answer can be as short as "Q3: yes, Q7: keep".

State: `main` after v0.6.5 and the pause housekeeping (2026-10-13); where each stream picks up:
[`in-progress/NEXT-STEPS.md`](in-progress/NEXT-STEPS.md). The open engine items that are not questions:
KLANG-00290 (engine follow ups).

---

# Part 0: Before the refill (2026-10-13)

The calls that decide where the next work goes. All answered 2026-10-13: Q34 (the corpus re-accepted; songs move freely: KLANG-00312 (song checks move freely)), Q35 (no ADAA exists, the
link fixed), Q36 (the eight merged branches and the worktree deleted), Q37 (rule 1 stays provisional and is expected to go: the next review loop tries reviewers running mutants again;
rule 2 dropped, the exception documented in its KDoc, and the register says rules are guard-rails), Q38 (after the refill, finish engine
follow-ups 31 to 41, as long as each stays a purely independent performance fix that changes no API), Q39 (fade, do not cut: engine follow-up 42), Q18 (the data tables stay positional, documented at
their helpers), Q19 (the disposal order stays, documented in the dispatcher's KDoc), Q20 (fix the frozen songs'
shared orbits: KLANG-00281 (song orbit ownership review)), Q13 (`progress()` and `elapsed()`, curves as their own future
feature: KLANG-00245 (pitch pipeline into the tree) 7f, KLANG-00304 (curves)), Q33 and Q32 (accelerate and the pitch envelope both stay nodes: pitch 7d, 7e), Q29 (`duty(width)`: engine follow-up 44), Q24 (typed Kotlin doors with `.ign`/`.kat`, KlangScript keeps its own
door: KLANG-00314 (kotlin typed doors)), Q30 and Q31 (confirmed as decided by default; FM gets its own round with the
maintainer: KLANG-00305 (fm revisit)), Q28 (solo: the latest value wins at once, engine
follow-up 43).

---

# Part 0b: The C calls you asked details for (2026-10-14)

All answered 2026-10-14: Q40 (C3, NaN in a signal is the author's own; the task archived), Q41 (C5, `randL` and
`filter` cut), Q42 (C12, the next block-framing round when the nodes are stable: KLANG-00156 (block framing invariance)),
Q43 (the two laws moved to `audio/ref/`).

---

# Part 0c: Typed Kotlin doors, the design's open points (2026-10-16)

All answered 2026-10-16 (Q44 to Q51): recorded in KLANG-00314 (kotlin typed doors), "The maintainer's
answers". Q46 adds typed Kotlin `Ign` and `Kat` objects for surface parity.

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
