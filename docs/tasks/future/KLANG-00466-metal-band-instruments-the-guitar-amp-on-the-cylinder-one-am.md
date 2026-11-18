# KLANG-00466: Metal band instruments: the guitar amp on the cylinder, one amp per guitarist

> **Status (2026-11-18): IDEA.** 
>
> **Part of:** KLANG-00459 (Metal band instruments: one band, the same instruments in every metal song)

## Why this exists

The maintainer (2026-11-18): "we really need to work on the guitar tone ... the shared cylinder amp might be the
solution, or at least a good start for real improvements. Because no matter how I turn the knobs, something is
always off." And: the two rhythm guitars of Der Schmetterling should go through one amp.

Today the whole rig is part of the voice, so every note has an amp of its own (KLANG-00459, "Found by reading the
rig"): notes never meet inside a clipper, and every note starts a cold amp. A real guitarist has one amp.

The plan: the voice keeps the string and the pickup; the pedal, the preamp, the power amp, the cab and the level
trim become a declared chain on the guitar's cylinder.

## What exists already (read from the tree, 2026-11-18)

- **The cylinder's `distort` is the voice's**: the same shaper core, the same shapes, the same drive law, left and
  right each through a core of their own. Its KDoc names this use: "the amp fed by a whole chord". One deliberate
  difference: its DC blocker's corner is near 7.6 Hz, the voice's near 35 Hz.
- **On a chain today:** `distort`, `eq` with `band` and `tap`, `mul` and the other arithmetic, `parallel`, `serial`,
  `oversampleAtLeast`, `body` (the materials), `vowel`, `compressor`, `limiter`, `reverb`, `gain`. So `body` and
  `vowel` need no work.
- **The filters run in the cylinder's EQ engine already.** `EqCore` has lowpass, highpass, bandpass and notch
  section kinds, the wire carries them (`eqLowpass`, `eqHighpass`, ...), and `eqSectionSpec` maps them for the voice
  and the cylinder alike. What is missing is only their WORDS: the EQ builders offer `band` and `tap`, on both
  surfaces (KLANG-00250 (general EQ core) says the same).
- **The chain stays alive between notes.** A cylinder never deactivates while a voice plays on it. It goes after its
  mix has been silent for the grace (ten clean-up visits, one cylinder visited per block, so with ten cylinders
  about 0.3 s) and nothing rings; then the chain is reset. An amp that has been silent that long is at rest anyway.

## What is missing

1. **`lowpass` and `highpass` on a cylinder chain**, with `q` and `passes`. Every part of the rig uses them (the
   highpasses between the gain stages, the pickup's resonance, the cab's wall). No new DSP: the door appends
   sections to the shared EQ core, a cascade as N sections on the Butterworth ladder.
2. Nothing else for the amp. `shape` and `drive` have no cylinder word, but only `guitarShape` and `powerStock` use
   them, and both can be written with what exists.

"All Ignitor nodes on the Katalyst" is not needed, and about half of them cannot exist on a cylinder: oscillators,
envelopes and the pitch nodes need a note, and a cylinder has none.

## Decide before implementing (the maintainer's, with `/dsl-design`)

- **The door's shape.** Proposed: `k.lowpass(freq, q, x => x.passes(n))` and `k.highpass(...)` on the chain itself,
  the same words as the voice's doors, so a rig part's body ports word for word and a part written as a plain
  function can serve a voice and a cylinder alike. The alternative is section words inside `eq(...)` only, which is
  less to build and reads worse in a rig.
- **Whether `bandpass` and `notch` come in the same step** (the parity rule says a word exists on both surfaces).
- **How this sits beside KLANG-00250**, which wants one EQ door for everything: this is its smallest slice, or it
  waits for that design.
- **One cylinder for both rhythm guitars, or one each.** One cylinder is what the maintainer asked for. Their two
  lines are mostly an octave, a fifth, or both apart (bar 1: 11, 11, 7, 4, 7, 7, 11, 7 scale steps), so together
  they are power chords split over two guitars, and one amp would let them fuse. Where the lines are a third apart
  it will sound rougher, as in a real amp.

## The song work, once the words exist

- **Split the instrument.** `makeGuitar` ends at the pickup. The rest becomes `Katalyst(k => k.oversampleAtLeast(4,
  r => r.serial(pedal, preamp, power, cab, level)))`, attached with `.katalyst(...)`.
- **Levels change meaning.** The voice's `gain` now sets how hard the amp is hit, and so does the number of notes
  sounding. The trim to mix level (`guitarShape`'s 0.18) moves to the chain's end. The compressor and the faders are
  set again. This is the two-level picture of KLANG-00460 (the level contract).
- **Bus settings move into the chain.** On a shared cylinder only the newest voice's bus settings are heard, and the
  two rhythm guitars set different `body` materials today (maple, mahogany). In the chain they are fixed numbers.
- **Pan comes before the amp.** Left and right are two amps. The rhythm guitars, each doubled left and right, get
  one amp per side, which is what double tracking is. A centred guitar reaches both amps at a lower level.
- **Keep the old rig for the A/B.** The per-note rig stays selectable until the maintainer has compared the two.

## What to expect, and what nobody knows yet

- Intervals fuse and grow difference tones; overlapping notes and release tails intermodulate too.
- One rig per cylinder instead of one per voice costs less.
- Probes get easier: inside a cylinder amp every stage is one signal, so KLANG-00457 (named nodes report their
  level) can read it without the per-voice question.
- UNVERIFIED: that an `eq` inside a cylinder's oversampling region computes its coefficients for the region's
  rate; that two patterns naming the same `Katalyst` value on one cylinder cause no chain swap; how the 7 and 5
  detuned unison voices sound when both guitars meet in one clipper (fewer may be wanted).
- Nothing here has been listened to. "Fatter" is a prediction.

## Acceptance

One rig of Der Schmetterling runs as a cylinder chain, the maintainer has compared it with the per-note rig, and
the decision which one the band keeps is recorded here.

## History

- 2026-11-18: created, from the maintainer's question what a shared guitar amp needs.
