package es.sebas1705.haltera.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.haltera.data.sync.AccountSession
import es.sebas1705.haltera.data.sync.SyncStatus
import es.sebas1705.haltera.resources.Res
import es.sebas1705.haltera.resources.account_anonymous
import es.sebas1705.haltera.resources.account_cancel
import es.sebas1705.haltera.resources.account_delete
import es.sebas1705.haltera.resources.account_delete_confirm
import es.sebas1705.haltera.resources.account_delete_erase_local
import es.sebas1705.haltera.resources.account_delete_erase_local_hint
import es.sebas1705.haltera.resources.account_reauth_cancelled
import es.sebas1705.haltera.resources.account_delete_text
import es.sebas1705.haltera.resources.account_delete_title
import es.sebas1705.haltera.resources.account_deleted
import es.sebas1705.haltera.resources.account_failed
import es.sebas1705.haltera.resources.account_google
import es.sebas1705.haltera.resources.account_link_google
import es.sebas1705.haltera.resources.account_linked
import es.sebas1705.haltera.resources.account_merged
import es.sebas1705.haltera.resources.account_not_configured
import es.sebas1705.haltera.resources.account_sign_in_google
import es.sebas1705.haltera.resources.account_sign_out
import es.sebas1705.haltera.resources.account_signed_in
import es.sebas1705.haltera.resources.account_signed_out
import es.sebas1705.haltera.resources.account_signing_in
import es.sebas1705.haltera.resources.sync_disabled
import es.sebas1705.haltera.resources.sync_error
import es.sebas1705.haltera.resources.sync_idle
import es.sebas1705.haltera.resources.sync_idle_never
import es.sebas1705.haltera.resources.sync_offline
import es.sebas1705.haltera.resources.sync_syncing
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Settings → Account: who is signed in, sync status, Google link/sign-in, sign out, delete. */
@Composable
fun AccountSection(viewModel: AccountViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(sessionLine(state.session), style = MaterialTheme.typography.bodyLarge)
        state.syncStatus?.let { Text(syncLine(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        AccountActions(state, viewModel::onEvent)
        state.message?.let { message ->
            Text(
                messageLine(message, state.error),
                color = if (message == AccountMessage.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    if (state.confirmDelete) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(AccountIntent.DismissDelete) },
            title = { Text(stringResource(Res.string.account_delete_title)) },
            text = { DeleteDialogBody(state.eraseLocal) { viewModel.onEvent(AccountIntent.SetEraseLocal(it)) } },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(AccountIntent.ConfirmDelete) }) {
                    Text(stringResource(Res.string.account_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(AccountIntent.DismissDelete) }) {
                    Text(stringResource(Res.string.account_cancel))
                }
            },
        )
    }
}

/** What is deleted, and the unticked-by-default box to also wipe this phone. */
@Composable
private fun DeleteDialogBody(eraseLocal: Boolean, onEraseLocal: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.account_delete_text))
        Row(
            Modifier.toggleable(value = eraseLocal, role = Role.Checkbox, onValueChange = onEraseLocal),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(checked = eraseLocal, onCheckedChange = null)
            Column(Modifier.padding(start = 12.dp)) {
                Text(stringResource(Res.string.account_delete_erase_local), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(Res.string.account_delete_erase_local_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountActions(state: AccountState, onEvent: (AccountIntent) -> Unit) {
    val session = state.session
    if (session == AccountSession.NotConfigured) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val enabled = !state.busy
        when {
            session is AccountSession.SignedIn && session.isAnonymous && state.googleAvailable ->
                Button(onClick = { onEvent(AccountIntent.LinkGoogle) }, enabled = enabled) {
                    Text(stringResource(Res.string.account_link_google))
                }
            session == AccountSession.SignedOut && state.googleAvailable ->
                Button(onClick = { onEvent(AccountIntent.SignInWithGoogle) }, enabled = enabled) {
                    Text(stringResource(Res.string.account_sign_in_google))
                }
        }
        if (session is AccountSession.SignedIn) {
            if (!session.isAnonymous) {
                OutlinedButton(onClick = { onEvent(AccountIntent.SignOut) }, enabled = enabled) {
                    Text(stringResource(Res.string.account_sign_out))
                }
            }
            OutlinedButton(onClick = { onEvent(AccountIntent.RequestDelete) }, enabled = enabled) {
                Text(stringResource(Res.string.account_delete), color = MaterialTheme.colorScheme.error)
            }
        }
        if (state.busy) CircularProgressIndicator(Modifier.size(24.dp))
    }
}

@Composable
private fun sessionLine(session: AccountSession): String = when (session) {
    AccountSession.NotConfigured -> stringResource(Res.string.account_not_configured)
    AccountSession.SignedOut -> stringResource(Res.string.account_signed_out)
    is AccountSession.SignedIn -> when {
        session.isAnonymous -> stringResource(Res.string.account_anonymous)
        else -> stringResource(
            Res.string.account_google,
            session.email ?: session.displayName ?: stringResource(Res.string.account_signed_in),
        )
    }
}

@Composable
private fun syncLine(status: SyncStatus): String = when (status) {
    SyncStatus.Disabled -> stringResource(Res.string.sync_disabled)
    is SyncStatus.Idle -> status.lastSyncedAt?.let { stringResource(Res.string.sync_idle, formatTime(it)) }
        ?: stringResource(Res.string.sync_idle_never)
    is SyncStatus.Syncing -> stringResource(Res.string.sync_syncing, status.pending)
    is SyncStatus.Offline -> stringResource(Res.string.sync_offline, status.pending)
    is SyncStatus.Error -> stringResource(Res.string.sync_error, status.pending, status.message)
}

@Composable
private fun messageLine(message: AccountMessage, error: String?): String = when (message) {
    AccountMessage.LINKED -> stringResource(Res.string.account_linked)
    AccountMessage.MERGED -> stringResource(Res.string.account_merged)
    AccountMessage.SIGNED_IN -> stringResource(Res.string.account_signing_in)
    AccountMessage.DELETED -> stringResource(Res.string.account_deleted)
    AccountMessage.REAUTH_CANCELLED -> stringResource(Res.string.account_reauth_cancelled)
    AccountMessage.FAILED -> stringResource(Res.string.account_failed, error.orEmpty())
}

@OptIn(ExperimentalTime::class)
private fun formatTime(epochMillis: Long): String {
    val time = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${time.date} ${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}
