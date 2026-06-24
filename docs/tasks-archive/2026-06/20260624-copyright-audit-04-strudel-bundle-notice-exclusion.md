# Copyright Audit 04 — `strudel-bundle.mjs`: notice + release exclusion

**Bucket B (verbatim copy) · 🟡 should-fix · compliance + build · NOT a code rewrite**

> **Status: ✅ DONE (2026-06-24).** Chosen approach: make the **build** enforce test-only (stronger
> than a label). Actual changes (supersede the old paths described below):
> - Moved the whole `graal/` oracle (`GraalJsHelpers`/`GraalSprudelCompiler`/`GraalSprudelPattern`)
    > `jvmMain` → **`jvmTest`**, and the generated `strudel-bundle.mjs` (+ `.map`) → `jvmTest/resources`.
> - Moved the GraalVM `polyglot`/`js` deps from `jvmMain` → `jvmTest` in `sprudel/build.gradle.kts`;
    > also removed the now-dead GraalVM deps from the root `:klang` app build. Production jars (both
    > `:sprudel` and `:klang`) no longer carry GraalVM or the bundle. Verified: `:sprudel:compileKotlinJvm`
    > and `:klang:compileKotlinJvm` build with **zero** GraalVM on the production classpath.
> - Bundle is **git-ignored + built on the fly**: `jsbridge/build.sh` outfile → `jvmTest/resources`,
    > `buildStrudelBundle` wired to `jvmTestProcessResources` (`.gitignore` updated). Verified the bundle
    > regenerates into the new location.
> - `JsCompatTests` now **skips when not on GraalVM** (`onGraalVm` vendor check; `graalCompiler` made
    > `lazy` so no GraalVM `Context` is built off-Graal). Verified it skips cleanly on Corretto
    > (BUILD SUCCESSFUL, oracle never constructed).
> - Provenance/AGPL note added at `sprudel/jsbridge/README.md`.
    > Code-review (independent agent): APPROVE; its `:klang` dead-dep finding was applied.

## Context

The repo vendors a **complete, verbatim, minified copy of Strudel** as a JVM-side test/reference oracle. This
is *not* laundered into the Kotlin engine — it is loaded by GraalJS to run real Strudel for differential
testing — but it is literal AGPL code distributed in the repo, and it currently carries **no upstream license
notice**.

- `sprudel/src/jvmMain/resources/strudel-bundle.mjs` (~716 KB) — loaded by
  `sprudel/src/jvmMain/kotlin/graal/GraalSprudelCompiler.kt` (`ctx.eval(source)`).
- `sprudel/jsbridge/node_modules/.pnpm/@strudel+mini@1.2.5/…` — vendored Strudel mini package.
- Confirmed: **not present in the JS production executable** (`build/dist/js/productionExecutable`).

## Two things to fix

### A. AGPL compliance for the current (AGPL) project

Strudel is AGPL; Klang is AGPL — compatible — **but** the bundle must carry Strudel's copyright + AGPL notice
and a pointer to corresponding source.

1. Add a `sprudel/src/jvmMain/resources/strudel-bundle.LICENSE` (or a header block / `NOTICE`) with Strudel's
   copyright line + AGPL-3.0 reference + upstream URL (`https://codeberg.org/uzu/strudel`) and the bundle's
   version.
2. List it in `CREDITS.MD` (Strudel is already credited — add that a *verbatim build* is vendored for testing)
   and optionally in a top-level `THIRD_PARTY_NOTICES`.
3. Same for the vendored `@strudel/mini` under `jsbridge` (it already ships its own header — confirm it is
   intact).

### B. Hard guarantee it never ships in a proprietary artifact

This is the load-bearing item for the commercial goal: the bundle is *all of Strudel*, AGPL.

1. Confirm it is confined to `jvmMain` test/oracle paths and `jsbridge` dev deps (it is) and is **never**
   bundled into a distributable/proprietary build output.
2. Add a build/packaging guard (and a note in the release checklist) asserting `strudel-bundle.mjs` and the
   `@strudel/mini` dep are excluded from any non-AGPL release artifact.
3. Consider moving the oracle to a test-only source set / `testFixtures` so it can't leak into `main`.

## Verification

- `grep -rl "strudel-bundle" sprudel/src` → only `jvmMain` (test/oracle) references.
- Inspect the JS production bundle for Strudel markers (control names like `progNum`, `ccn`, `sysex`) → absent.
- Bundle file has an accompanying license/notice.

## Done when

The vendored Strudel bundle carries a proper AGPL/copyright notice, is credited, and there is an enforced
guarantee (build guard + checklist) that it is excluded from any proprietary release.
