---
name: Live music engine vision
description: Klang's core vision is music-as-runtime with two-way reactive binding. Music is never recorded, always generated from code. Hooks let external events change music; PlaybackSignals let music change the world.
type: project
---

**Core insight (2026-03-20):** The long-run idea is NOT to record music into mp3s. Music always plays from code. Two-way
binding:

1. **Outside -> Music (Inbound Hooks):** External events (game state, sensors, user input, APIs) trigger changes in the
   running music
2. **Music -> Outside (Outbound Signals):** Musical events (beats, notes, patterns) trigger application behavior.
   PlaybackSignals + real-time code highlighting is the first incarnation.

**What this means strategically:**

- Klang is closer to middleware (like Wwise/FMOD/Reactional) than to a DAW
- But unlike middleware, Klang is transparent and code-first (pattern language, not parameter knobs on a black box)
- The web frontend is simultaneously a product for creative coders AND a showcase/dev environment for the embeddable
  engine
- Kotlin Multiplatform makes embedding genuinely feasible across web, desktop, mobile

**Closest competitor:** Reactional Music -- does note-by-note generation with two-way game binding, but is proprietary
and opaque. Klang's transparency and pattern language are the key differentiators.

**Why:** Founder's explicit vision statement. This is the north star, not a feature request.

**How to apply:** Every strategic recommendation should be evaluated against: does this serve the vision of
music-as-reactive-runtime? Does it make the engine more embeddable, more reactive, more transparent, or better sounding?
