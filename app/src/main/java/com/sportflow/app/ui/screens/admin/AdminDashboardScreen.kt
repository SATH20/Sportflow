package com.sportflow.app.ui.screens.admin

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.sportflow.app.ui.viewmodel.CreateMatchForm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    navController: NavHostController,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val matchForm by viewModel.matchForm.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Create Match", "Live Scoring", "Matches", "Payments", "Tournaments")

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
                            text = "GNITS Control Room",
                            style = SportFlowTheme.typography.displayLarge,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Manage matches, scores & tournaments",
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
                    label = "Tournaments",
                    accentColor = GnitsOrange,
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
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = PureWhite,
                contentColor = TextPrimary,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = GnitsOrange,
                            height = 3.dp
                        )
                    }
                },
                divider = {
                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                },
                edgePadding = 16.dp
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
                        selectedContentColor = GnitsOrange,
                        unselectedContentColor = TextTertiary
                    )
                }
            }
        }

        // Content
        when (selectedTab) {
            0 -> {
                // ── CREATE MATCH TAB ─────────────────────────────────────
                item { SectionHeader(title = "Create New Match") }
                item { CreateMatchFormCard(matchForm, viewModel) }
            }
            1 -> {
                // ── LIVE SCORING TAB ─────────────────────────────────────
                item { SectionHeader(title = "Live Scoring Engine") }
                if (uiState.selectedMatch != null) {
                    item { LiveScoringPanel(uiState.selectedMatch!!, viewModel) }
                } else {
                    // Show live/scheduled matches to select
                    val scorableMatches = uiState.allMatches.filter {
                        it.status == MatchStatus.SCHEDULED || it.status == MatchStatus.LIVE || it.status == MatchStatus.HALFTIME
                    }
                    if (scorableMatches.isEmpty()) {
                        item { EmptyState("No active matches to score") }
                    } else {
                        item {
                            Text(
                                text = "Select a match to start scoring:",
                                style = SportFlowTheme.typography.bodyMedium,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }
                        items(scorableMatches) { match ->
                            SelectableMatchCard(
                                match = match,
                                onClick = { viewModel.selectMatchForScoring(match) }
                            )
                        }
                    }
                }
            }
            2 -> {
                // ── ALL MATCHES TAB ──────────────────────────────────────
                item { SectionHeader(title = "All GNITS Matches") }
                if (uiState.allMatches.isEmpty()) {
                    item { EmptyState("No matches created yet") }
                } else {
                    items(uiState.allMatches) { match ->
                        AdminMatchCard(match = match)
                    }
                }
            }
            3 -> {
                // ── PAYMENTS TAB ─────────────────────────────────────────
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
            4 -> {
                // ── TOURNAMENTS TAB ──────────────────────────────────────
                item { SectionHeader(title = "Tournament Management") }
                items(uiState.tournaments) { tournament ->
                    TournamentManagementCard(
                        tournament = tournament,
                        isGenerating = uiState.isGeneratingBracket,
                        onGenerateBracket = { viewModel.generateBracket(tournament.id) }
                    )
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

// ── CREATE MATCH FORM ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateMatchFormCard(
    form: CreateMatchForm,
    viewModel: AdminViewModel
) {
    val sportTypes = listOf("Football", "Cricket", "Basketball", "Badminton", "Volleyball", "Table Tennis", "Kabaddi", "Athletics")
    val venues = GnitsVenue.allNames
    val departments = GnitsDepartment.entries

    var sportExpanded by remember { mutableStateOf(false) }
    var venueExpanded by remember { mutableStateOf(false) }
    var deptAExpanded by remember { mutableStateOf(false) }
    var deptBExpanded by remember { mutableStateOf(false) }

    SportCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .animateContentSize(animationSpec = tween(300))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Sport Type Dropdown
            ExposedDropdownMenuBox(
                expanded = sportExpanded,
                onExpandedChange = { sportExpanded = !sportExpanded }
            ) {
                OutlinedTextField(
                    value = form.sportType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Sport Type *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sportExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(14.dp)
                )
                ExposedDropdownMenu(
                    expanded = sportExpanded,
                    onDismissRequest = { sportExpanded = false }
                ) {
                    sportTypes.forEach { sport ->
                        DropdownMenuItem(
                            text = { Text(sport) },
                            onClick = {
                                viewModel.updateMatchForm(form.copy(sportType = sport))
                                sportExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Team A
            OutlinedTextField(
                value = form.teamA,
                onValueChange = { viewModel.updateMatchForm(form.copy(teamA = it)) },
                label = { Text("Team A Name *") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Team A Department
            ExposedDropdownMenuBox(
                expanded = deptAExpanded,
                onExpandedChange = { deptAExpanded = !deptAExpanded }
            ) {
                OutlinedTextField(
                    value = form.teamADepartment,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Team A Department") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deptAExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(14.dp)
                )
                ExposedDropdownMenu(
                    expanded = deptAExpanded,
                    onDismissRequest = { deptAExpanded = false }
                ) {
                    departments.forEach { dept ->
                        DropdownMenuItem(
                            text = { Text("${dept.name} — ${dept.displayName}") },
                            onClick = {
                                viewModel.updateMatchForm(form.copy(teamADepartment = dept.name))
                                deptAExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Team B
            OutlinedTextField(
                value = form.teamB,
                onValueChange = { viewModel.updateMatchForm(form.copy(teamB = it)) },
                label = { Text("Team B Name *") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Team B Department
            ExposedDropdownMenuBox(
                expanded = deptBExpanded,
                onExpandedChange = { deptBExpanded = !deptBExpanded }
            ) {
                OutlinedTextField(
                    value = form.teamBDepartment,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Team B Department") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deptBExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(14.dp)
                )
                ExposedDropdownMenu(
                    expanded = deptBExpanded,
                    onDismissRequest = { deptBExpanded = false }
                ) {
                    departments.forEach { dept ->
                        DropdownMenuItem(
                            text = { Text("${dept.name} — ${dept.displayName}") },
                            onClick = {
                                viewModel.updateMatchForm(form.copy(teamBDepartment = dept.name))
                                deptBExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Venue Dropdown — GNITS campus venues
            ExposedDropdownMenuBox(
                expanded = venueExpanded,
                onExpandedChange = { venueExpanded = !venueExpanded }
            ) {
                OutlinedTextField(
                    value = form.venue,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Venue *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = venueExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(14.dp)
                )
                ExposedDropdownMenu(
                    expanded = venueExpanded,
                    onDismissRequest = { venueExpanded = false }
                ) {
                    venues.forEach { venue ->
                        DropdownMenuItem(
                            text = { Text(venue) },
                            onClick = {
                                viewModel.updateMatchForm(form.copy(venue = venue))
                                venueExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Optional fields
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = form.tournamentName,
                    onValueChange = { viewModel.updateMatchForm(form.copy(tournamentName = it)) },
                    label = { Text("Tournament") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = form.round,
                    onValueChange = { viewModel.updateMatchForm(form.copy(round = it)) },
                    label = { Text("Round") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            PillButton(
                text = "Create Match",
                onClick = { viewModel.createMatch() },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Filled.Add,
                containerColor = GnitsOrange
            )
        }
    }
}

// ── LIVE SCORING PANEL ───────────────────────────────────────────────────────

@Composable
private fun LiveScoringPanel(
    match: Match,
    viewModel: AdminViewModel
) {
    var highlightText by remember { mutableStateOf("") }

    SportCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Match info header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip(
                    text = match.status.name,
                    color = when (match.status) {
                        MatchStatus.LIVE -> LiveRed
                        MatchStatus.SCHEDULED -> InfoBlue
                        MatchStatus.HALFTIME -> WarningAmber
                        MatchStatus.COMPLETED -> SuccessGreen
                        MatchStatus.CANCELLED -> ErrorRed
                    }
                )
                TextButton(onClick = { viewModel.deselectMatch() }) {
                    Text("Deselect", color = TextTertiary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Score Display
            ScoreDisplay(
                teamAName = match.teamA,
                teamBName = match.teamB,
                scoreA = match.scoreA,
                scoreB = match.scoreB,
                isLive = match.status == MatchStatus.LIVE
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Score Controls — +/- buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Team A controls
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = match.teamA,
                        style = SportFlowTheme.typography.labelLarge,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledIconButton(
                            onClick = { viewModel.decrementScoreA() },
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = LiveRedBg,
                                contentColor = LiveRed
                            ),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = "Decrement A")
                        }
                        FilledIconButton(
                            onClick = { viewModel.incrementScoreA() },
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = GnitsOrangeLight,
                                contentColor = GnitsOrangeDark
                            ),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Increment A")
                        }
                    }
                }

                // Team B controls
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = match.teamB,
                        style = SportFlowTheme.typography.labelLarge,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledIconButton(
                            onClick = { viewModel.decrementScoreB() },
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = LiveRedBg,
                                contentColor = LiveRed
                            ),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = "Decrement B")
                        }
                        FilledIconButton(
                            onClick = { viewModel.incrementScoreB() },
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = GnitsOrangeLight,
                                contentColor = GnitsOrangeDark
                            ),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Increment B")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Add Highlight
            OutlinedTextField(
                value = highlightText,
                onValueChange = { highlightText = it },
                label = { Text("Add match highlight") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                trailingIcon = {
                    IconButton(onClick = {
                        viewModel.addHighlight(highlightText)
                        highlightText = ""
                    }) {
                        Icon(Icons.Filled.Send, contentDescription = "Send", tint = GnitsOrange)
                    }
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = CardBorder)
            Spacer(modifier = Modifier.height(16.dp))

            // Match Lifecycle Controls
            Text(
                text = "Match Lifecycle",
                style = SportFlowTheme.typography.headlineSmall,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (match.status) {
                    MatchStatus.SCHEDULED -> {
                        PillButton(
                            text = "Start Match",
                            onClick = { viewModel.startMatch() },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.PlayArrow,
                            containerColor = SuccessGreen
                        )
                    }
                    MatchStatus.LIVE -> {
                        PillButton(
                            text = "Half Time",
                            onClick = { viewModel.halfTime() },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.Pause,
                            containerColor = WarningAmber
                        )
                        PillButton(
                            text = "End Match",
                            onClick = { viewModel.completeMatch() },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.Stop,
                            containerColor = LiveRed
                        )
                    }
                    MatchStatus.HALFTIME -> {
                        PillButton(
                            text = "Resume",
                            onClick = { viewModel.resumeMatch() },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.PlayArrow,
                            containerColor = SuccessGreen
                        )
                        PillButton(
                            text = "End Match",
                            onClick = { viewModel.completeMatch() },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.Stop,
                            containerColor = LiveRed
                        )
                    }
                    else -> {
                        Text(
                            text = "Match is ${match.status.name}",
                            style = SportFlowTheme.typography.bodyMedium,
                            color = TextTertiary
                        )
                    }
                }
            }
        }
    }
}

// ── Selectable Match Card ────────────────────────────────────────────────────

@Composable
private fun SelectableMatchCard(
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
                    text = "${match.sportType} · ${match.venue}",
                    style = SportFlowTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
            StatusChip(
                text = match.status.name,
                color = when (match.status) {
                    MatchStatus.LIVE -> LiveRed
                    MatchStatus.SCHEDULED -> InfoBlue
                    MatchStatus.HALFTIME -> WarningAmber
                    MatchStatus.COMPLETED -> SuccessGreen
                    MatchStatus.CANCELLED -> ErrorRed
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "Select",
                tint = GnitsOrange
            )
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
                    color = GnitsOrange,
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
                    containerColor = SuccessGreen
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
                        TournamentStatus.IN_PROGRESS -> GnitsOrange
                        TournamentStatus.COMPLETED -> TextTertiary
                        TournamentStatus.CANCELLED -> LiveRed
                    }
                )
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
                    color = GnitsOrangeLight,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GnitsOrange.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = GnitsOrangeDark,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Bracket Generated",
                            style = SportFlowTheme.typography.labelLarge,
                            color = GnitsOrangeDark,
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
                    text = "${match.sportType} · ${match.venue}",
                    style = SportFlowTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }

            if (match.status == MatchStatus.LIVE || match.status == MatchStatus.COMPLETED) {
                Text(
                    text = "${match.scoreA} - ${match.scoreB}",
                    style = SportFlowTheme.typography.headlineLarge,
                    color = if (match.status == MatchStatus.LIVE) GnitsOrange else TextSecondary,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            StatusChip(
                text = match.status.name,
                color = when (match.status) {
                    MatchStatus.LIVE -> LiveRed
                    MatchStatus.SCHEDULED -> InfoBlue
                    MatchStatus.COMPLETED -> SuccessGreen
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
