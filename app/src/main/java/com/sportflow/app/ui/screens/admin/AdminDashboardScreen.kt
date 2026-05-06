@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.google.firebase.Timestamp
import com.sportflow.app.data.model.*
import com.sportflow.app.ui.components.*
import com.sportflow.app.ui.theme.*
import com.sportflow.app.ui.viewmodel.AdminViewModel
import com.sportflow.app.ui.viewmodel.AdminUiState
import com.sportflow.app.ui.viewmodel.CreateMatchForm
import com.sportflow.app.ui.viewmodel.CreateTournamentForm
import com.sportflow.app.ui.viewmodel.CreateTournamentMatchForm
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    navController: NavHostController,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val matchForm by viewModel.matchForm.collectAsStateWithLifecycle()
    val tournamentForm by viewModel.tournamentForm.collectAsStateWithLifecycle()
    val fixtureConfig by viewModel.fixtureConfig.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    // Hoisted here (not inside LazyListScope) so rememberSaveable has a valid @Composable context
    var selectedReg by rememberSaveable { mutableStateOf<String?>(null) }
    var regFilter by rememberSaveable { mutableStateOf("ALL") }
    var denyTargetReg by rememberSaveable { mutableStateOf<String?>(null) }
    var denyReason by rememberSaveable { mutableStateOf("") }
    val tabs = listOf(
        "Create Tournament", "Live Scoring", "Create Match",
        "Create Fixtures", "Manual Editor", "Tournaments", "Broadcast Center", "Registration Approvals"
    )

    // Broadcast dialog state
    var showBroadcastDialog by remember { mutableStateOf(false) }
    var broadcastTitle by remember { mutableStateOf("") }
    var broadcastBody by remember { mutableStateOf("") }
    var broadcastTarget by remember { mutableStateOf("All Users") }
    var broadcastMatchId by remember { mutableStateOf("") }
    val targetOptions = listOf("All Users") + SportType.entries.map { it.display }

    Box(modifier = Modifier.fillMaxSize()) {
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
                                text = "Admin Dashboard",
                                style = SportFlowTheme.typography.displayLarge,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Tournament creation, match management, and registration approvals",
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
                        icon = Icons.Filled.HowToReg,
                        value = "${uiState.registrations.count { it.status == RegistrationStatus.PENDING }}",
                        label = "Approvals",
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
                    StatTile(
                        icon = Icons.Filled.HowToReg,
                        value = "${uiState.registrations.size}",
                        label = "Entries",
                        accentColor = SuccessGreen,
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = title,
                                        style = SportFlowTheme.typography.labelLarge,
                                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                    )
                                    // "New Entry" badge for Registrations tab
                                    if (title == "Registration Approvals" && uiState.newRegistrationCount > 0) {
                                        Badge(
                                            containerColor = LiveRed,
                                            contentColor = Color.White
                                        ) {
                                            Text(
                                                text = if (uiState.newRegistrationCount > 9) "9+" else "${uiState.newRegistrationCount}",
                                                style = SportFlowTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
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
                    item { SectionHeader(title = "Create New Tournament") }
                    item { CreateTournamentFormCard(tournamentForm, viewModel) }
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
                    // ── MANUAL FIXTURE ENGINE TAB ────────────────────────────
                    item { SectionHeader(title = "Create Match Under Tournament") }
                    item { CreateTournamentMatchFormCard(uiState, viewModel) }
                }

                3 -> {
                    // ── CREATE FIXTURES TAB ──────────────────────────────────
                    item { SectionHeader(title = "Create Fixtures Between Registered Users") }
                    item { CreateFixturesCard(uiState, viewModel) }
                }

                4 -> {
                    // ── MANUAL FIXTURE EDITOR TAB ────────────────────────────
                    item { SectionHeader(title = "✏️ Manual Fixture Editor") }
                    val scheduledMatches =
                        uiState.allMatches.filter { it.status == MatchStatus.SCHEDULED }
                    if (scheduledMatches.isEmpty()) {
                        item { EmptyState("No scheduled matches to edit") }
                    } else {
                        items(scheduledMatches) { match ->
                            ManualFixtureEditorCard(match = match, viewModel = viewModel)
                        }
                    }
                }

                5 -> {
                    // ── TOURNAMENTS TAB ──────────────────────────────────────
                    item { SectionHeader(title = "Tournament Management") }
                    if (uiState.tournaments.isEmpty()) {
                        item { EmptyState("No tournaments created yet") }
                    } else {
                        // Display tournaments only (matches moved to Manual Editor)
                        items(uiState.tournaments, key = { it.id }) { tournament ->
                            TournamentManagementCard(
                                tournament = tournament,
                                isGenerating = uiState.isGeneratingBracket,
                                onGenerateBracket = { viewModel.generateBracket(tournament.id) }
                            )
                        }
                    }
                }

                6 -> {
                    item {
                        SectionHeader(
                            title = "Admin Broadcast Center",
                            actionText = "New Broadcast",
                            onAction = { showBroadcastDialog = true }
                        )
                    }
                    item {
                        AdminBroadcastOverview(
                            updates = uiState.broadcastUpdates,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                        )
                    }
                    if (uiState.broadcastUpdates.isEmpty()) {
                        item { EmptyState("No broadcasts saved yet") }
                    } else {
                        items(uiState.broadcastUpdates, key = { it.id }) { update ->
                            AdminBroadcastCard(
                                update = update,
                                onDelete = { viewModel.deleteBroadcastUpdate(update.id) }
                            )
                        }
                    }
                }

                7 -> {
                    // ── REGISTRATIONS TAB (Admin Data Bridge) ───────────────────
                    // selectedReg and regFilter are hoisted to the parent Composable scope

                    item {
                        SectionHeader(
                            title = "Registration Approvals",
                            actionText = if (uiState.newRegistrationCount > 0) "Mark All Seen" else null,
                            onAction = { viewModel.markAllRegistrationsSeen() }
                        )
                    }

                    // Filter chips
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("ALL", "CONFIRMED", "PENDING").forEach { filter ->
                                FilterChip(
                                    selected = regFilter == filter,
                                    onClick = { regFilter = filter },
                                    label = {
                                        Text(
                                            filter.lowercase().replaceFirstChar { it.uppercase() })
                                    },
                                    leadingIcon = if (regFilter == filter) {
                                        { Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }
                    }

                    val fixtureReadyMatchId = uiState.registrations
                        .filter { it.status == com.sportflow.app.data.model.RegistrationStatus.CONFIRMED }
                        .groupBy { it.tournamentId.ifBlank { it.matchId } }
                        .entries
                        .firstOrNull { it.value.size >= 2 }
                        ?.key
                    if (fixtureReadyMatchId != null) {
                        item {
                            PillButton(
                                text = "Generate Tournament Matches",
                                onClick = {
                                    if (uiState.tournaments.any { it.id == fixtureReadyMatchId }) {
                                        viewModel.generateFixturesFromApprovedTournamentRegistrations(
                                            fixtureReadyMatchId
                                        )
                                    } else {
                                        viewModel.generateFixturesFromApprovedRegistrations(
                                            fixtureReadyMatchId
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                icon = Icons.Filled.AutoAwesome,
                                containerColor = GnitsOrange
                            )
                        }
                    }

                    // Show detail card if a registration is selected
                    if (selectedReg != null) {
                        val reg = uiState.registrations.find { it.id == selectedReg }
                        if (reg != null) {
                            item {
                                RegistrationDetailCard(
                                    registration = reg,
                                    onDismiss = { selectedReg = null },
                                    onAccept = {
                                        viewModel.acceptRegistration(reg.id)
                                        selectedReg = null
                                    },
                                    onDeny = {
                                        denyTargetReg = reg.id
                                    }
                                )
                            }
                        }
                    }

                    val filteredRegs = when (regFilter) {
                        "CONFIRMED" -> uiState.registrations.filter {
                            it.status == com.sportflow.app.data.model.RegistrationStatus.CONFIRMED
                        }

                        "PENDING" -> uiState.registrations.filter {
                            it.status == com.sportflow.app.data.model.RegistrationStatus.PENDING
                        }

                        else -> uiState.registrations
                    }

                    if (filteredRegs.isEmpty()) {
                        item { EmptyState("No registrations found") }
                    } else {
                        items(filteredRegs, key = { it.id }) { reg ->
                            AdminRegistrationCard(
                                registration = reg,
                                onClick = {
                                    viewModel.markRegistrationAsSeen(reg.id)
                                    selectedReg = if (selectedReg == reg.id) null else reg.id
                                }
                            )
                        }
                    }

                }
            }
        }
        
        // Floating Action Button for Manual Broadcast
        FloatingActionButton(
            onClick = { showBroadcastDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = GnitsOrange,
            contentColor = Color.White
        ) {
            Icon(
                Icons.Filled.Campaign,
                contentDescription = "Broadcast Message",
                modifier = Modifier.size(28.dp)
            )
        }
        
        // Broadcast Dialog
        if (showBroadcastDialog) {
            BroadcastDialog(
                title = broadcastTitle,
                body = broadcastBody,
                target = broadcastTarget,
                attachedMatchId = broadcastMatchId,
                availableMatches = uiState.allMatches,
                targetOptions = targetOptions,
                onTitleChange = { broadcastTitle = it },
                onBodyChange = { broadcastBody = it },
                onTargetChange = { broadcastTarget = it },
                onAttachedMatchChange = { broadcastMatchId = it },
                onDismiss = {
                    showBroadcastDialog = false
                    broadcastTitle = ""
                    broadcastBody = ""
                    broadcastTarget = "All Users"
                    broadcastMatchId = ""
                },
                onSend = {
                    val targetSport = if (broadcastTarget == "All Users") "" else broadcastTarget
                    viewModel.sendBroadcastUpdate(
                        title = broadcastTitle,
                        body = broadcastBody,
                        targetSport = targetSport,
                        attachedMatchId = broadcastMatchId
                    )
                    showBroadcastDialog = false
                    broadcastTitle = ""
                    broadcastBody = ""
                    broadcastTarget = "All Users"
                    broadcastMatchId = ""
                }
            )
        }
    }

    if (denyTargetReg != null) {
            AlertDialog(
                onDismissRequest = {
                    denyTargetReg = null
                    denyReason = ""
                },
                title = { Text("Deny Registration") },
                text = {
                    OutlinedTextField(
                        value = denyReason,
                        onValueChange = { denyReason = it },
                        label = { Text("Reason") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            denyTargetReg?.let {
                                viewModel.denyRegistration(
                                    it,
                                    denyReason.ifBlank { "No reason provided" })
                            }
                            denyTargetReg = null
                            denyReason = ""
                            selectedReg = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LiveRed)
                    ) {
                        Text("Deny")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        denyTargetReg = null
                        denyReason = ""
                    }) {
                        Text("Cancel")
                    }
                }
            )
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
    private fun CreateTournamentFormCard(
        form: CreateTournamentForm,
        viewModel: AdminViewModel
    ) {
        val sportTypes = listOf(
            "Football",
            "Cricket",
            "Basketball",
            "Badminton",
            "Volleyball",
            "Table Tennis",
            "Kabaddi",
            "Athletics"
        )
        val venues = GnitsVenue.allNames
        var sportExpanded by remember { mutableStateOf(false) }
        var venueExpanded by remember { mutableStateOf(false) }

        SportCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { viewModel.updateTournamentForm(form.copy(name = it)) },
                    label = { Text("Tournament Name *") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )

                ExposedDropdownMenuBox(
                    expanded = sportExpanded,
                    onExpandedChange = { sportExpanded = !sportExpanded }) {
                    OutlinedTextField(
                        value = form.sport,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Sport *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sportExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(14.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = sportExpanded,
                        onDismissRequest = { sportExpanded = false }) {
                        sportTypes.forEach { sport ->
                            DropdownMenuItem(
                                text = { Text(sport) },
                                onClick = {
                                    viewModel.updateTournamentForm(form.copy(sport = sport))
                                    sportExpanded = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = venueExpanded,
                    onExpandedChange = { venueExpanded = !venueExpanded }) {
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
                        onDismissRequest = { venueExpanded = false }) {
                        venues.forEach { venue ->
                            DropdownMenuItem(
                                text = { Text(venue) },
                                onClick = {
                                    viewModel.updateTournamentForm(form.copy(venue = venue))
                                    venueExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = form.startDateText,
                        onValueChange = { viewModel.updateTournamentForm(form.copy(startDateText = it)) },
                        label = { Text("Start (dd/MM/yyyy HH:mm)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = form.endDateText,
                        onValueChange = { viewModel.updateTournamentForm(form.copy(endDateText = it)) },
                        label = { Text("End (dd/MM/yyyy HH:mm)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = form.maxTeams,
                        onValueChange = { value ->
                            viewModel.updateTournamentForm(form.copy(maxTeams = value.filter { it.isDigit() }))
                        },
                        label = { Text("Max Teams") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = form.prizePool,
                        onValueChange = { viewModel.updateTournamentForm(form.copy(prizePool = it)) },
                        label = { Text("Prize / Note") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = form.eligibilityText,
                    onValueChange = { viewModel.updateTournamentForm(form.copy(eligibilityText = it)) },
                    label = { Text("Eligibility") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    minLines = 2
                )

                PillButton(
                    text = "Create Tournament",
                    onClick = { viewModel.createTournament() },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.EmojiEvents,
                    containerColor = GnitsOrange
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun CreateTournamentMatchFormCard(
        uiState: AdminUiState,
        viewModel: AdminViewModel
    ) {
        val form by viewModel.tournamentMatchForm.collectAsStateWithLifecycle()
        val venues = GnitsVenue.allNames

        var tournamentExpanded by remember { mutableStateOf(false) }
        var venueExpanded by remember { mutableStateOf(false) }
        var teamAExpanded by remember { mutableStateOf(false) }
        var teamBExpanded by remember { mutableStateOf(false) }

        // Get approved teams for the selected tournament
        val approvedTeams = remember(form.selectedTournamentId, uiState.registrations) {
            if (form.selectedTournamentId.isBlank()) {
                emptyList<String>()
            } else {
                uiState.registrations
                    .filter { reg -> reg.tournamentId == form.selectedTournamentId && reg.status == RegistrationStatus.CONFIRMED }
                    .map { reg -> reg.fixtureUnitName.ifBlank { reg.teamName.ifBlank { reg.userName } } }
                    .distinct()
                    .sorted()
            }
        }

        SportCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Tournament Selection
                ExposedDropdownMenuBox(
                    expanded = tournamentExpanded,
                    onExpandedChange = { tournamentExpanded = !tournamentExpanded }
                ) {
                    OutlinedTextField(
                        value = uiState.tournaments.find { t -> t.id == form.selectedTournamentId }?.name
                            ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Tournament *") },
                        placeholder = { Text("Choose a tournament") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tournamentExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(14.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = tournamentExpanded,
                        onDismissRequest = { tournamentExpanded = false }
                    ) {
                        uiState.tournaments.forEach { tournament ->
                            DropdownMenuItem(
                                text = { Text("${tournament.name} (${tournament.sport})") },
                                onClick = {
                                    viewModel.updateTournamentMatchForm(
                                        form.copy(
                                            selectedTournamentId = tournament.id,
                                            teamA = "",
                                            teamB = ""
                                        )
                                    )
                                    tournamentExpanded = false
                                }
                            )
                        }
                    }
                }

                // Show warning if not enough teams
                if (form.selectedTournamentId.isNotBlank() && approvedTeams.size < 2) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = WarningAmber.copy(alpha = 0.1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, WarningAmber)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = WarningAmber,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Not enough teams available",
                                    style = SportFlowTheme.typography.labelLarge,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "At least 2 approved teams are required. Currently: ${approvedTeams.size} team(s).",
                                    style = SportFlowTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                // Show form or message based on tournament selection
                if (form.selectedTournamentId.isNotBlank()) {
                    if (approvedTeams.isEmpty()) {
                        Text(
                            text = "No approved teams for this tournament yet. Approve registrations first.",
                            style = SportFlowTheme.typography.bodySmall,
                            color = WarningAmber,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Text(
                            text = "${approvedTeams.size} approved team(s) available",
                            style = SportFlowTheme.typography.bodySmall,
                            color = SuccessGreen,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        // Team A Selection
                        ExposedDropdownMenuBox(
                            expanded = teamAExpanded,
                            onExpandedChange = { teamAExpanded = !teamAExpanded }
                        ) {
                            OutlinedTextField(
                                value = form.teamA,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Team A *") },
                                placeholder = { Text("Select first team") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = teamAExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                shape = RoundedCornerShape(14.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = teamAExpanded,
                                onDismissRequest = { teamAExpanded = false }
                            ) {
                                approvedTeams.forEach { team ->
                                    DropdownMenuItem(
                                        text = { Text(team) },
                                        onClick = {
                                            viewModel.updateTournamentMatchForm(form.copy(teamA = team))
                                            teamAExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Team B Selection
                        ExposedDropdownMenuBox(
                            expanded = teamBExpanded,
                            onExpandedChange = { teamBExpanded = !teamBExpanded }
                        ) {
                            OutlinedTextField(
                                value = form.teamB,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Team B *") },
                                placeholder = { Text("Select second team") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = teamBExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                shape = RoundedCornerShape(14.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = teamBExpanded,
                                onDismissRequest = { teamBExpanded = false }
                            ) {
                                approvedTeams.forEach { team ->
                                    DropdownMenuItem(
                                        text = { Text(team) },
                                        onClick = {
                                            viewModel.updateTournamentMatchForm(form.copy(teamB = team))
                                            teamBExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Venue Selection
                        ExposedDropdownMenuBox(
                            expanded = venueExpanded,
                            onExpandedChange = { venueExpanded = !venueExpanded }
                        ) {
                            OutlinedTextField(
                                value = form.venue,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Venue *") },
                                placeholder = { Text("Select venue") },
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
                                            viewModel.updateTournamentMatchForm(form.copy(venue = venue))
                                            venueExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Scheduled Date/Time
                        OutlinedTextField(
                            value = form.scheduledDateText,
                            onValueChange = {
                                viewModel.updateTournamentMatchForm(
                                    form.copy(
                                        scheduledDateText = it
                                    )
                                )
                            },
                            label = { Text("Scheduled Time (dd/MM/yyyy HH:mm) *") },
                            placeholder = { Text("e.g., 25/12/2024 14:30") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true
                        )

                        // Round
                        OutlinedTextField(
                            value = form.round,
                            onValueChange = { viewModel.updateTournamentMatchForm(form.copy(round = it)) },
                            label = { Text("Round") },
                            placeholder = { Text("e.g., Round 1, Quarter Final") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true
                        )

                        PillButton(
                            text = "Create Match",
                            onClick = { viewModel.createTournamentMatch() },
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Filled.SportsSoccer,
                            containerColor = GnitsOrange,
                            enabled = !uiState.isCreatingMatch
                        )
                    }
                } else {
                    Text(
                        text = "Select a tournament to begin creating matches",
                        style = SportFlowTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun CreateMatchFormCard(
        form: CreateMatchForm,
        viewModel: AdminViewModel
    ) {
        val sportTypes = listOf(
            "Football",
            "Cricket",
            "Basketball",
            "Badminton",
            "Volleyball",
            "Table Tennis",
            "Kabaddi",
            "Athletics"
        )
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

// ── SPORT-SPECIFIC LIVE SCORING PANEL ───────────────────────────────────────

    @Composable
    private fun LiveScoringPanel(
        match: Match,
        viewModel: AdminViewModel
    ) {
        val score = SportScoreEngine.formatScore(match)
        var highlightText by remember { mutableStateOf("") }
        val sportType = SportType.fromString(match.sportType)

        SportCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Header row ────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
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
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                match.sportType,
                                style = SportFlowTheme.typography.labelMedium,
                                color = GnitsOrange
                            )
                            Text("·", color = TextTertiary)
                            Text(
                                match.venue,
                                style = SportFlowTheme.typography.labelMedium,
                                color = TextTertiary
                            )
                        }
                    }
                    TextButton(onClick = { viewModel.deselectMatch() }) {
                        Text("Deselect", color = TextTertiary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Match Not Started Warning ──────────────────────────────────
                if (match.status == MatchStatus.SCHEDULED) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = WarningAmber.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, WarningAmber)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = WarningAmber
                            )
                            Text(
                                text = "Match Must Be Started",
                                style = SportFlowTheme.typography.headlineSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Scoring is locked until the match is started. Click 'Start Match' below to begin live scoring.",
                                style = SportFlowTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            PillButton(
                                text = "Start Match Now",
                                onClick = { viewModel.startMatch() },
                                icon = Icons.Filled.PlayArrow,
                                containerColor = SuccessGreen,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    return@Column
                }

                // ── Match Completed Warning ────────────────────────────────────
                if (match.status == MatchStatus.COMPLETED) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = SuccessGreen.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = SuccessGreen
                            )
                            Text(
                                text = "Match Completed",
                                style = SportFlowTheme.typography.headlineSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "This match has been finalized. Scoring is read-only. To make changes, revert to LIVE status first.",
                                style = SportFlowTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            
                            // Show final score
                            val sport = SportType.fromString(match.sportType)
                            val finalScoreA = when (sport) {
                                SportType.BADMINTON, SportType.VOLLEYBALL, SportType.TABLE_TENNIS -> match.setsWonA
                                else -> match.scoreA
                            }
                            val finalScoreB = when (sport) {
                                SportType.BADMINTON, SportType.VOLLEYBALL, SportType.TABLE_TENNIS -> match.setsWonB
                                else -> match.scoreB
                            }
                            
                            Text(
                                text = "${match.teamA} $finalScoreA - $finalScoreB ${match.teamB}",
                                style = SportFlowTheme.typography.displayMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            
                            if (match.winnerId.isNotBlank() && match.winnerId != "DRAW") {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.EmojiEvents,
                                        contentDescription = null,
                                        tint = GnitsOrange,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Winner: ${match.winnerId}",
                                        style = SportFlowTheme.typography.labelLarge,
                                        color = GnitsOrange,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    return@Column
                }

                // ── Sport-specific score header ────────────────────────────────
                SportSpecificScoreHeader(match = match, score = score, sportType = sportType)

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = CardBorder)
                Spacer(modifier = Modifier.height(16.dp))

                // ── Sport-specific scoring controls ───────────────────────────
                Text(
                    "Scoring Controls — ${match.sportType}",
                    style = SportFlowTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))

                when (sportType) {
                    SportType.CRICKET -> CricketScoringControls(match, viewModel)
                    SportType.BASKETBALL -> BasketballScoringControls(match, viewModel)
                    SportType.BADMINTON,
                    SportType.VOLLEYBALL,
                    SportType.TABLE_TENNIS -> SetBasedScoringControls(match, viewModel, sportType)

                    else -> GenericScoringControls(match, viewModel) // Football, Kabaddi, Athletics
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = CardBorder)
                Spacer(modifier = Modifier.height(16.dp))

                // ── Highlight input ───────────────────────────────────────────
                OutlinedTextField(
                    value = highlightText,
                    onValueChange = { highlightText = it },
                    label = { Text("Add match highlight / commentary") },
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

                // ── Match Lifecycle Controls ────────────────────────────────
                Text(
                    "Match Lifecycle",
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
                                "Match is ${match.status.name}",
                                style = SportFlowTheme.typography.bodyMedium,
                                color = TextTertiary
                            )
                            
                            // EMERGENCY FIX BUTTON for completed matches with wrong scores
                            if (match.status == MatchStatus.COMPLETED) {
                                Spacer(modifier = Modifier.height(8.dp))
                                PillButton(
                                    text = "🔧 Fix Scores",
                                    onClick = { viewModel.fixCompletedMatchScores() },
                                    modifier = Modifier.fillMaxWidth(),
                                    icon = Icons.Filled.Build,
                                    containerColor = WarningAmber
                                )
                                Text(
                                    "Use this if completed match shows 0-0",
                                    style = SportFlowTheme.typography.bodySmall,
                                    color = TextTertiary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

// ── Sport-specific score header display ──────────────────────────────────────

    @Composable
    private fun SportSpecificScoreHeader(match: Match, score: SportScore, sportType: SportType) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(OffWhite, RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        score.displayA,
                        style = SportFlowTheme.typography.displayLarge,
                        color = GnitsOrangeDark,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Center
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    if (score.centerText.isNotBlank()) {
                        Surface(shape = RoundedCornerShape(8.dp), color = GnitsOrangeLight) {
                            Text(
                                score.centerText,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = SportFlowTheme.typography.labelSmall,
                                color = GnitsOrangeDark,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text(
                            "VS",
                            style = SportFlowTheme.typography.displayMedium,
                            color = TextTertiary
                        )
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
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        score.displayB,
                        style = SportFlowTheme.typography.displayLarge,
                        color = InfoBlue,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Sub-info row
            if (score.subText.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    score.subText,
                    style = SportFlowTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            if (score.extras.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    score.extras,
                    style = SportFlowTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }
    }

// ── Cricket Scoring Controls ──────────────────────────────────────────────────

    @Composable
    private fun CricketScoringControls(match: Match, viewModel: AdminViewModel) {
        val battingTeam = if (match.currentInning == 1) "A" else "B"
        val battingName = if (match.currentInning == 1) match.teamA else match.teamB
        val oversField = if (match.currentInning == 1) match.oversA else match.oversB

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = GnitsOrangeLight,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "🏏 Batting: $battingName | Overs: $oversField",
                modifier = Modifier.padding(12.dp),
                style = SportFlowTheme.typography.labelMedium,
                color = GnitsOrangeDark
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        val actions = SportScoreEngine.getActionsForTeam(match.sportType, battingTeam)
        // Group run actions vs special actions
        val runActions = actions.filter { it.color != "danger" && it.actionType == "increment" }
        val extraActions = actions.filter { it.color == "warning" || it.actionType == "special" }
        val wicketActions = actions.filter { it.color == "danger" }

        Text("Runs", style = SportFlowTheme.typography.labelMedium, color = TextSecondary)
        Spacer(modifier = Modifier.height(8.dp))
        ScoringActionGrid(runActions, onAction = { viewModel.applyScoringAction(it) })

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Extras & Events",
            style = SportFlowTheme.typography.labelMedium,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        ScoringActionGrid(
            extraActions + wicketActions,
            onAction = { viewModel.applyScoringAction(it) })

        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { viewModel.advanceOver() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, GnitsOrange)
            ) {
                Icon(Icons.Filled.Timer, null, tint = GnitsOrange, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Over +1 Ball", color = GnitsOrange)
            }
            if (match.currentInning == 1) {
                OutlinedButton(
                    onClick = { viewModel.startSecondInning() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, InfoBlue)
                ) {
                    Text("2nd Innings", color = InfoBlue)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Undo button
        OutlinedButton(
            onClick = { viewModel.undoScore(battingTeam) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LiveRed)
        ) {
            Icon(Icons.Filled.Undo, null, tint = LiveRed, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Undo Last Run", color = LiveRed)
        }
    }

// ── Basketball Scoring Controls ───────────────────────────────────────────────

    @Composable
    private fun BasketballScoringControls(match: Match, viewModel: AdminViewModel) {
        val actionsA = SportScoreEngine.getActionsForTeam(match.sportType, "A")
        val actionsB = SportScoreEngine.getActionsForTeam(match.sportType, "B")
        val q = match.currentQuarter

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = InfoBlue.copy(alpha = 0.1f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "🏀 Q$q: ${match.teamA} ${match.q1A + match.q2A + match.q3A + match.q4A} – ${match.q1B + match.q2B + match.q3B + match.q4B} ${match.teamB}",
                modifier = Modifier.padding(12.dp),
                style = SportFlowTheme.typography.labelMedium,
                color = InfoBlue
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    match.teamA,
                    style = SportFlowTheme.typography.labelMedium,
                    color = TextPrimary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(6.dp))
                actionsA.filter { it.actionType == "increment" }.forEach { action ->
                    ScoringActionButton(
                        action,
                        fullWidth = true
                    ) { viewModel.applyScoringAction(it) }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                OutlinedButton(
                    onClick = { viewModel.undoScore("A") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LiveRed)
                ) { Text("Undo", color = LiveRed, style = SportFlowTheme.typography.labelSmall) }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    match.teamB,
                    style = SportFlowTheme.typography.labelMedium,
                    color = TextPrimary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(6.dp))
                actionsB.filter { it.actionType == "increment" }.forEach { action ->
                    ScoringActionButton(
                        action,
                        fullWidth = true
                    ) { viewModel.applyScoringAction(it) }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                OutlinedButton(
                    onClick = { viewModel.undoScore("B") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LiveRed)
                ) { Text("Undo", color = LiveRed, style = SportFlowTheme.typography.labelSmall) }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        PillButton(
            text = "End Quarter → Q${q + 1}",
            onClick = { viewModel.nextQuarter() },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Filled.FastForward,
            containerColor = InfoBlue
        )
    }

// ── Set-Based Sports (Badminton / Volleyball / Table Tennis) ─────────────────

    @Composable
    private fun SetBasedScoringControls(
        match: Match,
        viewModel: AdminViewModel,
        sportType: SportType
    ) {
        val actionsA = SportScoreEngine.getActionsForTeam(match.sportType, "A")
            .filter { it.actionType == "increment" }
        val actionsB = SportScoreEngine.getActionsForTeam(match.sportType, "B")
            .filter { it.actionType == "increment" }
        val pointsToWin = when (sportType) {
            SportType.VOLLEYBALL -> 25
            SportType.TABLE_TENNIS -> 11
            else -> 21 // Badminton
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = GnitsOrangeLight,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "Set ${match.currentSet} · ${match.currentSetScoreA} – ${match.currentSetScoreB}",
                    style = SportFlowTheme.typography.headlineMedium,
                    color = GnitsOrangeDark,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "First to $pointsToWin wins the set",
                    style = SportFlowTheme.typography.bodySmall,
                    color = GnitsOrange
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    match.teamA,
                    style = SportFlowTheme.typography.labelMedium,
                    color = TextPrimary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(6.dp))
                actionsA.forEach { action ->
                    ScoringActionButton(
                        action,
                        fullWidth = true
                    ) { viewModel.applyScoringAction(it) }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                PillButton(
                    text = "Set Won ✓",
                    onClick = { viewModel.completeSet("A") },
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = SuccessGreen
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    match.teamB,
                    style = SportFlowTheme.typography.labelMedium,
                    color = TextPrimary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(6.dp))
                actionsB.forEach { action ->
                    ScoringActionButton(
                        action,
                        fullWidth = true
                    ) { viewModel.applyScoringAction(it) }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                PillButton(
                    text = "Set Won ✓",
                    onClick = { viewModel.completeSet("B") },
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = InfoBlue
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Sets: ${match.teamA} ${match.setsWonA} – ${match.setsWonB} ${match.teamB}",
            style = SportFlowTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth()
        )
    }

// ── Generic (Football / Kabaddi / Athletics) ─────────────────────────────────

    @Composable
    private fun GenericScoringControls(match: Match, viewModel: AdminViewModel) {
        val actionsA = SportScoreEngine.getActionsForTeam(match.sportType, "A")
        val actionsB = SportScoreEngine.getActionsForTeam(match.sportType, "B")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    match.teamA,
                    style = SportFlowTheme.typography.labelMedium,
                    color = TextPrimary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(6.dp))
                actionsA.filter { it.actionType == "increment" }.forEach { action ->
                    ScoringActionButton(
                        action,
                        fullWidth = true
                    ) { viewModel.applyScoringAction(it) }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                OutlinedButton(
                    onClick = { viewModel.undoScore("A") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LiveRed)
                ) { Text("Undo", color = LiveRed, style = SportFlowTheme.typography.labelSmall) }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    match.teamB,
                    style = SportFlowTheme.typography.labelMedium,
                    color = TextPrimary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(6.dp))
                actionsB.filter { it.actionType == "increment" }.forEach { action ->
                    ScoringActionButton(
                        action,
                        fullWidth = true
                    ) { viewModel.applyScoringAction(it) }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                OutlinedButton(
                    onClick = { viewModel.undoScore("B") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LiveRed)
                ) { Text("Undo", color = LiveRed, style = SportFlowTheme.typography.labelSmall) }
            }
        }
    }

// ── Scoring Action Helpers ────────────────────────────────────────────────────

    @Composable
    private fun ScoringActionGrid(
        actions: List<ScoringAction>,
        onAction: (ScoringAction) -> Unit
    ) {
        val rows = actions.chunked(3)
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { action ->
                    ScoringActionButton(action, modifier = Modifier.weight(1f), onAction = onAction)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }

    @Composable
    private fun ScoringActionButton(
        action: ScoringAction,
        modifier: Modifier = Modifier,
        fullWidth: Boolean = false,
        onAction: (ScoringAction) -> Unit
    ) {
        val bgColor = when (action.color) {
            "danger" -> LiveRedBg
            "warning" -> WarningAmber.copy(alpha = 0.12f)
            else -> GnitsOrangeLight
        }
        val textColor = when (action.color) {
            "danger" -> LiveRed
            "warning" -> WarningAmber
            else -> GnitsOrangeDark
        }

        Surface(
            modifier = (if (fullWidth) modifier.fillMaxWidth() else modifier)
                .clickable { onAction(action) },
            shape = RoundedCornerShape(12.dp),
            color = bgColor,
            border = androidx.compose.foundation.BorderStroke(1.dp, textColor.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(action.emoji, style = SportFlowTheme.typography.headlineSmall)
                Text(
                    action.label,
                    style = SportFlowTheme.typography.labelSmall,
                    color = textColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
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
                    if (match.tournamentName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tournament: ${match.tournamentName}",
                            style = SportFlowTheme.typography.bodySmall,
                            color = GnitsOrange
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Scheduled: ${formatAdminDateTime(match.scheduledTime)}",
                        style = SportFlowTheme.typography.bodySmall,
                        color = TextSecondary
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
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${tournament.venue} • ${
                                formatTournamentRange(
                                    tournament.startDate,
                                    tournament.endDate
                                )
                            }",
                            style = SportFlowTheme.typography.bodySmall,
                            color = TextTertiary
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
                        text = if (isGenerating) "Generating..." else "Generate Tournament Matches",
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
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            GnitsOrange.copy(alpha = 0.3f)
                        )
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
                                text = "Tournament Generated",
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
            Column(modifier = Modifier.padding(14.dp)) {
                // Round badge
                if (match.round.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = GnitsOrangeLight,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = match.round,
                            style = SportFlowTheme.typography.labelSmall,
                            color = GnitsOrangeDark,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
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

    private fun formatAdminDateTime(timestamp: Timestamp?): String {
        if (timestamp == null) return "TBD"
        val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        return formatter.format(timestamp.toDate())
    }

    private fun formatTournamentRange(start: Timestamp?, end: Timestamp?): String {
        return when {
            start != null && end != null -> "From ${formatAdminDateTime(start)} to ${
                formatAdminDateTime(
                    end
                )
            }"

            start != null -> "Starts ${formatAdminDateTime(start)}"
            end != null -> "Ends ${formatAdminDateTime(end)}"
            else -> "Schedule TBD"
        }
    }

    private fun parseAdminScreenDateTime(raw: String): Timestamp? {
        return try {
            val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            formatter.isLenient = false
            val parsed = formatter.parse(raw.trim()) ?: return null
            Timestamp(parsed)
        } catch (_: Exception) {
            null
        }
    }

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// AI FIXTURE ENGINE CARD — Dual-Mode (Single Elimination / Round Robin)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AIFixtureEngineCard(
        config: FixtureConfig,
        viewModel: AdminViewModel,
        isGenerating: Boolean
    ) {
        val sportTypes = SportType.displayList
        val venues = GnitsVenue.allNames
        val tournamentTypes = TournamentType.entries

        var sportExpanded by remember { mutableStateOf(false) }
        var venueExpanded by remember { mutableStateOf(false) }
        var typeExpanded by remember { mutableStateOf(false) }
        var teamsRawText by remember { mutableStateOf(config.teams.joinToString("\n")) }

        SportCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .animateContentSize(animationSpec = tween(300))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Engine header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = InfoBlue.copy(alpha = 0.12f)
                    ) {
                        Text(
                            "🤖 AI",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = SportFlowTheme.typography.labelMedium,
                            color = InfoBlue, fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Deterministic Scheduling Engine",
                        style = SportFlowTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Tournament Name
                OutlinedTextField(
                    value = config.tournamentName,
                    onValueChange = { viewModel.updateFixtureConfig(config.copy(tournamentName = it)) },
                    label = { Text("Tournament Name *") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Tournament Type Dropdown
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = !typeExpanded }
                ) {
                    OutlinedTextField(
                        value = config.tournamentType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tournament Format *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(14.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        tournamentTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName) },
                                onClick = {
                                    viewModel.updateFixtureConfig(config.copy(tournamentType = type))
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Sport Type
                ExposedDropdownMenuBox(
                    expanded = sportExpanded,
                    onExpandedChange = { sportExpanded = !sportExpanded }
                ) {
                    OutlinedTextField(
                        value = config.sportType,
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
                                    viewModel.updateFixtureConfig(config.copy(sportType = sport))
                                    sportExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Venue
                ExposedDropdownMenuBox(
                    expanded = venueExpanded,
                    onExpandedChange = { venueExpanded = !venueExpanded }
                ) {
                    OutlinedTextField(
                        value = config.venue,
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
                                    viewModel.updateFixtureConfig(config.copy(venue = venue))
                                    venueExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Interval
                OutlinedTextField(
                    value = config.intervalMinutes.toString(),
                    onValueChange = {
                        val mins = it.toLongOrNull() ?: 60
                        viewModel.updateFixtureConfig(config.copy(intervalMinutes = mins))
                    },
                    label = { Text("Interval (minutes)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Teams — one per line
                OutlinedTextField(
                    value = teamsRawText,
                    onValueChange = { raw ->
                        teamsRawText = raw
                        val teams = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
                        viewModel.updateFixtureConfig(config.copy(teams = teams))
                    },
                    label = { Text("Teams (one per line) *") },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    shape = RoundedCornerShape(14.dp),
                    maxLines = 20,
                    placeholder = { Text("CSE XI\nIT Warriors\nECE Challengers\n...") }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${config.teams.size} teams entered",
                    style = SportFlowTheme.typography.bodySmall,
                    color = if (config.teams.size >= 2) SuccessGreen else TextTertiary
                )

                Spacer(modifier = Modifier.height(20.dp))

                PillButton(
                    text = if (isGenerating) "Generating..." else "🤖 Generate ${config.tournamentType.displayName} Fixtures",
                    onClick = { viewModel.generateAIFixtures() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isGenerating,
                    icon = Icons.Filled.AutoFixHigh,
                    containerColor = InfoBlue
                )
            }
        }
    }

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// MANUAL FIXTURE EDITOR CARD — Inline editing for each scheduled match
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ManualFixtureEditorCard(
        match: Match,
        viewModel: AdminViewModel
    ) {
        var expanded by remember { mutableStateOf(false) }
        var editVenue by remember(match.id) { mutableStateOf(match.venue) }
        var editTeamA by remember(match.id) { mutableStateOf(match.teamA) }
        var editTeamB by remember(match.id) { mutableStateOf(match.teamB) }
        var editScheduledTime by remember(match.id) { mutableStateOf(formatAdminDateTime(match.scheduledTime)) }
        var venueExpanded by remember { mutableStateOf(false) }

        SportCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .animateContentSize(animationSpec = tween(300))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Match header (collapsed view)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${match.teamA} vs ${match.teamB}",
                            style = SportFlowTheme.typography.headlineSmall,
                            color = TextPrimary, maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${match.sportType} · ${match.venue} · ${match.round.ifBlank { "No round" }}",
                            style = SportFlowTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = "Toggle edit",
                        tint = GnitsOrange
                    )
                }

                // Expanded edit form
                if (expanded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = CardBorder)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Team A
                    OutlinedTextField(
                        value = editTeamA,
                        onValueChange = { editTeamA = it },
                        label = { Text("Team A") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Team B
                    OutlinedTextField(
                        value = editTeamB,
                        onValueChange = { editTeamB = it },
                        label = { Text("Team B") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Venue
                    ExposedDropdownMenuBox(
                        expanded = venueExpanded,
                        onExpandedChange = { venueExpanded = !venueExpanded }
                    ) {
                        OutlinedTextField(
                            value = editVenue,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Venue") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = venueExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = RoundedCornerShape(14.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = venueExpanded,
                            onDismissRequest = { venueExpanded = false }
                        ) {
                            GnitsVenue.allNames.forEach { venue ->
                                DropdownMenuItem(
                                    text = { Text(venue) },
                                    onClick = {
                                        editVenue = venue
                                        venueExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = editScheduledTime,
                        onValueChange = { editScheduledTime = it },
                        label = { Text("Scheduled Time (dd/MM/yyyy HH:mm)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Delete button
                        OutlinedButton(
                            onClick = {
                                viewModel.deleteMatch(match.id)
                                expanded = false
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, LiveRed)
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                null,
                                tint = LiveRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Delete",
                                color = LiveRed,
                                style = SportFlowTheme.typography.labelSmall
                            )
                        }

                        // Swap teams button
                        OutlinedButton(
                            onClick = {
                                val tmp = editTeamA
                                editTeamA = editTeamB
                                editTeamB = tmp
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, WarningAmber)
                        ) {
                            Icon(
                                Icons.Filled.SwapHoriz,
                                null,
                                tint = WarningAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Swap",
                                color = WarningAmber,
                                style = SportFlowTheme.typography.labelSmall
                            )
                        }

                        // Save changes
                        PillButton(
                            text = "Save",
                            onClick = {
                                viewModel.applyManualEdit(
                                    ManualFixtureEdit(
                                        matchId = match.id,
                                        newTeamA = editTeamA.takeIf { it != match.teamA },
                                        newTeamB = editTeamB.takeIf { it != match.teamB },
                                        newVenue = editVenue.takeIf { it != match.venue },
                                        newScheduledTime = parseAdminScreenDateTime(
                                            editScheduledTime
                                        )?.takeIf { it != match.scheduledTime }
                                    )
                                )
                                expanded = false
                            },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.Save,
                            containerColor = SuccessGreen
                        )
                    }
                }
            }
        }
    }

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// REFEREE PANEL HEADER — Shows selected match info
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Composable
    private fun RefereePanelHeader(match: Match, viewModel: AdminViewModel) {
        SportCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${match.teamA} vs ${match.teamB}",
                        style = SportFlowTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "${match.sportType} · ${match.venue}",
                        style = SportFlowTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(
                        text = "Init Cards",
                        onClick = { viewModel.initializeScorecards() },
                        icon = Icons.Filled.PlaylistAdd,
                        containerColor = GnitsOrange
                    )
                    TextButton(onClick = { viewModel.deselectMatch() }) {
                        Text("✕", color = TextTertiary)
                    }
                }
            }
        }
    }

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// PLAYER SCORECARD EDITOR — Sport-specific stat buttons per player
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Composable
    private fun PlayerScorecardEditorCard(
        scorecard: PlayerScorecard,
        sportType: String,
        onIncrementStat: (statKey: String, delta: Int) -> Unit
    ) {
        val attributes = PlayerScorecardStrategy.getAttributes(sportType)

        SportCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .animateContentSize(animationSpec = tween(300))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Player info header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(GnitsOrangeLight, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            scorecard.playerName.take(2).uppercase(),
                            style = SportFlowTheme.typography.labelLarge,
                            color = GnitsOrangeDark,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            scorecard.playerName.ifBlank { scorecard.playerId.take(8) },
                            style = SportFlowTheme.typography.headlineSmall,
                            color = TextPrimary
                        )
                        Text(
                            "${scorecard.department} · Team ${scorecard.team.ifBlank { "—" }}",
                            style = SportFlowTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = CardBorder)
                Spacer(modifier = Modifier.height(12.dp))

                // Sport-specific stat buttons — 2 columns
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
                                is String -> currentValue
                                else -> "0"
                            }

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onIncrementStat(attr.key, 1) },
                                shape = RoundedCornerShape(12.dp),
                                color = GnitsOrangeLight,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    GnitsOrange.copy(alpha = 0.2f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = 10.dp,
                                        vertical = 10.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(attr.emoji, style = SportFlowTheme.typography.bodyMedium)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            attr.label,
                                            style = SportFlowTheme.typography.labelSmall,
                                            color = TextSecondary,
                                            maxLines = 1
                                        )
                                        Text(
                                            displayVal,
                                            style = SportFlowTheme.typography.headlineSmall,
                                            color = GnitsOrangeDark,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = "+1",
                                        tint = GnitsOrange,
                                        modifier = Modifier.size(20.dp)
                                    )
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

// ── ADMIN REGISTRATION CARD — List item for Registrations tab ─────────────────

    @Composable
    private fun AdminRegistrationCard(
        registration: com.sportflow.app.data.model.Registration,
        onClick: () -> Unit
    ) {
        SportCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            onClick = onClick
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Avatar with initials
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(GnitsOrange, GnitsOrangeDark)
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = registration.userName.split(" ")
                            .take(2)
                            .mapNotNull { it.firstOrNull()?.uppercase() }
                            .joinToString(""),
                        style = SportFlowTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = registration.userName.ifBlank { "Student" },
                            style = SportFlowTheme.typography.labelLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (registration.department.isNotBlank()) {
                            Text(
                                text = registration.department,
                                style = SportFlowTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        if (registration.sportRole.isNotBlank()) {
                            Text("·", color = TextTertiary)
                            Text(
                                text = registration.sportRole,
                                style = SportFlowTheme.typography.bodySmall,
                                color = GnitsOrange,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (registration.matchName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = registration.matchName,
                            style = SportFlowTheme.typography.labelSmall,
                            color = TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Sport type chip
                if (registration.sportType.isNotBlank()) {
                    com.sportflow.app.ui.components.SportTypeChip(sportType = registration.sportType)
                }

                // Chevron
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = "View details",
                    tint = TextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

// ── REGISTRATION DETAIL CARD — Full student profile for eligibility checks ────

    @Composable
    private fun RegistrationDetailCard(
        registration: com.sportflow.app.data.model.Registration,
        onDismiss: () -> Unit,
        onAccept: () -> Unit,
        onDeny: () -> Unit
    ) {
        SportCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header with back button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Student Profile",
                        style = SportFlowTheme.typography.headlineSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Back", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Avatar + Name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    colors = listOf(GnitsOrange, GnitsOrangeDark)
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = registration.userName.split(" ")
                                .take(2)
                                .mapNotNull { it.firstOrNull()?.uppercase() }
                                .joinToString(""),
                            style = SportFlowTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Column {
                        Text(
                            text = registration.userName.ifBlank { "Unknown Student" },
                            style = SportFlowTheme.typography.headlineMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        if (registration.rollNumber.isNotBlank()) {
                            Text(
                                text = "Roll: ${registration.rollNumber}",
                                style = SportFlowTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = CardBorder)
                Spacer(modifier = Modifier.height(16.dp))

                // Detail Grid
                val details = listOf(
                    "Department" to registration.department.ifBlank { "—" },
                    "Year of Study" to registration.yearOfStudy.ifBlank { "—" },
                    "Sport Role" to registration.sportRole.ifBlank { "Not specified" },
                    "Sport Type" to registration.sportType.ifBlank { "—" },
                    "Match" to registration.matchName.ifBlank { "—" },
                    "Entry Type" to registration.registrationKind.name.replace('_', ' '),
                    "Fixture Unit" to registration.fixtureUnitName.ifBlank { registration.teamName.ifBlank { registration.userName } },
                    "Status" to registration.status.name
                )

                details.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        row.forEach { (label, value) ->
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = label,
                                    style = SportFlowTheme.typography.labelSmall,
                                    color = TextTertiary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = value,
                                    style = SportFlowTheme.typography.labelLarge,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = CardBorder)
                Spacer(modifier = Modifier.height(12.dp))

                if (registration.teamName.isNotBlank() || registration.partnerName.isNotBlank() || registration.roster.isNotEmpty()) {
                    Text(
                        text = "Registration Unit",
                        style = SportFlowTheme.typography.labelLarge,
                        color = GnitsOrange,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (registration.teamName.isNotBlank()) {
                        Text(
                            "Team: ${registration.teamName}",
                            style = SportFlowTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Text(
                            "Captain: ${registration.captainName}",
                            style = SportFlowTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                    if (registration.partnerName.isNotBlank()) {
                        Text(
                            "Partner: ${registration.partnerName} (${registration.partnerRollNumber})",
                            style = SportFlowTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                    registration.roster.forEachIndexed { index, player ->
                        Text(
                            "${index + 1}. ${player.name} - ${player.rollNumber} - ${player.role}",
                            style = SportFlowTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = CardBorder)
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Eligibility Assessment
                val isEligible = registration.department.isNotBlank() &&
                        registration.yearOfStudy.isNotBlank() &&
                        registration.status == com.sportflow.app.data.model.RegistrationStatus.CONFIRMED

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isEligible) SuccessGreenLight else WarningAmberBg
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            if (isEligible) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                            contentDescription = null,
                            tint = if (isEligible) SuccessGreen else WarningAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (isEligible) "Eligible — All profile fields verified"
                            else "Incomplete — Missing profile data for eligibility",
                            style = SportFlowTheme.typography.labelMedium,
                            color = if (isEligible) SuccessGreen else WarningAmber,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (registration.status == com.sportflow.app.data.model.RegistrationStatus.PENDING) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDeny,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = LiveRed),
                            border = androidx.compose.foundation.BorderStroke(1.dp, LiveRed)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Deny")
                        }
                        Button(
                            onClick = onAccept,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Accept")
                        }
                    }
                }
            }
        }
    }

// ── CREATE FIXTURES CARD ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateFixturesCard(
    uiState: AdminUiState,
    viewModel: AdminViewModel
) {
    var selectedTournamentId by remember { mutableStateOf("") }
    var selectedSportType by remember { mutableStateOf("") }
    var selectedVenue by remember { mutableStateOf(GnitsVenue.MAIN_GROUND.displayName) }
    var startTimeText by remember { mutableStateOf("") }
    var intervalMinutes by remember { mutableStateOf("60") }
    var isCreating by remember { mutableStateOf(false) }
    
    var tournamentExpanded by remember { mutableStateOf(false) }
    var sportExpanded by remember { mutableStateOf(false) }
    var venueExpanded by remember { mutableStateOf(false) }

    val availableTournaments = uiState.tournaments.filter { 
        it.status == TournamentStatus.REGISTRATION 
    }
    val sportTypes = SportType.displayList
    val venues = GnitsVenue.allNames

    SportCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = GnitsOrange.copy(alpha = 0.12f)
                ) {
                    Text(
                        "⚡ Quick",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = SportFlowTheme.typography.labelMedium,
                        color = GnitsOrange, 
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Create Fixtures Between Registered Users",
                    style = SportFlowTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Tournament Selection
            ExposedDropdownMenuBox(
                expanded = tournamentExpanded,
                onExpandedChange = { tournamentExpanded = !tournamentExpanded }
            ) {
                OutlinedTextField(
                    value = availableTournaments.find { it.id == selectedTournamentId }?.name ?: "Select Tournament",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Tournament *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tournamentExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(14.dp)
                )
                ExposedDropdownMenu(
                    expanded = tournamentExpanded,
                    onDismissRequest = { tournamentExpanded = false }
                ) {
                    availableTournaments.forEach { tournament ->
                        DropdownMenuItem(
                            text = { 
                                Column {
                                    Text(tournament.name)
                                    Text(
                                        "${tournament.sport} • ${tournament.teams.size} registered",
                                        style = SportFlowTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            },
                            onClick = {
                                selectedTournamentId = tournament.id
                                selectedSportType = tournament.sport
                                selectedVenue = tournament.venue
                                tournamentExpanded = false
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            // Sport Type
            ExposedDropdownMenuBox(
                expanded = sportExpanded,
                onExpandedChange = { sportExpanded = !sportExpanded }
            ) {
                OutlinedTextField(
                    value = selectedSportType.ifEmpty { "Select Sport" },
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
                                selectedSportType = sport
                                sportExpanded = false
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            // Venue
            ExposedDropdownMenuBox(
                expanded = venueExpanded,
                onExpandedChange = { venueExpanded = !venueExpanded }
            ) {
                OutlinedTextField(
                    value = selectedVenue,
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
                                selectedVenue = venue
                                venueExpanded = false
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            // Start Time
            OutlinedTextField(
                value = startTimeText,
                onValueChange = { startTimeText = it },
                label = { Text("Start Time (dd/MM/yyyy HH:mm) *") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                placeholder = { Text("25/04/2026 14:00") }
            )
            
            Spacer(modifier = Modifier.height(12.dp))

            // Interval
            OutlinedTextField(
                value = intervalMinutes,
                onValueChange = { intervalMinutes = it },
                label = { Text("Interval Between Matches (minutes)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            // Info about selected tournament
            if (selectedTournamentId.isNotEmpty()) {
                val tournament = availableTournaments.find { it.id == selectedTournamentId }
                if (tournament != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = InfoBlue.copy(alpha = 0.1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, InfoBlue.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Tournament Info",
                                style = SportFlowTheme.typography.labelMedium,
                                color = InfoBlue,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "• ${tournament.teams.size} teams registered",
                                style = SportFlowTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(
                                "• Will create ${tournament.teams.size / 2} matches",
                                style = SportFlowTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(
                                "• Each registered user will get a match",
                                style = SportFlowTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Create Button
            PillButton(
                text = if (isCreating) "Creating Fixtures..." else "Create Fixtures",
                onClick = {
                    if (selectedTournamentId.isNotEmpty() && 
                        selectedSportType.isNotEmpty() && 
                        startTimeText.isNotEmpty()) {
                        
                        val startTime = parseAdminScreenDateTime(startTimeText)
                        if (startTime != null) {
                            isCreating = true
                            viewModel.createFixturesBetweenUsers(
                                tournamentId = selectedTournamentId,
                                sportType = selectedSportType,
                                venue = selectedVenue,
                                startTime = startTime,
                                intervalMinutes = intervalMinutes.toLongOrNull() ?: 60L
                            )
                            // Reset form
                            selectedTournamentId = ""
                            selectedSportType = ""
                            startTimeText = ""
                            isCreating = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isCreating && selectedTournamentId.isNotEmpty() && 
                         selectedSportType.isNotEmpty() && startTimeText.isNotEmpty(),
                icon = Icons.Filled.SportsSoccer,
                containerColor = GnitsOrange
            )
            
            if (availableTournaments.isEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "No tournaments with registrations found. Create a tournament first and wait for registrations.",
                    style = SportFlowTheme.typography.bodySmall,
                    color = TextTertiary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }  // Close Column inside SportCard
    }  // Close SportCard
}

@Composable
private fun AdminBroadcastOverview(
    updates: List<Update>,
    modifier: Modifier = Modifier
) {
    val manualCount = updates.count { it.updateType == "manual" }
    val resultCount = updates.count { it.updateType == "match_result" }
    val attachedMatchCount = updates.count { it.matchId.isNotBlank() }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatTile(
            icon = Icons.Filled.Campaign,
            value = "${updates.size}",
            label = "Saved",
            accentColor = GnitsOrange,
            modifier = Modifier.weight(1f)
        )
        StatTile(
            icon = Icons.Filled.Edit,
            value = "$manualCount",
            label = "Manual",
            accentColor = InfoBlue,
            modifier = Modifier.weight(1f)
        )
        StatTile(
            icon = Icons.Filled.EmojiEvents,
            value = "$resultCount",
            label = "Results",
            accentColor = SuccessGreen,
            modifier = Modifier.weight(1f)
        )
        StatTile(
            icon = Icons.Filled.SportsScore,
            value = "$attachedMatchCount",
            label = "Linked",
            accentColor = WarningAmber,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AdminBroadcastCard(
    update: Update,
    onDelete: () -> Unit
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
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = update.title,
                        style = SportFlowTheme.typography.headlineSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatAdminBroadcastTimestamp(update.createdAt),
                        style = SportFlowTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete broadcast",
                        tint = LiveRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = update.body,
                style = SportFlowTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BroadcastTag(
                    label = if (update.updateType == "match_result") "Match Result" else "Manual",
                    containerColor = if (update.updateType == "match_result") SuccessGreen.copy(alpha = 0.14f) else GnitsOrange.copy(alpha = 0.14f),
                    contentColor = if (update.updateType == "match_result") SuccessGreen else GnitsOrange
                )
                BroadcastTag(
                    label = if (update.targetSport.isBlank()) "All Users" else update.targetSport,
                    containerColor = if (update.targetSport.isBlank()) InfoBlue.copy(alpha = 0.14f) else WarningAmber.copy(alpha = 0.14f),
                    contentColor = if (update.targetSport.isBlank()) InfoBlue else WarningAmber
                )
                if (update.matchStatus.isNotBlank()) {
                    BroadcastTag(
                        label = update.matchStatus.replaceFirstChar { it.uppercase() },
                        containerColor = OffWhite,
                        contentColor = TextPrimary
                    )
                }
            }

            if (update.matchId.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = ScreenBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = update.sportType.ifBlank { "Attached Match" },
                            style = SportFlowTheme.typography.labelMedium,
                            color = GnitsOrange,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = listOf(update.teamA, update.teamB).filter { it.isNotBlank() }.joinToString(" vs "),
                            style = SportFlowTheme.typography.bodyLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (update.scoreLine.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = update.scoreLine,
                                style = SportFlowTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                        if (update.winnerName.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (update.winnerName == "DRAW") "Winner: Draw" else "Winner: ${update.winnerName}",
                                style = SportFlowTheme.typography.bodyMedium,
                                color = if (update.winnerName == "DRAW") TextSecondary else SuccessGreen,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        val detailLine = listOf(
                            update.round,
                            update.venue,
                            update.scheduledTime?.let(::formatCompactTimestamp)
                        ).filter { !it.isNullOrBlank() }.joinToString(" • ")
                        if (detailLine.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = detailLine,
                                style = SportFlowTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BroadcastTag(
    label: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = SportFlowTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun broadcastMatchLabel(match: Match): String =
    listOf(match.teamA, match.teamB).filter { it.isNotBlank() }.joinToString(" vs ")

private fun formatAdminBroadcastTimestamp(timestamp: Timestamp?): String {
    if (timestamp == null) return "Saved just now"
    return SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(timestamp.toDate())
}

private fun formatCompactTimestamp(timestamp: Timestamp): String =
    SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(timestamp.toDate())

// ── Broadcast Dialog Component ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BroadcastDialog(
    title: String,
    body: String,
    target: String,
    attachedMatchId: String,
    availableMatches: List<Match>,
    targetOptions: List<String>,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onTargetChange: (String) -> Unit,
    onAttachedMatchChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSend: () -> Unit
) {
    var targetExpanded by remember { mutableStateOf(false) }
    var matchExpanded by remember { mutableStateOf(false) }
    val attachedMatch = availableMatches.find { it.id == attachedMatchId }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.Campaign,
                    contentDescription = null,
                    tint = GnitsOrange,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "Broadcast Message",
                    style = SportFlowTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title TextField
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("Message Title") },
                    placeholder = { Text("e.g., Important Update") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GnitsOrange,
                        focusedLabelColor = GnitsOrange
                    )
                )
                
                // Body TextField
                OutlinedTextField(
                    value = body,
                    onValueChange = onBodyChange,
                    label = { Text("Message Body") },
                    placeholder = { Text("Enter your message here...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp),
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GnitsOrange,
                        focusedLabelColor = GnitsOrange
                    )
                )
                
                // Target Dropdown
                ExposedDropdownMenuBox(
                    expanded = targetExpanded,
                    onExpandedChange = { targetExpanded = !targetExpanded }
                ) {
                    OutlinedTextField(
                        value = target,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Target Audience") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GnitsOrange,
                            focusedLabelColor = GnitsOrange
                        )
                    )
                    
                    ExposedDropdownMenu(
                        expanded = targetExpanded,
                        onDismissRequest = { targetExpanded = false }
                    ) {
                        targetOptions.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            if (option == "All Users") Icons.Filled.Public else Icons.Filled.SportsSoccer,
                                            contentDescription = null,
                                            tint = if (option == target) GnitsOrange else TextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            option,
                                            color = if (option == target) GnitsOrange else TextPrimary
                                        )
                                    }
                                },
                                onClick = {
                                    onTargetChange(option)
                                    targetExpanded = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = matchExpanded,
                    onExpandedChange = { matchExpanded = !matchExpanded }
                ) {
                    OutlinedTextField(
                        value = attachedMatch?.let(::broadcastMatchLabel) ?: "No match attached",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Attach Match Details") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = matchExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GnitsOrange,
                            focusedLabelColor = GnitsOrange
                        )
                    )

                    ExposedDropdownMenu(
                        expanded = matchExpanded,
                        onDismissRequest = { matchExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("No match attached") },
                            onClick = {
                                onAttachedMatchChange("")
                                matchExpanded = false
                            }
                        )
                        availableMatches.forEach { match ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(broadcastMatchLabel(match))
                                        Text(
                                            "${match.displayStatusLabel()} • ${match.sportType}",
                                            style = SportFlowTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                },
                                onClick = {
                                    onAttachedMatchChange(match.id)
                                    matchExpanded = false
                                }
                            )
                        }
                    }
                }

                attachedMatch?.let { match ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = PureWhite,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = match.sportType,
                                style = SportFlowTheme.typography.labelMedium,
                                color = GnitsOrange,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = broadcastMatchLabel(match),
                                style = SportFlowTheme.typography.bodyMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            val detailLine = listOf(match.round, match.venue)
                                .filter { it.isNotBlank() }
                                .joinToString(" • ")
                            if (detailLine.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = detailLine,
                                    style = SportFlowTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
                
                // Info text
                Text(
                    text = if (target == "All Users") {
                        "📢 This message will be sent to ALL users"
                    } else {
                        "🎯 This message will be sent only to $target players"
                    },
                    style = SportFlowTheme.typography.bodySmall,
                    color = if (target == "All Users") WarningAmber else InfoBlue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (target == "All Users") WarningAmber.copy(alpha = 0.1f) else InfoBlue.copy(alpha = 0.1f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSend,
                enabled = title.isNotBlank() && body.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = GnitsOrange)
            ) {
                Icon(
                    Icons.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Send Broadcast")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
