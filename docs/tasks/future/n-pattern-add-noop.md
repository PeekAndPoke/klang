# `add()` does nothing on an `n()` pattern

Status: **future / unconfirmed footgun — found 2026-08-31 while probing tweaks, not investigated.**
Not a tweaks issue; filed separately so it does not get lost with that work.

## What happens

```klangscript
n("0 1 2").add(2).scale("c3:major")   // C3 D3 E3 — the add() did nothing
seq("0 1 2").add(2)                   // values 2, 3, 4 — works here
```

`add()` silently has no effect on a pattern built with `n()`. No error, no warning, the notes just
come out untransposed. It works as expected on `seq()`.

## Why (traced, not guessed)

`n("0")` stores the step into `soundIndex`. `add()` operates on `value`, which `n()` leaves null. So
the arithmetic runs against nothing and the result is discarded.

Confirmed pre-existing rather than something a caller does wrong, using two reference points:

- the plain baseline `n("0 1 2").add(2)` is inert, with no tweaks involved anywhere;
- `n("0 1 2").superimpose(x => x.add(2))` produces untransposed copies too, so any transform of this
  shape hits it, not just one particular call site.

`transpose()` works correctly on the same patterns, so there IS a working door.

## Why it is worth someone's time

This is the silent-inert class: a call that reads as if it does something, does nothing, and says
nothing. `n("0 2 4").add(7)` is a completely reasonable thing for someone to write when they want to
move a line up a fifth, and they will not find out that it did not work except by ear.

## Options, none decided

1. Make `add()` (and the arithmetic family) operate on `soundIndex` when `value` is null. Fixes the
   intent, but changes the meaning of arithmetic depending on how the pattern was built, which may be
   worse than the current honest-but-silent no-op.
2. Leave the behaviour and surface it: an editor diagnostic when an arithmetic call cannot reach
   anything. Same shape as the unknown-tweak diagnostic in
   `docs/tasks/future/mini-notation-tweaks-followups.md`.
3. Document it and do nothing, if the maintainer reads `n()` as deliberately index-space.

Needs a maintainer call on what `add()` is *supposed* to mean before any of these is right.
