package es.sebas1705.ironlog.feature.home

import es.sebas1705.ironlog.core.common.mvi.MVIBaseState
import es.sebas1705.ironlog.data.models.User
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class HomeState(
    val user      : User?            = null,
    val items     : ImmutableList<HomeItem> = persistentListOf(),
    val isLoading : Boolean          = false,
    val error     : String?          = null,
) : MVIBaseState

data class HomeItem(val id: String, val title: String, val subtitle: String)
