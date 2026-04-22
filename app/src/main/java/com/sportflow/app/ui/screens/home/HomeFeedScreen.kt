package com.sportflow.app.ui.screens.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
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
import com.sportflow.app.ui.viewmodel.HomeViewModel
import com.sportflow.app.ui.viewmodel.RegistrationViewModel

// ── Home Feed Screen ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFeedScreen(
    navController: NavHostController,
    currentUser: SportUser? = null,
    isAdmin: Boolean = false,
    viewModel: HomeViewModel = hiltViewModel(),
    regViewModel: RegistrationViewModel = hiltViewModel()
) {
    val uiState  by viewModel.uiState.collectAsStateWithLifecycle()
    val regState by regViewModel.uiState.collectAsStateWithLifecycle()

    // Unified feed: open-registration tournaments first, then scheduled matches
    val unifiedFeed: List<Any> = remember(uiState.upcomingMatches, uiState.tournaments) {
        buildList {
            uiState.tournaments
                .filter { it.status == TournamentStatus.REGISTRATION }
                .forEach { add(it) }
            uiState.upcomingMatches.forEach { add(it) }
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = ScreenBg
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {

            // ── Header ───────────────────────────────────────────────────
            item {
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
                                    onClick = { navController.navigate("admin") },
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

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = OffWhite,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = null,
                                tint = TextTertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Search GNITS tournaments, events...",
                                style = SportFlowTheme.typography.bodyMedium,
                                color = TextTertiary
                            )
                        }
                    }
                }
            }

            // ── Loading ──────────────────────────────────────────────────
            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = GnitsOrange) }
                }
            }

            // ── Live Now ─────────────────────────────────────────────────
            if (uiState.liveMatches.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Live Now 🔴",
                        actionText = "View All",
                        onAction = { navController.navigate("live") }
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(uiState.liveMatches) { match ->
                            LiveMatchBanner(
                                match = match,
                                onClick = { navController.navigate("live") }
                            )
                        }
                    }
                }
            }

            // ── Featured Tournament Hero ──────────────────────────────────
            if (uiState.tournaments.isNotEmpty()) {
                item { SectionHeader(title = "Featured Tournament") }
                item {
                    TopTournamentHero(
                        tournament = uiState.tournaments.first(),
                        onClick = {
                            navController.navigate(
                                "bracket?tournamentId=${uiState.tournaments.first().id}"
                            )
                        }
                    )
                }
            }

            // ── Unified Feed: Matches + Open Tournaments ──────────────────
            if (unifiedFeed.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Events & Tournaments",
                        actionText = "See All",
                        onAction = { navController.navigate("events") }
                    )
                }

                items(unifiedFeed) { feedItem ->
                    when (feedItem) {
                        is Match -> {
                            val isRegistered = feedItem.id in regState.registeredMatchIds
                            val isLoading    = feedItem.id in regState.loadingMatchIds

                            // ── Eligibility mismatch detection (department + year) ──────────
                            val eligibilityBlocked = remember(feedItem, currentUser) {
                                if (currentUser == null) false
                                else {
                                    val deptBlocked = feedItem.allowedDepartments.isNotEmpty() &&
                                        currentUser.department.isNotBlank() &&
                                        feedItem.allowedDepartments.none {
                                            it.equals(currentUser.department, ignoreCase = true)
                                        }
                                    val yearBlocked = feedItem.allowedYears.isNotEmpty() &&
                                        currentUser.yearOfStudy.isNotBlank() &&
                                        feedItem.allowedYears.none {
                                            it.equals(currentUser.yearOfStudy, ignoreCase = true)
                                        }
                                    deptBlocked || yearBlocked
                                }
                            }

                            EventFeedCard(
                                match             = feedItem,
                                isRegistered      = isRegistered,
                                isRegistering     = isLoading,
                                eligibilityBlocked = eligibilityBlocked,
                                // Admins physically cannot register — hide the button entirely
                                showRegisterButton = !isAdmin,
                                onRegister = {
                                    val role = currentUser?.role ?: UserRole.PLAYER
                                    if (isRegistered) regViewModel.cancelRegistration(feedItem.id, role)
                                    else             regViewModel.register(feedItem, role)
                                },
                                onClick = { }
                            )
                        }
                        is Tournament -> {
                            TournamentFeedCard(
                                tournament = feedItem,
                                onClick = {
                                    navController.navigate("bracket?tournamentId=${feedItem.id}")
                                }
                            )
                        }
                    }
                }
            }

            // ── Empty State ───────────────────────────────────────────────
            if (!uiState.isLoading && uiState.liveMatches.isEmpty() &&
                uiState.upcomingMatches.isEmpty() && uiState.tournaments.isEmpty()
            ) {
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
                                text = "No events yet",
                                style = SportFlowTheme.typography.headlineMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Ask your Department Sports Coordinator to create matches",
                                style = SportFlowTheme.typography.bodyMedium,
                                color = TextTertiary
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Live Match Banner ─────────────────────────────────────────────────────────

@Composable
private fun LiveMatchBanner(
    match: Match,
    onClick: () -> Unit
) {
    SportCard(
        modifier = Modifier.width(320.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SportTypeChip(sportType = match.sportType)
                LiveBadge()
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    TeamAvatar(teamName = match.teamA, size = 44.dp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = match.teamA,
                        style = SportFlowTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "${match.scoreA} - ${match.scoreB}",
                        style = SportFlowTheme.typography.displayMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${match.currentPeriod} · ${match.elapsedTime}",
                        style = SportFlowTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    TeamAvatar(teamName = match.teamB, size = 44.dp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = match.teamB,
                        style = SportFlowTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = match.venue,
                    style = SportFlowTheme.typography.bodySmall,
                    color = TextTertiary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = match.tournamentName,
                    style = SportFlowTheme.typography.labelMedium,
                    color = InfoBlue,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ── Featured Tournament Hero Card ─────────────────────────────────────────────

@Composable
private fun TopTournamentHero(
    tournament: Tournament,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(200.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(GnitsOrange, GnitsOrangeDark, Color(0xFF8B5E0C))
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(200.dp)
                .offset(x = 220.dp, y = (-40).dp)
                .background(Color.White.copy(alpha = 0.08f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(120.dp)
                .offset(x = 280.dp, y = 100.dp)
                .background(Color.White.copy(alpha = 0.05f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "🏆 FEATURED",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = SportFlowTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Icon(
                    Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(32.dp)
                )
            }

            Column {
                Text(
                    text = tournament.name,
                    style = SportFlowTheme.typography.displayMedium,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = tournament.sport,
                        style = SportFlowTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text("•", color = Color.White.copy(alpha = 0.5f))
                    Text(
                        text = tournament.eligibilityText.ifBlank { "GNITS Inter-Dept" },
                        style = SportFlowTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Bold
                    )
                    Text("•", color = Color.White.copy(alpha = 0.5f))
                    Text(
                        text = "${tournament.maxTeams} teams",
                        style = SportFlowTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

// ── Event Feed Card — Upcoming Match with Eligibility + Register button ────────────

@Composable
private fun EventFeedCard(
    match: Match,
    isRegistered: Boolean = false,
    isRegistering: Boolean = false,
    eligibilityBlocked: Boolean = false,
    showRegisterButton: Boolean = true,      // false for ADMIN mode
    onRegister: () -> Unit = {},
    onClick: () -> Unit
) {
    // Squad slot state derived from the new model fields
    val isSquadFull = match.maxSquadSize > 0 &&
        match.currentSquadCount >= match.maxSquadSize &&
        !isRegistered                               // registered users keep their cancel button
    SportCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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

            // GNITS Eligibility badge
            if (match.eligibilityText.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (eligibilityBlocked) Icons.Outlined.Warning else Icons.Outlined.VerifiedUser,
                        contentDescription = "Eligibility",
                        tint = if (eligibilityBlocked) WarningAmber else SuccessGreen,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (eligibilityBlocked)
                            "⚠️ ${match.eligibilityText} — you are not eligible"
                        else
                            match.eligibilityText,
                        style = SportFlowTheme.typography.bodySmall,
                        color = if (eligibilityBlocked) WarningAmber else SuccessGreen,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Allowed years badge (when year-restricted)
            if (match.allowedYears.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.School,
                        contentDescription = "Allowed years",
                        tint = InfoBlue,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    val yearLabels = match.allowedYears
                        .mapNotNull { com.sportflow.app.data.model.GnitsYear.fromCode(it)?.displayName }
                        .ifEmpty { match.allowedYears }
                        .joinToString(", ")
                    Text(
                        text = "Open to: $yearLabels",
                        style = SportFlowTheme.typography.bodySmall,
                        color = InfoBlue,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Squad slots progress indicator
            if (match.maxSquadSize > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                val slotsFraction = (match.currentSquadCount.toFloat() / match.maxSquadSize).coerceIn(0f, 1f)
                val slotsColor = when {
                    slotsFraction >= 1f   -> ErrorRed
                    slotsFraction >= 0.8f -> WarningAmber
                    else                  -> SuccessGreen
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.Group,
                        contentDescription = "Squad slots",
                        tint = slotsColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "${match.currentSquadCount}/${match.maxSquadSize} slots",
                        style = SportFlowTheme.typography.bodySmall,
                        color = slotsColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    LinearProgressIndicator(
                        progress = { slotsFraction },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = slotsColor,
                        trackColor = slotsColor.copy(alpha = 0.15f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom row: status chip + register button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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

                // Register button — PLAYER only + SCHEDULED + not blocked
                if (showRegisterButton && match.status == MatchStatus.SCHEDULED) {
                    when {
                        isRegistering -> CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = GnitsOrange,
                            strokeWidth = 2.dp
                        )
                        isRegistered -> OutlinedButton(
                            onClick = onRegister,
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
                        // ⚠️ Hard eligibility block — WarningAmber chip, no action
                        eligibilityBlocked -> Button(
                            onClick = {},
                            enabled = false,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = WarningAmber.copy(alpha = 0.15f),
                                disabledContentColor   = WarningAmber
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Not Eligible",
                                style = SportFlowTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        isSquadFull -> Button(
                            onClick = {},
                            enabled = false,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = TextTertiary.copy(alpha = 0.15f),
                                disabledContentColor   = TextTertiary
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Squad Full",
                                style = SportFlowTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        else -> Button(
                            onClick = onRegister,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GnitsOrange),
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
                                text = "Register",
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

// ── Tournament Feed Card (in unified feed) ─────────────────────────────────────

@Composable
private fun TournamentFeedCard(
    tournament: Tournament,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    Brush.linearGradient(colors = listOf(GnitsOrange, GnitsOrangeDark))
                )
        )
        Box(
            modifier = Modifier
                .size(120.dp)
                .offset(x = 270.dp, y = (-20).dp)
                .background(Color.White.copy(alpha = 0.07f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "🏆 REGISTRATION OPEN",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = SportFlowTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = tournament.name,
                style = SportFlowTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tournament.sport,
                    style = SportFlowTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Text("•", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                Text(
                    text = if (tournament.eligibilityText.isNotBlank())
                        tournament.eligibilityText
                    else
                        "All GNITS students",
                    style = SportFlowTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text("•", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                Text(
                    text = "${tournament.teams.size}/${tournament.maxTeams}",
                    style = SportFlowTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

// ── Tournament Compact Card (kept for backward compat) ────────────────────────

@Composable
private fun TournamentCompactCard(
    tournament: Tournament,
    onClick: () -> Unit
) {
    SportCard(
        modifier = Modifier.width(260.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip(
                    text = tournament.status.name.replace("_", " "),
                    color = when (tournament.status) {
                        TournamentStatus.REGISTRATION -> WarningAmber
                        TournamentStatus.IN_PROGRESS  -> GnitsOrange
                        TournamentStatus.COMPLETED    -> TextTertiary
                        TournamentStatus.CANCELLED    -> ErrorRed
                    }
                )
                Icon(
                    Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = GnitsOrange,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = tournament.name,
                style = SportFlowTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = tournament.sport,
                style = SportFlowTheme.typography.bodySmall,
                color = TextTertiary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Venue",
                        style = SportFlowTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                    Text(
                        text = tournament.venue.ifEmpty { "TBD" },
                        style = SportFlowTheme.typography.labelLarge,
                        color = GnitsOrange,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Teams",
                        style = SportFlowTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                    Text(
                        text = "${tournament.teams.size}/${tournament.maxTeams}",
                        style = SportFlowTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}
