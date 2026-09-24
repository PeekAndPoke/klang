# `.katalyst(k => ...)` and `.master(m => ...)`: a pattern door that takes the value or its configure lambda

> **Written 2026-09-18, not started.** Priority: **NICE** (pure convenience, the maintainer's
> words). Outside the signal-flow work stream on purpose. **Depends on**
> [`klangscript-union-types.md`](klangscript-union-types.md): without a union type the editor
> cannot type the `k` in `.katalyst(k => k.`, and completion inside a builder is most of a
> builder's value, so this task does not start before that one's step 3.

## 1. What the author writes

Today a chain is built and handed over in two words:

```
.katalyst(Katalyst(k => k.classic().eq(e => e.band(freq = 300, q = 0.8, db = 2.0))))
.master(Master(m => m.reverb(0.2, 7)))
```

After this task both spellings work, on every form of the two doors:

```
.katalyst(k => k.classic().eq(e => e.band(freq = 300, q = 0.8, db = 2.0)))
.katalyst(Katalyst(k => k.classic()))          // unchanged
.master(m => m.reverb(0.2, 7))
.master(Master.default())                      // unchanged
```

**Not for `.sound()`** (maintainer, 2026-09-18): it takes pattern-like names, where a lambda
already means a mapper, so the form would be ambiguous there.

## 2. Semantics

- **The lambda is the configure lambda of the object's own door.** `.katalyst(f)` is exactly
  `.katalyst(Katalyst(f))`, `.master(f)` is exactly `.master(Master(f))`. The builder starts where
  that door starts it: EMPTY for `Katalyst` (so `.katalyst(k => k.eq(...))` is an EQ and nothing
  else; `k.classic()` is the explicit word for the historical chain), and whatever
  `Master(...)` starts from for the master. One definition, one place (`Katalyst.build`,
  `Master.build`); the pattern door calls it and adds nothing.
- **The lambda runs ONCE, when the door is built, never per event.** The door stamps one shared
  value instance onto every event, as it does today (step 5a-2: one `KatalystValue.Dsl` per door).
  Per-event evaluation would allocate a chain per event and let an impure lambda mint a new
  content-addressed identity per event, which is a chain swap per event.
- **Anything else is an error that names both forms**: "katalyst(...) takes a Katalyst or a
  configure lambda `k => ...`, got <kind>". Coerce-not-require does not apply: this is a
  construction input, not an audio parameter.
- **Replace semantics are untouched**: the last `.katalyst(...)` on a pattern is the chain.

## 3. Shape in code

- **Script door:** the ONE registered function per door form takes an alias of `Any`,
  `KatalystLike` / `MasterLike`, with its union declared as the union-types task specifies:
  `KatalystDsl | (KatalystBuilder) -> KatalystBuilder`, `MasterDsl | (MasterBuilder) ->
  MasterBuilder`. KlangScript has no overloads, so this is the only way. Under `Any` a script
  lambda arrives as a Kotlin `Function1` (the interop's existing behaviour, the one sprudel's
  `patternMapper` relies on), so the door checks `is KatalystDsl`, `is Function1<*, *>`, else the
  error.
- **Kotlin door:** two TYPED overloads beside it, `katalyst(katalyst: KatalystDsl)` and
  `katalyst(configure: (KatalystBuilder) -> KatalystBuilder)`, so Kotlin callers keep type safety
  and door parity holds (both spellings on both doors). Only the `KatalystLike` one carries the
  `@KlangScript` annotation; Kotlin's overload resolution prefers the typed ones. Verify that KSP's
  collision check (one annotated function per script name and receiver) stays satisfied.
- **All four forms of each door**: the top-level carrier, `SprudelPattern.`, `String.`,
  `PatternMapperFn.` (the form-(d) test of the door parity spec).
- `sprudel` already depends on `klangscript-libs`, so the builders are visible; no module moves.

## 4. Parked for the maintainer

- **§D1, the short form `.kat`.** The register keeps a few short forms on purpose (`comp`, `uni`,
  `vib`, `pamt`), and `katp` already uses the stem. If `kat` joins them it is added on all four
  door forms of `katalyst`, recorded in the rules register beside the others, and the docs use
  ONE spelling in examples (the long one) so the tutorial text stays searchable. No short form
  for `master`: it is short already.

## 5. Tests (light tier: sprudel door; the KSP part is covered by the union-types task)

- `LangKatalystSpec` / the master twin: the lambda form yields the same `uniqueId()` as the
  wrapped form, on all four door forms; one value instance per door across events (identity);
  the lambda is called exactly once for a pattern queried over several cycles (a counting lambda);
  a wrong argument kind raises the error naming both forms; replace semantics with mixed forms
  (`.katalyst(A).katalyst(k => ...)` yields the lambda's chain).
- Door parity spec: both spellings on both doors.
- Editor: inside `.katalyst(k => k.` the builder's knobs complete (this is the union-types task's
  acceptance, asserted here for these two aliases).
- Mutations: evaluate the lambda per event (identity row red); start the builder from classic
  (the `uniqueId` row red).

## 6. Docs

KDoc on the two doors shows both spellings, the lambda form first (it is what people will
write). The `Katalyst` and `Master` objects keep their KDoc; the tutorial and Lexikon sweep of the
signal-flow plan's phase 5 picks the lambda form for new text.

## Links

- [`klangscript-union-types.md`](klangscript-union-types.md), the dependency.
- [`katalyst-dsl.md`](katalyst-dsl.md) §6 (the application path, replace semantics),
  [`master-dsl-followups.md`](master-dsl-followups.md).
- `/dsl-design` §2 (door shape: construction inputs on the door, `configure` last) and §3 (two
  doors, one DSL).
