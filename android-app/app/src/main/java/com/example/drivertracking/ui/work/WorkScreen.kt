package com.example.drivertracking.ui.work

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.drivertracking.theme.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun LiveMapView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val mapView = remember {
        org.osmdroid.views.MapView(context).apply {
            org.osmdroid.config.Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE))
            setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(18.0)
            val locationOverlay = org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay(org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider(context), this)
            locationOverlay.enableMyLocation()
            locationOverlay.enableFollowLocation()
            overlays.add(locationOverlay)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> mapView.onResume()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }
    
    AndroidView(
        factory = { mapView },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkScreen(
    navController: NavController,
    viewModel: WorkViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    var person by remember { mutableStateOf("") }
    var vehicle by remember { mutableStateOf("car") }
    var remarks by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            // Permission granted
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    LaunchedEffect(state.hasActiveWork) {
        val intent = android.content.Intent(context, com.example.drivertracking.service.LocationTrackingService::class.java)
        if (state.hasActiveWork) {
            intent.action = com.example.drivertracking.service.LocationTrackingService.ACTION_START_TRACKING
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        } else {
            intent.action = com.example.drivertracking.service.LocationTrackingService.ACTION_STOP_TRACKING
            context.startService(intent)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBackground)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.size(48.dp)) // To keep title centered
            Text("Work Entry", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextDark)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BrandBlue.copy(alpha = 0.1f),
                modifier = Modifier.size(48.dp),
                onClick = { viewModel.fetchActiveWork() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = BrandBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Create a new work entry to track your journey",
            style = MaterialTheme.typography.bodyMedium,
            color = TextGray
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (state.isLoading) {
            CircularProgressIndicator(color = BrandBlue)
        } else if (state.error != null) {
            Text(text = state.error!!, color = ErrorRed)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { viewModel.fetchActiveWork() }) {
                Text("Retry")
            }
        } else if (state.hasActiveWork && state.activeEntry != null) {
            val entry = state.activeEntry!!
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = LightSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(IconBgLightBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = BrandBlue)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Active Work", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextDark)
                            Text("Work Given Person: ${entry.workGivenPerson}", style = MaterialTheme.typography.bodySmall, color = TextGray)
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text("Vehicle Type: ${entry.vehicleType.uppercase()}", style = MaterialTheme.typography.bodyMedium, color = TextDark)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Start Time: ${entry.nextStartTime ?: "N/A"}", style = MaterialTheme.typography.bodyMedium, color = TextDark)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Distance: ${entry.distanceDisplay}", style = MaterialTheme.typography.bodyMedium, color = TextDark)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Remarks: ${entry.remarks}", style = MaterialTheme.typography.bodyMedium, color = TextDark)
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    com.example.drivertracking.ui.components.SwipeButton(
                        text = "End Work",
                        loadingText = "Ending...",
                        containerColor = ErrorRed,
                        contentColor = Color.White,
                        enabled = !state.isLoading,
                        isLoading = state.isLoading,
                        onSwipeComplete = {
                            viewModel.endWork(null)
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                LiveMapView(modifier = Modifier.fillMaxSize())
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = LightSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(IconBgLightBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = BrandBlue)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Start New Work", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextDark)
                            Text("Fill in the details below to get started", style = MaterialTheme.typography.bodySmall, color = TextGray)
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    val workPeopleOptions = listOf("Vijay Anand P", "Shajahan S", "Prem Anand Y", "HR Department", "Manager", "IT Department", "Other")
                    var personExpanded by remember { mutableStateOf(false) }
                    var otherPerson by remember { mutableStateOf("") }
                    
                    Text("Work given person", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextDark)
                    Spacer(modifier = Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = personExpanded,
                        onExpandedChange = { personExpanded = !personExpanded }
                    ) {
                        OutlinedTextField(
                            value = person,
                            onValueChange = {},
                            readOnly = true,
                            placeholder = { Text("Select person", color = TextGray) },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = BrandBlue) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = personExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandBlue,
                                unfocusedBorderColor = Color.LightGray
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = personExpanded,
                            onDismissRequest = { personExpanded = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            workPeopleOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        person = option
                                        personExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    
                    if (person == "Other") {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = otherPerson,
                            onValueChange = { otherPerson = it },
                            placeholder = { Text("Enter person name", color = TextGray) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Vehicle Type", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextDark)
                    Spacer(modifier = Modifier.height(8.dp))
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = vehicle.replaceFirstChar { it.uppercase() },
                            onValueChange = {},
                            readOnly = true,
                            placeholder = { Text("Select vehicle type", color = TextGray) },
                            leadingIcon = { Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = BrandBlue) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandBlue,
                                unfocusedBorderColor = Color.LightGray
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            DropdownMenuItem(text = { Text("Car") }, onClick = { vehicle = "car"; expanded = false })
                            DropdownMenuItem(text = { Text("Bike") }, onClick = { vehicle = "bike"; expanded = false })
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Remarks", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextDark)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = remarks,
                        onValueChange = { if(it.length <= 250) remarks = it },
                        placeholder = { Text("Add any remarks or notes...", color = TextGray) },
                        leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, tint = BrandBlue) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandBlue,
                            unfocusedBorderColor = Color.LightGray
                        ),
                        supportingText = {
                            Text("${remarks.length}/250", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    com.example.drivertracking.ui.components.SwipeButton(
                        text = "Start Work",
                        loadingText = "Starting...",
                        containerColor = BrandBlue,
                        contentColor = Color.White,
                        enabled = !state.isLoading && (if (person == "Other") otherPerson else person).isNotBlank() && remarks.isNotBlank(),
                        isLoading = state.isLoading,
                        onSwipeComplete = {
                            val finalPerson = if (person == "Other") otherPerson else person
                            if (finalPerson.isNotBlank() && remarks.isNotBlank()) {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                                        .addOnSuccessListener { location: Location? ->
                                            viewModel.startWork(finalPerson, vehicle, remarks, location)
                                        }
                                } else {
                                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}
