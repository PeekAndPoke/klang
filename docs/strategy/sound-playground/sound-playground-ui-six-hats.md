# Six Hats Analysis: Sound Playground UI Design

**Date:** 2026-03-14
**Rounds:** 3

---

## White Hat — Facts & Context

- Kraft UI framework (Kotlin/JS) provides VDom components, routing, forms — all in-browser
- Existing visual tools: ADSR curve SVG editors, filter frequency response curves, waveform selectors, compressor
  transfer functions — currently CodeMirror popups, need standalone Kraft components
- Audio runs in JS worklet with VoiceData bridge; real-time parameter updates possible
- 5 stages: Oscillators, Filters, ADSR, FM Synthesis, Effects — with ~7 melodies available per stage
- Key constraints: instrument not tutorial, sound in 5 seconds, one-knob progressive disclosure, "design for adults that
  kids also enjoy"

---

## Discussion Summary

### Round 1 — Opening Positions

#### Red Hat — Feelings & Intuition

- Dark background with living color (Ableton Push / Vital synth aesthetic). Sound already playing on arrive.
- Knobs should feel physical — gradients, glows, grabbable. Movement everywhere: pulsing waveforms, breathing curves.
- Code panel should feel like a mysterious window into the engine room, not homework. Dim it, let it glow on changes.
- Stage progression should feel like unlocking/revealing, not advancing through lessons.
- Screenshot-worthy when the whole UI resonates with the user's creation.

#### Black Hat — Risks & Problems

- 5 stages x 7 melodies = 35 states. Users will wonder "am I done?" — navigation confusion risk.
- Sculpt-down inverts learning model — may trigger loss aversion (removing features feels like breaking things).
- SVG visualizers + audio worklet + code panel = three competing render loops. Frame drops make UI feel broken.
- Browser autoplay policies block "sound on arrive" on every major browser — you get silence then a permission modal.
- Mobile: touch targets for knobs must be 48px+, drag conflicts with scroll, autoplay harder.
- Knob UIs are screen-reader hostile; color-coded selectors fail for colorblind users.
- Chrome Music Lab: engaging for 90 seconds then abandoned. Ableton Learning Synths: linear, no replay value.

#### Yellow Hat — Benefits & Value

- Left-to-right signal chain layout mirrors how audio actually flows — teaches synthesis architecture without words.
- Collapsible stage cards = progressive disclosure built into the layout itself.
- Existing SVG curve components (ADSR, filter response) provide tactile richness most web synth playgrounds lack.
- Code panel that highlights the exact line changing as user drags a knob — bridges gesture to code.
- Dual view (visual controls + updating code) is rare and genuinely valuable — this is the product's moat.

#### Green Hat — Creative Ideas

- "Sound Garden" metaphor: stages as garden plots, synthesis concepts as natural phenomena.
- River/landscape metaphor: sound flows left-to-right through painted terrain, parameters reshape the geography.
- Dieter Rams approach: one large circle per stage, radical reduction, no labels until hover.
- UI makes sound on transitions — stage selection itself is musical, navigation is performance.
- Miyazaki touch: tiny animated creatures ride the waveform (sine = caterpillar, saw = hedgehog).

#### Blue Hat — Process & Next Steps

- Desktop-first, single-screen, no scrolling. Fixed viewport with flexbox.
- Three-panel structure: stage nav (left) | control surface (center) | code panel (right, collapsible).
- Blocking decision: do stages accumulate (hear all modifications) or replace (focus on one at a time)?
- Build order: extract SVG editors → shell with routing → single stage (oscillator) → remaining stages → code panel.

### Round 2 — Key Debates

The most productive clash was between Red Hat's "sound on arrive" aspiration and Black Hat's autoplay reality check.
Black pointed out that every major browser blocks `AudioContext.resume()` without a user gesture — making the "already
warm instrument" impossible without a click. Yellow Hat flipped this constraint into an opportunity: the first tap to
unmute audio IS the first interaction. Make that tap land directly on the oscillator control. Sound and touch arrive
together. This resolved the tension elegantly — instead of fighting the browser, the UI embraces the constraint by
making the first click meaningful rather than ceremonial.

Green Hat's landscape metaphor sparked significant debate. Black Hat argued that metaphors delight on first encounter
but frustrate on the tenth — Ableton succeeded with direct manipulation, not decoration. Red Hat agreed that whimsy
needs restraint: "one living visual element per stage, maximum." But Yellow Hat saw the landscape not as decoration but
as navigation — the spatial layout IS the signal chain, and dormant stages feel like "rooms with the lights on, not
doors to unlock." The debate converged on a key reframe from Yellow: stages aren't locked or unlocked — they're **asleep
**. Every card is visible from moment one, just dormant. Tap any card, it wakes up. No gating, no progression gates,
just curiosity-driven exploration with a default that already sounds good.

Blue Hat's pixel-budget concern (5 stages + code panel = ~150px per stage on 1440px) forced a practical resolution. The
three-column layout with thumbnails (left) | expanded active stage (center) | code panel (right) means only one stage
shows its full SVG editor at a time, while others show compact waveform previews. This gives the active stage enough
room for rich interaction while keeping all stages visible and touchable. Green Hat contributed the insight that audio
should always lead visuals — the SVG is confirmation, not discovery — which means occasional visual lag from render-loop
contention feels intentional rather than broken.

### Round 3 — Final Verdicts

| Hat    | Final Verdict                                                                                                                                                                      |
|--------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Red    | Ship three-column layout with pre-loaded patch. Sound on first click. Every stage always visible, always touchable. If it doesn't feel alive in two seconds, nothing else matters. |
| Black  | Solve autoplay gate and ship single-stage MVP before investing in five-stage architecture. The design is rich but unvalidated — complexity is the primary risk.                    |
| Yellow | Instant sound on first tap is the irreplaceable value. Ship that, and the platform earns trust before it asks for effort.                                                          |
| Green  | Ship a single auto-playing horizontal strip with inline code prompt — the simplest path that keeps sound alive at every moment.                                                    |
| Blue   | Three sprints: skeleton (layout + one patch + sound on tap), content (five patches with thumbnails + code snippets), polish (animations + persistence). Five patches, not fifteen. |

---

## Synthesis

The three rounds produced a surprisingly unified vision despite starting from very different positions. The core
consensus: **the playground is a single-screen instrument where all stages are always visible, sound begins on first
tap, and code grows as a byproduct of interaction.**

The most important evolution was the resolution of progressive disclosure. Round 1 saw tension between Red's "unlocking"
model and Black's warning that gating feels patronizing. By Round 2, Yellow introduced "dormant-not-locked" — all stages
visible from the start, most asleep, any touchable at any time. This eliminates both the gating problem (adults don't
feel restricted) and the overwhelm problem (dormant stages are visually quiet). By Round 3, everyone agreed: the signal
chain is always complete, the user chooses where to intervene, and the default preset already sounds good.

The second key convergence was around the autoplay constraint. What initially seemed like a blocker became a design
feature: the first user gesture should land on a control that immediately produces sound. No splash screen, no "click to
begin" — the first click IS the first musical act. This satisfies Red's "alive in 2 seconds" while respecting browser
reality.

The unresolved tension is scope vs. ambition. Black and Blue both push hard for a single-stage MVP (oscillator only)
shipped fast and validated before building the full five-stage architecture. Green and Red want the spatial richness of
the full landscape from day one. The pragmatic path: build the three-column layout shell with routing for all five
stages, but only implement the oscillator stage fully. Ship with placeholder thumbnails for the other four. This proves
the architecture, validates "sound on first tap," and lets the team iterate on one stage before committing to five.

## Recommended Layout

```
┌─────────────────────────────────────────────────────────────────┐
│  [Melody: Greensleeves ▾]              [Preset: Rich Saw ▾]    │
├──────────┬──────────────────────────────┬───────────────────────┤
│          │                              │                       │
│  ┌────┐  │   ╔══════════════════════╗   │  // strudel code      │
│  │ Osc│◄─│   ║                      ║   │  note("c3 e3 g3")     │
│  └────┘  │   ║   Active Stage       ║   │    .sound("sawtooth") │
│  ┌────┐  │   ║   SVG Editor         ║   │    .cutoff(800)       │
│  │Filt│  │   ║   + Knobs            ║   │    .resonance(0.3)    │
│  └────┘  │   ║   + Waveform View    ║   │    .attack(0.01)      │
│  ┌────┐  │   ║                      ║   │    .decay(0.1)        │
│  │ADSR│  │   ╚══════════════════════╝   │    .sustain(0.8)      │
│  └────┘  │                              │    .release(0.3)      │
│  ┌────┐  │                              │    .room(0.4)         │
│  │ FM │  │                              │                       │
│  └────┘  │                              │  [Copy to Editor]     │
│  ┌────┐  │                              │                       │
│  │ FX │  │                              │                       │
│  └────┘  │                              │                       │
├──────────┴──────────────────────────────┴───────────────────────┤
│  ▸ Waveform visualizer (real-time audio output)                 │
└─────────────────────────────────────────────────────────────────┘
```

**Left column:** Stage thumbnails — compact waveform/spectrum previews, always visible, clickable. Active stage
highlighted. Dormant stages dimmed but touchable.

**Center column:** Active stage's full editor — SVG curve editors, knobs, waveform selectors. One stage at a time,
maximum space for rich interaction.

**Right column:** Read-only strudel code panel. Highlights the line corresponding to the parameter being tweaked. "Copy
to Editor" button bridges to the main workspace.

**Bottom strip:** Real-time waveform/spectrum visualizer showing actual audio output. Always running. This is the "
alive" signal.

**Top bar:** Melody selector + preset selector. Minimal. Functional.

## Design Principles

1. **Dark canvas, living color.** Dark background (#1a1a2e or similar). Saturated accent colors per stage (amber for
   Osc, cyan for Filter, magenta for ADSR, etc.). Glowing active elements.

2. **Sound on first tap.** Page loads silent (browser requirement). First click on any stage thumbnail starts audio AND
   expands that stage. No intermediate "enable audio" step.

3. **Dormant, not locked.** All five stages visible from the start. Dormant stages show a mini waveform preview in muted
   colors. Any stage touchable at any time. No progression gates.

4. **Audio leads, visuals confirm.** Parameter changes affect sound immediately. SVG visualizations update to confirm.
   If there's render contention, audio wins.

5. **Code as byproduct.** The strudel code panel updates live but stays quiet — no syntax highlighting fireworks. It's a
   window, not a wall. The "aha" moment is seeing code appear from knob turns.

6. **One knob at a time.** When a stage first expands, show the single most impactful parameter (e.g., filter cutoff).
   Additional parameters revealed by a subtle "more" affordance. Never show all knobs at once on first visit.

## Recommended Actions

1. **Build three-column Kraft layout shell** with routing for 5 stages. Fixed viewport, no scroll, dark theme. Ship with
   oscillator stage only — other thumbnails visible but show "coming soon" state.

2. **Implement oscillator stage end-to-end:** Waveform selector + pitch/gain knob + live waveform visualizer + audio
   hookup via VoiceData. Validate "sound on first tap" across Chrome, Firefox, Safari.

3. **Add read-only code panel** that mirrors the current VoiceData state as strudel syntax. Highlight active parameter
   on knob drag.

4. **Add filter stage** as second priority — filter cutoff sweep is the most dramatic "one knob" demo and validates the
   stage-switching UX.

5. **Defer:** Landscape metaphors, animated creatures, growing terrain, click-to-edit code, mobile layout, persistence,
   sharing. All valuable, none needed for validation.
