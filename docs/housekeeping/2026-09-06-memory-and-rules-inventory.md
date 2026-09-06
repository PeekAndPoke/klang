# Memory and rules inventory, 2026-09-06

Housekeeping session requested by the maintainer: "do a deep inventory about memories and rules
for this project. We need to curate them and also give them scores. Outdated memories need to go.
Rules need a hardness describing how binding a rule is. Not everything that I say is to be treated
as a forever rule."

Outcome in one line: the 89 home-directory memories are all retired (archived, not deleted); the
facts that existed nowhere else moved into repo files; every standing rule now sits in the
`CLAUDE.md` rules register with a hardness level.

## Scoring

| Score | Meaning                                                                    | Disposition                  |
|-------|----------------------------------------------------------------------------|------------------------------|
| 3     | A standing rule, or a fact recorded nowhere in the repo                    | moved into the repo          |
| 2     | Already recorded in the repo (code, KDoc, task doc, skill, module memory)  | retired, pointer in this file|
| 1     | Finished work fully recorded by git history and the task archive           | retired                      |
| 0     | Outdated, superseded, or a one-off session instruction                     | retired                      |

Hardness levels (stone / rule / guideline / guardrail) are defined in `CLAUDE.md`.

## Home memories (89 files)

Archived to `~/.claude/projects/-opt-dev-peekandpoke-klang/memory/_archive-2026-09-06/`. The
archive is not loaded by any session; the maintainer can delete the directory once satisfied.

| Memory file                              | Score | Disposition and where the fact lives now                                                     |
|------------------------------------------|-------|----------------------------------------------------------------------------------------------|
| engine_dsl_misnamed                      | 1     | PR #64, `docs/tasks/engine-tuning-profile.md`                                                |
| feedback_ast_walker_location             | 3     | register (rule)                                                                              |
| feedback_caricature_sound_model          | 3     | register (guideline)                                                                         |
| feedback_codefactor_lint                 | 2     | `/review-loop` Gotchas; register (guideline)                                                 |
| feedback_design_for_adults               | 3     | register (guideline)                                                                         |
| feedback_exhaustive_when                 | 3     | `/code-style` §19                                                                            |
| feedback_flat_directories                | 3     | `/code-style` §3                                                                             |
| feedback_icon_render                     | 3     | `/kraft-knowhow` Klang UI conventions                                                        |
| feedback_klangbuch_parts_arrangement_free| 3     | `sprudel/MEMORY.md` Lessons; register (rule)                                                 |
| feedback_kotest_test_filter              | 3     | `/review-loop` Gotchas; register (guardrail)                                                 |
| feedback_motor_umlaut                    | 3     | register (stone, naming)                                                                     |
| feedback_nan_guard_comment               | 3     | `/code-style` §20                                                                            |
| feedback_no_em_dashes                    | 3     | `/code-style` §22; register (rule)                                                           |
| feedback_no_fqcn                         | 3     | `/code-style` §18                                                                            |
| feedback_no_long_in_audio                | 2     | `/code-style` §5; register (stone)                                                           |
| feedback_review_loop                     | 2     | `/review-loop`                                                                               |
| feedback_roundgauge_sizing               | 3     | `/kraft-knowhow` Klang UI conventions                                                        |
| feedback_semantic_with_syntax            | 3     | `/kraft-knowhow` Klang UI conventions                                                        |
| feedback_stop_before_commit              | 0     | one-off, superseded 2026-09-06 ("free to commit completed steps"); register (rule, commit per step) |
| feedback_tutorial_quality                | 3     | `docs/tasks/tutorial-curriculum.md` appendix                                                 |
| feedback_tutorial_series                 | 3     | same                                                                                         |
| feedback_tutorial_workflow               | 3     | same                                                                                         |
| feedback_we_built_it_together            | 3     | register (guideline)                                                                         |
| osc_detune_spread_rename                 | 1     | git history                                                                                  |
| pipeline_stage_design                    | 2     | `docs/plans/unified-eq.md`, whitepaper (double-VCA sandwich)                                 |
| project_analog_drift_tuning              | 2     | `AnalogDriftSpec`, `audio/MEMORY.md`                                                         |
| project_arrange_boundary_drop            | 2     | `StructuralCycleSelectionSpec`; register (guardrail)                                         |
| project_audio_backend_audit              | 2     | `docs/tasks/audio-backend-audit.md`, `docs/tasks/future/audit-parked-decisions.md`           |
| project_block_framing_invariance         | 2     | `docs/plans/block-framing-invariance.md`, `docs/tasks/by-ear/`                               |
| project_block_size_parity                | 2     | `DelayLine` KDoc; register (guardrail)                                                       |
| project_bluetooth_latency                | 2     | `docs/tasks/future/midi-latency-optimizations.md`                                            |
| project_body_resonator                   | 2     | `audio/MEMORY.md`, archived resonator-swing task                                             |
| project_browser_benchmark_page           | 2     | `docs/tasks/future/browser-benchmark-page.md`                                                |
| project_cycletime_migration              | 2     | `sprudel` code and docs                                                                      |
| project_effect_scope_docs                | 2     | `docs/tasks/orbit-level-effect-docs.md`                                                      |
| project_engine_dsl                       | 2     | `docs/tasks/engine-tuning-profile.md`                                                        |
| project_engine_naming                    | 2     | `Pipeline.modern` / `Pipeline.pedal` in code                                                 |
| project_fe_be_state_placement            | 2     | plan complete; principle now in `audio/MEMORY.md` Architecture Decisions                     |
| project_filter_freq_naming               | 2     | `docs/tasks-archive/2026-08/` (decided, unstarted)                                           |
| project_filter_review                    | 2     | OnePole HPF bias documented in code; register (guardrail)                                    |
| project_filter_saturation_dead_end       | 2     | `audio/MEMORY.md` section                                                                    |
| project_filter_unification               | 2     | `docs/plans/filter-unification.md`                                                           |
| project_fractional_pitch_closed          | 2     | `docs/tasks-archive/2026-08/20260824-fractional-pitch-input.md`                              |
| project_gradle_no_concurrent_builds      | 2     | `/agent-fleet`, `/review-loop` Gotchas (watcher line added)                                  |
| project_ignitor_envelope_ownership       | 1     | archived task, `docs/tasks/future/envelope-shape-followups.md`                               |
| project_import_overwrite_warning         | 3     | `docs/tasks/klangscript-intellisense.md` backlog item                                        |
| project_katalyzers                       | 2     | `docs/tasks/katalyst-dsl.md`                                                                 |
| project_klangblocks_removed              | 2     | register (retired list)                                                                      |
| project_klangmotor_rename                | 2     | archived motor-branding task; register (stone, naming)                                       |
| project_latency_hint_topic               | 0     | superseded by later commits ("js backend back to playback"); ownership expired               |
| project_licensing                        | 2     | `LICENSE`, `AUTHORS.MD`, `tones/LICENSE`, `docs/tasks/copyright-audit-00-overview.md`; register (stone) |
| project_live_update_double_voice         | 2     | `VoiceScheduler.isDuplicate` and its spec                                                    |
| project_midi_note_off_review             | 1     | archived task                                                                                |
| project_midi_playground                  | 1     | `docs/tasks/midi-keyboard-playground.md`, `realtime-playback-controller.md`; KeyLab map committed |
| project_mini_notation_tweaks             | 1     | archived task, `docs/tasks/future/mini-notation-tweaks-followups.md`                         |
| project_motor_naming                     | 2     | README, whitepaper vocabulary (Cylinder, Injection, Ignitor, Katalyst)                       |
| project_motor_slogans                    | 2     | strategist agent memory                                                                      |
| project_mutable_voicedata                | 2     | `sprudel/MEMORY.md` Lessons, `docs/tasks/constant-control-fast-path.md`                      |
| project_n_add_noop                       | 2     | `docs/tasks/future/n-pattern-add-noop.md`                                                    |
| project_noise_generator_knobs            | 1     | archived task                                                                                |
| project_notstrom_demo                    | 2     | strategist agent memory, song sources                                                        |
| project_perf_native_backend              | 2     | `docs/tasks/future/high-performance-audio-backend.md`; register (guideline, sound first)     |
| project_per_playback_engine              | 1     | archived, `audio/ref/architecture.md`                                                        |
| project_phoneme_singing                  | 3     | `docs/tasks/future/phoneme-singing.md` (was the only record)                                 |
| project_pipeline_coefficient_exposure    | 2     | `docs/tasks/pipeline-dsl-coefficient-exposure.md`                                            |
| project_playback_layer_decomposition     | 2     | `docs/tasks/playback-layer-decomposition.md`                                                 |
| project_realtime_latency                 | 2     | `docs/tasks/realtime-playback-controller.md`, `future/midi-latency-optimizations.md`         |
| project_resource_warehouse_pool          | 1     | archived task, code                                                                          |
| project_reverb_denormal_handling         | 2     | `Reverb.kt` comment; register (guardrail)                                                    |
| project_sample_mirror                    | 3     | `docs/tasks/future/sample-mirror-operations.md` (operations + licensing audit had no doc)    |
| project_scale_apply_once                 | 3     | `sprudel/MEMORY.md` Lessons                                                                  |
| project_signal_sampling_semantics        | 2     | `docs/tasks/future/live-voice-modulation.md`                                                 |
| project_silent_shape_discard             | 2     | `docs/tasks/silent-shape-discard-on-error.md`, `runtime-errors-in-the-editor.md`             |
| project_song_cpu_benchmark               | 2     | `runSongBenchmark` harness in code                                                           |
| project_sound_first                      | 3     | register (guideline); the home-dir plan file it cites is gone, the strategist vision doc remains |
| project_soundfont_looping                | 1     | archived task                                                                                |
| project_supersaw_onset                   | 2     | `audio/MEMORY.md`, spec                                                                      |
| project_supersaw_rewrite                 | 2     | `AnalogSawSpec`, `audio/MEMORY.md`                                                           |
| project_timeshift_boundary_dedup         | 2     | `LangLateAlternationSpec`, `TimeShiftPattern` KDoc                                           |
| project_tutorial_curriculum_rework       | 2     | `docs/tasks/tutorial-curriculum.md` (the other session's doc; "owned by another agent" is a session fact, see the hand-off note there) |
| project_unified_eq_workstream            | 2     | `docs/plans/unified-eq.md`                                                                   |
| project_unison_phase_pool                | 2     | code, specs, `audio/MEMORY.md`                                                               |
| project_version_info                     | 2     | `docs/tasks-archive/2026-06/20260624-build-version-info.md`                                  |
| project_voice_takeover                   | 2     | `docs/tasks/voice-takeover.md`                                                               |
| project_warehouse_stats_feed             | 1     | archived task                                                                                |
| project_whitepaper                       | 2     | `docs/whitepaper/klang-whitepaper.html`; the "old artifact preview" note was session-only    |
| project_worklet_clock_divergence         | 3     | `docs/tasks/future/worklet-clock-divergence.md`                                              |
| project_worklet_serialization            | 2     | archived task (ProtoBuf rejection recorded there)                                            |
| sprudel_dsl_test_coverage                | 2     | `docs/tasks/sprudel-test-coverage-and-review.md`                                             |

Things the memories claimed that the repo contradicts, so the memory lost:

- The licensing memory said the copyright header spells "Motör"; the header is "Klangmotor".
- The latencyHint memory said another agent owned the topic; the maintainer's own later commits
  settled it (`playback`).
- The MIDI memory said the KeyLab CC map was uncommitted; it is committed in `MidiPanel.kt`.
- The sample-mirror memory said commits and a Gradle run were still pending; both happened.

## Repo-side rule sources (checked, kept)

| Source                                   | Verdict                                                                                  |
|------------------------------------------|------------------------------------------------------------------------------------------|
| `CLAUDE.md`                              | Now carries the register; prose sections "Complexity" and "Memory lives in the repo" became stone rows. |
| `.claude/skills/code-style`              | Kept; gained §3 flat directories and §18 to §22 (no FQCN, exhaustive when, NaN-guard, coerce vs require, no em-dashes). |
| `.claude/skills/dsl-design`              | Kept as written 2026-09-05; the register points at its sections.                          |
| `.claude/skills/review-loop`             | Kept; Gotchas gained the one-filter rule, the expect-red trap, and the watcher check.      |
| `.claude/skills/agent-fleet`             | Kept; gained the "rounds 3+ on the strongest tier" note.                                  |
| `.claude/skills/kraft-knowhow`           | Kept; gained "Klang UI conventions".                                                      |
| `.claude/skills/klang-music-writing`     | Not changed; still teaches the caricature model (register points there).                  |
| `.claude/BUILD-LOCK.md`                  | Coordination only; not a rule source.                                                     |
| `.claude/agents/music-platform-strategist` memory | Not touched; duplicates slogans, NOTSTROM, naming decision. It is that agent's own memory. |
| Module `MEMORY.md` files                 | `sprudel` and `audio` gained one lesson each; `klangscript` unchanged (updated 2026-09-06 already). |

## Not forever rules (explicitly demoted)

- "Stop before commit, the maintainer inspects the diff first": per-phase request; the standing
  rule is "commit completed, reviewed steps".
- "Switch to Fable if round 3 or more is necessary": kept as a guideline in `/agent-fleet`, not
  a rule about model names.
- "Tutorial curriculum is owned by a separate agent", "latencyHint is owned by another agent":
  coordination facts for a period; the hand-off note in the tutorial doc carries the live one.
- "Do not compile frontend changes (watcher running)": became a guardrail check, not a ban.
- "Old whitepaper artifact preview is frozen": artifact of one session, dropped.

## What is left for the maintainer

- Read the register once and move rows between hardness levels where the assigned level feels
  wrong; the levels are my proposal from the recorded wording and how often each was enforced.
- Delete the archive directory in the home memory when satisfied.
- `docs/tasks/tutorial-curriculum.md` appendix: the tutorial session may fold the craft rules
  into its own structure.
