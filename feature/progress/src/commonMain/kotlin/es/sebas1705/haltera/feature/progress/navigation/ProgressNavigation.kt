@file:Suppress("MatchingDeclarationName") // Route objects and their graph builder live together.

package es.sebas1705.haltera.feature.progress.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import es.sebas1705.haltera.feature.progress.ui.ProgressScreen
import es.sebas1705.haltera.feature.progress.ui.session.SessionDetailScreen
import es.sebas1705.haltera.feature.progress.ui.session.SessionDetailViewModel
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Top-level destination of the Progress tab. */
@Serializable
data object ProgressRoute

/** Detail of a saved session, with its edit actions (nested in the Progress tab). */
@Serializable
internal data class ProgressSessionRoute(val sessionId: String)

/**
 * Registers the progress destinations. The app shell calls this from its NavHost.
 * [onOpenSession] opens a session (in progress or finished) in feature:session; Progress uses it for
 * sessions still in progress, for a session just logged on a calendar day and for "Edit sets" of a saved
 * one (feature:session opens it in log mode); finished sessions show in its own detail.
 */
fun NavGraphBuilder.progressGraph(
    navController: NavController,
    onOpenSession: (sessionId: String) -> Unit,
) {
    composable<ProgressRoute> {
        ProgressScreen(
            onOpenSessionId = onOpenSession,
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
        SessionDetailScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            // Editing sets opens the saved session in feature:session, which derives log mode from it.
            onEditSets = { onOpenSession(sessionId) },
        )
    }
}
