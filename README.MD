[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-success.svg)](https://kotlinlang.org/docs/releases.html)
[![CodeFactor](https://www.codefactor.io/repository/github/peekandpoke/klang/badge?s=a4aca92dba91c2faa10e953058445a301b2191d8)](https://www.codefactor.io/repository/github/peekandpoke/klang)
[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)

# Klang Audio Motör

Ein Klang-Generator | A Sound-Generator

This is the home of the Klang Audio Motör, a Kotlin multiplatform realtime audio generatör.

![Klang Audio Motör](docs/media/klang-motör-02.png)

Currently supported platforms:

- JS / Web
- JVM

Currently supported live coding frameworks:

- Sprudel (a Kotlin pattern language, sibling of strudel.cc — now on its own direction)

# License

Klang Audio Motör is free software, licensed under the **GNU Affero General Public License v3**.
See [LICENSE](LICENSE) for the full terms.

# Credits

Klang stands on the shoulders of many open-source projects and references.
See [CREDITS.MD](CREDITS.MD) for the full list.

# Authors

Copyright (C) 2025-2026 The Klang Audio Motör Authors.
See [AUTHORS.MD](AUTHORS.MD) for the list of copyright holders.

# Pre-Alpha-Demo

There are still some rough edges ... For the best experience use a chrome-browser on a laptop or pc please.

https://klang.finzo.de

# Module Structure

```
                            ┌──────────────────┐
                            │   klang (app)    │  Main application (JS/JVM)
                            └──┬──┬──┬──┬──┬───┘
                               │  │  │  │  │
          ┌────────────────────┘  │  │  │  └────────────────────┐
          │            ┌──────────┘  │  └──────────┐            │
          v            v             v             v            v
   ┌────────────┐ ┌─────────┐ ┌───────────┐ ┌──────────┐ ┌────────────────┐
   │  sprudel   │ │klangui  │ │klangblocks│ │klangsc-ui│ │ klang-notebook │
   │            │ │         │ │           │ │          │ │                │
   │ Pattern    │ │ Shared  │ │ Visual    │ │ Editor   │ │  Interactive   │
   │ language   │ │ UI      │ │ block     │ │ CodeMirr │ │  notebook      │
   └──┬──┬──────┘ └──┬──┬───┘ │ editor    │ └──┬──┬──┬─┘ └───┬──┬──┬──┬───┘
      │  │           │  │     └──┬──┬─────┘    │  │  │       │  │  │  │
      │  │           │  │        │  │          │  │  │       │  │  │  │
      │  │     ┌─────┘  │    ┌───┘  │     ┌────┘  │  │       │  │  │  │
      │  │     │     ┌──┘    │   ┌──┘     │    ┌──┘  │       │  │  │  │
      v  v     v     v       v   v        v    v     v       v  v  v  v
   ┌─────────────────────────────────────────────────────────────────────┐
   │                         klangscript                                 │
   │              Scripting language: parser, interpreter                │
   └─────────────────────────────────────────────────────────────────────┘


   ── Build-time / tooling ──────────────────────────────────────────────────────────────────────

   ┌──────────┐ ┌─────────────────────────┐ ┌──────────────────┐ ┌──────────────────────┐ ┌─────────────────┐
   │ klangjs  │ │ klangscript-annotations │ │ klangscript-ksp  │ │ audio-wire-codec-ksp │ │ audio_benchmark │
   │          │ │                         │ │                  │ │                      │ │                 │
   │ External │ │ @KlangScript binding    │ │ KSP: stdlib reg. │ │ KSP: worklet wire    │ │ Engine perf     │
   │ JS decls │ │ annotations             │ │ + completions    │ │ codec generation     │ │ harness (RTF)   │
   │(CodeMirr)│ └─────────────────────────┘ └──────────────────┘ └──────────────────────┘ └─────────────────┘
   └──────────┘


                    ┌──────────────────────┐
                    │    audio_bridge      │  Shared audio data types + wire contracts
                    └──┬───────────────┬───┘
                       │               │
              ┌────────┘               └────────┐
              v                                 v
       ┌────────────┐                    ┌────────────┐
       │  audio_be  │                    │  audio_fe  │
       │            │                    │            │
       │  Backend   │                    │  Frontend  │
       │  render    │                    │  playback  │
       └────────────┘                    └──┬─────────┘
                                            │
                                            v
                                     ┌─────────────────┐
                                     │ audio_jsworklet │
                                     │                 │
                                     │ WebAudio        │
                                     │ worklet         │
                                     └─────────────────┘

   ┌──────────┐          ┌──────────┐
   │  common  │          │  tones   │
   │          │          │          │
   │ Shared   │          │ Music    │
   │ utils    │          │ theory   │
   └──────────┘          └──────────┘
       ^                      ^
       │                      │
   (used by most modules)  (audio_fe, klangui, sprudel)
```

## Modules

| Module                    | Platform  | Description                                                                            |
|---------------------------|-----------|----------------------------------------------------------------------------------------|
| `klang`                   | JS/JVM    | Main application — web UI, pages, routing, audio player integration                    |
| `sprudel`                 | Common/JS | Pattern language — Kotlin sibling of strudel.cc for live coding music (own direction)  |
| `klangscript`             | Common    | Scripting language — hand-rolled parser, tree-walking interpreter, type system         |
| `klangscript-annotations` | Common    | KlangScript binding annotations (`@Object`, `@Method`, `@TypeExtensions`, `@Function`) |
| `klangscript-ksp`         | JVM       | KSP processor — generates stdlib registration + completion data from the annotations   |
| `klangscript-ui`          | JS        | CodeMirror editor with code completion, hover docs, tool badges                        |
| `klangblocks`             | Common/JS | Visual block editor — AST-to-blocks, code generation, round-trip editing               |
| `klangui`                 | JS        | Shared UI components — theme, hover popups, tool registry, SVG helpers                 |
| `klangjs`                 | JS        | External JS declarations (CodeMirror bindings) — no project dependencies               |
| `klang-notebook`          | Common/JS | Interactive notebook support — cell execution, reactive evaluation                     |
| `audio_bridge`            | Common    | Shared audio data types + wire contracts (`@WireFormat` voice/engine/command types)    |
| `audio_be`                | Common    | Audio backend — oscillators, filters, effects, voice rendering                         |
| `audio_fe`                | Common/JS | Audio frontend — playback scheduling, sample loading, engine facade                    |
| `audio_jsworklet`         | JS        | WebAudio worklet — runs audio rendering in a dedicated thread                          |
| `audio_benchmark`         | Common    | Audio engine benchmarks — real-time-factor / voice-count performance harness           |
| `audio-wire-codec-ksp`    | JVM       | KSP processor — generates the worklet wire codec from `@WireFormat` types              |
| `common`                  | Common    | Shared utilities — caching, math helpers, platform abstractions                        |
| `tones`                   | Common    | Music theory — note names, scales, frequencies, MIDI conversion                        |

# Dev Diary & History

A running, informal log of what happened day to day — the wins, the fights, and the dead ends.
See [DEV-DIARY.MD](DEV-DIARY.MD).

For the bigger picture, [docs/history](docs/history/) has condensed, grouped-by-system
summaries — one file per quarter.
