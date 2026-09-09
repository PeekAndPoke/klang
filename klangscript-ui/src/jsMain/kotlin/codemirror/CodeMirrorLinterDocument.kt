/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codemirror

import io.peekandpoke.klang.codemirror.ext.Text
import io.peekandpoke.klang.script.intel.LinterDocument

/**
 * [LinterDocument] backed by the live CodeMirror document.
 *
 * The whole adapter: the offset arithmetic it feeds lives in `klangscript` `commonMain`
 * (`AnalyzerDiagnosticOffsets.kt`), where it is plain Kotlin and under test.
 */
internal class CodeMirrorLinterDocument(private val doc: Text) : LinterDocument {
    override val lineCount: Int get() = doc.lines
    override val length: Int get() = doc.length
    override fun lineStart(line: Int): Int = doc.line(line).from
    override fun lineEnd(line: Int): Int = doc.line(line).to
}
