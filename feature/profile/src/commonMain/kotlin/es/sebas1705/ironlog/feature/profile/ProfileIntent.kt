package es.sebas1705.ironlog.feature.profile

import es.sebas1705.ironlog.core.common.mvi.MVIBaseIntent
import es.sebas1705.ironlog.data.models.User

sealed interface ProfileIntent : MVIBaseIntent {
    data object Load                          : ProfileIntent
    data class  Save(val user: User)          : ProfileIntent
    data object ClearSuccess                  : ProfileIntent
    data object ClearError                    : ProfileIntent
}
