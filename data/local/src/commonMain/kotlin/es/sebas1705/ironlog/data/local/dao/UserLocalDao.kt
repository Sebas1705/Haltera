package es.sebas1705.ironlog.data.local.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import es.sebas1705.ironlog.data.local.db.AppDatabase
import es.sebas1705.ironlog.data.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserLocalDao(private val db: AppDatabase) {

    fun observeAll(): Flow<List<User>> =
        db.userEntityQueries.selectAll().asFlow()
            .mapToList(Dispatchers.IO)
            .map { list -> list.map { it.toUser() } }

    suspend fun upsert(user: User) = db.userEntityQueries.upsertUser(
        id         = user.id,
        name       = user.name,
        email      = user.email,
        avatar_url = user.avatarUrl,
        created_at = user.createdAt?.toString(),
    )

    suspend fun deleteById(id: String) = db.userEntityQueries.deleteById(id)

    suspend fun deleteAll() = db.userEntityQueries.deleteAll()

    private fun es.sebas1705.ironlog.data.local.db.UserEntity.toUser() = User(
        id        = id,
        name      = name,
        email     = email,
        avatarUrl = avatar_url,
    )
}
