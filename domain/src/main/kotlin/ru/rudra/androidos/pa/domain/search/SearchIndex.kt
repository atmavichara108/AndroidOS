package ru.rudra.androidos.pa.domain.search

import java.util.Locale

/**
 * Pure full-text search primitives (docs/architecture.md: FTS5 in the local
 * store). The domain owns how text is tokenized and how a query matches, so
 * the Room FTS5 table and any UI apply identical semantics; this stays
 * dependency-free and unit-testable.
 *
 * Tokenizer keeps lowercase Unicode letter sequences (including Cyrillic) and
 * drops punctuation/whitespace. A query matches when every one of its tokens
 * is present (AND semantics) — the record contains all terms.
 */
object SearchTokens {

    /** Lowercased letter sequences (handles Cyrillic), deduplicated. */
    fun normalize(text: String): Set<String> {
        val tokens = HashSet<String>()
        val sb = StringBuilder()
        for (ch in text) {
            if (ch.isLetter()) {
                sb.append(ch)
            } else {
                if (sb.isNotEmpty()) {
                    tokens += sb.toString().lowercase(Locale.ROOT)
                    sb.setLength(0)
                }
            }
        }
        if (sb.isNotEmpty()) tokens += sb.toString().lowercase(Locale.ROOT)
        return tokens
    }

    /** Whether a document's token set [docTokens] satisfies a query's [queryTokens] (AND). */
    fun matches(docTokens: Set<String>, queryTokens: Set<String>): Boolean =
        docTokens.containsAll(queryTokens)
}

/**
 * In-memory inverted index (token -> document ids) mirroring the FTS5 table.
 * Useful for the app to answer search on the current in-session set without a
 * SQL query, and as the reference model the Room FTS5 query must match.
 */
class SearchIndex {

    private val byToken = HashMap<String, MutableSet<String>>()

    fun index(docId: String, text: String) {
        val tokens = SearchTokens.normalize(text)
        for (token in tokens) {
            byToken.getOrPut(token) { HashSet() }.add(docId)
        }
    }

    fun indexAll(docs: Collection<Pair<String, String>>) {
        docs.forEach { (id, text) -> index(id, text) }
    }

    fun remove(docId: String) {
        byToken.values.forEach { it.remove(docId) }
    }

    /** Document ids containing every token of [query]. Empty query matches nothing. */
    fun search(query: String): List<String> {
        val queryTokens = SearchTokens.normalize(query)
        if (queryTokens.isEmpty()) return emptyList()
        var result: Set<String>? = null
        for (token in queryTokens) {
            val ids = byToken[token] ?: return emptyList()
            result = if (result == null) ids else result.intersect(ids)
        }
        return result?.toList() ?: emptyList()
    }
}