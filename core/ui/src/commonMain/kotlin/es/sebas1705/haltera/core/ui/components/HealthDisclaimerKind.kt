package es.sebas1705.haltera.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import es.sebas1705.haltera.core.ui.resources.Res
import es.sebas1705.haltera.core.ui.resources.health_disclaimer_analysis
import es.sebas1705.haltera.core.ui.resources.health_disclaimer_general
import es.sebas1705.haltera.core.ui.resources.health_disclaimer_nutrition
import es.sebas1705.haltera.core.ui.resources.health_disclaimer_statistics
import org.jetbrains.compose.resources.stringResource

/** Where a "this is not medical advice" line is shown; one wording per place, kept together here. */
enum class HealthDisclaimerKind { GENERAL, STATISTICS, ANALYSIS, NUTRITION }

/**
 * The one shared health disclaimer (Google Play health-content policy): neutral, short, never a blocker.
 * Shown in About, under the statistics overview, in the routine analysis and next to the nutrition goal
 * calculator.
 */
@Composable
fun HealthDisclaimer(kind: HealthDisclaimerKind, modifier: Modifier = Modifier) {
    val text = when (kind) {
        HealthDisclaimerKind.GENERAL -> stringResource(Res.string.health_disclaimer_general)
        HealthDisclaimerKind.STATISTICS -> stringResource(Res.string.health_disclaimer_statistics)
        HealthDisclaimerKind.ANALYSIS -> stringResource(Res.string.health_disclaimer_analysis)
        HealthDisclaimerKind.NUTRITION -> stringResource(Res.string.health_disclaimer_nutrition)
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
