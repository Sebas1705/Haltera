package es.sebas1705.ironlog.feature.profile

import es.sebas1705.ironlog.core.common.mvi.MVIBaseState
import es.sebas1705.ironlog.data.models.User

data class ProfileState(
    val user      : User?   = null,
    val isLoading : Boolean = false,
    val isSaving  : Boolean = false,
    val saveSuccess: Boolean = false,
    val error     : String? = null,
) : MVIBaseState
