# Klang Project

Enjoy the ride. Errors happen, no worries, we find them, we fix them. 
We write exceptional software. We are an awesome team and we give our very best.
Der Weg ist das Ziel. Sound first!

## Complexity is the enemy

Keep complexity as low as we can; that is what keeps future development smooth and our
understanding of the whole project high. Before introducing build-time magic or anything of
similar weight (cross-module generated sources, processor options, unusual Gradle wiring, clever
indirection), STOP and consult the maintainer. Prefer the plain module boundary, the plain
function, the plain data class. (Maintainer, 2026-09-06.)

## Memory lives in the repo

Record decisions, status and lessons in the module memory files the skills load
(`klangscript/MEMORY.md`, `audio/MEMORY.md`, `sprudel/MEMORY.md`) and in `docs/tasks/`.
Never in the home-directory auto-memory. If something has no module home, ask where it belongs.

## Available Agent

| Agent                       | Trigger                                                                                                       | Description                                                                                                                                                         |
|-----------------------------|---------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `music-platform-strategist` | **Explicit only**: say "music-platform-strategist" or "talk to the strategist" or "platform strategy session" | Strategic product advisor. NOT a coder. Reasons about user value, platform surface, and launch readiness. Has persistent memory. Saves output to `.claude/vision/`. |

## Available Skills

Use `/skill-name` or describe what you need in natural language to invoke a skill.

| Skill                    | Trigger                                                                                        | Description                                                                                               |
|--------------------------|------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| `/skill-builder`         | "create a skill", "build a skill", "audit a skill"                                             | Guide skill creation, optimization, and auditing following Claude Code best practices                     |
| `/sprudel-dev-knowhow`   | "work on sprudel", "implement sprudel feature", "sprudel tests"                                | Load sprudel module architecture, critical rules, current status, and feature checklist                   |
| `/klangscript-knowhow`   | "work on klangscript", "add language feature", "klangscript parser", "klangscript interpreter" | Load klangscript context incrementally (dispatcher + targeted ref files)                                  |
| `/klangaudio-knowhow`    | "work on audio", "audio engine", "voice synthesis", "effects", "sample loading", "orbits"      | Load audio subsystem context (audio_bridge / audio_be / audio_fe / audio_jsworklet)                       |
| `/code-style`            | "apply code style", "check code style", "clean up code style", "follow code conventions"       | Project code style rules (curly braces, formatting, etc.)                                                 |
| `/dsl-design`            | "design a DSL", "add a DSL door/knob", "review a DSL change", "builder", "configure lambda", "DSL immutability", "parameter parity" | Design principles for every Klang DSL: construction-time immutability, builder/configure-lambda door shape, two doors, parity, one word per concept, coerce vs raw, wire types, review checklist |
| `/review-loop`           | "review this change", "code review", "review loop", "mutation check", "apply review findings"  | Review standard: reviews loop until a clean round (fixes get re-reviewed); new tests are mutation-checked |
| `/ultra-libs-knowhow`    | "ultra libs", "ultra.html", "ultra events", "io.peekandpoke.ultra"                             | Source reference for all `io.peekandpoke.ultra.*` modules (html, streams, common, etc.)                   |
| `/kraft-knowhow`         | "kraft", "kraft component", "kraft forms", "kraft routing", "io.peekandpoke.kraft"             | Source reference for the Kraft UI framework (components, VDom, forms, routing, etc.)                      |
| `/klang-music-writing`   | "write music", "compose", "make a beat", "create an instrument", "sound design"                | LLM-ready reference for writing sprudel patterns and designing ignitor instruments                        |
| `/klang-music-recording` | "record audio", "render to wav", "export wav", "offline render", "record.sh"                   | Offline WAV rendering pipeline: CLI commands, KlangOfflineRenderer, WavFileWriter                         |
| `/six-hats`              | "six hats", "thinking hats", "multi-perspective analysis"                                      | Run Six Thinking Hats method: 5 parallel agents (Red/Black/Yellow/Green/Blue) + synthesis                 |
| `/agent-fleet`           | "fan out agents", "launch agents in parallel", "spawn sub-agents", before any multi-agent run  | Model/effort tiers per sub-agent + fan-out safety (coordinator owns Gradle; workers never fan out)        |
