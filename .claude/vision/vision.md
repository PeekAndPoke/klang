# Klang Vision

*Last updated: 2026-03-20*

## Core Identity

Klang is a **live music engine** -- a system where music is code that runs, reacts, and emits events, rather than
audio that plays back.

Music in Klang is never recorded into a file. It is always generated live from Sprudel patterns (a pattern language
descended from Strudel/TidalCycles). This is not a limitation -- it is the foundational design decision that makes
everything else possible.

## The Two-Way Reactive Binding

This is what makes Klang genuinely novel:

**Outside to Music (Inbound Hooks):**
External events change the music in real time. Game state, user interaction, sensor data, API responses, time of day,
heart rate -- anything that can emit an event can shape the musical output. The music is alive and responsive to its
environment.

**Music to Outside (Outbound Signals / PlaybackSignals):**
Musical events trigger application behavior. Beats, notes, pattern changes, phrase boundaries -- these become events
that drive visual effects, game mechanics, lighting, narrative beats, or any other application logic. The real-time
code highlighting in the web frontend is the first incarnation of this.

## What Klang Is NOT

- **Not a DAW.** There is no timeline, no arrangement view, no bounce-to-file. Music is runtime, not recording.
- **Not a learning platform.** Learning happens because the machinery is visible, not because there are lessons.
- **Not a jukebox.** The music doesn't "play" -- it runs. The distinction matters.

## Competitive Positioning

Klang occupies a unique intersection:

| Capability                         | Wwise/FMOD | Reactional | Strudel/Sonic Pi | Klang         |
|------------------------------------|------------|------------|------------------|---------------|
| Embeddable in apps/games           | Yes        | Yes        | No               | Yes (KMP)     |
| Generative (no pre-recorded audio) | No         | Yes        | Yes              | Yes           |
| Code-visible (transparent)         | No         | No         | Yes              | Yes           |
| Two-way reactive binding           | Limited    | Yes        | No               | Yes           |
| Pattern language                   | No         | No         | Yes              | Yes (Sprudel) |
| Runs in browser                    | No         | No         | Yes (Strudel)    | Yes           |

The key differentiator: Klang is the only system that combines a transparent, code-first pattern language with
two-way reactive binding AND embeddability.

## The Role of Pedagogic Transparency

Transparency is not a feature category -- it is a design standard applied to everything.

Because music is always live code, and the code is always visible:

- Watching code highlight in sync with music teaches pattern reading
- Modifying a pattern and hearing the change teaches theory
- Hooking events to musical parameters teaches the relationship between structure and sound

The pedagogic value is an emergent property of the engine's transparency, not a separate product.

## Platform Architecture (Conceptual)

- **Sprudel**: The pattern language. Where musical ideas are expressed as code.
- **Klang Engine**: The runtime that evaluates Sprudel patterns and generates audio.
- **Hooks (Inbound)**: The API surface for external events to affect running patterns.
- **Signals (Outbound)**: The API surface for musical events to affect the outside world.
- **Web Frontend**: The development environment, showcase, and community gathering point.

## Naming

- **Klang** = the platform/engine (German for "sound" or "tone")
- **Sprudel** = the pattern language (German for "sparkling," sibling to Strudel)
- The distinction matters: Klang is where music runs; Sprudel is how music is written

## Key Audiences (in priority order for early adoption)

1. **Creative coders and generative artists** -- comfortable with code, want embeddable music
2. **Web developers building interactive products** -- apps, games, experiences that benefit from living music
3. **Game developers** -- long-term high-value audience, but high trust bar and entrenched incumbents
4. **Live performers and experimental musicians** -- passionate community, great evangelists
5. **Curious learners** -- served by the transparency of all of the above, not by separate features
