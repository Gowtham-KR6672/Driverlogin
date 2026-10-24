package com.example.drivertracking.ui.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import com.example.drivertracking.ui.admin.AdminHomeScreen
import com.example.drivertracking.ui.admin.AdminUserScreen
import com.example.drivertracking.ui.admin.AdminWorkScreen
import com.example.drivertracking.ui.admin.AdminViewModel
import com.example.drivertracking.ui.history.HistoryScreen
import com.example.drivertracking.ui.home.HomeScreen
import com.example.drivertracking.ui.login.LoginScreen
import com.example.drivertracking.ui.permission.PermissionScreen
import com.example.drivertracking.ui.profile.ProfileScreen
import com.example.drivertracking.ui.work.WorkScreen
import com.example.drivertracking.util.ServerStatus
import com.example.drivertracking.util.SessionManager

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Filled.Home)
    object Work : Screen("work", "Work", Icons.Filled.Navigation)
    object History : Screen("history", "History", Icons.Filled.DateRange)
    object Profile : Screen("profile", "Profile", Icons.Filled.Person)
}

sealed class AdminScreen(val route: String, val title: String, val icon: ImageVector) {
    object Home : AdminScreen("admin_home", "Home", Icons.Filled.Home)
    object Users : AdminScreen("admin_users", "Users", Icons.Filled.People)
    object Entries : AdminScreen("admin_entries", "Entries", Icons.Filled.List)
    object Profile : AdminScreen("admin_profile", "Profile", Icons.Filled.Person)
}

@Composable
fun AppNavigation(sessionManager: SessionManager, serverStatus: ServerStatus) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val serverWakingUp by serverStatus.wakingUp.collectAsState()

    LaunchedEffect(Unit) {
        sessionManager.sessionExpired.collect {
            Toast.makeText(context, "Your session expired. Please log in again.", Toast.LENGTH_LONG).show()
            navController.navigate("login") {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val isLoggedIn = sessionManager.getAuthToken() != null
    val isAdmin = sessionManager.getUserRole() == "admin"
    
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    val isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    val allPermissionsGranted = hasLocationPermission && hasNotificationPermission && isIgnoringBatteryOptimizations
    
    val startDestination = if (isLoggedIn) {
        if (isAdmin) {
            AdminScreen.Home.route
        } else {
            if (allPermissionsGranted) Screen.Home.route else "permission_screen"
        }
    } else {
        "login"
    }

    val userItems = listOf(Screen.Home, Screen.Work, Screen.History, Screen.Profile)
    val adminItems = listOf(AdminScreen.Home, AdminScreen.Users, AdminScreen.Entries, AdminScreen.Profile)

    data class NavItem(val route: String, val title: String, val icon: ImageVector)
    val userNavItems = userItems.map { NavItem(it.route, it.title, it.icon) }
    val adminNavItems = adminItems.map { NavItem(it.route, it.title, it.icon) }

    Scaffold(
        topBar = {
            if (serverWakingUp) ServerWakingUpBanner()
        },
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val currentRoute = currentDestination?.route

            val isAdminRoute = currentRoute in adminItems.map { it.route }
            val isUserRoute = currentRoute in userItems.map { it.route }

            if (isAdminRoute || isUserRoute) {
                val items = if (isAdminRoute) adminNavItems else userNavItems
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 0.dp)
                        .shadow(16.dp, RoundedCornerShape(32.dp), spotColor = Color(0x33000000), ambientColor = Color(0x33000000))
                        .background(Color.White, RoundedCornerShape(32.dp))
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items.forEach { screen ->
                            val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                            val contentColor = Color(0xFF4A148C) // Deep Purple for icon/text
                            val unselectedTextColor = Color(0xFF4A4A68)

                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(Color(0xFFF3E8FF), Color(0xFFEADBFF))
                                            )
                                        )
                                        .clickable(
                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                            indication = null,
                                            onClick = {
                                                navController.navigate(screen.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        )
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(screen.icon, contentDescription = screen.title, tint = contentColor)
                                        Spacer(Modifier.height(4.dp))
                                        Text(screen.title, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable(
                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                            indication = null,
                                            onClick = {
                                                navController.navigate(screen.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        ),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                Brush.radialGradient(
                                                    colors = listOf(Color(0xFFF3E8FF), Color.Transparent),
                                                    radius = 50f
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(screen.icon, contentDescription = screen.title, tint = contentColor)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(screen.title, color = unselectedTextColor, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = startDestination, Modifier.padding(innerPadding)) {
            composable("login") {
                LoginScreen(onLoginSuccess = {
                    val userIsAdmin = sessionManager.getUserRole() == "admin"
                    val nextScreen = if (userIsAdmin) AdminScreen.Home.route else "permission_screen"
                    navController.navigate(nextScreen) {
                        popUpTo("login") { inclusive = true }
                    }
                })
            }
            composable("permission_screen") { PermissionScreen(navController) }
            
            // Admin Routes
            composable(AdminScreen.Home.route) { 
                val viewModel: AdminViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                AdminHomeScreen(navController, viewModel) 
            }
            composable(AdminScreen.Users.route) { 
                val viewModel: AdminViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                AdminUserScreen(navController, viewModel) 
            }
            composable(AdminScreen.Entries.route) { 
                val viewModel: AdminViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                AdminWorkScreen(navController, viewModel) 
            }
            composable(AdminScreen.Profile.route) { ProfileScreen(navController, sessionManager) }
            
            // Driver Routes
            composable(Screen.Home.route) { HomeScreen(navController) }
            composable(Screen.Work.route) { WorkScreen(navController) }
            composable(Screen.History.route) { HistoryScreen(navController) }
            composable(Screen.Profile.route) { ProfileScreen(navController, sessionManager) }
        }
    }
}

// Shown while a request waits on the server, e.g. while the sleeping Render instance starts.
@Composable
private fun ServerWakingUpBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF4E5))
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = Color(0xFFB45309)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "Connecting to the server… This can take up to a minute if it was asleep.",
            color = Color(0xFF7C2D12),
            fontSize = 13.sp
        )
    }
}
