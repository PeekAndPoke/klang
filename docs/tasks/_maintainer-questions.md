# Open questions for the maintainer

A running log while the coordinator works in a loop (maintainer, 2026-10-07: "keep a log of the blockers, I will
answer them in one go"). Newest at the bottom of each section. When answered, the answer moves into the task file
it belongs to, and the question leaves this list.

Branch: `engine-pass-1` (from `main` at `7b04120c`, v0.5.5).

## Blocking (work waits on the answer)

- **Pitch pipeline, D6: bare instruments lose the pitch doors** ([`pitch-pipeline-into-the-tree.md`](pitch-pipeline-into-the-tree.md)
  §8). Moving sprudel's `penv` / `vib` / `accelerate` / `fm` into `classic()` stages means an instrument that does not
  end in `classic()` no longer hears those doors (today the strip applies them to every voice). No corpus song is
  affected. Recommendation: accept (one rule for every door). Blocks step 1 (penv) onward; step 0 can go ahead.
- **Pitch pipeline, D2 (blocks step 3): one accelerate base for both doors.** The strip glides over onset to scheduled
  end (release included), the Ignitor node over the gate. Recommendation: the strip's base for both (every song hears
  it today); the Ignitor door's `accelerate` changes sound, no song uses it.
- **Pitch pipeline, D1 and D3 (block step 4, FM).** D1: a pitch door stops bending an `fm` node's modulator (the tree's
  semantics); recommendation: accept now, a small "bend the whole operator" item later for the ear. D3: FM moves onto
  the node's law (per-sample envelope instead of block-held, closing ledger E11), proven by listening pairs, and sprudel's
  `fm` gets a `release`; recommendation: yes to both.

## Decided by default, please confirm (work went ahead with the conservative choice)

- **Unison voice cap: 64** (empty-variants fix, `engine-pass-1`). `voices(1e9)` or an infinite signal allocated
  without bound on the audio thread at note-on. `/code-style` §21 allows clamping counts; 64 keeps every builtin sound
  byte-identical (the largest authored count is 32, `TetrisRemix.kt:47`). A non-finite count reads as 0.
- **An empty `variants()` on a Katalyst bus knob reads 0.0** (same fix). Before, it threw, was caught, and the knob
  fell back to its default; now "an empty variants is silence" holds everywhere. Say if a bus knob should keep its
  default instead.
- **Solo defaults** ([`bugfix-solo-rests-and-amount.md`](bugfix-solo-rests-and-amount.md), fixed on
  `engine-pass-1`): kept today's behaviour for the ramps (1.5 s in and out, 2 s hold), the cylinder tails under
  `solo(1.0)` (they decay naturally), several solos (the strongest wins), the word `control`; the solo id is one per
  call site (`"solo@" + the call location`, stable across re-evaluation, no collisions across modules). Two small
  differences: a source re-recorded keeps the last amount and the later end; protection ends 2 s after the source's
  last solo event (not its last voice), so a soloed release tail longer than 2 s is ducked only if another solo is live.

- **Pitch pipeline, D4, D5, D7** (§8): slot names follow sprudel's readers (`fm.h`, `fm.env`, `vibrato.depth`; the
  asymmetry with the node's `ratio` / `depth` / `semitones` recorded); each door's wire fields are cut in its own step
  (not all at the end, the one deviation from the task text); `voices/strip/` dissolves into `voices/` at the end.

## For later (not blocking anything now)

- **Solo ramp times** (solo fix review, reviewer B): today 1.5 s in and out (a cubic swell) with a 2 s hold, so
  `solo("<1 0>")` is a slow swell, not a toggle (at cps 1 it never settles). Recommendation from review: one ramp of about
  0.1 to 0.25 s both ways, hold `max(ramp, 0.5 s)`. Kept at 1.5 s until you decide; the KDoc now says it fades.
- **A soloed release tail beside another solo** (same review): protection now ends 2 s after the source's last solo
  event, not after its last voice, so a long release (a 5 s pad) is ducked mid-tail if another solo is live (measured
  -23.6 to -40.8 dB at 4 s). The old code let it ring at full level. The click is fixed (a 128-frame ramp); whether the
  tail should stay protected until the voice ends is yours.
- **Pitch pipeline, the composition block (D8 to D11)** (§8): the semitone pitch primitive's name (`pitchMod` stays the
  linear one); no vibrato `range` or `phase` on sprudel for now; compose the pitch envelope through `adsr` only after a
  spike; accelerate and FM stay nodes. Recommendations as written in §8.

- **Helper merges that change behaviour** ([`utils-home-pass.md`](utils-home-pass.md), "Left for a decision"):
  fold `Environment.loadLibrary`'s own Levenshtein into `suggestNames` (the error message changes); swap
  `MnRenderer.renderNumber` for `formatAsIntOrDouble` (fixes a clamp above `Int.MAX_VALUE`); one home in `common` for the
  four text-position helpers; a small class for the tracked-timeouts code shared by `MnEditorBase` and
  `NoteStaffEditor`. The coordinator would do all four as small reviewed steps unless you say otherwise.
- **Frozen songs** ([`song-orbit-ownership-review.md`](song-orbit-ownership-review.md)): fix their shared-orbit
  conflicts, or keep them as snapshots of the old sound?
