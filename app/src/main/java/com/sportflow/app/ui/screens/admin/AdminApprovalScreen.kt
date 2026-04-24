package com.sportflow.app.ui.screens.admin

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportflow.app.data.model.*
import com.sportflow.app.ui.components.AdminDecisionPanel
import com.sportflow.app.ui.theme.*
import com.sportflow.app.ui.viewmodel.AdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminApprovalScreen(
    viewModel: AdminViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    var selectedFilter by remember { mutableStateOf("ALL") }
    var showDenyDialog by remember { mutableStateOf(false) }
    var selectedRegistration by remember { mutableStateOf<Registration?>(null) }
    var denyReason by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Registration Approvals")
                        if (uiState.newRegistrationCount > 0) {
                            Spacer(Modifier.width(8.dp))
                            Badge(
                                containerColor = Color(0xFFF09819),
                                contentColor = Color.White
                            ) {
                                Text("${uiState.newRegistrationCount}")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SoftWhite,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = Color(0xFFF5F5F5)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("All") },
                    leadingIcon = if (selectedFilter == "ALL") {
                        { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }
                    } else null
                )
                FilterChip(
                    selected = selectedFilter == "PENDING",
                    onClick = { selectedFilter = "PENDING" },
                    label = { Text("Pending") },
                    leadingIcon = if (selectedFilter == "PENDING") {
                        { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }
                    } else null
                )
                FilterChip(
                    selected = selectedFilter == "CONFIRMED",
                    onClick = { selectedFilter = "CONFIRMED" },
                    label = { Text("Confirmed") },
                    leadingIcon = if (selectedFilter == "CONFIRMED") {
                        { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }
                    } else null
                )
            }

            // Registrations list
            val filteredRegistrations = when (selectedFilter) {
                "PENDING" -> uiState.registrations.filter { it.status == RegistrationStatus.PENDING }
                "CONFIRMED" -> uiState.registrations.filter { it.status == RegistrationStatus.CONFIRMED }
                else -> uiState.registrations
            }
            val fixtureReadyMatchId = uiState.registrations
                .filter { it.status == RegistrationStatus.CONFIRMED }
                .groupBy { it.tournamentId.ifBlank { it.matchId } }
                .entries
                .firstOrNull { it.value.size >= 2 }
                ?.key

            if (fixtureReadyMatchId != null) {
                Button(
                    onClick = {
                        if (uiState.tournaments.any { it.id == fixtureReadyMatchId }) {
                            viewModel.generateFixturesFromApprovedTournamentRegistrations(fixtureReadyMatchId)
                        } else {
                            viewModel.generateFixturesFromApprovedRegistrations(fixtureReadyMatchId)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF09819))
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Generate Fixtures from Approved")
                }
                Spacer(Modifier.height(8.dp))
            }

            if (filteredRegistrations.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.Gray
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "No registrations found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredRegistrations, key = { it.id }) { registration ->
                        RegistrationCard(
                            registration = registration,
                            onAccept = {
                                viewModel.acceptRegistration(registration.id)
                                viewModel.markRegistrationAsSeen(registration.id)
                            },
                            onDeny = {
                                selectedRegistration = registration
                                showDenyDialog = true
                            },
                            onMarkSeen = {
                                viewModel.markRegistrationAsSeen(registration.id)
                            }
                        )
                    }
                }
            }
        }
    }

    // Deny dialog
    if (showDenyDialog && selectedRegistration != null) {
        AlertDialog(
            onDismissRequest = { 
                showDenyDialog = false
                denyReason = ""
            },
            title = { Text("Deny Registration") },
            text = {
                Column {
                    Text("Are you sure you want to deny this registration?")
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = denyReason,
                        onValueChange = { denyReason = it },
                        label = { Text("Reason (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedRegistration?.let {
                            viewModel.denyRegistration(it.id, denyReason.ifBlank { "No reason provided" })
                        }
                        showDenyDialog = false
                        denyReason = ""
                        selectedRegistration = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red
                    )
                ) {
                    Text("Deny")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDenyDialog = false
                    denyReason = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Success/Error messages
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            // Show snackbar
            viewModel.clearMessage()
        }
    }
}

@Composable
fun RegistrationCard(
    registration: Registration,
    onAccept: () -> Unit,
    onDeny: () -> Unit,
    onMarkSeen: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!registration.seen) Color(0xFFFFF3E0) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status indicator
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                when (registration.status) {
                                    RegistrationStatus.PENDING -> Color(0xFFFFA726)
                                    RegistrationStatus.CONFIRMED -> Color(0xFF66BB6A)
                                    RegistrationStatus.CANCELLED -> Color.Gray
                                }
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = registration.userName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = registration.rollNumber,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }

                if (!registration.seen) {
                    Surface(
                        color = Color(0xFFF09819),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "NEW",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Quick info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                InfoChip(
                    icon = Icons.Filled.School,
                    text = registration.department
                )
                InfoChip(
                    icon = Icons.Filled.SportsSoccer,
                    text = registration.sportType
                )
            }

            Spacer(Modifier.height(8.dp))

            // Match name
            Text(
                text = registration.matchName,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )

            // Expandable details
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = Color.LightGray)
                    Spacer(Modifier.height(12.dp))

                    DetailRow("Email", registration.email)
                    DetailRow("Year", GnitsYear.fromCode(registration.yearOfStudy)?.displayName ?: registration.yearOfStudy)
                    DetailRow("Entry Type", registration.registrationKind.name.replace('_', ' '))
                    DetailRow("Fixture Unit", registration.fixtureUnitName.ifBlank { registration.teamName.ifBlank { registration.userName } })
                    DetailRow("Sport Role", registration.sportRole)
                    if (registration.teamName.isNotBlank() || registration.squadName.isNotBlank()) {
                        DetailRow("Team Name", registration.teamName.ifBlank { registration.squadName })
                        DetailRow("Captain", registration.captainName)
                        DetailRow("Captain Phone", registration.captainPhone)
                    }
                    if (registration.partnerName.isNotBlank()) {
                        DetailRow("Partner", registration.partnerName)
                        DetailRow("Partner Roll", registration.partnerRollNumber)
                        DetailRow("Partner Role", registration.partnerRole)
                    }
                    if (registration.roster.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Squad List",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFF09819),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        registration.roster.forEachIndexed { index, player ->
                            DetailRow(
                                "Player ${index + 1}",
                                "${player.name} - ${player.rollNumber} - ${player.role}"
                            )
                        }
                    }
                    DetailRow("Registered At", registration.registeredAt?.toDate()?.toString() ?: "N/A")
                }
            }

            // Expand/Collapse button
            TextButton(
                onClick = {
                    expanded = !expanded
                    if (!registration.seen) onMarkSeen()
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(if (expanded) "Show Less" else "Show More")
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null
                )
            }

            // Action buttons (only for PENDING)
            if (registration.status == RegistrationStatus.PENDING) {
                Spacer(Modifier.height(8.dp))
                AdminDecisionPanel(
                    rollNumber = registration.rollNumber,
                    email = registration.email,
                    department = registration.department,
                    onAccept = onAccept,
                    onDeny = onDeny,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (registration.status == RegistrationStatus.CONFIRMED) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Registration Confirmed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(
        color = Color(0xFFF5F5F5),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color(0xFFF09819)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(2f)
        )
    }
}
