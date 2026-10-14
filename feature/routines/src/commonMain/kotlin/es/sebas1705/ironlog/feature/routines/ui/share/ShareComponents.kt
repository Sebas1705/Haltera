package es.sebas1705.ironlog.feature.routines.ui.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.ironlog.feature.routines.resources.Res
import es.sebas1705.ironlog.feature.routines.resources.routines_close
import es.sebas1705.ironlog.feature.routines.resources.routines_qr_hint
import es.sebas1705.ironlog.feature.routines.resources.routines_qr_too_large
import es.sebas1705.ironlog.feature.routines.resources.routines_share_error
import es.sebas1705.ironlog.feature.routines.resources.routines_share_file
import es.sebas1705.ironlog.feature.routines.resources.routines_show_qr
import es.sebas1705.ironlog.feature.routines.resources.routines_untitled
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.jetbrains.compose.resources.stringResource

/** "Share file" and "Show QR" entries for a routine menu; each closes the menu first. */
@Composable
internal fun ExportMenuItems(routineId: String, export: RoutineExportViewModel, closeMenu: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.routines_share_file)) },
        leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
        onClick = {
            closeMenu()
            export.onEvent(RoutineExportIntent.ShareFile(routineId))
        },
    )
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.routines_show_qr)) },
        onClick = {
            closeMenu()
            export.onEvent(RoutineExportIntent.ShowQr(routineId))
        },
    )
}

/** The full-screen QR and the export error, driven by [export]. */
@Composable
internal fun ExportOverlays(export: RoutineExportViewModel) {
    val state by export.uiState.collectAsStateWithLifecycle()
    state.qr?.let { qr -> QrDialog(qr, onDismiss = { export.onEvent(RoutineExportIntent.DismissQr) }) }
    if (state.error) {
        AlertDialog(
            onDismissRequest = { export.onEvent(RoutineExportIntent.ErrorShown) },
            confirmButton = {
                TextButton(onClick = { export.onEvent(RoutineExportIntent.ErrorShown) }) {
                    Text(stringResource(Res.string.routines_close))
                }
            },
            text = { Text(stringResource(Res.string.routines_share_error)) },
        )
    }
}

@Composable
private fun QrDialog(qr: QrState, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Text(
                    qr.name.ifBlank { stringResource(Res.string.routines_untitled) },
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                if (qr.code != null) {
                    Box(
                        Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f)
                            .background(Color.White, MaterialTheme.shapes.medium).padding(16.dp),
                    ) {
                        Image(
                            painter = rememberQrCodePainter(qr.code),
                            contentDescription = stringResource(Res.string.routines_show_qr),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Text(
                        stringResource(Res.string.routines_qr_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        stringResource(Res.string.routines_qr_too_large),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }
                Button(onClick = onDismiss) { Text(stringResource(Res.string.routines_close)) }
            }
        }
    }
}
