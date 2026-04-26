@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sportflow.app.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.sportflow.app.data.model.*
import com.sportflow.app.ui.components.GlassmorphicCard
import com.sportflow.app.ui.components.PremiumButton
import com.sportflow.app.ui.theme.*
import com.sportflow.app.ui.viewmodel.MyMatchesViewModel
import com.sportflow.app.ui.viewmodel.RegistrationViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyMatchesScreenComplete(
    navController: NavHostController,
    currentUserRole: UserRole,
    myMatchesViewModel: MyMatchesViewModel = hiltViewModel(),
    registrationViewModel: RegistrationViewModel = hiltViewModel()
) {
    val regState by registrationViewModel.uiState.collectAsStateWithLifecycle()
    val myMatchesState by myMatchesViewModel.uiState.collectAsStateWithLifecycle()
    val myMatches = myMatchesState.myMatches
    
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var matchToCancel by remember { mutableStateOf<Match?>(null) }

    val tabs = listOf("Upcoming", "Live", "Completed")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Matches") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF09819),
                    titleContentColor = Color.White
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
            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = Color(0xFFF09819)
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            // Filtered matches
            val filteredMatches = when (selectedTab) {
                0 -> myMatches.filter { it.status == MatchStatus.SCHEDULED }
                1 -> myMatches.filter { it.status == MatchStatus.LIVE || it.status == MatchStatus.HALFTIME }
                2 -> myMatches.filter { it.status == MatchStatus.COMPLETED }
                else -> emptyList()
            }

            if (filteredMatches.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.EventBusy,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.Gray
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "No ${tabs[selectedTab].lowercase()} matches",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Register for matches from the Home screen",
                            style = MaterialTheme.typography.bodySmall,
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
                    items(filteredMatches) { match ->
                        MyMatchCard(
                            match = match,
                            onCancel = {
                                matchToCancel = match
                                showCancelDialog = true
                            },
                            onNavigateToLive = {
                                navController.navigate("live")
                            }
                        )
                    }
                }
            }
        }
    }

    // Cancel confirmation dialog
    if (showCancelDialog && matchToCancel != null) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel Registration?") },
            text = {
                Text("Are you sure you want to cancel your registration for ${matchToCancel!!.teamA} vs ${matchToCancel!!.teamB}? This will open up a spot for other students.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        registrationViewModel.cancelRegistration(
                            matchId = matchToCancel!!.id,
                            matchName = "${matchToCancel!!.teamA} vs ${matchToCancel!!.teamB}"
                        )
                        showCancelDialog = false
                        matchToCancel = null
                    }
                ) {
                    Text("Yes, Cancel", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("Keep Registration")
                }
            }
        )
    }

    // Show success/error messages
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(regState.successMessage) {
        regState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            registrationViewModel.clearMessages()
        }
    }
}

@Composable
fun MyMatchCard(
    match: Match,
    onCancel: () -> Unit,
    onNavigateToLive: () -> Unit
) {
    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        onClick = { if (match.status == MatchStatus.LIVE) onNavigateToLive() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Sport badge and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = GnitsOrangeLight,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = match.sportType,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = SportFlowTheme.typography.labelMedium,
                        color = GnitsOrange,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Status badge
                Surface(
                    color = when (match.status) {
                        MatchStatus.LIVE -> LiveRed
                        MatchStatus.SCHEDULED -> InfoBlue
                        MatchStatus.COMPLETED -> SuccessGreen
                        else -> Color.Gray
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = match.status.name,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = SportFlowTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Match info
            Text(
                text = "${match.teamA} vs ${match.teamB}",
                style = SportFlowTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(Modifier.height(8.dp))

            // Venue
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = TextTertiary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = match.venue,
                    style = SportFlowTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Spacer(Modifier.height(4.dp))

            // Time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = TextTertiary
                )
                Spacer(Modifier.width(6.dp))
                        Text(
                            text = formatMatchTime(match.scheduledTime),
                            style = SportFlowTheme.typography.bodySmall,
                            color = TextSecondary
                        )
            }

            // Tournament name
            if (match.tournamentName.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = GnitsOrange
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = match.tournamentName,
                        style = SportFlowTheme.typography.bodySmall,
                        color = GnitsOrange,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Cancel button (only for scheduled matches)
            if (match.status == MatchStatus.SCHEDULED) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = LiveRed
                    ),
                    border = BorderStroke(1.5.dp, LiveRed)
                ) {
                    Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cancel Registration", fontWeight = FontWeight.SemiBold)
                }
            }

            // View live button (for live matches)
            if (match.status == MatchStatus.LIVE || match.status == MatchStatus.HALFTIME) {
                Spacer(Modifier.height(12.dp))
                PremiumButton(
                    text = "Watch Live",
                    onClick = onNavigateToLive,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    icon = Icons.Filled.PlayCircle,
                    containerColor = SuccessGreen
                )
            }

            // Score display (for completed matches)
            if (match.status == MatchStatus.COMPLETED) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = ScreenBg,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                match.teamA,
                                style = SportFlowTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                "${match.scoreA}",
                                style = SportFlowTheme.typography.scoreDisplay.copy(fontSize = 28.sp),
                                fontWeight = FontWeight.Bold,
                                color = GnitsOrange
                            )
                        }
                        Text("-", style = SportFlowTheme.typography.scoreDisplay.copy(fontSize = 28.sp), color = TextTertiary)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                match.teamB,
                                style = SportFlowTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                "${match.scoreB}",
                                style = SportFlowTheme.typography.scoreDisplay.copy(fontSize = 28.sp),
                                fontWeight = FontWeight.Bold,
                                color = GnitsOrange
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatMatchTime(timestamp: com.google.firebase.Timestamp?): String {
    if (timestamp == null) return "TBD"
    val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    return formatter.format(timestamp.toDate())
}
