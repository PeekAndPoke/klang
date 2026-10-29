# KLANG-00385: Diagnostics as metrics: any owner posts, the Motor window graphs them by category

> **Status (2026-10-28): IN PROGRESS.** the plan written, being built (the maintainer, 2026-10-28)

## Why this exists

The maintainer, 2026-10-28: "The motor stats need to have some kind a "hook" where additional stats can be added. Then the
sprudel playback would call something like `klangPlayer.diagnostics.post(...)` ... This means we would need to rewrite
KlangMotorDiagnostics to work with arbitrary inputs. By doing so also other "things" could add diagnostics ... add
something like category = "CPU" to group multiple graphs." First use: how long sprudel's pattern work takes at runtime.

Today `KlangMotorDiagnostics` knows three readings by name (heat, cylinders, voices), and the Motor window draws one
graph each. Combining a list of streams that come and go (playbacks start and stop) is not something Ultra's
`combine` does; a clock that reads a registry is simpler and does the same.

## The design

**A metric is described once and posts plain numbers.** `diagnostics.metric(id, name, category, unit, min, max)`
returns a handle; `handle.post(value)` writes one `Double` and the time it came, no object per post (sprudel's timing
core and the scheduler stay allocation-free); `handle.close()` takes it off the registry.

**Metrics belong to their owner.** Whoever registers a metric closes it when it ends: the player its own metrics at
`shutdown()`, a playback its metric when it stops. Nothing grows with use (the rule "nothing allocates without a way
to clean it up"). A closed metric's line stays in the graph until it scrolls out of the two minutes, then it is gone.

**One clock samples them all.** A ticker (4 a second, as now) reads every registered metric: its latest value when it
posted within the last second, else a gap. The last two minutes of these readings are the history (Ultra's
`history()`; sampling a stream on a clock is ULTRA-00267). The history hands each metric's series out with its
description, oldest first, null for a gap; a metric that came later starts with gaps.

**The Motor's own readings become metrics, the player their owner.** The player subscribes to the backend's
diagnostics and posts: Motor heat (category CPU, %, 0 to 100), active cylinders (Cylinders, 0 to 32), active voices
(Voices, 0 to 100). `diagnostics.stream`, the latest backend diagnostics, stays as it is for the gauges and the
warehouse.

**Sprudel's runtime is the first new metric.** The pattern scheduler times its pattern queries (the next cycles and
the sample look-ahead) with the playback's clock, adds up the milliseconds, and every quarter second posts the share of
real time they took: Sprudel runtime (CPU, %, 0 to 100), registered when a playback starts, closed when it stops.
The playback context hands the player's diagnostics to the scheduler. A line is a metric's id, and every playback
registers Sprudel runtime under the one id `sprudel`, so playing, stopping and playing again goes on in one line after
a gap instead of piling up a line per play (the maintainer, 2026-10-29: "maybe a single sprudel entry is better and
simpler"); two songs playing at once share the line, the newer post showing.

**The Motor window draws one graph per category.** Its metrics are lines in it, each in its own colour, on the
category's scale (the widest of its metrics' ranges), with a legend: a lamp in the line's colour, the name, the latest
value with its unit (the maintainer, 2026-10-28: "Use the LED comp we have", "LED + Label in the legend"). The colours
are the UI's, chosen in the Motor window, never part of a metric ("I would decide the colors on the ui side"): fixed
data colours, hues far apart (cyan, amber, magenta, lime, violet, coral), as the look's spectrum steps were too alike
("Choose colors that are further apart"); the lamp takes a colour of its own for it. CPU then shows Motor heat and Sprudel runtime
side by side.

## The work

1. `klang`: `KlangMotorDiagnostics` becomes `KlangDiagnostics`: the backend stream as before; the registry of metric
   handles; the ticker, the history of readings, the series per metric. The player registers and posts its three
   metrics and closes them at shutdown.
2. `klang`: `KlangPlaybackContext` carries the diagnostics; `KlangPatternScheduler` registers Sprudel runtime at the
   start, times its queries, posts each quarter second, closes the metric at the stop.
3. App: `Player.diagnostics.history` hands out the new history; the Motor window draws a graph per category, lines and
   legend in the look's colours.
4. Specs: the registry and the history (a post within the second, a gap after, a closed metric scrolling out, a late
   metric starting with gaps, the capacity); the player's three metrics from the backend stream.

## Acceptance

- The Motor window shows CPU (Motor heat and Sprudel runtime, over the budget in red) and Cylinders and
  voices in one graph (the maintainer, 2026-10-29), each scaling to its values, with a legend each.
- A song playing shows Sprudel runtime; stopped, its line has a gap; played again, it goes on in the same line.
- No object is made per post; closing every metric leaves the registry empty.

## History

- 2026-10-28: created; the plan written and built: `KlangDiagnostics`, the player's three metrics, Sprudel runtime
  in the scheduler, the graphs per category with a lamp legend; waiting for the maintainer's look.
- 2026-10-29: Option B (one summed metric owned by the player) not taken: the maintainer saw the lines pile up on play
  and stop and asked for "an identifier that "survives" stop and play". The history keys its lines by metric id. A line
  per player (`KlangPlaybackSource`) came first; still "the empty metrics are still there", so one id for all
  playbacks, "a single sprudel entry is better and simpler". The legend is a grid of two columns.
