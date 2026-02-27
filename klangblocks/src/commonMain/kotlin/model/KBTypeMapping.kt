package io.peekandpoke.klang.blocks.model

import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.script.types.KlangType

object KBTypeMapping {

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
            result += if (param.isVararg) {
                KBVarArgSlot(result.size, param.name, kind)
            } else {
                KBSingleSlot(result.size, param.name, kind)
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

/**
 * Expands slots into (argIndex, slot) render pairs.
 *
 * - KBSingleSlot  → one pair at slot.index
 * - KBVarArgSlot  → one pair per filled arg starting at slot.index, plus one extra empty slot
 */
fun List<KBSlot>.toRenderItems(args: List<KBArgValue>): List<Pair<Int, KBSlot>> {
    val items = mutableListOf<Pair<Int, KBSlot>>()
    for (slot in this) {
        when (slot) {
            is KBSingleSlot -> items.add(slot.index to slot)
            is KBVarArgSlot -> {
                var idx = slot.index
                while (true) {
                    val a = args.getOrNull(idx)
                    if (a == null || a is KBEmptyArg) break
                    items.add(idx to slot)
                    idx++
                }
                items.add(idx to slot) // always one empty slot at the end
            }
        }
    }
    return items
}
