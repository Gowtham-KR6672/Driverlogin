package com.example.drivertracking.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.drivertracking.data.remote.AdminUser
import androidx.compose.material.icons.filled.CalendarToday

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUserScreen(navController: NavController, viewModel: AdminViewModel) {
    val users by viewModel.usersState.collectAsState()
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var newUsername by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.fetchUsers()
    }

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("Manage Users", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) 
        },
        floatingActionButton = {
            if (viewModel.canEdit) {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Text("+")
                }
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (users == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!viewModel.canEdit) {
                        item {
                            Text(
                                "View-only access: you can see users but not change them.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    items(users!!) { user ->
                        AdminUserCard(
                            user = user,
                            canEdit = viewModel.canEdit,
                            onDelete = {
                                viewModel.deleteUser(user.id)
                            },
                            onReset = { newPass ->
                                viewModel.resetPassword(user.id, newPass)
                            }
                        )
                    }
                }
            }
        }

        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("Create User") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = newUsername,
                            onValueChange = { newUsername = it },
                            label = { Text("Username") }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text("Password") }
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.createUser(newUsername, newPassword)
                        showCreateDialog = false
                        newUsername = ""
                        newPassword = ""
                    }) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun AdminUserCard(user: AdminUser, canEdit: Boolean, onDelete: () -> Unit, onReset: (String) -> Unit) {
    var showResetDialog by remember { mutableStateOf(false) }
    var resetPasswordText by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Avatar Placeholder
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = user.username.take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = user.username, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Joined: ${user.createdAt.substringBefore("T")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (user.role == "admin") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    val accessLabel = when (user.accessLevel) {
                        "view" -> " · VIEW ONLY"
                        "edit" -> " · EDIT"
                        "full" -> " · FULL"
                        else -> ""
                    }
                    Text(text = user.role.uppercase() + (if (user.role == "admin") accessLabel else ""), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = if (user.role == "admin") MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }

            if (user.role == "user" && canEdit) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = { showResetDialog = true }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reset")
                        Spacer(Modifier.width(4.dp))
                        Text("Reset")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onDelete, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        Spacer(Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Password for ${user.username}") },
            text = {
                OutlinedTextField(
                    value = resetPasswordText,
                    onValueChange = { resetPasswordText = it },
                    label = { Text("New Password") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    onReset(resetPasswordText)
                    showResetDialog = false
                    resetPasswordText = ""
                }) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }
}
