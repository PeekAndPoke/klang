package io.peekandpoke.klang.blocks.model

import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.script.types.KlangType

object KBTypeMapping {

    const val MAX_VARARG_SLOTS = 4

    fun slotKindFor(type: KlangType): KBSlotKind {
        if (type.isUnion) {
            val members = type.unionMembers!!.map { slotKindFor(it) }.distinct()
            return KBSlotKind.Union(members)
        }
        return primitiveKindFor(type.simpleName)
    }

    fun primitiveKindFor(typeName: String): KBSlotKind = when (typeName) {
        "String" -> KBSlotKind.Str
        "Double", "Float", "Int", "Long", "Number" -> KBSlotKind.Num
        "Boolean" -> KBSlotKind.Bool
        "StrudelPattern", "Pattern" -> KBSlotKind.PatternResult
        // v1: PatternLike hardcoded; v2 will use @KlangType annotation wired into KlangType.unionMembers
        "PatternLike" -> KBSlotKind.Union(
            listOf(KBSlotKind.Str, KBSlotKind.Num, KBSlotKind.PatternResult)
        )

        else -> KBSlotKind.NamedObject(typeName)
    }

    fun slotsFor(doc: KlangSymbol): List<KBSlot> {
        val params = doc.variants.filterIsInstance<KlangCallable>()
            .firstOrNull()?.params ?: return emptyList()
        val result = mutableListOf<KBSlot>()
        for (param in params) {
            val kind = slotKindFor(param.type)
            if (param.isVararg) {
                repeat(MAX_VARARG_SLOTS) { slot ->
                    result += KBSlot(result.size, "${param.name}[$slot]", kind, isVararg = true)
                }
            } else {
                result += KBSlot(result.size, param.name, kind)
            }
        }
        return result
    }

    fun compatible(argValue: KBArgValue, kind: KBSlotKind): Boolean = when (kind) {
        is KBSlotKind.Union -> kind.members.any { compatible(argValue, it) }
        KBSlotKind.PatternResult -> argValue is KBNestedChainArg || argValue is KBIdentifierArg
        KBSlotKind.Str -> argValue is KBStringArg || argValue is KBEmptyArg
        KBSlotKind.Num -> argValue is KBNumberArg || argValue is KBEmptyArg
        KBSlotKind.Bool -> argValue is KBBoolArg || argValue is KBEmptyArg
        else -> true
    }
}
