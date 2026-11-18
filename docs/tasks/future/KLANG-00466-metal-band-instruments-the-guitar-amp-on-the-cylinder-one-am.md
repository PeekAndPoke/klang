# KLANG-00466: Metal band instruments: the guitar amp on the cylinder, one amp per guitarist

> **Status (2026-11-18): IDEA.** 
>
> **Part of:** KLANG-00459 (Metal band instruments: one band, the same instruments in every metal song)

## Why this exists

The maintainer (2026-11-18): "we really need to work on the guitar tone ... the shared cylinder amp might be the
solution, or at least a good start for real improvements. Because no matter how I turn the knobs, something is
always off."

Today the whole guitar rig is part of the voice, so every note has an amp of its own (KLANG-00459, "Found by
reading the rig"): notes never meet inside a clipper, and every note starts a cold amp. A real guitarist has one
amp. This ticket is the plan to move the amp to the guitarist's cylinder. It is the index; each step is a ticket
of its own, written so that an agent can carry it out.

## Decided by the maintainer (2026-11-18)

1. The amp moves to the cylinder: the voice keeps the string and the pickup, the rest becomes a declared chain.
2. A chain gets `lowpass()`, `highpass()`, `bandpass()` and `notch()` beside its `eq()`, and `shape()`.
3. The Katalyst gets an optimizer that combines filters, as the Ignitor has one.
4. The two rhythm guitars of Der Schmetterling go through one amp.
5. Der Schmetterling and Kokon are rewritten for it.
6. A bass guitar rig is prepared in the same style.
7. The notes on what may not be ideal in the current caricatures are written down.

## The steps

| Step | Ticket | What | Needs |
|---|---|---|---|
| 1 | KLANG-00467 (step 1: the filter and shape words on a chain) | engine and DSL | the decisions below |
| 2 | KLANG-00468 (step 2: the Katalyst optimizer) | engine | step 1 |
| 3 | KLANG-00469 (step 3: Der Schmetterling) | song | step 1; step 2 is not needed to start |
| 4 | KLANG-00470 (step 4: Kokon) | song | step 3's verdict |
| 5 | KLANG-00471 (step 5: the bass rig) | song | step 1 |
| notes | KLANG-00472 (the caricatures reviewed) | a list to work through by ear | nothing; read before steps 3 to 5 |

Steps 1 and 2 are engine work and go through the full review loop. Steps 3 to 5 are song work: the maintainer
listens, and nothing in them is done until the maintainer has.

## Decisions the plan rests on (to confirm before step 1 starts)

Each is written with the choice the plan makes. A "no" to one of them changes the step it names.

| | Decision | The plan's choice | Why |
|---|---|---|---|
| D1 | How a chain filter is stored | The four doors append an `eq` stage holding the matching sections. No new stage types. The optimizer then merges adjacent `eq` stages. | The EQ core, the wire and the cylinder's EQ effect already run lowpass, highpass, bandpass and notch sections (`eqSectionSpec`). A voice needs filter nodes of its own because they carry `analog`, `env` and `humanize`; a chain filter carries none of them. The other way (four new stage types that an optimizer turns into sections) gives the same sound with more wire, more backend arms and more tests. |
| D2 | `passes` on a chain | A plain number fixed with the chain, on a small builder (`x => x.passes(3)`), expanded into sections when the chain is built | The number of sections is structure on a chain (as `distort`'s `shape` and a region's `factor` are); on a voice it is a slot knob. A recorded asymmetry. |
| D3 | A slot as `q` with more than one pass | Refused, with a script error | A cascade gives every stage its own q (the Butterworth ladder), and a section holds a number or a slot, never an expression: the refusal of Q49 of KLANG-00314 (kotlin typed doors), one case more. |
| D4 | `analog`, `humanize`, `env`, `adsr` on a chain filter | Not offered | They belong to a note; a cylinder has none. A builder offers only what its host can do. |
| D5 | `shape` on a chain | A new stage, `Shape` | The `distort` stage is off at an amount of 0, so "the curve at unity drive" cannot be written with it. |
| D6 | `drive` on a chain | Not added | `drive` is gain without a curve; a chain has `mul`. `powerStock`'s `drive(0.3)` becomes `mul(2.29)`. |
| D7 | `Kat.fn(k => ...)` | Added, the twin of `Ign.fn` | The maintainer's decision of 2026-10-28 for `Ign.fn`: written through it, the editor knows the argument's type and offers its doors. Rig parts become chain functions, so they need the same. |
| D8 | What the optimizer merges | Adjacent `eq` stages whose knobs are all numbers; never an `eq` holding a `tap` into the one before it; no kill switch | A tap reads its EQ's input, so merging would move what it hears. A slot knob can move while the chain runs, and the stage answers a move as a whole, so merging would change how a move sounds. With both refusals the merged chain renders the same bits as the written one, always, so there is nothing to settle by ear. |
| D9 | Where the optimizer runs | In the backend, when a chain is registered | The voice's optimizer runs there (`IgnitorRegistry`); the wire carries what was written. |
| D10 | Section words inside `eq(...)` (`e.lowpass(...)`) | Not added now | That is KLANG-00250 (general EQ core); by the parity rule it would have to land on the voice's EQ builder too. |
| D11 | How the two variants are compared | Both in the song behind one switch, the old rig deleted after the verdict | The maintainer edits the songs daily; a copied song file would be stale within a day. |
| D12 | The bass rig | On the bass's cylinder too | Its first stage is a compressor, and a compressor exists only on a cylinder. |
| D13 | Pan after the amp | OPEN, found while planning. A chain has no `pan`; with the amp on the cylinder a voice's `pan` comes before the amp. Der Schmetterling does not need it, Kokon has four lines that do | KLANG-00470 (step 4) has the options: a `pan` stage on a chain, accepting the drift, or leaving those lines on the per-note rig. |

## What every step may rely on (read from the tree, 2026-11-18)

- **The cylinder's `distort` is the voice's**: the same shaper core (`DistortionCore`), the same shapes, the same
  drive law `shape(x * 10^(amount * 1.2))`, left and right each through a core of their own. Its KDoc names this
  use: "the amp fed by a whole chord". One deliberate difference: its DC blocker's corner is 7.64 Hz, the voice's
  near 35 Hz.
- **The voice's plain filters are EQ sections already.** A `lowpass(freq, q)` without `analog`, `env` or
  `humanize` is fused by the voice's optimizer into `EqCore` sections, a cascade as N sections on the Butterworth
  ladder. A chain filter built from the same sections is the same filter.
- **On a chain today:** `distort`, `eq` with `band` and `tap`, the arithmetic (`mul`, ...), `parallel`, `serial`,
  `blend`, `bands`, `oversampleBy`, `oversampleAtLeast`, `body`, `vowel`, `delay`, `reverb`, `phaser`,
  `compressor`, `limiter`, `duck`, `gain`, `classic()`. Inside a region every stage keeps its sound in seconds and
  Hz, a filter its cutoff (`KatalystStageDsl.OversampleBy`'s KDoc).
- **`classic()` can follow an amp.** It appends the cylinder's usual stages (body, vowel, delay, reverb, phaser,
  compressor, the fader), all listening to their slots, so the pattern's `body(...)`, `reverb(...)`,
  `compressor(...)` and `katp("gain.gain", ...)` keep working on a cylinder that declares an amp first.
- **A chain's identity is its content.** Two patterns naming chains that are equal by value get the same name, so
  two guitars on one cylinder naming the same amp do not swap chains (`KatalystDsl.uniqueId`).
- **The chain stays alive between notes.** A cylinder never deactivates while a voice plays on it; it goes after
  its mix has been silent for the grace (ten clean-up visits, one cylinder visited per block, so with ten cylinders
  about 0.3 s) and nothing rings, and only then is the chain reset.
- **Only the newest voice's bus settings are heard on a cylinder.** Two patterns on one cylinder must write the
  same bus settings, or the settings live in the chain as numbers.
- **`Ign.fn(f)` returns `f` as it is.** A rig part is a plain function already; `Ign.fn` only tells the editor the
  type.
- **No built-in song puts a chain on a cylinder yet** (`.katalyst(...)` on a pattern); every one uses a chain only
  at the output (`master(...)`). The song steps are the first real use.

## For the agent that carries a step out

- **Load before you start:** `/klangaudio-knowhow` and `/dsl-design` (steps 1 and 2), `/klangscript-knowhow`
  (step 1's script doors), `/code-style`, `/console-tools`, `/review-loop`; `/klang-music-writing` for steps 3 to 5.
  Each step's ticket names the files to read first.
- **Move the ticket** when you start and when you finish, with the `ticket` tool, never by hand.
- **Gradle only through the lock**, one build at a time: `env KLANG_LOCK_TIMEOUT=7200 console/with-build-lock.sh`,
  and read a test run with `test_digest.py run ... --expect <the specs you care about>` (`/console-tools`). A long
  run goes to the background.
- **Steps 1 and 2 are strict code** (`audio_be`, `audio_bridge`): the review loop starts on rung 2
  (`reviewer-fable-medium`), a code reviewer and an audio reviewer, round 1 blind, until a clean round; every new
  test is mutation-checked with a `mutate` campaign. A new stage runs the DSP probes that apply to it first
  (`audio/ref/verification.md`, "The probes").
- **Never say how something sounds.** No agent can listen. Say what was built, what was rendered and what was
  measured; the maintainer says how it sounds.
- **The songs are edited every day.** Before a song step: `git status` and `git log -3` on the song file, and read
  it again. Never overwrite uncommitted edits; the numbers in the tickets are those of 2026-11-18 and may have
  moved, so port the numbers the file holds, not the ticket's.
- **Commit each finished, reviewed step** by its paths, with the `Co-Authored-By:` trailer naming your model. No
  em-dashes in docs, KDoc or commit messages.
- **Stop and ask** when a decision above turns out not to hold in the code, when a step needs more surface than
  its ticket names (the stone rule on complexity), and before deleting the old per-note rig.

## Acceptance

The rhythm guitars of Der Schmetterling run through one amp on one cylinder, the maintainer has compared it with
the per-note rig, and the decision which one the band keeps is recorded here. Each step's own ticket has its own
acceptance.

## History

- 2026-11-18: created, from the maintainer's question what a shared guitar amp needs.
- 2026-11-18: rewritten as the index of the plan, with its decisions and steps (KLANG-00467 to KLANG-00472).
