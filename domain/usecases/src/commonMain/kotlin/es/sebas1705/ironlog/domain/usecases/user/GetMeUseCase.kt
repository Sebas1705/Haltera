package es.sebas1705.ironlog.domain.usecases.user

import es.sebas1705.ironlog.core.common.utils.AppResult
import es.sebas1705.ironlog.data.models.User
import es.sebas1705.ironlog.data.repository.IUserRepository
import es.sebas1705.ironlog.domain.usecases.NoParamUseCase

class GetMeUseCase(private val repository: IUserRepository) : NoParamUseCase<User>() {
    override suspend fun execute(): AppResult<User> = repository.getMe()
}
