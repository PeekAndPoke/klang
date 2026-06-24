# jsbridge — Strudel JS-compat test oracle (build input)

This directory builds a bundled copy of **Strudel** (<https://codeberg.org/uzu/strudel>) so the
Kotlin engine can be diff-tested against real Strudel.

- `build.sh` bundles `strudel-entry.mjs` (which imports the `@strudel/*` packages from
  `node_modules`) with esbuild into `../src/jvmTest/resources/strudel-bundle.mjs`.
- That bundle is a **generated, git-ignored, TEST-ONLY artifact**. It is built on the fly by the
  Gradle `buildStrudelBundle` task (wired to `jvmTestProcessResources`) and is loaded **only** by
  the JS-compatibility differential-test oracle in `src/jvmTest/kotlin/graal`
  (`GraalSprudelCompiler`, run from `JsCompatTests`).
- It is **never** part of any Klang production artifact: the GraalVM dependency and the oracle live
  in the `jvmTest` source set only, and the bundle is not committed.

## License / attribution

Strudel and the `@strudel/*` packages are licensed under the **GNU AGPL-3.0**. The generated bundle
is a verbatim build of that AGPL code; their copyright and license notices live with the packages in
`node_modules`. Klang itself is also AGPL-3.0, so this test-only usage is license-compatible. If the
oracle is ever removed, this directory and the GraalVM test dependency can be deleted with it.
