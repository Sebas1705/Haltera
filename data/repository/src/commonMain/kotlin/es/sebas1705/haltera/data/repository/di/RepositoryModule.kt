package es.sebas1705.haltera.data.repository.di

import es.sebas1705.haltera.data.local.catalog.CatalogImporter
import es.sebas1705.haltera.data.local.db.AppDatabase
import es.sebas1705.haltera.data.local.foods.FoodImporter
import es.sebas1705.haltera.data.repository.BodyMeasurementRepository
import es.sebas1705.haltera.data.repository.BodyWeightRepository
import es.sebas1705.haltera.data.repository.ProgressPhotoRepository
import es.sebas1705.haltera.data.repository.photos.FileProgressPhotoRepository
import es.sebas1705.haltera.data.repository.photos.PhotoFiles
import es.sebas1705.haltera.data.repository.CatalogRepository
import es.sebas1705.haltera.data.repository.ExerciseRepository
import es.sebas1705.haltera.data.repository.export.DataExporter
import es.sebas1705.haltera.data.repository.export.SqlDataExporter
import es.sebas1705.haltera.data.repository.importer.HistoryImporter
import es.sebas1705.haltera.data.repository.importer.SqlHistoryImporter
import es.sebas1705.haltera.data.repository.health.HealthGateway
import es.sebas1705.haltera.data.repository.health.HealthRepository
import es.sebas1705.haltera.data.repository.health.SqlHealthRepository
import es.sebas1705.haltera.data.repository.health.UnavailableHealthGateway
import es.sebas1705.haltera.data.repository.NutritionRepository
import es.sebas1705.haltera.data.repository.RoutineRepository
import es.sebas1705.haltera.data.repository.SessionRepository
import es.sebas1705.haltera.data.repository.share.DefaultRoutineSharing
import es.sebas1705.haltera.data.repository.share.RoutineSharing
import es.sebas1705.haltera.data.repository.sql.HistoryLoader
import es.sebas1705.haltera.data.repository.sql.SqlBodyMeasurementRepository
import es.sebas1705.haltera.data.repository.sql.SqlBodyWeightRepository
import es.sebas1705.haltera.data.repository.sql.SqlCatalogRepository
import es.sebas1705.haltera.data.repository.sql.SqlExerciseRepository
import es.sebas1705.haltera.data.repository.sql.SqlNutritionRepository
import es.sebas1705.haltera.data.repository.off.OpenFoodFactsClient
import es.sebas1705.haltera.data.repository.off.openFoodFactsHttpClient
import es.sebas1705.haltera.data.repository.sql.SqlRoutineRepository
import es.sebas1705.haltera.data.repository.sql.SqlSessionRepository
import es.sebas1705.haltera.data.routines.FamousRoutines
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val IoDispatcher = named("io")

/** The app version name, for the Open Food Facts User-Agent; bound by the app ("dev" when absent). */
data class AppVersionName(val value: String)

/** Binds every repository. Needs an [AppDatabase] from the app module and the platform [PhotoFiles]. */
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
    single<BodyMeasurementRepository> { SqlBodyMeasurementRepository(get(), get(IoDispatcher), get(named("now"))) }
    // Progress photos: files in app-private storage only (the platform binds where, PhotoFiles).
    single<ProgressPhotoRepository> { FileProgressPhotoRepository(get(), get(IoDispatcher), get(named("now"))) }
    single<RoutineSharing> { DefaultRoutineSharing(get(), get(), get(named("now"))) }
    single { FoodImporter(get<AppDatabase>(), now = get(named("now"))) }
    single {
        OpenFoodFactsClient(
            openFoodFactsHttpClient(),
            getOrNull<AppVersionName>()?.value ?: "dev",
            now = get(named("now"))
        )
    }
    single<NutritionRepository> {
        SqlNutritionRepository(get(), get(), get(), get(), get(IoDispatcher), get(named("now")))
    }
    single<DataExporter> { SqlDataExporter(get(), get(), get(), get(), get(), get(), get(), get(), get(IoDispatcher)) }
    single<HistoryImporter> { SqlHistoryImporter(get(), get(), get(IoDispatcher)) }
    // The platform binds its HealthGateway (Health Connect on Android); without one the integration is unsupported.
    single<HealthRepository> {
        SqlHealthRepository(
            get(),
            getOrNull<HealthGateway>() ?: UnavailableHealthGateway,
            get(),
            get(IoDispatcher),
            get(named("now")),
        )
    }
}
