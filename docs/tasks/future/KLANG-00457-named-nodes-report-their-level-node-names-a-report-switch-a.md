# KLANG-00457: Named nodes report their level: node names, a report switch, a UI

> **Status (2026-11-17): IDEA.** 
>
> **Part of:** KLANG-00196 (Realtime analysis: crowded, thin, loud, and who is responsible)

## Why this exists

The session of 2026-11-17 on Der Schmetterling: a compressor went onto the three guitars, and its threshold was
guessed three times because nobody could see the level arriving at it. The maintainer's idea, the same day.

It is the general form of the telemetry channel that KLANG-00142 (realtime analytics meters) names as its one
missing piece; that ticket's limiter gain-reduction meter and per-cylinder level ladder become readers of it.

## Decided with the maintainer (2026-11-17)

- **Any node in the graph can announce a name.** A node that has one reports its values back to the frontend
  (through the diagnostics message or something like it), and the UI can show them.
- **`.probe("NAME")` is a pass-through node** that does nothing but read the values. It is for the points between
  stages that have no node of their own.
- **Reporting is switched on and off by a dedicated message to the backend**, so it costs close to nothing when
  nobody looks.
- **sprudel keeps its fixed cylinder chain.** The idea of building the chain in the order the sprudel calls are
  written was weighed and dropped the same day: a later call overriding an earlier one (`.gain(...).gain(mul(...))`,
  a shape followed by an arrangement) is what sprudel songs rely on. Another order would be another DSL, some day.
  What follows from it here: the fixed chain's stages (body, vowel, delay, reverb, phaser, compressor, gain) can
  carry names of their own, so a song gets its readings without declaring a chain.
- **Ignitors wait.** A node inside a voice exists once per sounding voice, so its readings need a rule for
  combining them. Not needed for the first version.

So the work is three pieces: node names, the report switch, the frontend UI.

## What to measure (a proposal, not decided)

Per named node, between two reports, the engine keeps three numbers and resets them when it reports:

- **the peak**: the largest absolute sample. It says how close the signal comes to clipping and what a clipper, a
  shaper or a limiter reacts to.
- **the sum of squares and the sample count**: from these the UI computes the RMS (the root of the mean of the
  squares), the signal's average power, which is roughly how loud it sounds.
- **the gain reduction**, on a compressor or limiter node only: the deepest reduction in dB since the last report.

The UI does the rest: decibels, the RMS over a window of about 300 ms, a peak hold of a second or two, and the
crest factor (peak minus RMS in dB, how spiky the signal is). Keeping the windowing in the UI keeps the engine's
share at one compare and one multiply-add per sample.

## Decide before implementing

- UNVERIFIED: whether the diagnostics message the dispatcher sends every 20 ms of audio (KLANG-00399) can carry
  these values, or whether they need a message of their own. Nobody has read that code for this ticket.
- The per-block path must not allocate (the allocation check, incident of 2026-11-04): a fixed table of named
  nodes per playback, freed with the playback.
- What identifies a reading: the same chain runs on several cylinders, so the key is the cylinder and the name.
- Stereo: one reading per node (the louder side for the peak, both sides for the RMS) or one per side.
- Where the UI shows it: beside the code, in the Motor, or both. KLANG-00196 gates UI phases on the UI rework.

## Acceptance

Not written yet: it follows the decisions above.

## History

- 2026-11-17: created, from the Der Schmetterling session.
