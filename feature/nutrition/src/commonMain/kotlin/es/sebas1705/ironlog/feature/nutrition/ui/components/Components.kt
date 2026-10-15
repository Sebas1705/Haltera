package es.sebas1705.ironlog.feature.nutrition.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.sebas1705.ironlog.core.ui.theme.IronlogTheme
import es.sebas1705.ironlog.core.ui.theme.tabularFigures
import es.sebas1705.ironlog.data.models.Food
import es.sebas1705.ironlog.data.models.FoodUnit
import es.sebas1705.ironlog.data.models.Meal
import es.sebas1705.ironlog.data.models.Nutrients
import es.sebas1705.ironlog.feature.nutrition.model.Amounts
import es.sebas1705.ironlog.feature.nutrition.resources.Res
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_back
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_carbs
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_close
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_date_full
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_energy
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_fat
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_fiber
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_grams_value
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_info
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_kcal
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_kcal_value
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_meal_breakfast
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_meal_dinner
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_meal_lunch
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_meal_snack
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_ml_value
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_months_short
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_protein
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_salt
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_sugar
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_unknown
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_weekdays_short
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/** The UI language code for food names and number formatting. */
@Composable
internal fun language(): String = Locale.current.language

/** Macro colours from the brand palette: protein accent, carbohydrates gold, fat neutral. */
internal object MacroColors {
    val protein: Color @Composable get() = MaterialTheme.colorScheme.primary
    val carbs: Color @Composable get() = IronlogTheme.colors.prGold
    val fat: Color @Composable get() = MaterialTheme.colorScheme.secondary
    val kcal: Color @Composable get() = IronlogTheme.colors.accent
    val withinGoal: Color @Composable get() = IronlogTheme.colors.success
}

internal val Meal.label: StringResource
    get() = when (this) {
        Meal.BREAKFAST -> Res.string.nutrition_meal_breakfast
        Meal.LUNCH -> Res.string.nutrition_meal_lunch
        Meal.DINNER -> Res.string.nutrition_meal_dinner
        Meal.SNACK -> Res.string.nutrition_meal_snack
    }

@Composable
internal fun kcalText(kcal: Double): String = stringResource(Res.string.nutrition_kcal_value, Amounts.format(kcal, 0))

@Composable
internal fun gramsText(grams: Double, decimals: Int = 1): String =
    stringResource(Res.string.nutrition_grams_value, Amounts.format(grams, decimals, language()))

/** An amount of a food: "150 g" or "250 ml". */
@Composable
internal fun amountText(grams: Double, unit: FoodUnit): String {
    val value = Amounts.format(grams, 1, language())
    return stringResource(
        if (unit == FoodUnit.ML) Res.string.nutrition_ml_value else Res.string.nutrition_grams_value,
        value
    )
}

/** "Thu 15 Oct 2026". */
@Composable
internal fun formatDate(date: LocalDate, withYear: Boolean = true): String {
    val month = stringArrayResource(Res.array.nutrition_months_short)[date.month.number - 1]
    val weekday = stringArrayResource(Res.array.nutrition_weekdays_short)[date.dayOfWeek.ordinal]
    val text = stringResource(Res.string.nutrition_date_full, weekday, date.day.toString(), month, date.year.toString())
    return if (withYear) text else text.substringBeforeLast(' ')
}

@Composable
internal fun shortDate(date: LocalDate): String {
    val month = stringArrayResource(Res.array.nutrition_months_short)[date.month.number - 1]
    return "${date.day} $month"
}

@Composable
internal fun Food.displayName(): String = name(language())

/** A nested screen of the Nutrition tab: its own top bar with a back arrow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NutritionScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    // The shell Scaffold already pads for the system bars.
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.nutrition_back)
                        )
                    }
                },
                actions = { actions() },
                windowInsets = WindowInsets(0),
            )
        },
        floatingActionButton = floatingActionButton,
    ) { padding -> content(Modifier.padding(padding).imePadding()) }
}

/** ⓘ button that explains a figure in a dialog. */
@Composable
internal fun InfoButton(title: String, text: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier.size(36.dp)) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = stringResource(Res.string.nutrition_info, title),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = { Text(text, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                TextButton(
                    onClick = { open = false }
                ) { Text(stringResource(Res.string.nutrition_close)) }
            },
        )
    }
}

/** A titled card with an optional ⓘ explanation. */
@Composable
internal fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    info: String? = null,
    trailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp)
                )
                trailing()
                if (info != null) InfoButton(title, info)
            }
            Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        }
    }
}

/** Ring of [value] against [goal]; past the goal the ring is full and turns warning-coloured. */
@Composable
internal fun EnergyRing(
    value: Double,
    goal: Int?,
    modifier: Modifier = Modifier,
    size: Dp = 148.dp,
    center: @Composable () -> Unit,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val over = goal != null && value > goal * 1.1
    val color = if (over) IronlogTheme.colors.warning else MacroColors.kcal
    val fraction = if (goal == null || goal <= 0) 0f else (value / goal).toFloat().coerceIn(0f, 1f)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            drawArc(
                track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke)
            )
            if (fraction > 0f) {
                drawArc(
                    color,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { center() }
    }
}

/** One macro: name, "eaten / goal g" and a bar of the progress towards the goal. */
@Composable
internal fun MacroBar(label: String, grams: Double, goal: Int?, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                if (goal != null) {
                    "${Amounts.format(
                        grams,
                        0
                    )} / ${gramsText(goal.toDouble(), 0)}"
                } else {
                    gramsText(grams, 0)
                },
                style = MaterialTheme.typography.bodyMedium.tabularFigures(),
            )
        }
        Spacer(Modifier.height(4.dp))
        val fraction = if (goal == null || goal <= 0) 0f else (grams / goal).toFloat().coerceIn(0f, 1f)
        Box(
            Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(Modifier.fillMaxWidth(fraction).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        }
    }
}

/** Energy and nutrients as label/value rows (unknown optional values show as "—"). */
@Composable
internal fun NutrientRows(n: Nutrients, modifier: Modifier = Modifier) {
    val unknown = stringResource(Res.string.nutrition_unknown)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        NutrientRow(stringResource(Res.string.nutrition_energy), kcalText(n.kcal), bold = true)
        NutrientRow(stringResource(Res.string.nutrition_protein), gramsText(n.protein), dot = MacroColors.protein)
        NutrientRow(stringResource(Res.string.nutrition_carbs), gramsText(n.carbs), dot = MacroColors.carbs)
        NutrientRow(
            stringResource(Res.string.nutrition_sugar),
            n.sugar?.let { gramsText(it) } ?: unknown,
            indent = true
        )
        NutrientRow(
            stringResource(Res.string.nutrition_fiber),
            n.fiber?.let { gramsText(it) } ?: unknown,
            indent = true
        )
        NutrientRow(stringResource(Res.string.nutrition_fat), gramsText(n.fat), dot = MacroColors.fat)
        NutrientRow(stringResource(Res.string.nutrition_salt), n.salt?.let { gramsText(it, 2) } ?: unknown)
    }
}

@Composable
private fun NutrientRow(
    label: String,
    value: String,
    bold: Boolean = false,
    indent: Boolean = false,
    dot: Color? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(start = if (indent) 18.dp else 0.dp).semantics(mergeDescendants = true) {
            contentDescription = "$label $value"
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
        }
        val style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
        Text(label, style = style, modifier = Modifier.weight(1f))
        Text(value, style = style.tabularFigures(), fontWeight = if (bold) FontWeight.SemiBold else null)
    }
}
