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

## Acceptance

- [ ] `version.json` is generated on every JS build with the exact field set/format above and real git
  values (branch, 8-char rev, `git describe --tags`, timestamp).
- [ ] File is gitignored and lands in the webpack dist (served at `/version.json`).
- [ ] `index.html` carries the version (global and/or meta tag) via `HtmlWebpackPlugin` template params.
- [ ] Frontend renders the build info somewhere (About / footer / diagnostics).
- [ ] Non-git / fresh-checkout builds degrade gracefully (`"n/a"` fields, no build failure).

## Open questions

- Where in the UI does the version belong? (footer, About modal, a `console.info` banner, all of the above?)
- Do we also want the typed Kotlin model (`AppVersion` à la funktor) on the JS side, or is the raw
  `window.__APP_VERSION__` object enough for now?
- Should the JVM CLI (`runCli`) also embed `version.json` for a `--version` flag? (cheap add: extra output dir.)
