package ru.rudra.androidos.pa.domain.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchIndexTest {

    @Test
    fun `normalize lowercases and keeps cyrillic`() {
        val tokens = SearchTokens.normalize("Позвонить ВитЕ, купить молоко!")
        assertEquals(setOf("позвонить", "вите", "купить", "молоко"), tokens)
    }

    @Test
    fun `normalize drops punctuation and digits`() {
        assertEquals(setOf("abc", "def"), SearchTokens.normalize("abc, 123 def-7"))
        assertEquals(emptySet<String>(), SearchTokens.normalize("  ,  !! 123 "))
    }

    @Test
    fun `and query matches only docs with all tokens`() {
        val idx = SearchIndex()
        idx.index("1", "купить молоко")
        idx.index("2", "купить хлеб")
        idx.index("3", "молоко и хлеб")
        assertEquals(listOf("1"), idx.search("купить молоко"))
        assertEquals(listOf("2", "3"), idx.search("хлеб").sorted())
    }

    @Test
    fun `empty or no-token query matches nothing`() {
        val idx = SearchIndex()
        idx.index("1", "купить молоко")
        assertEquals(emptyList(), idx.search(""))
        assertEquals(emptyList(), idx.search("!! 123"))
        assertEquals(emptyList(), idx.search("неттакогослова"))
    }

    @Test
    fun `query is case-insensitive`() {
        val idx = SearchIndex()
        idx.index("1", "Позвонить Вите")
        assertEquals(listOf("1"), idx.search("позвонить вите"))
        assertEquals(listOf("1"), idx.search("ПОЗВОНИТЬ"))
    }

    @Test
    fun `remove drops a doc from the index`() {
        val idx = SearchIndex()
        idx.index("1", "купить молоко")
        idx.index("2", "купить хлеб")
        idx.remove("1")
        assertEquals(listOf("2"), idx.search("купить"))
        assertEquals(emptyList(), idx.search("молоко"))
    }

    @Test
    fun `matches respects and semantics`() {
        val doc = setOf("а", "б", "в")
        assertTrue(SearchTokens.matches(doc, setOf("б", "а")))
        assertFalse(SearchTokens.matches(doc, setOf("а", "г")))
        assertTrue(SearchTokens.matches(doc, emptySet()))
    }
}