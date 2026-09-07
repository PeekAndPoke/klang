# Sprudel accessors: fields that only have a compound door

Follow-up to `docs/tasks/sprudel-field-accessors.md`, opened 2026-09-07 after the numeric sweep.

Status 2026-09-07: option 1 below is the decided shape. `adsr` piloted it; the seven compound
effects (`room`, `delay`, `phaser`, `tremolo`, `distort`, `crush`, `coarse`) followed in batch E,
each an object with slot children and the setter as `invoke`, their per-knob doors removed. The
filter envelopes in the table are batch F.

## The gap

An accessor exists per FIELD, and every field with a single-field door has one. Sixteen numeric
fields have no single-field door, only a slot in a compound door, so they have no accessor and a
mapper handed to their compound door is still silently dropped:

| Compound door                | Fields (SprudelVoiceData)                               |
|------------------------------|---------------------------------------------------------|
| `lpadsr(a, d, s, r)`         | `lpattack, lpdecay, lpsustain, lprelease`               |
| `hpadsr(a, d, s, r)`         | `hpattack, hpdecay, hpsustain, hprelease`               |
| `bpadsr(a, d, s, r)`         | `bpattack, bpdecay, bpsustain, bprelease`               |
| `nfadsr(a, d, s, r)`         | `nfattack, nfdecay, nfsustain, nfrelease`, DONE: these four have doors and accessors |

`adsr(a, d, s, r)` was the PILOT for this shape (2026-09-07): its four stages have no doors of
their own any more (the maintainer removed `attack()`, `decay()`, `sustain()`, `release()`), and
`adsr` is an object whose children `adsr.attack` etc. are the slot accessors. The compressor's `ratio, knee, attack, release` slots are the same
shape (only `compressor`, the threshold, has an accessor); listed here for completeness, lower
priority.

## The constraint

The single-field doors for the filter envelope stages EXISTED and were retired on purpose:
`LangDeletedFilterAliasesSpec` (the C6a cleanup) pins `lpattack, lpa, lpdecay, lpd, lpsustain,
lps, lprelease, lpr` and the `hp*`/`bp*` twins, plus `lpenv, hpenv, bpenv`, as names that must
fail dispatch. Bringing them back as accessors would undo that decision. `sprudel/MEMORY.md`'s
feature list still named them as implemented; corrected 2026-09-07.

## Options for a naming convention

1. **Slot accessors on the compound object.** `lpadsr` becomes an object with member accessors:
   `lpadsr.attack`, `lpadsr.decay`, `lpadsr.sustain`, `lpadsr.release`, each a `FieldAccessor`
   for the matching field, and `lpadsr(...)` stays the setter (KSP supports member properties on
   objects, the `Osc.slot.analog` chain is the precedent). Reads: `lpf(500).lpadsr(0.01, 0.3)
   .lpq(lpadsr.attack.mul(20))`. Mapper: `lpadsr(mul(2))` on the first slot, like `lpf(mul(2), 8)`,
   or `lpadsr(attack = mul(2))` by name. No new top-level names, nothing retired comes back, and
   the same shape would serve `hpadsr`, `bpadsr`, `compressor.ratio` and any future compound.
2. **Reinstate the long stage names** (`lpattack` ...) as accessor objects. Contradicts C6a;
   would need the maintainer to reverse that decision and the deleted-alias spec to change.
3. **Won't implement**: envelope stages stay compound-only; a mapper on `lpadsr` is documented as
   unsupported (or reported as a diagnostic, see the OPEN diagnostic item in the main plan).

Decision (maintainer, 2026-09-07): option 1, piloted on `adsr` (`object Adsr` with
`@KlangScript.Property val attack: FieldAccessor` children and the setter as `invoke`; see
`sprudel/ref/dsl-conventions.md`). Next: `lpadsr`, `hpadsr`, `bpadsr` in the same shape, then the
compressor's remaining slots.
