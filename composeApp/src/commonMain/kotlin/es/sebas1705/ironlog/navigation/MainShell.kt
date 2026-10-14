@file:Suppress("MatchingDeclarationName") // Route objects and their graph builder live together.

package es.sebas1705.ironlog.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import es.sebas1705.ironlog.share.IncomingRoutineChannel
import org.koin.compose.koinInject
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import es.sebas1705.ironlog.feature.exercises.navigation.ExercisesRoute
import es.sebas1705.ironlog.feature.exercises.navigation.exercisesGraph
import es.sebas1705.ironlog.feature.progress.navigation.ProgressRoute
import es.sebas1705.ironlog.feature.progress.navigation.progressGraph
import es.sebas1705.ironlog.feature.routines.navigation.RoutinesRoute
import es.sebas1705.ironlog.feature.routines.navigation.routinesGraph
import es.sebas1705.ironlog.feature.session.navigation.SessionRoute
import es.sebas1705.ironlog.feature.session.navigation.sessionGraph
import es.sebas1705.ironlog.feature.today.navigation.TodayRoute
import es.sebas1705.ironlog.feature.today.navigation.todayGraph
import es.sebas1705.ironlog.resources.Res
import es.sebas1705.ironlog.resources.settings_title
import es.sebas1705.ironlog.resources.tab_exercises
import es.sebas1705.ironlog.resources.tab_progress
import es.sebas1705.ironlog.resources.tab_routines
import es.sebas1705.ironlog.resources.tab_today
import es.sebas1705.ironlog.settings.settingsGraph
import es.sebas1705.ironlog.settings.SettingsRoute
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.reflect.KClass

/** The four tabs (spec §7). Their routes are the features' top-level route objects. */
enum class Tab(val route: Any, val routeClass: KClass<*>, val label: StringResource, val icon: ImageVector) {
    TODAY(TodayRoute, TodayRoute::class, Res.string.tab_today, Icons.Filled.Home),
    ROUTINES(RoutinesRoute, RoutinesRoute::class, Res.string.tab_routines, Icons.AutoMirrored.Filled.List),
    EXERCISES(ExercisesRoute, ExercisesRoute::class, Res.string.tab_exercises, Icons.Filled.Search),
    PROGRESS(ProgressRoute, ProgressRoute::class, Res.string.tab_progress, Icons.Filled.DateRange),
}

private fun NavDestination?.currentTab(): Tab? =
    Tab.entries.firstOrNull { tab -> this?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true }

/**
 * App shell: top bar with Settings and bottom navigation on the four tab roots; nested feature
 * screens and the live session draw their own chrome.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(navController: NavHostController = rememberNavController()) {
    val backStack by navController.currentBackStackEntryAsState()
    val tab = backStack?.destination?.currentTab()
    val onTabRoot = tab != null && backStack?.destination?.hasRoute(tab.routeClass) == true
    val incoming by koinInject<IncomingRoutineChannel>().value.collectAsState()
    // A routine opened from outside the app: the Routines tab root shows its import preview.
    LaunchedEffect(incoming) {
        if (incoming != null) {
            navController.navigate(RoutinesRoute) {
                popUpTo(navController.graph.findStartDestination().id)
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        topBar = {
            if (onTabRoot && tab != null) {
                TopAppBar(
                    title = { Text(stringResource(tab.label)) },
                    actions = {
                        IconButton(onClick = { navController.navigate(SettingsRoute) }) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(Res.string.settings_title))
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (onTabRoot) {
                NavigationBar {
                    Tab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = item == tab,
                            onClick = { navController.navigateToTab(item) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TodayRoute,
            modifier = Modifier.padding(padding),
        ) {
            val openSession: (String) -> Unit = { id -> navController.navigate(SessionRoute(id)) }
            todayGraph(navController, onOpenSession = openSession)
            routinesGraph(navController)
            exercisesGraph(navController)
            progressGraph(navController, onOpenSession = openSession)
            sessionGraph(navController, onFinished = { navController.popBackStack(TodayRoute, inclusive = false) })
            settingsGraph(navController)
        }
    }
}

/** Switches tab keeping one back stack per tab. */
fun NavHostController.navigateToTab(tab: Tab) = navigate(tab.route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
