---
name: sprudel-dev-knowhow
description: Use when working on the sprudel / sprudel-ksp module, implementing sprudel DSL functions, writing sprudel tests, or needing sprudel architecture knowledge.
---

## What This Skill Does

Loads context for working on the `sprudel` Kotlin/Multiplatform module — Klang's pattern language
for live coding music. Sprudel is a sibling of the JavaScript [Strudel](https://strudel.cc) pattern
language, sharing the same roots and many of the same ideas but now taking its own direction.
Also covers `sprudel-ksp`, the KSP processor that extracts DSL documentation from KDoc blocks on
`@KlangScript.Function`-annotated items.

## Context to Read

**Always read first:**

1. **`sprudel/CLAUDE.md`** — dispatcher: key files + which ref file to read next
2. **`sprudel/MEMORY.md`**: what is true now (state, rules in force, traps, open threads), short by design; the full dated record is `sprudel/ref/memory-history.md`, read only when you need the history of a decision

**Then read only the ref file(s) relevant to your task:**

| Task                                                              | Read                              |
|-------------------------------------------------------------------|-----------------------------------|
| Understanding events, part/whole, isOnset, operation categories   | `sprudel/ref/event-model.md`      |
| Working with control patterns, `_innerJoin`, `fmap`/`squeezeJoin` | `sprudel/ref/control-patterns.md` |
| Adding or documenting DSL functions in `lang_*.kt`                | `sprudel/ref/dsl-conventions.md`  |
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
- After completing significant work, keep `sprudel/MEMORY.md` short (restructured 2026-09-29, it had grown into a log): update the current-state section your change touched IN PLACE, add ONE line to its History list (date, a few words, the link to the task record), and put the narrative in the task record, which gets archived. Never append a dated essay to MEMORY.md.
- `sprudel/TODOS.MD` has the pending feature checklist.