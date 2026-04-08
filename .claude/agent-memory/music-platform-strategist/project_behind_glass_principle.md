---
name: Behind Glass design principle
description: Decided principle for engine metaphor visibility — visible but not required, glass thins with user growth
type: project
---

## "Behind Glass" — Engine Metaphor Visibility Principle

**Decided 2026-04-08**

The Klang Audio Motor engine metaphor (Cylinder, Ignitor, Katalyst, Fusion, etc.) should be **visible at first but "
behind glass"** at the product surface.

**Why:** The metaphor is a core part of the brand identity and personality. Hiding it entirely wastes a differentiator.
But requiring users to understand it gates progress behind jargon. "Behind glass" resolves this: the engine is part of
the aesthetic — you can see it, appreciate it, feel confidence that something real is underneath — but you never need to
touch it to make music.

**How to apply:**

### Glass Thickness by Context

| Context                      | Glass Thickness | Metaphor Role                                      |
|------------------------------|-----------------|----------------------------------------------------|
| Marketing / first impression | Thick glass     | Aesthetic, vibe, "this feels engineered"           |
| Daily use interface          | Medium glass    | Subtle labels, design language, optional discovery |
| "Under the Hood" content     | Thin glass      | Explained, educational, satisfying curiosity       |
| SDK / live coding            | No glass        | Structural, API vocabulary, the actual engine      |

### Key Rules

1. **Never gate progress behind metaphor comprehension.** A user who doesn't know what a Cylinder is must still be able
   to use every feature.
2. **The metaphor is ambient, not mandatory.** It shapes the feel, not the workflow.
3. **The glass thins with curiosity.** Progressive reveal — Day 1 it's wallpaper, Month 6 you're writing `ignite()`
   calls.
4. **Plain-language vocabulary must coexist.** "Channel" alongside "Cylinder", "Effects" alongside "Katalyst". The
   metaphor adds personality but is never the only way to understand something.
5. **Onboarding never requires metaphor knowledge.** First 5 minutes are pure "Sound First!" — the engine theme is
   atmospheric, not instructional.

### UI Implications

- Mixer channels labeled "Channel 1" with subtle "Cyl. 1" secondary indicator
- Effects chains can show "Katalyst" as a badge, not a required label
- Sound design panels can have "Ignitor" as a hoverable/discoverable label
- System status can show engine-themed indicators (RPM for tempo, active Cylinders) as atmospheric HUD
- Visual design carries engine-inspired aesthetics (gauges, meters, industrial warmth) without requiring vocabulary

### Documentation Implications

- Product docs use plain language with metaphor as flavor (section headers, illustrations)
- "Under the Hood" sections explain the metaphor for the curious
- SDK docs use metaphor as primary vocabulary — no glass, full engine access

### Risk to manage

The non-metaphor layer must be fully functional on its own. If removing every engine term breaks comprehension, the
glass has become a wall. The metaphor earns its place by adding personality, not by being required.
