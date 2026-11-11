# KLANG-00437: Engine optimizations backlog: what the audit saw and did not build

> **Status (2026-11-11): IDEA.** Collected from the engine audit (KLANG-00420), maintainer 2026-11-11.
>
> **Part of:** KLANG-00355 (Performance: fast on every device)

## Why this exists

The maintainer (2026-11-11): "we should keep a possible optimizations backlog somewhere." The engine audit
(KLANG-00420) saw these and did not build them. Each line names the ticket that holds the measurement and the
options. An item leaves this list when it is built or dropped. The Ignitor optimizer has its own list: KLANG-00204.

## The list

| What | Where it is described | State |
|---|---|---|
| Build a voice at schedule time, not in its onset block | KLANG-00427, L2-06 option (a) | not built; the build cost was halved in KLANG-00429 |
| Per-note rings (shimmer, pluck, superpluck) rented from the warehouse and given back when the voice ends | KLANG-00429, L5-03 option (a) | wanted (maintainer 2026-11-11), a plan first |
| A modulator shared by a source's knob and a filter's knob built once per pitch context | KLANG-00429, L5-05 option (b) | left |
| A block-constant read of a lifted LFO reads the base-rate source | KLANG-00429, L5-08 option (a) | left |
| The fork of built trees bounded per playback | KLANG-00429, L5-10 option (b) | accepted as it is |
| One generator per noise node, an inline xorshift (saves a virtual call per sample) | KLANG-00430, L3-16 | with the noise cost work |
| The box of the band-limited stacks (cost +33 percent on a supersaw voice) made cheaper | KLANG-00430, L3-02 | kept as landed; a cheaper form is open |
| The diagnostics: the clock at 50 Hz, the gauges only when they change | KLANG-00431, D4 | waits for a phone profile |
| A sample handed to the worklet as one transferred buffer | KLANG-00431, D5 | open |
| One PCM per zone in the worklet, not one per note; eviction | KLANG-00431, D2 | open |
| The warm-up runs the master chain and the stages outside the born-with chain | KLANG-00431, D7 | a phone measurement first |
| `KlangSnapshotMap.getOrPut` copies the map at every new key | seen in KLANG-00429 | open |
| `SizedBuffers.giveBack` allocates an `Idle` wrapper | seen in KLANG-00428 | open |

## History

- 2026-11-11: created.
