package es.sebas1705.haltera.feature.exercises.di

import es.sebas1705.haltera.data.repository.RoutineRepository
import es.sebas1705.haltera.domain.usecases.stats.TrainingMath
import es.sebas1705.haltera.feature.exercises.ui.ExercisesViewModel
import es.sebas1705.haltera.feature.exercises.ui.create.NewExerciseViewModel
import es.sebas1705.haltera.feature.exercises.ui.detail.ExerciseDetailViewModel
import kotlinx.coroutines.flow.map
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Koin bindings of the exercises feature (ViewModels, feature-local classes). Registered in composeApp. */
val exercisesModule = module {
    viewModel { ExercisesViewModel(exercises = get()) }
    viewModel { params ->
        ExerciseDetailViewModel(
            exerciseId = params.get(),
            exercises = get(),
            settings = get(),
            bilateralSlots = get<RoutineRepository>().observeRoutines().map { TrainingMath.bilateralSlots(it) },
        )
    }
    viewModel { params -> NewExerciseViewModel(exerciseId = params.getOrNull<String>(), exercises = get()) }
}
