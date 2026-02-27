package io.peekandpoke.klang.codemirror

import de.peekandpoke.kraft.utils.jsObject
import io.peekandpoke.klang.codemirror.ext.EditorView
import io.peekandpoke.klang.codemirror.ext.Extension
import io.peekandpoke.klang.codemirror.ext.hoverTooltip
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangProperty
import io.peekandpoke.klang.script.types.KlangSymbol
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement

fun dslHoverTooltipExtension(
    docProvider: (String) -> KlangSymbol?,
    onNavigate: (doc: KlangSymbol, event: dynamic) -> Unit,
): Extension {
    val source: (EditorView, Int, Int) -> dynamic = { view, pos, _ ->
        val word = view.state.wordAt(pos)
        if (word == null) null
        else {
            val name = view.state.doc.sliceString(word.from, word.to)
            val doc = docProvider(name)
            if (doc == null) null
            else buildTooltipObject(word.from, word.to, doc, onNavigate)
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
            ".cm-dsl-sample-wrapper": {
                position: "relative",
                marginTop: "6px"
            },
            ".cm-dsl-sample": {
                fontFamily: "monospace",
                backgroundColor: "#f8f8f8",
                border: "1px solid #ddd",
                borderRadius: "3px",
                padding: "4px 8px",
                paddingRight: "28px",
                overflowX: "auto",
                fontSize: "12px",
                margin: "0"
            },
            ".cm-dsl-copy-btn": {
                position: "absolute",
                top: "4px",
                right: "4px",
                background: "none",
                border: "none",
                cursor: "pointer",
                color: "#999",
                padding: "2px 4px",
                lineHeight: "1",
                fontSize: "13px"
            },
            ".cm-dsl-copy-btn:hover": {
                color: "#333"
            },
            ".cm-dsl-docs-link": {
                marginTop: "8px",
                paddingTop: "6px",
                borderTop: "1px solid #eee",
                color: "#333",
                cursor: "pointer",
                fontSize: "12px"
            },
            ".cm-dsl-docs-link:hover": {
                color: "#000",
                textDecoration: "underline"
            }
        })"""
        )
    )

    return arrayOf(
        hoverTooltip(source),
        tooltipTheme,
    ).unsafeCast<Extension>()
}

private fun buildTooltipObject(
    from: Int,
    to: Int,
    doc: KlangSymbol,
    onNavigate: (KlangSymbol, dynamic) -> Unit,
): dynamic {
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
            (el.querySelector(".cm-dsl-docs-link") as? HTMLElement)?.onclick = { e: dynamic ->
                onNavigate(doc, e)
                null
            }
            val copyBtns = el.querySelectorAll(".cm-dsl-copy-btn")
            for (i in 0 until copyBtns.length) {
                val btn = copyBtns.item(i) as? HTMLElement ?: continue
                val sample = btn.getAttribute("data-sample") ?: continue
                btn.onclick = { e: dynamic ->
                    e.stopPropagation()
                    window.asDynamic().navigator.clipboard.writeText(sample)
                    btn.innerHTML = """<i class="check icon"></i>"""
                    window.setTimeout({ btn.innerHTML = """<i class="copy icon"></i>""" }, 1500)
                    null
                }
            }
            jsObject<dynamic> { this.dom = el }
        }
    }
}

private fun buildTooltipHtml(doc: KlangSymbol): String {
    return buildString {
        doc.variants.firstOrNull()?.description
            ?.takeIf { it.isNotBlank() }
            ?.let { desc ->
                val firstParagraph = desc.split("\n\n").first().trim()
                append("""<div class="cm-dsl-section-title">Description</div>""")
                append("""<div class="cm-dsl-desc">${escapeHtml(firstParagraph)}</div>""")
            }

        append("""<div class="cm-dsl-section-title">Signatures</div>""")
        doc.variants
            .sortedBy {
                when (it) {
                    is KlangCallable -> it.receiver?.simpleName?.length ?: 0
                    is KlangProperty -> it.owner?.simpleName?.length ?: 0
                }
            }
            .forEach { variant ->
                append("""<div class="cm-dsl-sig"><code>${escapeHtml(variant.signature)}</code></div>""")
            }

        doc.variants.firstOrNull { it.samples.isNotEmpty() }?.samples?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { sample ->
                append("""<div class="cm-dsl-section-title">Example</div>""")
                append("""<div class="cm-dsl-sample-wrapper">""")
                append("""<button class="cm-dsl-copy-btn" data-sample="${escapeHtml(sample)}"><i class="copy icon"></i></button>""")
                append("""<pre class="cm-dsl-sample"><code>${escapeHtml(sample)}</code></pre>""")
                append("""</div>""")
            }

        append("""<div class="cm-dsl-docs-link"><i class="book icon"></i> View docs</div>""")
    }
}

private fun escapeHtml(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
