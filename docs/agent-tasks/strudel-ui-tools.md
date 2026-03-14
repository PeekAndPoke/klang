# Strudel UI Tools — Status Overview

Last updated: 2026-03-13

## Summary

Every DSL function that accepts parameters now has a dedicated editor tool with a clean tooltip title.
Combined-format functions (`freq:resonance:env`, `threshold:ratio:knee:attack:release`, etc.) have
multi-field editors with SVG visualizations. The compressor editor includes preset buttons.

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
| `resonance()` / `lpq()`                 | `StrudelLpResonanceEditor` | `StrudelLpResonanceSequenceEditor` | single value         |
| `lpenv()` / `lpe()`                     | `StrudelLpEnvEditor`       | `StrudelLpEnvSequenceEditor`       | single value         |
| `lpattack()` / `lpa()`                  | `StrudelLpAttackEditor`    | `StrudelLpAttackSequenceEditor`    | single value         |
| `lpdecay()` / `lpd()`                   | `StrudelLpDecayEditor`     | `StrudelLpDecaySequenceEditor`     | single value         |
| `lpsustain()` / `lps()`                 | `StrudelLpSustainEditor`   | `StrudelLpSustainSequenceEditor`   | single value         |
| `lprelease()` / `lpr()`                 | `StrudelLpReleaseEditor`   | `StrudelLpReleaseSequenceEditor`   | single value         |
| `lpadsr()`                              | `StrudelLpAdsrEditor`      | `StrudelLpAdsrSequenceEditor`      | `a:d:s:r`            |

## High Pass Filter

| DSL Function                   | Editor                     | Sequence Editor                    | Format               |
|--------------------------------|----------------------------|------------------------------------|----------------------|
| `hpf()` / `hp()` / `hcutoff()` | `StrudelHpFilterEditor`    | `StrudelHpFilterSequenceEditor`    | `freq:resonance:env` |
| `hresonance()` / `hpq()`       | `StrudelHpResonanceEditor` | `StrudelHpResonanceSequenceEditor` | single value         |
| `hpenv()` / `hpe()`            | `StrudelHpEnvEditor`       | `StrudelHpEnvSequenceEditor`       | single value         |
| `hpattack()` / `hpa()`         | `StrudelHpAttackEditor`    | `StrudelHpAttackSequenceEditor`    | single value         |
| `hpdecay()` / `hpd()`          | `StrudelHpDecayEditor`     | `StrudelHpDecaySequenceEditor`     | single value         |
| `hpsustain()` / `hps()`        | `StrudelHpSustainEditor`   | `StrudelHpSustainSequenceEditor`   | single value         |
| `hprelease()` / `hpr()`        | `StrudelHpReleaseEditor`   | `StrudelHpReleaseSequenceEditor`   | single value         |
| `hpadsr()`                     | `StrudelHpAdsrEditor`      | `StrudelHpAdsrSequenceEditor`      | `a:d:s:r`            |

## Band Pass Filter

| DSL Function                 | Editor                   | Sequence Editor                  | Format       |
|------------------------------|--------------------------|----------------------------------|--------------|
| `bandf()` / `bpf()` / `bp()` | `StrudelBpFilterEditor`  | `StrudelBpFilterSequenceEditor`  | `freq:q:env` |
| `bandq()` / `bpq()`          | `StrudelBpQEditor`       | `StrudelBpQSequenceEditor`       | single value |
| `bpenv()` / `bpe()`          | `StrudelBpEnvEditor`     | `StrudelBpEnvSequenceEditor`     | single value |
| `bpattack()` / `bpa()`       | `StrudelBpAttackEditor`  | `StrudelBpAttackSequenceEditor`  | single value |
| `bpdecay()` / `bpd()`        | `StrudelBpDecayEditor`   | `StrudelBpDecaySequenceEditor`   | single value |
| `bpsustain()` / `bps()`      | `StrudelBpSustainEditor` | `StrudelBpSustainSequenceEditor` | single value |
| `bprelease()` / `bpr()`      | `StrudelBpReleaseEditor` | `StrudelBpReleaseSequenceEditor` | single value |
| `bpadsr()`                   | `StrudelBpAdsrEditor`    | `StrudelBpAdsrSequenceEditor`    | `a:d:s:r`    |

## Notch Filter

| DSL Function                           | Editor                                            | Sequence Editor                    | Format       |
|----------------------------------------|---------------------------------------------------|------------------------------------|--------------|
| `notchf()`                             | `StrudelNotchFilterEditor`                        | `StrudelNotchFilterSequenceEditor` | `freq:q:env` |
| `nresonance()` / `notchq()` / `nres()` | `StrudelNResonanceEditor` / `StrudelNotchQEditor` | Sequence variants                  | single value |
| `nfenv()` / `nfe()`                    | `StrudelNfEnvEditor`                              | `StrudelNfEnvSequenceEditor`       | single value |
| `nfattack()` / `nfa()`                 | `StrudelNfAttackEditor`                           | `StrudelNfAttackSequenceEditor`    | single value |
| `nfdecay()` / `nfd()`                  | `StrudelNfDecayEditor`                            | `StrudelNfDecaySequenceEditor`     | single value |
| `nfsustain()` / `nfs()`                | `StrudelNfSustainEditor`                          | `StrudelNfSustainSequenceEditor`   | single value |
| `nfrelease()` / `nfr()`                | `StrudelNfReleaseEditor`                          | `StrudelNfReleaseSequenceEditor`   | single value |
| `nfadsr()`                             | `StrudelNfAdsrEditor`                             | `StrudelNfAdsrSequenceEditor`      | `a:d:s:r`    |

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
