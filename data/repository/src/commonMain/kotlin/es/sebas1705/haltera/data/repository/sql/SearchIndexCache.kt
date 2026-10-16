package es.sebas1705.haltera.data.repository.sql

import app.cash.sqldelight.Query
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * A value built from the database (a search index) once per [key], rebuilt only after a table read by one
 * of [watched] changes. Searching on every keystroke then costs only the in-memory ranking.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class SearchIndexCache<K, V : Any>(watched: List<Query<*>>, private val build: (K) -> V) {
    private val version = AtomicInt(0)
    private val lock = Mutex()
    private val values = HashMap<K, Pair<Int, V>>()

    // Kept so the listeners live as long as the cache.
    private val queries = watched
    private val listener = Query.Listener { version.incrementAndFetch() }

    init {
        queries.forEach { it.addListener(listener) }
    }

    suspend fun get(key: K): V = lock.withLock {
        val current = version.load()
        values[key]?.takeIf { it.first == current }?.second
            ?: build(key).also { values[key] = current to it }
    }
}
