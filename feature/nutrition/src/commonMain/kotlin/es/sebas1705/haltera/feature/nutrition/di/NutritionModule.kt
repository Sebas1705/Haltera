package es.sebas1705.haltera.feature.nutrition.di

import androidx.compose.ui.text.intl.Locale
import es.sebas1705.haltera.feature.nutrition.model.AppLanguage
import es.sebas1705.haltera.feature.nutrition.model.Today
import es.sebas1705.haltera.feature.nutrition.scan.BarcodeScanner
import es.sebas1705.haltera.feature.nutrition.scan.NoBarcodeScanner
import es.sebas1705.haltera.feature.nutrition.ui.day.DayViewModel
import es.sebas1705.haltera.feature.nutrition.ui.detail.FoodDetailArgs
import es.sebas1705.haltera.feature.nutrition.ui.detail.FoodDetailViewModel
import es.sebas1705.haltera.feature.nutrition.ui.editor.FoodEditorArgs
import es.sebas1705.haltera.feature.nutrition.ui.editor.FoodEditorViewModel
import es.sebas1705.haltera.feature.nutrition.ui.goals.GoalsViewModel
import es.sebas1705.haltera.feature.nutrition.ui.recipe.RecipeArgs
import es.sebas1705.haltera.feature.nutrition.ui.recipe.RecipeEditorViewModel
import es.sebas1705.haltera.feature.nutrition.ui.search.FoodSearchViewModel
import es.sebas1705.haltera.feature.nutrition.ui.stats.StatsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin bindings of the nutrition feature (ViewModels, feature-local classes). Registered in composeApp, which may
 * also bind a platform [BarcodeScanner] (none: scanning is hidden).
 */
val nutritionModule = module {
    single<AppLanguage>(createdAtStart = false) { AppLanguage { Locale.current.language } }
    single { Today.System }
    viewModel { DayViewModel(get(), get()) }
    viewModel { FoodSearchViewModel(get(), get(), getOrNull<BarcodeScanner>() ?: NoBarcodeScanner) }
    viewModel { (args: FoodDetailArgs) -> FoodDetailViewModel(args, get()) }
    viewModel { (args: FoodEditorArgs) -> FoodEditorViewModel(args.foodId, args.barcode, get()) }
    viewModel { (args: RecipeArgs) -> RecipeEditorViewModel(args.recipeId, get()) }
    viewModel { GoalsViewModel(get(), get(), get()) }
    viewModel { StatsViewModel(get(), get(), get()) }
}
