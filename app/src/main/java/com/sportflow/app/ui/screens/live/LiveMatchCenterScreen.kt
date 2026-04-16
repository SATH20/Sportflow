package com.sportflow.app.ui.screens.live

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.sportflow.app.data.model.*
import com.sportflow.app.ui.components.*
import com.sportflow.app.ui.theme.*
import com.sportflow.app.ui.viewmodel.LiveMatchViewModel

@Composable
fun LiveMatchCenterScreen(
    navController: NavHostController,
    viewModel: LiveMatchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "My Matches",
                        style = SportFlowTheme.typography.displayLarge,
                        color = TextPrimary
                    )
                    if (uiState.liveMatches.isNotEmpty()) {
                        LiveBadge()
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (uiState.liveMatches.isEmpty()) "No live matches" else "${uiState.liveMatches.size} matches in progress",
                    style = SportFlowTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        // Loading
        if (uiState.isLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = GnitsOrange)
                }
            }
        }

        // Empty state
        if (!uiState.isLoading && uiState.liveMatches.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(GnitsOrangeLight, RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.SportsScore,
                                contentDescription = null,
                                tint = GnitsOrange,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Live Matches",
                            style = SportFlowTheme.typography.headlineLarge,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Check back when GNITS tournaments are underway",
                            style = SportFlowTheme.typography.bodyMedium,
                            color = TextTertiary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Match Selector
        if (uiState.liveMatches.size > 1) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.liveMatches) { match ->
                        MatchTab(
                            match = match,
                            isSelected = match.id == uiState.selectedMatch?.id,
                            onClick = { viewModel.selectMatch(match) }
                        )
                    }
                }
            }
        }

        // Selected Match Score
        uiState.selectedMatch?.let { match ->
            val sportScore = SportScoreEngine.formatScore(match)

            item {
                Spacer(modifier = Modifier.height(12.dp))
                SportCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Tournament / Sport info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusChip(
                                text = match.tournamentName.ifEmpty { "GNITS Match" },
                                color = InfoBlue
                            )
                            StatusChip(
                                text = match.sportType.ifEmpty { match.round },
                                color = GnitsOrange
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Sport-specific score header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Team A
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    match.teamA,
                                    style = SportFlowTheme.typography.labelLarge,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    sportScore.displayA,
                                    style = SportFlowTheme.typography.displayLarge,
                                    color = GnitsOrangeDark,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                if (match.teamADepartment.isNotBlank()) {
                                    StatusChip(text = match.teamADepartment, color = GnitsOrange)
                                }
                            }

                            // Center period badge
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (match.status == MatchStatus.LIVE) LiveRedBg else OffWhite
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        if (match.status == MatchStatus.LIVE) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Box(modifier = Modifier.size(6.dp).background(LiveRed, CircleShape))
                                                Text("LIVE", style = SportFlowTheme.typography.labelSmall, color = LiveRed, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        if (sportScore.centerText.isNotBlank()) {
                                            Text(
                                                sportScore.centerText,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                style = SportFlowTheme.typography.labelSmall,
                                                color = TextSecondary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            // Team B
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    match.teamB,
                                    style = SportFlowTheme.typography.labelLarge,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    sportScore.displayB,
                                    style = SportFlowTheme.typography.displayLarge,
                                    color = InfoBlue,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                if (match.teamBDepartment.isNotBlank()) {
                                    StatusChip(text = match.teamBDepartment, color = InfoBlue)
                                }
                            }
                        }

                        // Sport-specific subtext (quarter breakdown, chase target, set scores)
                        if (sportScore.subText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = OffWhite,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    sportScore.subText,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    style = SportFlowTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                        if (sportScore.extras.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(sportScore.extras, style = SportFlowTheme.typography.bodySmall, color = TextTertiary)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Timer / period
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = GnitsOrangeLight,
                            border = androidx.compose.foundation.BorderStroke(1.dp, GnitsOrange.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Timer,
                                    contentDescription = null,
                                    tint = GnitsOrangeDark,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "${match.currentPeriod} · ${match.elapsedTime}",
                                    style = SportFlowTheme.typography.timerDisplay,
                                    color = GnitsOrangeDark
                                )
                            }
                        }
                    }
                }
            }



            // Venue info
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PureWhite,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = TextTertiary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = match.venue,
                                style = SportFlowTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PureWhite,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SportTypeChip(sportType = match.sportType)
                        }
                    }
                }
            }

            // Live Feed
            if (match.highlights.isNotEmpty()) {
                item { SectionHeader(title = "Match Timeline") }

                items(match.highlights) { highlight ->
                    TimelineItem(text = highlight)
                }
            }

            // Stats
            item {
                SectionHeader(title = "Match Stats")
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatTile(
                        icon = Icons.Filled.SportsSoccer,
                        value = "${match.scoreA + match.scoreB}",
                        label = "Total Goals",
                        accentColor = GnitsOrange,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        icon = Icons.Filled.Timer,
                        value = match.elapsedTime.ifEmpty { "--" },
                        label = "Elapsed",
                        accentColor = InfoBlue,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        icon = Icons.Filled.Stadium,
                        value = match.venue.split(" ").first(),
                        label = "Venue",
                        accentColor = LiveRed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// ── Match Tab ────────────────────────────────────────────────────────────────

@Composable
private fun MatchTab(
    match: Match,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) GnitsOrangeLight else PureWhite,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) GnitsOrange else CardBorder
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = match.sportType,
                style = SportFlowTheme.typography.labelSmall,
                color = if (isSelected) GnitsOrangeDark else TextTertiary
            )
            Text(
                text = "${match.teamA.take(3).uppercase()} vs ${match.teamB.take(3).uppercase()}",
                style = SportFlowTheme.typography.labelLarge,
                color = if (isSelected) TextPrimary else TextSecondary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

// ── Timeline Item ────────────────────────────────────────────────────────────

@Composable
private fun TimelineItem(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(GnitsOrange, CircleShape)
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(40.dp)
                    .background(CardBorder)
            )
        }
        SportCard(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(14.dp),
                style = SportFlowTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}
