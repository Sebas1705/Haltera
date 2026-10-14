@file:Suppress("MatchingDeclarationName") // Route objects and their graph builder live together.

package es.sebas1705.ironlog.feature.progress.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import es.sebas1705.ironlog.feature.progress.ui.ProgressScreen
import es.sebas1705.ironlog.feature.progress.ui.session.SessionDetailScreen
import es.sebas1705.ironlog.feature.progress.ui.session.SessionDetailViewModel
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Top-level destination of the Progress tab. */
@Serializable
data object ProgressRoute

/** Read-only detail of a finished session (nested in the Progress tab). */
@Serializable
internal data class ProgressSessionRoute(val sessionId: String)

/**
 * Registers the progress destinations. The app shell calls this from its NavHost.
 * [onOpenSession] opens a session (in progress or finished) in feature:session; Progress uses it for
 * sessions still in progress and shows finished ones in its own read-only detail.
 */
fun NavGraphBuilder.progressGraph(
    navController: NavController,
    onOpenSession: (sessionId: String) -> Unit,
) {
    composable<ProgressRoute> {
        ProgressScreen(
            onOpenSession = { session ->
                if (session.inProgress) {
                    onOpenSession(session.id)
                } else {
                    navController.navigate(ProgressSessionRoute(session.id))
                }
            },
        )
    }
    composable<ProgressSessionRoute> { entry ->
        val sessionId = entry.toRoute<ProgressSessionRoute>().sessionId
        val viewModel = koinViewModel<SessionDetailViewModel> { parametersOf(sessionId) }
        SessionDetailScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
    }
}
