package es.sebas1705.ironlog.core.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.sebas1705.ironlog.core.ui.resources.Res
import es.sebas1705.ironlog.core.ui.resources.pr_badge
import es.sebas1705.ironlog.core.ui.theme.IronlogTheme
import org.jetbrains.compose.resources.stringResource

/** Gold "PR" pill marking a personal record. [label] replaces the default text when needed. */
@Composable
fun PrBadge(modifier: Modifier = Modifier, label: String = stringResource(Res.string.pr_badge)) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = IronlogTheme.colors.prGoldContainer,
        contentColor = IronlogTheme.colors.onPrGoldContainer,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
