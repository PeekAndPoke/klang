# KLANG-00304: Curves as nodes

> **Status (2026-10-20): IDEA.**
>
> **Part of:** KLANG-00356 (Sound sources: instruments, samples, soundfonts)

Status: **future, wanted (maintainer, 2026-10-13, Q13): "useful it definitely is"; needs a deep design round before
anything is built.**

A curve is just another node that can be plugged in everywhere: on the Ignitors and on the Katalysts alike. It
shapes a 0-to-1 input (a `progress()`, a tween's ramp, an envelope stage) into a 0-to-1 output.

What it would unify:
- the `adsr` stages' curves (`AdsrCurves`: linear, square, cube, scurve, invsquare, exponential);
- `progress(curve = linear)`, the first place a curve argument would appear
  (KLANG-00245 (pitch pipeline into the tree) 7f);
- tweens: one automation write with a duration and a curve
  (KLANG-00216 (signal graph engine) 6.8);
- fully individual curves an author draws or writes.

Questions for the design round: one curve vocabulary across both doors and both DSLs (Ignitor, Katalyst); a curve as
a value (an argument) or as a node in the graph, or both; how an individual curve is written; the cost per sample on
V8 and the JVM.

## History

- 2026-10-20: numbered KLANG-00304 (was `docs/tasks/future/curves.md`).
- 2026-10-27: part of KLANG-00356 (Sound sources: instruments, samples, soundfonts), the epic grouping.
