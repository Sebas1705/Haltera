package es.sebas1705.ironlog.data.repository

import es.sebas1705.ironlog.core.common.utils.AppResult
import es.sebas1705.ironlog.core.common.utils.safeApiCall
import es.sebas1705.ironlog.data.local.dao.UserLocalDao
import es.sebas1705.ironlog.data.models.User
import es.sebas1705.ironlog.data.network.api.UserApi
import kotlinx.coroutines.flow.Flow

interface IUserRepository {
    fun observeUsers(): Flow<List<User>>
    suspend fun getMe(): AppResult<User>
    suspend fun getById(id: String): AppResult<User>
    suspend fun updateProfile(user: User): AppResult<User>
}

class UserRepository(
    private val api: UserApi,
    private val dao: UserLocalDao,
) : IUserRepository {

    override fun observeUsers(): Flow<List<User>> = dao.observeAll()

    override suspend fun getMe(): AppResult<User> = safeApiCall {
        val result = api.getMe()
        result.data?.also { dao.upsert(it) } ?: error(result.error ?: "Unknown error")
    }

    override suspend fun getById(id: String): AppResult<User> = safeApiCall {
        val result = api.getById(id)
        result.data?.also { dao.upsert(it) } ?: error(result.error ?: "Unknown error")
    }

    override suspend fun updateProfile(user: User): AppResult<User> = safeApiCall {
        val result = api.updateProfile(user)
        result.data?.also { dao.upsert(it) } ?: error(result.error ?: "Unknown error")
    }
}
