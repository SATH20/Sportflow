package com.sportflow.app.ui.screens.home

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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.sportflow.app.data.model.*
import com.sportflow.app.ui.components.AdvancedRegistrationBottomSheet
import com.sportflow.app.ui.components.GlassmorphicCard
import com.sportflow.app.ui.components.PulseAnimation
import com.sportflow.app.ui.components.StateAwareButton
import com.sportflow.app.ui.components.ButtonState
import com.sportflow.app.ui.theme.*
import com.sportflow.app.ui.viewmodel.HomeViewModel
import com.sportflow.app.ui.viewmodel.RegistrationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFeedScreenComplete(
    navController: NavHostController,
    currentUser: SportUser?,
    isAdmin: Boolean,
    homeViewModel: HomeViewModel = hiltViewModel(),
    registrationViewModel: RegistrationViewModel = hiltViewModel()
) {
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val regState by registrationViewModel.uiState.collectAsStateWithLifecycle()
    
    var showRegistrationSheet by remember { mutableStateOf(false) }
    var selectedMatch by remember { mutableStateOf<Match?>(null) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var matchToCancel by remember { mutableStateOf<Match?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Show success/error messages
    LaunchedEffect(regState.successMessage) {
        regState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            registrationViewModel.clearMessages()
        }
    }

    LaunchedEffect(regState.error) {
        regState.error?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            registrationViewModel.clearMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("GNITS Sports") },
                actions = {
                    if (isAdmin) {
                        IconButton(onClick = { navController.navigate("admin") }) {
                            Icon(Icons.Filled.AdminPanelSettings, "Admin")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF09819),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFFF5F5F5)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Live matches section
            if (homeState.liveMatches.isNotEmpty()) {
                item {
                    Text(
                        "Live Now 🔴",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(homeState.liveMatches) { match ->
                    LiveMatchCard(
                        match = match,
                        onClick = { navController.navigate("live") }
                    )
                }
            }

            // Upcoming matches section
            if (homeState.upcomingMatches.isNotEmpty()) {
                item {
                    Text(
                        "Upcoming Matches",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(homeState.upcomingMatches) { match ->
                    val isRegistered = registrationViewModel.isRegisteredFor(match.id)
                    val isFull = match.currentSquadCount >= match.maxSquadSize && match.maxSquadSize > 0
                    
                    // Check eligibility
                    val isEligible = remember(match, currentUser) {
                        if (currentUser == null) false
                        else {
                            val deptOk = match.allowedDepartments.isEmpty() ||
                                match.allowedDepartments.any { it.equals(currentUser.department, ignoreCase = true) }
                            val yearOk = match.allowedYears.isEmpty() ||
                                match.allowedYears.any { it.equals(currentUser.yearOfStudy, ignoreCase = true) }
                            deptOk && yearOk
                        }
                    }

                    MatchCard(
                        match = match,
                        isRegistered = isRegistered,
                        isFull = isFull,
                        isEligible = isEligible,
                        showRegisterButton = !isAdmin,
                        onRegisterClick = {
                            if (isRegistered) {
                                matchToCancel = match
                                showCancelDialog = true
                            } else {
                                selectedMatch = match
                                showRegistrationSheet = true
                            }
                        }
                    )
                }
            }

            // Empty state
            if (!homeState.isLoading && homeState.liveMatches.isEmpty() && homeState.upcomingMatches.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.EventBusy,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color.Gray
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "No matches available",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }

    // Registration bottom sheet
    if (showRegistrationSheet && selectedMatch != null) {
        AdvancedRegistrationBottomSheet(
            match = selectedMatch!!,
            currentUser = regState.currentUser,
            onDismiss = {
                showRegistrationSheet = false
                selectedMatch = null
            },
            onRegister = { data ->
                registrationViewModel.registerForMatch(selectedMatch!!, data)
                showRegistrationSheet = false
                selectedMatch = null
            }
        )
    }

    // Cancel confirmation dialog
    if (showCancelDialog && matchToCancel != null) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel Registration?") },
            text = {
                Text("Are you sure you want to cancel your registration for ${matchToCancel!!.teamA} vs ${matchToCancel!!.teamB}?")
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
}

@Composable
fun LiveMatchCard(match: Match, onClick: () -> Unit) {
    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    match.sportType,
                    style = SportFlowTheme.typography.labelMedium,
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    color = LiveRed,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color.White, CircleShape)
                        )
                        Text(
                            "LIVE",
                            style = SportFlowTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(match.teamA, style = SportFlowTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("${match.scoreA}", style = SportFlowTheme.typography.scoreDisplay.copy(fontSize = 36.sp), color = GnitsOrange)
                }
                Text("-", style = SportFlowTheme.typography.scoreDisplay.copy(fontSize = 36.sp), color = TextTertiary)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(match.teamB, style = SportFlowTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("${match.scoreB}", style = SportFlowTheme.typography.scoreDisplay.copy(fontSize = 36.sp), color = GnitsOrange)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "${match.currentPeriod} • ${match.venue}",
                style = SportFlowTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun MatchCard(
    match: Match,
    isRegistered: Boolean,
    isFull: Boolean,
    isEligible: Boolean,
    showRegisterButton: Boolean,
    onRegisterClick: () -> Unit
) {
    val buttonState = when {
        isRegistered -> ButtonState.REGISTERED
        isFull -> ButtonState.FULL
        !isEligible -> ButtonState.INELIGIBLE
        else -> ButtonState.REGISTER
    }
    
    val shouldPulse = isFull && !isRegistered && isEligible
    
    val cardContent: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(16.dp)) {
            // Sport badge
            Surface(
                color = GnitsOrangeLight,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    match.sportType,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = SportFlowTheme.typography.labelMedium,
                    color = GnitsOrange,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(12.dp))

            // Teams
            Text(
                "${match.teamA} vs ${match.teamB}",
                style = SportFlowTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(Modifier.height(8.dp))

            // Venue and time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Place, null, Modifier.size(16.dp), tint = TextTertiary)
                Spacer(Modifier.width(6.dp))
                Text(match.venue, style = SportFlowTheme.typography.bodySmall, color = TextSecondary)
            }

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, null, Modifier.size(16.dp), tint = TextTertiary)
                Spacer(Modifier.width(6.dp))
                Text(
                    match.scheduledTime?.toDate()?.toString() ?: "TBD",
                    style = SportFlowTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            // Eligibility text
            if (match.eligibilityText.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    match.eligibilityText,
                    style = SportFlowTheme.typography.bodySmall,
                    color = GnitsOrange,
                    fontWeight = FontWeight.Medium
                )
            }

            // Squad capacity
            if (match.maxSquadSize > 0) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { match.currentSquadCount.toFloat() / match.maxSquadSize },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (isFull) LiveRed else GnitsOrange,
                    trackColor = Color.Gray.copy(alpha = 0.15f)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${match.currentSquadCount}/${match.maxSquadSize} slots filled",
                    style = SportFlowTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            // Register button
            if (showRegisterButton) {
                Spacer(Modifier.height(14.dp))
                StateAwareButton(
                    state = buttonState,
                    onClick = onRegisterClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    
    if (shouldPulse) {
        PulseAnimation(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            color = GnitsOrange
        ) {
            GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                cardContent()
            }
        }
    } else {
        GlassmorphicCard(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)) {
            cardContent()
        }
    }
}
