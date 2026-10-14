package es.sebas1705.ironlog.core.common.extensions

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

fun <T> Flow<T>.mapToResult(): Flow<es.sebas1705.ironlog.core.common.utils.AppResult<T>> =
    map { es.sebas1705.ironlog.core.common.utils.AppResult.Success(it) as es.sebas1705.ironlog.core.common.utils.AppResult<T> }
        .catch { emit(es.sebas1705.ironlog.core.common.utils.AppResult.Error(it.message ?: "Error")) }
