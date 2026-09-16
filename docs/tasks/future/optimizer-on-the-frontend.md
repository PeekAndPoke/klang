# Run the ignitor graph optimizer on the frontend

Status: **future.** Maintainer, 2026-09-16: "for the future this would be nice to do it on the
frontend for two reasons: smaller wire footprint (in general), and we could show the optimized
graph in the UI."

## Today

`IgnitorDsl.optimize()` is a pure `IgnitorDsl -> IgnitorDsl` transform in `audio_bridge`, so it
is available on both sides of the wire. It RUNS in the backend: `IgnitorRegistry.register` (the
worklet's message handler on JS, the audio backend on the JVM) optimizes the authored tree once
per registered sound and stores both, the authored tree behind `get()` and the optimized one
behind `optimized()`, which is what voice creation lowers. The frontend, the UI and the param
listing see only the authored tree.

## The change

1. The frontend optimizes before sending; the wire carries the optimized tree. The pass is
   idempotent (the fuzz's law), so the backend may keep calling `optimize()` on what arrives, or
   skip it; keeping the call costs a tree walk per registration and keeps the backend safe
   against a frontend that forgot.
2. The UI shows the optimized graph. This half needs NO wire change: the frontend can call
   `optimize()` on the authored tree it already holds and render both, today. It is the cheap
   half and could land first.

## What to keep straight

- The backend's `get()` would then return the optimized tree, not the authored one. Nothing in
  the backend reads `get()` for authored semantics today (voice creation reads `optimized()`;
  `get()` exists for the frontend-facing contract), but the KDoc says "the tree AS AUTHORED" and
  must change with it.
- Params: `collectParams` order is a UI contract read from the AUTHORED tree on the frontend; that
  stays on the frontend and is unaffected. The optimized tree's params are a subset in the same
  order (the fuzz's params law), so nothing the backend keys by name changes.
- `optimizer(0)` (the kill switch hint) is handled inside the pass, so it keeps working wherever
  the pass runs.
- The never-throw fallback in `register` (a failing pass degrades to the authored tree, counted
  in `optimizerFailures`) has no equivalent on the frontend yet; the frontend needs the same
  shape, or the backend keeps its own call as the safety net.
- The wire footprint claim should be measured, not assumed: an `Eq` with sections is smaller than
  the filters it replaces and an `Affine` smaller than three arithmetic nodes, but a song's
  instrument definitions are sent once per registration, so the saving is per edit, not per
  note.
