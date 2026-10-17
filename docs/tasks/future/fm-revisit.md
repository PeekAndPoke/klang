# FM: a round with the maintainer

Status: **future (maintainer, 2026-10-13): "we need to visit fm anyway, as I have no experience with it at all."**
A listening and design round on frequency modulation, together, before more FM work. Everything FM that is open or
was decided by default is collected here, so the round starts from one page.

## What stands today

- **An `fm` without an envelope keeps its depth through the release tail** (Q30, confirmed 2026-10-13): the node runs
  an envelope only when one is written, as the Ignitor door's `fm` always did; before pitch step 4, sprudel's strip
  dropped the depth at the gate. `note("c3").s("sine").fm(300, 1.4).release(0.5)`. Listening pair:
  `tmp/listening/pp-step4/`, heard 2026-10-17: "keep the new version".
- **`s("sgpad").fm(...)` loses its pitch** (Q31, 2026-10-13: kept as recorded, decided when a song wants it): one
  modulator serves both detuned saw layers and jumps a block of phase at every block boundary (95 % of the energy off
  the harmonic grid, block-size dependent). The only built-in with that shape; no song uses sprudel's `fm`. Options
  then: rebuild `sgpad` to read one pitch per FM, or let the diagnostic below name it. Heard 2026-10-17 (listening
  pair `tmp/listening/pp-step4/sgpad-*.wav`): the after "sounds broken, at least very different".
- **The fm diagnostic** ([`fm-above-forking-detune-diagnostic.md`](fm-above-forking-detune-diagnostic.md),
  C6 of the 2026-10-10 baseline): the first editor diagnostic, for an fm above a forking `detune`.
- **FM's V8 allocations**: engine follow-ups 10c and 10d
  ([`../in-progress/engine-follow-ups.md`](../in-progress/engine-follow-ups.md)), performance only.

## For the round

What FM is for in Klang's sound (bells, electric pianos, basses, metallic hits), a few listening examples on both
doors, and then which of the above matter.
