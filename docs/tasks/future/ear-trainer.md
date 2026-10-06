# An ear trainer: learning to hear what a knob does

Status: **future, idea, not started.** Asked for by the maintainer 2026-10-07, while tuning Kokon's high-gain preamp:

> "I can change the values however I want, there is no chance for me to get the desired sound, because I do not know
> / hear the difference between the shapes, and worse when they are stacked."

A future learning tool for Klang, next to the tutorials (`../tutorial-curriculum.md`, `../tutorial-master-plan.md`).
The first subject is the distortion shapes; the same tool fits every knob whose effect is hard to name by ear.

## Why

Tuning by ear needs a vocabulary of the ear first. Without it, the knobs of a chain like this one are guesswork:

```javascript
let preampHighGain = x => x
  .highpass(120)
  .distort(0.40, "tube", 4).highpass(110)
  .distort(0.55, "softsat", 4).highpass(100)
  .distort(0.65, "soft", 4)
  .lowpass(5800)
  .mul(0.30)
```

What the measurements of 2026-10-06/07 showed about it (Kokon's `chug`, tapped after every stage; the waveform crest
over 20 ms frames as the measure of how much a stage squashes):

- Almost all of the saturation happens in ONE stage, the third (6 dB of squash); stage 1 barely acts (its input is
  too quiet), stage 2 squashes 1.2 dB, the power amp is practically clean at that level.
- Driven that deep, every curve ends up near a square wave: swapping the third stage's shape or drive (`hard`,
  `soft`, `softsat`, `tube`, at matched output level) moved the fizz band by 2 dB at most. So the shapes are only
  learnable at light and medium drive, and a trainer must say so: at heavy drive they converge, which is a lesson too.
- The roar (150 to 500 Hz) is made by the cab's EQ, not by the saturation. A trainer that teaches "what each stage is
  for" would have made that obvious before any tuning.

## Shape of the idea

**Real engine sounds, not imitations.** The clips are rendered offline by the Klang engine (`console/record.sh`), so
a learner hears exactly what `distort(..., "tube")` does in a song. Every clip **level-matched** (measured loudness),
or the louder one always seems "better" or "more distorted".

**Four modes:**

1. **Learn.** Each shape at three drives (light, medium, heavy), labelled, with its curve (how it bends the waveform)
   and its spectrum (which harmonics it adds: `asym` and `tube` even ones, warmer; `hard` and `soft` odd ones, harsher).
2. **A/B.** Any two shapes at the same drive, switching back and forth.
3. **Quiz.** A random clip, pick the shape; a confusion table shows which differences the learner already hears
   ("I mix up softsat and soft, never hard and tube").
4. **Stacks.** The same quiz with two or three stages in a row, at a real chain's levels, the case that is hardest by
   ear and the one this request came from.

**Sources:** a single note and a power chord, each as a plain sawtooth and as a real guitar chain's input (string,
pickup, pedal), so the training transfers to the songs.

**Shapes first:** the ones that matter for guitars (`soft`, `hard`, `softsat`, `tube`, `asym`, `gentle`, `cubic`,
`diode`), not all sixteen of `DistortionShapes` at once.

## Later subjects (the same tool)

Filter types and slopes, reverb sizes (the size table in the music-writing reference is a ready answer key),
compressor settings, unison width and analog drift (the harmonica effect of 2026-10-03), the stages of a rig in
order ("what does each step add?").

## To decide when it starts

- In the app (part of the tutorials, a page or a lesson type) or a separate page; how the clips are produced and
  shipped (rendered at build time, or rendered live in the worklet).
- How a stage of a real chain is tapped for the "what does each step add?" mode (`through` makes a rig a list, so a
  prefix of it is a tap).
- Whether the quiz keeps a learner's progress (per viewer, private).
