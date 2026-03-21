# Sprudel — Dispatcher

Klang's pattern language for live coding music. Sprudel is a sibling of the JavaScript
[Strudel](https://strudel.cc) pattern language — sharing the same roots and many of the same ideas,
but now taking its own direction as part of Klang.
Patterns generate musical events scheduled over cyclic time (1 cycle ≈ 1 measure).

## Key Files

| File                                           | Role                                      |
|------------------------------------------------|-------------------------------------------|
| `SprudelPatternEvent.kt`                       | Event definition (part/whole/isOnset)     |
| `SprudelPattern.kt`                            | Pattern interface + helpers               |
| `SprudelVoiceData.kt` / `SprudelVoiceValue.kt` | Voice data; values inc. `Pattern` variant |
| `BindPattern.kt`                               | Inner join (clipping)                     |
| `TempoModifierPattern.kt`                      | fast/slow (scaling)                       |
| `RepeatCyclesPattern.kt`                       | Cycle repetition (shifting)               |
| `AtomicPattern.kt`                             | Basic event creation                      |
| `SprudelPlayback.kt`                           | Schedules events, filters by `isOnset`    |
| `lang_*.kt`                                    | User-facing DSL API                       |

## Reference Files — Read Only What You Need

| Topic                                                                | File                      |
|----------------------------------------------------------------------|---------------------------|
| Event structure, part/whole, operation categories, common operations | `ref/event-model.md`      |
| Control patterns (`_innerJoin`), `fmap`/`squeezeJoin`, when stuck    | `ref/control-patterns.md` |
| Adding/documenting DSL functions, KDoc format, KSP                   | `ref/dsl-conventions.md`  |
| Addon functions in `lang/addons/` (non-strudel extensions)           | `ref/dsl-addons.md`       |
| Running tests, 12-cycle rule, isOnset, JS compat                     | `ref/testing.md`          |
| Building or modifying UI editor tools, registry, @param-tool         | `ref/uitools.md`          |

## Build & Test

```bash
./gradlew :sprudel:jvmTest                          # preferred (fast)
./gradlew :sprudel:jvmTest --tests LangBpmSpec      # specific class — NO quotes
./gradlew :sprudel:jsTest                           # browser-specific only
```
