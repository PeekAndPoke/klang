# Reverb models: more than one kind of room

Status: **future, design round, not started.** Asked for by the maintainer 2026-09-30, after the stereo
reverb landed ([`../../tasks-archive/2026-09/20260930-stereo-reverb.md`](../../tasks-archive/2026-09/20260930-stereo-reverb.md)):
"design multiple reverb models based on what the research surfaced. Pre-requisite might be an IR
implementation."

## Why

One reverb cannot be every room. What the stereo reverb round showed, by ear:

- **Der Schmetterling**: the one-room tail (both sides' combs fed `(L + R) / 2`) "works really well".
- **Kokon with hard panning**: "a bit strange, as on the other ear there is only the impulse response".
  The far ear hears the room but never the source itself; a real ear would also get the direct sound,
  later and duller (the head's shadow), and early reflections that lean towards the source's side.
  Our network has no early part at all.

## What there is today

`Reverb` (`audio_be/.../effects/Reverb.kt`): a Freeverb tail, 8 parallel combs and 4 series allpasses per
channel, fed as one room (`CROSS_FEED` 0.5 since 2026-09-30). No pre-delay, no early reflections. The
first output comes after one revolution of the shortest comb, about 25 to 37 ms at every `size`; `size`
sets the comb feedback, so it changes how long the room rings, not how big it sounds at the start.
One class, pooled by `ReverbUnits`, shared by the orbit and the master bus.

## The families (from memory of the published designs; verify each before building on it)

UNVERIFIED as written here: the descriptions below are from memory, not read from a source this
session. Confirm each against the paper or code when the round starts.

1. **Mono in, stereo out** (Freeverb, Schroeder/Moorer, Dattorro's plate): the input summed to one tank,
   the stereo image from decorrelated outputs. What we have now.
2. **Plate** (Dattorro 1997, "Effect Design, Part 1", Griesinger style): mono in, input diffusers, a
   figure-eight tank, outputs tapped at several points per side. Dense, bright, fast build-up; the
   classic for a lead or a vocal.
3. **Feedback delay network / hall** (Jot and later): N delay lines coupled by an orthogonal mixing matrix,
   stereo injected into different lines, decay set per band. Smooth, long, few metallic tells.
4. **Room with early reflections** ("true stereo" in the algorithmic sense): a tapped delay line per side
   derived from a room size (and perhaps a source position) in front of a diffuse tail; the lean
   towards the source lives in the early part, the tail stays one room. The idea parked on
   2026-09-30 is this model: [`room-reverb.md`](room-reverb.md).
5. **Convolution** (an impulse response of a real space): "true stereo" as four responses, L to L, L to R,
   R to L, R to R, the cross paths usually quieter and later. The most faithful, the most expensive, and
   a reference to tune the algorithmic models against.

## The prerequisite: an IR implementation

Convolution is both a model and the measuring stick for the others, so it likely comes first.

- **The engine**: partitioned FFT convolution, uniform or non-uniform partitions, so a long response
  runs at block 128 without latency; CPU and memory budget in the JS worklet (the reverb is already the
  heaviest unit per sample).
- **Where responses come from**: loaded like samples (`audio_fe`), mono or stereo (four channels for
  true stereo), normalised to a common level.
- **Licensing**: a recorded impulse response of a real space carries its own licence; check it before
  one ships (the licensing rule in `CLAUDE.md`).
- **History**: the old `iresponse` / `ir` door and the wire field `iResponse` were retired 2026-09-16
  because no convolution reverb ever read them; a future one designs its own door
  (`docs/retired-names.md`). Related idea: [`ir-to-modal-table-extraction.md`](ir-to-modal-table-extraction.md)
  (an IR reduced to a `body()` material).

## To decide when it starts

- **Which models**, and how many: by ear, on songs with small and big rooms, leads and pads. Taste is
  also what we do not build.
- **The door**: how a song picks a model (a named model on `reverb`, like `body`'s material?). One word
  per concept, both DSLs, the same word on orbit and master, the gate's off value
  (`/dsl-design`, `audio/ref/off-values.md`).
- **The warehouse**: `ReverbUnits` pools one class today; several models need a pool per model or one
  unit that switches.
- **One room per song**: a shared room on the master with slight rooms per line (Kokon's one-room
  mix, done 2026-09-30: [`20260930-kokon-one-room.md`](../../tasks-archive/2026-09/20260930-kokon-one-room.md)) may matter as much as the model. Decide the song-level story with the models.
- **Tuning by ear before the tutorials lock the sound**, or explicitly after V1.
