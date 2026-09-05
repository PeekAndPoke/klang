# Split `klangscript` into language core and `klangscript-libs`

> Status: **DONE 2026-09-06** (L1 to L6 green; see the notes under each step). Branch `dsl-adjustments`.
> Prerequisite for step S2 of [`dsl-configure-lambdas.md`](dsl-configure-lambdas.md) (the builders).
> Each step ends green and is committed on its own.

## Why

`klangscript` today mixes two things: the language (parser, interpreter, native interop, editor
analyzer, docs registry) and the script-facing standard library (`Osc`, `Master`, `Pipeline`,
`Math`, `Object`, `console`, the String/Array/Number/Boolean extensions). Because the stdlib
builds `IgnitorDsl` trees, the language module depends on `audio_bridge`. That dependency is the
wrong way round for the builder work: the builders must be usable from Kotlin AND from script,
their script registration is KSP-generated code that needs the language runtime, and
`audio_bridge` (the dependency root of the audio stack, part of the worklet bundle) can never
depend on the language runtime. The only way to have builders in `audio_bridge` was a
cross-module generated-source arrangement, which the maintainer rejected on principle
(`CLAUDE.md`, "Complexity is the enemy").

The cut the maintainer asked for (2026-09-06): **`klangscript` is language and runtime only.**
Everything script-facing moves to a new module `klangscript-libs`, under the single library
name `"stdlib"` (scripts keep `import * from "stdlib"`). This mirrors `sprudel`, the module the
project holds up as the model: one module, both doors, depending on `klangscript` and
`audio_bridge`, KSP running in it like everywhere else.

## Target layout

```
klangscript            language + runtime: ast/ builder/ docs/ intel/ parser/ runtime/ types/,
                       KlangScriptEngine, KlangScriptLibrary, klangScriptEngine() (bare engine)
                       deps: common, klangscript-annotations          (audio_bridge REMOVED, KSP REMOVED)
klangscript-libs       io.peekandpoke.klang.script.stdlib.* (all 35 files + PlatformConsole jvm/js),
                       stdlibLib, klangScript() (engine WITH stdlib), the coming builders
                       deps: klangscript, audio_bridge, klangscript-annotations; KSP: klangscript-ksp
sprudel                deps: + klangscript-libs (uses IgnitorDslLike/toIgnitorDsl, klangScript() in tests)
klangscript-ui         deps: + klangscript-libs (KlangStdLib, ConsoleLevel)
root app               gets klangscript-libs transitively through sprudel (api)
klang, klang-notebook, klangui   keep api(klangscript); they use no stdlib symbols
```

Packages do not change (`io.peekandpoke.klang.script.stdlib` stays), so imports in dependents
stay valid; only Gradle dependencies move. The engine factory `klangScript()` keeps its name,
package and behaviour (engine with stdlib) and moves to `klangscript-libs`; the core gains
`klangScriptEngine()` (bare engine, no libraries) for its own tests and for hosts that assemble
libraries themselves.

## Facts gathered 2026-09-06

- Core `commonMain` outside `stdlib/` has NO `audio_bridge` import and NO `@KlangScript`
  annotation (two KDoc mentions only). Platform stdlib code: `jvmMain/stdlib/PlatformConsole.kt`,
  `jsMain/stdlib/PlatformConsole.kt`.
- Tests: 96 files. 32 reference stdlib symbols by grep (`"stdlib"`, `Osc.`, `Math.`,
  `generatedStdlibDocs`, `.length()`, ...); the rest may still use extension methods the grep
  missed, so the real split is decided by running the core suite after the move.
  `GeneratedRegistrationTest` and `CompletionProviderTest` import `generatedStdlibDocs`: libs.
  No test DEFINES annotated symbols, so the core needs no KSP on test source sets either.
- External call sites of `klangScript()` / stdlib types: sprudel tests (4 files), app `src/jsMain`
  (`player.kt`, `PlayableCodeExample.kt`, `KlangScriptReplComp.kt`, `KlangScriptLibraryDocsPage.kt`),
  app `src/jvmTest` (`TutorialCurriculumSpec`, `DslDocExamplesSpec`), `klangscript-ui`
  (`KlangStdLib`, `ConsoleLevel`). Nothing in `klang/`, `klang-notebook/`, `klangui/`.
- Gradle: `sprudel/build.gradle.kts` is the template (KSP `kspCommonMainMetadata`, generated
  srcDir, compilation tasks depend on the KSP task).

## Steps

| Step | What | Verify |
|------|------|--------|
| L1 ✅ | New module `klangscript-libs` (settings entry, build file cloned from sprudel's shape: KMP jvm+js, KSP, deps `api(klangscript)`, `api(audio_bridge)`), empty source sets. | `./gradlew :klangscript-libs:compileKotlinJvm` |
| L2 ✅ | `git mv` the 35 `stdlib/` files + the two `PlatformConsole.kt` into `klangscript-libs`; move `stdlibLib` and `klangScript()` there (`index_libs.kt`, package `io.peekandpoke.klang.script`); core keeps `getUniqueClassName` and gains `klangScriptEngine()`; core build drops `audio_bridge` and all KSP wiring. | `./gradlew :klangscript:compileKotlinJvm :klangscript-libs:compileKotlinJvm` |
| L3 ✅ | Tests: `git mv` the stdlib-dependent tests (incl. `docs/` and `intel/` ones using `generatedStdlibDocs`, `GeneratedRegistrationTest`) to `klangscript-libs/src/{commonTest,jvmTest}`; in the remaining core tests replace `klangScript(` with `klangScriptEngine(`; run the core suite, move whatever still fails for a missing stdlib symbol. | `./gradlew :klangscript:jvmTest :klangscript-libs:jvmTest`, then `:klangscript:jsTest :klangscript-libs:jsTest` |
| L4 ✅ | Dependents: `sprudel` and `klangscript-ui` add `api(project(":klangscript-libs"))`; root app compiles through sprudel. | `./gradlew :sprudel:jvmTest :klangscript-ui:compileKotlinJs :compileKotlinJs` and the two app jvm specs |
| L5 ✅ | Docs: `klangscript/CLAUDE.md` + `MEMORY.md` + `ref/*` (paths, "stdlib is a separate module"), new `klangscript-libs/CLAUDE.md` (dispatcher: what lives here, how to add a door, KSP), `.claude/skills/klangscript-knowhow` and `dsl-design` (builders live in `klangscript-libs`, the Kotlin door is that module), `README.MD` module map, `dsl-configure-lambdas.md` (S1b replaced by this split; builder location). | read-through |
| L6 ✅ | Full verification and commit. Worklet bundle untouched by construction (nothing below `klangscript` changed), so no bundle measurement needed. | `./gradlew :klangscript:jvmTest :klangscript-libs:jvmTest :sprudel:jvmTest :klangscript:jsTest :klangscript-libs:jsTest` |

## Non-goals

- No package renames, no API changes to the doors, no behaviour change in any script.
- No new processor options, no shared generated directories, no Gradle tricks.
- The builders themselves are S2 of `dsl-configure-lambdas.md`, after this lands.

## Notes from execution (2026-09-06)

- The kotest Gradle plugin requires the KSP *plugin* to be applied, so `:klangscript` keeps
  `id("com.google.devtools.ksp")` but runs no symbol processor (no `kspCommonMainMetadata`
  dependency, no generated srcDir).
- KSP-generated `@Object` registrations call `NativeObjectExtensionsBuilder.builder` and `.cls`,
  which were `@PublishedApi internal`; they are public now because the generated code lives in
  another module.
- Kotlin cannot smart-cast a property declared in another module: two moved tests needed explicit
  casts (`ExpressionTypeInferrerE2eTest`, `StdlibDocsInferenceTest`).
- Stale `klangscript/build/generated/ksp` output from before the split had to be deleted once.
- The core suite (64 test files) passed against the bare engine on the first run; every test that
  needed the stdlib had been identified by the grep and moved (33 files).
