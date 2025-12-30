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

## Dynamics

- compressor: Reduces the dynamic range (difference between loud and soft parts). Strudel has a global compressor or
  per-orbit.
- post-gain: You have this, but verify velocity aliases to it correctly if needed.

## Time-Based Effects (Spatial)

You have delay and reverb partially implemented.

- leslie: Simulation of a rotating speaker cabinet (Doppler effect + amplitude modulation).
- phaser: Creates sweeping notch filters (uses all-pass filters).

## Sound-fonts

- studel has a variety of soundfonts integrated.
- See https://codeberg.org/uzu/strudel/src/branch/main/packages/soundfonts
- see https://codeberg.org/uzu/strudel/src/branch/main/packages/soundfonts/gm.mjs

## Pitch / Modulation (Beyond Vibrato)

- Vowel Filter: vowel.
- Modulation: phaser, leslie.

- glide (Portamento / pitch bend): Smooth pitch transition between notes.
  - here we get data formed differently -> check TestPatterns::glisandoTest3 -> produces error
  - instead of "n" being the sample index we get this:

```
"value": Object {
  "accelerate": 1,
  "gain": 1,
  "n": Object {
    "0": 1,
    "1": 3,
    "note": "",
  },
  "s": "sine",
  "vib": 8,
  "vibmod": 0.5,
},
```
