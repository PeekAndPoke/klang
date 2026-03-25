---
name: Tutorial factory strategy - evolutionary approach
description: Decision to use breadth-first generative approach for tutorial creation with morning review as selection pressure, not top-down curation
type: project
---

**Decision (2026-03-23):** Tutorial factory will use an evolutionary/generative approach rather than top-down curation.

**How it works:**

- Overnight autonomous agents generate tutorials across full matrix: beginner to pro, small to large scope, all topics
- Everything is tagged rigorously (difficulty, scope, entry point, topic cluster, status)
- Morning review by founder = natural selection (keep / archive / kill)
- Repeat over many nights = evolutionary pressure refines what survives
- After 3-5 rounds, shift from exploration to exploitation (double down on what works)

**Why this beats top-down curation:**

- Nobody knows what "good" looks like for Klang tutorials yet -- too new, too unlike other platforms
- Reviewing is faster and more informed than specifying upfront
- Breadth-first discovers unexpected framings (e.g., reactive hooks tutorial might beat music theory tutorial)
- Low cost of failure per tutorial
- Moves human judgment to AFTER generation, making overnight autonomy cleaner

**Non-negotiable guardrails:**

1. Every tutorial must be runnable in the current engine (no referencing non-existent features)
2. Tagging system is load-bearing: difficulty, scope, entry point, topic cluster, review status
3. After 3-5 rounds, consciously shift from explore to exploit mode

**Risk:** Selection pressure is founder's judgment as proxy for real users. Mitigate by asking per tutorial: Who is this
for? What do they try first? Do they feel something in 60 seconds?

**How to apply:** When the tutorial factory is built, ensure the generation pipeline includes honest self-tagging and
runnability validation. Review UI should support batch review with filtering by tags.
