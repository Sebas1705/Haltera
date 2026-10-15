package es.sebas1705.ironlog.feature.routines.model

import kotlinx.coroutines.CancellationException

/** The UI language ("en", "es"…) used for catalogue names and template copies. */
internal fun interface AppLanguage {
    fun code(): String
}

/** Epoch millis now; a seam so tests fix the time. */
internal fun interface RoutinesClock {
    fun now(): Long
}

/** Runs [block], turning failures into a [Result] but letting cancellation through. */
@Suppress("TooGenericExceptionCaught") // Repository failures of any kind become a message, never a crash.
internal suspend inline fun <T> attempt(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

/** The position [delta] places from [index] in a list of [size] items, or null when it falls outside. */
internal fun movedIndex(index: Int, delta: Int, size: Int): Int? = (index + delta).takeIf { it in 0 until size }
