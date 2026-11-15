# KLANG-00445: A physical voice: a vocal tract as a sound source

> **Status (2026-11-15): IDEA.** The maintainer's idea, 2026-11-14, after CHIMERA came out; researched at a high level, nothing designed.
>
> **Part of:** KLANG-00356 (Sound sources: instruments, samples, soundfonts)

## Why this exists

CHIMERA (github.com/jamesondh/chimera-vocal-synth, MIT, created 2026-11-05) is a voice synthesizer that simulates
the vocal apparatus instead of playing samples: lungs, vocal folds, a branched vocal tract, lips and nose. It
speaks, sings and beatboxes. The maintainer (2026-11-14): "I think it would neatly fit into our synthesizer."

The question was whether Klang could build something similar. The answer of the research: yes, as a sound source
of our own, but a voice at CHIMERA's fidelity is expensive, so it would be one lead singer and not a choir.

## What the research found

Read 2026-11-14 through `webq`: CHIMERA's `docs/architecture.md` and `docs/rust-engine.md`, its README, and the
README of the community plugin (chimera-lab). The source code was not read.

### How CHIMERA works

| Part | What it does |
|------|--------------|
| Tube | A network of short tube sections, pressure at the nodes and flow in the branches, updated in turn every sample (a staggered-grid FDTD transmission line). Not a Kelly-Lochbaum waveguide. |
| Rate | 96 kHz for an adult human, 192 kHz for small animals, 48 kHz allowed for very large tracts; decimated to the output rate. The section length follows from the stability condition (at least c·T/0.95): about 4 mm at 96 kHz, so about 45 sections for a 17 cm tract plus the branches (our arithmetic, not a figure of theirs). |
| Branches | Nose, sinuses, piriform fossae, two bronchi; vocal sacs for the creatures. |
| Glottis | A parametric pulse of the glottal opening area (opening and closing phase, open quotient, skew, largest area, rest area, a leak). It is a branch of the same network, so the flow shape and the devoicing under a closed mouth come from the tube. A two-mass fold model (Ishizaka and Flanagan) was designed and never built. |
| Noise | One mechanism for fricatives and aspiration: where the flow through a constriction turns turbulent (by its Reynolds number), noise is injected about 1 cm downstream; a second source at the lower teeth for sibilants. |
| Losses | Yielding walls (mass, spring and damper per node), viscous loss, the expansion loss at a constriction. |
| Output | A radiation load (Flanagan, R and L in parallel) at lips and nostrils; the output is the derivative of the radiated flow. |
| Control | About 21 articulators (jaw, tongue body x/y, tongue tip x/y, tip curl and more, with lung pressure and f0), each with critically damped second-order dynamics. The geometry becomes an area function every 64 to 128 output samples, interpolated per sample. |
| Text | Planners turn English text or IPA into timed gestures (the CMU Pronouncing Dictionary for English). They allocate and run off the audio thread. |
| Real time | `Engine::render` allocates nothing in the steady state and advances in fixed 128-sample sub-blocks; commands are method calls that apply at the next render. Switching the body builds a new network and allocates. |

### What it costs

The author's figures, one male voice at 2x oversampling, on a Ryzen 7 8745HS:

| Engine | Share of one core |
|--------|-------------------|
| TypeScript in Chromium | 46% |
| Rust as WASM in Chromium | 32% |
| Rust native | 19 to 21% |

The community plugin reports about 32% of one core's real-time budget for two held notes, 62% for four and 94% for
six (48 kHz, 256-sample blocks, its own machine), and advises starting with two.

A Kotlin/JS port would probably land near the TypeScript figure, as both run as JavaScript. That is an estimate,
not a measurement.

### How it would fit Klang

- A sound source in the engine, an Ignitor as `KarplusString` is one: the pitch from the note, the articulators as
  slots. `oversampleBy(2)` gives the 96 kHz the tube asks for.
- The split matches ours ("the engine is the horse"): text and gesture planning belong to the frontend and send
  timed articulator values over the wire, in seconds; only tube and glottis run in the engine.
- MIT is compatible with the AGPL where code is ported: the notice is kept and the work credited (`CREDITS.MD`
  and the Credits page, when it lands). The CMU dictionary has its own licence file, not read; it matters only
  with the English text front end.
- Their golden fixtures ask for 1e-9 on geometry and about 1e-6 of peak on audio, not for bit-exactness, so a port
  could be checked against them.

### Not found

- The size of the engine in lines of code.
- Which published anatomy the tract shapes are based on.
- The text of the CMU dictionary's licence.

## Decide before implementing

1. **Is this sound wanted in songs?** The maintainer listens to CHIMERA's web app first. Everything else waits for
   that.
2. **Our own, or theirs embedded?** The recommendation is our own, with CHIMERA's documents as the reference.
   Embedding the WASM module puts a second engine into the worklet, works only in the browser, and goes around
   Ignitors and Katalyst; the by-ear test it would buy is already there in their web app.
3. **How much fidelity?** The full tube at 96 kHz, or, if the spike (below) is too expensive, a Kelly-Lochbaum
   tube at 48 kHz (the Pink Trombone approach): from memory far cheaper, sings vowels well, gives up the accurate
   consonants.
4. **What the voice is played with:** vowels and a few articulator knobs, phonemes or syllables from sprudel, or
   English text. The recommendation is in that order, English text last or never.

## The work

A sketch, to be planned when the idea is taken up:

1. A spike: the glottal area pulse and one unbranched tube, vowels only, as an Ignitor. Measure its cost on
   Kotlin/JS; that number decides what follows.
2. The nasal branch, then the turbulence noise for consonants.
3. Gestures from sprudel: phonemes or syllables first.

## Acceptance

Open until the idea is planned. The spike is done when one sung vowel plays in a song and its cost per voice on
Kotlin/JS is measured and written here.

## History

- 2026-11-15: created from the research of 2026-11-14 (Klang-Research).
