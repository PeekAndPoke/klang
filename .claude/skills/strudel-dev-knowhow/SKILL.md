---
name: strudel-dev-knowhow
description: Use when working on the strudel / strudel-ksp module, implementing strudel DSL functions, writing strudel tests, or needing strudel architecture knowledge.
---

## What This Skill Does

Loads essential context for working on the `strudel` Kotlin/Multiplatform module — a port of the JavaScript Strudel
pattern language for live coding music.

## Context to Read

Before starting any strudel work, read these files in order:

1. **`strudel/CLAUDE.md`** — Architecture, critical patterns (event structure, `_innerJoin`, `fmap`/`squeezeJoin`),
   testing strategy, DSL documentation conventions
2. **`strudel/MEMORY.md`** — Current implementation status, recent work, lessons learned
3. **`strudel/TODOS.MD`** — Feature checklist (scan for pending items if doing feature work)

This skill also covers the **`strudel-ksp`** subproject — a KSP (Kotlin Symbol Processing) processor that extracts DSL
documentation from KDoc blocks on `@StrudelDsl`-annotated functions and properties, and generates the docs/search index
used by `StrudelDocsPage`.

## Testing

```bash
# Preferred: JVM tests (fast)
./gradlew :strudel:jvmTest

# Specific test class — NO quotes around the name
./gradlew :strudel:jvmTest --tests LangBpmSpec

# JS tests only when testing browser-specific code
./gradlew :strudel:jsTest
```

## Notes

- Update `strudel/MEMORY.md` after completing significant work or discovering new lessons.
- Keep `strudel/MEMORY.md` lean — status and lessons only, no duplicating CLAUDE.md content.
