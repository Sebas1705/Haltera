package es.sebas1705.haltera.feature.exercises.di

import es.sebas1705.haltera.feature.exercises.ui.ExercisesViewModel
import es.sebas1705.haltera.feature.exercises.ui.create.NewExerciseViewModel
import es.sebas1705.haltera.feature.exercises.ui.detail.ExerciseDetailViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Koin bindings of the exercises feature (ViewModels, feature-local classes). Registered in composeApp. */
val exercisesModule = module {
    viewModel { ExercisesViewModel(exercises = get()) }
    viewModel { params -> ExerciseDetailViewModel(exerciseId = params.get(), exercises = get(), settings = get()) }
    viewModelOf(::NewExerciseViewModel)
}
