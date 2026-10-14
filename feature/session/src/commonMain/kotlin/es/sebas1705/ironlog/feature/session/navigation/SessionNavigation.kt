@file:Suppress("MatchingDeclarationName") // Route objects and their graph builder live together.

package es.sebas1705.ironlog.feature.session.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import es.sebas1705.ironlog.feature.session.ui.SessionScreen
import kotlinx.serialization.Serializable

/** Live session of [sessionId] (spec §5). Navigate with `navController.navigate(SessionRoute(id))`. */
@Serializable
data class SessionRoute(val sessionId: String)

/**
 * Registers the session destinations. The app shell calls this from its NavHost.
 * [onFinished] leaves the session screen after finishing or discarding (the shell pops back to Today).
 */
@Suppress("UnusedParameter") // Wired by the shell; the feature implementation uses them.
fun NavGraphBuilder.sessionGraph(
    navController: NavController,
    onFinished: () -> Unit,
) {
    composable<SessionRoute> { entry -> SessionScreen(sessionId = entry.toRoute<SessionRoute>().sessionId) }
}
