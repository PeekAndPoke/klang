# Engine tidy-up: the Katalyst leftovers and a backend ready for a Zig port

Status: **V1, queued (maintainer, 2026-10-07).** Step 3 of the engine order in [`_v1-scope.md`](_v1-scope.md), after
the voice lifecycle (`voice-lifecycle-state-machine.md`) and the pitch pipeline (`pitch-pipeline-into-the-tree.md`).
One exception runs first: the crash below.

## Why

The maintainer, 2026-10-07: "I want to get the engine into a nice, clean code state. At some point we want to port
everything to Zig, which means the backend codebase must be as tidy as can be." And: revisiting the engine surfaces
subtle bugs (the 16-bit browser output was one), so each pass is also quality control.

## The audit

[`../audio-audit/2026-10-07-engine-tidy-audit.md`](../audio-audit/2026-10-07-engine-tidy-audit.md), read-only, at
`4481ea25`. Section A: the Katalyst DSL leftovers (7 of 10 named items still open, 13 unnamed code leftovers).
Section B: tidiness with the Zig port in mind (dead code, duplicated laws, names, per-block allocations and closures,
the wire). Section C: `../plans/effect-state-machines.md` verified DONE as written; the flags kept on purpose are
named with their decisions (state machines only where they pay off, maintainer: "Augenmaß"); one clear finding,
`PlaybackEngine`'s end of life spread over five places. Section D: a cleanup order, behaviour-neutral steps first,
each one reviewable commit. Section E: 11 decisions for the maintainer.

## First, a bug

`Ignitor.variants()` with no children reaches `require(children.isNotEmpty())` in `IgnitorDslRuntime.kt` (the
`Variants.pick` helper) on the audio thread at note-on; nothing catches there. The KlangScript door accepts zero
arguments. Per `/code-style` §21 (coerce user input, never `require()` it): an empty `variants()` is silence.
S, with a row on both doors. Do it right after voice lifecycle step 5 lands (the audit item B4.7).

## The order

Audit section D, unchanged, steps 1 to 20. Steps that touch `Voice`, `VoiceScheduler` or `Cylinders` wait for
lifecycle step 5; the named-arguments pass (`code-style-named-args-pass.md`) goes before or after them, never in
parallel. Bit-identity (the 18-song corpus) is the proof for every behaviour-neutral step.

## Decisions for the maintainer

Audit section E, D1 to D11, and the judgement calls C4.1 and C4.2. The ones that change the most:

- **D1** "cylinder" or "orbit" (one word per concept; the wire field `VoiceData.cylinder`).
- **D3** which state-machine shape the next lifecycle copies: inner classes (the effects) or an enum with `when`
  (the voice). The enum maps more directly to Zig.
- **D5** bus knobs typed as full Ignitor expressions (a native backend would need the Ignitor builder to read a
  reverb's `wet`).
- **D7** own the RNG (reproduce `XorWowRandom` in a project class), the precondition for a bit-identical port.

## Decided (maintainer, 2026-10-07)

- **D1, the word:** "cylinder" is the engine's and the user's word. `orbit()` stays only as an alias in sprudel, to
  honour its Strudel origin. Today sprudel has it the other way round (`orbit` is the object, `cylinder` the
  alias), so the rename is: sprudel's canonical door becomes `cylinder` (aliases `orbit`, `o`), and the docs, KDoc,
  tutorials, UI text and the Katalyst vocabulary say cylinder. L; plan it as its own step.
- **D3, the state-machine shape:** "as it fits". A state that carries data only it may see is a class; a state
  without data is a `data object`. Classes are the usual case, because they extend without a rewrite. Recorded in
  `../plans/effect-state-machines.md`. Consequence: the voice's `State` enum becomes a sealed type in a second,
  bit-identical round (`voice-lifecycle-state-machine.md`, step 5b).
- **D7, owning the RNG:** a prerequisite for the Zig port, not needed now. Deferred to the port's preparation.
- **D5, bus knobs typed as Ignitor expressions:** KEPT as they are. The maintainer: "the Zig side will in any case
  need to understand this data model and the contract ... I would not bend our implementation on this side just
  because another backend has things to solve to use the inputs. The duty is on the other side, not here."
  A general rule for the port: the Kotlin engine defines the contract; a second backend adapts to it.

