---
name: strudel-dev-knowhow
description: Use when working on the strudel / strudel-ksp module, implementing strudel DSL functions, writing strudel tests, or needing strudel architecture knowledge.
---

## What This Skill Does

Loads context for working on the `strudel` Kotlin/Multiplatform module — a port of the JavaScript
Strudel pattern language for live coding music. Also covers `strudel-ksp`, the KSP processor that
extracts DSL documentation from KDoc blocks on `@StrudelDsl`-annotated items.

## Context to Read

**Always read first:**

1. **`strudel/CLAUDE.md`** — dispatcher: key files + which ref file to read next
2. **`strudel/MEMORY.md`** — current status and lessons learned

**Then read only the ref file(s) relevant to your task:**

| Task                                                              | Read                              |
|-------------------------------------------------------------------|-----------------------------------|
| Understanding events, part/whole, isOnset, operation categories   | `strudel/ref/event-model.md`      |
| Working with control patterns, `_innerJoin`, `fmap`/`squeezeJoin` | `strudel/ref/control-patterns.md` |
| Adding or documenting DSL functions in `lang_*.kt`                | `strudel/ref/dsl-conventions.md`  |
| Adding addon functions in `lang/addons/`                          | `strudel/ref/dsl-addons.md`       |
| Running or writing tests                                          | `strudel/ref/testing.md`          |
| Building or modifying UI editor tools                             | `strudel/ref/uitools.md`          |

## Testing

```bash
./gradlew :strudel:jvmTest                          # preferred (fast)
./gradlew :strudel:jvmTest --tests LangBpmSpec      # specific class — NO quotes
./gradlew :strudel:jsTest                           # browser-specific only
```

## Notes

- Do NOT read all ref files upfront — load only what the task requires.
- Update `strudel/MEMORY.md` after completing significant work.
- `strudel/TODOS.MD` has the pending feature checklist.
