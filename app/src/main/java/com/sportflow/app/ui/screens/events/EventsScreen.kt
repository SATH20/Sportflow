package com.sportflow.app.ui.screens.events

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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

@Composable
fun EventsScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Football", "Cricket", "Basketball", "Badminton")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PureWhite)
                    .padding(20.dp)
            ) {
                Text(
                    text = "Events & Tournaments",
                    style = SportFlowTheme.typography.displayLarge,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Find your next game",
                    style = SportFlowTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        // Sport Filter Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PureWhite)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filters.forEach { filter ->
                    val isSelected = selectedFilter == filter
                    FilterChip(
                        onClick = { selectedFilter = filter },
                        label = {
                            Text(
                                text = filter,
                                style = SportFlowTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        selected = isSelected,
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = OffWhite,
                            selectedContainerColor = PlayoGreenLight,
                            labelColor = TextSecondary,
                            selectedLabelColor = PlayoGreenDark
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = CardBorder,
                            selectedBorderColor = PlayoGreen,
                            enabled = true,
                            selected = isSelected
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Tournaments Section
        if (uiState.tournaments.isNotEmpty()) {
            item {
                SectionHeader(title = "Active Tournaments")
            }

            items(uiState.tournaments.filter {
                selectedFilter == "All" || it.sport.equals(selectedFilter, ignoreCase = true)
            }) { tournament ->
                TournamentEventCard(
                    tournament = tournament,
                    onClick = {
                        navController.navigate("bracket?tournamentId=${tournament.id}")
                    }
                )
            }
        }

        // Upcoming Events Section
        item {
            SectionHeader(title = "Upcoming Events")
        }

        items(uiState.upcomingMatches.filter {
            selectedFilter == "All" || it.sportType.equals(selectedFilter, ignoreCase = true)
        }) { match ->
            UpcomingEventCard(
                match = match,
                onClick = { }
            )
        }
    }
}

// ── Tournament Event Card — Full-width with hero style ───────────────────────

@Composable
private fun TournamentEventCard(
    tournament: Tournament,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
    ) {
        // Background gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = when (tournament.sport.lowercase()) {
                            "football" -> listOf(Color(0xFF1E3A5F), Color(0xFF0F172A))
                            "cricket" -> listOf(Color(0xFF7C3AED), Color(0xFF4C1D95))
                            "basketball" -> listOf(Color(0xFFEA580C), Color(0xFF9A3412))
                            else -> listOf(PlayoGreenDark, Color(0xFF064E3B))
                        }
                    )
                )
        )

        // Decorative
        Box(
            modifier = Modifier
                .size(150.dp)
                .offset(x = 250.dp, y = (-30).dp)
                .background(Color.White.copy(alpha = 0.06f), CircleShape)
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = tournament.sport.uppercase(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = SportFlowTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when (tournament.status) {
                        TournamentStatus.IN_PROGRESS -> PlayoGreen.copy(alpha = 0.9f)
                        TournamentStatus.REGISTRATION -> WarningAmber.copy(alpha = 0.9f)
                        else -> TextTertiary.copy(alpha = 0.6f)
                    }
                ) {
                    Text(
                        text = tournament.status.name.replace("_", " "),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = SportFlowTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Column {
                Text(
                    text = tournament.name,
                    style = SportFlowTheme.typography.displayMedium,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = tournament.prizePool,
                            style = SportFlowTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Group,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${tournament.teams.size}/${tournament.maxTeams} teams",
                            style = SportFlowTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
}

// ── Upcoming Event Card ──────────────────────────────────────────────────────

@Composable
private fun UpcomingEventCard(
    match: Match,
    onClick: () -> Unit
) {
    SportCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sport icon
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(PlayoGreenLight, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (match.sportType.lowercase()) {
                        "football" -> Icons.Filled.SportsSoccer
                        "basketball" -> Icons.Filled.SportsBasketball
                        "cricket" -> Icons.Filled.SportsCricket
                        "badminton" -> Icons.Filled.SportsTennis
                        else -> Icons.Filled.Sports
                    },
                    contentDescription = null,
                    tint = PlayoGreenDark,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${match.teamA} vs ${match.teamB}",
                    style = SportFlowTheme.typography.headlineSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = match.venue,
                        style = SportFlowTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                SkillTag(level = listOf("Beginner", "Intermediate").random())
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = match.tournamentName,
                    style = SportFlowTheme.typography.labelSmall,
                    color = InfoBlue,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
