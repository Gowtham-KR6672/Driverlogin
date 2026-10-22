package com.example.drivertracking.ui.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.drivertracking.ui.navigation.Screen

@Composable
fun PermissionScreen(navController: NavController) {
    val context = LocalContext.current
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    var hasLocationPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }
    
    var hasBackgroundLocation by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    // Optional: lets the app count calls during work (by type only). Not required to continue.
    var hasCallLogPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED)
    }

    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }

    val allPermissionsGranted = hasLocationPermission && hasNotificationPermission && hasBackgroundLocation && isIgnoringBatteryOptimizations

    // Automatically skip if permissions are already granted
    LaunchedEffect(allPermissionsGranted) {
        if (allPermissionsGranted) {
            navController.navigate(Screen.Home.route) {
                popUpTo("permission_screen") { inclusive = true }
            }
        }
    }

    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: hasLocationPermission
        hasCallLogPermission = permissions[Manifest.permission.READ_CALL_LOG] ?: hasCallLogPermission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: hasNotificationPermission
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            hasBackgroundLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = "Warning",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Permissions Required",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "To track your work and routes accurately in the background, we need a few permissions.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        PermissionItem(
            title = "Location Tracking",
            isGranted = hasLocationPermission
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        PermissionItem(
            title = "Notifications",
            isGranted = hasNotificationPermission
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        PermissionItem(
            title = "Background Location (Allow all the time)",
            isGranted = hasBackgroundLocation
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        PermissionItem(
            title = "Allow Background Usage (Battery)",
            isGranted = isIgnoringBatteryOptimizations
        )

        Spacer(modifier = Modifier.height(8.dp))

        PermissionItem(
            title = "Call log (counts work calls only, no numbers)",
            isGranted = hasCallLogPermission
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = {
                val permissionsToRequest = mutableListOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.READ_CALL_LOG
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                
                multiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
                
                if (!isIgnoringBatteryOptimizations) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    batteryOptimizationLauncher.launch(intent)
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Grant Permissions")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        }) {
            Text("Open App Settings (for Background Location)")
        }
    }
}

@Composable
fun PermissionItem(title: String, isGranted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isGranted) Icons.Filled.Check else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
    }
}
