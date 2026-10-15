@file:Suppress("MatchingDeclarationName") // Route objects and their graph builder live together.

package es.sebas1705.ironlog.feature.nutrition.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import es.sebas1705.ironlog.data.models.FoodSource
import es.sebas1705.ironlog.data.models.Meal
import es.sebas1705.ironlog.feature.nutrition.resources.Res
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_add_ingredient
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_add_to
import es.sebas1705.ironlog.feature.nutrition.ui.components.label
import es.sebas1705.ironlog.feature.nutrition.ui.day.DayScreen
import es.sebas1705.ironlog.feature.nutrition.ui.detail.FoodDetailArgs
import es.sebas1705.ironlog.feature.nutrition.ui.detail.FoodDetailScreen
import es.sebas1705.ironlog.feature.nutrition.ui.editor.FoodEditorArgs
import es.sebas1705.ironlog.feature.nutrition.ui.editor.FoodEditorScreen
import es.sebas1705.ironlog.feature.nutrition.ui.goals.GoalsScreen
import es.sebas1705.ironlog.feature.nutrition.ui.recipe.RecipeArgs
import es.sebas1705.ironlog.feature.nutrition.ui.recipe.RecipeEditorIntent
import es.sebas1705.ironlog.feature.nutrition.ui.recipe.RecipeEditorScreen
import es.sebas1705.ironlog.feature.nutrition.ui.recipe.RecipeEditorViewModel
import es.sebas1705.ironlog.feature.nutrition.ui.search.FoodSearchScreen
import es.sebas1705.ironlog.feature.nutrition.ui.stats.StatsScreen
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Top-level destination of the Nutrition tab: the day view. */
@Serializable
data object NutritionRoute

/** Nested: find a food to log in [meal] on [date], or (pickForRecipe) an ingredient for the recipe editor. */
@Serializable
internal data class FoodSearchRoute(val date: String, val meal: String, val pickForRecipe: Boolean = false)

/** Nested: a food with the amount editor; [entryId] edits a logged entry. */
@Serializable
internal data class FoodDetailRoute(val foodId: String, val date: String, val meal: String, val entryId: String? = null)

/** Nested: the custom food editor; with [date] and [meal] a new food is then logged there. */
@Serializable
internal data class FoodEditorRoute(
    val foodId: String? = null,
    val barcode: String? = null,
    val date: String? = null,
    val meal: String? = null,
)

/** Nested: the recipe editor. */
@Serializable
internal data class RecipeEditorRoute(val recipeId: String? = null)

@Serializable
internal data object GoalsRoute

@Serializable
internal data object NutritionStatsRoute

/** Saved-state key through which an ingredient picked in the search returns to the recipe editor. */
private const val PICKED_INGREDIENT = "nutrition.pickedIngredient"

/**
 * Registers the nutrition destinations (docs/designs/2026-10-15-nutrition-design.md §4). The app shell calls this
 * from its NavHost; nested destinations draw their own top bar.
 */
@Suppress("LongMethod") // One registration per destination.
fun NavGraphBuilder.nutritionGraph(navController: NavController) {
    val backToDay: () -> Unit = { navController.popBackStack(NutritionRoute, inclusive = false) }
    composable<NutritionRoute> {
        DayScreen(
            onAddFood = { date, meal ->
                navController.navigate(FoodSearchRoute(date.toString(), meal.name)) {
                    launchSingleTop = true
                }
            },
            onOpenEntry = { entry ->
                navController.navigate(
                    FoodDetailRoute(entry.foodId, entry.date.toString(), entry.meal.name, entry.id)
                ) {
                    launchSingleTop = true
                }
            },
            onOpenGoals = { navController.navigate(GoalsRoute) { launchSingleTop = true } },
            onOpenStats = { navController.navigate(NutritionStatsRoute) { launchSingleTop = true } },
        )
    }
    composable<FoodSearchRoute> { entry -> SearchDestination(navController, entry.toRoute()) }
    composable<FoodDetailRoute> { entry ->
        val route = entry.toRoute<FoodDetailRoute>()
        val args = FoodDetailArgs(route.foodId, LocalDate.parse(route.date), Meal.fromDb(route.meal), route.entryId)
        FoodDetailScreen(
            viewModel = koinViewModel { parametersOf(args) },
            onBack = { navController.popBackStack() },
            onFinished = backToDay,
            onEditFood = { food ->
                val target = if (food.source == FoodSource.RECIPE) {
                    RecipeEditorRoute(
                        food.id
                    )
                } else {
                    FoodEditorRoute(foodId = food.id)
                }
                navController.navigate(target) { launchSingleTop = true }
            },
        )
    }
    composable<FoodEditorRoute> { entry ->
        val route = entry.toRoute<FoodEditorRoute>()
        FoodEditorScreen(
            viewModel = koinViewModel { parametersOf(FoodEditorArgs(route.foodId, route.barcode)) },
            onBack = { navController.popBackStack() },
            onFinished = { finished ->
                val savedId = finished.foodId
                when {
                    savedId == null -> backToDay() // deleted or hidden
                    route.foodId == null && route.date != null && route.meal != null ->
                        // A new food from the search: log it next.
                        navController.navigate(FoodDetailRoute(savedId, route.date, route.meal)) {
                            popUpTo<FoodEditorRoute> { inclusive = true }
                        }
                    else -> navController.popBackStack()
                }
            },
        )
    }
    composable<RecipeEditorRoute> { entry -> RecipeDestination(navController, entry, backToDay) }
    composable<GoalsRoute> { GoalsScreen(viewModel = koinViewModel(), onBack = { navController.popBackStack() }) }
    composable<NutritionStatsRoute> {
        StatsScreen(
            viewModel = koinViewModel(),
            onBack = { navController.popBackStack() }
        )
    }
}

@Composable
private fun SearchDestination(navController: NavController, route: FoodSearchRoute) {
    val meal = Meal.fromDb(route.meal)
    FoodSearchScreen(
        viewModel = koinViewModel(),
        title = if (route.pickForRecipe) {
            stringResource(Res.string.nutrition_add_ingredient)
        } else {
            stringResource(Res.string.nutrition_add_to, stringResource(meal.label))
        },
        pickMode = route.pickForRecipe,
        onBack = { navController.popBackStack() },
        onOpenFood = { id ->
            navController.navigate(
                FoodDetailRoute(id, route.date, route.meal)
            ) { launchSingleTop = true }
        },
        onPicked = { foodId, grams ->
            navController.previousBackStackEntry?.savedStateHandle?.set(PICKED_INGREDIENT, "$foodId|$grams")
            navController.popBackStack()
        },
        onCreateFood = { barcode ->
            val target = if (route.pickForRecipe) {
                FoodEditorRoute(barcode = barcode)
            } else {
                FoodEditorRoute(barcode = barcode, date = route.date, meal = route.meal)
            }
            navController.navigate(target) { launchSingleTop = true }
        },
        onCreateRecipe = { navController.navigate(RecipeEditorRoute()) { launchSingleTop = true } },
    )
}

@Composable
private fun RecipeDestination(navController: NavController, entry: NavBackStackEntry, backToDay: () -> Unit) {
    val route = entry.toRoute<RecipeEditorRoute>()
    val viewModel = koinViewModel<RecipeEditorViewModel> { parametersOf(RecipeArgs(route.recipeId)) }
    val picked by entry.savedStateHandle.getStateFlow<String?>(PICKED_INGREDIENT, null).collectAsState()
    LaunchedEffect(picked) {
        val value = picked ?: return@LaunchedEffect
        entry.savedStateHandle[PICKED_INGREDIENT] = null
        val foodId = value.substringBeforeLast('|')
        val grams = value.substringAfterLast('|').toDoubleOrNull() ?: return@LaunchedEffect
        viewModel.onEvent(RecipeEditorIntent.AddItem(foodId, grams))
    }
    RecipeEditorScreen(
        viewModel = viewModel,
        onBack = { navController.popBackStack() },
        onAddIngredient = {
            navController.navigate(FoodSearchRoute(date = "", meal = Meal.SNACK.name, pickForRecipe = true)) {
                launchSingleTop = true
            }
        },
        onFinished = { finished -> if (finished.recipeId == null) backToDay() else navController.popBackStack() },
    )
}
