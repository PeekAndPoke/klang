package io.peekandpoke.klang.script.builder

import io.peekandpoke.klang.common.SourceLocation
import io.peekandpoke.klang.script.runtime.ArrayValue
import io.peekandpoke.klang.script.runtime.KlangScriptArgumentError
import io.peekandpoke.klang.script.runtime.NullValue
import io.peekandpoke.klang.script.runtime.ParamSpec
import io.peekandpoke.klang.script.runtime.RuntimeValue
import io.peekandpoke.klang.script.runtime.convertToKotlin
import io.peekandpoke.klang.script.runtime.wrapAsRuntimeValue
import kotlin.reflect.KClass

// ============================================================================
//  FunctionBuilder — Phase 4 native-registration DSL
// ============================================================================
//
// Type-state hierarchy:
//
//   createFunction("name")                          → FunctionBuilder0
//     .withReceiver<T>()                            → FunctionBuilder1<T>      (only on Builder0)
//     .withParam<T>("name")                         → FunctionBuilderN+1<...>
//     .withOptionalParam<T>("name") { default }     → FunctionBuilderN+1<...>
//     .withVararg<T>("name")                        → TerminalVararg<...>       (terminal — only .body)
//     .body { p1, ..., pN -> result }               → registers
//
// Total slot budget: 10 (receiver counts as slot 0).
//
// Modifiers withCallInfo / withEngine are deferred to Phase 4b; only the plain
// .body terminal exists today.
//
// ============================================================================

/**
 * Entry point: start building a native function or method registration.
 *
 * @param name The script-visible name for this callable.
 */
fun KlangScriptExtensionBuilder.createFunction(name: String): FunctionBuilder0 =
    FunctionBuilder0(BuilderCtx(name = name, parent = this))

// ============================================================================
//  Shared context — accumulates state during the chain
// ============================================================================

/**
 * Mutable scratchpad used during chain construction. Becomes immutable once
 * `.body` is called and the closure is registered.
 */
class BuilderCtx internal constructor(
    val name: String,
    private val parent: KlangScriptExtensionBuilder,
) {
    var receiverClass: KClass<*>? = null
        private set

    private val mutableSpecs = mutableListOf<ParamSpec>()
    val specs: List<ParamSpec> get() = mutableSpecs

    /** Total slots used so far: 1 if a receiver is bound, plus one per spec. */
    val slotsUsed: Int get() = (if (receiverClass != null) 1 else 0) + mutableSpecs.size

    fun setReceiver(cls: KClass<*>) {
        check(receiverClass == null) { "$name: receiver already set" }
        check(mutableSpecs.isEmpty()) { "$name: withReceiver must be the first builder call" }
        receiverClass = cls
    }

    fun addSpec(spec: ParamSpec) {
        check(mutableSpecs.none { it.name == spec.name }) {
            "$name: duplicate parameter name '${spec.name}'"
        }
        check(mutableSpecs.none { it.isVararg }) {
            "$name: cannot add parameters after a vararg slot"
        }
        check(slotsUsed < MAX_SLOTS) {
            "$name: too many parameters (max $MAX_SLOTS slots, receiver counts)"
        }
        mutableSpecs.add(spec)
    }

    /**
     * Finalise registration. Routes via [KlangScriptExtensionBuilder.registerExtensionMethodWithSpecs]
     * if a receiver was bound; otherwise via [KlangScriptExtensionBuilder.registerFunctionWithSpecs].
     *
     * @param body The user's typed lambda already wrapped to take a `List<RuntimeValue>`
     *             (positional, aligned with [specs] — vararg slot already an ArrayValue).
     *             Returns a raw Kotlin value; this method wraps via [wrapAsRuntimeValue].
     */
    internal fun registerWithBody(body: (List<RuntimeValue>, Any?, SourceLocation?) -> Any?) {
        val receiver = receiverClass
        if (receiver != null) {
            parent.registerExtensionMethodWithSpecs(receiver, name, specs.toList()) { rcv, args, loc ->
                wrapAsRuntimeValue(body(args, rcv, loc))
            }
        } else {
            parent.registerFunctionWithSpecs(name, specs.toList()) { args, loc ->
                wrapAsRuntimeValue(body(args, null, loc))
            }
        }
    }

    companion object {
        const val MAX_SLOTS = 10
    }
}

// ============================================================================
//  Body-arg conversion helpers
// ============================================================================

private fun convertSlot(fnName: String, spec: ParamSpec, value: RuntimeValue, loc: SourceLocation?): Any? {
    if (value is NullValue) {
        if (!spec.isNullable) {
            throw KlangScriptArgumentError(
                functionName = fnName,
                message = "parameter '${spec.name}' is not nullable but received null",
                location = loc,
            )
        }
        return null
    }
    @Suppress("UNCHECKED_CAST")
    return value.convertToKotlin(spec.kotlinType as KClass<Any>, loc)
}

private fun convertVararg(fnName: String, spec: ParamSpec, value: RuntimeValue, loc: SourceLocation?): List<Any?> {
    val arr = value as? ArrayValue ?: throw KlangScriptArgumentError(
        functionName = fnName,
        message = "vararg parameter '${spec.name}' requires an array; got ${value::class.simpleName}",
        location = loc,
    )
    return arr.elements.map { convertSlot(fnName, spec, it, loc) }
}

/**
 * Read the typed value at body-lambda parameter index [paramIdx]. When the
 * builder has a receiver, [paramIdx] 0 is the receiver (handled separately
 * by each body); script-visible params start at index 1, so we subtract one
 * to find the spec/arg index. When there is no receiver, body params and
 * specs line up.
 */
@Suppress("UNCHECKED_CAST")
private fun <T> slot(ctx: BuilderCtx, args: List<RuntimeValue>, paramIdx: Int, loc: SourceLocation?): T {
    val specIdx = if (ctx.receiverClass != null) paramIdx - 1 else paramIdx
    return convertSlot(ctx.name, ctx.specs[specIdx], args[specIdx], loc) as T
}

/** Vararg counterpart of [slot] — see its docs for the [paramIdx] convention. */
@Suppress("UNCHECKED_CAST")
private fun <T> varargSlot(ctx: BuilderCtx, args: List<RuntimeValue>, paramIdx: Int, loc: SourceLocation?): List<T> {
    val specIdx = if (ctx.receiverClass != null) paramIdx - 1 else paramIdx
    return convertVararg(ctx.name, ctx.specs[specIdx], args[specIdx], loc) as List<T>
}

// ============================================================================
//  FunctionBuilder0 — entry; can declare a receiver
// ============================================================================

class FunctionBuilder0 @PublishedApi internal constructor(@PublishedApi internal val ctx: BuilderCtx) {

    inline fun <reified T : Any> withReceiver(): FunctionBuilder1<T> {
        ctx.setReceiver(T::class)
        return FunctionBuilder1(ctx)
    }

    inline fun <reified T : Any> withParam(name: String): FunctionBuilder1<T> {
        ctx.addSpec(ParamSpec(name = name, kotlinType = T::class))
        return FunctionBuilder1(ctx)
    }

    inline fun <reified T : Any> withOptionalParam(
        name: String,
        noinline default: () -> T,
    ): FunctionBuilder1<T> {
        ctx.addSpec(
            ParamSpec(
                name = name,
                kotlinType = T::class,
                isOptional = true,
                default = { wrapAsRuntimeValue(default()) },
            )
        )
        return FunctionBuilder1(ctx)
    }

    inline fun <reified T : Any> withVararg(name: String): TerminalVararg1<T> {
        ctx.addSpec(ParamSpec(name = name, kotlinType = T::class, isVararg = true))
        return TerminalVararg1(ctx)
    }

    fun body(fn: () -> Any?) {
        // Builder0 has zero specs. The interpreter's legacy passthrough lets
        // any positional args reach the bridge unchecked, so we enforce the
        // zero-arg invariant here — otherwise `answer(99)` would silently call
        // `fn()` and ignore the user's arg.
        val ctxName = ctx.name
        ctx.registerWithBody { args, _, loc ->
            if (args.isNotEmpty()) {
                throw KlangScriptArgumentError(
                    functionName = ctxName,
                    message = "$ctxName expects no arguments but got ${args.size}",
                    expected = 0,
                    actual = args.size,
                    location = loc,
                )
            }
            fn()
        }
    }
}

// ============================================================================
//  FunctionBuilder1<P1> .. FunctionBuilder10<...> — typed positional chain
// ============================================================================

class FunctionBuilder1<P1> @PublishedApi internal constructor(@PublishedApi internal val ctx: BuilderCtx) {
    inline fun <reified T : Any> withParam(name: String): FunctionBuilder2<P1, T> {
        ctx.addSpec(ParamSpec(name = name, kotlinType = T::class))
        return FunctionBuilder2(ctx)
    }

    inline fun <reified T : Any> withOptionalParam(name: String, noinline default: () -> T): FunctionBuilder2<P1, T> {
        ctx.addSpec(ParamSpec(name = name, kotlinType = T::class, isOptional = true, default = { wrapAsRuntimeValue(default()) }))
        return FunctionBuilder2(ctx)
    }

    inline fun <reified T : Any> withVararg(name: String): TerminalVararg2<P1, T> {
        ctx.addSpec(ParamSpec(name = name, kotlinType = T::class, isVararg = true))
        return TerminalVararg2(ctx)
    }

    fun body(fn: (P1) -> Any?) {
        ctx.registerWithBody { args, rcv, loc ->
            @Suppress("UNCHECKED_CAST")
            val p1: P1 = if (ctx.receiverClass != null) rcv as P1 else slot(ctx, args, 0, loc)
            fn(p1)
        }
    }
}

class FunctionBuilder2<P1, P2> @PublishedApi internal constructor(@PublishedApi internal val ctx: BuilderCtx) {
    inline fun <reified T : Any> withParam(name: String): FunctionBuilder3<P1, P2, T> {
        ctx.addSpec(ParamSpec(name = name, kotlinType = T::class))
        return FunctionBuilder3(ctx)
    }

    inline fun <reified T : Any> withOptionalParam(name: String, noinline default: () -> T): FunctionBuilder3<P1, P2, T> {
        ctx.addSpec(ParamSpec(name = name, kotlinType = T::class, isOptional = true, default = { wrapAsRuntimeValue(default()) }))
        return FunctionBuilder3(ctx)
    }

    inline fun <reified T : Any> withVararg(name: String): TerminalVararg3<P1, P2, T> {
        ctx.addSpec(ParamSpec(name = name, kotlinType = T::class, isVararg = true))
        return TerminalVararg3(ctx)
    }

    fun body(fn: (P1, P2) -> Any?) {
        ctx.registerWithBody { args, rcv, loc ->
            @Suppress("UNCHECKED_CAST")
            val p1: P1 = if (ctx.receiverClass != null) rcv as P1 else slot(ctx, args, 0, loc)
            val p2: P2 = slot(ctx, args, 1, loc)
            fn(p1, p2)
        }
    }
}

class FunctionBuilder3<P1, P2, P3> @PublishedApi internal constructor(@PublishedApi internal val ctx: BuilderCtx) {
    inline fun <reified T : Any> withParam(name: String): FunctionBuilder4<P1, P2, P3, T> {
        ctx.addSpec(ParamSpec(name = name, kotlinType = T::class))
        return FunctionBuilder4(ctx)
    }

    inline fun <reified T : Any> withOptionalParam(name: String, noinline default: () -> T): FunctionBuilder4<P1, P2, P3, T> {
        ctx.addSpec(ParamSpec(name = name, kotlinType = T::class, isOptional = true, default = { wrapAsRuntimeValue(default()) }))
        return FunctionBuilder4(ctx)
    }

    inline fun <reified T : Any> withVararg(name: String): TerminalVararg4<P1, P2, P3, T> {
        ctx.addSpec(ParamSpec(name = name, kotlinType = T::class, isVararg = true))
        return TerminalVararg4(ctx)
    }

    fun body(fn: (P1, P2, P3) -> Any?) {
        ctx.registerWithBody { args, rcv, loc ->
            @Suppress("UNCHECKED_CAST")
            val p1: P1 = if (ctx.receiverClass != null) rcv as P1 else slot(ctx, args, 0, loc)
            val p2: P2 = slot(ctx, args, 1, loc)
            val p3: P3 = slot(ctx, args, 2, loc)
            fn(p1, p2, p3)
        }
    }
}

class FunctionBuilder4<P1, P2, P3, P4> @PublishedApi internal constructor(@PublishedApi internal val ctx: BuilderCtx) {
    inline fun <reified T : Any> withParam(name: String): FunctionBuilder5<P1, P2, P3, P4, T> {
        ctx.addSpec(ParamSpec(name = name, kotlinType = T::class))
        return FunctionBuilder5(ctx)
    }

    inline fun <reified T : Any> withOptionalParam(name: String, noinline default: () -> T): FunctionBuilder5<P1, P2, P3, P4, T> {
        ctx.addSpec(ParamSpec(name = name, kotlinType = T::class, isOptional = true, default = { wrapAsRuntimeValue(default()) }))
        return FunctionBuilder5(ctx)
    }

    inline fun <reified T : Any> withVararg(name: String): TerminalVararg5<P1, P2, P3, P4, T> {
        ctx.addSpec(ParamSpec(name = name, kotlinType = T::class, isVararg = true))
        return TerminalVararg5(ctx)
    }

    fun body(fn: (P1, P2, P3, P4) -> Any?) {
        ctx.registerWithBody { args, rcv, loc ->
            @Suppress("UNCHECKED_CAST")
            val p1: P1 = if (ctx.receiverClass != null) rcv as P1 else slot(ctx, args, 0, loc)
            val p2: P2 = slot(ctx, args, 1, loc)
            val p3: P3 = slot(ctx, args, 2, loc)
            val p4: P4 = slot(ctx, args, 3, loc)
            fn(p1, p2, p3, p4)
        }
    }
}

class FunctionBuilder5<P1, P2, P3, P4, P5> @PublishedApi internal constructor(@PublishedApi internal val ctx: BuilderCtx) {
    inline fun <reified T : Any> withParam(name: String): FunctionBuilder6<P1, P2, P3, P4, P5, T> {
        ctx.addSpec(ParamSpec(name = name, kotlinType = T::class))
        return FunctionBuilder6(ctx)
    }

    inline fun <reified T : Any> withOptionalParam(name: String, noinline default: () -> T): FunctionBuilder6<P1, P2, P3, P4, P5, T> {
        ctx.addSpec(ParamSpec(name = name, kotlinType = T::class, isOptional = true, default = { wrapAsRuntimeValue(default()) }))
        return FunctionBuilder6(ctx)
    }

    inline fun <reified T : Any> withVararg(name: String): TerminalVararg6<P1, P2, P3, P4, P5, T> {
        ctx.addSpec(ParamSpec(name = name, kotlinType = T::class, isVararg = true))
        return TerminalVararg6(ctx)
    }

    fun body(fn: (P1, P2, P3, P4, P5) -> Any?) {
        ctx.registerWithBody { args, rcv, loc ->
            @Suppress("UNCHECKED_CAST")
            val p1: P1 = if (ctx.receiverClass != null) rcv as P1 else slot(ctx, args, 0, loc)
            val p2: P2 = slot(ctx, args, 1, loc)
            val p3: P3 = slot(ctx, args, 2, loc)
            val p4: P4 = slot(ctx, args, 3, loc)
            val p5: P5 = slot(ctx, args, 4, loc)
            fn(p1, p2, p3, p4, p5)
        }
    }
}

class FunctionBuilder6<P1, P2, P3, P4, P5, P6> @PublishedApi internal constructor(@PublishedApi internal val ctx: BuilderCtx) {
    inline fun <reified T : Any> withParam(name: String): FunctionBuilder7<P1, P2, P3, P4, P5, P6, T> {
        ctx.addSpec(ParamSpec(name = name, kotlinType = T::class))
        return FunctionBuilder7(ctx)
    }

    inline fun <reified T : Any> withOptionalParam(name: String, noinline default: () -> T): FunctionBuilder7<P1, P2, P3, P4, P5, P6, T> {
        ctx.addSpec(ParamSpec(name = name, kotlinType = T::class, isOptional = true, default = { wrapAsRuntimeValue(default()) }))
        return FunctionBuilder7(ctx)
    }

    inline fun <reified T : Any> withVararg(name: String): TerminalVararg7<P1, P2, P3, P4, P5, P6, T> {
        ctx.addSpec(ParamSpec(name = name, kotlinType = T::class, isVararg = true))
        return TerminalVararg7(ctx)
    }

    fun body(fn: (P1, P2, P3, P4, P5, P6) -> Any?) {
        ctx.registerWithBody { args, rcv, loc ->
            @Suppress("UNCHECKED_CAST")
            val p1: P1 = if (ctx.receiverClass != null) rcv as P1 else slot(ctx, args, 0, loc)
            val p2: P2 = slot(ctx, args, 1, loc)
            val p3: P3 = slot(ctx, args, 2, loc)
            val p4: P4 = slot(ctx, args, 3, loc)
            val p5: P5 = slot(ctx, args, 4, loc)
            val p6: P6 = slot(ctx, args, 5, loc)
            fn(p1, p2, p3, p4, p5, p6)
        }
    }
}

class FunctionBuilder7<P1, P2, P3, P4, P5, P6, P7> @PublishedApi internal constructor(@PublishedApi internal val ctx: BuilderCtx) {
    inline fun <reified T : Any> withParam(name: String): FunctionBuilder8<P1, P2, P3, P4, P5, P6, P7, T> {
        ctx.addSpec(ParamSpec(name = name, kotlinType = T::class))
        return FunctionBuilder8(ctx)
    }

    inline fun <reified T : Any> withOptionalParam(
        name: String,
        noinline default: () -> T
    ): FunctionBuilder8<P1, P2, P3, P4, P5, P6, P7, T> {
        ctx.addSpec(ParamSpec(name = name, kotlinType = T::class, isOptional = true, default = { wrapAsRuntimeValue(default()) }))
        return FunctionBuilder8(ctx)
    }

    inline fun <reified T : Any> withVararg(name: String): TerminalVararg8<P1, P2, P3, P4, P5, P6, P7, T> {
        ctx.addSpec(ParamSpec(name = name, kotlinType = T::class, isVararg = true))
        return TerminalVararg8(ctx)
    }

    fun body(fn: (P1, P2, P3, P4, P5, P6, P7) -> Any?) {
        ctx.registerWithBody { args, rcv, loc ->
            @Suppress("UNCHECKED_CAST")
            val p1: P1 = if (ctx.receiverClass != null) rcv as P1 else slot(ctx, args, 0, loc)
            val p2: P2 = slot(ctx, args, 1, loc)
            val p3: P3 = slot(ctx, args, 2, loc)
            val p4: P4 = slot(ctx, args, 3, loc)
            val p5: P5 = slot(ctx, args, 4, loc)
            val p6: P6 = slot(ctx, args, 5, loc)
            val p7: P7 = slot(ctx, args, 6, loc)
            fn(p1, p2, p3, p4, p5, p6, p7)
        }
    }
}

class FunctionBuilder8<P1, P2, P3, P4, P5, P6, P7, P8> @PublishedApi internal constructor(@PublishedApi internal val ctx: BuilderCtx) {
    inline fun <reified T : Any> withParam(name: String): FunctionBuilder9<P1, P2, P3, P4, P5, P6, P7, P8, T> {
        ctx.addSpec(ParamSpec(name = name, kotlinType = T::class))
        return FunctionBuilder9(ctx)
    }

    inline fun <reified T : Any> withOptionalParam(
        name: String,
        noinline default: () -> T
    ): FunctionBuilder9<P1, P2, P3, P4, P5, P6, P7, P8, T> {
        ctx.addSpec(ParamSpec(name = name, kotlinType = T::class, isOptional = true, default = { wrapAsRuntimeValue(default()) }))
        return FunctionBuilder9(ctx)
    }

    inline fun <reified T : Any> withVararg(name: String): TerminalVararg9<P1, P2, P3, P4, P5, P6, P7, P8, T> {
        ctx.addSpec(ParamSpec(name = name, kotlinType = T::class, isVararg = true))
        return TerminalVararg9(ctx)
    }

    fun body(fn: (P1, P2, P3, P4, P5, P6, P7, P8) -> Any?) {
        ctx.registerWithBody { args, rcv, loc ->
            @Suppress("UNCHECKED_CAST")
            val p1: P1 = if (ctx.receiverClass != null) rcv as P1 else slot(ctx, args, 0, loc)
            val p2: P2 = slot(ctx, args, 1, loc)
            val p3: P3 = slot(ctx, args, 2, loc)
            val p4: P4 = slot(ctx, args, 3, loc)
            val p5: P5 = slot(ctx, args, 4, loc)
            val p6: P6 = slot(ctx, args, 5, loc)
            val p7: P7 = slot(ctx, args, 6, loc)
            val p8: P8 = slot(ctx, args, 7, loc)
            fn(p1, p2, p3, p4, p5, p6, p7, p8)
        }
    }
}

class FunctionBuilder9<P1, P2, P3, P4, P5, P6, P7, P8, P9> @PublishedApi internal constructor(@PublishedApi internal val ctx: BuilderCtx) {
    inline fun <reified T : Any> withParam(name: String): FunctionBuilder10<P1, P2, P3, P4, P5, P6, P7, P8, P9, T> {
        ctx.addSpec(ParamSpec(name = name, kotlinType = T::class))
        return FunctionBuilder10(ctx)
    }

    inline fun <reified T : Any> withOptionalParam(
        name: String,
        noinline default: () -> T
    ): FunctionBuilder10<P1, P2, P3, P4, P5, P6, P7, P8, P9, T> {
        ctx.addSpec(ParamSpec(name = name, kotlinType = T::class, isOptional = true, default = { wrapAsRuntimeValue(default()) }))
        return FunctionBuilder10(ctx)
    }

    inline fun <reified T : Any> withVararg(name: String): TerminalVararg10<P1, P2, P3, P4, P5, P6, P7, P8, P9, T> {
        ctx.addSpec(ParamSpec(name = name, kotlinType = T::class, isVararg = true))
        return TerminalVararg10(ctx)
    }

    fun body(fn: (P1, P2, P3, P4, P5, P6, P7, P8, P9) -> Any?) {
        ctx.registerWithBody { args, rcv, loc ->
            @Suppress("UNCHECKED_CAST")
            val p1: P1 = if (ctx.receiverClass != null) rcv as P1 else slot(ctx, args, 0, loc)
            val p2: P2 = slot(ctx, args, 1, loc)
            val p3: P3 = slot(ctx, args, 2, loc)
            val p4: P4 = slot(ctx, args, 3, loc)
            val p5: P5 = slot(ctx, args, 4, loc)
            val p6: P6 = slot(ctx, args, 5, loc)
            val p7: P7 = slot(ctx, args, 6, loc)
            val p8: P8 = slot(ctx, args, 7, loc)
            val p9: P9 = slot(ctx, args, 8, loc)
            fn(p1, p2, p3, p4, p5, p6, p7, p8, p9)
        }
    }
}

class FunctionBuilder10<P1, P2, P3, P4, P5, P6, P7, P8, P9, P10> @PublishedApi internal constructor(@PublishedApi internal val ctx: BuilderCtx) {
    // No more withParam — slot budget exhausted.
    fun body(fn: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10) -> Any?) {
        ctx.registerWithBody { args, rcv, loc ->
            @Suppress("UNCHECKED_CAST")
            val p1: P1 = if (ctx.receiverClass != null) rcv as P1 else slot(ctx, args, 0, loc)
            val p2: P2 = slot(ctx, args, 1, loc)
            val p3: P3 = slot(ctx, args, 2, loc)
            val p4: P4 = slot(ctx, args, 3, loc)
            val p5: P5 = slot(ctx, args, 4, loc)
            val p6: P6 = slot(ctx, args, 5, loc)
            val p7: P7 = slot(ctx, args, 6, loc)
            val p8: P8 = slot(ctx, args, 7, loc)
            val p9: P9 = slot(ctx, args, 8, loc)
            val p10: P10 = slot(ctx, args, 9, loc)
            fn(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10)
        }
    }
}

// ============================================================================
//  TerminalVarargN — vararg slot was added; only .body remains
// ============================================================================
//  TerminalVarargN<..., T> means: total slots = N, the last one is vararg of T.
//  body lambda has N typed params; the last is List<T>.
// ============================================================================

class TerminalVararg1<T> @PublishedApi internal constructor(@PublishedApi internal val ctx: BuilderCtx) {
    // Type-state guarantees: TerminalVararg1 is only reachable from
    // FunctionBuilder0.withVararg, which can only be called before withReceiver,
    // so ctx.receiverClass is necessarily null here.
    fun body(fn: (List<T>) -> Any?) {
        ctx.registerWithBody { args, _, loc ->
            val tail: List<T> = varargSlot(ctx, args, 0, loc)
            fn(tail)
        }
    }
}

class TerminalVararg2<P1, T> @PublishedApi internal constructor(@PublishedApi internal val ctx: BuilderCtx) {
    fun body(fn: (P1, List<T>) -> Any?) {
        ctx.registerWithBody { args, rcv, loc ->
            @Suppress("UNCHECKED_CAST")
            val p1: P1 = if (ctx.receiverClass != null) rcv as P1 else slot(ctx, args, 0, loc)
            val tail: List<T> = varargSlot(ctx, args, 1, loc)
            fn(p1, tail)
        }
    }
}

class TerminalVararg3<P1, P2, T> @PublishedApi internal constructor(@PublishedApi internal val ctx: BuilderCtx) {
    fun body(fn: (P1, P2, List<T>) -> Any?) {
        ctx.registerWithBody { args, rcv, loc ->
            @Suppress("UNCHECKED_CAST")
            val p1: P1 = if (ctx.receiverClass != null) rcv as P1 else slot(ctx, args, 0, loc)
            val p2: P2 = slot(ctx, args, 1, loc)
            val tail: List<T> = varargSlot(ctx, args, 2, loc)
            fn(p1, p2, tail)
        }
    }
}

class TerminalVararg4<P1, P2, P3, T> @PublishedApi internal constructor(@PublishedApi internal val ctx: BuilderCtx) {
    fun body(fn: (P1, P2, P3, List<T>) -> Any?) {
        ctx.registerWithBody { args, rcv, loc ->
            @Suppress("UNCHECKED_CAST")
            val p1: P1 = if (ctx.receiverClass != null) rcv as P1 else slot(ctx, args, 0, loc)
            val p2: P2 = slot(ctx, args, 1, loc)
            val p3: P3 = slot(ctx, args, 2, loc)
            val tail: List<T> = varargSlot(ctx, args, 3, loc)
            fn(p1, p2, p3, tail)
        }
    }
}

class TerminalVararg5<P1, P2, P3, P4, T> @PublishedApi internal constructor(@PublishedApi internal val ctx: BuilderCtx) {
    fun body(fn: (P1, P2, P3, P4, List<T>) -> Any?) {
        ctx.registerWithBody { args, rcv, loc ->
            @Suppress("UNCHECKED_CAST")
            val p1: P1 = if (ctx.receiverClass != null) rcv as P1 else slot(ctx, args, 0, loc)
            val p2: P2 = slot(ctx, args, 1, loc)
            val p3: P3 = slot(ctx, args, 2, loc)
            val p4: P4 = slot(ctx, args, 3, loc)
            val tail: List<T> = varargSlot(ctx, args, 4, loc)
            fn(p1, p2, p3, p4, tail)
        }
    }
}

class TerminalVararg6<P1, P2, P3, P4, P5, T> @PublishedApi internal constructor(@PublishedApi internal val ctx: BuilderCtx) {
    fun body(fn: (P1, P2, P3, P4, P5, List<T>) -> Any?) {
        ctx.registerWithBody { args, rcv, loc ->
            @Suppress("UNCHECKED_CAST")
            val p1: P1 = if (ctx.receiverClass != null) rcv as P1 else slot(ctx, args, 0, loc)
            val p2: P2 = slot(ctx, args, 1, loc)
            val p3: P3 = slot(ctx, args, 2, loc)
            val p4: P4 = slot(ctx, args, 3, loc)
            val p5: P5 = slot(ctx, args, 4, loc)
            val tail: List<T> = varargSlot(ctx, args, 5, loc)
            fn(p1, p2, p3, p4, p5, tail)
        }
    }
}

class TerminalVararg7<P1, P2, P3, P4, P5, P6, T> @PublishedApi internal constructor(@PublishedApi internal val ctx: BuilderCtx) {
    fun body(fn: (P1, P2, P3, P4, P5, P6, List<T>) -> Any?) {
        ctx.registerWithBody { args, rcv, loc ->
            @Suppress("UNCHECKED_CAST")
            val p1: P1 = if (ctx.receiverClass != null) rcv as P1 else slot(ctx, args, 0, loc)
            val p2: P2 = slot(ctx, args, 1, loc)
            val p3: P3 = slot(ctx, args, 2, loc)
            val p4: P4 = slot(ctx, args, 3, loc)
            val p5: P5 = slot(ctx, args, 4, loc)
            val p6: P6 = slot(ctx, args, 5, loc)
            val tail: List<T> = varargSlot(ctx, args, 6, loc)
            fn(p1, p2, p3, p4, p5, p6, tail)
        }
    }
}

class TerminalVararg8<P1, P2, P3, P4, P5, P6, P7, T> @PublishedApi internal constructor(@PublishedApi internal val ctx: BuilderCtx) {
    fun body(fn: (P1, P2, P3, P4, P5, P6, P7, List<T>) -> Any?) {
        ctx.registerWithBody { args, rcv, loc ->
            @Suppress("UNCHECKED_CAST")
            val p1: P1 = if (ctx.receiverClass != null) rcv as P1 else slot(ctx, args, 0, loc)
            val p2: P2 = slot(ctx, args, 1, loc)
            val p3: P3 = slot(ctx, args, 2, loc)
            val p4: P4 = slot(ctx, args, 3, loc)
            val p5: P5 = slot(ctx, args, 4, loc)
            val p6: P6 = slot(ctx, args, 5, loc)
            val p7: P7 = slot(ctx, args, 6, loc)
            val tail: List<T> = varargSlot(ctx, args, 7, loc)
            fn(p1, p2, p3, p4, p5, p6, p7, tail)
        }
    }
}

class TerminalVararg9<P1, P2, P3, P4, P5, P6, P7, P8, T> @PublishedApi internal constructor(@PublishedApi internal val ctx: BuilderCtx) {
    fun body(fn: (P1, P2, P3, P4, P5, P6, P7, P8, List<T>) -> Any?) {
        ctx.registerWithBody { args, rcv, loc ->
            @Suppress("UNCHECKED_CAST")
            val p1: P1 = if (ctx.receiverClass != null) rcv as P1 else slot(ctx, args, 0, loc)
            val p2: P2 = slot(ctx, args, 1, loc)
            val p3: P3 = slot(ctx, args, 2, loc)
            val p4: P4 = slot(ctx, args, 3, loc)
            val p5: P5 = slot(ctx, args, 4, loc)
            val p6: P6 = slot(ctx, args, 5, loc)
            val p7: P7 = slot(ctx, args, 6, loc)
            val p8: P8 = slot(ctx, args, 7, loc)
            val tail: List<T> = varargSlot(ctx, args, 8, loc)
            fn(p1, p2, p3, p4, p5, p6, p7, p8, tail)
        }
    }
}

class TerminalVararg10<P1, P2, P3, P4, P5, P6, P7, P8, P9, T> @PublishedApi internal constructor(@PublishedApi internal val ctx: BuilderCtx) {
    fun body(fn: (P1, P2, P3, P4, P5, P6, P7, P8, P9, List<T>) -> Any?) {
        ctx.registerWithBody { args, rcv, loc ->
            @Suppress("UNCHECKED_CAST")
            val p1: P1 = if (ctx.receiverClass != null) rcv as P1 else slot(ctx, args, 0, loc)
            val p2: P2 = slot(ctx, args, 1, loc)
            val p3: P3 = slot(ctx, args, 2, loc)
            val p4: P4 = slot(ctx, args, 3, loc)
            val p5: P5 = slot(ctx, args, 4, loc)
            val p6: P6 = slot(ctx, args, 5, loc)
            val p7: P7 = slot(ctx, args, 6, loc)
            val p8: P8 = slot(ctx, args, 7, loc)
            val p9: P9 = slot(ctx, args, 8, loc)
            val tail: List<T> = varargSlot(ctx, args, 9, loc)
            fn(p1, p2, p3, p4, p5, p6, p7, p8, p9, tail)
        }
    }
}
