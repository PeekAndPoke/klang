---
name: Positioning analysis April 2026
description: Four directions evaluated (Sound-as-Code, Educational, Live-coding, Production). Sound-as-Code recommended as primary with live-coding as community layer and education as emergent benefit.
type: project
---

**Decision (2026-04-03):** Under consideration. Four strategic directions analyzed for Klang positioning.

**Recommendation:** "Sound as Code" (procedural audio SDK) as primary identity. Three-layer model:

- Layer 1 (Business): Procedural Audio SDK, native compilation, embeddable runtime
- Layer 2 (Discovery): Web-based interactive playground (the current frontend)
- Layer 3 (Community): Live coding / algorave community and creative coding

**Key reasoning:**

- Sound-as-Code aligns perfectly with the existing architecture (reactive binding, runtime generation, embeddable)
- It has a clear B2B revenue model (SDK licensing) viable for a solo founder
- Deep technical moat (synthesis engine + pattern language + native runtime)
- Strong R42 fit as game audio middleware
- Education is emergent from transparency, not a separate feature set (aligns with founder's stated vision)
- Live-coding community is patient zero for discovery but not a revenue source
- Music production ruled out (contradicts runtime-not-recording vision, impossible competition)

**Biggest open risks:**

1. Native audio path engineering investment (WebAudio -> native is substantial)
2. Game developer credibility gap for a solo founder
3. Whether indie game devs are reachable without game industry connections

**Why:** Full analysis at `.claude/vision/klang-positioning-analysis.md`

**How to apply:** Future strategic recommendations should evaluate against Sound-as-Code as the primary positioning.
Features that make the engine more embeddable, better sounding, or easier to integrate take priority.
