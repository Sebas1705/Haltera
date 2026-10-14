package es.sebas1705.ironlog.data.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.russhwolf.settings.MapSettings
import es.sebas1705.ironlog.core.datastore.KeyValueSettingsRepository
import es.sebas1705.ironlog.data.local.catalog.CatalogImporter
import es.sebas1705.ironlog.data.local.db.AppDatabase
import es.sebas1705.ironlog.data.local.db.SyncState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.util.Properties

/** In-memory database with the schema created and foreign keys on, as on the device, plus a tiny catalogue. */
fun testDatabase(): AppDatabase = AppDatabase(
    JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        .also { AppDatabase.Schema.create(it) },
).also { CatalogImporter(it, now = { 0L }).importIfNewer(TEST_CATALOG) }

val TEST_CATALOG = """
    {"catalogVersion":1,"contentHash":"h","deprecatedIds":[],"exercises":[
      {"id":"bench-press","name":{"en":"Bench Press","es":"Press de banca"},"category":"STRENGTH",
       "trackingType":"WEIGHT_REPS","level":"BEGINNER","primaryMuscles":["CHEST"],"secondaryMuscles":["TRICEPS"],
       "instructions":{"en":["Lie down."],"es":["Túmbate."]},
       "variants":[{"id":"bench-press.barbell","equipment":"BARBELL","loadType":"BARBELL","frames":["b/0.jpg"]}]},
      {"id":"squat","name":{"en":"Squat","es":"Sentadilla"},"category":"STRENGTH",
       "trackingType":"WEIGHT_REPS","level":"BEGINNER","primaryMuscles":["QUADRICEPS"],"secondaryMuscles":["GLUTES"],
       "instructions":{"en":[],"es":[]},
       "variants":[{"id":"squat.barbell","equipment":"BARBELL","loadType":"BARBELL","frames":[]}]}
    ]}
""".trimIndent()

/** Shared in-memory "cloud": `users/{uid}/{collection}/{id}`, with live listeners and an offline switch. */
class FakeCloud : RemoteStore {
    private val docs = MutableStateFlow<Map<Triple<String, String, String>, SyncDocument>>(emptyMap())
    val online = MutableStateFlow(true)
    var pushes = 0
        private set

    override suspend fun push(uid: String, documents: List<SyncDocument>) {
        check(online.value) { "offline" }
        pushes++
        docs.value += documents.associateBy { Triple(uid, it.collection, it.id) }
    }

    override fun changes(uid: String, collection: String, sinceUpdatedAt: Long): Flow<List<SyncDocument>> =
        docs.onEach { check(online.value) { "offline" } }
            .map { all ->
                all.filterKeys { it.first == uid && it.second == collection }.values
                    .filter { it.updatedAt > sinceUpdatedAt }.sortedBy { it.updatedAt }
            }
            .distinctUntilChanged()

    override suspend fun deleteAll(uid: String) {
        docs.value = docs.value.filterKeys { it.first != uid }
    }

    fun doc(uid: String, collection: String, id: String): SyncDocument? = docs.value[Triple(uid, collection, id)]

    fun all(uid: String): List<SyncDocument> = docs.value.filterKeys { it.first == uid }.values.toList()

    /** Writes a document as another device would. */
    fun put(uid: String, doc: SyncDocument) {
        docs.value += Triple(uid, doc.collection, doc.id) to doc
    }
}

class FakeAccount(initial: AccountSession = AccountSession.SignedIn("uid-1", isAnonymous = true)) : AccountService {
    private val state = MutableStateFlow(initial)
    override val session: StateFlow<AccountSession> = state

    fun set(session: AccountSession) {
        state.value = session
    }

    override suspend fun ensureSignedIn() = Unit
    override suspend fun linkGoogle(credentials: GoogleIdToken): LinkResult = LinkResult.Linked
    override suspend fun signInWithGoogle(credentials: GoogleIdToken) = Unit
    override suspend fun signOut() = set(AccountSession.SignedOut)
    override suspend fun deleteUser() = set(AccountSession.SignedOut)
}

/** One phone: its own database and settings, the shared cloud. */
internal class Device(
    cloud: FakeCloud,
    io: CoroutineDispatcher,
    val account: FakeAccount = FakeAccount(),
) {
    val db = testDatabase()
    val settings = KeyValueSettingsRepository(MapSettings())
    val bridge = SettingsSyncBridge(settings, db)
    val engine = SyncEngineImpl(db, cloud, account, bridge, io, now = { System.currentTimeMillis() })
    val store = AggregateStore(db, bridge)

    fun state(collection: String, id: String): SyncState? =
        db.syncTrackingQueries.selectState(collection, id).executeAsOneOrNull()

    fun dirty(): List<SyncState> = db.syncTrackingQueries.selectDirty(1_000).executeAsList()

    fun read(collection: String, id: String): String? = store.read(collection, id)

    // Writes as the repositories would (triggers track them).

    fun routine(id: String = "r1", name: String = "Push Pull Legs") {
        db.routineEntityQueries.insert(id, name, "", null, null, 1, 1)
        db.routineDayEntityQueries.insert("$id-d1", id, 0, "Push", "TRAINING")
        db.routineDayEntityQueries.insert("$id-d2", id, 1, "Rest", "REST")
        db.routineSlotEntityQueries.insert(
            "$id-s1", "$id-d1", 0, """{"kind":"fixed","variantId":"bench-press.barbell"}""", 3, 1, "{}", 90, 120, null,
            0, "", "{}",
        )
    }

    fun userExercise(id: String = "user-1") {
        db.exerciseEntityQueries.insertUser(
            id = id, nameEn = "My lift", nameEs = "Mi ejercicio", nameEnSearch = "my lift", nameEsSearch = "mi ejercicio",
            category = "STRENGTH", trackingType = "WEIGHT_REPS", level = "BEGINNER", primaryMuscles = "CHEST",
            secondaryMuscles = "", instructionsEn = "[]", instructionsEs = "[]", updatedAt = 5,
        )
        db.exerciseVariantEntityQueries.insertUser(
            id = "$id.barbell",
            exerciseId = id,
            position = 0,
            equipment = "BARBELL",
            loadType = "BARBELL",
            nameEn = null,
            nameEs = null,
            updatedAt = 5,
        )
    }

    /** A session of routine r1 with a catalogue exercise linked to its slot and a USER exercise. */
    fun session(id: String = "w1") {
        db.workoutSessionEntityQueries.insert(id, 10, "r1", 0, "Push")
        db.sessionExerciseEntityQueries.insert("$id-e1", id, 0, "bench-press.barbell", "r1-s1")
        db.sessionExerciseEntityQueries.insert("$id-e2", id, 1, "user-1.barbell", null)
        db.setLogEntityQueries.insert("$id-t1", "$id-e1", 0, "WORKING", 5, 100_000, null, null, null, 8.5, 1, 20)
        db.setLogEntityQueries.insert("$id-t2", "$id-e2", 0, "WARMUP", 10, 40_000, """{"x":1}""", null, null, null, 0, null)
        db.workoutSessionEntityQueries.finish(30, id)
    }

    fun bodyWeight(id: String = "bw1", grams: Long = 80_000) {
        db.bodyWeightEntityQueries.upsert(id, 1_000, grams, "morning")
    }
}

/** Waits a few milliseconds so the next tracked change gets a later `updatedAt` than the previous one. */
fun tick() = Thread.sleep(3)
