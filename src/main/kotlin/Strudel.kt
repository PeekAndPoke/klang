package io.peekandpoke

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.io.IOAccess
import java.nio.file.Path

class Strudel(private val bundlePath: Path) : AutoCloseable {
    val ctx: Context = Context.newBuilder("js")
        .allowIO(IOAccess.ALL) // needed for filesystem module loading :contentReference[oaicite:4]{index=4}
        .option("js.esm-eval-returns-exports", "true") // return module namespace :contentReference[oaicite:5]{index=5}
        .build()

    val source = Source.newBuilder("js", bundlePath.toFile())
        .mimeType("application/javascript+module")
        .build()

    val exports = ctx.eval(source)           // module namespace object :contentReference[oaicite:6]{index=6}

    val core = exports.getMember("core")
    val mini = exports.getMember("mini")
    val transpiler = exports.getMember("transpiler")

    val compileFn = exports.getMember("compile")
    val queryPatternFn = exports.getMember("queryPattern")

    val prettyFormatFn = exports.getMember("prettyFormat")
    val jsonStringifyFn = ctx.eval(
        "js",
        "(v) => { try { return JSON.stringify(v); } catch(e) { return String(e); } }",
    )

    fun coreHasExecutableMember(name: String): Boolean = core.hasMember(name) && core.getMember(name).canExecute()

    fun compile(code: String): Value =
        compileFn.execute(code)

    fun queryPattern(pattern: Value, from: Double, to: Double): Value =
        queryPatternFn.execute(pattern, from, to)

    fun jsonStringify(value: Value): Value? {
        return jsonStringifyFn.execute(value)
    }

    fun prettyFormat(value: Any?): Value? {
        return prettyFormatFn.execute(value)
    }

    override fun close() = ctx.close()
}
