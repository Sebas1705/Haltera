package es.sebas1705.ironlog.feature.progress.di

import es.sebas1705.ironlog.feature.progress.model.ProgressClock
import es.sebas1705.ironlog.feature.progress.ui.bodyweight.BodyWeightViewModel
import es.sebas1705.ironlog.feature.progress.ui.calendar.CalendarViewModel
import es.sebas1705.ironlog.feature.progress.ui.exercise.ExerciseProgressViewModel
import es.sebas1705.ironlog.feature.progress.ui.session.SessionDetailViewModel
import es.sebas1705.ironlog.feature.progress.ui.volume.MuscleVolumeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Koin bindings of the progress feature (ViewModels, feature-local classes). Registered in composeApp. */
val progressModule = module {
    factory { ProgressClock() }
    viewModelOf(::CalendarViewModel)
    viewModelOf(::ExerciseProgressViewModel)
    viewModelOf(::BodyWeightViewModel)
    viewModelOf(::MuscleVolumeViewModel)
    viewModel { params -> SessionDetailViewModel(params.get(), get(), get(), get(), get()) }
}
