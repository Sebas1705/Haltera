package es.sebas1705.ironlog.feature.nutrition.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.ironlog.core.ui.theme.tabularFigures
import es.sebas1705.ironlog.data.models.Food
import es.sebas1705.ironlog.data.models.FoodSource
import es.sebas1705.ironlog.data.models.FoodUnit
import es.sebas1705.ironlog.feature.nutrition.model.AmountMode
import es.sebas1705.ironlog.feature.nutrition.model.Amounts
import es.sebas1705.ironlog.feature.nutrition.resources.Res
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_add
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_amount_grams
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_amount_ml
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_badge_off
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_badge_own
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_badge_recipe
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_cancel
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_create_food
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_error
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_favourite_add
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_favourite_remove
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_new_food
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_new_recipe
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_per100_line
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_per100ml_line
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_scan
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_scan_not_found
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_scan_not_found_title
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_scan_unavailable
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_search_clear
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_search_empty
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_search_hint
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_servings
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_tab_empty_favourites
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_tab_empty_mine
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_tab_empty_recent
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_tab_favourites
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_tab_mine
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_tab_recent
import es.sebas1705.ironlog.feature.nutrition.ui.components.BarcodeIcon
import es.sebas1705.ironlog.feature.nutrition.ui.components.NutritionScaffold
import es.sebas1705.ironlog.feature.nutrition.ui.components.displayName
import es.sebas1705.ironlog.feature.nutrition.ui.components.language
import org.jetbrains.compose.resources.stringResource

/**
 * Finds a food to log ([pickMode] false: tapping opens it) or an ingredient for a recipe ([pickMode] true: tapping
 * asks the amount and returns it with [onPicked]).
 */
@Suppress("LongMethod") // Screen with its dialogs and effects.
@Composable
internal fun FoodSearchScreen(
    viewModel: FoodSearchViewModel,
    title: String,
    pickMode: Boolean,
    onBack: () -> Unit,
    onOpenFood: (String) -> Unit,
    onPicked: (foodId: String, grams: Double) -> Unit,
    onCreateFood: (barcode: String?) -> Unit,
    onCreateRecipe: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onEvent: (FoodSearchIntent) -> Unit = { viewModel.onEvent(it) }
    var picking by remember { mutableStateOf<Food?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val unavailable = stringResource(Res.string.nutrition_scan_unavailable)
    val errorText = stringResource(Res.string.nutrition_error)

    LaunchedEffect(state.openFoodId) {
        val id = state.openFoodId ?: return@LaunchedEffect
        onEvent(FoodSearchIntent.FoodOpened)
        val food = state.listed.firstOrNull { it.id == id }
        if (pickMode) picking = food ?: viewModelFood(state, id) else onOpenFood(id)
    }
    LaunchedEffect(state.scanProblem) {
        if (state.scanProblem == ScanProblem.Unavailable) {
            onEvent(FoodSearchIntent.ScanProblemShown)
            snackbar.showSnackbar(unavailable)
        }
    }
    LaunchedEffect(state.error) {
        if (state.error) {
            onEvent(FoodSearchIntent.ErrorShown)
            snackbar.showSnackbar(errorText)
        }
    }

    NutritionScaffold(
        title = title,
        onBack = onBack,
        actions = {
            if (state.canScan) {
                IconButton(onClick = { onEvent(FoodSearchIntent.Scan) }) {
                    Icon(BarcodeIcon, contentDescription = stringResource(Res.string.nutrition_scan))
                }
            }
        },
    ) { modifier ->
        Box(modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                SearchField(state.query, onEvent)
                if (state.scanning || state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.query.isBlank()) {
                    Tabs(state.tab, onEvent)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = { onCreateFood(null) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(Res.string.nutrition_new_food), maxLines = 1)
                        }
                        if (!pickMode) {
                            OutlinedButton(onClick = onCreateRecipe, modifier = Modifier.weight(1f)) {
                                Text(stringResource(Res.string.nutrition_new_recipe), maxLines = 1)
                            }
                        }
                    }
                }
                FoodList(
                    state = state,
                    onClick = { food -> if (pickMode) picking = food else onOpenFood(food.id) },
                    onToggleFavourite = { onEvent(FoodSearchIntent.ToggleFavourite(it)) },
                )
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }

    (state.scanProblem as? ScanProblem.NotFound)?.let { problem ->
        AlertDialog(
            onDismissRequest = { onEvent(FoodSearchIntent.ScanProblemShown) },
            title = { Text(stringResource(Res.string.nutrition_scan_not_found_title)) },
            text = { Text(stringResource(Res.string.nutrition_scan_not_found, problem.barcode)) },
            confirmButton = {
                TextButton(onClick = {
                    onEvent(FoodSearchIntent.ScanProblemShown)
                    onCreateFood(problem.barcode)
                }) {
                    Text(stringResource(Res.string.nutrition_create_food))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onEvent(FoodSearchIntent.ScanProblemShown) }
                ) { Text(stringResource(Res.string.nutrition_cancel)) }
            },
        )
    }
    picking?.let { food ->
        AmountDialog(
            food,
            onDismiss = { picking = null },
            onConfirm = { grams ->
                picking = null
                onPicked(food.id, grams)
            }
        )
    }
}

private fun viewModelFood(state: FoodSearchState, id: String): Food? =
    (state.results + state.recents + state.favourites + state.own).firstOrNull { it.id == id }

@Composable
private fun SearchField(query: String, onEvent: (FoodSearchIntent) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = query,
        onValueChange = { onEvent(FoodSearchIntent.QueryChanged(it)) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(stringResource(Res.string.nutrition_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onEvent(FoodSearchIntent.QueryChanged("")) }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(Res.string.nutrition_search_clear))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
    )
}

@Composable
private fun Tabs(tab: FoodTab, onEvent: (FoodSearchIntent) -> Unit) {
    PrimaryTabRow(selectedTabIndex = tab.ordinal) {
        FoodTab.entries.forEach { t ->
            Tab(
                selected = t == tab,
                onClick = { onEvent(FoodSearchIntent.SelectTab(t)) },
                text = {
                    Text(
                        stringResource(
                            when (t) {
                                FoodTab.RECENT -> Res.string.nutrition_tab_recent
                                FoodTab.FAVOURITES -> Res.string.nutrition_tab_favourites
                                FoodTab.MINE -> Res.string.nutrition_tab_mine
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun FoodList(state: FoodSearchState, onClick: (Food) -> Unit, onToggleFavourite: (Food) -> Unit) {
    val foods = state.listed
    if (foods.isEmpty() && !state.searching) {
        val empty = when {
            state.query.isNotBlank() -> Res.string.nutrition_search_empty
            state.tab == FoodTab.RECENT -> Res.string.nutrition_tab_empty_recent
            state.tab == FoodTab.FAVOURITES -> Res.string.nutrition_tab_empty_favourites
            else -> Res.string.nutrition_tab_empty_mine
        }
        Text(
            stringResource(empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(32.dp),
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
        items(foods, key = { it.id }) { food ->
            FoodRow(food, onClick = { onClick(food) }, onToggleFavourite = { onToggleFavourite(food) })
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
internal fun FoodRow(food: Food, onClick: () -> Unit, onToggleFavourite: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().clickable(
            onClick = onClick
        ).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                food.displayName(),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val badge = when (food.source) {
                FoodSource.USER -> stringResource(Res.string.nutrition_badge_own)
                FoodSource.RECIPE -> stringResource(Res.string.nutrition_badge_recipe)
                FoodSource.OFF -> stringResource(Res.string.nutrition_badge_off)
                FoodSource.GENERIC -> null
            }
            val brand = listOfNotNull(food.brand, badge).joinToString(" · ")
            if (brand.isNotEmpty()) {
                Text(
                    brand,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
            val lang = language()
            val n = food.per100
            Text(
                stringResource(
                    if (food.unit == FoodUnit.ML) Res.string.nutrition_per100ml_line else Res.string.nutrition_per100_line,
                    Amounts.format(n.kcal, 0),
                    Amounts.format(n.protein, 1, lang),
                    Amounts.format(n.carbs, 1, lang),
                    Amounts.format(n.fat, 1, lang),
                ),
                style = MaterialTheme.typography.bodySmall.tabularFigures(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onToggleFavourite != null) {
            IconButton(onClick = onToggleFavourite) {
                Icon(
                    if (food.favourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = stringResource(
                        if (food.favourite) Res.string.nutrition_favourite_remove else Res.string.nutrition_favourite_add,
                    ),
                    tint = if (food.favourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Asks how much of [food] (grams/ml or servings) and returns grams. */
@Composable
internal fun AmountDialog(
    food: Food,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
    initialGrams: Double? = null
) {
    var mode by remember { mutableStateOf(AmountMode.GRAMS) }
    var text by remember { mutableStateOf(initialGrams?.let { Amounts.format(it, 1) } ?: "") }
    val grams = Amounts.parse(text)?.let { Amounts.grams(it, mode, food.servingGrams) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(food.displayName(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = {
                        Text(
                            when {
                                mode == AmountMode.SERVINGS -> stringResource(Res.string.nutrition_servings)
                                food.unit == FoodUnit.ML -> stringResource(Res.string.nutrition_amount_ml)
                                else -> stringResource(Res.string.nutrition_amount_grams)
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (food.hasServing) {
                    Row {
                        TextButton(onClick = { mode = AmountMode.GRAMS }, enabled = mode != AmountMode.GRAMS) {
                            Text(
                                stringResource(
                                    if (food.unit == FoodUnit.ML) Res.string.nutrition_amount_ml else Res.string.nutrition_amount_grams
                                )
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { mode = AmountMode.SERVINGS }, enabled = mode != AmountMode.SERVINGS) {
                            Text(stringResource(Res.string.nutrition_servings))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { grams?.let(onConfirm) },
                enabled = grams != null
            ) { Text(stringResource(Res.string.nutrition_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.nutrition_cancel)) } },
    )
}
