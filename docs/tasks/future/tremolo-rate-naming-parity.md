# Tremolo: sprudel's `sync` against the Ignitor's `rate`

Status: **future, a naming decision for the maintainer.** Recorded "for the sprudel side" in the phase 3 door-shape
walk (2026-09-23); opened as a file 2026-09-28 when the phase 3 record was archived
(`docs/tasks-archive/2026-09/20260928-builtin-instruments.md`, section 3b, the `tremolo` row).

## What it is

One concept, two spellings:

- the Ignitor door is `tremolo(rate, depth, configure)`, rate first like every Ignitor LFO door (`phaser`,
  `vibrato`), with `shape` on the builder;
- sprudel's door is `tremolo(depth, sync, shape)` (`sprudel/.../lang/lang_effects_modulation.kt`), depth
  first, and it calls the rate `sync`.

`sync` is an LFO rate in Hz, the same meaning and scale as the Ignitor's `rate` (`docs/tasks/sprudel-ui-tools.md`
notes the same; since 2026-09-29 both feed the frequency of the LFO oscillator, `docs/tasks/tremolo-as-composition.md`).

## Why it is open

The rules register's parameter parity rule asks for the same name, meaning and scale on every surface, with any
asymmetry recorded with a reason. The name and the positional order differ, and no reason was recorded; the walk
only noted it.

## The decision

Rename sprudel's `sync` to `rate` (one word per concept; a replaced surface is removed, not deprecated), and whether
the positional order follows too (depth first is sprudel's reinterpretation rule: a bare call reads the pattern's
values as the first parameter). Or keep `sync` and record the reason. Either way the `classic()` slot key
`tremolo.sync` (`Slots.tremolo.sync`, `audio_bridge/.../IgnitorDslClassic.kt`), the field accessor `tremolo.sync`,
the editor tool and the Lexikon move together.
