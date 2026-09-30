# Stereo reverb: every room reaches both ears

> **Status: DONE 2026-09-30.** Option 2 in `Reverb.kt`, `CROSS_FEED` **0.5**: one room fed from both
> sides, the classic mono-in design, chosen by ear. Archived. The follow-ups live on in
> [`future/reverb-models.md`](../../tasks/future/reverb-models.md) (the umbrella, with
> [`future/room-reverb.md`](../../tasks/future/room-reverb.md)) and
> [`future/kokon-one-room.md`](../../tasks/future/kokon-one-room.md). Found while mixing Kokon, maintainer asked for the
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
2. **Cross-feed at the input**: `inL' = inL + k * (inR - inL)`, `inR' = inR + k * (inL - inR)` (the
   same as `(inL + c * inR) / (1 + c)` with `k = c / (1 + c)`, but written as a step so that an input
   with equal sides feeds exactly what it did, bit for bit). `k = 0` is today, `k = 0.5` is option 1.
   A room that leans to its source but reaches both ears. One subtraction, one multiply and two adds
   per sample (implemented this way).
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

## Decisions and follow-ups (2026-09-30)

- **No cross-feed knob in this task (leaning, agreed in discussion).** A knob whose zero end
  recreates two separate rooms would bring the bug back for anyone who turns it down "for width".
  If a song ever needs a narrower or wider room, the door is a `width` on the output side (Freeverb's
  `wet1` / `wet2`, 0 a mono-ish room, 1 the full natural room) with the input always blended the
  natural amount; neither end then splits the room.
- **Size-dependent timing** (a far-side room that answers later in a big room: pre-delay and early
  reflections) is a new class of reverb: [`future/room-reverb.md`](../../tasks/future/room-reverb.md). This task
  only fixes "two rooms", not "how big is the room".
- **Kokon, after this task: one room.** Kokon gives almost every line its own room (arp size 4,
  melody 5, swells 6, wings 3, last chord 6), which may be a second cause of the strange feeling: a
  band plays in one room. Parked by the maintainer: move to a master reverb as the shared room, with
  a slight dedicated reverb only on the lines that need their own depth. A/B by ear.

## Outcome (2026-09-30)

**The decision.** `CROSS_FEED` = 0.5, by ear, from A/B renders at 0, 0.15, 0.3 and 0.5 of Kokon (as
committed and with the arp and swells panned hard), Der Schmetterling (40 cycles, guitars at 0.10 and
0.90) and Sandsturm (the control). Maintainer: "On Schmetterling the 0.5 works really well. In Kokon it
is a bit strange when hard panned, as on the other ear there is only the impulse response. But I think
the 0.5 is ok as well." At 0.5 each side's combs are fed `(L + R) / 2`, the relation the original
Freeverb, Dattorro's plate and most mono-in designs have; the lean towards a source belongs in early
reflections, which this network does not have (hence the models task). Written as a step,
`feedL = inL + k (inR - inL)`, `feedR = inR - k (inR - inL)`, so an input with equal sides feeds exactly
what it did before, bit for bit (the engine's centre pan is equal to one ulp only, so a centred voice
matches to about 1e-16). Cost: one subtraction, one multiply, two adds per sample.

**Measured** (offline renders, 16-bit): Sandsturm, whose voices are all centred, byte-identical at every
value. Kokon with the arp hard left: the right ear held nothing during the 24 s intro (-219 dB, digital
silence), and holds the room at -39, -33 and -28 dB under the left at 0.15, 0.3 and 0.5. Whole-mix width
and level moved by 0.1 dB at most. A hard-panned source's reverb loses up to 3 dB in total at 0.5 (the
tanks are decorrelated): the Freeverb relation, accepted.

**Tests.** `ReverbNetworkLawSpec`: the Freeverb oracle written in the test blends its feed with a literal
0.5; a new row renders an input with equal sides against the oracle with no cross-feed, bit for bit.
`ClosedFormTailSpec`: a new row, a hard-left source gets a room in the right ear and the tail ceiling
still covers both sides' combs (the blend is a weighted average, so no comb input exceeds the feed's peak;
the audio reviewer proved it under rounding). `ReverbStabilitySpec`: `combPeakAbs` told the channels apart
by a one-sided impulse, which the one-room feed makes identical on both sides; it now uses the comb
lengths (the right combs are 23 samples longer) to put the loudest cell on each side in turn.
Mutation-checked, each red for the right reason: `CROSS_FEED` 0 (oracle row, right-ear row), a 1e-12
bias on the step (equal-sides row and oracle row), `CROSS_FEED` 3 (ceiling row), `combPeakAbs` scanning
only one channel, each way (both rows of the rewritten test). The inexact blend `(1 - k) L + k R` is an
equivalent mutant at 0.5 (multiplying by 0.5 is exact), so it cannot be caught there and is not a defect.

**Review.** Round 1 (blind, a code and an audio reviewer): no CRITICAL or MAJOR; three MINORs applied
as one batch (the loss formula is the hard-pan figure, `CROSS_FEED` private, "centred" stated as "equal
sides" with the one-ulp note) and the audio reviewer's optional one-step form (bit-identical). Out of
contract and not changed: above `Double.MAX_VALUE / 8` the allpasses can go non-finite before any comb
cell does, so `combPeakAbs`'s "allpass poison implies a poisoned comb cell first" is not strict there.
