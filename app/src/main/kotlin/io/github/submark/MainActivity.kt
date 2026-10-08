package io.github.submark

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.submark.core.ui.navigation.DEEP_LINK_SCHEME
import io.github.submark.ui.LaunchRequest
import io.github.submark.ui.SubMarkApp

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var launchRequest by mutableStateOf<LaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) launchRequest = intent.toLaunchRequest()
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            SubMarkApp(
                state = state,
                launchRequest = launchRequest,
                onLaunchRequestHandled = { launchRequest = null },
                onUnlocked = viewModel::onUnlocked,
                onDismissAlerts = viewModel::dismissAlerts,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.toLaunchRequest()?.let { launchRequest = it }
    }

    private fun Intent.toLaunchRequest(): LaunchRequest? {
        getStringExtra(EXTRA_SUBSCRIPTION_ID)?.let { return LaunchRequest.OpenSubscription(it) }
        if (action == Intent.ACTION_VIEW && data?.scheme == DEEP_LINK_SCHEME) return LaunchRequest.DeepLink(this)
        return null
    }

    companion object {
        /** Set by notifications and widgets to open a subscription's detail page. */
        const val EXTRA_SUBSCRIPTION_ID = "submark.subscriptionId"
    }
}
