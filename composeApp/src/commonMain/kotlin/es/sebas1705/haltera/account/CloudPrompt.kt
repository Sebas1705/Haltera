package es.sebas1705.haltera.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.haltera.core.common.mvi.MVIBaseIntent
import es.sebas1705.haltera.core.common.mvi.MVIBaseState
import es.sebas1705.haltera.core.common.mvi.MVIBaseViewModel
import es.sebas1705.haltera.core.datastore.CloudBackupState
import es.sebas1705.haltera.core.datastore.CloudBackupStore
import es.sebas1705.haltera.data.sync.AccountService
import es.sebas1705.haltera.data.sync.AccountSession
import es.sebas1705.haltera.data.sync.SyncEngine
import es.sebas1705.haltera.resources.Res
import es.sebas1705.haltera.resources.cloud_card_no
import es.sebas1705.haltera.resources.cloud_card_text
import es.sebas1705.haltera.resources.cloud_card_title
import es.sebas1705.haltera.resources.cloud_card_yes
import es.sebas1705.haltera.resources.cloud_notice_ok
import es.sebas1705.haltera.resources.cloud_notice_settings
import es.sebas1705.haltera.resources.cloud_notice_text
import es.sebas1705.haltera.resources.cloud_notice_title
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Which card the shell shows on Today, if any. */
enum class CloudCard { NONE, PROMPT, NOTICE }

data class CloudPromptState(val card: CloudCard = CloudCard.NONE) : MVIBaseState

sealed interface CloudPromptIntent : MVIBaseIntent {
    /** "Yes": opt in. */
    data object Accept : CloudPromptIntent

    /** "Not now": stays off and is not asked again (the switch in Settings → Account stays available). */
    data object Decline : CloudPromptIntent

    data object DismissNotice : CloudPromptIntent
}

/**
 * The first-launch question about the optional cloud copy, and the one-time notice for installs that were already
 * syncing. Never shown when Firebase is not configured in this build.
 */
class CloudPromptViewModel(
    private val account: AccountService,
    private val engine: SyncEngine,
    private val cloud: CloudBackupStore,
) : MVIBaseViewModel<CloudPromptState, CloudPromptIntent>(
    CloudPromptState(cardFor(account.session.value, cloud.state.value))
) {

    init {
        execute {
            combine(account.session, cloud.state, ::cardFor)
                .collect { card -> updateState { it.copy(card = card) } }
        }
    }

    override fun intentHandler(intent: CloudPromptIntent): Job = when (intent) {
        CloudPromptIntent.Accept -> execute {
            cloud.setEnabled(true)
            engine.markAllDirty() // What was logged before opting in is uploaded too.
        }
        CloudPromptIntent.Decline -> execute { cloud.dismissPrompt() }
        CloudPromptIntent.DismissNotice -> execute { cloud.dismissNotice() }
    }

    private companion object {
        fun cardFor(session: AccountSession, state: CloudBackupState): CloudCard = when {
            session == AccountSession.NotConfigured -> CloudCard.NONE
            state.noticePending -> CloudCard.NOTICE
            // A signed-in install is migrated to "on" at startup before it is ever asked.
            !state.promptHandled && session !is AccountSession.SignedIn -> CloudCard.PROMPT
            else -> CloudCard.NONE
        }
    }
}

/** The dismissible card at the top of Today. [onOpenSettings] opens Settings (where the switch lives). */
@Composable
fun CloudBackupCard(onOpenSettings: () -> Unit, viewModel: CloudPromptViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (state.card) {
        CloudCard.NONE -> Unit
        CloudCard.PROMPT -> CardBody(
            title = stringResource(Res.string.cloud_card_title),
            text = stringResource(Res.string.cloud_card_text),
        ) {
            TextButton(onClick = { viewModel.onEvent(CloudPromptIntent.Decline) }) {
                Text(stringResource(Res.string.cloud_card_no))
            }
            Button(onClick = { viewModel.onEvent(CloudPromptIntent.Accept) }) {
                Text(stringResource(Res.string.cloud_card_yes))
            }
        }
        CloudCard.NOTICE -> CardBody(
            title = stringResource(Res.string.cloud_notice_title),
            text = stringResource(Res.string.cloud_notice_text),
        ) {
            OutlinedButton(onClick = {
                viewModel.onEvent(CloudPromptIntent.DismissNotice)
                onOpenSettings()
            }) { Text(stringResource(Res.string.cloud_notice_settings)) }
            Button(onClick = { viewModel.onEvent(CloudPromptIntent.DismissNotice) }) {
                Text(stringResource(Res.string.cloud_notice_ok))
            }
        }
    }
}

@Composable
private fun CardBody(title: String, text: String, actions: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) { actions() }
        }
    }
}
