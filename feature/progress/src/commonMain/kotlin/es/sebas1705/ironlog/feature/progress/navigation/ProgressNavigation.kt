@file:Suppress("MatchingDeclarationName") // Route objects and their graph builder live together.

package es.sebas1705.ironlog.feature.progress.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import es.sebas1705.ironlog.feature.progress.ui.ProgressScreen
import kotlinx.serialization.Serializable

/** Top-level destination of the Progress tab. */
@Serializable
data object ProgressRoute

/**
 * Registers the progress destinations. The app shell calls this from its NavHost.
 * [onOpenSession] opens a session (in progress or finished) in feature:session.
 */
@Suppress("UnusedParameter") // Wired by the shell; the feature implementation uses them.
fun NavGraphBuilder.progressGraph(
    navController: NavController,
    onOpenSession: (sessionId: String) -> Unit,
) {
    composable<ProgressRoute> { ProgressScreen() }
}
