# KLANG-00312: Songs move freely: no check goes red on a song edit

> **Status (2026-10-15): DONE.**

Status: **done (2026-10-15).** Small. Comes from Q34: "It is also important that
the songs can 'move' freely without making any tests red", and the rule it led to (`CLAUDE.md`, "Two kinds of
check", provisional): every check is either for the future or scaffolding for one piece of work, and scaffolding goes
when that work is done.

## What was built

The corpus reference holds a code hash per row (the song's text, its rpm and the built-in modules it imports) next to
its sound hash. A frozen text whose sound differs is red, and so is a live song whose code is unchanged but whose sound
moved (the engine or its inputs moved it); a live song whose code moved is information. `corpus-accept` refuses while
any row is red, and `corpus-accept-engine` accepts a deliberate engine sound change. The rig suite runs on the frozen
2026-09-25 Der Schmetterling, so its anchors never meet a live song, and each song-related check names its kind in its
KDoc. `console/full-check.sh` stays green on a live song edit and lists it.

## What went red before, when a song was edited

Seen on 2026-10-12 and 13, each time the maintainer edited Kokon or Der Schmetterling:

| Check | What it ties to the live song | Kind | Proposal |
|---|---|---|---|
| Corpus (`CorpusCheck.kt`, `runSongBenchmark --args=corpus`, `docs/benchmarks/corpus-reference.md`) | a hash of every live built-in song's render | for the future, but only its engine half: it proves a behaviour-neutral engine change is neutral | keep the frozen songs and pieces red on a change; a live song's CHANGED row becomes information, not RED. Or drop the live songs and freeze one snapshot of each per release |
| `SongBenchmarkCasesCompileSpec`, the live rig suite (`SongBenchmarkCases.kt`, `liveCase`, `swapAll(..., expected = 3)`) | text anchors in the live Der Schmetterling | scaffolding of the rig-ablation measurements (2026-08/09) | run the suite on a frozen snapshot (`FrozenSongs.derSchmetterling_2026_09_25` exists), or remove it if its job is done |
| `BuiltInSongsSmokeTest` | every live song compiles | for the future | keep: a song that no longer compiles is a real break (the 2026-10-12 paste of a retired `through`) |
| `OptimizerSongParitySpec` | every live song's inlined instruments render within the margin under the optimizer pass | for the future (an engine property over real material) | keep; it goes red only when the song does not compile or the optimizer breaks |

## Done when

A song edit that still compiles leaves the full check green, and each song-related check above names its kind in its
KDoc.

## History

- 2026-10-20: numbered KLANG-00312 (was `docs/tasks-archive/2026-10/20261015-song-checks-move-freely.md`).
