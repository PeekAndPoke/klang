# KLANG-00352: Sound banks of instruments: Ign.bank, played by name and index

> **Status (2026-10-27): IDEA.** the maintainer, for the not so far future
>
> **Part of:** KLANG-00359 (Sprudel: the pattern language's surface)

## Why this exists

The maintainer, 2026-10-27: "We have Ign.variants() which can be used via sound(myInst).n(1). It would be super cool
to also have this."

```
let myBank = Ign.bank({
  "bd": Ign.variants(myBd1, myBd2),
  "sd": mySd,
})

sound("bd:0 sd bd:1 sd").bank(myBank)
```

A song builds its own drum kit (or any kit) from Ignitor instruments and plays it with the same mini-notation it uses
for a sample bank: the name picks the instrument, the index after the colon picks the variant
(`IgnitorDsl.Variants`, by the voice's `soundIndex`, as `sound(myInst).n(1)` does today). It brings the sample
workflow (`s("bd sd").bank("RolandTR808")`) to sounds made with the Motor.

## Today

- `Ign.variants(...)` / `Ignitor.variants(...)` (`audio_bridge/.../Ign.kt`, `klangscript-libs/.../KlangScriptIgnitor.kt`)
  builds an `IgnitorDsl.Variants`; the engine picks one child per note by `soundIndex`.
- `sound(dsl)` stamps `SoundValue.Dsl(dsl)` on each event; `sound("bd:1")` stamps `SoundValue.Named("bd")` and
  `soundIndex = 1` (`sprudel/.../lang/lang_tonal_sound.kt`).
- `.bank(name)` takes a string only: the sample bank's name, a typed wire field.

## Decide before implementing

- **Where the lookup happens.** Recommended: in sprudel, before the wire. `.bank(myBank)` turns each event's
  `SoundValue.Named(name)` into `SoundValue.Dsl(bank[name])` and keeps `soundIndex`; the backend sees what
  `sound(myInst).n(1)` sends today, and never learns about banks (the engine is the horse). A name the bank lacks:
  falls through to the sample lookup, or is an error the editor shows (KLANG-00251)?
- **A plain instrument under a name** (`"sd": mySd`): any index plays it, or `sd:1` is an error / wraps round as
  `Variants` does?
- **The door's shape** (`/dsl-design`): `Ign.bank({...})` with an object literal, the Kotlin twin (`Ign.bank(mapOf(...))`
  or a builder), parameter parity, and whether `.bank()` takes both a string and a bank (a union type, KLANG-00212).
- **Name.** "bank" for the sample collection and for this; one word for both, or another word (kit?).
- **Patterned banks:** `.bank("<a b>")` alternates sample banks per cycle; does `.bank("<$kitA $kitB>")` need to work?
- Relation to the `snd*` doors (KLANG-00101, KLANG-00189).

## The work

Not planned while this is an idea.

## Acceptance

Not set while this is an idea.

## History

- 2026-10-27: created from the maintainer's idea.
- 2026-10-27: part of KLANG-00359 (Sprudel: the pattern language's surface), the epic grouping.
