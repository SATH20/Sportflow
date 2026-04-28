package com.sportflow.app.ui.screens.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import com.sportflow.app.ui.viewmodel.ScorecardViewModel

/**
 * My Matches Screen — shows only the events the current GNITS student is registered for.
 *
 * PLAYER ONLY: This route is physically absent from the NavHost when role == ADMIN.
 * The currentUserRole parameter is forwarded to cancelRegistration() so the ViewModel
 * RBAC guard fires even if a user somehow finds a deep-link path to this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyMatchesScreen(
    navController: NavHostController,
    currentUserRole: UserRole = UserRole.PLAYER,
    viewModel: MyMatchesViewModel = hiltViewModel(),
    regViewModel: RegistrationViewModel = hiltViewModel(),
    scorecardViewModel: ScorecardViewModel = hiltViewModel()
) {
    val uiState  by viewModel.uiState.collectAsStateWithLifecycle()
    val regState by regViewModel.uiState.collectAsStateWithLifecycle()
    val scorecardState by scorecardViewModel.uiState.collectAsStateWithLifecycle()

    // Tab state
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Upcoming", "Live", "Completed")

    // Filter matches by selected tab
    val filteredMatches = remember(uiState.myMatches, selectedTab) {
        when (selectedTab) {
            0 -> uiState.myMatches.filter { it.status == MatchStatus.SCHEDULED }
            1 -> uiState.myMatches.filter { it.status == MatchStatus.LIVE || it.status == MatchStatus.HALFTIME }
            2 -> uiState.myMatches.filter { it.status == MatchStatus.COMPLETED }
            else -> uiState.myMatches
        }
    }

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

            // ── Tabs ──────────────────────────────────────────────────────
            if (!uiState.isLoading && uiState.myMatches.isNotEmpty()) {
                item {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = PureWhite,
                        contentColor = GnitsOrange,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = GnitsOrange
                            )
                        }
                    ) {
                        tabs.forEachIndexed { index, title ->
                            val count = when (index) {
                                0 -> uiState.myMatches.count { it.status == MatchStatus.SCHEDULED }
                                1 -> uiState.myMatches.count { it.status == MatchStatus.LIVE || it.status == MatchStatus.HALFTIME }
                                2 -> uiState.myMatches.count { it.status == MatchStatus.COMPLETED }
                                else -> 0
                            }
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = title,
                                            style = SportFlowTheme.typography.labelLarge,
                                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (count > 0) {
                                            Surface(
                                                shape = CircleShape,
                                                color = if (selectedTab == index) GnitsOrange else TextTertiary.copy(alpha = 0.3f)
                                            ) {
                                                Text(
                                                    text = "$count",
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = SportFlowTheme.typography.labelSmall,
                                                    color = if (selectedTab == index) Color.White else TextSecondary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                },
                                selectedContentColor = GnitsOrange,
                                unselectedContentColor = TextSecondary
                            )
                        }
                    }
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

                // Show filtered matches based on selected tab
                if (filteredMatches.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Outlined.SportsScore,
                                    contentDescription = null,
                                    tint = TextTertiary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No ${tabs[selectedTab].lowercase()} matches",
                                    style = SportFlowTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = filteredMatches,
                        key = { it.id }
                    ) { match ->
                        val isLoadingReg = match.id in regState.loadingMatchIds
                        val isShowingScorecard = scorecardState.selectedMatchId == match.id
                        MyMatchCard(
                            match           = match,
                            isUnregistering = isLoadingReg,
                            scorecard       = if (isShowingScorecard) scorecardState.scorecard else null,
                            isScorecardLoading = isShowingScorecard && scorecardState.isLoading,
                            onCancelReg = {
                                if (match.status == MatchStatus.SCHEDULED) {
                                    // Pass role so ViewModel RBAC guard can verify
                                    regViewModel.cancelRegistration(match.id, currentUserRole)
                                }
                            },
                            onViewLive = {
                                if (match.status == MatchStatus.LIVE) {
                                    navController.navigate("live")
                                }
                            },
                            onViewPerformance = {
                                if (isShowingScorecard) {
                                    scorecardViewModel.clearScorecard()
                                } else {
                                    scorecardViewModel.loadScorecard(match.id)
                                }
                            }
                        )
                    }
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
    scorecard: PlayerScorecard? = null,
    isScorecardLoading: Boolean = false,
    onCancelReg: () -> Unit,
    onViewLive: () -> Unit,
    onViewPerformance: () -> Unit = {}
) {
    SportCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .animateContentSize(animationSpec = tween(300)),
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

            // ── Performance Button (for Live, Completed) ─────────────────
            if (match.status == MatchStatus.LIVE || match.status == MatchStatus.HALFTIME || match.status == MatchStatus.COMPLETED) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onViewPerformance,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (scorecard != null) GnitsOrange else InfoBlue
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Icon(
                        if (scorecard != null) Icons.Filled.ExpandLess else Icons.Filled.BarChart,
                        contentDescription = null,
                        tint = if (scorecard != null) GnitsOrange else InfoBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (scorecard != null) "Hide Performance" else "View My Performance",
                        style = SportFlowTheme.typography.labelMedium,
                        color = if (scorecard != null) GnitsOrange else InfoBlue,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ── Inline Scorecard (expanded when loaded) ──────────────────
            if (isScorecardLoading) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = GnitsOrange, modifier = Modifier.size(24.dp))
                }
            } else if (scorecard != null) {
                Spacer(modifier = Modifier.height(12.dp))
                PlayerPerformanceCard(scorecard = scorecard, sportType = match.sportType)
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// PLAYER PERFORMANCE CARD — Sport-Specific Personal Scorecard Display
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun PlayerPerformanceCard(
    scorecard: PlayerScorecard,
    sportType: String
) {
    val attributes = PlayerScorecardStrategy.getAttributes(sportType)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = GnitsOrangeLight.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, GnitsOrange.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Assessment,
                    contentDescription = null,
                    tint = GnitsOrangeDark,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Personal Scorecard",
                    style = SportFlowTheme.typography.labelLarge,
                    color = GnitsOrangeDark,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stats grid — 2 columns
            val rows = attributes.chunked(2)
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { attr ->
                        val currentValue = scorecard.sportData[attr.key]
                        val displayVal = when (currentValue) {
                            is Number -> currentValue.toInt().toString()
                            is String -> currentValue.ifBlank { "—" }
                            else -> "0"
                        }

                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = PureWhite
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(attr.emoji, style = SportFlowTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        attr.label,
                                        style = SportFlowTheme.typography.labelSmall,
                                        color = TextTertiary,
                                        maxLines = 1
                                    )
                                    Text(
                                        displayVal,
                                        style = SportFlowTheme.typography.headlineSmall,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    // Fill last row if odd number of items
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}
