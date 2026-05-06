package io.peekandpoke.klang.ui.comp

import io.peekandpoke.kraft.addons.marked.marked
import io.peekandpoke.kraft.addons.registry.AddonRegistry.Companion.addons
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.utils.SimpleAsyncQueue
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.key
import kotlinx.coroutines.delay
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.unsafe
import kotlin.time.Duration.Companion.milliseconds

@Suppress("FunctionName")
fun Tag.MarkdownDisplay(
    markdown: String,
    key: String = "md-" + markdown.hashCode().toString(),
) = comp(
    MarkdownDisplay.Props(markdown = markdown, key = key)
) {
    MarkdownDisplay(it)
}

class MarkdownDisplay(ctx: Ctx<Props>) : Component<MarkdownDisplay.Props>(ctx) {

    companion object {
        val cache = mutableMapOf<String, String>()
    }

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val markdown: String,
        val key: String,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val marked by subscribingTo(addons.marked) {
        q.add {
            delay(1)
            updateMd(props.markdown)
        }
    }

    private var md: String? by value(null)
    private val q: SimpleAsyncQueue = SimpleAsyncQueue()

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    init {
        lifecycle {
            onNextProps { new, _ ->
                q.add {
                    delay(1.milliseconds)
                    updateMd(new.markdown)
                }
            }
        }
    }

    private fun updateMd(markdown: String) {
        val m = marked ?: return

        md = try {
            cache.getOrPut(markdown) {
                m.markdown2html(markdown)
            }
        } catch (e: Exception) {
            console.warn("Error rendering markdown:", e)
            markdown
        }
    }

    override fun VDom.render() {
        div {
            key = props.key
            md?.let {
                unsafe { +it }
            }
        }
    }
}
