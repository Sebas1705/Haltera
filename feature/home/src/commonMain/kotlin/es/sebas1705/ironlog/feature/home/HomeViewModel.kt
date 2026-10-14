package es.sebas1705.ironlog.feature.home

import es.sebas1705.ironlog.core.common.mvi.MVIBaseViewModel
import es.sebas1705.ironlog.core.common.utils.AppResult
import es.sebas1705.ironlog.domain.usecases.user.GetMeUseCase
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Job

class HomeViewModel(
    private val getMe: GetMeUseCase,
) : MVIBaseViewModel<HomeState, HomeIntent>() {

    init { onEvent(HomeIntent.Load) }

    override fun initState() = HomeState()

    override fun intentHandler(intent: HomeIntent): Job = when (intent) {
        HomeIntent.Load, HomeIntent.Refresh -> load()
        HomeIntent.ClearError -> execute { updateState { it.copy(error = null) } }
    }

    private fun load() = execute {
        updateState { it.copy(isLoading = true, error = null) }
        when (val result = getMe()) {
            is AppResult.Success -> updateState {
                it.copy(
                    isLoading = false,
                    user      = result.data,
                    items     = listOf(
                        HomeItem("1", "Bienvenido", result.data.name),
                        HomeItem("2", "Email", result.data.email),
                    ).toPersistentList()
                )
            }
            is AppResult.Error -> updateState { it.copy(isLoading = false, error = result.message) }
            else -> {}
        }
    }
}
