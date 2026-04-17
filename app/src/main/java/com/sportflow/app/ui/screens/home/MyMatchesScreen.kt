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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.sportflow.app.data.model.*
import com.sportflow.app.ui.components.*
import com.sportflow.app.ui.theme.*
import com.sportflow.app.ui.viewmodel.MyMatchesViewModel
import com.sportflow.app.ui.viewmodel.RegistrationViewModel

/**
 * My Matches Screen — shows only the events the current GNITS student is registered for.
 *
 * Filters matches based on the current user's UID presence in the
 * gnits_matches/{matchId}/registrations sub-collection.
 * Each card indicates the live match status and allows cancelling a registration
 * if the match hasn't started yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyMatchesScreen(
    navController: NavHostController,
    viewModel: MyMatchesViewModel = hiltViewModel(),
    regViewModel: RegistrationViewModel = hiltViewModel()
) {
    val uiState  by viewModel.uiState.collectAsStateWithLifecycle()
    val regState by regViewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(regState.successMessage, regState.error) {
        val msg = regState.successMessage ?: regState.error
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            regViewModel.clearMessage()
        }
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        containerColor = ScreenBg
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {

            // ── Header ────────────────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PureWhite)
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Text(
                        text = "My Matches",
                        style = SportFlowTheme.typography.displayLarge,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your GNITS registered events",
                        style = SportFlowTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            // ── Loading ───────────────────────────────────────────────────
            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = GnitsOrange)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Loading your registrations...",
                                style = SportFlowTheme.typography.bodySmall,
                                color = TextTertiary
                            )
                        }
                    }
                }
            }

            // ── Registered Matches ────────────────────────────────────────
            if (!uiState.isLoading && uiState.myMatches.isNotEmpty()) {
                // Stats summary banner
                item {
                    MyMatchesSummaryBanner(
                        total     = uiState.myMatches.size,
                        live      = uiState.myMatches.count { it.status == MatchStatus.LIVE },
                        upcoming  = uiState.myMatches.count { it.status == MatchStatus.SCHEDULED },
                        completed = uiState.myMatches.count { it.status == MatchStatus.COMPLETED }
                    )
                }

                item {
                    SectionHeader(title = "Registered Events")
                }

                items(
                    items = uiState.myMatches,
                    key = { it.id }
                ) { match ->
                    val isLoadingReg = match.id in regState.loadingMatchIds
                    MyMatchCard(
                        match         = match,
                        isUnregistering = isLoadingReg,
                        onCancelReg   = {
                            if (match.status == MatchStatus.SCHEDULED) {
                                regViewModel.cancelRegistration(match.id)
                            }
                        },
                        onViewLive    = {
                            if (match.status == MatchStatus.LIVE) {
                                navController.navigate("live")
                            }
                        }
                    )
                }
            }

            // ── Empty State ───────────────────────────────────────────────
            if (!uiState.isLoading && uiState.myMatches.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(GnitsOrangeLight, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.SportsScore,
                                    contentDescription = null,
                                    tint = GnitsOrange,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "No registrations yet",
                                style = SportFlowTheme.typography.headlineLarge,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.emptyMessage,
                                style = SportFlowTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { navController.navigate("home") },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GnitsOrange)
                            ) {
                                Icon(
                                    Icons.Outlined.Explore,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Browse Events",
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
}

// ── Summary Stats Banner ──────────────────────────────────────────────────────

@Composable
private fun MyMatchesSummaryBanner(
    total: Int,
    live: Int,
    upcoming: Int,
    completed: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatPill(label = "Upcoming", value = "$upcoming", color = InfoBlue,      modifier = Modifier.weight(1f))
        StatPill(label = "Live",     value = "$live",     color = LiveRed,       modifier = Modifier.weight(1f))
        StatPill(label = "Done",     value = "$completed", color = SuccessGreen, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatPill(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = SportFlowTheme.typography.displayMedium,
                color = color,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = label,
                style = SportFlowTheme.typography.labelSmall,
                color = color.copy(alpha = 0.8f)
            )
        }
    }
}

// ── My Match Card ─────────────────────────────────────────────────────────────

@Composable
private fun MyMatchCard(
    match: Match,
    isUnregistering: Boolean = false,
    onCancelReg: () -> Unit,
    onViewLive: () -> Unit
) {
    SportCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        onClick = { if (match.status == MatchStatus.LIVE) onViewLive() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Top row: sport chip + status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SportTypeChip(sportType = match.sportType)
                StatusChip(
                    text = match.status.name,
                    color = when (match.status) {
                        MatchStatus.SCHEDULED -> InfoBlue
                        MatchStatus.LIVE      -> LiveRed
                        MatchStatus.HALFTIME  -> WarningAmber
                        MatchStatus.COMPLETED -> SuccessGreen
                        MatchStatus.CANCELLED -> ErrorRed
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Match title
            Text(
                text = "${match.teamA}  vs  ${match.teamB}",
                style = SportFlowTheme.typography.headlineLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Venue + round info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = match.venue.ifEmpty { "GNITS Campus" },
                    style = SportFlowTheme.typography.bodySmall,
                    color = TextTertiary
                )
                if (match.round.isNotBlank()) {
                    Text("·", color = TextTertiary, fontSize = 10.sp)
                    Text(
                        text = match.round,
                        style = SportFlowTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Tournament + eligibility
            if (match.tournamentName.isNotBlank() || match.eligibilityText.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (match.tournamentName.isNotBlank()) {
                        Icon(
                            Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            tint = GnitsOrange,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = match.tournamentName,
                            style = SportFlowTheme.typography.bodySmall,
                            color = GnitsOrange,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (match.eligibilityText.isNotBlank()) {
                        if (match.tournamentName.isNotBlank()) Text("·", color = TextTertiary, fontSize = 10.sp)
                        Icon(
                            Icons.Outlined.VerifiedUser,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = match.eligibilityText,
                            style = SportFlowTheme.typography.bodySmall,
                            color = SuccessGreen
                        )
                    }
                }
            }

            // Live score display
            if (match.status == MatchStatus.LIVE || match.status == MatchStatus.HALFTIME) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = LiveRed.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = match.teamA,
                            style = SportFlowTheme.typography.labelLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${match.scoreA}  –  ${match.scoreB}",
                                style = SportFlowTheme.typography.headlineLarge,
                                color = TextPrimary,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = match.currentPeriod.ifEmpty { "LIVE" },
                                style = SportFlowTheme.typography.labelSmall,
                                color = LiveRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = match.teamB,
                            style = SportFlowTheme.typography.labelLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Registered badge (always shown)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SuccessGreen.copy(alpha = 0.1f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Confirmed",
                            style = SportFlowTheme.typography.labelMedium,
                            color = SuccessGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Contextual action button
                when (match.status) {
                    MatchStatus.LIVE, MatchStatus.HALFTIME -> {
                        Button(
                            onClick = onViewLive,
                            colors = ButtonDefaults.buttonColors(containerColor = LiveRed),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Filled.PlayCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Watch Live", color = Color.White, fontWeight = FontWeight.Bold,
                                style = SportFlowTheme.typography.labelMedium)
                        }
                    }
                    MatchStatus.SCHEDULED -> {
                        if (isUnregistering) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = ErrorRed,
                                    strokeWidth = 2.dp
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = onCancelReg,
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = null,
                                    tint = ErrorRed,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cancel", color = ErrorRed,
                                    style = SportFlowTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    else -> { /* Completed / Cancelled — no action */ }
                }
            }
        }
    }
}
