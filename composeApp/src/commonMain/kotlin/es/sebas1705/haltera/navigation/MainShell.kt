@file:Suppress("MatchingDeclarationName") // Route objects and their graph builder live together.

package es.sebas1705.haltera.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import es.sebas1705.haltera.account.CloudBackupCard
import es.sebas1705.haltera.core.ui.adaptive.LocalWindowSize
import es.sebas1705.haltera.share.IncomingRoutineChannel
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
import es.sebas1705.haltera.feature.exercises.navigation.ExerciseDetailRoute
import es.sebas1705.haltera.feature.exercises.navigation.ExercisesRoute
import es.sebas1705.haltera.feature.exercises.navigation.exercisesGraph
import es.sebas1705.haltera.feature.nutrition.navigation.NutritionRoute
import es.sebas1705.haltera.feature.nutrition.navigation.nutritionGraph
import es.sebas1705.haltera.feature.progress.navigation.ExerciseProgressRoute
import es.sebas1705.haltera.feature.progress.navigation.ProgressRoute
import es.sebas1705.haltera.feature.progress.navigation.progressGraph
import es.sebas1705.haltera.feature.routines.navigation.RoutinesRoute
import es.sebas1705.haltera.feature.routines.navigation.routinesGraph
import es.sebas1705.haltera.feature.session.navigation.SessionRoute
import es.sebas1705.haltera.feature.session.navigation.sessionGraph
import es.sebas1705.haltera.feature.today.navigation.TodayRoute
import es.sebas1705.haltera.feature.today.navigation.todayGraph
import es.sebas1705.haltera.resources.Res
import es.sebas1705.haltera.resources.settings_title
import es.sebas1705.haltera.resources.tab_exercises
import es.sebas1705.haltera.resources.tab_nutrition
import es.sebas1705.haltera.resources.tab_progress
import es.sebas1705.haltera.resources.tab_routines
import es.sebas1705.haltera.resources.tab_today
import es.sebas1705.haltera.settings.settingsGraph
import es.sebas1705.haltera.settings.SettingsRoute
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.reflect.KClass

/** The five tabs (spec §7, nutrition design §4). Their routes are the features' top-level route objects. */
enum class Tab(val route: Any, val routeClass: KClass<*>, val label: StringResource, val icon: ImageVector) {
    TODAY(TodayRoute, TodayRoute::class, Res.string.tab_today, Icons.Filled.Home),
    ROUTINES(RoutinesRoute, RoutinesRoute::class, Res.string.tab_routines, Icons.AutoMirrored.Filled.List),
    EXERCISES(ExercisesRoute, ExercisesRoute::class, Res.string.tab_exercises, Icons.Filled.Search),
    NUTRITION(NutritionRoute, NutritionRoute::class, Res.string.tab_nutrition, NutritionIcon),
    PROGRESS(ProgressRoute, ProgressRoute::class, Res.string.tab_progress, Icons.Filled.DateRange),
}

private fun NavDestination?.currentTab(): Tab? =
    Tab.entries.firstOrNull { tab -> this?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true }

/**
 * App shell: top bar with Settings and tab navigation on the five tab roots; nested feature
 * screens and the live session draw their own chrome. The tabs are a bottom bar on compact windows
 * and a navigation rail on medium/expanded widths and on landscape phones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(navController: NavHostController = rememberNavController()) {
    val backStack by navController.currentBackStackEntryAsState()
    val tab = backStack?.destination?.currentTab()
    val onTabRoot = tab != null && backStack?.destination?.hasRoute(tab.routeClass) == true
    val useRail = LocalWindowSize.current.useNavigationRail
    ShowIncomingRoutine(navController)

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
            if (onTabRoot && !useRail) {
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
        // The shell consumes its insets so nested screens' own Scaffolds and top bars do not add the
        // status and navigation bar insets a second time.
        Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (onTabRoot && useRail && tab != null) {
                NavigationRail(windowInsets = WindowInsets(0)) {
                    Tab.entries.forEach { item ->
                        NavigationRailItem(
                            selected = item == tab,
                            onClick = { navController.navigateToTab(item) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.label)) },
                        )
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                // One-time cloud copy question (or notice) on top of Today; it dismisses itself.
                if (onTabRoot && tab == Tab.TODAY) {
                    CloudBackupCard(onOpenSettings = { navController.navigate(SettingsRoute) })
                }
                ShellNavHost(navController, Modifier.weight(1f))
            }
        }
    }
}

/** A routine opened from outside the app: the Routines tab root shows its import preview. */
@Composable
private fun ShowIncomingRoutine(navController: NavHostController) {
    val incoming by koinInject<IncomingRoutineChannel>().value.collectAsState()
    LaunchedEffect(incoming) {
        if (incoming != null) {
            navController.navigate(RoutinesRoute) {
                popUpTo(navController.graph.findStartDestination().id)
                launchSingleTop = true
            }
        }
    }
}

@Composable
private fun ShellNavHost(navController: NavHostController, modifier: Modifier) {
    NavHost(navController = navController, startDestination = TodayRoute, modifier = modifier) {
        val openSession: (String) -> Unit = { id -> navController.navigate(SessionRoute(id)) }
        todayGraph(
            navController,
            onOpenSession = openSession,
            onOpenNutrition = { navController.navigateToTab(Tab.NUTRITION) },
        )
        routinesGraph(navController)
        exercisesGraph(navController)
        nutritionGraph(navController)
        progressGraph(
            navController,
            onOpenSession = openSession,
            // The exercise name in a saved session and the exercise progress open its detail (and its Restore banner).
            onOpenExerciseDetail = { exerciseId ->
                navController.navigate(ExerciseDetailRoute(exerciseId)) { launchSingleTop = true }
            },
        )
        sessionGraph(
            navController,
            onFinished = { navController.popBackStack(TodayRoute, inclusive = false) },
            // Opened on top of the session, which stays on the back stack (and keeps running): back returns to it.
            onOpenExerciseProgress = { exerciseId, variantId ->
                navController.navigate(ExerciseProgressRoute(exerciseId, variantId)) { launchSingleTop = true }
            },
            onOpenExerciseDetail = { exerciseId ->
                navController.navigate(ExerciseDetailRoute(exerciseId)) { launchSingleTop = true }
            },
        )
        settingsGraph(navController)
    }
}

/** Switches tab keeping one back stack per tab. */
fun NavHostController.navigateToTab(tab: Tab) = navigate(tab.route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
