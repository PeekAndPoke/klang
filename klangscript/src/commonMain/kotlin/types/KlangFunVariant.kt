package io.peekandpoke.klang.script.types

data class KlangFunVariant(
    val type: KlangFunKind,
    val signatureModel: KlangFunSignature,
    val description: String,
    val returnDoc: String = "",
    val samples: List<String> = emptyList(),
) {
    val signature: String get() = signatureModel.render()
    val params: List<KlangParam> get() = signatureModel.params ?: emptyList()
}
