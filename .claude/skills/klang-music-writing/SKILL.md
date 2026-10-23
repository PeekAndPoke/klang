# Klang Music Writing — Skill Dispatcher

Use when: writing music patterns, composing beats/melodies, creating instruments, sound design,
or any task involving sprudel patterns or the Ignitor DSL.

Trigger: "write music", "create an instrument", "compose", "make a beat", "klang-music-writing",
"sound design", "pattern", "melody", "chord progression"

## What to load

| Task                                 | Load                                                     |
|--------------------------------------|----------------------------------------------------------|
| Writing patterns / composing music   | `ref/klangscript-basics.md` + `ref/sprudel-reference.md` |
| Designing instruments / sound design | `ref/klangscript-basics.md` + `ref/ignitor-reference.md` |
| Full track with custom instruments   | All three ref files                                      |
| Judging / reviewing rendered audio   | `ref/production-quality-rubric.md`                       |

## Rules

Moved here from the root rules register at the rules retro (2026-10-23), word for word; the levels (stone, rule,
guideline, guardrail) are the ones the root `CLAUDE.md` defines, and the root keeps one pointer row.

| Rule | Level | Since | Detail |
|------|-------|-------|--------|
| Builtin songs are KlangScript inside Kotlin strings: `/` divides, `$` interpolates. | guardrail | 2026-09 | this line |
| Caricature sound model: 2 to 4 real acoustic tells, sparse fill, tuned by ear, a fixed learnable target (no randomised or adaptive parameters). | guideline | 2026-07 | this skill |

## Notes

- The reference files are self-contained — they can also be pasted into any LLM (ChatGPT, Gemini, etc.) as context
- All code examples use KlangScript syntax and are directly runnable
- Every KlangScript file starts with `import * from "stdlib"` and `import * from "sprudel"`
