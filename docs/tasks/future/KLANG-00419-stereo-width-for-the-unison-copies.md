# KLANG-00419: Stereo width for the unison copies

> **Status (2026-11-06): IDEA.** From KLANG-00417 B7 (maintainer, 2026-11-06): unison(pan) removed, stereo width is its own ticket.
>
> **Part of:** KLANG-00354 (The Motor's shape: signal flow and the graph)

## Why this exists

The super oscillators (`supersaw`, `supersine`, ...) and `superpluck` sum their unison copies to mono. sprudel's
`unison(voices, spread, pan)` reserved a `pan` knob for the stereo width, wrote it to an `ignitorParams` key
(`panSpread`) and no engine stage ever read it; the tutorials had to warn against it. KLANG-00417 B7 removed it (no
dead knobs). A real stereo width is worth having: a wide supersaw is a staple sound.

## Decide before implementing

- The law: how each copy is placed (an even spread from left to right, alternating sides, the detune order).
- The knob's word and home: a slot of the super oscillators (`width`?) on both Ignitor doors, and the sprudel door
  that writes it (`unison(voices, spread, width)`), by the parity rules (`/dsl-design` sections 4 and 5).
- The cost and the contract: `Ignitor.generate` writes ONE mono buffer and the voice is panned only at its channel
  (`voices/voice_channel.kt`), so stereo copies need a second channel through the Ignitor contract and every stage
  after the source (`classic()`'s filters and envelopes), not only a wider sum. The biggest question of the ticket.
- Strudel's `spread` is a stereo width (its detune is `detune`), yet the Strudel-compatibility mapping
  (`GraalSprudelPattern`) maps it onto Klang's detune `spread`; settle the mapping with the word.

## The work

1. The design above, with the maintainer.
2. The engine stage, both Ignitor doors, the sprudel door, the parity rows, a corpus check (an unset width must
   render bit for bit as today).

## Acceptance

- `unison(5, 0.3, width)` (or the chosen word) spreads the copies across the stereo field; unset, nothing changes.

## History

- 2026-11-06: created.
