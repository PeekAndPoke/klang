# Decision Log

This file tracks strategic decisions for the Klang platform.
Format: date, decision, reasoning, what was ruled out.

---

## 2026-03-20: Klang is a live music engine, not a DAW (supersedes earlier "production platform" framing)

**Decision:** Klang's core identity is a **live music engine** with two-way reactive binding between code/music and
external systems. Music is always generated from code at runtime, never recorded to files.

**Reasoning:** The founder's long-term vision clarified that the goal was never to produce audio artifacts (MP3s,
WAVs). The goal is music as a running process that can be influenced by and influence its environment. This
positions Klang in a fundamentally different category from DAWs (Ableton, FL Studio) and even from standalone live
coding tools (Strudel, Sonic Pi).

**What this supersedes:**

- The earlier "music production platform with pedagogic transparency" framing was a step in the right direction
  (correctly ruling out "learning platform") but still implied the output is a recording. The real identity is
  deeper: music as runtime, not as artifact.
- "Ableton if it grew up in the live coding world" comparison -- too limiting, implies DAW-shaped output.

**What was ruled out:**

- DAW-like features (timeline, arrangement, bounce-to-file) are explicitly not on the roadmap
- "Learning platform" framing (already ruled out, reinforced here)
- Competing directly with Wwise/FMOD on their turf (asset management for pre-recorded audio)

---

## 2026-03-20: Competitive positioning as reactive music middleware

**Decision:** The competitive frame for Klang is interactive/reactive music middleware (Reactional Music, conceptually
Wwise/FMOD) combined with live coding environments (Strudel, Sonic Pi). Klang is unique in combining all three
properties: embeddable + generative + transparent.

**Reasoning:** Market research shows:

- Wwise/FMOD are embeddable but manage pre-recorded assets, not generative music
- Reactional Music is the closest competitor (note-by-note generation, two-way binding) but is a proprietary black box
- Strudel/Sonic Pi have the right pattern language DNA but aren't designed for embedding
- No existing tool combines transparent code-first patterns + two-way reactive binding + embeddability

**What was ruled out:**

- Comparing to music learning platforms (Yousician, Simply Piano) -- wrong category entirely
- Comparing only to DAWs -- too narrow, misses the embedding story

---

## 2026-03-20: Audience priority for early adoption

**Decision:** Target creative coders and web developers first, not general music learners or game studios.

**Reasoning:**

- Creative coders tolerate rough edges and incomplete onboarding -- they're already comfortable with code
- Web developers have immediate use cases (interactive experiences, installations, visualizations)
- Game studios need high trust, documentation, and stability before they'll adopt middleware -- serve them later
- The general "curious learner" audience is served as a consequence of transparency, not through dedicated features

**What was ruled out:**

- Trying to serve all audiences simultaneously at launch
- Building dedicated learning/tutorial features before the engine is proven with technical audiences

---

## 2026-03-20: Web frontend is product now, engine is product long-term

**Decision:** The web frontend is the primary product surface in the near term. The embeddable engine is the long-term
strategic value. They are the same codebase but serve different purposes at different stages.

**Reasoning:** The web frontend is where:

- The engine gets developed and tested
- The community forms around shared patterns
- The showcase demos live that attract embedding users
- The transparency is most visible

But the durable competitive advantage is the engine that can run inside other software. The web frontend is both a
product and a proof-of-concept for the engine's capabilities.

**What was ruled out:**

- Treating the web frontend as a throwaway demo -- it IS a product, for the creative coder audience
- Shipping an engine/SDK before the web experience proves the concept -- too early, not enough proof points
