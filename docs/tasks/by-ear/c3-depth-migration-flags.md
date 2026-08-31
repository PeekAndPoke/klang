# C3 semitone-depth migration, maintainer flag list (2026-08-24)

## Needs ears, ranked (mid-sweep trajectories changed even where endpoints are exact)

The old law was Hz-linear (fast at the bottom of the sweep, slow at the top); the new law is
pitch-uniform, so upward sweeps open slower at first. Deep + slow + exposed ranks highest:

1. IrishLamentTechno hitStab: lpe(71.2) with lpadsr(2.5, 0.5, 0.5, 10.0) - 5.9 octaves over a
   2.5 s attack (also flagged below for its suspicious pre-C3 value 60).
2. SoundOfTheSea "Waves": lpe(31) with lpadsr(1.0, 3.0, 0.4, 20.0) on pink noise - slow exposed
   noise sweeps are the most trajectory-sensitive material in the corpus.
3. TetrisRemix sub: lpe(48) with lpadsr(0.005, 1.0, 0.0, 0.2) - 4-octave 1 s closing sweep.
4. StrangerThings melody: lpe(perlin.range(21.7, 27.9).slow(8)) - patterned depth, migrated at
   endpoints; the 5 s bloom's shape changed.
5. Deep fast percussive sweeps (Tetris subShape 56.4, ATruthWorthLyingFor bass 52.7,
   FrozenSongs bass 62): attack character may shift slightly; lower priority.

## Patterned depths (endpoint-migrated in the C3 review round; ranges reshaped by the law)

- StrangerThings.kt: .lpe(perlin.range(2.5, 4.0).slow(8)) -> range(21.7, 27.9)
- FrozenSongs.kt (x2): berlin.range(2, 2.10) -> range(19.0, 19.6); perlin.range(2.5, 4.0) ->
  range(21.7, 27.9) (syntax-only freeze exception applies)
- SongBenchmarkCases.kt: berlin.range(2, 2.10) -> range(19.0, 19.6)
- GoldenCorpus.kt: berlin.range(3.0, 3.0) -> range(24.0, 24.0). Curious pre-existing quirk
  found while verifying: this signal-valued lpe never surfaces as `envelope.depth` in the
  golden dump at EITHER value (the corpus lead's other params do) - worth a look whether
  signal-valued lpe reaches the wire at all on that entry.

## Open user decision (from the plan)

Either rename the depth call (so stale linear values fail loudly) or teach intellisense to flag
suspiciously small values (lpe(2) = 2 semitones is almost certainly a stale linear-era value).
Parked for the maintainer.


Every constant depth was migrated `new = 12*log2(1 + old)` (exact at envelope
endpoints; MID-SWEEP trajectories differ — pitch-linear now). Items needing ears:

- src/commonMain/kotlin/builtinsongs/IrishLamentTechno.kt: .lpe(60) -> .lpe(71.2) — suspiciously large old value, verify by ear

Full migration log:

- src/commonMain/kotlin/builtinsongs/Tetris.kt: lpe(1.35) -> (14.8)
- src/commonMain/kotlin/builtinsongs/Tetris.kt: lpe(1.3) -> (14.4)
- src/commonMain/kotlin/builtinsongs/Tetris.kt: lpe(25) -> (56.4)
- src/commonMain/kotlin/builtinsongs/TetrisRemix.kt: lpe(1) -> (12)
- src/commonMain/kotlin/builtinsongs/TetrisRemix.kt: lpe(2) -> (19)
- src/commonMain/kotlin/builtinsongs/TetrisRemix.kt: lpe(2.5) -> (21.7)
- src/commonMain/kotlin/builtinsongs/TetrisRemix.kt: lpe(15.0) -> (48)
- src/commonMain/kotlin/builtinsongs/IrishLamentTechno.kt: lpe(1.5) -> (15.9)
- src/commonMain/kotlin/builtinsongs/IrishLamentTechno.kt: lpe(2) -> (19)
- src/commonMain/kotlin/builtinsongs/IrishLamentTechno.kt: lpe(0.25) -> (3.9)
- src/commonMain/kotlin/builtinsongs/IrishLamentTechno.kt: lpe(1.2) -> (13.7)
- src/commonMain/kotlin/builtinsongs/IrishLamentTechno.kt: lpe(60) -> (71.2)
- src/commonMain/kotlin/builtinsongs/IrishLamentTechno.kt: lpe(4) -> (27.9)
- src/commonMain/kotlin/builtinsongs/IrishLamentTechno.kt: lpe(1.2) -> (13.7)
- src/commonMain/kotlin/builtinsongs/IrishLamentTechno.kt: lpe(3) -> (24)
- src/commonMain/kotlin/builtinsongs/IrishLamentTechno.kt: lpe(2) -> (19)
- src/commonMain/kotlin/builtinsongs/IrishLamentTechno.kt: lpe(1.5) -> (15.9)
- src/commonMain/kotlin/builtinsongs/IrishLamentTechno.kt: lpe(1.5) -> (15.9)
- src/commonMain/kotlin/builtinsongs/SoundOfTheSea.kt: lpe(2) -> (19)
- src/commonMain/kotlin/builtinsongs/SoundOfTheSea.kt: lpe(5) -> (31)
- src/commonMain/kotlin/builtinsongs/SoundOfTheSea.kt: lpe(3.0) -> (24)
- src/commonMain/kotlin/builtinsongs/ATruthWorthLyingFor.kt: lpe(2) -> (19)
- src/commonMain/kotlin/builtinsongs/ATruthWorthLyingFor.kt: lpe(1.5) -> (15.9)
- src/commonMain/kotlin/builtinsongs/ATruthWorthLyingFor.kt: lpe(1.5) -> (15.9)
- src/commonMain/kotlin/builtinsongs/ATruthWorthLyingFor.kt: lpe(1.25) -> (14)
- src/commonMain/kotlin/builtinsongs/ATruthWorthLyingFor.kt: lpe(20.0) -> (52.7)
- src/commonMain/kotlin/pages/docs/tutorials/tut_TheFilterEnvelope.kt: lpe(7) -> (36)
- src/commonMain/kotlin/pages/docs/tutorials/tut_TheFilterEnvelope.kt: lpe(7) -> (36)
- src/commonMain/kotlin/pages/docs/tutorials/tut_TheFilterEnvelope.kt: lpe(7) -> (36)
- src/commonMain/kotlin/pages/docs/tutorials/tut_TheFilterEnvelope.kt: lpe(7) -> (36)
- src/commonMain/kotlin/pages/docs/tutorials/tut_TheFilterEnvelope.kt: lpe(7) -> (36)
- src/commonMain/kotlin/pages/docs/tutorials/tut_TheFilterEnvelope.kt: lpe(7) -> (36)
- src/commonMain/kotlin/pages/docs/tutorials/tut_TheFilterEnvelope.kt: lpe(7) -> (36)
- src/commonMain/kotlin/pages/docs/tutorials/tut_TheFilterEnvelope.kt: lpe(7) -> (36)
- src/jvmMain/kotlin/FrozenSongs.kt: lpe(0.6) -> (8.1)
- src/jvmMain/kotlin/FrozenSongs.kt: lpe(0.6) -> (8.1)
- src/jvmMain/kotlin/FrozenSongs.kt: lpe(35) -> (62)
- src/jvmMain/kotlin/SongBenchmarkCases.kt: lpe(0.6) -> (8.1)
- src/jvmMain/kotlin/SongBenchmarkCases.kt: lpe(0.6) -> (8.1)
- src/jvmMain/kotlin/SongBenchmarkCases.kt: lpe(35) -> (62)
- src/jvmMain/kotlin/SongBenchmarkCases.kt: lpe(0.6) -> (8.1)
- src/jvmMain/kotlin/SongBenchmarkCases.kt: lpe(0.6) -> (8.1)
- src/jvmMain/kotlin/SongBenchmarkCases.kt: lpe(0.6) -> (8.1)
- src/jvmMain/kotlin/SongBenchmarkCases.kt: lpe(0.6) -> (8.1)
- sprudel/src/jvmTest/kotlin/golden/GoldenCorpus.kt: lpe(1.0) -> (12)
- sprudel/src/jvmTest/kotlin/golden/GoldenCorpus.kt: lpe(1.0) -> (12)
- sprudel/src/jvmTest/kotlin/golden/GoldenCorpus.kt: lpe(2) -> (19)
- .claude/skills/klang-music-writing/ref/sprudel-reference.md: lpe(3) -> (24)
- .claude/skills/klang-music-writing/ref/sprudel-reference.md: lpe(3) -> (24)
- .claude/skills/klang-music-writing/ref/sprudel-reference.md: hpe(3) -> (24)
- .claude/skills/klang-music-writing/ref/sprudel-reference.md: hpe(3) -> (24)
- .claude/skills/klang-music-writing/ref/sprudel-reference.md: bpe(4) -> (27.9)
- .claude/skills/klang-music-writing/ref/sprudel-reference.md: bpe(4) -> (27.9)
