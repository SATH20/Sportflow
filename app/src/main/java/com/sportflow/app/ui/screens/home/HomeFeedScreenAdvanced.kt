package com.sportflow.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.sportflow.app.data.model.*
import com.sportflow.app.ui.components.*
import com.sportflow.app.ui.theme.*
import com.sportflow.app.ui.viewmodel.HomeViewModel
import com.sportflow.app.ui.viewmodel.AdvancedRegistrationViewModel
import com.sportflow.app.ui.viewmodel.AuthViewModel

/**
 * ADVANCED HOME FEED SCREEN
 * 
 * Integrates the 3-step registration bottom sheet with:
 * - Eligibility validation
 * - Squad details form
 * - Real-time capacity tracking
 * - Firestore transactions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFeedScreenAdvanced(
    navController: NavHostController,
    isAdmin: Boolean = false,
    viewModel: HomeViewModel = hiltViewModel(),
    regViewModel: AdvancedRegistrationViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val uiState  by viewModel.uiState.collectAsStateWithLifecycle()
    val regState by regViewModel.uiState.collectAsStateWithLifecycle()
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    
    val currentUser = authState.currentUser

    val snackbarHostState = remember { SnackbarHostState() }
    
    // Show snackbar messages
    LaunchedEffect(regState.successMessage, regState.error) {
        val msg = regState.successMessage ?: regState.error
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            regViewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = ScreenBg
    ) { innerPadding ->

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Header (same as before)
                item {
                    HomeFeedHeader(
                        isAdmin = isAdmin,
                        onAdminClick = { navController.navigate("admin") }
                    )
                }

                // Live Matches Section
                if (uiState.liveMatches.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Live Now 🔴",
                            actionText = "View All",
                            onAction = { navController.navigate("live") }
                        )
                    }
                    // ... live matches carousel
                }

                // Upcoming Events with Advanced Registration
                if (uiState.upcomingMatches.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Upcoming Events",
                            actionText = "See All"
                        )
                    }

                    items(uiState.upcomingMatches) { match ->
                        val isRegistered = match.id in regState.registeredMatchIds
                        val isLoading = match.id in regState.loadingMatchIds
                        val currentSquadCount = regState.squadCountMap[match.id] ?: match.currentSquadCount
                        val maxSquads = regState.maxSquadsMap[match.id] ?: match.maxSquadSize

                        // Subscribe to real-time updates
                        LaunchedEffect(match.id) {
                            regViewModel.checkRegistration(match.id)
                            regViewModel.subscribeToSquadUpdates(match.id)
                        }

                        AdvancedEventCard(
                            match = match,
                            currentSquadCount = currentSquadCount,
                            maxSquads = maxSquads,
                            isRegistered = isRegistered,
                            isRegistering = isLoading,
                            onRegisterClick = {
                                if (isRegistered) {
                                    regViewModel.cancelRegistration(match.id)
                                } else {
                                    regViewModel.showRegistrationSheet(match)
                                }
                            },
                            onClick = { }
                        )
                    }
                }
            }

            // Advanced Registration Bottom Sheet
            if (regState.showBottomSheet && regState.selectedMatch != null) {
                val match = regState.selectedMatch!!
                val currentSquadCount = regState.squadCountMap[match.id] ?: match.currentSquadCount
                val maxSquads = regState.maxSquadsMap[match.id] ?: match.maxSquadSize

                RegistrationBottomSheet(
                    match = match,
                    currentUser = currentUser,
                    currentSquadCount = currentSquadCount,
                    maxSquads = maxSquads,
                    onDismiss = { regViewModel.hideRegistrationSheet() },
                    onRegister = { squadName, captainName, captainPhone, squadSize ->
                        regViewModel.registerWithSquad(
                            match = match,
                            squadName = squadName,
                            captainName = captainName,
                            captainPhone = captainPhone,
                            squadSize = squadSize
                        )
                    }
                )
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// ADVANCED EVENT CARD WITH CAPACITY INDICATOR
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun AdvancedEventCard(
    match: Match,
    currentSquadCount: Int,
    maxSquads: Int,
    isRegistered: Boolean,
    isRegistering: Boolean,
    onRegisterClick: () -> Unit,
    onClick: () -> Unit
) {
    val progress = if (maxSquads > 0) currentSquadCount.toFloat() / maxSquads.toFloat() else 0f
    val isFull = maxSquads > 0 && currentSquadCount >= maxSquads

    SportCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SportTypeChip(sportType = match.sportType)
                if (match.teamADepartment.isNotBlank()) {
                    StatusChip(
                        text = "${match.teamADepartment} vs ${match.teamBDepartment}",
                        color = GnitsOrange
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Match title
            Text(
                text = "${match.teamA} vs ${match.teamB}",
                style = SportFlowTheme.typography.headlineLarge,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Venue + tournament
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = match.venue.ifEmpty { "GNITS Campus" },
                        style = SportFlowTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
                if (match.tournamentName.isNotBlank()) {
                    Text("•", color = TextTertiary, fontSize = 10.sp)
                    Text(
                        text = match.tournamentName,
                        style = SportFlowTheme.typography.bodySmall,
                        color = InfoBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Eligibility
            if (match.eligibilityText.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.VerifiedUser,
                        contentDescription = "Eligibility",
                        tint = SuccessGreen,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = match.eligibilityText,
                        style = SportFlowTheme.typography.bodySmall,
                        color = SuccessGreen,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Squad Capacity Progress Bar
            if (maxSquads > 0) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Groups,
                                contentDescription = null,
                                tint = if (isFull) ErrorRed else GnitsOrange,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Squad Capacity",
                                style = SportFlowTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        Text(
                            text = "$currentSquadCount / $maxSquads",
                            style = SportFlowTheme.typography.labelMedium,
                            color = if (isFull) ErrorRed else GnitsOrange,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (isFull) ErrorRed else GnitsOrange,
                        trackColor = OffWhite,
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // Bottom row: status + register button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip(
                    text = match.status.name,
                    color = when (match.status) {
                        MatchStatus.SCHEDULED -> InfoBlue
                        MatchStatus.LIVE -> LiveRed
                        MatchStatus.HALFTIME -> WarningAmber
                        MatchStatus.COMPLETED -> SuccessGreen
                        MatchStatus.CANCELLED -> ErrorRed
                    }
                )

                // Register button
                if (match.status == MatchStatus.SCHEDULED) {
                    when {
                        isRegistering -> CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = GnitsOrange,
                            strokeWidth = 2.dp
                        )
                        isRegistered -> OutlinedButton(
                            onClick = onRegisterClick,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Registered",
                                style = SportFlowTheme.typography.labelMedium,
                                color = SuccessGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        else -> Button(
                            onClick = onRegisterClick,
                            enabled = !isFull,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GnitsOrange,
                                disabledContainerColor = CardBorder
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.Outlined.HowToReg,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isFull) "Full" else "Register",
                                style = SportFlowTheme.typography.labelMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeFeedHeader(
    isAdmin: Boolean,
    onAdminClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PureWhite)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Welcome to GNITS! 🏆",
                    style = SportFlowTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Text(
                    text = "GNITS Sports",
                    style = SportFlowTheme.typography.displayLarge,
                    color = TextPrimary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isAdmin) {
                    IconButton(
                        onClick = onAdminClick,
                        modifier = Modifier
                            .size(44.dp)
                            .background(GnitsOrangeLight, CircleShape)
                    ) {
                        Icon(
                            Icons.Filled.AdminPanelSettings,
                            contentDescription = "Admin Portal",
                            tint = GnitsOrange
                        )
                    }
                }
                IconButton(
                    onClick = { },
                    modifier = Modifier
                        .size(44.dp)
                        .background(OffWhite, CircleShape)
                ) {
                    Icon(
                        Icons.Outlined.Notifications,
                        contentDescription = "Notifications",
                        tint = TextSecondary
                    )
                }
            }
        }
    }
}
