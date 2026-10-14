package es.sebas1705.ironlog.data.repository.di

import es.sebas1705.ironlog.data.local.catalog.CatalogImporter
import es.sebas1705.ironlog.data.local.db.AppDatabase
import es.sebas1705.ironlog.data.repository.BodyWeightRepository
import es.sebas1705.ironlog.data.repository.CatalogRepository
import es.sebas1705.ironlog.data.repository.ExerciseRepository
import es.sebas1705.ironlog.data.repository.RoutineRepository
import es.sebas1705.ironlog.data.repository.SessionRepository
import es.sebas1705.ironlog.data.repository.share.DefaultRoutineSharing
import es.sebas1705.ironlog.data.repository.share.RoutineSharing
import es.sebas1705.ironlog.data.repository.sql.HistoryLoader
import es.sebas1705.ironlog.data.repository.sql.SqlBodyWeightRepository
import es.sebas1705.ironlog.data.repository.sql.SqlCatalogRepository
import es.sebas1705.ironlog.data.repository.sql.SqlExerciseRepository
import es.sebas1705.ironlog.data.repository.sql.SqlRoutineRepository
import es.sebas1705.ironlog.data.repository.sql.SqlSessionRepository
import es.sebas1705.ironlog.data.routines.FamousRoutines
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val IoDispatcher = named("io")

/** Binds every repository. Needs an [AppDatabase] from the app module. */
@OptIn(ExperimentalTime::class)
val repositoryModule = module {
    single<CoroutineDispatcher>(IoDispatcher) { Dispatchers.IO }
    single<() -> Long>(named("now")) { { Clock.System.now().toEpochMilliseconds() } }
    single { CatalogImporter(get<AppDatabase>(), now = get(named("now"))) }
    single { HistoryLoader(get()) }
    single<CatalogRepository> { SqlCatalogRepository(get(), get(), get(IoDispatcher)) }
    single<ExerciseRepository> { SqlExerciseRepository(get(), get(), get(IoDispatcher), get(named("now"))) }
    single<RoutineRepository> { SqlRoutineRepository(get(), FamousRoutines.all, get(IoDispatcher), get(named("now"))) }
    single<SessionRepository> { SqlSessionRepository(get(), get(), get(IoDispatcher), get(named("now"))) }
    single<BodyWeightRepository> { SqlBodyWeightRepository(get(), get(IoDispatcher), get(named("now"))) }
    single<RoutineSharing> { DefaultRoutineSharing(get(), get(), get(named("now"))) }
}
