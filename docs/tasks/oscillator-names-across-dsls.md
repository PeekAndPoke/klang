# One name per oscillator shape across the DSLs

Status: **asked 2026-10-05 by the maintainer, one decision open.** Not started.

## The ask (maintainer, 2026-10-05)

"Also now that i saw the oscillator list in sprudel. I think we also have to align these in the Ign.xxx() family."
The sprudel side was cleaned first (`docs/tasks-archive/2026-10/20261005-sprudel-signals-range-cleanup.md`): its
signals are now `sine`, `cosine`, `saw`, `tri`, `square`, `perlin`, `berlin`, `rand`.

## Where the names stand (2026-10-05)

| Shape           | sprudel signal | Ignitor door                  | sound name (`s("...")`) | LFO shape (`LfoShapes`) |
|-----------------|----------------|-------------------------------|-------------------------|-------------------------|
| sine            | `sine`         | `Ign.sine`, `supersine`       | `sine`, `sin`           | `sine` (alias `sin`)    |
| cosine          | `cosine`       | none                          | none                    | none                    |
| rising saw      | `saw`          | `Ign.saw`, `supersaw`         | `saw`, `sawtooth`       | `sawtooth` (alias `saw`) |
| falling saw     | `saw.range(1, 0)` | `Ign.ramp`, `superramp`    | `ramp`                  | `ramp`                  |
| triangle        | `tri`          | `Ign.triangle`, `supertri`    | `triangle`, `tri`       | `triangle` (alias `tri`) |
| square / pulse  | `square`       | `Ign.square`, `supersquare`   | `square`, `sqr`, `pulse` | `square` (aliases `sqr`, `pulse`) |
| noise           | `perlin`, `berlin`, `rand` | `Ign.perlin`, `berlin`, the noise colours | the noise names | none |

## What is already covered elsewhere

- **cosine on the Ignitor**: arrives with the phase knob, as a sine with `phase = 0.25`
  (`docs/tasks/oscillator-phase-knob.md`).
- **`ramp`, `zamp`, `zawtooth`, `pulze`**: folding the raw twins into their rounded oscillators with an edge knob is
  `docs/tasks/future/chip-style-instruments.md`, which needs a bit-identity proof per fold.

## Open decision

**The triangle.** The Ignitor door says `triangle` but its super variant says `supertri`; sprudel says `tri`; the sound
names and the LFO shapes accept both. The options:

- `triangle` everywhere with `tri` as the short name on every surface (the Ign/Ignitor pattern: a full and a short
  name for one word), `supertri` stays as the super variant's name;
- `tri` only, with `triangle` removed as a door and kept as a sound alias or not;
- leave it as it is, recorded as an asymmetry with its reason.

The same question then holds for `saw` / `sawtooth` and `square` / `sqr` / `pulse` in the sound and LFO names.
