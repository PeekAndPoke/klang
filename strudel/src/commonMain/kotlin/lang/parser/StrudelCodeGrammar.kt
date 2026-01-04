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
    val numLiteral by regexToken("-?\\d+(\\.\\d+)?")
    val strLiteral by regexToken("\"[^\"\\\\]*(\\\\.[^\"\\\\]*)*\"")

    // Backtick fix: use dot-all pattern for multiline support
    val backtickLiteral by regexToken("`[\\s\\S]*?`")

    val identifier by regexToken("[a-zA-Z_][a-zA-Z0-9_]*")

    // --- Parser Rules ---

    // 1. Literals
    val literalParser: Parser<LiteralNode> by
    (numLiteral map { LiteralNode(it.text.toDouble()) }) or
            (strLiteral map { LiteralNode(it.text.trim('"')) }) or
            (backtickLiteral map { LiteralNode(it.text.trim('`')) })

    // 2. Arguments List: "arg1, arg2, arg3"
    // separatedTerms doesn't handle trailing commas natively.
    // We define `argsList` as the terms themselves.
    val argsList by separatedTerms(parser { expression }, comma, acceptZero = true)

    // 3. List Structure: "[ expr, expr, ]"
    // We explicitly allow an optional comma after the args list
    val listParser: Parser<ListNode> by (skip(lBracket) and argsList and skip(optional(comma)) and skip(rBracket)) map {
        ListNode(it)
    }

    // 4. Function Call: "name(args,)"
    // Allow optional trailing comma here too
    val funcCallParser: Parser<FunCallNode> by (identifier and skip(lPar) and argsList and skip(optional(comma)) and skip(
        rPar
    )) map { (name, args) ->
        FunCallNode(name.text, args)
    }

    // 5. Atomic Term
    val term: Parser<AstNode> by literalParser or
            funcCallParser or
            listParser or
            (skip(lPar) and parser { expression } and skip(rPar))

    // 6. Chain
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
