package es.sebas1705.ironlog

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import es.sebas1705.ironlog.core.ui.components.LoadingScreen
import es.sebas1705.ironlog.core.ui.theme.AppTheme
import es.sebas1705.ironlog.feature.auth.*
import es.sebas1705.ironlog.feature.home.*
import es.sebas1705.ironlog.feature.profile.*
import es.sebas1705.ironlog.feature.settings.*
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

// ── Type-safe routes ──────────────────────────────────────────────────────────
@Serializable object HomeRoute
@Serializable object ProfileRoute
@Serializable object SettingsRoute

private data class NavItem(val label: String, val icon: ImageVector, val route: Any)

private val bottomNavItems = listOf(
    NavItem("Inicio",   Icons.Default.Home,    HomeRoute),
    NavItem("Perfil",   Icons.Default.Person,  ProfileRoute),
    NavItem("Ajustes",  Icons.Default.Settings, SettingsRoute),
)

@Composable
fun App(modifier: Modifier = Modifier) {
    AppTheme(modifier) {
        val authVm: AuthViewModel = koinViewModel()
        val authState by authVm.uiState.collectAsStateWithLifecycle()

        AnimatedContent(
            targetState = authState.status,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
        ) { status ->
            when (status) {
                AuthStatus.Checking        -> LoadingScreen()
                AuthStatus.Unauthenticated -> LoginScreen(
                    isLoading    = authState.isLoading,
                    error        = authState.error,
                    onLogin      = { authVm.onEvent(AuthIntent.Login(it)) },
                    onAnonymous  = { authVm.onEvent(AuthIntent.LoginAnonymous) },
                    onClearError = { authVm.onEvent(AuthIntent.ClearError) },
                )
                AuthStatus.Authenticated   -> MainGraph(
                    onLogout = { authVm.onEvent(AuthIntent.Logout) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainGraph(onLogout: () -> Unit) {
    val nav     = rememberNavController()
    val back    by nav.currentBackStackEntryAsState()

    val homeVm     : HomeViewModel     = koinViewModel()
    val profileVm  : ProfileViewModel  = koinViewModel()
    val settingsVm : SettingsViewModel = koinViewModel()

    val homeState     by homeVm.uiState.collectAsStateWithLifecycle()
    val profileState  by profileVm.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsVm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { item ->
                    val selected = back?.destination?.hasRoute(item.route::class) == true
                    NavigationBarItem(
                        selected = selected,
                        onClick  = {
                            nav.navigate(item.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState    = true
                            }
                        },
                        icon  = { Icon(item.icon, item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController     = nav,
            startDestination  = HomeRoute,
            modifier          = Modifier.fillMaxSize().padding(padding),
            enterTransition   = { slideInHorizontally { it / 4 } + fadeIn() },
            exitTransition    = { slideOutHorizontally { -it / 4 } + fadeOut() },
            popEnterTransition  = { slideInHorizontally { -it / 4 } + fadeIn() },
            popExitTransition   = { slideOutHorizontally { it / 4 } + fadeOut() },
        ) {
            composable<HomeRoute> {
                HomeScreen(state = homeState, onRefresh = { homeVm.onEvent(HomeIntent.Refresh) })
            }
            composable<ProfileRoute> {
                // ProfileScreen(state = profileState, ...)
                // TODO: implement profile screen
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    state          = settingsState,
                    onDarkMode     = { settingsVm.onEvent(SettingsIntent.SetDarkMode(it)) },
                    onSaveUrl      = { settingsVm.onEvent(SettingsIntent.SaveBaseUrl(it)) },
                    onSaveToken    = { settingsVm.onEvent(SettingsIntent.SaveToken(it)) },
                    onLogout       = { settingsVm.onEvent(SettingsIntent.Logout); onLogout() },
                    onClearSuccess = { settingsVm.onEvent(SettingsIntent.ClearSuccess) },
                )
            }
        }
    }
}
