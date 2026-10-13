# Curves as nodes

Status: **future, wanted (maintainer, 2026-10-13, Q13): "useful it definitely is"; needs a deep design round before
anything is built.**

A curve is just another node that can be plugged in everywhere: on the Ignitors and on the Katalysts alike. It
shapes a 0-to-1 input (a `progress()`, a tween's ramp, an envelope stage) into a 0-to-1 output.

What it would unify:
- the `adsr` stages' curves (`AdsrCurves`: linear, square, cube, scurve, invsquare, exponential);
- `progress(curve = linear)`, the first place a curve argument would appear
  ([`../pitch-pipeline-into-the-tree.md`](../pitch-pipeline-into-the-tree.md) 7f);
- tweens: one automation write with a duration and a curve
  ([`../../plans/future/signal-graph-engine.md`](../../plans/future/signal-graph-engine.md) 6.8);
- fully individual curves an author draws or writes.

Questions for the design round: one curve vocabulary across both doors and both DSLs (Ignitor, Katalyst); a curve as
a value (an argument) or as a node in the graph, or both; how an individual curve is written; the cost per sample on
V8 and the JVM.
