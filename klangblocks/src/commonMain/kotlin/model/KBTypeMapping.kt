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
 * One item in the rendered slot list.
 *
 * [index] is the **actual** position in the args list — used for both reads and writes.
 * [arg]   is the current value at that position (null = empty / not yet set).
 * [slot]  is the slot descriptor.
 */
data class KBRenderItem(val index: Int, val arg: KBArgValue?, val slot: KBSlot)

/**
 * Expands slots into render items.
 *
 * - KBSingleSlot  → one item at slot.index
 * - KBVarArgSlot  → one item per *filled* arg (holes from mid-list empties are skipped),
 *                   plus one extra empty item at the next available position.
 *                   Each item carries its actual arg-list index so reads/writes stay correct.
 */
fun List<KBSlot>.toRenderItems(args: List<KBArgValue>): List<KBRenderItem> {
    val items = mutableListOf<KBRenderItem>()
    for (slot in this) {
        when (slot) {
            is KBSingleSlot -> items.add(KBRenderItem(slot.index, args.getOrNull(slot.index), slot))
            is KBVarArgSlot -> {
                // Collect actual positions of non-empty args in the vararg range, skipping holes
                val filled = mutableListOf<Int>()
                var scanIdx = slot.index
                while (scanIdx < args.size) {
                    if (args[scanIdx] !is KBEmptyArg) filled.add(scanIdx)
                    scanIdx++
                }
                for (actualIdx in filled) {
                    items.add(KBRenderItem(actualIdx, args[actualIdx], slot))
                }
                // Always one empty slot at the next position after the last filled
                val nextIdx = (filled.lastOrNull()?.plus(1)) ?: slot.index
                items.add(KBRenderItem(nextIdx, null, slot))
            }
        }
    }
    return items
}
