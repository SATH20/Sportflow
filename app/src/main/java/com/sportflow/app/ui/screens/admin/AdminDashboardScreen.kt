package com.sportflow.app.ui.screens.admin

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.sportflow.app.data.model.*
import com.sportflow.app.ui.components.*
import com.sportflow.app.ui.theme.*
import com.sportflow.app.ui.viewmodel.AdminViewModel

@Composable
fun AdminDashboardScreen(
    navController: NavHostController,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Payments", "Tournaments", "Matches")

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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Admin Portal",
                            style = SportFlowTheme.typography.displayLarge,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Manage events & operations",
                            style = SportFlowTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier
                            .size(44.dp)
                            .background(OffWhite, CircleShape)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }
            }
        }

        // Stats Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatTile(
                    icon = Icons.Filled.Payment,
                    value = "${uiState.pendingPayments.size}",
                    label = "Pending",
                    accentColor = WarningAmber,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    icon = Icons.Filled.EmojiEvents,
                    value = "${uiState.tournaments.size}",
                    label = "Active",
                    accentColor = PlayoGreen,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    icon = Icons.Filled.SportsSoccer,
                    value = "${uiState.allMatches.size}",
                    label = "Matches",
                    accentColor = InfoBlue,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Tab Bar
        item {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = PureWhite,
                contentColor = TextPrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = PlayoGreen,
                        height = 3.dp
                    )
                },
                divider = {
                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                },
                modifier = Modifier.padding(horizontal = 0.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                style = SportFlowTheme.typography.labelLarge,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        selectedContentColor = PlayoGreen,
                        unselectedContentColor = TextTertiary
                    )
                }
            }
        }

        // Content
        when (selectedTab) {
            0 -> {
                item { SectionHeader(title = "Pending Verifications") }
                if (uiState.pendingPayments.isEmpty()) {
                    item { EmptyState("No pending payments") }
                } else {
                    items(uiState.pendingPayments) { payment ->
                        PaymentCard(
                            payment = payment,
                            onVerify = { viewModel.verifyPayment(payment.id) },
                            onReject = { viewModel.rejectPayment(payment.id) }
                        )
                    }
                }
            }
            1 -> {
                item { SectionHeader(title = "Tournament Management") }
                items(uiState.tournaments) { tournament ->
                    TournamentManagementCard(
                        tournament = tournament,
                        isGenerating = uiState.isGeneratingBracket,
                        onGenerateBracket = { viewModel.generateBracket(tournament.id) }
                    )
                }
            }
            2 -> {
                item { SectionHeader(title = "All Matches") }
                items(uiState.allMatches) { match ->
                    AdminMatchCard(match = match)
                }
            }
        }
    }

    uiState.successMessage?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearMessage()
        }
    }
}

// ── Payment Card ─────────────────────────────────────────────────────────────

@Composable
private fun PaymentCard(
    payment: Payment,
    onVerify: () -> Unit,
    onReject: () -> Unit
) {
    SportCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .animateContentSize(animationSpec = tween(300))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(WarningAmberBg, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Payment,
                            contentDescription = null,
                            tint = WarningAmber,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = payment.teamName,
                            style = SportFlowTheme.typography.headlineSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "TXN: ${payment.transactionId}",
                            style = SportFlowTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                }
                Text(
                    text = "₹${payment.amount.toInt()}",
                    style = SportFlowTheme.typography.headlineLarge,
                    color = PlayoGreen,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PillButton(
                    text = "Verify",
                    onClick = onVerify,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.CheckCircle,
                    containerColor = PlayoGreen
                )

                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LiveRed.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LiveRed)
                ) {
                    Icon(
                        Icons.Filled.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Reject",
                        style = SportFlowTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ── Tournament Management Card ───────────────────────────────────────────────

@Composable
private fun TournamentManagementCard(
    tournament: Tournament,
    isGenerating: Boolean,
    onGenerateBracket: () -> Unit
) {
    SportCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tournament.name,
                        style = SportFlowTheme.typography.headlineLarge,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${tournament.sport} · ${tournament.teams.size}/${tournament.maxTeams} teams",
                        style = SportFlowTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                StatusChip(
                    text = tournament.status.name.replace("_", " "),
                    color = when (tournament.status) {
                        TournamentStatus.REGISTRATION -> WarningAmber
                        TournamentStatus.IN_PROGRESS -> PlayoGreen
                        TournamentStatus.COMPLETED -> TextTertiary
                        TournamentStatus.CANCELLED -> LiveRed
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Prize Pool",
                        style = SportFlowTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                    Text(
                        text = tournament.prizePool,
                        style = SportFlowTheme.typography.headlineSmall,
                        color = PlayoGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (!tournament.bracketGenerated) {
                PillButton(
                    text = if (isGenerating) "Generating..." else "Generate Bracket",
                    onClick = onGenerateBracket,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isGenerating,
                    icon = Icons.Filled.AccountTree
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = PlayoGreenLight,
                    border = androidx.compose.foundation.BorderStroke(1.dp, PlayoGreen.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = PlayoGreenDark,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Bracket Generated",
                            style = SportFlowTheme.typography.labelLarge,
                            color = PlayoGreenDark,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ── Admin Match Card ─────────────────────────────────────────────────────────

@Composable
private fun AdminMatchCard(match: Match) {
    SportCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${match.teamA} vs ${match.teamB}",
                    style = SportFlowTheme.typography.headlineSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${match.sportType} · ${match.tournamentName}",
                    style = SportFlowTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }

            if (match.status == MatchStatus.LIVE || match.status == MatchStatus.COMPLETED) {
                Text(
                    text = "${match.scoreA} - ${match.scoreB}",
                    style = SportFlowTheme.typography.headlineLarge,
                    color = if (match.status == MatchStatus.LIVE) PlayoGreen else TextSecondary,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            StatusChip(
                text = match.status.name,
                color = when (match.status) {
                    MatchStatus.LIVE -> LiveRed
                    MatchStatus.UPCOMING -> InfoBlue
                    MatchStatus.COMPLETED -> PlayoGreen
                    MatchStatus.HALFTIME -> WarningAmber
                    MatchStatus.CANCELLED -> LiveRed
                }
            )
        }
    }
}

// ── Empty State ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(OffWhite, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Inbox,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = SportFlowTheme.typography.bodyMedium,
                color = TextTertiary
            )
        }
    }
}
