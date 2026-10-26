/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:JsModule("@codemirror/state")
@file:JsNonModule
@file:Suppress("unused")

package io.peekandpoke.klang.codemirror.ext

/**
 * Extension type - can be a single extension or an array of extensions
 */
external interface Extension

/**
 * A facet is a value that can be dynamically computed based on the editor state
 */
external interface Facet<T> {
    fun of(value: T): Extension
    fun from(field: StateField<out T>): Extension

    /** The facet's value computed from [get], which runs again only when one of [deps] changed (a field, "doc"). */
    fun compute(deps: Array<Any>, get: (state: EditorState) -> T): Extension
}

/**
 * A state field stores additional state in the editor
 */
external interface StateField<T> {
    /** The field, started from [create] instead of its config's `create`. */
    fun init(create: (state: EditorState) -> T): Extension

    companion object {
        fun <T> define(config: StateFieldConfig<T>): StateField<T>
    }
}

/**
 * Configuration for creating a StateField
 */
external interface StateFieldConfig<T> {
    var create: (() -> T)?
    var update: ((value: T, tr: Transaction) -> T)?
    var provide: ((field: StateField<T>) -> Extension)?
}

/**
 * Configuration options for creating an EditorState
 */
external interface EditorStateConfig {
    var doc: String?
    var selection: EditorSelection?
    var extensions: Array<Extension>?
}

/**
 * The editor state - immutable representation of the editor
 */
external class EditorState {
    val doc: Text
    val selection: EditorSelection

    /** True when the editor does not take changes from the user's commands (CodeMirror's `readOnly` facet). */
    val readOnly: Boolean

    fun update(vararg specs: TransactionSpec): Transaction
    fun replaceSelection(text: String): Transaction
    /** A transaction spec (changes, selection, effects) from [f]'s change and new range for each range. */
    fun changeByRange(f: (range: SelectionRange) -> dynamic): dynamic

    fun field(field: StateField<*>): Any?

    /** The value of [field]; with [require] false, undefined when the state does not have it instead of throwing. */
    fun field(field: StateField<*>, require: Boolean): Any?
    fun facet(facet: Facet<*>): Any?

    fun wordAt(pos: Int): SelectionRange?

    fun sliceDoc(from: Int = definedExternally, to: Int = definedExternally): String
    fun toJSON(): Any

    companion object {
        fun create(config: EditorStateConfig): EditorState
        fun fromJSON(json: Any, config: EditorStateConfig = definedExternally): EditorState

        /** Whether the selection may hold several ranges (several cursors); off by default. */
        val allowMultipleSelections: Facet<Boolean>

        /** Functions that may add to every transaction: a spec with `annotations` or `effects`, or null. */
        val transactionExtender: Facet<(tr: Transaction) -> Any?>
    }
}

/**
 * A document - the content of the editor
 */
external class Text {
    val length: Int
    val lines: Int

    fun line(n: Int): Line
    fun lineAt(pos: Int): Line
    fun slice(from: Int, to: Int = definedExternally): Text
    fun sliceString(from: Int, to: Int = definedExternally, lineSep: String = definedExternally): String
    override fun toString(): String

    companion object {
        fun of(text: Array<String>): Text
        val empty: Text
    }
}

/**
 * A line in the document
 */
external class Line {
    val from: Int
    val to: Int
    val number: Int
    val text: String
}

/**
 * A selection - one or more selection ranges
 */
external class EditorSelection {
    val ranges: Array<SelectionRange>
    val main: SelectionRange
    val mainIndex: Int

    fun map(change: ChangeSet): EditorSelection
    fun eq(other: EditorSelection): Boolean

    /** This selection with [range] added; with [main] (the default) the new range becomes the main one. */
    fun addRange(range: SelectionRange, main: Boolean = definedExternally): EditorSelection
    fun toJSON(): Any

    companion object {
        fun single(anchor: Int, head: Int = definedExternally): EditorSelection
        fun create(ranges: Array<SelectionRange>, mainIndex: Int = definedExternally): EditorSelection
        fun cursor(pos: Int, assoc: Int = definedExternally): SelectionRange
        fun range(anchor: Int, head: Int, goalColumn: Int = definedExternally): SelectionRange
        fun fromJSON(json: Any): EditorSelection
    }
}

/**
 * A single selection range
 */
external class SelectionRange {
    val from: Int
    val to: Int
    val anchor: Int
    val head: Int
    val empty: Boolean

    fun map(change: ChangeSet): SelectionRange
    fun extend(from: Int, to: Int = definedExternally): SelectionRange
    fun eq(other: SelectionRange): Boolean
}

/**
 * A transaction - describes a change to the editor state
 */
external class Transaction {
    val state: EditorState
    val changes: ChangeSet
    val selection: EditorSelection?
    val effects: Array<StateEffect<*>>
    val startState: EditorState
    val newDoc: Text
    val newSelection: EditorSelection

    /** True when the transaction's user event is [event] or one of its sub-events (`move.line` for `move`). */
    fun isUserEvent(event: String): Boolean
}

/**
 * Specification for creating a transaction
 */
external interface TransactionSpec {
    var changes: Any? // ChangeSpec
    var selection: Any? // EditorSelection or SelectionRange
    var effects: Any? // StateEffect or Array<StateEffect>
    var scrollIntoView: Boolean?
    var userEvent: String?
    var annotations: Any?
}

/**
 * A set of document changes
 */
external class ChangeSet {
    val length: Int
    val newLength: Int
    val empty: Boolean

    fun invert(doc: Text): ChangeSet
    fun compose(other: ChangeSet): ChangeSet
    fun map(other: ChangeSet, before: Boolean = definedExternally): ChangeSet
    fun iterChanges(f: (fromA: Int, toA: Int, fromB: Int, toB: Int, inserted: Text) -> Unit)
    fun desc(): ChangeDesc

    /** Maps a position in the old document to the new one (a `ChangeSet` is a `ChangeDesc` in CodeMirror). */
    fun mapPos(pos: Int, assoc: Int = definedExternally): Int
}

/**
 * Description of changes without the inserted text
 */
external class ChangeDesc {
    val length: Int
    val newLength: Int
    fun invert(doc: Text): ChangeDesc
    fun mapPos(pos: Int, assoc: Int = definedExternally): Int
}

/**
 * A state effect - causes side effects
 */
external class StateEffect<T> {
    val value: T

    fun map(mapping: ChangeDesc): StateEffect<T>?
    fun `is`(type: StateEffectType<*>): Boolean

    companion object {
        fun <T> define(): StateEffectType<T>
        fun <T> appendConfig(): StateEffectType<T>
    }
}

/** A kind of transaction annotation (CodeMirror's `AnnotationType`): [of] makes one for a transaction. */
external interface AnnotationType<T> {
    fun of(value: T): Annotation<T>
}

/** One annotation of a transaction. */
external interface Annotation<T>

/** Precedence for extensions: a higher one runs first (key bindings, for example). */
external object Prec {
    fun highest(ext: Extension): Extension
    fun high(ext: Extension): Extension
}

/**
 * Type of a state effect
 */
external interface StateEffectType<T> {
    fun of(value: T): StateEffect<T>
}

/**
 * A change specification - used to describe document changes
 */
external interface ChangeSpec {
    var from: Int
    var to: Int?
    var insert: Any? // Text or string
}

/**
 * Range set - used for decorations and other range-based data
 */
external class RangeSet<T> {
    val size: Int
    fun between(from: Int, to: Int, f: (from: Int, to: Int, value: T) -> Unit)
    fun iter(from: Int = definedExternally): RangeSetIterator<T>

    companion object {
        /** A set of [ranges], which must be sorted by position unless [sort] is true. */
        fun <T> of(ranges: Array<Range<T>>, sort: Boolean = definedExternally): RangeSet<T>
    }
}

external interface RangeSetIterator<T> {
    val value: T
    val from: Int
    val to: Int
    fun next()
}

/**
 * Range value - base for decorations
 */
external interface RangeValue {
    fun eq(other: RangeValue): Boolean
}

/**
 * Character categorizer
 */
external interface CharCategory {
    fun <T> of(base: T, extend: (char: Int, cat: T, prev: T) -> T): Facet<(char: Int) -> T>
}
