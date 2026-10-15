package es.sebas1705.haltera.domain.usecases

import es.sebas1705.haltera.core.common.utils.AppResult

abstract class UseCase<in P, R> {
    suspend operator fun invoke(params: P): AppResult<R> = execute(params)
    protected abstract suspend fun execute(params: P): AppResult<R>
}

abstract class NoParamUseCase<R> {
    suspend operator fun invoke(): AppResult<R> = execute()
    protected abstract suspend fun execute(): AppResult<R>
}
