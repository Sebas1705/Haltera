package es.sebas1705.ironlog.feature.routines.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.sebas1705.ironlog.core.ui.theme.tabularFigures
import es.sebas1705.ironlog.domain.usecases.routinescore.RoutineFocus
import es.sebas1705.ironlog.domain.usecases.routinescore.RoutineScore
import es.sebas1705.ironlog.feature.routines.resources.Res
import es.sebas1705.ironlog.feature.routines.resources.routines_score_cardio
import es.sebas1705.ironlog.feature.routines.resources.routines_score_description
import es.sebas1705.ironlog.feature.routines.resources.routines_score_short
import es.sebas1705.ironlog.feature.routines.ui.analysis.scoreColor
import es.sebas1705.ironlog.feature.routines.ui.analysis.verdictLabel
import org.jetbrains.compose.resources.stringResource

/**
 * "Score 87" pill coloured by the score, or "Cardio" for conditioning routines. Nothing while the score is
 * unknown or the routine has no training slots.
 */
@Composable
internal fun ScoreBadge(score: RoutineScore?, modifier: Modifier = Modifier) {
    if (score == null || score.focus == RoutineFocus.EMPTY) return
    if (!score.scored) {
        Pill(
            stringResource(Res.string.routines_score_cardio),
            MaterialTheme.colorScheme.onSurfaceVariant,
            null,
            modifier
        )
        return
    }
    val description = stringResource(Res.string.routines_score_description, score.total, verdictLabel(score.verdict))
    Pill(stringResource(Res.string.routines_score_short, score.total), scoreColor(score.total), description, modifier)
}

@Composable
private fun Pill(text: String, color: androidx.compose.ui.graphics.Color, description: String?, modifier: Modifier) {
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
        shape = MaterialTheme.shapes.small,
        modifier = if (description != null) modifier.clearAndSetSemantics { contentDescription = description } else modifier,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.tabularFigures(),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
