# Strudel — Dispatcher

Kotlin/Multiplatform port of the JavaScript Strudel pattern language for live coding music.
Patterns generate musical events scheduled over cyclic time (1 cycle ≈ 1 measure).

## Key Files

| File                                           | Role                                      |
|------------------------------------------------|-------------------------------------------|
| `StrudelPatternEvent.kt`                       | Event definition (part/whole/isOnset)     |
| `StrudelPattern.kt`                            | Pattern interface + helpers               |
| `StrudelVoiceData.kt` / `StrudelVoiceValue.kt` | Voice data; values inc. `Pattern` variant |
| `BindPattern.kt`                               | Inner join (clipping)                     |
| `TempoModifierPattern.kt`                      | fast/slow (scaling)                       |
| `RepeatCyclesPattern.kt`                       | Cycle repetition (shifting)               |
| `AtomicPattern.kt`                             | Basic event creation                      |
| `StrudelPlayback.kt`                           | Schedules events, filters by `isOnset`    |
| `lang_*.kt`                                    | User-facing DSL API                       |

## Reference Files — Read Only What You Need

| Topic                                                                | File                      |
|----------------------------------------------------------------------|---------------------------|
| Event structure, part/whole, operation categories, common operations | `ref/event-model.md`      |
| Control patterns (`_innerJoin`), `fmap`/`squeezeJoin`, when stuck    | `ref/control-patterns.md` |
| Adding/documenting DSL functions, KDoc format, KSP                   | `ref/dsl-conventions.md`  |
| Addon functions in `lang/addons/` (non-strudel extensions)           | `ref/dsl-addons.md`       |
| Running tests, 12-cycle rule, isOnset, JS compat                     | `ref/testing.md`          |

## Build & Test

```bash
./gradlew :strudel:jvmTest                          # preferred (fast)
./gradlew :strudel:jvmTest --tests LangBpmSpec      # specific class — NO quotes
./gradlew :strudel:jsTest                           # browser-specific only
```
