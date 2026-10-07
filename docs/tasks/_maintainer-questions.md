# Open questions for the maintainer

A running log while the coordinator works in a loop (maintainer, 2026-10-07: "keep a log of the blockers, I will
answer them in one go"). Newest at the bottom of each section. When answered, the answer moves into the task file
it belongs to, and the question leaves this list.

Branch: `engine-pass-1` (from `main` at `7b04120c`, v0.5.5).

## Blocking (work waits on the answer)

_(none yet)_

## Decided by default, please confirm (work went ahead with the conservative choice)

- **Unison voice cap: 64** (empty-variants fix, `engine-pass-1`). `voices(1e9)` or an infinite signal allocated
  without bound on the audio thread at note-on. `/code-style` §21 allows clamping counts; 64 keeps every builtin sound
  byte-identical (the largest authored count is 32, `TetrisRemix.kt:47`). A non-finite count reads as 0.
- **An empty `variants()` on a Katalyst bus knob reads 0.0** (same fix). Before, it threw, was caught, and the knob
  fell back to its default; now "an empty variants is silence" holds everywhere. Say if a bus knob should keep its
  default instead.

## For later (not blocking anything now)

- **Solo** ([`bugfix-solo-rests-and-amount.md`](bugfix-solo-rests-and-amount.md)): the ramp times (1.5 s in and
  out, 2 s hold; a faster way in for a full mute?), the cylinder tails under `solo(1.0)` (let them decay, or also mute
  cylinders with no soloed voice?), several solos (keep "strongest wins"?), the solo id (one per `solo` call?), and
  whether `control` stays the word for a data-only event.
- **Helper merges that change behaviour** ([`utils-home-pass.md`](utils-home-pass.md), "Left for a decision"):
  fold `Environment.loadLibrary`'s own Levenshtein into `suggestNames` (the error message changes); swap
  `MnRenderer.renderNumber` for `formatAsIntOrDouble` (fixes a clamp above `Int.MAX_VALUE`); one home in `common` for the
  four text-position helpers; a small class for the tracked-timeouts code shared by `MnEditorBase` and
  `NoteStaffEditor`. The coordinator would do all four as small reviewed steps unless you say otherwise.
- **Frozen songs** ([`song-orbit-ownership-review.md`](song-orbit-ownership-review.md)): fix their shared-orbit
  conflicts, or keep them as snapshots of the old sound?
