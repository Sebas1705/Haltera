package es.sebas1705.haltera

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import es.sebas1705.haltera.data.repository.health.HealthRepository
import es.sebas1705.haltera.share.AndroidRoutineShareBridge
import es.sebas1705.haltera.share.CurrentActivity
import es.sebas1705.haltera.share.IncomingRoutineChannel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val incoming: IncomingRoutineChannel by inject()
    private val health: HealthRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CurrentActivity.activity = this
        enableEdgeToEdge()
        setContent { App(modifier = Modifier.fillMaxSize()) }
        if (savedInstanceState == null) handleRoutineIntent(intent)
        // Workouts and weights logged while the app is open are written to Health Connect a few seconds later;
        // nothing runs once the app is in the background.
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { health.watchLocalChanges() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleRoutineIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        CurrentActivity.activity = this
        // Health Connect is read only in the foreground: on every resume (throttled, and only once connected).
        lifecycleScope.launch {
            health.checkStatus()
            health.refresh()
        }
    }

    override fun onDestroy() {
        if (CurrentActivity.activity === this) CurrentActivity.activity = null
        super.onDestroy()
    }

    /** A `.haltera` file opened (ACTION_VIEW) or shared (ACTION_SEND) with the app goes to the import preview. */
    private fun handleRoutineIntent(intent: Intent?) {
        val uri: Uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.streamExtra()
            else -> null
        } ?: return
        lifecycleScope.launch {
            AndroidRoutineShareBridge.readText(this@MainActivity, uri)?.let(incoming::offer)
        }
    }

    private fun Intent.streamExtra(): Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(Intent.EXTRA_STREAM)
    }
}
