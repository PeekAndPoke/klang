# Build Version Info — extract git metadata to `version.json` and surface it in the frontend

## Goal

At build time, extract version/git metadata and write it to a file the frontend can read, so
the running app can display which build it is. The required output format is **exactly**:

```json
{
  "project": "funktor-demo-server",
  "version": "unspecified",
  "gitBranch": "vault-other-dbs",
  "gitRev": "d5fb5b29",
  "gitDesc": "v0.94.2-21-gd5fb5b29",
  "date": "2025-10-26 17:18:55"
}
```

(For klang this resolves to `project: "klang-engine"`, `version: "0.1.0"` — from
`settings.gradle.kts` `rootProject.name` and `gradle.properties` `VERSION_NAME` — plus the live git
fields.)

This is a known, solved problem in the sibling repo **`ultra/funktor`** — we should mirror it rather
than invent something. The format above is byte-for-byte what funktor produces.

## Status — IMPLEMENTED (2026-06-24)

Built and compiling (`./gradlew :compileKotlinJs` green; `:versionFile` produces the exact format).
Files changed:

- `build.gradle.kts` — `versionFile` task (writes `src/jsMain/resources/version.json`), wired as a
  `dependsOn` of `jsProcessResources`.
- `.gitignore` — ignores the generated `src/jsMain/resources/version.json`.
- `webpack.config.d/02-html-config.js` — reads `version.json`, passes it as the `appVersion` template param.
- `src/jsMain/resources/index_template.html` — bakes `window.__APP_VERSION__` at the CONFIG slot.
- `src/commonMain/kotlin/AppVersion.kt` — model (`project/version/gitBranch/gitRev/gitDesc/date`, `isAvailable`,
  `describe()`).
- `src/jsMain/kotlin/utils/VersionController.kt` — `Stream<AppVersion>` global (injected-global → fetch fallback).
- `src/jsMain/kotlin/index.kt` — `val version = VersionController()`.
- `src/jsMain/kotlin/pages/StartPage.kt` — bottom-left corner stamp (`subscribingTo(version)`).
- `src/jsMain/kotlin/comp/Motoer.kt` — native `title` tooltip on "KLANG AUDIO MOTÖR" (`subscribingTo(version)`).

**Divergences from the plan below (all deliberate):**

1. **Shell-git, not Grgit (option (b), not (a)).** Avoids a buildSrc-wide dependency. `git describe
   --tags --always` falls back to the short hash when there are no tags, so the no-tag case is handled
   without grgit's edge-case throwing. Every git call is guarded → `"n/a"` on failure.
2. **`AppVersion` is a plain data class, not `@Serializable`.** The root `klang-engine` module declares the
   kotlinx-serialization plugin `apply false` (`build.gradle.kts:13`), so `@Serializable` wouldn't be
   processed here. The JS side parses via `dynamic` / `JSON.parse` instead — no plugin change, no kotlinx
   decode needed.
3. **Motoer reveal = native HTML `title` only** (no floating panel / `SemanticUiPopupComponent`), per
   direction: keep it markup-free.

## Reference implementation (funktor — copy this)

Funktor generates `version.json` from a Gradle task backed by the **Grgit** library
(`org.ajoberstar.grgit:grgit-core`). Source:
`/opt/dev/peekandpoke/ultra/buildSrc/src/main/kotlin/Deps.kt` →

```kotlin
import org.ajoberstar.grgit.Grgit
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun Project.createVersionFile(
    vararg outputDirs: String = arrayOf("./src/main/resources", "./tmp"),
) {
    tasks.register("versionFile") {
        doLast {
            val git = Grgit.open { dir = rootProject.projectDir }
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

            val content = """{
                "project": "${project.path.removePrefix(":").replace(":", "-")}",
                "version": "${project.version}",
                "gitBranch": "${git.branch.current().name}",
                "gitRev": "${git.head().id.take(8)}",
                "gitDesc": "${git.describe { tags = true }}",
                "date": "$now"
            }""".trimIndent()

            outputDirs.forEach { dir ->
                File(projectDir, dir).apply { mkdirs() }.also { File(it, "version.json").writeText(content) }
            }
        }
    }
}
```

Buildscript dep (funktor `buildSrc/build.gradle.kts`): `implementation("org.ajoberstar.grgit:grgit-core:5.3.3")`.

The consumer side in funktor is the data class
`io.peekandpoke.funktor.core.model.AppVersion` (`@Serializable`, identical field names) + an
`AppVersion.fromResource("version.json")` loader. We don't need that JVM-classpath loader for the
**JS frontend**, but the data class is a good shape to copy if we want a typed model on the Kotlin/JS
side too.

## Plan for klang

### 1. Generate `version.json` at build time

klang's `buildSrc` does **not** yet depend on Grgit (`buildSrc/build.gradle.kts` only has the Kotlin
gradle plugin + `gradleApi()`), and `buildSrc/src/main/java/Deps.kt` has no version task. Two options:

- **(a) Grgit (matches funktor exactly).** Add `implementation("org.ajoberstar.grgit:grgit-core:5.3.3")`
  to `buildSrc/build.gradle.kts`, port `createVersionFile()` into klang's `Deps.kt`. Pros: identical to
  the proven funktor code, no shelling out. Cons: extra buildSrc dependency.
- **(b) Shell `git`.** Run `git rev-parse`, `git rev-parse --abbrev-ref HEAD`, `git describe --tags` via
  `ProcessBuilder` in a small Gradle task. Pros: zero new deps. Cons: re-derives what Grgit already does;
  must handle the not-a-git-checkout / shallow-clone / CI-detached-HEAD cases ourselves.

**Recommend (a)** for parity with funktor and to avoid edge-case handling. Keep a safe fallback so a
non-git build (e.g. a source tarball) still produces a valid file with `"n/a"` fields rather than failing.

Output target for the JS app: write `version.json` into **`src/jsMain/resources/`** so it flows through
`jsProcessResources` into the webpack dist and is served at **`/version.json`**. (funktor's default
`./src/main/resources` is the JVM equivalent.) Gitignore the generated file.

### 2. Wire it into the build graph

The `versionFile` task must run **before** the resources are processed / webpack runs, and must
regenerate on every build (it's cheap and git state changes). In klang's root `build.gradle.kts`:

```kotlin
createVersionFile(outputDirs = arrayOf("./src/jsMain/resources"))   // or also "./build/version" for webpack

tasks.named<ProcessResources>("jsProcessResources") {
    dependsOn("versionFile")
    outputs.upToDateWhen { false }   // already set today for the worklet copy
}
```

(If we also want it on the JVM CLI side, add `"./src/jvmMain/resources"` to the output dirs.)

## The webpack question — "can we inject the file into `index_template.html`?"

**Yes.** The HTML is already produced by `HtmlWebpackPlugin`, which supports EJS/lodash
`templateParameters`. We're already using that mechanism today:

- `webpack.config.d/02-html-config.js` passes `templateParameters: { buildHash }`.
- `src/jsMain/resources/index_template.html` consumes it: `…/klang.css?v=<%= buildHash %>`.

So injecting the version is the **same trick, one more parameter** — read `version.json` at
webpack-config-eval time and hand it to the template.

### Webpack side (`webpack.config.d/02-html-config.js`)

```js
const fs = require('fs');

// Read the generated version.json (produced by the gradle `versionFile` task).
// Fall back to an empty object so dev builds don't break if it's missing yet.
let appVersion = {};
try {
    appVersion = JSON.parse(
        fs.readFileSync(path.resolve(__dirname, '../../../../src/jsMain/resources/version.json'), 'utf8')
    );
} catch (e) { /* version.json not generated yet — leave empty */
}

config.plugins.push(new HtmlWebpackPlugin({
    template: path.resolve(__dirname, '../../../../src/jsMain/resources/index_template.html'),
    inject: 'body',
    filename: 'index.html',
    publicPath: '/',
    templateParameters: {buildHash, appVersion},
}));
```

### Template side (`index_template.html`)

There's already a natural injection slot: `<!-- PLACEHOLDER::CONFIG -->`. Replace/augment it with either
a global the Kotlin/JS code can read synchronously, or a meta tag:

```html
<!-- exposes the build info to the app with no extra network request -->
<script>window.__APP_VERSION__ = <%= JSON.stringify(appVersion) % >;</script>
<!-- or, human-inspectable in devtools: -->
<meta name="app-version" content="<%= appVersion.gitDesc || '' %>">
```

The Kotlin/JS frontend then reads `window.asDynamic().__APP_VERSION__` (or queries the meta tag) and
renders it in an About/footer/diagnostics surface.

### Caveats / ordering

1. **The file must exist when webpack evaluates its config.** webpack config runs *inside* the
   `jsBrowser…Webpack` Gradle task, so the `versionFile` task must be an ancestor of it (via the
   `jsProcessResources` → webpack dependency chain). The `try/catch` fallback keeps the very first
   cold build (or an IDE that runs webpack standalone) from crashing.
2. **Two consumption paths, pick per use:**
    - *Build-time inject (this question):* baked into `index.html`, available synchronously, no request.
      Best for an always-present version stamp.
    - *Runtime fetch:* the same `version.json` is also served at `/version.json`, so the app could just
      `fetch('/version.json')`. Simpler, no webpack edit, but adds a request and is async. Either works;
      they're not mutually exclusive (one task feeds both).
3. **Cache-busting:** the injected copy rides the `index.html` (which is non-cached/regenerated), so it's
   always fresh. A fetched `/version.json` should be requested with a cache-buster or `no-store`.

## Frontend consumption — global state published as a `Stream` (LOCKED)

The version data must live in **global state, published through a `Stream`**, so any component can
subscribe and redraw when it arrives. This mirrors the existing `FullscreenController`
(`src/jsMain/kotlin/utils/FullscreenController.kt`), which is the canonical pattern in this app:
a class that `implements Stream<T>`, holds a private `StreamSource<T>`, exposes `invoke()` +
`subscribeToStream()`, and is instantiated **once** in `index.kt` (`val fs = FullscreenController()`).

### 1. Typed model — `AppVersion` (commonMain)

Mirror funktor's data class so the field names line up 1:1 with `version.json`. Put it in `commonMain`
(serializable; usable from both JS and a future JVM `--version`):

```kotlin
@Serializable
data class AppVersion(
    val project: String = N_A,
    val version: String = N_A,
    val gitBranch: String = N_A,
    val gitRev: String = N_A,
    val gitDesc: String = N_A,
    val date: String? = null,
) {
    companion object {
        private const val N_A = "n/a"
    }

    val isAvailable get() = gitBranch != N_A
    fun describe() = listOfNotNull(gitDesc.takeIf { it != N_A }, version.takeIf { it != N_A }).firstOrNull() ?: "dev"
}
```

### 2. `VersionController : Stream<AppVersion>` (jsMain) — loads **independent of origin**

Same shape as `FullscreenController`. The controller's job is to load the data **regardless of where it
comes from** and publish it once available. Load order (first hit wins, both feed the same source):

1. **Webpack-injected global** `window.__APP_VERSION__` — present synchronously if the
   `HtmlWebpackPlugin` injection (above) ran. Decode it and push immediately.
2. **Fallback fetch** `/version.json` — if the global is absent/empty (e.g. dev server, IDE webpack
   standalone), `fetch()` it and push when it resolves.

```kotlin
class VersionController : Stream<AppVersion> {
    private val source = StreamSource(AppVersion())   // initial = all-"n/a"; UI shows nothing until loaded

    override fun invoke(): AppVersion = source()
    override fun subscribeToStream(sub: (AppVersion) -> Unit): Unsubscribe = source.subscribeToStream(sub)

    init {
        // 1) Build-time injected global (synchronous, no request)
        val injected = window.asDynamic().__APP_VERSION__
        val fromGlobal =
            injected?.let { runCatching { Json.decodeFromString<AppVersion>(JSON.stringify(it)) }.getOrNull() }

        if (fromGlobal?.isAvailable == true) {
            source(fromGlobal)
        } else {
            // 2) Runtime fetch fallback
            launch {
                runCatching {
                    val txt = window.fetch("/version.json").await().text().await()
                    Json.decodeFromString<AppVersion>(txt)
                }.getOrNull()?.let { source(it) }
            }
        }
    }
}
```

Instantiate once next to `fs` in `index.kt`:

```kotlin
val fs = FullscreenController()
val version = VersionController()      // <-- new global
```

(Notes: `StreamSource(initial)` is the factory; `source(next)` publishes; `Stream` requires `invoke()`

+ `subscribeToStream()`. "Independent of origin" = the injected-global path and the fetch path both end
  in the *same* `source(...)` push, so consumers don't care which fired. `source.invoke(block)` already
  no-ops on unchanged values, so a double-load is harmless.)

### 3. Subscribing from components — `subscribingTo`

Kraft components consume a `Stream` with `subscribingTo(stream)` (see
`FullscreenToggleButton.kt:46` → `private val state by subscribingTo(props.fs)`). It returns a property
holding the current value and **redraws the component on every new value** — so the async load "just
works": the component first renders with the `n/a` placeholder, then redraws once the data lands.

```kotlin
private val info by subscribingTo(version)   // `version` = the global from index.kt
// ... in render(): if (info.isAvailable) { +info.describe() }
```

### 4. Display surface A — StartPage corner

Show a small, low-opacity version stamp in a **bottom corner** of `StartPage`
(`src/jsMain/kotlin/pages/StartPage.kt`). The page already uses absolute-positioned overlays
(`position = absolute; bottom = …`), so add one more `div` pinned `bottom`/`left` (or `right`) with
`pointerEvents = none`, `opacity ≈ 0.4`, monospace, showing `info.describe()` (e.g. `v0.1.0-21-gd5fb5b29`)
or `gitRev`. Render nothing while `!info.isAvailable`.

### 5. Display surface B — `Motoer` hover

In `comp/Motoer.kt`, the `"KLANG AUDIO MOTÖR"` title (`Motoer.kt:115-147`) should reveal the **full**
build info on hover. Two ways, pick one:

- **Lightweight (matches FullscreenController-style state):** add `onMouseEnter`/`onMouseLeave` on the
  title `div` toggling a local `showVersion` state, and conditionally render a small floating panel
  listing `project / version / gitBranch / gitRev / gitDesc / date`. Note (from ultra-libs): `onMouseEnter`
  /`onMouseLeave` **do not bubble** — correct here, we only want the title itself.
- **Idiomatic tooltip:** use Kraft's `SemanticUiPopupComponent` (semanticui module) to attach a popup to
  the title with the same details. Cleaner, no manual hover state.

Either way the panel reads from the same `subscribingTo(version)` property — the `Motoer` component
subscribes, so it redraws when the data loads.

## Acceptance

- [x] `version.json` is generated on every JS build with the exact field set/format above and real git
  values (branch, 8-char rev, `git describe --tags --always`, timestamp).
- [x] File is gitignored and lands in the webpack dist (served at `/version.json`).
- [x] `index.html` carries the version (`window.__APP_VERSION__` global) via `HtmlWebpackPlugin` template params.
- [x] `AppVersion` model exists in `commonMain` (plain data class — funktor-identical fields; see divergence #2).
- [x] `VersionController : Stream<AppVersion>` loads the data **independent of origin** (injected global →
  fetch fallback) and is instantiated once in `index.kt` next to `fs`.
- [x] StartPage shows a subtle version stamp in the bottom-left corner (renders nothing until loaded).
- [x] Hovering "KLANG AUDIO MOTÖR" in `Motoer` reveals the full build info (native `title` tooltip).
- [x] Both surfaces consume the data via `subscribingTo(version)` and redraw when it arrives.
- [x] Non-git / fresh-checkout builds degrade gracefully (`"n/a"` fields, no build failure).
- [ ] **By-eye check in browser** still pending (corner stamp position/opacity, tooltip contents).

## Open questions

- StartPage corner: **left or right**? (bottom-left keeps clear of the fullscreen toggle if that sits
  bottom-right — confirm.)
- Motoer hover: lightweight `onMouseEnter`/`onMouseLeave` panel vs. `SemanticUiPopupComponent` tooltip?
- Should the JVM CLI (`runCli`) also embed `version.json` for a `--version` flag? (cheap add: extra output dir.)
