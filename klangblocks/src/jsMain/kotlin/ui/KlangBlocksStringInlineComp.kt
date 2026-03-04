package io.peekandpoke.klang.blocks.ui

import kotlinx.html.Tag

/** Thin wrapper — delegates to [KlangBlocksStringEditorComp]. */
@Suppress("FunctionName")
fun Tag.KlangBlocksStringInlineComp(
    value: String,
    ctx: KlangBlocksCtx,
    onCommit: (String) -> Unit,
) = KlangBlocksStringEditorComp(value = value, ctx = ctx, onCommit = onCommit)
