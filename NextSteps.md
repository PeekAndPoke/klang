# Immediate Todos

## More

- extract Tones from audio_bridge
  - create own module for that
  - fully implement tonaljs und hook it up to strudel

- change KlangPlayer.play() logic
  - is must be able to play multiple song / sound etc with the same player instance
  - play() will return a Klang-Object
  - this object has a stop() method

# Strudel Engine Next Steps

## Filters

You currently have lpf (Low Pass), hpf (High Pass) and bandf (Band Pass) is partially in the data structure but
implementation is missing/TODO.

- bandf (Band Pass Filter): Passes frequencies within a certain range and rejects others. Often used with resonance (q).
  -> DONE, but needs testing
- notchf (Notch Filter): The opposite of a band pass; it rejects a specific frequency band.
  -> DONE, but needs testing / Also how to invoke it?
- allpassf (All Pass Filter): Passes all frequencies but alters their phase relationship. Used in phasers.
- lp / bp / hp / notch / comb / allpass: Strudel has aliases or variations for these standard filters.
- vowel (Formant Filter): A specific filter that mimics human vowel sounds (a, e, i, o, u).

- see https://strudel.cc/learn/effects/#continuous-changes
  - The ADSR curve (governed by attack, sustain, decay, release)
  - The pitch envelope curve (governed by penv and its associated ADSR)
  - The FM curve (fmenv)
  - The filter envelopes (lpenv, hpenv, bpenv)
  - Tremolo (tremolo)
  - Phaser (phaser)
  - Vibrato (vib)
  - Ducking (duckorbit)

## Time-Based Effects (Spatial)

You have delay and reverb partially implemented.

- legato: Controls the length of the note relative to its duration. Important for smooth transitions (slides) or
  staccato.
- clip: Hard clipping distortion (distinct from tanh soft clipping/saturation).
- leslie: Simulation of a rotating speaker cabinet (Doppler effect + amplitude modulation).
- phaser: Creates sweeping notch filters (uses all-pass filters).

## Bitcrushing / Lo-Fi

You have placeholders for crush and coarse.

- crush (Bit Reduction): Reduces the bit depth of the signal (e.g., from 16-bit to 8-bit or 4-bit), creating digital
  noise.
- coarse (Sample Rate Reduction): Reduces the effective sample rate (e.g., from 48kHz to 2kHz), introducing aliasing
  artifacts.

## Dynamics

- compressor: Reduces the dynamic range (difference between loud and soft parts). Strudel has a global compressor or
  per-orbit.
- gain: You have this, but verify velocity aliases to it correctly if needed.

## Pitch / Modulation (Beyond Vibrato)

You have vib (Vibrato).

- accelerate: Pitch glide over the duration of a note (up or down).
- glide (Portamento): Smooth pitch transition between notes.
- Summary Checklist for Implementation
- Bitcrusher: crush (bits), coarse (sample rate).
- Vowel Filter: vowel.
- Pitch Glides: accelerate, glide.
- Filters: bandf, notchf.
- Modulation: phaser, leslie.

## Sliding notes (pitch bend)

Should be possible now that we have LFO / Vibrato.
