@file:Suppress("MatchingDeclarationName") // Route objects and their graph builder live together.

package es.sebas1705.ironlog.feature.today.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import es.sebas1705.ironlog.feature.today.ui.TodayScreen
import kotlinx.serialization.Serializable

/** Top-level destination of the Today tab. */
@Serializable
data object TodayRoute

/**
 * Registers the today destinations. The app shell calls this from its NavHost.
 * [onOpenSession] opens the live session (feature:session) for a started or resumed session id.
 */
@Suppress("UnusedParameter") // Wired by the shell; the feature implementation uses them.
fun NavGraphBuilder.todayGraph(
    navController: NavController,
    onOpenSession: (sessionId: String) -> Unit,
) {
    composable<TodayRoute> { TodayScreen() }
}
