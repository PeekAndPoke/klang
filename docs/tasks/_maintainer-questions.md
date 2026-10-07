# Open questions for the maintainer

A running log while the coordinator works in a loop (maintainer, 2026-10-07: "keep a log of the blockers, I will
answer them in one go"). Newest at the bottom of each section. When answered, the answer moves into the task file
it belongs to, and the question leaves this list.

Branch: `engine-pass-1` (from `main` at `7b04120c`, v0.5.5).

## Blocking (work waits on the answer)

_(none yet)_

## Decided by default, please confirm (work went ahead with the conservative choice)

_(none yet)_

## For later (not blocking anything now)

- **Solo** ([`bugfix-solo-rests-and-amount.md`](bugfix-solo-rests-and-amount.md)): the ramp times (1.5 s in and
  out, 2 s hold; a faster way in for a full mute?), the cylinder tails under `solo(1.0)` (let them decay, or also mute
  cylinders with no soloed voice?), several solos (keep "strongest wins"?), the solo id (one per `solo` call?), and
  whether `control` stays the word for a data-only event.
- **Frozen songs** ([`song-orbit-ownership-review.md`](song-orbit-ownership-review.md)): fix their shared-orbit
  conflicts, or keep them as snapshots of the old sound?
