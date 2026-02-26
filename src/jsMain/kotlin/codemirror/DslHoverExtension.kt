package io.peekandpoke.klang.codemirror

import de.peekandpoke.kraft.utils.jsObject
import io.peekandpoke.klang.codemirror.ext.EditorView
import io.peekandpoke.klang.codemirror.ext.Extension
import io.peekandpoke.klang.codemirror.ext.hoverTooltip
import io.peekandpoke.klang.script.docs.FunctionDoc
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

fun dslHoverTooltipExtension(docProvider: (String) -> FunctionDoc?): Extension {
    val source: (EditorView, Int, Int) -> dynamic = { view, pos, _ ->
        val word = view.state.wordAt(pos)
        if (word == null) null
        else {
            val name = view.state.doc.sliceString(word.from, word.to)
            val doc = docProvider(name)
            if (doc == null) null
            else buildTooltipObject(word.from, word.to, doc)
        }
    }

    val tooltipTheme = EditorView.baseTheme(
        js(
            """({
            ".cm-dsl-tooltip": {
                padding: "6px 10px",
                maxWidth: "80vw",
                fontFamily: "inherit",
                fontSize: "14px",
                lineHeight: "1.4"
            },
            ".cm-dsl-section-title": {
                fontWeight: "bold",
                fontSize: "11px",
                textTransform: "uppercase",
                letterSpacing: "0.05em",
                color: "#888",
                marginTop: "8px",
                marginBottom: "2px"
            },
            ".cm-dsl-sig": {
                marginBottom: "4px"
            },
            ".cm-dsl-sig code": {
                fontFamily: "monospace",
                backgroundColor: "#f0f0f0",
                padding: "1px 4px",
                borderRadius: "3px"
            },
            ".cm-dsl-desc": {
                color: "#555",
                marginTop: "4px"
            },
            ".cm-dsl-sample": {
                fontFamily: "monospace",
                backgroundColor: "#f8f8f8",
                border: "1px solid #ddd",
                borderRadius: "3px",
                padding: "4px 8px",
                marginTop: "6px",
                overflowX: "auto",
                fontSize: "12px"
            }
        })"""
        )
    )

    return arrayOf(
        hoverTooltip(source),
        tooltipTheme,
    ).unsafeCast<Extension>()
}

private fun buildTooltipObject(from: Int, to: Int, doc: FunctionDoc): dynamic {
    val html = buildTooltipHtml(doc)
    return jsObject {
        this.pos = from
        this.end = to
        this.above = true
        this.arrow = true
        this.create = { _: dynamic ->
            val el = document.createElement("div") as HTMLElement
            el.className = "cm-dsl-tooltip"
            el.innerHTML = html
            jsObject<dynamic> { this.dom = el }
        }
    }
}

private fun buildTooltipHtml(doc: FunctionDoc): String {
    return buildString {
        doc.variants.firstOrNull()?.description
            ?.takeIf { it.isNotBlank() }
            ?.let { desc ->
                val firstParagraph = desc.split("\n\n").first().trim()
                append("""<div class="cm-dsl-section-title">Description</div>""")
                append("""<div class="cm-dsl-desc">${escapeHtml(firstParagraph)}</div>""")
            }

        append("""<div class="cm-dsl-section-title">Signatures</div>""")
        doc.variants.forEach { variant ->
            append("""<div class="cm-dsl-sig"><code>${escapeHtml(variant.signature)}</code></div>""")
        }

        doc.variants.firstOrNull { it.samples.isNotEmpty() }?.samples?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { sample ->
                append("""<div class="cm-dsl-section-title">Example</div>""")
                append("""<pre class="cm-dsl-sample"><code>${escapeHtml(sample)}</code></pre>""")
            }
    }
}

private fun escapeHtml(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
