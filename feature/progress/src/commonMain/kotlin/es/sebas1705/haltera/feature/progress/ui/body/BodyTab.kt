package es.sebas1705.haltera.feature.progress.ui.body

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.sebas1705.haltera.feature.progress.resources.Res
import es.sebas1705.haltera.feature.progress.resources.progress_body_measurements
import es.sebas1705.haltera.feature.progress.resources.progress_body_photos
import es.sebas1705.haltera.feature.progress.resources.progress_body_weight
import es.sebas1705.haltera.feature.progress.ui.bodyweight.BodyWeightTab
import es.sebas1705.haltera.feature.progress.ui.measurements.MeasurementsTab
import es.sebas1705.haltera.feature.progress.ui.photos.PhotosTab
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private enum class BodySection(val label: StringResource) {
    WEIGHT(Res.string.progress_body_weight),
    MEASUREMENTS(Res.string.progress_body_measurements),
    PHOTOS(Res.string.progress_body_photos),
}

/** The Body tab of Progress: body weight, measurements and progress photos, one at a time. */
@Composable
internal fun BodyTab() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        SingleChoiceSegmentedButtonRow(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).widthIn(max = 560.dp).fillMaxWidth(),
        ) {
            BodySection.entries.forEachIndexed { index, section ->
                SegmentedButton(
                    selected = index == selected,
                    onClick = { selected = index },
                    shape = SegmentedButtonDefaults.itemShape(index, BodySection.entries.size),
                    icon = {},
                ) {
                    // Long labels ("Measurements" on a small phone) shrink to fit instead of being cut.
                    BasicText(
                        stringResource(section.label),
                        style = MaterialTheme.typography.labelLarge.copy(color = LocalContentColor.current),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 10.sp,
                            maxFontSize = MaterialTheme.typography.labelLarge.fontSize,
                        ),
                    )
                }
            }
        }
        when (BodySection.entries[selected]) {
            BodySection.WEIGHT -> BodyWeightTab()
            BodySection.MEASUREMENTS -> MeasurementsTab()
            BodySection.PHOTOS -> PhotosTab()
        }
    }
}
