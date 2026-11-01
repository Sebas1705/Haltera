package es.sebas1705.haltera.data.sync

import es.sebas1705.haltera.data.sync.SyncCollections.EXERCISES
import es.sebas1705.haltera.data.sync.SyncCollections.SESSIONS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Edits, deletes and share origin of custom exercises across two phones (core design §3.1.1, sync design §3):
 * the `exercises` collection carries the edit, a hard delete is a tombstone, a soft delete an update, and no
 * remote change ever breaks a session that logged the exercise on the receiving phone.
 */
class CustomExerciseSyncTest {
    private val cloud = FakeCloud()
    private val d1 = Device(cloud, Dispatchers.Unconfined)
    private val d2 = Device(cloud, Dispatchers.Unconfined)

    private fun Device.exercise(id: String = "user-1") = db.exerciseEntityQueries.selectById(id).executeAsOneOrNull()

    private fun Device.variants(id: String = "user-1") =
        db.exerciseVariantEntityQueries.selectByExercise(id).executeAsList()

    /** A finished session on this phone that logged `user-1.barbell` (no routine). */
    private fun Device.logged(sessionId: String = "w9") {
        db.workoutSessionEntityQueries.insert(sessionId, 10, null, null, "")
        db.sessionExerciseEntityQueries.insert("$sessionId-e1", sessionId, 0, "user-1.barbell", null)
        db.setLogEntityQueries.insert("$sessionId-t1", "$sessionId-e1", 0, "WORKING", 5, 60_000, null, null, null, null, 1, 20)
        db.workoutSessionEntityQueries.finish(30, sessionId)
    }

    /** Deletes the exercise rows as the repository's hard delete does (variants, then the exercise). */
    private fun Device.hardDelete(id: String = "user-1") {
        variants(id).forEach { db.exerciseVariantEntityQueries.deleteUser(it.id) }
        db.exerciseEntityQueries.deleteUser(id)
    }

    @Test
    fun `an edit propagates with the same ids`() = runTest {
        d1.userExercise()
        d1.engine.syncNow()
        d2.engine.syncNow()

        cloud.tick()
        d1.db.exerciseEntityQueries.updateUser(
            nameEn = "Sled", nameEs = "Trineo", nameEnSearch = "sled", nameEsSearch = "trineo", category = "STRENGTH",
            trackingType = "WEIGHT_REPS", level = "EXPERT", primaryMuscles = "QUADRICEPS", secondaryMuscles = "GLUTES",
            instructionsEn = "[\"Push.\"]", instructionsEs = "[\"Empuja.\"]", updatedAt = 9, id = "user-1",
        )
        d1.db.exerciseVariantEntityQueries.insertUser(
            "user-1.machine",
            "user-1",
            1,
            "MACHINE",
            "MACHINE",
            null,
            null,
            9
        )
        d1.engine.syncNow()
        d2.engine.syncNow()

        assertEquals(d1.read(EXERCISES, "user-1"), d2.read(EXERCISES, "user-1"))
        assertEquals("Sled", d2.exercise()?.nameEn)
        assertEquals(listOf("user-1.barbell", "user-1.machine"), d2.variants().map { it.id })
        assertTrue(d2.dirty().isEmpty())
    }

    @Test
    fun `a hard delete is a tombstone that removes the exercise on the other phone`() = runTest {
        d1.userExercise()
        d1.engine.syncNow()
        d2.engine.syncNow()
        assertNotNull(d2.exercise())

        d1.hardDelete()
        assertEquals(1L, d1.state(EXERCISES, "user-1")?.deleted)
        d1.engine.syncNow()
        val doc = cloud.doc("uid-1", EXERCISES, "user-1")!!
        assertTrue(doc.deleted)
        assertEquals("", doc.payload)

        d2.engine.syncNow()
        assertNull(d2.exercise())
        assertTrue(d2.variants().isEmpty())
        assertEquals(1L, d2.state(EXERCISES, "user-1")?.deleted)
        assertTrue(d2.dirty().isEmpty())
    }

    @Test
    fun `a tombstone for an exercise this phone logged hides it and keeps the session`() = runTest {
        d1.userExercise()
        d1.engine.syncNow()
        d2.engine.syncNow()
        // Phone 2 logs it (offline, say) before the delete made on phone 1 arrives.
        d2.logged()
        d1.hardDelete()
        d1.engine.syncNow()

        d2.engine.syncNow()
        val kept = assertNotNull(d2.exercise())
        assertEquals(1L, kept.deprecated)
        assertEquals(listOf("user-1.barbell"), d2.variants().map { it.id })
        assertEquals(1, d2.db.sessionExerciseEntityQueries.selectBySession("w9").executeAsList().size)
        assertEquals(1L, d2.state(EXERCISES, "user-1")?.deleted)
        // The session still syncs and points at the kept variant.
        d2.engine.syncNow()
        assertTrue(d2.read(SESSIONS, "w9")!!.contains("user-1.barbell"))
    }

    @Test
    fun `a soft delete syncs as an update`() = runTest {
        d1.userExercise()
        d1.logged()
        d1.engine.syncNow()
        d2.engine.syncNow()

        cloud.tick()
        d1.db.exerciseEntityQueries.setUserDeprecated(deprecated = 1, updatedAt = 50, id = "user-1")
        assertEquals(0L, d1.state(EXERCISES, "user-1")?.deleted)
        d1.engine.syncNow()
        assertEquals(false, cloud.doc("uid-1", EXERCISES, "user-1")?.deleted)

        d2.engine.syncNow()
        assertEquals(1L, d2.exercise()?.deprecated)
        assertEquals(1, d2.db.sessionExerciseEntityQueries.selectBySession("w9").executeAsList().size)

        // Restoring syncs back the same way.
        cloud.tick()
        d1.db.exerciseEntityQueries.setUserDeprecated(deprecated = 0, updatedAt = 60, id = "user-1")
        d1.engine.syncNow()
        d2.engine.syncNow()
        assertEquals(0L, d2.exercise()?.deprecated)
    }

    @Test
    fun `a remote edit without a variant this phone logged deprecates it instead of deleting it`() = runTest {
        d1.userExercise()
        d1.db.exerciseVariantEntityQueries.insertUser(
            "user-1.machine",
            "user-1",
            1,
            "MACHINE",
            "MACHINE",
            null,
            null,
            5
        )
        d1.engine.syncNow()
        d2.engine.syncNow()
        d2.logged()

        // Phone 1 (which never logged it) deletes the barbell variant.
        cloud.tick()
        d1.db.exerciseVariantEntityQueries.deleteUser("user-1.barbell")
        d1.engine.syncNow()
        d2.engine.syncNow()

        val variants = d2.variants().associateBy { it.id }
        assertEquals(setOf("user-1.barbell", "user-1.machine"), variants.keys)
        assertEquals(1L, variants.getValue("user-1.barbell").deprecated)
        assertEquals(0L, variants.getValue("user-1.machine").deprecated)
        assertEquals(1, d2.db.sessionExerciseEntityQueries.selectBySession("w9").executeAsList().size)
    }

    @Test
    fun `the creator and origin of an imported exercise sync`() = runTest {
        d1.userExercise()
        d1.db.exerciseEntityQueries.setShareOrigin(author = "Ana", originId = "user-abc", id = "user-1")
        d1.engine.syncNow()
        d2.engine.syncNow()
        assertEquals("Ana", d2.exercise()?.author)
        assertEquals("user-abc", d2.exercise()?.originId)
        assertEquals("user-1", d2.db.exerciseEntityQueries.selectUserByOrigin("user-abc").executeAsOne().id)
    }

    @Test
    fun `documents written before schema 6 still apply`() = runTest {
        // An exercise document from an older app: no author or originId.
        val payload = """
            {"id":"user-7","nameEn":"Old","nameEs":"Viejo","category":"STRENGTH","trackingType":"REPS",
             "level":"BEGINNER","primaryMuscles":"","secondaryMuscles":"","instructionsEn":"[]","instructionsEs":"[]",
             "updatedAt":3,"variants":[{"id":"user-7.none","position":0,"equipment":"NONE","loadType":"NONE",
             "frames":"[]","updatedAt":3}]}
        """.trimIndent()
        cloud.put("uid-1", SyncDocument(EXERCISES, "user-7", updatedAt = 100, deleted = false, payload = payload))
        d2.engine.syncNow()
        val old = assertNotNull(d2.exercise("user-7"))
        assertNull(old.author)
        assertNull(old.originId)
        assertEquals(listOf("user-7.none"), d2.variants("user-7").map { it.id })
    }
}
