package io.peekandpoke.klang.strudel.lang.parser

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.lexer.literalToken
import com.github.h0tk3y.betterParse.lexer.regexToken
import com.github.h0tk3y.betterParse.parser.Parser

object StrudelCodeGrammar : Grammar<AstNode>() {

    // --- Tokens ---

    // Ignored
    val ws by regexToken("\\s+", ignore = true)
    val comment by regexToken("//.*", ignore = true)
    val blockComment by regexToken("/\\*[\\s\\S]*?\\*/", ignore = true)

    // Punctuation
    val lPar by literalToken("(")
    val rPar by literalToken(")")
    val lBracket by literalToken("[")
    val rBracket by literalToken("]")
    val comma by literalToken(",")
    val dot by literalToken(".")

    // Literals
    // Matches integer or float (e.g. 1, 0.5, -10.2)
    val numLiteral by regexToken("-?\\d+(\\.\\d+)?")

    // Matches standard quotes "..."
    val strLiteral by regexToken("\"[^\"\\\\]*(\\\\.[^\"\\\\]*)*\"")

    // Matches backticks `...` (useful for multiline mini-notation)
    // [^`] matches any character that is not a backtick, including newlines
    val backtickLiteral by regexToken("`[^`]*`")

    // Identifiers (function names)
    // Must be after literals to avoid consuming parts of them if they overlap (unlikely here)
    val identifier by regexToken("[a-zA-Z_][a-zA-Z0-9_]*")

    // --- Parser Rules ---

    // 1. Literals
    val literalParser: Parser<LiteralNode> by
    (numLiteral map { LiteralNode(it.text.toDouble()) }) or
            (strLiteral map { LiteralNode(it.text.trim('"')) }) or
            (backtickLiteral map { LiteralNode(it.text.trim('`')) })

    // 2. Arguments List: "arg1, arg2, arg3"
    // We use `parser { expression }` to handle forward recursion
    val argsList by separatedTerms(parser { expression }, comma, acceptZero = true)

    // New: List "[ expr, expr ]"
    val listParser: Parser<ListNode> by (skip(lBracket) and argsList and skip(rBracket)) map {
        ListNode(it)
    }

    // 3. Function Call: "name(args)"
    val funcCallParser: Parser<FunCallNode> by (identifier and skip(lPar) and argsList and skip(rPar)) map { (name, args) ->
        FunCallNode(name.text, args)
    }

    // 4. Atomic Term
    // Can be a literal, a function call, a list, or an expression in parenthesis
    val term: Parser<AstNode> by literalParser or
            funcCallParser or
            listParser or
            (skip(lPar) and parser { expression } and skip(rPar))

    // 5. Chain: "term.func().func()"
    // Left-associative logic: `note().fast()` is `(note()).fast()`
    val chainParser: Parser<AstNode> by (term and zeroOrMore(skip(dot) and funcCallParser)) map { (start, chains) ->
        chains.fold(start) { receiver, method ->
            ChainNode(receiver, method)
        }
    }

    // Root Expression
    val expression: Parser<AstNode> by chainParser

    // Main Entry Point
    override val rootParser by expression
}
