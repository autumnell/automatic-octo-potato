package com.mtgscanner.data.search

import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.unitEur
import com.mtgscanner.data.db.unitPrice
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Scryfall search syntax (https://scryfall.com/docs/syntax) worked out against your own cards, so the
 * collection search box reads the same queries as Scryfall. Pure Kotlin, for tests.
 *
 * Text with no syntax at all is left to the old plain search ([CompiledQuery.plain]). Terms that need Scryfall's
 * own data (f:, otag:, is:commander…) are asked of Scryfall ([CompiledQuery.asks]) and matched by oracle id,
 * or by set and number for terms about one printing.
 */

/** A search Scryfall has to answer: the query to send, and whether hits count per printing (set and number). */
data class RemoteAsk(val query: String, val prints: Boolean)

/** What the evaluator needs besides the card itself. */
class SearchEnv(
    /** A box's name by id, for box:. */
    val boxName: (Long) -> String? = { null },
    /** Rows with copies lent out, for is:lent. */
    val lent: Set<Long> = emptySet(),
    /** Scryfall's answer to an ask, as keys from [ScryfallQueryCache.keysOf]; null while unknown. */
    val remote: (RemoteAsk) -> Set<String>? = { ScryfallQueryCache.hits(it) },
    val today: LocalDate = LocalDate.now(),
    val zone: ZoneId = ZoneId.systemDefault(),
)

sealed class QNode {
    class And(val kids: List<QNode>) : QNode()
    class Or(val kids: List<QNode>) : QNode()
    class Not(val kid: QNode) : QNode()
    class Leaf(val term: Term) : QNode()
}

sealed class Term {
    /** A bare word or "quoted phrase" in the name; [exact] for !"name". */
    data class Name(val text: String, val exact: Boolean = false, val quoted: Boolean = false) : Term()
    /** key op value, with [key] in its short form (c, t, mv…). */
    data class Field(val key: String, val op: String, val value: String, val quoted: Boolean = false, val regex: Regex? = null, val rawKey: String = key) : Term()
    /** Only Scryfall can answer this one. [label] names it in notes ("f:"). */
    class Remote(val raw: String, val label: String, val prints: Boolean) : Term()
    /** include:, unique:, order:…: about how Scryfall shows results, ignored here. */
    data class Directive(val raw: String) : Term()
}

class CompiledQuery(
    val text: String,
    val root: QNode?,
    /** Lower-case text for the old substring search, when the query has no syntax or couldn't be read. */
    val plain: String?,
    /** True when the text looked like syntax but couldn't be read, so it's searched as plain text. */
    val invalid: Boolean,
    /** Searches to ask Scryfall, with the label of the term each one is for. */
    val asks: List<Pair<RemoteAsk, String>>,
) {
    /** Whether [c] matches. Only for syntax queries ([plain] is null); terms nobody can answer yet are skipped. */
    fun matches(c: CollectionCard, env: SearchEnv = SearchEnv()): Boolean {
        val r = root ?: return true
        return ScryfallQuery.eval(r, c, env, remoteFor) != false
    }

    private val remoteFor: Map<Term.Remote, RemoteAsk> = HashMap<Term.Remote, RemoteAsk>().also { m ->
        root?.let { ScryfallQuery.remoteAsksByTerm(it, m) }
    }
}

object ScryfallQuery {

    // Keys

    private val ALIASES = mapOf(
        "c" to "c", "color" to "c", "colors" to "c",
        "id" to "id", "identity" to "id", "ci" to "id",
        "t" to "t", "type" to "t",
        "o" to "o", "oracle" to "o", "fo" to "o", "fulloracle" to "o",
        "m" to "m", "mana" to "m",
        "mv" to "mv", "cmc" to "mv", "manavalue" to "mv",
        "pow" to "pow", "power" to "pow", "tou" to "tou", "toughness" to "tou", "pt" to "pt", "powtou" to "pt",
        "r" to "r", "rarity" to "r",
        "s" to "s", "e" to "s", "set" to "s", "edition" to "s",
        "cn" to "cn", "number" to "cn",
        "a" to "a", "artist" to "a",
        "lang" to "lang", "language" to "lang",
        "year" to "year", "date" to "date",
        "usd" to "usd", "eur" to "eur",
        "is" to "is", "not" to "not",
        "name" to "name",
        "kw" to "kw", "keyword" to "kw",
        "oracleid" to "oracleid",
        // Collection only
        "box" to "box", "tag" to "tag", "cond" to "cond", "condition" to "cond",
        "qty" to "qty", "quantity" to "qty", "added" to "added", "note" to "note", "notes" to "note",
    )

    /** Keys only Scryfall can answer. */
    private val REMOTE_KEYS = setOf(
        "f", "format", "legal", "banned", "restricted", "otag", "oracletag", "function", "atag", "arttag", "art",
        "prints", "sets", "papersets", "paperprints", "in", "st", "b", "block", "frame", "border", "stamp", "wm",
        "watermark", "game", "cube", "devotion", "produces", "loy", "loyalty", "tix", "new", "ft", "flavor", "has",
        "lore", "cheapest", "illustrations", "oracletext",
    )

    /** Remote keys whose answer is about one printing rather than the card. */
    private val PRINT_KEYS = setOf("atag", "arttag", "art", "frame", "border", "stamp", "wm", "watermark", "ft", "flavor", "new", "game", "in", "st", "b", "block", "has")

    private val DIRECTIVES = setOf("include", "unique", "order", "direction", "prefer", "display")

    private val COLLECTION_KEYS = setOf("box", "tag", "cond", "qty", "added", "note")

    /** is: values worked out here; the ones Scryfall also knows can go along in a Scryfall query. */
    private val LOCAL_IS_SCRYFALL = setOf("reserved", "spell", "permanent", "historic", "vanilla", "party", "outlaw", "bear")
    private val LOCAL_IS_OWN = setOf("foil", "nonfoil", "token", "proxy", "signed", "altered", "graded", "forsale", "sale", "pinned", "lent")

    /** is: values that are about one printing. */
    private val PRINT_IS = setOf(
        "reprint", "firstprint", "firstprinting", "promo", "fullart", "textless", "borderless", "etched", "glossy", "hires",
        "digital", "extendedart", "showcase", "oversized", "booster", "spotlight", "datestamped", "prerelease", "boosterfun",
        "scryfallpreview", "unique", "paper", "mtgo", "arena", "default", "atypical", "colorshifted", "covered", "playtest",
    )

    private val OPS = listOf("!=", "<=", ">=", ":", "=", "<", ">")

    // Compiling

    private val compiled = object : LinkedHashMap<String, CompiledQuery>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CompiledQuery>?) = size > 32
    }

    /** [text] read once and remembered, since every card is checked against it. */
    fun compile(text: String): CompiledQuery = synchronized(compiled) {
        compiled.getOrPut(text) { compileNow(text) }
    }

    /** Curly quotes from phone keyboards made plain, so "quoted" phrases work. */
    fun straightenQuotes(s: String): String =
        s.replace('“', '"').replace('”', '"').replace('„', '"').replace('″', '"').replace('‘', '\'').replace('’', '\'')

    private fun compileNow(text: String): CompiledQuery {
        val t = straightenQuotes(text).trim()
        val plain = CompiledQuery(text, null, text.trim().lowercase(), false, emptyList())
        if (t.isEmpty()) return plain
        val tokens = try { tokenize(t) } catch (e: QueryError) {
            return CompiledQuery(text, null, text.trim().lowercase(), true, emptyList())
        }
        if (tokens.none { it.isSyntax }) return plain
        val root = try { Parser(tokens).parse() } catch (e: QueryError) {
            return CompiledQuery(text, null, text.trim().lowercase(), true, emptyList())
        }
        val m = HashMap<Term.Remote, RemoteAsk>()
        remoteAsksByTerm(root, m)
        val asks = remoteLeaves(root).map { r -> m.getValue(r) to r.label }.distinct()
        return CompiledQuery(text, root, null, false, asks)
    }

    class QueryError(msg: String) : Exception(msg)

    // Tokens

    sealed class Tok {
        open val isSyntax: Boolean get() = true
        object LP : Tok()
        object RP : Tok()
        object OR : Tok()
        object NEG : Tok()
        class Atom(val term: Term, val syntax: Boolean) : Tok() {
            override val isSyntax get() = syntax
        }
    }

    private fun isBreak(ch: Char) = ch.isWhitespace() || ch == '(' || ch == ')'

    fun tokenize(s: String): List<Tok> {
        val out = mutableListOf<Tok>()
        var i = 0
        fun readQuoted(): String {
            // s[i] is the opening quote.
            val sb = StringBuilder()
            i++
            while (i < s.length) {
                val ch = s[i]
                if (ch == '\\' && i + 1 < s.length) { sb.append(s[i + 1]); i += 2; continue }
                if (ch == '"') { i++; return sb.toString() }
                sb.append(ch); i++
            }
            throw QueryError("Missing closing quote")
        }
        fun readRun(): String {
            val start = i
            while (i < s.length && !isBreak(s[i])) i++
            return s.substring(start, i)
        }
        while (i < s.length) {
            val ch = s[i]
            when {
                ch.isWhitespace() -> i++
                ch == '(' -> { out += Tok.LP; i++ }
                ch == ')' -> { out += Tok.RP; i++ }
                ch == '-' && i + 1 < s.length && !s[i + 1].isWhitespace() && !s[i + 1].isDigit() -> { out += Tok.NEG; i++ }
                ch == '!' && i + 1 < s.length && !s[i + 1].isWhitespace() -> {
                    i++
                    val name = if (s[i] == '"') readQuoted() else readRun()
                    if (name.isBlank()) throw QueryError("Empty name")
                    out += Tok.Atom(Term.Name(name, exact = true), true)
                }
                ch == '"' -> {
                    val phrase = readQuoted()
                    out += Tok.Atom(Term.Name(phrase, quoted = true), true)
                }
                else -> out += readWordOrTerm(s, i).also { i = it.second }.first
            }
        }
        return out
    }

    /** A plain word, "or", or key:value starting at [start]. Returns the token and where reading stopped. */
    private fun readWordOrTerm(s: String, start: Int): Pair<Tok, Int> {
        var i = start
        while (i < s.length && s[i].isLetter()) i++
        val key = s.substring(start, i).lowercase()
        val op = if (key.isNotEmpty()) OPS.firstOrNull { s.startsWith(it, i) } else null
        val known = op != null && (key in ALIASES || key in REMOTE_KEYS || key in DIRECTIVES)
        if (!known) {
            var j = start
            while (j < s.length && !isBreak(s[j])) j++
            val word = s.substring(start, j)
            if (word.equals("or", ignoreCase = true)) return Tok.OR to j
            return Tok.Atom(Term.Name(word), false) to j
        }
        i += op!!.length
        var quoted = false
        var regex: Regex? = null
        val value: String
        when {
            i < s.length && s[i] == '"' -> {
                quoted = true
                val sb = StringBuilder()
                i++
                var closed = false
                while (i < s.length) {
                    val ch = s[i]
                    if (ch == '\\' && i + 1 < s.length) { sb.append(s[i + 1]); i += 2; continue }
                    if (ch == '"') { i++; closed = true; break }
                    sb.append(ch); i++
                }
                if (!closed) throw QueryError("Missing closing quote")
                value = sb.toString()
            }
            i < s.length && s[i] == '/' && ALIASES[key] in setOf("name", "t", "o") -> {
                val sb = StringBuilder()
                i++
                var closed = false
                while (i < s.length) {
                    val ch = s[i]
                    if (ch == '\\' && i + 1 < s.length) { sb.append(ch).append(s[i + 1]); i += 2; continue }
                    if (ch == '/') { i++; closed = true; break }
                    sb.append(ch); i++
                }
                if (!closed) throw QueryError("Missing closing /")
                value = sb.toString()
                regex = try { Regex(value, RegexOption.IGNORE_CASE) } catch (e: Exception) { throw QueryError("Bad regex") }
            }
            else -> {
                val vs = i
                while (i < s.length && !isBreak(s[i])) i++
                value = s.substring(vs, i)
            }
        }
        if (value.isEmpty() && regex == null) throw QueryError("Empty value for $key")
        return Tok.Atom(makeTerm(key, op, value, quoted, regex), true) to i
    }

    private fun quoteIfNeeded(v: String, quoted: Boolean): String =
        if (quoted || v.any { it.isWhitespace() || it == '(' || it == ')' }) "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" else v

    private fun makeTerm(rawKey: String, op: String, value: String, quoted: Boolean, regex: Regex?): Term {
        val raw = rawKey + op + (if (regex != null) "/$value/" else quoteIfNeeded(value, quoted))
        if (rawKey in DIRECTIVES) return Term.Directive(raw)
        if (rawKey in REMOTE_KEYS) return Term.Remote(raw, "$rawKey:", rawKey in PRINT_KEYS)
        val key = ALIASES.getValue(rawKey)
        val v = value.lowercase()
        when (key) {
            "is", "not" -> if (v !in LOCAL_IS_SCRYFALL && v !in LOCAL_IS_OWN) {
                return Term.Remote(raw, "$rawKey:$v", v in PRINT_IS)
            }
            // A date given as a set code ("date>=ori") needs Scryfall's set dates.
            "date" -> if (dateRange(v, null) == null) return Term.Remote(raw, "date:", false)
        }
        validate(key, op, v)
        return Term.Field(key, op, value, quoted, regex, rawKey)
    }

    /** Throws when a value can't be read for its key, so the text is searched as plain text instead. */
    private fun validate(key: String, op: String, v: String) {
        val cmpOk = op != ":" && op != "=" && op != "!="
        when (key) {
            "c", "id" -> if (colorValue(v) == null) throw QueryError("Unknown color $v")
            "mv" -> if (v != "even" && v != "odd" && v.toDoubleOrNull() == null) throw QueryError("Bad number")
            "pow", "tou", "pt" -> if (v.toDoubleOrNull() == null && statKey(v) == null) throw QueryError("Bad number")
            "usd", "eur", "qty", "year" -> if (v.toDoubleOrNull() == null) throw QueryError("Bad number")
            "r" -> if (rarityRank(v) == null) throw QueryError("Unknown rarity")
            "cond" -> if (condRank(v) == null) throw QueryError("Unknown condition")
            "added" -> if (dateRange(v, LocalDate.of(2000, 1, 1)) == null) throw QueryError("Bad date")
            "m" -> if (manaOf(v) == null) throw QueryError("Bad mana")
            "s", "lang", "box", "tag", "name", "t", "o", "a", "kw", "oracleid", "note" -> if (cmpOk) throw QueryError("$key can't use $op")
        }
    }

    // Parsing

    private class Parser(val toks: List<Tok>) {
        var i = 0
        fun parse(): QNode {
            val n = orExpr()
            if (i < toks.size) throw QueryError("Unexpected )")
            return n
        }

        fun orExpr(): QNode {
            val parts = mutableListOf(andExpr())
            while (i < toks.size && toks[i] == Tok.OR) {
                i++
                parts += andExpr()
            }
            return if (parts.size == 1) parts[0] else QNode.Or(parts)
        }

        fun andExpr(): QNode {
            val parts = mutableListOf<QNode>()
            while (i < toks.size && toks[i] != Tok.OR && toks[i] != Tok.RP) parts += unary()
            if (parts.isEmpty()) throw QueryError("Empty search")
            return if (parts.size == 1) parts[0] else QNode.And(parts)
        }

        fun unary(): QNode {
            val t = toks[i++]
            return when (t) {
                Tok.NEG -> {
                    if (i >= toks.size || toks[i] == Tok.OR || toks[i] == Tok.RP || toks[i] == Tok.NEG) throw QueryError("Nothing after -")
                    QNode.Not(unary())
                }
                Tok.LP -> {
                    val n = orExpr()
                    // A missing ) at the very end is let go, as while typing.
                    if (i < toks.size) { if (toks[i] != Tok.RP) throw QueryError("Missing )"); i++ }
                    n
                }
                is Tok.Atom -> QNode.Leaf(t.term)
                else -> throw QueryError("Unexpected token")
            }
        }
    }

    // Remote asks

    private fun remoteLeaves(n: QNode): List<Term.Remote> = when (n) {
        is QNode.And -> n.kids.flatMap(::remoteLeaves)
        is QNode.Or -> n.kids.flatMap(::remoteLeaves)
        is QNode.Not -> remoteLeaves(n.kid)
        is QNode.Leaf -> listOfNotNull(n.term as? Term.Remote)
    }

    /**
     * For each remote term, the query to send: the term plus the Scryfall-readable terms beside it in its
     * nearest AND. Those have to hold for the AND to match anyway, and they keep Scryfall's answer small.
     */
    internal fun remoteAsksByTerm(root: QNode, out: MutableMap<Term.Remote, RemoteAsk>) {
        fun walk(n: QNode, siblings: List<QNode>) {
            when (n) {
                is QNode.And -> n.kids.forEach { k -> walk(k, n.kids.filter { it !== k }) }
                is QNode.Or -> n.kids.forEach { walk(it, siblings) }
                is QNode.Not -> walk(n.kid, siblings)
                is QNode.Leaf -> (n.term as? Term.Remote)?.let { r ->
                    val extra = siblings.filter(::scryfallSafe).map { toScryfall(it) }
                    val q = (listOf(r.raw) + extra + "include:extras").joinToString(" ")
                    out[r] = RemoteAsk(q, r.prints)
                }
            }
        }
        walk(root, emptyList())
    }

    /** Whether Scryfall reads [n] the same way it's read here. */
    private fun scryfallSafe(n: QNode): Boolean = when (n) {
        is QNode.And -> n.kids.all(::scryfallSafe)
        is QNode.Or -> n.kids.all(::scryfallSafe)
        is QNode.Not -> scryfallSafe(n.kid)
        is QNode.Leaf -> when (val t = n.term) {
            is Term.Name -> true
            is Term.Remote -> true
            is Term.Directive -> false
            is Term.Field -> when (t.key) {
                in COLLECTION_KEYS, "usd", "eur", "lang" -> false
                "is", "not" -> t.value.lowercase() in LOCAL_IS_SCRYFALL
                else -> true
            }
        }
    }

    /** [n] written back as Scryfall syntax. */
    fun toScryfall(n: QNode, top: Boolean = true): String = when (n) {
        is QNode.And -> n.kids.joinToString(" ") { toScryfall(it, false) }.let { if (top) it else "($it)" }
        is QNode.Or -> "(" + n.kids.joinToString(" or ") { toScryfall(it, false) } + ")"
        is QNode.Not -> "-" + toScryfall(n.kid, false)
        is QNode.Leaf -> when (val t = n.term) {
            is Term.Name -> when {
                t.exact -> "!" + quoteIfNeeded(t.text, true)
                t.quoted -> quoteIfNeeded(t.text, true)
                else -> t.text
            }
            is Term.Field -> t.rawKey + t.op + (if (t.regex != null) "/${t.value}/" else quoteIfNeeded(t.value, t.quoted))
            is Term.Remote -> t.raw
            is Term.Directive -> t.raw
        }
    }

    // Evaluating: true, false, or null when the term can't be answered yet (it's then skipped).

    internal fun eval(n: QNode, c: CollectionCard, env: SearchEnv, asks: Map<Term.Remote, RemoteAsk>): Boolean? = when (n) {
        is QNode.And -> {
            var any = false
            var result: Boolean? = null
            for (k in n.kids) {
                val v = eval(k, c, env, asks) ?: continue
                any = true
                if (!v) { result = false; break }
                result = true
            }
            if (!any) null else result
        }
        is QNode.Or -> {
            var known = false
            var hit = false
            for (k in n.kids) {
                val v = eval(k, c, env, asks) ?: continue
                known = true
                if (v) { hit = true; break }
            }
            if (!known) null else hit
        }
        is QNode.Not -> eval(n.kid, c, env, asks)?.not()
        is QNode.Leaf -> when (val t = n.term) {
            is Term.Name -> nameMatch(c, t)
            is Term.Directive -> null
            is Term.Remote -> asks[t]?.let { env.remote(it) }?.let { keys -> ScryfallQueryCache.cardKeys(c, t.prints).any { it in keys } }
            is Term.Field -> field(c, t, env)
        }
    }

    /** Lower case without accents, so "Lim-Dûl" is found by "lim-dul". */
    fun fold(s: String): String =
        if (s.all { it.code < 128 }) s.lowercase() else Normalizer.normalize(s, Normalizer.Form.NFD).replace(MARKS, "").lowercase()

    // Compiled once: these run for every card on every keystroke of a search.
    private val MARKS = Regex("\\p{Mn}+")
    private val NUMBER = Regex("-?\\d+(\\.\\d+)?")
    private val wordRegexes = java.util.concurrent.ConcurrentHashMap<String, Regex>()
    private fun wordRegex(w: String): Regex = wordRegexes.getOrPut(w) { Regex("\\b$w\\b") }

    private fun faces(name: String): List<String> = name.split(" // ").map { it.trim() }

    private fun nameMatch(c: CollectionCard, t: Term.Name): Boolean {
        val q = fold(t.text)
        val name = fold(c.name)
        return if (t.exact) name == q || faces(name).any { it == q } else name.contains(q)
    }

    private fun text(have: String, t: Term.Field, transform: (String) -> String = { it }): Boolean {
        val hit = t.regex?.containsMatchIn(have) ?: fold(have).contains(fold(transform(t.value)))
        return if (t.op == "!=") !hit else hit
    }

    private fun field(c: CollectionCard, t: Term.Field, env: SearchEnv): Boolean {
        val v = t.value.lowercase()
        val op = t.op
        return when (t.key) {
            "name" -> text(c.name, t)
            "t" -> text(c.typeLine, t)
            "o" -> {
                if (t.regex != null) text(c.oracleText, t)
                else {
                    // ~ stands for the card's own name, as on Scryfall.
                    val hit = faces(c.name).plus(c.name).distinct().any { n -> fold(c.oracleText).contains(fold(t.value.replace("~", n))) }
                    if (op == "!=") !hit else hit
                }
            }
            "a" -> text(c.artist.orEmpty(), t)
            "note" -> text(c.notes, t)
            "kw" -> {
                val hit = c.tags.split(',').any { fold(it.trim()) == fold(t.value) }
                if (op == "!=") !hit else hit
            }
            "oracleid" -> (c.oracleId.equals(v, ignoreCase = true)).let { if (op == "!=") !it else it }
            "c", "id" -> {
                val have = (if (t.key == "c") c.colors else c.colorIdentity).uppercase().filter { it in "WUBRG" }.toSet()
                colorMatch(have, if (op == ":") (if (t.key == "c") ">=" else "<=") else op, colorValue(v)!!)
            }
            "m" -> manaMatch(manaOf(c.manaCost) ?: Mana(0, emptyMap()), op, manaOf(t.value)!!)
            "mv" -> when (v) {
                "even", "odd" -> {
                    val whole = c.cmc % 1.0 == 0.0
                    val even = whole && c.cmc.toLong() % 2 == 0L
                    val hit = whole && (if (v == "even") even else !even)
                    if (op == "!=") !hit else hit
                }
                else -> compare(c.cmc, op, v.toDouble())
            }
            "pow", "tou", "pt" -> {
                val mine = stat(c, t.key) ?: return false
                val other = v.toDoubleOrNull() ?: stat(c, statKey(v)!!) ?: return false
                compare(mine, op, other)
            }
            "r" -> {
                val mine = rarityRank(c.rarity.lowercase()) ?: return false
                compare(mine.toDouble(), op, rarityRank(v)!!.toDouble())
            }
            "s" -> c.setCode.equals(v, ignoreCase = true).let { if (op == "!=") !it else it }
            "cn" -> when (op) {
                ":", "=" -> sameNumber(c.collectorNumber, v)
                "!=" -> !sameNumber(c.collectorNumber, v)
                else -> {
                    val mine = c.collectorNumber.takeWhile { it.isDigit() }.toDoubleOrNull() ?: return false
                    val want = v.takeWhile { it.isDigit() }.toDoubleOrNull() ?: return false
                    compare(mine, op, want)
                }
            }
            "lang" -> (v == "any" || c.lang.equals(v, ignoreCase = true)).let { if (op == "!=") !it else it }
            "year" -> {
                val y = c.releasedAt?.take(4)?.toDoubleOrNull() ?: return false
                compare(y, op, v.toDouble())
            }
            "date" -> {
                val d = c.releasedAt?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: return false
                inRange(d, op, dateRange(v, env.today)!!)
            }
            "usd" -> compare(c.unitPrice() ?: return false, op, v.toDouble())
            "eur" -> compare(c.unitEur() ?: return false, op, v.toDouble())
            "qty" -> compare(c.quantity.toDouble(), op, v.toDouble())
            "cond" -> compare((condRank(c.condition.lowercase()) ?: return false).toDouble(), op, condRank(v)!!.toDouble())
            "added" -> {
                val day = Instant.ofEpochMilli(c.addedAt).atZone(env.zone).toLocalDate()
                inRange(day, op, dateRange(v, env.today)!!)
            }
            "box" -> {
                val name = env.boxName(c.boxId)?.let(::fold) ?: return op == "!="
                val hit = if (op == "=") name == fold(t.value) else name.contains(fold(t.value))
                if (op == "!=") !hit else hit
            }
            "tag" -> {
                val tags = c.userTags.split(',').map { fold(it.trim()) }.filter { it.isNotEmpty() }
                val hit = if (op == "=") fold(t.value) in tags else tags.any { it.contains(fold(t.value)) }
                if (op == "!=") !hit else hit
            }
            "is" -> isMatch(c, v, env)
            "not" -> !isMatch(c, v, env)
            else -> false
        }
    }

    private fun sameNumber(a: String, b: String): Boolean =
        a.equals(b, ignoreCase = true) || (a.trimStart('0').ifEmpty { "0" }.equals(b.trimStart('0').ifEmpty { "0" }, ignoreCase = true))

    private fun compare(a: Double, op: String, b: Double): Boolean {
        val eq = kotlin.math.abs(a - b) < 1e-9
        return when (op) {
            ":", "=" -> eq
            "!=" -> !eq
            "<" -> a < b && !eq
            ">" -> a > b && !eq
            "<=" -> a < b || eq
            ">=" -> a > b || eq
            else -> false
        }
    }

    private fun isMatch(c: CollectionCard, v: String, env: SearchEnv): Boolean {
        val type = c.typeLine.lowercase()
        fun has(w: String) = wordRegex(w).containsMatchIn(type)
        return when (v) {
            "foil" -> c.foil
            "nonfoil" -> !c.foil
            "reserved" -> c.reserved
            "token" -> has("token")
            "spell" -> !has("land") && !has("token")
            "permanent" -> listOf("artifact", "creature", "enchantment", "land", "planeswalker", "battle").any(::has)
            "historic" -> has("legendary") || has("artifact") || has("saga")
            "vanilla" -> has("creature") && c.oracleText.isBlank()
            "party" -> has("creature") && listOf("cleric", "rogue", "warrior", "wizard").any(::has)
            "outlaw" -> has("creature") && listOf("assassin", "mercenary", "pirate", "rogue", "warlock").any(::has)
            "bear" -> has("creature") && c.cmc == 2.0 && c.power == "2" && c.toughness == "2"
            "proxy" -> c.proxy
            "signed" -> c.signed
            "altered" -> c.altered
            "graded" -> c.graded.isNotBlank()
            "forsale", "sale" -> c.forSale
            "pinned" -> c.pinned
            "lent" -> c.id in env.lent
            else -> false
        }
    }

    // Values

    private val COLOR_NAMES = mapOf(
        "white" to "w", "blue" to "u", "black" to "b", "red" to "r", "green" to "g",
        "azorius" to "wu", "dimir" to "ub", "rakdos" to "br", "gruul" to "rg", "selesnya" to "gw",
        "orzhov" to "wb", "izzet" to "ur", "golgari" to "bg", "boros" to "rw", "simic" to "gu",
        "bant" to "gwu", "esper" to "wub", "grixis" to "ubr", "jund" to "brg", "naya" to "rgw",
        "abzan" to "wbg", "jeskai" to "urw", "sultai" to "bgu", "mardu" to "rwb", "temur" to "gur",
        "silverquill" to "wb", "prismari" to "ur", "witherbloom" to "bg", "lorehold" to "rw", "quandrix" to "gu",
        "glint" to "ubrg", "dune" to "wbrg", "ink" to "wurg", "witch" to "wubg", "yore" to "wubr",
        "wubrg" to "wubrg", "fivecolor" to "wubrg", "rainbow" to "wubrg",
    )

    sealed class ColorValue {
        data class Colors(val set: Set<Char>) : ColorValue()
        data class Count(val n: Int) : ColorValue()
        object Multi : ColorValue()
    }

    fun colorValue(v: String): ColorValue? {
        v.toIntOrNull()?.let { return ColorValue.Count(it) }
        when (v) {
            "c", "colorless" -> return ColorValue.Colors(emptySet())
            "m", "multicolor", "multi" -> return ColorValue.Multi
        }
        val letters = COLOR_NAMES[v] ?: v
        if (letters.isEmpty() || letters.any { it !in "wubrg" }) return null
        return ColorValue.Colors(letters.uppercase().toSet())
    }

    private fun colorMatch(have: Set<Char>, op: String, want: ColorValue): Boolean = when (want) {
        is ColorValue.Count -> compare(have.size.toDouble(), op, want.n.toDouble())
        is ColorValue.Multi -> if (op == "!=" || op == "<" ) have.size < 2 else have.size >= 2
        // c:c and id:c mean colorless.
        is ColorValue.Colors -> if (want.set.isEmpty()) {
            when (op) {
                "!=", ">" -> have.isNotEmpty()
                "<" -> false
                else -> have.isEmpty()
            }
        } else {
            val w = want.set
            when (op) {
                "=" -> have == w
                "!=" -> have != w
                ">=" -> have.containsAll(w)
                ">" -> have.containsAll(w) && have != w
                "<=" -> w.containsAll(have)
                "<" -> w.containsAll(have) && have != w
                else -> false
            }
        }
    }

    private fun rarityRank(v: String): Int? = when (v) {
        "c", "common" -> 0
        "u", "uncommon" -> 1
        "r", "rare" -> 2
        "s", "special" -> 3
        "m", "mythic" -> 4
        "b", "bonus" -> 5
        else -> null
    }

    /** Better condition ranks higher, so cond>=lp means LP or better. */
    private fun condRank(v: String): Int? = when (v.replace(" ", "").replace("_", "")) {
        "m", "mint", "nm", "nearmint" -> 5
        "lp", "lightlyplayed", "ex", "excellent", "sp" -> 4
        "mp", "moderatelyplayed", "played", "gd", "good" -> 3
        "hp", "heavilyplayed" -> 2
        "dmg", "d", "damaged", "poor", "po" -> 1
        else -> null
    }

    private fun statKey(v: String): String? = when (v) {
        "pow", "power" -> "pow"
        "tou", "toughness" -> "tou"
        "mv", "cmc", "manavalue" -> "mv"
        "pt", "powtou" -> "pt"
        else -> null
    }

    /** Power and toughness as numbers; "*" counts as 0, as on Scryfall. Null when the card has none. */
    private fun statNumber(s: String?): Double? {
        val t = s?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        NUMBER.find(t)?.let { return it.value.toDouble() }
        return if (t.contains('*') || t.contains('X', ignoreCase = true) || t.contains('?')) 0.0 else null
    }

    private fun stat(c: CollectionCard, key: String): Double? = when (key) {
        "pow" -> statNumber(c.power)
        "tou" -> statNumber(c.toughness)
        "mv" -> c.cmc
        "pt" -> { val p = statNumber(c.power); val t = statNumber(c.toughness); if (p == null || t == null) null else p + t }
        else -> null
    }

    /** Mana symbols: generic mana as a number, the rest counted by symbol ("W", "W/U", "X"). */
    data class Mana(val generic: Int, val symbols: Map<String, Int>)

    fun manaOf(s: String): Mana? {
        var generic = 0
        val syms = HashMap<String, Int>()
        fun add(sym: String) {
            val u = sym.uppercase()
            if (u.isEmpty()) return
            u.toIntOrNull()?.let { generic += it; return }
            // {U/W} and {W/U} are the same symbol.
            val key = if (u.contains('/')) u.split('/').sorted().joinToString("/") else u
            syms[key] = (syms[key] ?: 0) + 1
        }
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            when {
                ch == '{' -> {
                    val end = s.indexOf('}', i)
                    if (end < 0) return null
                    add(s.substring(i + 1, end)); i = end + 1
                }
                ch.isDigit() -> {
                    var j = i
                    while (j < s.length && s[j].isDigit()) j++
                    add(s.substring(i, j)); i = j
                }
                ch.isLetter() -> { add(ch.toString()); i++ }
                ch.isWhitespace() || ch == '/' -> i++
                else -> return null
            }
        }
        return Mana(generic, syms)
    }

    private fun contains(a: Mana, b: Mana): Boolean =
        a.generic >= b.generic && b.symbols.all { (k, n) -> (a.symbols[k] ?: 0) >= n }

    private fun manaMatch(have: Mana, op: String, want: Mana): Boolean = when (op) {
        ":", ">=" -> contains(have, want)
        "=" -> have == want
        "!=" -> have != want
        "<=" -> contains(want, have)
        ">" -> contains(have, want) && have != want
        "<" -> contains(want, have) && have != want
        else -> false
    }

    /**
     * Days a date value covers, end exclusive: "2024-05-01", "2024-05", "2024", "today", "yesterday", or a time
     * ago ("7d", "2w", "3m", "1y" = that day). Null when it isn't a date. [today] null only checks the form.
     */
    fun dateRange(v: String, today: LocalDate?): Pair<LocalDate, LocalDate>? {
        val now = today ?: LocalDate.of(2000, 1, 1)
        try {
            when {
                v == "today" -> return now to now.plusDays(1)
                v == "yesterday" -> return now.minusDays(1) to now
                Regex("\\d{4}").matches(v) -> LocalDate.of(v.toInt(), 1, 1).let { return it to it.plusYears(1) }
                Regex("\\d{4}-\\d{1,2}").matches(v) -> {
                    val (y, m) = v.split('-').map { it.toInt() }
                    LocalDate.of(y, m, 1).let { return it to it.plusMonths(1) }
                }
                Regex("\\d{4}-\\d{1,2}-\\d{1,2}").matches(v) -> {
                    val (y, m, d) = v.split('-').map { it.toInt() }
                    LocalDate.of(y, m, d).let { return it to it.plusDays(1) }
                }
                Regex("\\d+[dwmy]").matches(v) -> {
                    val n = v.dropLast(1).toLong()
                    val day = when (v.last()) {
                        'd' -> now.minusDays(n)
                        'w' -> now.minusWeeks(n)
                        'm' -> now.minusMonths(n)
                        else -> now.minusYears(n)
                    }
                    return day to day.plusDays(1)
                }
            }
        } catch (e: Exception) {
            return null
        }
        return null
    }

    private fun inRange(d: LocalDate, op: String, r: Pair<LocalDate, LocalDate>): Boolean {
        val (start, end) = r
        return when (op) {
            ":", "=" -> !d.isBefore(start) && d.isBefore(end)
            "!=" -> d.isBefore(start) || !d.isBefore(end)
            ">" -> !d.isBefore(end)
            ">=" -> !d.isBefore(start)
            "<" -> d.isBefore(start)
            "<=" -> d.isBefore(end)
            else -> false
        }
    }
}

/**
 * Scryfall's answers to [RemoteAsk]s, kept for the app's run so lists and counts can use them too.
 * Thread safe; small.
 */
object ScryfallQueryCache {
    sealed class State {
        object Loading : State()
        /** [keys] from [keysOf]; [partial] when there were more pages than were read. */
        class Done(val keys: Set<String>, val partial: Boolean) : State()
        class Failed(val message: String?) : State()
    }

    private val map = object : LinkedHashMap<RemoteAsk, State>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<RemoteAsk, State>?) = size > 40
    }

    fun state(a: RemoteAsk): State? = synchronized(map) { map[a] }
    fun put(a: RemoteAsk, s: State) = synchronized(map) { map[a] = s }
    fun remove(a: RemoteAsk) = synchronized(map) { map.remove(a) }
    fun hits(a: RemoteAsk): Set<String>? = (state(a) as? State.Done)?.keys

    /** Keys for one Scryfall result: oracle id and name, or set and number for [prints]. */
    fun keysOf(oracleId: String?, name: String, set: String, number: String, prints: Boolean): List<String> =
        if (prints) listOf("${set.lowercase()}/${number.lowercase()}")
        else listOfNotNull(oracleId, "name:" + name.lowercase())

    /** The keys an owned card is found under. */
    fun cardKeys(c: CollectionCard, prints: Boolean): List<String> =
        keysOf(c.oracleId, c.name, c.setCode, c.collectorNumber, prints)
}
