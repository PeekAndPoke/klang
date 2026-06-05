# Sprudel UI Tools — Status Overview

Last updated: 2026-04-13 (renamed Strudel→Sprudel, status refreshed).

Since last revision, these editor tools have been added (see `sprudel/src/jsMain/kotlin/ui/`):
`SprudelDistortEditorTool`, `SprudelDistortShapeEditorTool`, `SprudelDustEditorTool`,
`SprudelPhaserEditorTool`, `SprudelPluckEditorTool`, `SprudelPulzeEditorTool`,
`SprudelSuperPluckEditorTool`, `SprudelSuperSawEditorTool`. Tier tables below still
reflect proposals — cross-check the directory before starting a "new" tool.

## Summary

Every DSL function that accepts parameters now has a dedicated editor tool with a clean tooltip title.
Combined-format functions (`freq:resonance:env`, `threshold:ratio:knee:attack:release`, etc.) have
multi-field editors with SVG visualizations. The compressor editor includes preset buttons.

## Review Status

| #  | Editor Tool                     | Implemented | Reviewed         |
|----|---------------------------------|-------------|------------------|
| 1  | `SprudelGainEditorTool`         | yes         | yes              |
| 2  | `SprudelPanEditorTool`          | yes         | yes              |
| 3  | `SprudelCompressorEditorTool`   | yes         | yes              |
| 4  | `SprudelAdsrEditorTool`         | yes         | yes              |
| 5  | `SprudelDelayEditorTool`        | yes         | yes              |
| 6  | `SprudelReverbEditorTool`       | yes         | yes              |
| 7  | `SprudelFilterEditorTool`       | yes         | yes              |
| 8  | `SprudelFilterAdsrEditorTool`   | yes         | yes              |
| 9  | `SprudelNumericEditorTool`      | yes         | yes              |
| 10 | `SprudelNoteEditorTool`         | yes         | yes              |
| 11 | `SprudelScaleEditorTool`        | yes         | yes              |
| 12 | `SprudelScaleDegreeEditorTool`  | yes         | yes              |
| 13 | `SprudelWaveformEditorTool`     | yes         | yes              |
| 14 | `SprudelSampleEditorTool`       | yes         | needs refinement |
| 15 | `SprudelEuclidEditorTool`       | yes         | **no**           |
| 16 | `SprudelMiniNotationEditorTool` | yes         | yes              |

## Proposed New Tools

### Tier 1 — High Impact (commonly used DSL functions)

| #  | DSL Function           | Proposed Editor                | Format / UI Idea                              |
|----|------------------------|--------------------------------|-----------------------------------------------|
| 17 | `speed()`              | `SprudelSpeedEditorTool`       | numeric slider (0.25–4.0), negative = reverse |
| 18 | `begin()` / `end()`    | `SprudelSampleRangeEditorTool` | dual slider or range bar (0–1)                |
| 19 | `crush()`              | `SprudelCrushEditorTool`       | numeric slider (1–16 bits)                    |
| 20 | `coarse()`             | `SprudelCoarseEditorTool`      | numeric slider (1–64)                         |
| 21 | `velocity()` / `vel()` | `SprudelVelocityEditorTool`    | numeric slider (0–1)                          |
| 22 | `orbit()` / `o()`      | `SprudelOrbitEditorTool`       | channel picker (0–11)                         |
| 23 | `cut()`                | `SprudelCutEditorTool`         | integer picker (cut group index)              |
| 24 | `legato()`             | `SprudelLegatoEditorTool`      | numeric slider (0–2), note duration vis       |

### Tier 2 — Medium Impact (synthesis, pitch, rhythm)

| #  | DSL Function                               | Proposed Editor                  | Format / UI Idea                           |
|----|--------------------------------------------|----------------------------------|--------------------------------------------|
| 25 | `fmh()`                                    | `SprudelFmHarmonicityEditorTool` | numeric slider with harmonic ratio presets |
| 26 | `fmenv()`                                  | `SprudelFmEnvEditorTool`         | numeric slider (0–5000 Hz depth)           |
| 27 | `fmattack()` / `fmdecay()` / `fmsustain()` | reuse `SprudelNumericEditorTool` | individual ADSR component sliders          |
| 28 | `unison()` / `uni()`                       | `SprudelUnisonEditorTool`        | integer picker (1–8 voices)                |
| 29 | `detune()`                                 | `SprudelDetuneEditorTool`        | numeric slider (0–50 cents)                |
| 30 | `transpose()`                              | `SprudelTransposeEditorTool`     | semitone slider (-24 to +24)               |
| 31 | `swing()`                                  | `SprudelSwingEditorTool`         | numeric slider (0–1) with timing diagram   |
| 32 | `distort()` / `dist()`                     | `SprudelDistortEditorTool`       | numeric slider (0–1)                       |
| 33 | `shape()`                                  | `SprudelShapeEditorTool`         | numeric slider (0–1) waveshaper            |

### Tier 3 — Lower Priority (advanced / less frequent)

| #  | DSL Function                           | Proposed Editor            | Format / UI Idea                    |
|----|----------------------------------------|----------------------------|-------------------------------------|
| 34 | `degrade()` / `degradeBy()`            | `SprudelDegradeEditorTool` | probability slider (0–100%)         |
| 35 | `vowel()`                              | `SprudelVowelEditorTool`   | vowel picker (a, e, i, o, u)        |
| 36 | `loop()` / `loopBegin()` / `loopEnd()` | `SprudelLoopEditorTool`    | toggle + range bar                  |
| 37 | `phaser*()` family                     | `SprudelPhaserEditorTool`  | combined center/depth/sweep editor  |
| 38 | `tremolo*()` family                    | `SprudelTremoloEditorTool` | combined depth/rate/shape editor    |
| 39 | `spread()`                             | `SprudelSpreadEditorTool`  | numeric slider (0–1) stereo width   |
| 40 | `freq()`                               | `SprudelFreqEditorTool`    | frequency slider (20–20000 Hz, log) |

## Tool Registry

All tools are registered in `sprudel/src/jsMain/kotlin/ui/SprudelUiTools.kt`.
`@param-tool` KDoc annotations wire DSL function parameters to their editor tools.

## Dynamics

| DSL Function              | Editor                    | Sequence Editor                   | Format                                          |
|---------------------------|---------------------------|-----------------------------------|-------------------------------------------------|
| `gain()`                  | `SprudelGainEditor`       | `SprudelGainSequenceEditor`       | single value                                    |
| `pan()`                   | `SprudelPanEditor`        | `SprudelPanSequenceEditor`        | single value                                    |
| `compressor()` / `comp()` | `SprudelCompressorEditor` | `SprudelCompressorSequenceEditor` | `threshold:ratio:knee:attack:release` + presets |

## Envelope

| DSL Function | Editor              | Sequence Editor             | Format    |
|--------------|---------------------|-----------------------------|-----------|
| `adsr()`     | `SprudelAdsrEditor` | `SprudelAdsrSequenceEditor` | `a:d:s:r` |

## Effects

| DSL Function                      | Editor                       | Sequence Editor                      | Format          |
|-----------------------------------|------------------------------|--------------------------------------|-----------------|
| `delay()`                         | `SprudelDelayEditor`         | `SprudelDelaySequenceEditor`         | `time:feedback` |
| `delaytime()`                     | `SprudelDelayTimeEditor`     | `SprudelDelayTimeSequenceEditor`     | single value    |
| `delayfeedback()` / `delayfb()`   | `SprudelDelayFeedbackEditor` | `SprudelDelayFeedbackSequenceEditor` | single value    |
| `reverb()` / `room()`             | `SprudelReverbEditor`        | `SprudelReverbSequenceEditor`        | single value    |
| `roomsize()` / `rsize()` / `sz()` | `SprudelRoomSizeEditor`      | `SprudelRoomSizeSequenceEditor`      | single value    |

## Low Pass Filter

| DSL Function                            | Editor                     | Sequence Editor                    | Format               |
|-----------------------------------------|----------------------------|------------------------------------|----------------------|
| `lpf()` / `lp()` / `cutoff()` / `ctf()` | `SprudelLpFilterEditor`    | `SprudelLpFilterSequenceEditor`    | `freq:resonance:env` |
| `resonance()` / `res()` / `lpq()`       | `SprudelLpResonanceEditor` | `SprudelLpResonanceSequenceEditor` | single value         |
| `lpenv()` / `lpe()`                     | `SprudelLpEnvEditor`       | `SprudelLpEnvSequenceEditor`       | single value         |
| `lpattack()` / `lpa()`                  | `SprudelLpAttackEditor`    | `SprudelLpAttackSequenceEditor`    | single value         |
| `lpdecay()` / `lpd()`                   | `SprudelLpDecayEditor`     | `SprudelLpDecaySequenceEditor`     | single value         |
| `lpsustain()` / `lps()`                 | `SprudelLpSustainEditor`   | `SprudelLpSustainSequenceEditor`   | single value         |
| `lprelease()` / `lpr()`                 | `SprudelLpReleaseEditor`   | `SprudelLpReleaseSequenceEditor`   | single value         |
| `lpadsr()`                              | `SprudelLpAdsrEditor`      | `SprudelLpAdsrSequenceEditor`      | `a:d:s:r`            |

## High Pass Filter

| DSL Function                        | Editor                     | Sequence Editor                    | Format               |
|-------------------------------------|----------------------------|------------------------------------|----------------------|
| `hpf()` / `hp()` / `hcutoff()`      | `SprudelHpFilterEditor`    | `SprudelHpFilterSequenceEditor`    | `freq:resonance:env` |
| `hresonance()` / `hres()` / `hpq()` | `SprudelHpResonanceEditor` | `SprudelHpResonanceSequenceEditor` | single value         |
| `hpenv()` / `hpe()`                 | `SprudelHpEnvEditor`       | `SprudelHpEnvSequenceEditor`       | single value         |
| `hpattack()` / `hpa()`              | `SprudelHpAttackEditor`    | `SprudelHpAttackSequenceEditor`    | single value         |
| `hpdecay()` / `hpd()`               | `SprudelHpDecayEditor`     | `SprudelHpDecaySequenceEditor`     | single value         |
| `hpsustain()` / `hps()`             | `SprudelHpSustainEditor`   | `SprudelHpSustainSequenceEditor`   | single value         |
| `hprelease()` / `hpr()`             | `SprudelHpReleaseEditor`   | `SprudelHpReleaseSequenceEditor`   | single value         |
| `hpadsr()`                          | `SprudelHpAdsrEditor`      | `SprudelHpAdsrSequenceEditor`      | `a:d:s:r`            |

## Band Pass Filter

| DSL Function                 | Editor                   | Sequence Editor                  | Format       |
|------------------------------|--------------------------|----------------------------------|--------------|
| `bandf()` / `bpf()` / `bp()` | `SprudelBpFilterEditor`  | `SprudelBpFilterSequenceEditor`  | `freq:q:env` |
| `bandq()` / `bpq()`          | `SprudelBpQEditor`       | `SprudelBpQSequenceEditor`       | single value |
| `bpenv()` / `bpe()`          | `SprudelBpEnvEditor`     | `SprudelBpEnvSequenceEditor`     | single value |
| `bpattack()` / `bpa()`       | `SprudelBpAttackEditor`  | `SprudelBpAttackSequenceEditor`  | single value |
| `bpdecay()` / `bpd()`        | `SprudelBpDecayEditor`   | `SprudelBpDecaySequenceEditor`   | single value |
| `bpsustain()` / `bps()`      | `SprudelBpSustainEditor` | `SprudelBpSustainSequenceEditor` | single value |
| `bprelease()` / `bpr()`      | `SprudelBpReleaseEditor` | `SprudelBpReleaseSequenceEditor` | single value |
| `bpadsr()`                   | `SprudelBpAdsrEditor`    | `SprudelBpAdsrSequenceEditor`    | `a:d:s:r`    |

## Notch Filter

| DSL Function                | Editor                                            | Sequence Editor                    | Format       |
|-----------------------------|---------------------------------------------------|------------------------------------|--------------|
| `notchf()`                  | `SprudelNotchFilterEditor`                        | `SprudelNotchFilterSequenceEditor` | `freq:q:env` |
| `nresonance()` / `notchq()` | `SprudelNResonanceEditor` / `SprudelNotchQEditor` | Sequence variants                  | single value |
| `nfenv()` / `nfe()`         | `SprudelNfEnvEditor`                              | `SprudelNfEnvSequenceEditor`       | single value |
| `nfattack()` / `nfa()`      | `SprudelNfAttackEditor`                           | `SprudelNfAttackSequenceEditor`    | single value |
| `nfdecay()` / `nfd()`       | `SprudelNfDecayEditor`                            | `SprudelNfDecaySequenceEditor`     | single value |
| `nfsustain()` / `nfs()`     | `SprudelNfSustainEditor`                          | `SprudelNfSustainSequenceEditor`   | single value |
| `nfrelease()` / `nfr()`     | `SprudelNfReleaseEditor`                          | `SprudelNfReleaseSequenceEditor`   | single value |
| `nfadsr()`                  | `SprudelNfAdsrEditor`                             | `SprudelNfAdsrSequenceEditor`      | `a:d:s:r`    |

## Other

| DSL Function            | Editor                      | Sequence Editor                 | Format                  |
|-------------------------|-----------------------------|---------------------------------|-------------------------|
| `note()`                | `SprudelNoteEditor`         | —                               | note name               |
| `scale()`               | `SprudelScaleEditor`        | —                               | scale name              |
| `scaleDegree()`         | `SprudelScaleDegreeEditor`  | —                               | degree                  |
| `waveform()` / `wave()` | `SprudelWaveformEditor`     | `SprudelWaveformSequenceEditor` | waveform name           |
| `s()` / `sound()`       | `SprudelSampleEditor`       | `SprudelSampleSequenceEditor`   | sample name             |
| `euclid()`              | `SprudelEuclidEditor`       | `SprudelEuclidSequenceEditor`   | `pulses:steps:rotation` |
| MN editor (generic)     | `SprudelMiniNotationEditor` | —                               | mini notation           |

## Editor Implementations

| File                               | Description                                                                                                         |
|------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| `SprudelAdsrEditorTool.kt`         | ADSR envelope editor with SVG curve                                                                                 |
| `SprudelFilterAdsrEditorTool.kt`   | Configurable filter ADSR editor (LP/HP/BP/Notch instances)                                                          |
| `SprudelFilterEditorTool.kt`       | Configurable combined filter editor with frequency response curve (LP/HP/BP/Notch instances)                        |
| `SprudelCompressorEditorTool.kt`   | Compressor editor with transfer function curve + 8 presets                                                          |
| `SprudelDelayEditorTool.kt`        | Delay editor with smooth decay curve                                                                                |
| `SprudelReverbEditorTool.kt`       | Reverb editor                                                                                                       |
| `SprudelNumericEditorTool.kt`      | Configurable numeric editor with drag bar (gain, pan, room size, delay time/feedback, all individual filter params) |
| `SprudelScaleEditorTool.kt`        | Scale picker                                                                                                        |
| `SprudelNoteEditorTool.kt`         | Note picker                                                                                                         |
| `SprudelScaleDegreeEditorTool.kt`  | Scale degree picker                                                                                                 |
| `SprudelWaveformEditorTool.kt`     | Waveform selector                                                                                                   |
| `SprudelSampleEditorTool.kt`       | Sample browser                                                                                                      |
| `SprudelEuclidEditorTool.kt`       | Euclidean rhythm editor                                                                                             |
| `SprudelMiniNotationEditorTool.kt` | Mini notation sequence editor (wraps any atom tool)                                                                 |

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
