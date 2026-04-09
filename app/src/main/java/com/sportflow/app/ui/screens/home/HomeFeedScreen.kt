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
import androidx.compose.ui.draw.shadow
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFeedScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // ── Search Bar + Greeting ────────────────────────────────────────
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
                            text = "Good evening! 👋",
                            style = SportFlowTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = "SportFlow",
                            style = SportFlowTheme.typography.displayLarge,
                            color = TextPrimary
                        )
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

                Spacer(modifier = Modifier.height(16.dp))

                // Search
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
                            text = "Search tournaments, events...",
                            style = SportFlowTheme.typography.bodyMedium,
                            color = TextTertiary
                        )
                    }
                }
            }
        }

        // ── Live Now Banner ──────────────────────────────────────────────
        if (uiState.liveMatches.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Live Now",
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

        // ── Top Tournament Hero ──────────────────────────────────────────
        if (uiState.tournaments.isNotEmpty()) {
            item {
                SectionHeader(title = "Featured Tournament")
            }
            item {
                TopTournamentHero(
                    tournament = uiState.tournaments.first(),
                    onClick = {
                        navController.navigate("bracket?tournamentId=${uiState.tournaments.first().id}")
                    }
                )
            }
        }

        // ── Suggested Events Feed ────────────────────────────────────────
        item {
            SectionHeader(
                title = "Suggested For You",
                actionText = "See All",
                onAction = { navController.navigate("events") }
            )
        }

        items(uiState.upcomingMatches) { match ->
            EventFeedCard(
                match = match,
                onClick = { }
            )
        }

        // ── Active Tournaments ───────────────────────────────────────────
        if (uiState.tournaments.size > 1) {
            item {
                SectionHeader(
                    title = "Tournaments",
                    actionText = "View All",
                    onAction = { navController.navigate("events") }
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(uiState.tournaments) { tournament ->
                        TournamentCompactCard(
                            tournament = tournament,
                            onClick = {
                                navController.navigate("bracket?tournamentId=${tournament.id}")
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── Live Match Banner ────────────────────────────────────────────────────────

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

            // Teams & Score
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

// ── Top Tournament Hero Card ─────────────────────────────────────────────────

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
        // Gradient background (simulating hero image)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            PlayoGreen,
                            PlayoGreenDark,
                            Color(0xFF064E3B)
                        )
                    )
                )
        )

        // Decorative circles
        Box(
            modifier = Modifier
                .size(200.dp)
                .offset(x = 220.dp, y = (-40).dp)
                .background(
                    Color.White.copy(alpha = 0.08f),
                    CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(120.dp)
                .offset(x = 280.dp, y = 100.dp)
                .background(
                    Color.White.copy(alpha = 0.05f),
                    CircleShape
                )
        )

        // Content overlay
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
                    Text(
                        text = "•",
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Text(
                        text = tournament.prizePool,
                        style = SportFlowTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "•",
                        color = Color.White.copy(alpha = 0.5f)
                    )
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

// ── Event Feed Card — Playo-style ────────────────────────────────────────────

@Composable
private fun EventFeedCard(
    match: Match,
    onClick: () -> Unit
) {
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
                SkillTag(level = listOf("Beginner", "Intermediate", "Advanced").random())
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "${match.teamA} vs ${match.teamB}",
                style = SportFlowTheme.typography.headlineLarge,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

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
                        text = match.venue,
                        style = SportFlowTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
                Text("•", color = TextTertiary, fontSize = 10.sp)
                Text(
                    text = match.tournamentName,
                    style = SportFlowTheme.typography.bodySmall,
                    color = InfoBlue,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Slots indicator
            SlotsIndicator(
                filled = (6..14).random(),
                total = 16
            )

            Spacer(modifier = Modifier.height(14.dp))

            PillButton(
                text = "Join Now",
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Filled.ArrowForward
            )
        }
    }
}

// ── Tournament Compact Card ──────────────────────────────────────────────────

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
                        TournamentStatus.IN_PROGRESS -> PlayoGreen
                        TournamentStatus.COMPLETED -> TextTertiary
                        TournamentStatus.CANCELLED -> ErrorRed
                    }
                )
                Icon(
                    Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = PlayoGreen,
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
                        text = "Prize",
                        style = SportFlowTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                    Text(
                        text = tournament.prizePool,
                        style = SportFlowTheme.typography.labelLarge,
                        color = PlayoGreen,
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
