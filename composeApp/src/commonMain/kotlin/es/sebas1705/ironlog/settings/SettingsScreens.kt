package es.sebas1705.ironlog.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import es.sebas1705.ironlog.core.units.PlateCalculator
import es.sebas1705.ironlog.core.units.WeightUnit
import es.sebas1705.ironlog.core.units.Weights
import es.sebas1705.ironlog.resources.Res
import es.sebas1705.ironlog.resources.back
import es.sebas1705.ironlog.resources.credits_catalog
import es.sebas1705.ironlog.resources.credits_license
import es.sebas1705.ironlog.resources.credits_title
import es.sebas1705.ironlog.resources.settings_bar_weight
import es.sebas1705.ironlog.resources.settings_credits
import es.sebas1705.ironlog.resources.settings_keep_screen_on
import es.sebas1705.ironlog.resources.settings_plates
import es.sebas1705.ironlog.resources.settings_rest_sound
import es.sebas1705.ironlog.resources.settings_rest_vibration
import es.sebas1705.ironlog.resources.settings_title
import es.sebas1705.ironlog.resources.settings_units
import es.sebas1705.ironlog.resources.unit_kg
import es.sebas1705.ironlog.resources.unit_lb
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Serializable
data object SettingsRoute

@Serializable
data object CreditsRoute

fun NavGraphBuilder.settingsGraph(navController: NavController) {
    composable<SettingsRoute> {
        SettingsScreen(onBack = { navController.popBackStack() }, onCredits = { navController.navigate(CreditsRoute) })
    }
    composable<CreditsRoute> { CreditsScreen(onBack = { navController.popBackStack() }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackScaffold(title: StringResource, onBack: () -> Unit, content: @Composable (Modifier) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
            )
        },
    ) { padding -> content(Modifier.padding(padding)) }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onCredits: () -> Unit, viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = state.settings
    BackScaffold(Res.string.settings_title, onBack) { modifier ->
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionTitle(Res.string.settings_units)
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeightUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = settings.unit == unit,
                        onClick = { viewModel.onEvent(SettingsIntent.SetUnit(unit)) },
                        label = {
                            Text(
                                stringResource(if (unit == WeightUnit.KG) Res.string.unit_kg else Res.string.unit_lb)
                            )
                        },
                    )
                }
            }
            SectionTitle(Res.string.settings_bar_weight)
            ChipRow(
                options = barOptions(settings.unit),
                selected = { it == settings.barWeightGrams },
                unit = settings.unit,
                onClick = { viewModel.onEvent(SettingsIntent.SetBarWeight(it)) },
            )
            SectionTitle(Res.string.settings_plates)
            ChipRow(
                options = PlateCalculator.defaultPlates(settings.unit),
                selected = { it in settings.availablePlatesGrams },
                unit = settings.unit,
                onClick = { viewModel.onEvent(SettingsIntent.TogglePlate(it)) },
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SwitchRow(Res.string.settings_keep_screen_on, settings.keepScreenOn) {
                viewModel.onEvent(SettingsIntent.SetKeepScreenOn(it))
            }
            SwitchRow(
                Res.string.settings_rest_sound,
                settings.restSound
            ) { viewModel.onEvent(SettingsIntent.SetRestSound(it)) }
            SwitchRow(Res.string.settings_rest_vibration, settings.restVibration) {
                viewModel.onEvent(SettingsIntent.SetRestVibration(it))
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            TextButton(onClick = onCredits, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(stringResource(Res.string.settings_credits))
            }
        }
    }
}

@Suppress("MagicNumber") // Common bar weights.
private fun barOptions(unit: WeightUnit): List<Long> = when (unit) {
    WeightUnit.KG -> listOf(20_000L, 15_000L, 10_000L, 7_000L)
    WeightUnit.LB -> listOf(45.0, 35.0, 25.0, 15.0).map { Weights.lbToGrams(it) }
}

@Composable
private fun SectionTitle(text: StringResource) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(options: List<Long>, selected: (Long) -> Boolean, unit: WeightUnit, onClick: (Long) -> Unit) {
    val unitLabel = stringResource(if (unit == WeightUnit.KG) Res.string.unit_kg else Res.string.unit_lb)
    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { grams ->
            FilterChip(
                selected = selected(grams),
                onClick = { onClick(grams) },
                label = { Text("${Weights.format(grams, unit)} $unitLabel") },
            )
        }
    }
}

@Composable
private fun SwitchRow(label: StringResource, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Credits: the exercise catalogue attribution and the app licence, read from the bundled files. */
@Composable
fun CreditsScreen(onBack: () -> Unit) {
    var attribution by remember { mutableStateOf("") }
    var license by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        attribution = Res.readBytes("files/ATTRIBUTION.md").decodeToString()
        license = Res.readBytes("files/LICENSE.txt").decodeToString()
    }
    BackScaffold(Res.string.credits_title, onBack) { modifier ->
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.credits_catalog), style = MaterialTheme.typography.titleMedium)
            Text(attribution.withoutMarkdown(), style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider()
            Text(stringResource(Res.string.credits_license), style = MaterialTheme.typography.titleMedium)
            Text(license, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Light clean-up of the Markdown attribution for plain-text display. */
private fun String.withoutMarkdown(): String = lines()
    .filterNot { it.startsWith("# ") || it.startsWith("Generated by") }
    .joinToString(
        "\n"
    ) { it.removePrefix("## ").replace("`", "").replace(Regex("\\[([^]]+)]\\(([^)]+)\\)"), "$1 ($2)") }
    .trim()
