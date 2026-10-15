package es.sebas1705.ironlog.feature.exercises.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.network.ktor3.KtorNetworkFetcherFactory
import es.sebas1705.ironlog.data.models.Muscle
import es.sebas1705.ironlog.feature.exercises.model.frameUrl
import es.sebas1705.ironlog.feature.exercises.resources.Res
import es.sebas1705.ironlog.feature.exercises.resources.exercises_media_description
import es.sebas1705.ironlog.feature.exercises.resources.exercises_media_placeholder
import es.sebas1705.ironlog.feature.exercises.ui.common.label
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

private const val FRAME_INTERVAL_MS = 1_000L
private const val MEDIA_ASPECT_RATIO = 3f / 2f
private const val PLACEHOLDER_ASPECT_RATIO = 2f

/** One image loader for the feature, so frames share the memory cache across detail screens. */
private object FrameImages {
    private var loader: ImageLoader? = null

    /** Called from composition (main thread) only. */
    fun loader(context: PlatformContext): ImageLoader = loader ?: ImageLoader.Builder(context)
        .components { add(KtorNetworkFetcherFactory()) }
        .build()
        .also { loader = it }
}

/**
 * The variant's frames alternating every second (spec §6.4), or a muscle placeholder card when it has
 * none or they cannot be loaded (offline).
 */
@Composable
internal fun ExerciseMedia(
    exerciseName: String,
    frames: List<String>,
    primaryMuscles: List<Muscle>,
    modifier: Modifier = Modifier,
    maxHeight: Dp = Dp.Infinity,
) {
    var failed by remember(frames) { mutableStateOf(false) }
    if (frames.isEmpty() || failed) {
        MusclePlaceholder(primaryMuscles, modifier.heightIn(max = maxHeight))
        return
    }
    var loaded by remember(frames) { mutableStateOf(false) }
    val context = LocalPlatformContext.current
    val loader = remember(context) { FrameImages.loader(context) }
    var shown by remember(frames) { mutableIntStateOf(0) }
    LaunchedEffect(frames) {
        while (frames.size > 1) {
            delay(FRAME_INTERVAL_MS)
            shown = (shown + 1) % frames.size
        }
    }
    val description = stringResource(Res.string.exercises_media_description, exerciseName)
    // The box keeps the photo's aspect ratio (capped at [maxHeight]) before it loads, so nothing jumps.
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .heightIn(max = maxHeight)
                .aspectRatio(MEDIA_ASPECT_RATIO)
                .clip(MaterialTheme.shapes.medium)
                .background(if (loaded) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            if (!loaded) CircularProgressIndicator()
            // Every frame stays composed (and loaded once); only the current one is visible.
            frames.forEachIndexed { index, path ->
                AsyncImage(
                    model = frameUrl(path),
                    contentDescription = null,
                    imageLoader = loader,
                    contentScale = ContentScale.Fit,
                    onSuccess = { if (index == 0) loaded = true },
                    onError = { failed = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (index == shown) 1f else 0f),
                )
            }
        }
    }
}

@Composable
private fun MusclePlaceholder(primaryMuscles: List<Muscle>, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().aspectRatio(PLACEHOLDER_ASPECT_RATIO),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            val muscles = primaryMuscles.map { stringResource(it.label) }
            if (muscles.isNotEmpty()) {
                Text(
                    text = muscles.joinToString(" · "),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = stringResource(Res.string.exercises_media_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Unspecified,
                textAlign = TextAlign.Center,
            )
        }
    }
}
