# Soundfont Looping Investigation

Looped soundfont instruments (e.g., gm_accordion, gm_violin, gm_organ) do not loop correctly.
Need to understand the soundfont data structure and how to translate it to the Klang engine.

## Status: TODO

## Problem

Looped soundfont instruments don't sustain/loop as expected. The data flow from soundfont JSON
through to SampleIgnitor needs to be traced and verified end-to-end.

## Investigation Steps

### 1. Understand the soundfont zone data structure

Examine actual soundfont JSON files for loopable instruments at:
`/opt/dev/peekandpoke/peekandpoke.github.io/klang/felixroos/gm/`

Look at:

- `gm_accordion/` — sustained instrument, should loop indefinitely while key is held
- `gm_violin/` — bowed instrument, continuous sustain
- `gm_organ/` — organ, indefinite sustain

For each, understand:

- How `loopStart` / `loopEnd` frame values relate to the actual PCM data
- What `anchor` means in practice (sample start offset? attack point?)
- How `originalPitch`, `keyRangeLow`, `keyRangeHigh` define zone coverage
- What `ahdsr` flag means vs the heuristic loop detection (50ms threshold)
- How multiple zones interact (layering? splitting by key range?)

### 2. Trace the data flow

```
SoundFont JSON zone
  → SoundFont.Zone.getSampleMetadata() [loop detection, ADSR, anchor]
  → SampleMetadata.LoopRange(startSec, endSec)
  → VoiceFactory [converts seconds → frames, creates SampleIgnitor]
  → SampleIgnitor [playhead, loop wrap, interpolation]
```

Verify at each step:

- Are loop points correct after seconds conversion?
- Is the playhead starting at the right position?
- Is `isLooping` being set to true?
- Does SampleIgnitor actually wrap at loop boundaries?
- Is the ADSR envelope allowing sustain (sustain=1.0)?

### 3. Compare with a known working SoundFont player

Compare Klang's zone selection and loop behavior with:

- WebAudioFont (the original source of these soundfont conversions)
- A standard SF2 player (e.g., FluidSynth)

Key questions:

- Does WebAudioFont use `anchor` as a playback start offset?
- How does WebAudioFont handle the `loopStart`/`loopEnd` values?
- Does it apply an ADSR or use raw sample looping?

### 4. Fix issues found

Likely areas:

- Loop point calculation (frames vs seconds, sample rate conversion)
- Anchor interpretation (is it seconds from start? normalized position?)
- ADSR interaction with looping (does the envelope cut the sound before the loop sustains?)
- Zone selection for multi-zone instruments
- `useMetaLoop` conditions in VoiceFactory (when does meta loop activate?)

## Key Files

| File                                        | Role                                                              |
|---------------------------------------------|-------------------------------------------------------------------|
| `audio_fe/.../samples/SoundFont.kt`         | Zone data model, getSampleMetadata(), loop detection              |
| `audio_fe/.../samples/SampleIndexLoader.kt` | Zone selection, Base64 decode, sample construction                |
| `audio_bridge/.../SampleMetadata.kt`        | LoopRange(startSec, endSec), anchor, ADSR                         |
| `audio_be/.../voices/VoiceFactory.kt`       | Seconds→frames, playhead start, isLooping, SampleIgnitor creation |
| `audio_be/.../ignitor/SampleIgnitor.kt`     | Playback engine: loop wrap, interpolation, stopFrame              |
| `audio_bridge/.../VoiceData.kt`             | Voice params: begin, end, loop, speed                             |

## Sample Data Location

`/opt/dev/peekandpoke/peekandpoke.github.io/klang/felixroos/gm/`
