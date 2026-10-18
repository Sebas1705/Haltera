package es.sebas1705.haltera.feature.progress.ui.photos

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.haltera.core.ui.components.LoadingScreen
import es.sebas1705.haltera.data.models.ProgressPhoto
import es.sebas1705.haltera.feature.progress.model.toLocalDate
import es.sebas1705.haltera.feature.progress.resources.*
import es.sebas1705.haltera.feature.progress.ui.components.ChartEmpty
import es.sebas1705.haltera.feature.progress.ui.components.DateField
import es.sebas1705.haltera.feature.progress.ui.components.DateFormat
import es.sebas1705.haltera.feature.progress.ui.components.rememberDateFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.abs

private val THUMB_MIN = 104.dp
private const val THUMB_CACHE = 48
private const val LOAD_FACTOR = 0.75f

/** Decoded thumbnails kept while the tab is shown, so scrolling back does not decode them again. */
private class ThumbnailCache : LinkedHashMap<String, ImageBitmap>(THUMB_CACHE, LOAD_FACTOR, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean = size > THUMB_CACHE
}

/** Progress photos: a grid by date (latest first), add with the system picker, open to edit or delete, compare two. */
@Composable
internal fun PhotosTab(viewModel: PhotosViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onEvent: (PhotosIntent) -> Unit = { viewModel.onEvent(it) }
    if (state.loading) {
        LoadingScreen()
        return
    }
    val dates = rememberDateFormat()
    val cache = remember { ThumbnailCache() }
    val thumb: suspend (String) -> ByteArray? = viewModel::thumbnail
    val image: suspend (String) -> ByteArray? = viewModel::image
    LazyVerticalGrid(
        columns = GridCells.Adaptive(THUMB_MIN),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) { Header(state, onEvent) }
        items(state.photos, key = { it.id }) { photo ->
            PhotoTile(photo, dates, cache, thumb) { onEvent(PhotosIntent.Open(photo.id)) }
        }
    }
    state.pending?.let { PendingDialog(it, state, onEvent) }
    state.detail?.let { DetailDialog(it, state, dates, image, onEvent) }
    state.comparison?.let { ComparisonDialog(it, state, dates, cache, thumb, image, onEvent) }
    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = { onEvent(PhotosIntent.DismissError) },
            text = {
                Text(
                    stringResource(
                        if (error == PhotoError.READ) Res.string.progress_p_error_read else Res.string.progress_p_error_save,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onEvent(PhotosIntent.DismissError) }
                ) { Text(stringResource(Res.string.progress_ok)) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(state: PhotosState, onEvent: (PhotosIntent) -> Unit) {
    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.photos.isEmpty()) ChartEmpty(stringResource(Res.string.progress_p_empty))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.pickerAvailable) {
                Button(onClick = { onEvent(PhotosIntent.Add) }, enabled = !state.busy) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(Res.string.progress_p_add), modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (state.canCompare) {
                OutlinedButton(onClick = { onEvent(PhotosIntent.Compare) }) {
                    Text(stringResource(Res.string.progress_p_compare))
                }
            }
            if (state.busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        if (!state.pickerAvailable) {
            Text(
                stringResource(Res.string.progress_p_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(Res.string.progress_p_private),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PhotoTile(
    photo: ProgressPhoto,
    dates: DateFormat,
    cache: ThumbnailCache,
    load: suspend (String) -> ByteArray?,
    onClick: () -> Unit,
) {
    val date = dates.full(photo.takenAt)
    val description = stringResource(Res.string.progress_p_photo_description, date)
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick).semantics(mergeDescendants = true) {
            contentDescription = description
        }
    ) {
        Column {
            PhotoImage(photo.id, cache, load, Modifier.fillMaxWidth().aspectRatio(THUMB_ASPECT), ContentScale.Crop)
            Text(
                date,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

private const val THUMB_ASPECT = 3f / 4f

/** The image of [id] decoded off the main thread ([cache] keeps thumbnails); a plain box while it loads. */
@OptIn(ExperimentalResourceApi::class)
@Composable
private fun PhotoImage(
    id: String,
    cache: ThumbnailCache?,
    load: suspend (String) -> ByteArray?,
    modifier: Modifier,
    scale: ContentScale,
) {
    val bitmap by produceState(cache?.get(id), id) {
        if (value == null) {
            value = withContext(Dispatchers.Default) {
                load(id)?.let { bytes -> runCatching { bytes.decodeToImageBitmap() }.getOrNull() }
            }?.also { cache?.put(id, it) }
        }
    }
    val image = bitmap
    if (image == null) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    } else {
        Image(image, contentDescription = null, modifier = modifier, contentScale = scale)
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun PendingDialog(pending: PendingPhoto, state: PhotosState, onEvent: (PhotosIntent) -> Unit) {
    val preview = remember(pending) { runCatching { pending.picked.thumbnail.decodeToImageBitmap() }.getOrNull() }
    AlertDialog(
        onDismissRequest = { onEvent(PhotosIntent.DiscardPending) },
        title = { Text(stringResource(Res.string.progress_p_new_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                preview?.let {
                    Image(
                        it,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
                DateField(
                    pending.dateUtcMillis,
                    state.maxDateUtcMillis
                ) { onEvent(PhotosIntent.PendingDateChanged(it)) }
                NoteField(pending.notes) { onEvent(PhotosIntent.PendingNotesChanged(it)) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(PhotosIntent.SavePending) }, enabled = !state.busy) {
                Text(stringResource(Res.string.progress_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(PhotosIntent.DiscardPending) }
            ) { Text(stringResource(Res.string.progress_cancel)) }
        },
    )
}

@Composable
private fun NoteField(text: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = onChange,
        label = { Text(stringResource(Res.string.progress_note_optional)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
}

/** A full-screen dialog with a close button, the [title] and [content]. */
@Composable
private fun FullScreen(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.progress_p_close))
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                content()
            }
        }
    }
}

@Composable
private fun DetailDialog(
    detail: PhotoDetail,
    state: PhotosState,
    dates: DateFormat,
    load: suspend (String) -> ByteArray?,
    onEvent: (PhotosIntent) -> Unit,
) {
    FullScreen(dates.full(detail.photo.takenAt), onClose = { onEvent(PhotosIntent.CloseDetail) }) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val description = stringResource(Res.string.progress_p_photo_description, dates.full(detail.photo.takenAt))
            PhotoImage(
                detail.photo.id,
                null,
                load,
                Modifier.fillMaxWidth().heightIn(
                    max = 520.dp
                ).aspectRatio(THUMB_ASPECT, matchHeightConstraintsFirst = true)
                    .align(Alignment.CenterHorizontally).semantics { contentDescription = description },
                ContentScale.Fit,
            )
            Text(stringResource(Res.string.progress_p_edit), style = MaterialTheme.typography.titleMedium)
            DateField(detail.dateUtcMillis, state.maxDateUtcMillis) { onEvent(PhotosIntent.DetailDateChanged(it)) }
            NoteField(detail.notes) { onEvent(PhotosIntent.DetailNotesChanged(it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { onEvent(PhotosIntent.SaveDetail) }
                ) { Text(stringResource(Res.string.progress_save)) }
                TextButton(onClick = { onEvent(PhotosIntent.AskDelete) }) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        stringResource(Res.string.progress_delete),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
    if (detail.confirmDelete) {
        AlertDialog(
            onDismissRequest = { onEvent(PhotosIntent.CancelDelete) },
            title = { Text(stringResource(Res.string.progress_p_delete_title)) },
            text = { Text(stringResource(Res.string.progress_p_delete_text)) },
            confirmButton = {
                TextButton(onClick = { onEvent(PhotosIntent.ConfirmDelete) }) {
                    Text(stringResource(Res.string.progress_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onEvent(PhotosIntent.CancelDelete) }
                ) { Text(stringResource(Res.string.progress_cancel)) }
            },
        )
    }
}

@Suppress("LongParameterList") // The comparison, its photos and the two image loaders.
@Composable
private fun ComparisonDialog(
    comparison: PhotoComparison,
    state: PhotosState,
    dates: DateFormat,
    cache: ThumbnailCache,
    thumb: suspend (String) -> ByteArray?,
    load: suspend (String) -> ByteArray?,
    onEvent: (PhotosIntent) -> Unit,
) {
    val left = state.photos.firstOrNull { it.id == comparison.leftId } ?: return
    val right = state.photos.firstOrNull { it.id == comparison.rightId } ?: return
    FullScreen(
        stringResource(Res.string.progress_p_compare_title),
        onClose = { onEvent(PhotosIntent.CloseComparison) }
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                apart(left, right),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(CompareSide.LEFT to left, CompareSide.RIGHT to right).forEach { (side, photo) ->
                    Side(side, photo, dates, load, onEvent, Modifier.weight(1f).fillMaxSize())
                }
            }
            comparison.choosing?.let { side ->
                Text(
                    stringResource(Res.string.progress_p_compare_pick, sideName(side)),
                    style = MaterialTheme.typography.titleSmall,
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(state.photos, key = { it.id }) { photo ->
                        Column(Modifier.size(width = 88.dp, height = 140.dp)) {
                            PhotoTile(photo, dates, cache, thumb) { onEvent(PhotosIntent.PickForSide(photo.id)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Side(
    side: CompareSide,
    photo: ProgressPhoto,
    dates: DateFormat,
    load: suspend (String) -> ByteArray?,
    onEvent: (PhotosIntent) -> Unit,
    modifier: Modifier,
) {
    val date = dates.full(photo.takenAt)
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(date, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val description = stringResource(Res.string.progress_p_photo_description, date)
        PhotoImage(
            photo.id,
            null,
            load,
            Modifier.weight(
                1f
            ).fillMaxWidth().clip(RoundedCornerShape(8.dp)).semantics { contentDescription = description },
            ContentScale.Fit,
        )
        val change = stringResource(Res.string.progress_p_compare_change, sideName(side))
        TextButton(
            onClick = { onEvent(PhotosIntent.ChooseSide(side)) },
            modifier = Modifier.semantics { contentDescription = change },
        ) { Text(stringResource(Res.string.progress_p_change)) }
    }
}

@Composable
private fun sideName(side: CompareSide): String =
    stringResource(
        if (side == CompareSide.LEFT) Res.string.progress_p_compare_left else Res.string.progress_p_compare_right
    )

/** "12 days apart" between the dates of the two photos (local calendar days). */
@Composable
private fun apart(a: ProgressPhoto, b: ProgressPhoto): String {
    val zone = TimeZone.currentSystemDefault()
    val days = abs(a.takenAt.toLocalDate(zone).daysUntil(b.takenAt.toLocalDate(zone)))
    return if (days == 0) {
        stringResource(Res.string.progress_p_same_day)
    } else {
        pluralStringResource(Res.plurals.progress_p_days_apart, days, days)
    }
}
