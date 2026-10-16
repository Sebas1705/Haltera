@file:Suppress("MatchingDeclarationName") // Route objects and their graph builder live together.

package es.sebas1705.haltera.feature.exercises.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import es.sebas1705.haltera.feature.exercises.ui.ExercisesScreen
import es.sebas1705.haltera.feature.exercises.ui.create.NewExerciseScreen
import es.sebas1705.haltera.feature.exercises.ui.detail.ExerciseDetailScreen
import kotlinx.serialization.Serializable

/** Top-level destination of the Exercises tab. */
@Serializable
data object ExercisesRoute

/**
 * Nested: one exercise (instructions, media, variants, records). Public so the shell can open it from other
 * features (the live session's "Description"); it draws its own top bar and back arrow.
 */
@Serializable
data class ExerciseDetailRoute(val exerciseId: String)

/** Nested: the custom exercise editor. */
@Serializable
internal data object NewExerciseRoute

/** Nested: the editor of an existing custom exercise. */
@Serializable
internal data class EditExerciseRoute(val exerciseId: String)

/**
 * Registers the exercises destinations. The app shell calls this from its NavHost.
 * Nested destinations (detail, custom exercise editor) are added here and navigated with [navController].
 */
fun NavGraphBuilder.exercisesGraph(
    navController: NavController,
) {
    composable<ExercisesRoute> {
        ExercisesScreen(
            onOpenExercise = { id -> navController.navigate(ExerciseDetailRoute(id)) { launchSingleTop = true } },
            onCreateExercise = { navController.navigate(NewExerciseRoute) { launchSingleTop = true } },
        )
    }
    composable<ExerciseDetailRoute> { entry ->
        val route = entry.toRoute<ExerciseDetailRoute>()
        ExerciseDetailScreen(
            exerciseId = route.exerciseId,
            onBack = { navController.popBackStack() },
            onEdit = { navController.navigate(EditExerciseRoute(route.exerciseId)) { launchSingleTop = true } },
        )
    }
    composable<NewExerciseRoute> {
        NewExerciseScreen(
            onBack = { navController.popBackStack() },
            // Replace the editor with the new exercise so back returns to the list.
            onSaved = { id ->
                navController.navigate(ExerciseDetailRoute(id)) {
                    popUpTo<NewExerciseRoute> { inclusive = true }
                }
            },
        )
    }
    composable<EditExerciseRoute> { entry ->
        val route = entry.toRoute<EditExerciseRoute>()
        // Saving returns to the detail, which shows the edited exercise.
        NewExerciseScreen(
            onBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
            exerciseId = route.exerciseId,
        )
    }
}
