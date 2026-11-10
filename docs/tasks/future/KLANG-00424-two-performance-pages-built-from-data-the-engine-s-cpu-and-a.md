# KLANG-00424: Two performance pages built from data: the engine's CPU and allocations, sprudel's performance

> **Status (2026-11-10): IDEA.** Waits for the frontend refactoring (KLANG-00422); the release tool (the ticket before this one) builds them.

## Why this exists

The maintainer (2026-11-10), for the release process (KLANG-00423): "plus two new pages: Engine Cpu Performance /
Allocations, Sprudel Performance ... these pages must be built from data automatically, this means we need
automation to first gather the data and then build the pages. The performance pages might also include a history of
previous runs and comparison and trend graphs."

They join the Topic Map, the Mission Log and the Engine Health on the Dev status page. It waits until the frontend
refactoring (KLANG-00422) is done (the maintainer, the same day).

## What exists

- **The engine's CPU:** `console/run-dsp-benchmarks.sh` runs the Ignitors and the effects on the JVM and on Node.js
  and writes dated Markdown into `docs/benchmarks/` (a JVM file, a Node.js file, a comparison). The stage cost
  probes of KLANG-00420 (the DSP stack review) measure stages one by one; their register is "The probes" in
  `audio/ref/verification.md`.
- **The engine's allocations:** `docs/benchmarks/alloc-scoreboard.md`, and the allocation step of
  `console/full-check.sh`.
- **Sprudel's performance:** nothing found (2026-11-10): no benchmark of the pattern engine.
- **The pages' machinery:** `console/dev-status/` (`build.py`, `engine_health.py`, the templates, `hud.css`), each
  page a flat HTML file in `src/jsMain/resources/`, a card on the Dev status page.

## Proposal

- **Data first, as files.** Each run writes one JSON file per subject into a dated folder (as the probes do in
  `docs/audio-audit/<date>/`), with the machine it ran on, the commit and the version. The Markdown of today is made
  from it, not the other way round.
- **A history that compares like with like.** The page shows the newest run, the run before, and a trend over the
  runs of the same machine: a number measured elsewhere is shown, marked, and left out of the trend.
- **The pages are built by a script each,** like `engine_health.py`, with no hand step: the release tool calls
  "gather" and then "build". A switch builds from the last run's data without measuring.
- **Sprudel needs its measurement first:** what to measure (querying a set of real patterns from the songs over a
  number of cycles, on the JVM and on JS) is decided with the maintainer.

## Decide before implementing

- **D1, which numbers lead each page:** for the engine, time per voice and per effect and the allocations per
  block; for sprudel, to be chosen.
- **D2, where the data lives:** beside the probes' runs in `docs/audio-audit/`, or in `docs/benchmarks/`.
- **D3, the machine:** one reference machine whose runs make the trend, or a trend per machine.
- **D4, how long a release may measure:** the full benchmarks, or a short set for a release and the full one on
  request.

## The work

1. The engine's CPU and allocation data as JSON, with the machine and the commit; the page from it.
2. The history and the trend graphs.
3. Sprudel's benchmark (after D1), its data, its page.
4. The two cards on the Dev status page, their previews; the release tool's calls (KLANG-00423).

## Acceptance

- Each page is built from data files by one command, with no hand edit.
- A second run shows the comparison with the first and a trend point.
- A run on another machine does not bend the trend.

## History

- 2026-11-10: created from the maintainer's list for the release process; waits for KLANG-00422.
