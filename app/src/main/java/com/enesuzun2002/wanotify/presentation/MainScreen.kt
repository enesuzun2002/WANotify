package com.enesuzun2002.wanotify.presentation

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.enesuzun2002.wanotify.core.utils.PermissionUtils
import com.enesuzun2002.wanotify.presentation.components.StatusCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WANotify") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Configuration",
                style = MaterialTheme.typography.headlineSmall
            )

            StatusCard(
                title = "1. Notification Access",
                description = "Required to read incoming WhatsApp messages and unpack conversation leaves.",
                isGranted = uiState.isNotificationAccessGranted,
                actionButtonText = "Grant Access",
                onActionClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            )

            StatusCard(
                title = "2. Battery Optimization",
                description = "Required to keep the notification bridge running reliably in the background without OS termination.",
                isGranted = uiState.isBatteryExemptionGranted,
                actionButtonText = "Disable Restrictions",
                onActionClick = {
                    val intent = PermissionUtils.createBatteryOptimizationIntent(context)
                    context.startActivity(intent)
                }
            )

            if (uiState.isSystemReady) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = "System is ready. Open your smartwatch companion app (e.g., Honor Health, Huawei Health, Zepp), disable notifications for WhatsApp, and enable notifications for WANotify.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}