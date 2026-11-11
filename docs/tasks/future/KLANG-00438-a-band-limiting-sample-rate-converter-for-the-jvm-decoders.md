# KLANG-00438: A band-limiting sample rate converter for the JVM decoders

> **Status (2026-11-11): IDEA.** Follow-up of KLANG-00430 (L3-05, L9-07), maintainer 2026-11-11.
>
> **Part of:** KLANG-00353 (The Motor: correct at the edges)

## Why this exists

A sample file that is not at the engine's rate is converted in two different ways (KLANG-00430, findings L3-05 and
L9-07): the browser converts at decode with its own converter, the JVM keeps the file's rate and converts in the
playhead with the cubic read, which leaves images in the top octave (a 10 kHz tone in a 44.1 kHz file: -30.6 dB). An
offline render and the browser therefore differ at the top.

The first half is done in KLANG-00430: the browser decodes at the engine's own rate (maintainer 2026-11-11: "yes
first now and follow-up ticket for the second"). This ticket is the second half.

## The work

A windowed-sinc converter in `audio_fe`'s JVM decoders, to the engine's rate, once per file at decode. The offline
render then matches the browser's in kind. New DSP: credited when it lands.

## Acceptance

- The aliasing meter's row "44.1 kHz file, 10 kHz tone" on the JVM is under -60 dB.
- The corpus rows that move are named.

## History

- 2026-11-11: created.
