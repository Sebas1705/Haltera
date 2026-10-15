package es.sebas1705.haltera.data.local

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MetaQueriesTest {
    @Test
    fun `missing key returns null`() {
        assertNull(inMemoryDatabase().metaQueries.get("catalogVersion").executeAsOneOrNull())
    }

    @Test
    fun `put overwrites the previous value`() {
        val db = inMemoryDatabase()
        db.metaQueries.put("catalogVersion", "1")
        db.metaQueries.put("catalogVersion", "2")
        assertEquals("2", db.metaQueries.get("catalogVersion").executeAsOne())
    }
}
