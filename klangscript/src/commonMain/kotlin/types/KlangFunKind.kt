package io.peekandpoke.klang.script.types

enum class KlangFunKind {
    /** Top-level function: `seq("a", "b")` */
    TOP_LEVEL,

    /** Pattern extension method: `pattern.seq("a", "b")` */
    EXTENSION_METHOD,

    /** Property accessor: `pattern.prop` */
    PROPERTY,

    /** Named object/constant pattern: `sine`, `berlin`, `silence` */
    OBJECT,
}
