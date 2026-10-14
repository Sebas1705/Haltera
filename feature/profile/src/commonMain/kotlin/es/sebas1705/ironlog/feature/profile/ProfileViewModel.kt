package es.sebas1705.ironlog.feature.profile

import es.sebas1705.ironlog.core.common.mvi.MVIBaseViewModel
import es.sebas1705.ironlog.core.common.utils.AppResult
import es.sebas1705.ironlog.data.models.User
import es.sebas1705.ironlog.domain.usecases.user.GetMeUseCase
import es.sebas1705.ironlog.domain.usecases.user.UpdateProfileUseCase
import kotlinx.coroutines.Job

class ProfileViewModel(
    private val getMe         : GetMeUseCase,
    private val updateProfile : UpdateProfileUseCase,
) : MVIBaseViewModel<ProfileState, ProfileIntent>() {

    init { onEvent(ProfileIntent.Load) }

    override fun initState() = ProfileState()

    override fun intentHandler(intent: ProfileIntent): Job = when (intent) {
        ProfileIntent.Load        -> load()
        is ProfileIntent.Save     -> save(intent.user)
        ProfileIntent.ClearSuccess -> execute { updateState { it.copy(saveSuccess = false) } }
        ProfileIntent.ClearError   -> execute { updateState { it.copy(error = null) } }
    }

    private fun load() = execute {
        updateState { it.copy(isLoading = true, error = null) }
        when (val r = getMe()) {
            is AppResult.Success -> updateState { it.copy(isLoading = false, user = r.data) }
            is AppResult.Error   -> updateState { it.copy(isLoading = false, error = r.message) }
            else -> {}
        }
    }

    private fun save(user: User) = execute {
        updateState { it.copy(isSaving = true, error = null, saveSuccess = false) }
        when (val r = updateProfile(user)) {
            is AppResult.Success -> updateState { it.copy(isSaving = false, user = r.data, saveSuccess = true) }
            is AppResult.Error   -> updateState { it.copy(isSaving = false, error = r.message) }
            else -> {}
        }
    }
}
