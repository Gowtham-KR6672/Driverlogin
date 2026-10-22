package com.example.drivertracking.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.drivertracking.data.remote.AdminUser
import com.example.drivertracking.data.remote.HistoryEntry
import com.example.drivertracking.data.remote.avgSpeedText
import com.example.drivertracking.data.remote.callsText
import com.example.drivertracking.data.remote.topSpeedText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminWorkScreen(navController: NavController, viewModel: AdminViewModel) {
    val users by viewModel.usersState.collectAsState()
    val entries by viewModel.workState.collectAsState()
    
    var selectedUser by remember { mutableStateOf<AdminUser?>(null) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (users == null) {
            viewModel.fetchUsers()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Work Entries") })

        Column(modifier = Modifier.padding(16.dp)) {
            val userList = users?.filter { it.role == "user" } ?: emptyList()

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedUser?.username ?: "Select Driver",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    userList.forEach { user ->
                        DropdownMenuItem(
                            text = { Text(user.username) },
                            onClick = {
                                selectedUser = user
                                expanded = false
                                viewModel.fetchWorkEntries(user.id)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedUser != null && entries == null) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (entries != null) {
                if (entries!!.isEmpty()) {
                    Text("No entries found for ${selectedUser?.username}.", modifier = Modifier.padding(16.dp))
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(entries!!) { entry ->
                            AdminEntryCard(entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminEntryCard(entry: HistoryEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Work: ${entry.workGivenPerson}", fontWeight = FontWeight.Bold)
                Text(text = entry.vehicleType, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Started: ${entry.nextStartTime ?: "Unknown"}")
            Text(text = "Ended: ${entry.endTime ?: "In Progress"}")
            Text(text = "Duration: ${entry.duration}")
            Text(text = "Distance: ${entry.distanceDisplay}")
            Text(text = "Top speed: ${entry.topSpeedText()} · Avg speed: ${entry.avgSpeedText()}")
            Text(text = "Calls: ${entry.callsText()}")
            if (entry.tripId != null) {
                Text(
                    text = "Part of trip #${entry.tripId}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (!entry.remarks.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Remarks: ${entry.remarks}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
