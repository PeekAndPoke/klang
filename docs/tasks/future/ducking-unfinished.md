# Ducking: built, wired, never used, never tested end to end

Status: **future / parked 2026-08-31.** Maintainer's call, taking it out of the audio-backend audit's
scope: *"it is not used yet and was never really tested yet, so let us not waste our time right now."*
Removed from audit finding [F14](../../audio-audit/FINDINGS.md#f14) rather than carried as an open
coverage hole.

**No shipped song uses it.** A repo-wide grep of `src/commonMain/kotlin/builtinsongs/` for `duck`
returns nothing, so nothing here is a regression risk — it is unfinished work, not broken work.

## What exists today

Sidechain ducking: one orbit's output pulls down another's, the kick-and-bass pump.

```klangscript
note("c3 e3 g3").duck(orbit = 1, depth = 0.8)   // duck when orbit 1 plays
```

The chain is complete from DSL to DSP:

| layer | file | tested? |
|-------|------|---------|
| DSL — `duck()` / `duck(depth = ...)` / `duck(attack = ...)` | `sprudel/lang_dynamics.kt:1778+` | ✅ `LangDuckingSpec` |
| wire | `VoiceData.ducking` → `Voice.Ducking(cylinderId, attackSeconds, depth)` | — |
| **per-voice → per-orbit join** | `Cylinder.kt:198-203` configures the cylinder's `KatalystDuckingEffect` from whichever voice owns it | ❌ **nothing** |
| **cross-orbit sidechain resolution** | `Cylinders.kt:87-89`, step 2 of `processAndMix`, after every cylinder has rendered | ❌ **nothing** |
| bus effect | `KatalystDuckingEffect` | ✅ `KatalystDuckingEffectSpec` |
| DSP | `effects/Ducking.kt` | ✅ `DuckingSpec` |

So the two ends are covered and **the join in the middle is not**. That is the shape that produced
[F18](../../audio-audit/FINDINGS.md#f18) in the cut/choke feature: both ends looked fine, and the
parameter never arrived.

## The four things to settle before it ships

### 1. `attackSeconds` does not name an attack

From `Ducking.kt`'s own KDoc: *"the duck-down is instantaneous; this parameter only controls the
return smoothing. Named 'attack' for strudel compatibility."*

So `duck(attack)` (then `duckattack`) sets the **release**. Worse, `Compressor.kt` in the same directory also has an
`attackSeconds`, and there it is a genuine attack — though a one-pole τ rather than a rise time, which
is audit finding [F17](../../audio-audit/FINDINGS.md#f17)(a). **Two parameters, same name, same
directory, two different meanings, neither matching what a DAW would print.** This is exactly the
parameter-parity rule the project already holds itself to, and the cheapest moment to fix it is
before anyone writes a song against the name.

### 2. The duck-down is a step

Instantaneous attack means the gain drops discontinuously the moment the sidechain crosses. At
`duck(depth = 0.8)` that is a large step, and a step in a gain multiplier is a click by construction.
Whether it is audible depends on programme material and depth, but it should be *decided* rather than
discovered.

### 3. It is a per-voice parameter driving a per-orbit effect

`Voice.Ducking` rides on each voice, but the effect lives on the cylinder. `Cylinder.kt:198-203`
configures the orbit from whichever voice currently owns it, so with two differently-ducked voices in
one orbit, the last one to take ownership wins. Related to the standing "mark per-orbit vs per-voice
on every effect" documentation item.

### 4. Its state survives across notes, conditionally

`Cylinder.kt:145-146` is explicit: compressor and ducking instances are reused, so their envelope
followers survive across notes **as long as consecutive owners keep the effect** — a takeover by a
voice with no ducking clears it. That is a deliberate design, and it is subtle enough that it needs a
test before anyone relies on the pump staying continuous across a phrase.

## When to pick this up

Not now. When someone actually wants a pumping bass, and at that point:

1. Settle question 1 first — it is a rename, and renames get expensive once songs use the name.
2. Write the missing join tests (`Cylinder.kt:198-203`, `Cylinders.kt:87-89`) *before* tuning by ear,
   because the F18 lesson is that a silently-unplumbed parameter is invisible to inspection.
3. Then tune depth and release by ear on real material, which is the only way to answer question 2.
