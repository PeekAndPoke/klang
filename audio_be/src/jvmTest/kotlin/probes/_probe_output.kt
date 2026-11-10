/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import java.io.File

// Where the JVM probe runners write their tables (KLANG-00420). The hand-written JSON beside each table is common code,
// `_probe_json.kt` in `commonTest`, so the Kotlin/JS cost table writes the same JSON.

/**
 * The file a probe runner writes its text table to: `$KLANG_PROBE_OUT` when set (a file path), else [fileName] in
 * `<build dir>/probes/`, the build dir found from where the probe classes were compiled to
 * (`<build dir>/classes/kotlin/jvm/test/`), so the agents' `.build-claude/` and the maintainer's `build/` both work.
 */
fun probeOutputFile(fileName: String): File {
    System.getenv("KLANG_PROBE_OUT")?.let { return File(it) }

    val classes = File(ProbeStage::class.java.protectionDomain.codeSource.location.toURI())
    val buildDir = classes.parentFile?.parentFile?.parentFile?.parentFile ?: File(System.getProperty("java.io.tmpdir"))

    return File(buildDir, "probes/$fileName")
}

/** The JSON file next to a text table: `stage-probe.txt` becomes `stage-probe.json`; a name without `.txt` gets `.json` appended. */
fun jsonTwin(text: File): File {
    val name = text.name
    val base = if (name.endsWith(".txt")) name.dropLast(4) else name

    return File(text.parentFile, "$base.json")
}

/** Writes [text] to [target] (creating its folder) and says where it went. */
fun writeProbeFile(target: File, text: String, what: String) {
    target.parentFile?.mkdirs()
    target.writeText(text)
    println("$what written to ${target.absolutePath}")
}
