# Common

Things that are really common. F.e.:

- math utilities
- `infra/`: small multiplatform types with a dedicated implementation per platform (`expect` in `commonMain`, the
  `actual`s in `jsMain` and `jvmMain`): `KlangLock`, and `KlangMap`, a `String`-keyed map over a native JS `Map`
  on Kotlin/JS (where a `HashMap` lookup allocates 40 bytes on V8) and a `LinkedHashMap` on the JVM. It is not a
  Kotlin `Map` (maintainer, 2026-10-11): read it by key, walk it with `forEachEntry`; `toMap()` and `toKlangMap()`
  convert for tests and tools
