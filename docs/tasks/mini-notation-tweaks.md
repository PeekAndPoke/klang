# Tweaks: named per-note modifiers in mini-notation

> Status: **Phases 0-4 DONE, phases 5-6 open.** Captured 2026-08-30, built 2026-08-30/31.
> Supersedes the `{key=value}` attribute block documented in `mini-notation-extensions.md`, which
> Phase 0 removed.
>
> **The feature works end to end:** `note("c3 e3{swell}").tweaks({ swell: x => x.gain(0.5) })`
> plays, on both the script and the Kotlin door. What is left is Phase 5 (the editor's
> unknown-tweak diagnostic, the one thing that keeps a typo from being silently inert) and Phase 6
> (tutorial/popup docs).
>
> Two things learned while building that the plan did not predict:
> - `ObjectValue` could not be a native param type at all: `convertToKotlin` had no identity case,
>   so it threw "Cannot convert ObjectValue to ObjectValue". Fixed in `NativeInterop.kt` with a
>   passthrough. Script lambdas survive only on that path.
> - The DSL constructs used in tests all hand out `part == whole`, so the clipping path needed a
>   stub pattern to be covered at all. See `ClippedEventPattern` in `LangTweaksSpec`.

## Why

Single notes in a pattern often want their own treatment: this one bends, that one swells, the
third is ghosted. Today the only inline door is the `{key=value}` attribute block, which hardcodes
a fixed key table (`v`, `g`, `l`, `pan`, `pg`, `adsr`, `o`, `bank`) in
`MnPatternToSprudelPattern.kt:214`. It cannot reach `detune`, `accelerate`, `oscparam`, or anything
else, every new key needs a parser change, and there is no way to name and reuse a combination.

A tweak is a **named bundle of modifications**, referenced from the note and bound to a transform
later. Naming it makes it reusable and makes the pattern read as intent (`e3{swell}`) rather than as
a pile of numbers (`e3{v=0.8 pan=-0.3 l=2}`).

### The rejected first sketch

The idea started as a global `registerNoteModifier("bend").detune(20)`. That was dropped before
design: a registry is process-global mutable state, so re-evaluating a script re-registers, deleting
the register line leaves a **ghost tweak** that keeps sounding, two songs in one process fight over
the name, and the same pattern string means different things depending on evaluation order. It is
the same class of bug as the per-playback custom-osc leak (`project_fe_be_state_placement`).

The fix that survived: **the binding lives on the pattern value, not in a registry.**

## The model

> **Mini-notation attaches tweak *names* to events. A later `tweaks(...)` call binds names to
> transforms and applies them. Names it does not bind pass through untouched.**

Three consequences fall out of that sentence, and they are the whole design:

| Property         | Because                                                                                  |
|------------------|------------------------------------------------------------------------------------------|
| No global state  | the binding is captured in the pattern node, so it travels with the value                 |
| Composable       | a part can carry `{bend}` markers and let the song decide what `bend` means               |
| Layerable        | unbound names survive, so an inner and an outer palette compose without knowing each other |

## Surface

```klangscript
note("c3 e3{swell} g3{swell bend}")
    .tweaks({
        swell: x => x.attack(0.3).gain(1.1),
        bend:  x => x.detune(20).accelerate(0.5),
    })
```

Pattern-level marking mirrors `.tag()` (`lang_structural_addons.kt:657`) in all four forms:

| Form                              | Meaning                                            |
|-----------------------------------|----------------------------------------------------|
| `SprudelPattern.tweak(name)`      | mark every event of this pattern                   |
| `String.tweak(name)`              | parse as pattern, then mark                        |
| `tweak(name)`                     | returns a `PatternMapperFn` that marks             |
| `PatternMapperFn.tweak(name)`     | chains marking onto a mapper                       |

Applying, on both doors (`feedback_dsl_dual_surface`):

| Door        | Signature                                                        |
|-------------|------------------------------------------------------------------|
| KlangScript | `tweaks(obj)` where `obj` is an object literal of `name: lambda`  |
| Kotlin      | `tweaks(map: Map<String, PatternMapperFn>)` + `vararg Pair` overload |

`PatternMapperFn` is already `(SprudelPattern) -> SprudelPattern` (`lang.kt:28`), so the lambda
users write is exactly the transform type the DSL already uses for `off`, `superimpose`, and friends.

### Placement in the chain matters

A tweak applies **where `tweaks(...)` sits**, so anything after it in the chain still overrides.
Put it last when the tweak should win. This is the direct reason the design does not apply tweaks
at parse time (see below).

## Naming

`tweak` was chosen over `mod` and `trait`.

**`mod` is unusable.** `SprudelPattern.mod(divisor)` already exists as arithmetic modulo, in all
four DSL forms (`lang_arithmetic.kt:369`+). It has zero song usage and could be renamed to `modulo`,
but the word would still be carrying three meanings in one codebase: modulo, *modulation* (the
LFO/envelope sense throughout `audio_be`), and note-modifier. That breaks "one word per concept".

**`trait` was the runner-up.** Rejected on accuracy: a trait is something a note *is*, a tweak is
something *done to* it, and these are transforms. `tweak` also fits the project's vocabulary
(Sprudel, Ignitor, Klangbuch) better than a clinical word.

**Known wrinkle:** `tweak` reads as a verb, so `.tweak("bend")` can sound like it performs the bend
when it only attaches the name. Accepted because `.tag()` has the identical verb-noun shape and
reads fine, and because nearly all marking happens in mini-notation (`e3{bend}`), where there is no
verb at all. The KDoc must lead with "attaches the name; the transform is bound later by `tweaks`".

Verified free across the repo at capture time: no identifier named `tweak`/`tweaks` exists, only
prose in comments.

## Data model

```kotlin
/**
 * Tweak names referencing transforms bound later by `tweaks(...)`. A LIST by design: tweaks apply
 * in the order written and may repeat. Treated as immutable-replace like [tags] / [oscParams]
 * (shared reference in [clone], fresh list on write).
 */
var tweaks: List<String>?
```

on `SprudelVoiceData`, next to `tags` (`SprudelVoiceData.kt:176`), with helpers mirroring
`mergeTags` / `addTag` / `withTag` (`SprudelVoiceData.kt:1508`, `:1522`, `:1529`).

### Why a List and not a Set

Order has to come from somewhere, and the only rule users will guess correctly is **"tweaks apply
left to right, in the order you wrote them"**. A Set forces the alternative rule "order comes from
the map declaration", which nobody guesses. `tags` is a Set precisely because it promises *no*
ordering; tweaks need the opposite promise.

Three consequences to implement deliberately:

- **Duplicates are meaningful.** `e3{bend bend}` applies bend twice. This is the feature that makes
  consumption unnecessary (below).
- **Merging is concatenation, not union.** Direction: inner first, then outer, matching how tags
  already join outward.
- **`addTweak` is not idempotent**, unlike `addTag` which no-ops when the tag is present. Deliberate
  divergence, needs a KDoc line.

### Why the list itself stays immutable

`SprudelVoiceData` is mutable single-owner, but its collections are not: `clone()` copies `tags` by
reference, and the KDoc states the contract as "immutable-replace like `oscParams` (shared reference
in `clone`, fresh set on write)". A genuinely mutable `MutableList` gives one of two bad outcomes:
clone keeps sharing and two voices alias each other's tweaks, or clone deep-copies and pays a list
allocation on **the hot path that `project_mutable_voicedata` made ~17x faster**.

The mutation that is actually needed happens on the field, not in the collection:
`tweaks = current + name`, in place, no new `SprudelVoiceData`. Same shape as `addTag`.

### Why tweaks stay off the wire

`tags` are copied into the bridge `VoiceData` (`audio_bridge/.../VoiceData.kt:194`) because UI
subscribers and visualizations consume them. Tweaks are a pattern-layer concern that never reaches
the synthesis engine, so they are dead weight in the wire format. A new field on `SprudelVoiceData`
does **not** serialize automatically (the `.tag()` commit `24b32c3b` had to touch the bridge
explicitly), so this costs nothing: just do not add it there.

## Application

### Why not at parse time

The parser builds an `AtomicPattern` per note and *could* apply the transform right there. Rejected
for two reasons:

1. **Everything later in the chain would override it.** `note("a{bend}").gain(0.5)` would clobber a
   gain set by `bend`. Tweaks must apply at a point the author controls.
2. It forces the binding map to exist at `note(...)` call time. Parsing is eager: `note(...)`
   (`lang_tonal.kt:255`) runs `parseMiniNotation` into `MnPatternToSprudelPattern.convert` and hands
   back a finished tree, so a chained `.tweaks(...)` has not run yet. Requiring
   `note("a{bend}", map)` would kill pattern-as-value and stop parts from staying binding-free.

### Why not filter + stack

The obvious 4-liner is a trap:

```kotlin
// DO NOT SHIP THIS
fun SprudelPattern.tweak1(name, fn) = stack(fn(filter { tagged }), filter { !tagged })
```

It queries its inner pattern **twice**, once per branch. Chain N of them and cost is 2^N queries of
the base pattern: five tweaks is 32x. Fine for one, unusable for a palette.

### Why not QueryContext

`QueryContext` (`SprudelPattern.kt:102`) is a typed copy-on-write bag and adding a key would be
cheap, but it is the wrong shape twice over. It reintroduces dynamic scoping ("what this pattern
sounds like depends on who queries it"), which is a milder version of the registry problem we
rejected. And the binding would be consulted at the leaf, where there is no pattern to transform,
only one event, forcing tweaks down to voice-data-only operations.

### The node

One pattern node that queries inner **exactly once** and routes:

```kotlin
class TweaksPattern(inner, map) {
    query(from, to, ctx):
        inner.query(from, to, ctx).flatMap { ev ->
            val names = ev.data.tweaks                       // null for most events
            if (names.isNullOrEmpty()) listOf(ev)            // common case, zero cost
            else composedFor(names)(ev.asPattern()).query(ev.whole, ctx)
        }
}
```

- `composedFor(names)` is cached per signature (the joined list, order-significant). A song has a
  handful of distinct signatures.
- Rebuilding one event as a pattern is faithful: events carry both `part` and `whole`
  (`SprudelPatternEvent.kt:20`).
- Voice-data tweaks come out identical to parse-time application; structural ones
  (`x => x.fast(2)`) are well defined as "within this note's slot", which is the right intuition.
- Because each node queries its inner once, **chaining stays linear**, so `.tweaks()` calls can be
  layered freely.

Cost is one small allocation plus a re-query **per marked event only**. Unmarked events are
untouched. If it ever shows in a profile, the pointwise case can collapse to `reinterpretVoice`
later; do not pre-optimize.

## Semantics: three rules

1. **Order is the written order.** `g3{swell bend}` applies swell then bend. Not the map's order.
2. **Unknown names pass through intact**, not dropped. This is what makes layering work: an inner
   `.tweaks({bend})` and an outer palette defining `accent` compose without either knowing about the
   other. Not an error at runtime (`feedback_no_user_param_require`), and it matches the existing
   unknown-attr precedent.
3. **Applying does NOT consume the marker.** Considered and rejected: once duplicates are meaningful
   (List), consuming would remove the author's control over multiplicity, and it would make
   `tweaks()` order-dependent state rather than a pure function of the pattern. The failure it would
   prevent (applying the *same* palette at part and song level) requires writing that palette twice,
   which is visible in the code. If it ever bites, consumption comes back as an **opt-in flag on the
   applier**, not a change to the core semantics.

### The visibility gap this leaves

A misspelled tweak is silently inert, which is the `project_silent_shape_discard` footgun again. The
runtime cannot help: only the analysis layer sees the whole script and can tell that *no* binding
anywhere claims the name. Catch it there, reusing the levenshtein helper from commit `09783f50`
(`suggestNames`, `klangscript/.../runtime/NameSuggestions.kt:25`), reported as an editor diagnostic:
*unknown tweak 'bnd', did you mean 'bend'?*

## Phase 0: remove the attribute block

The `{key=value}` block has **zero usage** in songs, tutorials, or the ref docs. The only references
outside the parser are its own spec and the design doc that introduced it. The visual editor never
touches `attrs`; it only round-trips through `MnRenderer`.

| File                            | Change                                                          |
|---------------------------------|------------------------------------------------------------------|
| `MiniNotationParser.kt:195`     | `L_BRACE` branch: read bare names until `}` instead of `k=v`     |
| `MiniNotationParser.kt:425`     | drop the `C_EQUALS` tokenizer case and the `EQUALS` token type    |
| `MnNode.kt:43`                  | delete `data class Attrs`                                        |
| `MnNode.kt:64`                  | `Mods.attrs: Attrs` becomes `Mods.tweaks: List<String>`          |
| `MnRenderer.kt:93`              | render a space-joined name list, not `k=v` pairs                 |
| `MnPatternToSprudelPattern.kt:214` | delete `applyAttrs` and its hardcoded key table                |
| `MiniNotationAttrsSpec.kt`      | replaced by `MiniNotationTweaksSpec.kt`                          |
| `mini-notation-extensions.md`   | mark Phase 1 superseded, point here                              |

The grammar gets strictly simpler: `EQUALS` disappears from the tokenizer entirely, and the second
parallel way to set gain (which had to be kept semantically in sync with the DSL) goes away.

**What is lost:** one-off inline values. There is no `c4{g=0.5}` any more; every variation must be
named and bound. That is the point, not the cost.

## Work plan

Each phase leaves the tree compiling and testable. Stop before committing so the diff can be
inspected (`feedback_stop_before_commit`).

| # | Phase                | Contents                                                                     |
|---|----------------------|------------------------------------------------------------------------------|
| 0 | Remove attrs ✅ DONE | the table above; parser accepts `{name name}` and stores names on `MnNode`    |
| 1 | Voice data ✅ DONE   | `tweaks: List<String>?` + `mergeTweaks` / `addTweak` / `withTweak`; NOT on wire |
| 2 | Parser wiring ✅ DONE | `MnPatternToSprudelPattern` writes the names onto the atom's voice data        |
| 3 | Marker DSL ✅ DONE   | `tweak(name)` in all four forms, mirroring `tag`                              |
| 4 | Applier ✅ DONE      | `TweaksPattern` + `tweaks(map)` on both doors, incl. `ObjectValue` bridging   |
| 5 | Editor diagnostic    | unknown-tweak warning via `suggestNames`                                      |
| 6 | Docs                 | `sprudel/ref` entries, KDoc with the verb-noun caveat, popup category         |

Phase 4 note: no sprudel DSL function currently takes an `ObjectValue`, so this is the first. KSP
maps `ObjectValue` to script type `Object` (`klangscript-ksp/.../KlangScriptProcessor.kt:72`). The Kotlin door needs its
own `Map` / `vararg Pair` signature so both surfaces stay first-class.

## Guard tests

- **Order**: `g3{a b}` and `g3{b a}` produce different results when both bind conflicting fields.
- **Duplicates**: `e3{bend bend}` applies twice.
- **Unbound passthrough**: an inner `tweaks` that binds one name leaves the other on the event for
  an outer `tweaks` to claim.
- **No consumption**: two `tweaks(...)` calls binding the same name both apply.
- **Chain placement**: `.tweaks(...).gain(x)` lets `gain` win; `.gain(x).tweaks(...)` lets the tweak win.
- **Clone aliasing**: mutating one clone's tweaks must not affect its sibling.
- **Linear cost**: N chained `tweaks()` nodes issue N inner queries, not 2^N (regression guard for
  the filter+stack trap).
- **Round-trip**: `MnRenderer` reproduces `{a b}` exactly; empty braces are stripped like `c4{}` was.

New tests are mutation-checked per `feedback_review_loop`.

## Open items

Ordered by what actually blocks calling this finished.

**1. Phase 5, the unknown-tweak diagnostic.** The one item that changes whether the feature is safe
rather than merely working: a misspelled name is silently inert today. The runtime cannot help,
because it genuinely cannot tell a typo from a name an outer palette will claim later. Only the
analysis layer sees the whole script. Reuse `suggestNames`
(`klangscript/.../runtime/NameSuggestions.kt:25`) from commit `09783f50`.

**2. Nothing has been heard yet.** Every claim in this doc is backed by tests, not by ears, which in
this project is half a verification. Worth a session with a real song: is `{swell}` legible at a
glance in a dense line, and does the *taste* of naming a treatment hold up when you have five of
them?

**3. The visual mini-notation editor has no tweak chip.** `MnSharedPanels.kt` offers chips for `*`,
`/`, `@` and `?` but nothing for the brace block. Not a regression (the attribute block never had one
either), but the braces are a first-class feature now, so their absence is more visible.

**4. Phase 6 docs are half done.** `sprudel/ref/dsl-addons.md` and the KDoc are in, including the
verb-noun caveat on `tweak`. Tutorials and the editor popup category are not.

**5. Group semantics, mechanically settled but not by ear.** `[c4 e4]{swell}` applies the transform
to each note separately, and `note("[c3,e3]{swell}")` is pinned in `LangTweaksSpec`. Whether
per-event is what a musician *wants* there, rather than once to the group as a unit, is a taste call.

**6. Whether tweaks should reach the UI after all.** Kept off the wire (see above). Revisit only with
a concrete consumer, e.g. a visualization that wants to draw bent notes differently.

**7. `tweak` vs `tweaks` ergonomics.** The singular/plural split is type-safe (String vs Object) but
subtle. If it reads badly in a real song, the fallback applier name is `withTweaks`.

## Found while building, unrelated but worth someone's time

`add()` does nothing on an `n()` pattern: `n("0 1 2").add(2)` is inert because `n()` stores into
`soundIndex` while `add()` operates on `value`. Verified against `superimpose` as a second reference
point, so it is not a tweaks artefact. It is the same silent-inert class this feature was designed to
avoid, and it deserves its own look.
