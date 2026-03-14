# Strudel UI Tools — Status Overview

Last updated: 2026-03-13

## Summary

Every DSL function that accepts parameters now has a dedicated editor tool with a clean tooltip title.
Combined-format functions (`freq:resonance:env`, `threshold:ratio:knee:attack:release`, etc.) have
multi-field editors with SVG visualizations. The compressor editor includes preset buttons.

## Review Status

| # | Editor Tool                     | Implemented | Reviewed |
|---|---------------------------------|-------------|----------|
| 1 | `StrudelGainEditorTool`         | yes         | yes      |
| 2 | `StrudelPanEditorTool`          | yes         | yes      |
| 3 | `StrudelCompressorEditorTool`   | yes         | yes      |
| 4 | `StrudelAdsrEditorTool`         | yes         | yes      |
| 5 | `StrudelDelayEditorTool`        | yes         | yes      |
| 6 | `StrudelReverbEditorTool`       | yes         | yes      |
| 7 | `StrudelFilterEditorTool`       | yes         | yes      |
| 8 | `StrudelFilterAdsrEditorTool`   | yes         | yes      |
| 9 | `StrudelNumericEditorTool`      | yes         | yes      |
| 10 | `StrudelNoteEditorTool`        | yes         | yes      |
| 11 | `StrudelScaleEditorTool`       | yes         | yes      |
| 12 | `StrudelScaleDegreeEditorTool` | yes         | yes      |
| 13 | `StrudelWaveformEditorTool`    | yes         | yes      |
| 14 | `StrudelSampleEditorTool`      | yes         | **no**   |
| 15 | `StrudelEuclidEditorTool`      | yes         | **no**   |
| 16 | `StrudelMiniNotationEditorTool`| yes         | yes      |

## Proposed New Tools

### Tier 1 — High Impact (commonly used DSL functions)

| # | DSL Function                     | Proposed Editor               | Format / UI Idea                        |
|---|----------------------------------|-------------------------------|-----------------------------------------|
| 17 | `speed()`                       | `StrudelSpeedEditorTool`      | numeric slider (0.25–4.0), negative = reverse |
| 18 | `begin()` / `end()`             | `StrudelSampleRangeEditorTool`| dual slider or range bar (0–1)          |
| 19 | `crush()`                       | `StrudelCrushEditorTool`      | numeric slider (1–16 bits)              |
| 20 | `coarse()`                      | `StrudelCoarseEditorTool`     | numeric slider (1–64)                   |
| 21 | `velocity()` / `vel()`          | `StrudelVelocityEditorTool`   | numeric slider (0–1)                    |
| 22 | `orbit()` / `o()`               | `StrudelOrbitEditorTool`      | channel picker (0–11)                   |
| 23 | `cut()`                         | `StrudelCutEditorTool`        | integer picker (cut group index)        |
| 24 | `legato()`                      | `StrudelLegatoEditorTool`     | numeric slider (0–2), note duration vis |

### Tier 2 — Medium Impact (synthesis, pitch, rhythm)

| # | DSL Function                     | Proposed Editor               | Format / UI Idea                        |
|---|----------------------------------|-------------------------------|-----------------------------------------|
| 25 | `fmh()`                         | `StrudelFmHarmonicityEditorTool` | numeric slider with harmonic ratio presets |
| 26 | `fmenv()`                       | `StrudelFmEnvEditorTool`      | numeric slider (0–5000 Hz depth)        |
| 27 | `fmattack()` / `fmdecay()` / `fmsustain()` | reuse `StrudelNumericEditorTool` | individual ADSR component sliders |
| 28 | `unison()` / `uni()`            | `StrudelUnisonEditorTool`     | integer picker (1–8 voices)             |
| 29 | `detune()`                      | `StrudelDetuneEditorTool`     | numeric slider (0–50 cents)             |
| 30 | `transpose()`                   | `StrudelTransposeEditorTool`  | semitone slider (-24 to +24)            |
| 31 | `swing()`                       | `StrudelSwingEditorTool`      | numeric slider (0–1) with timing diagram |
| 32 | `distort()` / `dist()`          | `StrudelDistortEditorTool`    | numeric slider (0–1)                    |
| 33 | `shape()`                       | `StrudelShapeEditorTool`      | numeric slider (0–1) waveshaper         |

### Tier 3 — Lower Priority (advanced / less frequent)

| # | DSL Function                     | Proposed Editor               | Format / UI Idea                        |
|---|----------------------------------|-------------------------------|-----------------------------------------|
| 34 | `degrade()` / `degradeBy()`    | `StrudelDegradeEditorTool`    | probability slider (0–100%)             |
| 35 | `vowel()`                       | `StrudelVowelEditorTool`      | vowel picker (a, e, i, o, u)            |
| 36 | `loop()` / `loopBegin()` / `loopEnd()` | `StrudelLoopEditorTool` | toggle + range bar                      |
| 37 | `phaser*()` family              | `StrudelPhaserEditorTool`     | combined center/depth/sweep editor      |
| 38 | `tremolo*()` family             | `StrudelTremoloEditorTool`    | combined depth/rate/shape editor        |
| 39 | `spread()`                      | `StrudelSpreadEditorTool`     | numeric slider (0–1) stereo width       |
| 40 | `freq()`                        | `StrudelFreqEditorTool`       | frequency slider (20–20000 Hz, log)     |

## Tool Registry

All tools are registered in `strudel/src/jsMain/kotlin/ui/StrudelUiTools.kt`.
`@param-tool` KDoc annotations wire DSL function parameters to their editor tools.

## Dynamics

| DSL Function              | Editor                    | Sequence Editor                   | Format                                          |
|---------------------------|---------------------------|-----------------------------------|-------------------------------------------------|
| `gain()`                  | `StrudelGainEditor`       | `StrudelGainSequenceEditor`       | single value                                    |
| `pan()`                   | `StrudelPanEditor`        | `StrudelPanSequenceEditor`        | single value                                    |
| `compressor()` / `comp()` | `StrudelCompressorEditor` | `StrudelCompressorSequenceEditor` | `threshold:ratio:knee:attack:release` + presets |

## Envelope

| DSL Function | Editor              | Sequence Editor             | Format    |
|--------------|---------------------|-----------------------------|-----------|
| `adsr()`     | `StrudelAdsrEditor` | `StrudelAdsrSequenceEditor` | `a:d:s:r` |

## Effects

| DSL Function                      | Editor                       | Sequence Editor                      | Format          |
|-----------------------------------|------------------------------|--------------------------------------|-----------------|
| `delay()`                         | `StrudelDelayEditor`         | `StrudelDelaySequenceEditor`         | `time:feedback` |
| `delaytime()`                     | `StrudelDelayTimeEditor`     | `StrudelDelayTimeSequenceEditor`     | single value    |
| `delayfeedback()` / `delayfb()`   | `StrudelDelayFeedbackEditor` | `StrudelDelayFeedbackSequenceEditor` | single value    |
| `reverb()` / `room()`             | `StrudelReverbEditor`        | `StrudelReverbSequenceEditor`        | single value    |
| `roomsize()` / `rsize()` / `sz()` | `StrudelRoomSizeEditor`      | `StrudelRoomSizeSequenceEditor`      | single value    |

## Low Pass Filter

| DSL Function                            | Editor                     | Sequence Editor                    | Format               |
|-----------------------------------------|----------------------------|------------------------------------|----------------------|
| `lpf()` / `lp()` / `cutoff()` / `ctf()` | `StrudelLpFilterEditor`    | `StrudelLpFilterSequenceEditor`    | `freq:resonance:env` |
| `resonance()` / `res()` / `lpq()`       | `StrudelLpResonanceEditor` | `StrudelLpResonanceSequenceEditor` | single value         |
| `lpenv()` / `lpe()`                     | `StrudelLpEnvEditor`       | `StrudelLpEnvSequenceEditor`       | single value         |
| `lpattack()` / `lpa()`                  | `StrudelLpAttackEditor`    | `StrudelLpAttackSequenceEditor`    | single value         |
| `lpdecay()` / `lpd()`                   | `StrudelLpDecayEditor`     | `StrudelLpDecaySequenceEditor`     | single value         |
| `lpsustain()` / `lps()`                 | `StrudelLpSustainEditor`   | `StrudelLpSustainSequenceEditor`   | single value         |
| `lprelease()` / `lpr()`                 | `StrudelLpReleaseEditor`   | `StrudelLpReleaseSequenceEditor`   | single value         |
| `lpadsr()`                              | `StrudelLpAdsrEditor`      | `StrudelLpAdsrSequenceEditor`      | `a:d:s:r`            |

## High Pass Filter

| DSL Function                         | Editor                     | Sequence Editor                    | Format               |
|--------------------------------------|----------------------------|------------------------------------|----------------------|
| `hpf()` / `hp()` / `hcutoff()`      | `StrudelHpFilterEditor`    | `StrudelHpFilterSequenceEditor`    | `freq:resonance:env` |
| `hresonance()` / `hres()` / `hpq()` | `StrudelHpResonanceEditor` | `StrudelHpResonanceSequenceEditor` | single value         |
| `hpenv()` / `hpe()`                 | `StrudelHpEnvEditor`       | `StrudelHpEnvSequenceEditor`       | single value         |
| `hpattack()` / `hpa()`              | `StrudelHpAttackEditor`    | `StrudelHpAttackSequenceEditor`    | single value         |
| `hpdecay()` / `hpd()`               | `StrudelHpDecayEditor`     | `StrudelHpDecaySequenceEditor`     | single value         |
| `hpsustain()` / `hps()`             | `StrudelHpSustainEditor`   | `StrudelHpSustainSequenceEditor`   | single value         |
| `hprelease()` / `hpr()`             | `StrudelHpReleaseEditor`   | `StrudelHpReleaseSequenceEditor`   | single value         |
| `hpadsr()`                           | `StrudelHpAdsrEditor`      | `StrudelHpAdsrSequenceEditor`      | `a:d:s:r`            |

## Band Pass Filter

| DSL Function                  | Editor                   | Sequence Editor                  | Format       |
|-------------------------------|--------------------------|----------------------------------|--------------|
| `bandf()` / `bpf()` / `bp()` | `StrudelBpFilterEditor`  | `StrudelBpFilterSequenceEditor`  | `freq:q:env` |
| `bandq()` / `bpq()`          | `StrudelBpQEditor`       | `StrudelBpQSequenceEditor`       | single value |
| `bpenv()` / `bpe()`          | `StrudelBpEnvEditor`     | `StrudelBpEnvSequenceEditor`     | single value |
| `bpattack()` / `bpa()`       | `StrudelBpAttackEditor`  | `StrudelBpAttackSequenceEditor`  | single value |
| `bpdecay()` / `bpd()`        | `StrudelBpDecayEditor`   | `StrudelBpDecaySequenceEditor`   | single value |
| `bpsustain()` / `bps()`      | `StrudelBpSustainEditor` | `StrudelBpSustainSequenceEditor` | single value |
| `bprelease()` / `bpr()`      | `StrudelBpReleaseEditor` | `StrudelBpReleaseSequenceEditor` | single value |
| `bpadsr()`                   | `StrudelBpAdsrEditor`    | `StrudelBpAdsrSequenceEditor`    | `a:d:s:r`    |

## Notch Filter

| DSL Function                | Editor                                            | Sequence Editor                    | Format       |
|-----------------------------|---------------------------------------------------|------------------------------------|--------------|
| `notchf()`                  | `StrudelNotchFilterEditor`                        | `StrudelNotchFilterSequenceEditor` | `freq:q:env` |
| `nresonance()` / `notchq()` | `StrudelNResonanceEditor` / `StrudelNotchQEditor` | Sequence variants                  | single value |
| `nfenv()` / `nfe()`         | `StrudelNfEnvEditor`                              | `StrudelNfEnvSequenceEditor`       | single value |
| `nfattack()` / `nfa()`      | `StrudelNfAttackEditor`                           | `StrudelNfAttackSequenceEditor`    | single value |
| `nfdecay()` / `nfd()`       | `StrudelNfDecayEditor`                            | `StrudelNfDecaySequenceEditor`     | single value |
| `nfsustain()` / `nfs()`     | `StrudelNfSustainEditor`                          | `StrudelNfSustainSequenceEditor`   | single value |
| `nfrelease()` / `nfr()`     | `StrudelNfReleaseEditor`                          | `StrudelNfReleaseSequenceEditor`   | single value |
| `nfadsr()`                  | `StrudelNfAdsrEditor`                             | `StrudelNfAdsrSequenceEditor`      | `a:d:s:r`    |

## Other

| DSL Function            | Editor                      | Sequence Editor                 | Format                  |
|-------------------------|-----------------------------|---------------------------------|-------------------------|
| `note()`                | `StrudelNoteEditor`         | —                               | note name               |
| `scale()`               | `StrudelScaleEditor`        | —                               | scale name              |
| `scaleDegree()`         | `StrudelScaleDegreeEditor`  | —                               | degree                  |
| `waveform()` / `wave()` | `StrudelWaveformEditor`     | `StrudelWaveformSequenceEditor` | waveform name           |
| `s()` / `sound()`       | `StrudelSampleEditor`       | `StrudelSampleSequenceEditor`   | sample name             |
| `euclid()`              | `StrudelEuclidEditor`       | `StrudelEuclidSequenceEditor`   | `pulses:steps:rotation` |
| MN editor (generic)     | `StrudelMiniNotationEditor` | —                               | mini notation           |

## Editor Implementations

| File                               | Description                                                                                                         |
|------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| `StrudelAdsrEditorTool.kt`         | ADSR envelope editor with SVG curve                                                                                 |
| `StrudelFilterAdsrEditorTool.kt`   | Configurable filter ADSR editor (LP/HP/BP/Notch instances)                                                          |
| `StrudelFilterEditorTool.kt`       | Configurable combined filter editor with frequency response curve (LP/HP/BP/Notch instances)                        |
| `StrudelCompressorEditorTool.kt`   | Compressor editor with transfer function curve + 8 presets                                                          |
| `StrudelDelayEditorTool.kt`        | Delay editor with smooth decay curve                                                                                |
| `StrudelReverbEditorTool.kt`       | Reverb editor                                                                                                       |
| `StrudelNumericEditorTool.kt`      | Configurable numeric editor with drag bar (gain, pan, room size, delay time/feedback, all individual filter params) |
| `StrudelScaleEditorTool.kt`        | Scale picker                                                                                                        |
| `StrudelNoteEditorTool.kt`         | Note picker                                                                                                         |
| `StrudelScaleDegreeEditorTool.kt`  | Scale degree picker                                                                                                 |
| `StrudelWaveformEditorTool.kt`     | Waveform selector                                                                                                   |
| `StrudelSampleEditorTool.kt`       | Sample browser                                                                                                      |
| `StrudelEuclidEditorTool.kt`       | Euclidean rhythm editor                                                                                             |
| `StrudelMiniNotationEditorTool.kt` | Mini notation sequence editor (wraps any atom tool)                                                                 |

## Compressor Presets

| Preset            | Configuration        | Use Case                                   |
|-------------------|----------------------|--------------------------------------------|
| Gentle Leveling   | `-15:2:6:0.01:0.2`   | Subtly even out a melody or pad            |
| Punchy Drums      | `-20:4:3:0.03:0.1`   | Let transients through, squeeze the tail   |
| Tight Percussion  | `-18:6:1:0.005:0.05` | Fast attack/release for tight control      |
| Vocal Smoothing   | `-12:3:8:0.01:0.15`  | Soft knee, moderate ratio for vocals       |
| Heavy Squeeze     | `-30:8:2:0.005:0.1`  | Low threshold, high ratio — pumping effect |
| Sidechain Pump    | `-25:10:0:0.001:0.3` | Hard knee, long release for pumping        |
| Brickwall Limiter | `-2:40:0:0.001:0.05` | Prevent any clipping above -2 dB           |
| Transparent Glue  | `-10:2:10:0.02:0.25` | Very soft knee, barely noticeable          |
