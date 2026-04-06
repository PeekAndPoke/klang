# Klang Music Recording — Skill

Use when: recording sprudel patterns to WAV files, offline rendering, batch exporting audio,
working on the CLI recording pipeline, or extending the offline renderer.

Trigger: "record audio", "render to wav", "export wav", "offline render", "klang-music-recording",
"record.sh", "batch render", "RenderWavCommand", "KlangOfflineRenderer"

## Architecture Overview

The recording pipeline renders sprudel patterns to WAV files at full CPU speed (no real-time playback).

```
Sprudel code (String)
  --> KlangScript engine (compile + execute)
  --> KlangPattern (last expression)
  --> KlangOfflineRenderer.render(pattern, cycles, cps, tailSec, onBlock)
      --> VoiceScheduler + KlangAudioRenderer (tight loop, full CPU speed)
      --> onBlock(ShortArray, count) callback per block
  --> WavFileWriter (JVM) streams blocks to disk
  --> 16-bit stereo PCM WAV @ 48kHz
```

## Key Files

| File                   | Location                                              | Purpose                                                                                                                |
|------------------------|-------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|
| `KlangOfflineRenderer` | `klang/src/commonMain/kotlin/KlangOfflineRenderer.kt` | Platform-independent renderer. Drives DSP graph directly, calls `onBlock` per rendered block. Available on JVM and JS. |
| `WavFileWriter`        | `klang/src/jvmMain/kotlin/cli/WavFileWriter.kt`       | Streams 16-bit stereo PCM to a WAV file. Writes placeholder header, patches sizes on close. JVM only.                  |
| `RenderWavCommand`     | `klang/src/jvmMain/kotlin/cli/RenderWavCommand.kt`    | Clikt CLI command `klang:record:wav`. Takes a `compilePattern` lambda to decouple from sprudel.                        |
| `Cli.kt`               | `src/jvmMain/kotlin/Cli.kt`                           | Root CLI entry point. Wires KlangScript engine + sprudel lib into `RenderWavCommand`.                                  |
| `record.sh`            | `console/record.sh`                                   | Bash shortcut: `./console/record.sh --file song.sprudel --cycles 8 -o song.wav`                                        |

## Usage

### Bash script

```bash
# Simple scale
./console/record.sh --code 'note("c3 d3 e3 f3 g3 a3 b3 c4")' --cycles 4 --rpm 30 -o scale.wav

# From a file
./console/record.sh --file my_song.sprudel --cycles 16 --rpm 120 -o song.wav

# Show all options
./console/record.sh --help
```

### Gradle

```bash
./gradlew -q runCli --args="klang:record:wav --file song.sprudel --cycles 4 -o out.wav"
```

### CLI Options

| Option          | Default      | Description                                          |
|-----------------|--------------|------------------------------------------------------|
| `--code, -c`    |              | Sprudel code as inline string                        |
| `--file, -f`    |              | Path to a `.sprudel` file                            |
| `--output, -o`  | `output.wav` | Output WAV file path                                 |
| `--cycles`      | `4`          | Number of cycles to render                           |
| `--rpm`         | `30.0`       | Tempo in RPM (cps = rpm / 60)                        |
| `--sample-rate` | `48000`      | Sample rate in Hz                                    |
| `--block-size`  | `512`        | Rendering block size in frames                       |
| `--tail`        | `2.0`        | Extra seconds after last note for reverb/delay tails |

## How the Offline Renderer Works

1. Creates a standalone DSP graph: `KlangCommLink` + `Cylinders` + `VoiceScheduler` + `KlangAudioRenderer`
2. Queries ALL pattern events upfront for the full cycle range
3. Converts each event to a `ScheduledVoice` with timing relative to time zero
4. Pre-schedules all voices into `VoiceScheduler` (min-heap handles progressive activation)
5. Renders blocks in a tight loop — no delays, no real-time pacing
6. Calls `onBlock(ShortArray, count)` for each block (caller decides what to do with the audio)

This bypasses the real-time infrastructure entirely (`KlangPlaybackController`, `JvmAudioBackend`, `KlangCommLink` IPC).
The `KlangCommLink` instance is created but its feedback is never consumed — harmless.

## Pattern Compilation

The CLI entry point (`Cli.kt`) compiles patterns using a full KlangScript engine:

```kotlin
val engine = klangScript {
    registerLibrary(sprudelLib)
}
engine.execute("""import * from "stdlib"""")
engine.execute("""import * from "sprudel"""")
val result = engine.execute(code + "\n")
return result.toObjectOrNull<KlangPattern>()
```

Any valid KlangScript code works, as long as the last expression evaluates to a `KlangPattern`.

## Adding New CLI Commands

1. Create a new `CliktCommand` in `klang/src/jvmMain/kotlin/cli/`
2. Register it in `src/jvmMain/kotlin/Cli.kt` via `.subcommands(...)`
3. Optionally add a bash shortcut in `console/`

Naming convention: `klang:<category>:<action>` (e.g., `klang:record:wav`)

## Extending for JS

`KlangOfflineRenderer` is in `commonMain` — it works on JS too. The `onBlock` callback can feed audio data to a `Blob`,
`AudioBuffer`, or any JS-side consumer. Only `WavFileWriter` is JVM-specific.

## Limitations (current)

- **Synth voices only** — sample-based sounds (drums etc.) are not yet supported in offline mode
- **No silence detection** — renders for exactly `(cycles / cps) + tail` seconds, even if audio ends earlier
- **No progress callback** — the render loop runs to completion without intermediate status updates
