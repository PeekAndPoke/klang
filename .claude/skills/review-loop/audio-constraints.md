# Audio backend constraints: paste into every audio review prompt

The engine carries a large body of **deliberate** decisions. A reviewer who "fixes" one of these makes
things worse. Paste this list into every audio-engineer reviewer prompt (template in `SKILL.md`) and
into any brief that touches `audio_be`, `audio_bridge`, `audio_fe` or `audio_jsworklet`. Background:
`audio/MEMORY.md` and `docs/tasks-archive/`.

Moved here 2026-09-27 from the audio backend audit brief (§7), which closed that day
(`docs/tasks-archive/2026-09/20260927-audio-backend-audit.md`). An entry naming code that has since
gone is removed when someone notices, not kept for history.

- **Raw Motor**: no defensive checks in the inner math, no safety clamps on user-facing params;
  defend at integration points (`ShapingFuncs.kt`, the KDoc at the top).
- **Reverb's `+ ANTI_DENORMAL` is a deliberate exception** to the engine-wide `flushDenormal()`
  convention; the consistent version cost about +11 % per sample and was reverted 2026-05-19.
- **The SVF is purely linear by design** (the BPF included). Two saturation attempts failed and were
  reverted; the `analog`/`bpFb` infrastructure is kept for a future re-introduction. Warmth comes
  from upstream.
- **The OnePole HPF cutoff bias is documented, not corrected.**
- **The master limiter lookahead is master-only.**
- **The reverb `size` bounded to normalized 0..1 (authored 0..10) is deliberate** (maintainer,
  2026-09-16). Normalized 1.0 is comb feedback 0.98; unity sits at about 1.071, and past it the comb
  network has no steady state. The soft-cap alternative was measured (DC rail, AC-RMS 0.0) and
  reverted 2026-08-03. See `Reverb.normalizeSize`.
- **Accepted and intentional, each documented:** triangle aliasing (no PolyBLEP), the hard clip at
  `IgniteRenderer`, the unconditional DC-block on distort, cylinder last-writer-wins,
  `Ducking.attackSeconds`'s misleading name (`docs/tasks/future/audit-parked-decisions.md` §2),
  `rectify()`'s hard clip, the body filter's fixed (non-note-tracking) resonances, `crackle`'s sound
  change, body and vowel at orbit level.
- **`Ignitor` is deliberately NOT a `fun interface`**: SAM turns captured `var`s into JS `ObjectRef`.
- **`PhaserCore.step` must stay `inline`**, its state `internal` (about 25 % JVM / 60 % JS otherwise).
- **`DelayLine`: no per-sample `isFinite`**: it cost about +33 % JVM / +30 % JS, removed 2026-05-22.
- **`Reverb.hasTail()` is not for per-block use** (about 28k samples); `MasterBus` throttles it on
  purpose.
- **`KatalystBodyEffect` / `KatalystFormantEffect` are intentional un-deduped twins**: change one,
  mirror the other.
- **Numerical contract**: `SAFE_MIN 1e-15` / `SAFE_MAX 1e15` (matches SuperCollider `zapgremlins`);
  "safe" means finite, **not** small: consumers must be O(1) regardless of magnitude.
- **Rejected optimisations, do not retry:** `fastCopy` (about 22x slower on JS), ProtoBuf for the
  wire format, snapshot-into-locals as a perf rule, the two SuperSaw *phase* fixes (dead ends,
  reverted by ear; the shipped fix is on the gain axis).
