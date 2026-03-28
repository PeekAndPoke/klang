# Klang Project

## Available Agent

| Agent                       | Trigger                                                                                                       | Description                                                                                                                                                         |
|-----------------------------|---------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `music-platform-strategist` | **Explicit only**: say "music-platform-strategist" or "talk to the strategist" or "platform strategy session" | Strategic product advisor. NOT a coder. Reasons about user value, platform surface, and launch readiness. Has persistent memory. Saves output to `.claude/vision/`. |

## Available Skills

Use `/skill-name` or describe what you need in natural language to invoke a skill.

| Skill                  | Trigger                                                                                        | Description                                                                               |
|------------------------|------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| `/skill-builder`       | "create a skill", "build a skill", "audit a skill"                                             | Guide skill creation, optimization, and auditing following Claude Code best practices     |
| `/sprudel-dev-knowhow` | "work on sprudel", "implement sprudel feature", "sprudel tests"                                | Load sprudel module architecture, critical rules, current status, and feature checklist   |
| `/klangscript-knowhow` | "work on klangscript", "add language feature", "klangscript parser", "klangscript interpreter" | Load klangscript context incrementally (dispatcher + targeted ref files)                  |
| `/klangblocks-knowhow` | "work on klangblocks", "block editor", "ast to blocks", "round-trip test", "code gen"          | Load klangblocks context incrementally (dispatcher + targeted ref files)                  |
| `/klangaudio-knowhow`  | "work on audio", "audio engine", "voice synthesis", "effects", "sample loading", "orbits"      | Load audio subsystem context (audio_bridge / audio_be / audio_fe / audio_jsworklet)       |
| `/code-style`          | "apply code style", "check code style", "clean up code style", "follow code conventions"       | Project code style rules (curly braces, formatting, etc.)                                 |
| `/ultra-libs-knowhow`  | "ultra libs", "ultra.html", "ultra events", "io.peekandpoke.ultra"                             | Source reference for all `io.peekandpoke.ultra.*` modules (html, streams, common, etc.)   |
| `/kraft-knowhow`       | "kraft", "kraft component", "kraft forms", "kraft routing", "io.peekandpoke.kraft"             | Source reference for the Kraft UI framework (components, VDom, forms, routing, etc.)      |
| `/six-hats`            | "six hats", "thinking hats", "multi-perspective analysis"                                      | Run Six Thinking Hats method: 5 parallel agents (Red/Black/Yellow/Green/Blue) + synthesis |
