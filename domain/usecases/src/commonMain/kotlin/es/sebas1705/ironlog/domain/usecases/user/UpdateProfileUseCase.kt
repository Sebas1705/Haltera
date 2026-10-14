package es.sebas1705.ironlog.domain.usecases.user

import es.sebas1705.ironlog.core.common.utils.AppResult
import es.sebas1705.ironlog.data.models.User
import es.sebas1705.ironlog.data.repository.IUserRepository
import es.sebas1705.ironlog.domain.usecases.UseCase

class UpdateProfileUseCase(private val repository: IUserRepository) : UseCase<User, User>() {
    override suspend fun execute(params: User): AppResult<User> = repository.updateProfile(params)
}
