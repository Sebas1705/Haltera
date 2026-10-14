@file:Suppress("MatchingDeclarationName") // Route objects and their graph builder live together.

package es.sebas1705.ironlog.feature.routines.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import es.sebas1705.ironlog.feature.routines.ui.RoutinesScreen
import kotlinx.serialization.Serializable

/** Top-level destination of the Routines tab. */
@Serializable
data object RoutinesRoute

/**
 * Registers the routines destinations. The app shell calls this from its NavHost.
 * Nested destinations (editor, catalogue) are added here and navigated with [navController].
 */
@Suppress("UnusedParameter") // Wired by the shell; the feature implementation uses them.
fun NavGraphBuilder.routinesGraph(
    navController: NavController,
) {
    composable<RoutinesRoute> { RoutinesScreen() }
}
