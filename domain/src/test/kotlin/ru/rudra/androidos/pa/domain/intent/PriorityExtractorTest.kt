package ru.rudra.androidos.pa.domain.intent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PriorityExtractorTest {

    @Test
    fun urgentWordsAreHigh() {
        assertEquals("HIGH", PriorityExtractor.extract("срочно позвонить ивану"))
        assertEquals("HIGH", PriorityExtractor.extract("это важно, не забудь"))
        assertEquals("HIGH", PriorityExtractor.extract("критическая задача"))
        assertEquals("HIGH", PriorityExtractor.extract("сделать как можно скорее"))
    }

    @Test
    fun deferringWordsAreLow() {
        assertEquals("LOW", PriorityExtractor.extract("потом разобраться с почтой"))
        assertEquals("LOW", PriorityExtractor.extract("это несрочно"))
        assertEquals("LOW", PriorityExtractor.extract("купить когда-нибудь книгу"))
    }

    @Test
    fun negatedUrgencyIsLowNotHigh() {
        assertEquals("LOW", PriorityExtractor.extract("не срочно, но сделай"))
        assertEquals("LOW", PriorityExtractor.extract("не важно когда"))
    }

    @Test
    fun noCueIsNull() {
        assertNull(PriorityExtractor.extract("позвонить маме завтра"))
        assertNull(PriorityExtractor.extract("встреча в офисе"))
    }
}
