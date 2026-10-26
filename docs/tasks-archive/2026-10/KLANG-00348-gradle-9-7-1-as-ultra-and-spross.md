# KLANG-00348: Gradle 9.7.1, as Ultra and spross

> **Status (2026-10-26): DONE.** Gradle 9.7.1, reviewed in two rounds (the second clean)

## Why this exists

Klang's wrapper is Gradle 8.14. Kotlin's Gradle plugin warns that it is deprecated ("The minimum supported Gradle
version will become Gradle 8.14.4 in Kotlin 2.5.0"). Ultra and spross run Gradle 9.7.1 with Kotlin 2.4.21 on this
machine, so the pair is proven.

## Decide before implementing

- **Who:** Klang-Research or Klang-Main-Dev, coordinated between them (the maintainer). The build is shared by every
  session in the tree: the step runs between other steps, announced, with nobody mid-build.
- **Not a version number only:** every build ends with "Deprecated Gradle features were used in this build, making it
  incompatible with Gradle 9.0". Those uses go first, still on 8.14.
- **Fallback:** 8.14.4 alone silences the warning and changes nothing else, if 9 turns out messy.

## The work

1. On 8.14, one run with `--warning-mode all`: list the deprecated uses (root script, `buildSrc`, the modules, the
   plugins).
2. Fix them, still on 8.14; the build stays green.
3. The wrapper to 9.7.1 (`./gradlew wrapper --gradle-version 9.7.1`, the jar and the scripts with it).
4. Check: every module's JVM and JS tests, the app's dev bundle and its page, the worklet copy, `buildSrc`, the
   per-user build root (MC-00031), the IDE's sync (the maintainer).

## What it took (2026-10-26)

- **On 8.14, `--warning-mode all`** (configuration and a compile of every module, main and test): one deprecated use,
  the root's `versionFile` reading `project` while it runs (an error in Gradle 10). It now reads the project's name,
  version, root and output file while the build is configured.
- **The property delegates** (deprecated in Gradle 9.6, as spross met): `val GROUP: String by project` and
  `VERSION_NAME` in 18 build scripts read with `project.property("...") as String`.
- **The wrapper:** its files belong to the maintainer's user (gerk), so the `wrapper` task could write them but not
  set their mode (MC-00031) and stopped halfway. Its second run, on 9.7.1, wrote the jar, the properties and
  `gradlew`; `gradlew.bat` was copied in from a wrapper 9.7.1 generated in a scratch project. All four files equal
  9.7.1's own and keep their owner and mode.
- **On 9.7.1, `--warning-mode all`:** `by registering` is deprecated (removed in Gradle 10): the root's three tasks use
  `register(...)` now. After that the configuration and the compile of every module carry no Gradle deprecation.
- **sprudel's copyright guard** (`verifyNoVendoredStrudelInProduction`, run by `check`) read `project` while it
  runs too. A run of configuration and compile cannot see that: only `check` runs it (the review, round 1). It now
  takes its directories and the `libs` directory provider while the build is configured; `buildStrudelBundle` its
  script the same way. Lesson: a deprecation hunt runs `check` too, not only a compile.
- **The worklet's bundles:** its development and production compile sync into the same package folder and each
  webpack reads it; 8.14 let that pass, 9.7.1 refuses it ("Property has implicit dependency"). The worklet's script
  orders them: the development sync, its webpack, the production sync, its webpack.
- **Checked on 9.7.1:** every module's JVM and JS tests, 18,501, all green but one spec of Klang-Main-Dev's
  uncommitted key-overview edit (`KlangEditorKeyBindingsSpec`: indent and unindent missing from the grouped overview,
  told to Klang-Main-Dev; not Gradle); the app's dev bundle with its page and the production worklet copied; the
  production bundle with content-hashed names; `check` (its test tasks had just run) and sprudel's guard on their
  own; no Gradle deprecation left in configuration, compile, `check` or the bundles.
- **Not Gradle, left out:** the Kotlin compiler flags `KSNode.validate()` as deprecated in klangscript-ksp and
  audio-wire-codec-ksp (a KSP API change); Klang-Main-Dev takes it after KLANG-00325.

## Review and checks (2026-10-26)

- **Round 1** (level 1): 1 MAJOR (sprudel's guard reading `project` at execution), fixed; 1 MINOR (the worklet's
  ordering covers its webpack tasks, not its own dev servers) accepted with a clearer comment; 1 NIT (an implied
  ordering line) removed.
- **Round 2** (level 2, two phases): clean, 1 MINOR (this record left out sprudel's change) applied.

## Acceptance

- Gradle 9.7.1, no Kotlin plugin warning about the Gradle version, no "incompatible with Gradle 9" line.
- All tests green, the app runs.

## History

- 2026-10-26: created.
- 2026-10-26: coordinated with Klang-Main-Dev: Klang-Research takes it after KLANG-00347; Klang-Main-Dev messages
  when its KLANG-00325 step 3b is committed and starts no build from then until the upgrade is done.
- 2026-10-26: done: Gradle 9.7.1, reviewed in two rounds (the second clean); the build back to Klang-Main-Dev; the
  maintainer reloads the Gradle project in the IDE.
- 2026-10-26: IN PROGRESS. started; the build handed over by Klang-Main-Dev
- 2026-10-26: DONE. Gradle 9.7.1, reviewed in two rounds (the second clean)
