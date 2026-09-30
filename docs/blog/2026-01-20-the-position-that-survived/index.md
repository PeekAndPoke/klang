---
title: "The Position That Survived"
subtitle: "How a source location rides from the parser through the pattern engine to a glowing rectangle in the editor"
date: 2026-01-20
slug: the-position-that-survived
tags: [klangscript, sprudel, editor, live-coding, source-locations, klang]
summary: >
  Live coding needs the editor to show what is playing right now, which means
  every runtime event must know which characters of source text it came from,
  across an interpreter, a mini-notation parser, a pattern engine, and the
  scheduler that turns events into voices. Klang's answer: locations ride the
  values themselves.
  Even a constant defined as "let feel = 1.0" lights up when it plays.
authors: [peekandpoke, claude]
hero: spans.png
status: draft
---

# The Position That Survived

*How a source location rides from the parser through the pattern engine to a
glowing rectangle in the editor.*

## 1. The problem: the editor must know what is playing

Live coding has one UI feature that separates "text editor next to a synth"
from an *instrument*: while the music runs, the code lights up. The atom
that just triggered flashes; you see your pattern breathe. For that to work,
every runtime event (thousands per minute) must know **which characters of
the source text it came from.**

That is a provenance problem, and it is harder than it looks, because the
distance between a character and a sound is long: a string literal is parsed
by a *second* parser (mini-notation), the result is transformed by pattern
combinators, evaluated into events, and scheduled into voices for an audio
worklet. Meanwhile the editor has to light the exact columns of the exact
line as that sound starts, so the span has to ride along with the event all
the way to the scheduler. Lose the thread at any step and the light shows
nothing, or worse, the wrong thing.

And there is a subtler version of the problem. Consider:

```javascript
let feel = 1.0

note("c3 [e3 g3]").gain(feel)
```

When those notes play, klang highlights the atoms `c3`, `e3`, `g3`, *and
the literal `1.0` two lines up.* The value participated, so its birthplace
lights up. That is the feature this post explains.

![What lights up](spans.png)

*Fig. 1: illustration of the editor's highlight behavior: the atoms flash on their beats, and the `feel` literal glows whenever a note that used it plays. The location traveled with the value.*

## 2. Prior art

**Compilers** solved provenance long ago (DWARF debug info, line tables,
JavaScript source maps), but those map *code to code* for a debugger
stepping through it. They answer "what line is this instruction from," not
"light up the third atom inside a string literal, 200 times a minute,
without touching the DOM per event."

**Live-coding systems** are the real ancestors. Strudel, the JavaScript
pattern language klang [began life borrowing](../2026-03-21-growing-our-own-brain/index.md),
highlights active mini-notation atoms in its REPL ([strudel.cc](https://strudel.cc)), and TidalCycles
editors do related tricks. The standard mechanism: the mini-notation parser
records each atom's offsets, events carry them, the editor draws. It works,
and it defines the baseline.

The baseline has a boundary, though: it lights up *pattern syntax*. A value that arrives through a variable or a function argument has usually lost its ancestry by the time it reaches an event; highlighting
stops at the quotation marks.

## 3. The thread: locations ride the values

Klang's design decision, made early (2026-01-20, when source-location
tracking landed): **provenance is a property of values, not of syntax.**
The `1.0` literal itself joined the highlights a little later: its span entered
the event's chain on 2026-01-21, and the editor drew the chain from 2026-01-23.
The figure below follows one span forward, from the parser to the highlight in
the editor; the way back, from an editor position to a node, is section 5.

<figure class="klang-figure">
  <iframe src="span-trace.html" title="Where a source span travels: scrub the playhead, point at a span, an event or a step" loading="lazy"></iframe>
  <figcaption>Drag the playhead or press play (silent), then point at a span in the code, an event in the lanes or a step on the right: whatever is linked to it lights up in the other views. A click pins it. Switch the argument to feel * 2 to see the thread break.</figcaption>
</figure>

Step by step:

1. **The parser stamps every AST node** with its span. Standard.
2. **The interpreter stamps every value.** `NumberValue` and `StringValue`
   carry an optional `location`, the span of the literal they were born
   from. The detail that makes this safe: **equality deliberately ignores
   the location.** Two `1.0`s from different lines are still equal;
   provenance is a passenger, not a participant in semantics. A value
   assigned to `feel`, passed through a call, returned from a helper:
   the location just rides along, because it is *in* the value.
   (One honest boundary: arithmetic severs the thread. `feel * 2` is a
   *new* value with no birthplace. The rule covers pass-through, not
   computation.)
3. **The registration layer forwards call-site context.** Every
   registered DSL function can receive a `CallInfo`:
   the call's own span, the receiver's span, and **per-parameter spans
   read straight off the argument values**. When `.gain(feel)` executes,
   the engine knows the argument's birthplace is `1.0` on line 1. No
   special-casing of variables anywhere: the value knew. (In January the
   registration was a loop over the DSL registry in `KlangScriptStrudelLib.kt`;
   the KSP processor that [generates it today](../2026-08-12-one-annotation-six-artifacts/index.md)
   arrived in March.)
4. **The mini-notation parser composes spans.** `"c3 [e3 g3]"` is a string,
   but the `StringValue` knows where the string sits in the source, and
   the mini-notation parser knows each atom's offset *inside* the string.
   Literal-span + in-string-offset = an exact span per atom. An
   integration test pins this to the column: `sound("bd hh sd oh")` must
   yield four events whose spans point at columns 8, 11, 14, 17.
5. **Events carry a *chain*, not a single span.** A pattern event's
   `sourceLocations` is a `SourceLocationChain`; transformations
   **prepend and append, and do not overwrite.** An atom wrapped in `struct()`
   inside a `superimpose()` keeps every ancestor. The first transformation
   that adds to the chain is the control: `gain(feel)` puts the span of the
   `1.0` literal in front of the atom's own, so on 23 January an event's chain
   held the control literal and the atom, and the editor drew the last five
   entries. The string literal and the call site were the intent written in the
   chain's KDoc; nothing put them in yet. (Today the editor draws the chain
   innermost first, deduped, filtered to the current file, and capped.)
   Transformations add context; they are not allowed to orphan an event.
6. **Voice events carry their spans to the editor, but not over the
   wire.** `sourceLocations` is `@Transient`, so the audio worklet never
   sees a span. When the playback turns events into voices, it also fires an
   in-page callback (`onVoiceScheduled`) with each event's times and chain,
   and the editor draws the highlight 25 ms ahead of the sound.

## 4. The last meter: drawing without paying

The display end has its own engineering story, because "highlight thousands
of events" is the kind of feature that falls over under load. The
first implementation was one DOM `<mark>` per event with a CSS `@keyframes`
pulse animating `border-color`: paint-bound properties that forced a
re-rasterization of every active mark, every frame. Weak GPUs choked. (No before/after frame numbers were recorded in the heat of that rewrite, a rare unmeasured claim in this series; the qualitative story below is from the component's own documentation.)

The current `CodeMirrorHighlightBuffer`, rewritten in June (months after
the story above), is a single transparent WebGL
canvas over the editor: each highlight is a pooled quad, one ticker handles
scheduling and fade, only `x/y/alpha` change per frame, and the ticker
stops when nothing plays, so an idle editor costs **zero**
`requestAnimationFrame`. The CodeMirror state itself is not touched. The
same discipline that rules the audio thread (no per-event allocation, no
per-frame paint) turned out to be what the *visual* thread needed too.

## 5. The same thread, backwards

Provenance also runs in reverse, a later step: `AstIndex`, added in March,
maps an editor position to the
AST node at that position, the infrastructure behind hover documentation,
context menus, and the visual parameter tools. Click on `body(0.7, "wood")` and
the editor knows which call you are in and which argument you are touching;
the [same generated metadata](../2026-08-12-one-annotation-six-artifacts/index.md)
that registered the function supplies the docs and the editing widget.
Forward, positions explain the music; backward, they explain the code.

## 6. Lessons

1. **Attach provenance to values, not syntax.** Everything else follows:
   variables, arguments, helper returns, with no case analysis and no "highlight
   only works inside pattern strings." The one rule that keeps it honest:
   location must not affect equality or behavior. It is a passenger.
2. **Chains beat single spans.** Transformations are additive; the moment
   one is allowed to *replace* a location, some pipeline five steps later
   is pointing at the wrong code.
3. **Compose parsers' coordinate systems explicitly.** The mini-notation
   parser does not know about files; it knows offsets in a string. The
   string knows where it lives. Keep both, add them, and pin it with a test
   that asserts actual line and column numbers.
4. **The display path is a hot path.** A provenance system that works but
   renders through per-event DOM mutation just moves the failure from
   "wrong highlight" to "dropped frames." The glow has a frame budget too.

The feature reads as a gimmick until the first time you watch a stranger's
song play with the code on screen, the music explaining itself, atom by
atom, constant by constant. Then it reads as the whole point.
