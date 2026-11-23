package com.idiots3.riskscanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.idiots3.riskscanner.navigation.NavGraph
import com.idiots3.riskscanner.receiver.InstallAlertNotifier
import com.idiots3.riskscanner.receiver.InstallMonitorReceiver
import com.idiots3.riskscanner.ui.theme.RiskAuditorTheme
import com.idiots3.riskscanner.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory(applicationContext) }

    private var deepLinkPackage by mutableStateOf<String?>(null)
    private var deepLinkTab by mutableStateOf<String?>(null)
    private var incomingApkUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeIntent(intent)

        setContent {
            RiskAuditorTheme {
                val context = LocalContext.current

                val notificationPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { /* alerts are optional; scanning works without them */ }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                // Live install/update monitoring while the app is open. Background changes while
                // closed are caught by WorkManager's BackgroundSecurityWorker and on resume.
                DisposableEffect(Unit) {
                    val receiver = InstallMonitorReceiver.register(context) { packageName ->
                        viewModel.onPackageChanged(packageName)
                    }
                    onDispose {
                        runCatching { context.unregisterReceiver(receiver) }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavGraph(
                        navController = navController,
                        viewModel = viewModel,
                        pendingDeepLinkPackage = deepLinkPackage,
                        pendingDeepLinkTab = deepLinkTab,
                        pendingApkUri = incomingApkUri,
                        onDeepLinkConsumed = {
                            deepLinkPackage = null
                            deepLinkTab = null
                            incomingApkUri = null
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkForExternalChanges()
    }

    private fun consumeIntent(intent: Intent?) {
        if (intent == null) return
        val openPkg = intent.getStringExtra(InstallAlertNotifier.EXTRA_OPEN_PACKAGE)
        val openTab = intent.getStringExtra(InstallAlertNotifier.EXTRA_OPEN_TAB)
        if (!openPkg.isNullOrBlank()) {
            deepLinkPackage = openPkg
            intent.removeExtra(InstallAlertNotifier.EXTRA_OPEN_PACKAGE)
        } else if (!openTab.isNullOrBlank()) {
            deepLinkTab = openTab
            intent.removeExtra(InstallAlertNotifier.EXTRA_OPEN_TAB)
        }

        val apkUri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            else -> null
        }
        if (apkUri != null) {
            incomingApkUri = apkUri
            intent.action = null
            intent.data = null
        }
    }
}
