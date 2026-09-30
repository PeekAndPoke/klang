# Stereo reverb: every room reaches both ears

> **Status (2026-09-30): OPEN, ready to design.** Found while mixing Kokon, maintainer asked for the
> task the same day. Changes the sound of every song that pans a voice through a reverb, so it lands
> before the tutorial phase locks the sound (`_v1-scope.md` Layer 2.5).

## What is wrong

`Reverb` (`audio_be/effects/Reverb.kt`) is two mono reverbs side by side. The left comb bank is fed
only from the left input, the right bank only from the right (`inpL` into `combBufsL`, `inpR` into
`combBufsR`); nothing crosses. The 23-sample spread decorrelates the two tails, but it cannot move
energy from one side to the other.

So a room always stays on the side of its source. Measured with the offline renderer, a saw note at
`pan(0.0)` with `reverb(wet = 0.5, size = 5)`:

| source pan | note L / R | tail L / R (0.5 to 1.5 s) |
|---|---|---|
| 0.5 | -7.9 / -8.2 dB | -42.0 / -39.7 dB |
| 0.25 | -6.9 / -14.8 dB | -39.7 / -45.1 dB |
| 0.0 | -6.9 / -180 dB | -39.0 / **-180 dB** |

The master reverb (`master(Katalyst(k => k.reverb(...)))`) is the same class and behaves the same:
a hard-left source leaves the right ear digitally silent, room included.

The original Freeverb does not do this: it sums both inputs into one tank
(`input = (inL + inR) * gain`) and spreads the two decorrelated outputs with a `width` mix. Our
KDoc still says "based on the Freeverb algorithm" and "a wide centred image even from a mono input",
which is true only for a centred source.

## Why it matters

In a real room the direct sound comes from the source's side, but the reflections arrive from
everywhere. With our reverb, voices panned apart each sit in their own half room. They separate well
but do not share a space; the maintainer's words, listening to Kokon with the arp hard left: "The
sounds mix well, but where do they belong?" The first 24 s of that version, the arp alone, had nothing at
all in the right ear.

Songs today work around it by not panning far. Kokon pulls its guitars in to 0.15 and 0.85 for this
reason (2026-09-30), which gives back some of the separation the panning bought. Der Schmetterling
pans its rhythm guitars to 0.10 and 0.90 through a shared reverb (`reverb(wet = 0.15, size = 3.0)`),
so their rooms are one-sided too.

## Options

1. **Mono tank** (Freeverb as written): feed `(inL + inR) / 2` into both banks. The room becomes
   fully diffuse whatever the source position. Simplest; loses any sense of the room leaning towards
   its source.
2. **Cross-feed at the input**: `inL' = inL + c * inR`, `inR' = inR + c * inL`, normalised by
   `1 / (1 + c)` so a **centred source keeps its reverb level exactly**. `c = 0` is today, `c = 1` is
   option 1. A room that leans to its source but reaches both ears. Two multiply-adds per sample.
3. **Width at the output** (Freeverb's `wet1` / `wet2`): mix the two tank outputs into each other.
   Close to option 2 in effect, but the cross-talk then carries the tank's decorrelation, not the
   dry input.

Leaning: **option 2 with a fixed engine constant**, value chosen by ear. The normalisation matters
most: most songs sit near the centre, and for them nothing should move. A `width` knob on the
`reverb` door is not part of this task; it becomes one only if a song needs it (a door on both DSLs,
parity, `/dsl-design`), and taste is also what you do not do.

## Constraints

- The Motor stays raw: this is a fixed property of the unit, not a clamp and not an authored knob.
- The deliberate `+ ANTI_DENORMAL` stays (guardrail, `audio-constraints.md`); `TailCeiling` and the
  drain countdown read the comb buffers and must keep agreeing with `process`.
- Both buses (orbit and master) use this class; they must keep agreeing, and the parameter-mapping
  contract in the KDoc stays as is.
- Block size stays 128 for every render in this task.
- Same code runs in the JS worklet (`commonMain`); nothing platform-specific.
- Fix the KDoc: say what the unit does with a panned source.

## Done when

- A hard-left source has a room in the right ear, at a level chosen by ear.
- A centred source's reverb is unchanged, measured (level and spectrum of the tail, before and after).
- Specs, mutation-checked (engine tier, `/review-loop`): a hard-left input produces a right-channel
  tail above a floor; a centred input matches the old output; the normalisation holds for `c` at its
  constant.
- By ear, before and after: Kokon (arp at 0.15, swells at 0.85, and a trial with them back at 0.0
  and 1.0), Der Schmetterling (guitars 0.10 and 0.90), and one mostly centred song to confirm nothing
  moves.
- If the hard pans now work in Kokon, decide with the maintainer whether Kokon goes back to them.
