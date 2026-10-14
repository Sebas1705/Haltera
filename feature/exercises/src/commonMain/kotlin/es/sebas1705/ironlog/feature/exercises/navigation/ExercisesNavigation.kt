@file:Suppress("MatchingDeclarationName") // Route objects and their graph builder live together.

package es.sebas1705.ironlog.feature.exercises.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import es.sebas1705.ironlog.feature.exercises.ui.ExercisesScreen
import kotlinx.serialization.Serializable

/** Top-level destination of the Exercises tab. */
@Serializable
data object ExercisesRoute

/**
 * Registers the exercises destinations. The app shell calls this from its NavHost.
 * Nested destinations (detail, custom exercise editor) are added here and navigated with [navController].
 */
@Suppress("UnusedParameter") // Wired by the shell; the feature implementation uses them.
fun NavGraphBuilder.exercisesGraph(
    navController: NavController,
) {
    composable<ExercisesRoute> { ExercisesScreen() }
}
