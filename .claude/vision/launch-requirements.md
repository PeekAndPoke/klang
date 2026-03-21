# Launch Requirements

*Written: 2026-03-20*
*Status: Under consideration*

## Launch Philosophy

A production platform launches when someone can **make something they're proud of and share it**.
Not when there are enough lessons. Not when every feature is polished. When the creative loop
is complete enough to be satisfying.

## Minimum Creative Loop

For Klang to be worth launching, a user must be able to:

1. **Start** — Open the platform and immediately hear/see something (or start from silence)
2. **Create** — Build a musical idea using Sprudel patterns and/or direct interaction
3. **Shape** — Modify the sound with synthesis parameters and effects
4. **See** — Understand what's happening (waveforms, pattern highlighting, signal flow)
5. **Finish** — Have some sense of a "complete" piece (even if it's a loop)
6. **Share** — Get it out: audio export, shareable link, or embed

## Minimum Feature Set (under discussion)

### Sound Sources

- [ ] Synthesizers with visible parameters (oscillator, envelope, filter)
- [ ] Sample playback
- [x] Audio engine is functional and sounds good (already validated)

### Composition

- [x] Sprudel pattern language (functional)
- [ ] Intuitive enough for non-coders to start modifying patterns
- [ ] Multiple tracks/orbits composable together
- [ ] Interactive playback with code highlighting

### Sound Shaping

- [ ] Effects: reverb, delay, distortion (at minimum)
- [x] Oscilloscope / waveform visualization (in progress)
- [ ] Visible signal chain (see what each effect does)
- [ ] Mixing: volume, pan per orbit

### Output

- [ ] Audio export (render to WAV/MP3)
- [ ] Shareable links or embeds
- [ ] Save/load projects

### Pedagogic Transparency (design standard, not feature list)

- Every sound parameter visually responds to changes
- Pattern code highlights during playback
- Effects show before/after on the signal
- No feature is a black box

## What's NOT Required for Launch

- Mobile apps
- Social features / community
- Video tutorials
- Formal partnerships
- Complete music theory curriculum
- Feature parity with Ableton
- Account system (can come later; local-first is fine)

## Open Questions

1. **Arrangement**: How does a user go from "a cool loop" to "a track with structure"?
   Live coding tools typically punt on this. Klang needs an answer eventually, but does it
   need one for launch?

2. **Recording**: Can users record live audio (voice, instrument) into Klang? This would
   massively expand who can use it, but adds complexity.

3. **Templates**: Pre-built starting points by genre? This could be the onboarding
   mechanism — "start from a beat, modify it, make it yours."

4. **Collaboration**: Not for launch, but the "share" step naturally leads to "make
   together." Worth keeping in mind architecturally.
