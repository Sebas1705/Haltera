@file:Suppress("MatchingDeclarationName") // The filter bar and its chip spec belong together.

package es.sebas1705.haltera.core.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** One filter of an [ExerciseFilterBar]: a chip that opens a menu with "Any" plus [options]. */
class FilterChipSpec<T : Any>(
    val title: String,
    val selected: T?,
    val options: List<T>,
    val optionLabel: @Composable (T) -> String,
    val onSelect: (T?) -> Unit,
)

/**
 * A horizontally scrolling row of filter chips (category, muscle, equipment…) with a "Clear" button
 * shown while any filter is set. Labels come from the caller, so each feature keeps its own strings.
 */
@Composable
fun ExerciseFilterBar(
    filters: List<FilterChipSpec<*>>,
    anyLabel: String,
    clearLabel: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // "Clear" comes first: at the end of the scrolling row it was cut off on narrow phones.
        if (filters.any { it.selected != null }) {
            AssistChip(
                onClick = onClear,
                label = { Text(clearLabel, maxLines = 1) },
                leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) },
            )
        }
        @Suppress("UNCHECKED_CAST") // Each spec is only used with its own option type.
        filters.forEach { FilterMenuChip(it as FilterChipSpec<Any>, anyLabel) }
    }
}

@Composable
private fun <T : Any> FilterMenuChip(filter: FilterChipSpec<T>, anyLabel: String) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = filter.selected != null,
            onClick = { expanded = true },
            label = { Text(filter.selected?.let { filter.optionLabel(it) } ?: filter.title) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = filter.title) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(anyLabel) },
                leadingIcon = { SelectedMark(filter.selected == null) },
                onClick = {
                    expanded = false
                    filter.onSelect(null)
                },
            )
            filter.options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(filter.optionLabel(option)) },
                    leadingIcon = { SelectedMark(option == filter.selected) },
                    onClick = {
                        expanded = false
                        filter.onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun SelectedMark(selected: Boolean) {
    if (selected) Icon(Icons.Filled.Check, contentDescription = null) else Spacer(Modifier.size(24.dp))
}
