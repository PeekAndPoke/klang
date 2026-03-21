---
name: sprudel-dev-knowhow
description: Use when working on the sprudel / sprudel-ksp module, implementing sprudel DSL functions, writing sprudel tests, or needing sprudel architecture knowledge.
---

## What This Skill Does

Loads context for working on the `sprudel` Kotlin/Multiplatform module — Klang's pattern language
for live coding music. Sprudel is a sibling of the JavaScript [Strudel](https://strudel.cc) pattern
language, sharing the same roots and many of the same ideas but now taking its own direction.
Also covers `sprudel-ksp`, the KSP processor that extracts DSL documentation from KDoc blocks on
`@SprudelDsl`-annotated items.

## Context to Read

**Always read first:**

1. **`sprudel/CLAUDE.md`** — dispatcher: key files + which ref file to read next
2. **`sprudel/MEMORY.md`** — current status and lessons learned

**Then read only the ref file(s) relevant to your task:**

| Task                                                              | Read                              |
|-------------------------------------------------------------------|-----------------------------------|
| Understanding events, part/whole, isOnset, operation categories   | `sprudel/ref/event-model.md`      |
| Working with control patterns, `_innerJoin`, `fmap`/`squeezeJoin` | `sprudel/ref/control-patterns.md` |
| Adding or documenting DSL functions in `lang_*.kt`                | `sprudel/ref/dsl-conventions.md`  |
| Adding addon functions in `lang/addons/`                          | `sprudel/ref/dsl-addons.md`       |
| Running or writing tests                                          | `sprudel/ref/testing.md`          |
| Building or modifying UI editor tools                             | `sprudel/ref/uitools.md`          |

## Testing

```bash
./gradlew :sprudel:jvmTest                          # preferred (fast)
./gradlew :sprudel:jvmTest --tests LangBpmSpec      # specific class — NO quotes
./gradlew :sprudel:jsTest                           # browser-specific only
```

## Notes

- Do NOT read all ref files upfront — load only what the task requires.
- Update `sprudel/MEMORY.md` after completing significant work.
- `sprudel/TODOS.MD` has the pending feature checklist.