package com.sportflow.app.ui.screens.events

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.sportflow.app.data.model.*
import com.sportflow.app.ui.components.*
import com.sportflow.app.ui.theme.*
import com.sportflow.app.ui.viewmodel.HomeViewModel
import com.sportflow.app.ui.viewmodel.RegistrationViewModel

@Composable
fun EventsScreen(
    navController: NavHostController,
    isAdmin: Boolean = false,
    viewModel: HomeViewModel = hiltViewModel(),
    registrationViewModel: RegistrationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val registrationState by registrationViewModel.uiState.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf("All") }
    var selectedTournament by remember { mutableStateOf<Tournament?>(null) }
    val filters = listOf("All", "Football", "Cricket", "Basketball", "Badminton", "Volleyball")

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
                    text = "GNITS Inter-Department Sports",
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
                            selectedContainerColor = GnitsOrangeLight,
                            labelColor = TextSecondary,
                            selectedLabelColor = GnitsOrangeDark
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = CardBorder,
                            selectedBorderColor = GnitsOrange,
                            enabled = true,
                            selected = isSelected
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
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

        // Tournaments Section
        if (uiState.tournaments.isNotEmpty()) {
            item {
                SectionHeader(title = "Active Tournaments")
            }

            items(
                items = uiState.tournaments.filter {
                    selectedFilter == "All" || it.sport.equals(selectedFilter, ignoreCase = true)
                },
                key = { it.id }
            ) { tournament ->
                TournamentEventCard(
                    tournament = tournament,
                    isRegistered = tournament.id in registrationState.registeredTournamentIds,
                    isAdmin = isAdmin,
                    onRegister = { selectedTournament = tournament },
                    onQuickRegister = { registrationViewModel.quickRegisterForTournament(tournament) },
                    onClick = {
                        if (tournament.id.isNotBlank()) {
                            navController.navigate("bracket?tournamentId=${tournament.id}")
                        }
                    }
                )
            }
        }

        // Upcoming Events Section
        if (uiState.upcomingMatches.isNotEmpty()) {
            item {
                SectionHeader(title = "Upcoming Events")
            }

            items(
                items = uiState.upcomingMatches.filter {
                    selectedFilter == "All" || it.sportType.equals(selectedFilter, ignoreCase = true)
                },
                key = { it.id }
            ) { match ->
                UpcomingEventCard(
                    match = match,
                    onClick = { /* No detail screen yet */ }
                )
            }
        }

        // Empty State
        if (!uiState.isLoading && uiState.tournaments.isEmpty() && uiState.upcomingMatches.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.EventBusy,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No events scheduled",
                            style = SportFlowTheme.typography.headlineMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }

    selectedTournament?.let { tournament ->
        AdvancedRegistrationBottomSheet(
            match = tournament.asRegistrationMatch(),
            currentUser = registrationState.currentUser,
            onDismiss = { selectedTournament = null },
            onRegister = { data ->
                registrationViewModel.registerForTournament(tournament, data)
                selectedTournament = null
            }
        )
    }
}

// ── Tournament Event Card — Full-width with hero style ───────────────────────

@Composable
private fun TournamentEventCard(
    tournament: Tournament,
    isRegistered: Boolean,
    isAdmin: Boolean,
    onRegister: () -> Unit,
    onQuickRegister: () -> Unit,
    onClick: () -> Unit
) {
    val allowQuickRegister = remember(tournament.sport) {
        val sportType = SportType.fromString(tournament.sport)
        sportType == SportType.BADMINTON || sportType == SportType.TABLE_TENNIS
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
    ) {
        // Background gradient — GNITS warm tones per sport
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = when (tournament.sport.lowercase()) {
                            "football" -> listOf(Color(0xFF1E3A5F), Color(0xFF0F172A))
                            "cricket" -> listOf(Color(0xFF7C3AED), Color(0xFF4C1D95))
                            "basketball" -> listOf(Color(0xFFEA580C), Color(0xFF9A3412))
                            else -> listOf(GnitsOrangeDark, Color(0xFF8B5E0C))
                        }
                    )
                )
        )

        // Decorative
        Box(
            modifier = Modifier
                .size(150.dp)
                .align(Alignment.TopEnd)
                .offset(x = 48.dp, y = (-30).dp)
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
                        TournamentStatus.IN_PROGRESS -> GnitsOrange.copy(alpha = 0.9f)
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
                            text = tournament.prizePool.ifEmpty { "GNITS Inter-Dept" },
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
                Spacer(modifier = Modifier.height(12.dp))

                // Registration buttons
                if (!isAdmin && !isRegistered && tournament.status == TournamentStatus.REGISTRATION) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (allowQuickRegister) {
                            OutlinedButton(
                                onClick = onQuickRegister,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White
                                ),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FlashOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Quick")
                            }
                        }

                        Button(
                            onClick = onRegister,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = GnitsOrangeDark
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GroupAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Register")
                        }
                    }
                } else {
                    Button(
                        onClick = onRegister,
                        enabled = !isAdmin && !isRegistered && tournament.status == TournamentStatus.REGISTRATION,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = GnitsOrangeDark,
                            disabledContainerColor = Color.White.copy(alpha = 0.35f),
                            disabledContentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = if (isRegistered) Icons.Filled.CheckCircle else Icons.Filled.GroupAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isAdmin) "Admin View" else if (isRegistered) "Registered" else "Register")
                    }
                }
            }
        }
    }
}

private fun Tournament.asRegistrationMatch(): Match = Match(
    id = id,
    tournamentId = id,
    tournamentName = name.ifBlank { "Tournament" },
    sportType = sport.ifBlank { "General" },
    teamA = name.ifBlank { "Tournament" },
    teamB = "Registration",
    venue = venue.ifBlank { "GNITS" },
    maxSquadSize = maxTeams,
    allowedDepartments = allowedDepartments,
    allowedYears = allowedYears,
    eligibilityText = eligibilityText
)

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
                    .background(GnitsOrangeLight, RoundedCornerShape(16.dp)),
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
                    tint = GnitsOrangeDark,
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
                if (match.teamADepartment.isNotBlank()) {
                    StatusChip(
                        text = match.teamADepartment,
                        color = GnitsOrange
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                if (match.tournamentName.isNotBlank()) {
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
}
