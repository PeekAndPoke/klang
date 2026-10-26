# KLANG-00348: Gradle 9.7.1, as Ultra and spross

> **Status (2026-10-26): SCHEDULED.** The maintainer: "Should we update gradle to i think 9.7.1 the max supported
> version for Kotlin 2.4.21"; "You or main, does not matter but coordinate please"

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

## Acceptance

- Gradle 9.7.1, no Kotlin plugin warning about the Gradle version, no "incompatible with Gradle 9" line.
- All tests green, the app runs.

## History

- 2026-10-26: created.
- 2026-10-26: coordinated with Klang-Main-Dev: Klang-Research takes it after KLANG-00347; Klang-Main-Dev messages
  when its KLANG-00325 step 3b is committed and starts no build from then until the upgrade is done.
