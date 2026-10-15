package es.sebas1705.haltera.core.common.extensions

import es.sebas1705.haltera.core.common.utils.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

fun <T> Flow<T>.mapToResult(): Flow<AppResult<T>> =
    map<T, AppResult<T>> { AppResult.Success(it) }
        .catch {
            if (it is CancellationException) throw it
            emit(AppResult.Error(it.message ?: "Error", it))
        }
