package es.sebas1705.haltera.feature.progress.di

import es.sebas1705.haltera.feature.progress.model.ProgressClock
import es.sebas1705.haltera.feature.progress.model.StatsPeriodSelection
import es.sebas1705.haltera.feature.progress.ui.activity.ActivityViewModel
import es.sebas1705.haltera.feature.progress.ui.bodyweight.BodyWeightViewModel
import es.sebas1705.haltera.feature.progress.ui.calendar.CalendarViewModel
import es.sebas1705.haltera.feature.progress.ui.measurements.MeasurementsViewModel
import es.sebas1705.haltera.feature.progress.ui.photos.PhotosViewModel
import es.sebas1705.haltera.feature.progress.photos.NoProgressPhotoPicker
import es.sebas1705.haltera.feature.progress.photos.ProgressPhotoPicker
import es.sebas1705.haltera.feature.progress.ui.exercise.ExerciseProgressViewModel
import es.sebas1705.haltera.feature.progress.ui.muscles.MusclesViewModel
import es.sebas1705.haltera.feature.progress.ui.overview.OverviewViewModel
import es.sebas1705.haltera.feature.progress.ui.session.SessionDetailViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Koin bindings of the progress feature (ViewModels, feature-local classes). Registered in composeApp, which may also
 * bind a platform [ProgressPhotoPicker] (none: adding photos is hidden).
 */
val progressModule = module {
    factory { ProgressClock() }
    single { StatsPeriodSelection() }
    viewModelOf(::OverviewViewModel)
    viewModelOf(::CalendarViewModel)
    // The tab has no parameters; the screen opened from a session passes an ExerciseFocus.
    viewModel { params -> ExerciseProgressViewModel(get(), get(), get(), params.getOrNull()) }
    viewModelOf(::MusclesViewModel)
    viewModelOf(::BodyWeightViewModel)
    viewModelOf(::MeasurementsViewModel)
    viewModel { PhotosViewModel(get(), getOrNull<ProgressPhotoPicker>() ?: NoProgressPhotoPicker, get()) }
    viewModelOf(::ActivityViewModel)
    viewModel { params -> SessionDetailViewModel(params.get(), get(), get(), get(), get(), get()) }
}
