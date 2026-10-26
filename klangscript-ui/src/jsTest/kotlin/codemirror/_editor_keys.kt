/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codemirror

import io.peekandpoke.klang.codemirror.ext.Command
import io.peekandpoke.klang.codemirror.ext.Extension
import io.peekandpoke.klang.workshop.keys.KeyPlatform
import io.peekandpoke.klang.workshop.keys.Keymap

/** The editor's presets on this browser's platform, as an editor without a key manager reads them. */
val editorPresets: Keymap by lazy { Keymap(platform = KeyPlatform.detect(), actions = KlangEditorKeyBindings.actions) }

/** The editor's keys as [CodeMirrorComp] wires them, running [commands]. */
fun editorKeys(commands: Map<String, List<Command>>): Extension =
    EditorKeyTable.extension(currentKeymap = { editorPresets }, commands = commands)
