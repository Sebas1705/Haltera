@file:Suppress("MatchingDeclarationName") // Route objects and their graph builder live together.

package es.sebas1705.ironlog.feature.today.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import es.sebas1705.ironlog.feature.today.ui.TodayTab
import kotlinx.serialization.Serializable

/** Top-level destination of the Today tab. */
@Serializable
data object TodayRoute

/**
 * Registers the today destinations. The app shell calls this from its NavHost.
 * [onOpenSession] opens the live session (feature:session) for a started or resumed session id;
 * [onOpenNutrition] opens the Nutrition tab from the optional "Today's nutrition" card.
 */
@Suppress("UnusedParameter") // navController is part of the fixed contract; Today has no nested destinations.
fun NavGraphBuilder.todayGraph(
    navController: NavController,
    onOpenSession: (sessionId: String) -> Unit,
    onOpenNutrition: () -> Unit = {},
) {
    composable<TodayRoute> { TodayTab(onOpenSession = onOpenSession, onOpenNutrition = onOpenNutrition) }
}
