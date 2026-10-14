package es.sebas1705.ironlog.feature.home

import es.sebas1705.ironlog.core.common.mvi.MVIBaseIntent

sealed interface HomeIntent : MVIBaseIntent {
    data object Load       : HomeIntent
    data object Refresh    : HomeIntent
    data object ClearError : HomeIntent
}
