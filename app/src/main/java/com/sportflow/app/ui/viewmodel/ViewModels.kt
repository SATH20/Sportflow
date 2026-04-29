package com.sportflow.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.sportflow.app.data.model.*
import com.sportflow.app.data.repository.SportFlowRepository
import com.sportflow.app.data.service.FixtureGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// AUTH VIEWMODEL — Dual-role authentication with GNITS student identity
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

data class AuthUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val userRole: UserRole? = null,
    val currentUser: SportUser? = null,
    val error: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: SportFlowRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        val user = repository.getCurrentUser()
        if (user != null) {
            _uiState.update { it.copy(isLoggedIn = true) }
            loadUserProfile(user.uid)
        }
    }

    /** Post-login: fetch user role and profile from Firestore */
    private fun loadUserProfile(uid: String) {
        viewModelScope.launch {
            repository.getUserProfile(uid).collect { profile ->
                _uiState.update {
                    it.copy(
                        currentUser = profile,
                        userRole = profile?.role
                    )
                }
            }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                repository.signIn(email, password)
                val uid = repository.getCurrentUser()?.uid
                _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
                uid?.let { loadUserProfile(it) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Sign in failed")
                }
            }
        }
    }

    /** Sign up with GNITS student identity: roll number + department */
    fun signUp(
        email: String,
        password: String,
        name: String,
        rollNumber: String,
        department: String
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                repository.signUp(email, password, name, rollNumber, department)
                val uid = repository.getCurrentUser()?.uid
                _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
                uid?.let { loadUserProfile(it) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Sign up failed")
                }
            }
        }
    }

    fun signOut() {
        repository.signOut()
        _uiState.update { AuthUiState() }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun updateProfile(
        displayName: String,
        rollNumber: String,
        department: String,
        yearOfStudy: String
    ) {
        val uid = repository.getCurrentUser()?.uid ?: return
        viewModelScope.launch {
            try {
                repository.updateUserProfile(
                    uid = uid,
                    displayName = displayName,
                    rollNumber = rollNumber,
                    department = department,
                    yearOfStudy = yearOfStudy
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Profile update failed") }
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// HOME VIEWMODEL — Player-side real-time sync
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

data class HomeUiState(
    val upcomingMatches: List<Match> = emptyList(),
    val liveMatches: List<Match> = emptyList(),
    val tournaments: List<Tournament> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

data class RoleHomeUiState(
    val myRegistrations: List<Registration> = emptyList(),
    val pendingRegistrations: List<Registration> = emptyList(),
    val allMatches: List<Match> = emptyList(),
    val tournaments: List<Tournament> = emptyList(),
    val notifications: List<NotificationItem> = emptyList(),
    val unseenNotificationCount: Int = 0,
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: SportFlowRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadFromFirebase()
    }

    private fun loadFromFirebase() {
        // Listen for live matches — real-time updates from gnits_matches
        viewModelScope.launch {
            try {
                repository.getAllMatches().collect { matches ->
                    val liveMatches = matches
                        .filter { it.status == MatchStatus.LIVE || it.status == MatchStatus.HALFTIME }
                        .sortedBy { it.scheduledTime }
                    val upcomingMatches = matches
                        .filter { it.status == MatchStatus.SCHEDULED }
                        .sortedBy { it.scheduledTime }

                    _uiState.update {
                        it.copy(
                            liveMatches = liveMatches,
                            upcomingMatches = upcomingMatches,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }

        // Listen for tournaments
        viewModelScope.launch {
            try {
                repository.getTournaments().collect { tournaments ->
                    _uiState.update { it.copy(tournaments = tournaments) }
                }
            } catch (_: Exception) {}
        }
    }
}

@HiltViewModel
class RoleHomeViewModel @Inject constructor(
    private val repository: SportFlowRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(RoleHomeUiState())
    val uiState: StateFlow<RoleHomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeMyRegistrations().collect { registrations ->
                _uiState.update { it.copy(myRegistrations = registrations, isLoading = false) }
            }
        }
        viewModelScope.launch {
            repository.observePendingRegistrations().collect { registrations ->
                _uiState.update { it.copy(pendingRegistrations = registrations) }
            }
        }
        viewModelScope.launch {
            repository.getAllMatches().collect { matches ->
                _uiState.update { it.copy(allMatches = matches) }
            }
        }
        viewModelScope.launch {
            repository.getTournaments().collect { tournaments ->
                _uiState.update { it.copy(tournaments = tournaments) }
            }
        }
        viewModelScope.launch {
            repository.observeNotifications().collect { notifications ->
                _uiState.update {
                    it.copy(
                        notifications = notifications.take(12),
                        unseenNotificationCount = notifications.count { notification -> !notification.seen }
                    )
                }
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// LIVE MATCH VIEWMODEL — Real-time match sync for players
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

data class LiveMatchUiState(
    val liveMatches: List<Match> = emptyList(),
    val selectedMatch: Match? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class LiveMatchViewModel @Inject constructor(
    private val repository: SportFlowRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveMatchUiState())
    val uiState: StateFlow<LiveMatchUiState> = _uiState.asStateFlow()

    init {
        loadFromFirebase()
    }

    private fun loadFromFirebase() {
        viewModelScope.launch {
            try {
                repository.getLiveMatches().collect { matches ->
                    _uiState.update { state ->
                        val selected = state.selectedMatch?.let { sel ->
                            matches.find { it.id == sel.id }
                        } ?: matches.firstOrNull()
                        state.copy(
                            liveMatches = matches,
                            selectedMatch = selected,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun selectMatch(match: Match) {
        _uiState.update { it.copy(selectedMatch = match) }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// BRACKET VIEWMODEL — Tournament bracket visualization
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

data class BracketUiState(
    val bracketNodes: List<BracketNode> = emptyList(),
    val tournaments: List<Tournament> = emptyList(),
    val selectedTournament: Tournament? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class BracketViewModel @Inject constructor(
    private val repository: SportFlowRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BracketUiState())
    val uiState: StateFlow<BracketUiState> = _uiState.asStateFlow()

    init {
        loadFromFirebase()
    }

    private fun loadFromFirebase() {
        viewModelScope.launch {
            try {
                repository.getTournaments().collect { tournaments ->
                    _uiState.update { state ->
                        val selected = state.selectedTournament
                            ?: tournaments.firstOrNull()
                        state.copy(
                            tournaments = tournaments,
                            selectedTournament = selected,
                            isLoading = false
                        )
                    }
                    // Load bracket for selected tournament
                    _uiState.value.selectedTournament?.let { loadBracket(it.id) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun selectTournament(tournament: Tournament) {
        _uiState.update { it.copy(selectedTournament = tournament) }
        loadBracket(tournament.id)
    }

    private fun loadBracket(tournamentId: String) {
        viewModelScope.launch {
            try {
                repository.getBracket(tournamentId).collect { nodes ->
                    _uiState.update { it.copy(bracketNodes = nodes) }
                }
            } catch (_: Exception) {}
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// ADMIN VIEWMODEL — Control Room with match CRUD, live scoring, lifecycle
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

data class AdminUiState(
    val pendingPayments: List<Payment> = emptyList(),
    val tournaments: List<Tournament> = emptyList(),
    val allMatches: List<Match> = emptyList(),
    val selectedMatch: Match? = null,
    val isLoading: Boolean = true,
    val isGeneratingBracket: Boolean = false,
    val isCreatingMatch: Boolean = false,
    /** Scorecards for the currently selected match (Referee Panel) */
    val matchScorecards: List<PlayerScorecard> = emptyList(),
    /** All registrations from gnits_registrations (Admin Data Bridge) */
    val registrations: List<Registration> = emptyList(),
    /** Count of unseen registrations ("New Entry" badge) */
    val newRegistrationCount: Int = 0,
    /** Currently selected registration for detail view */
    val selectedRegistration: Registration? = null,
    val error: String? = null,
    val successMessage: String? = null
)

/** Form state for creating a new match */
data class CreateMatchForm(
    val sportType: String = "",
    val teamA: String = "",
    val teamB: String = "",
    val teamADepartment: String = "",
    val teamBDepartment: String = "",
    val venue: String = "",
    val tournamentName: String = "",
    val round: String = ""
)

/** Form state for creating a match under a tournament with approved teams */
data class CreateTournamentMatchForm(
    val selectedTournamentId: String = "",
    val teamA: String = "",
    val teamB: String = "",
    val venue: String = "",
    val scheduledDateText: String = "",
    val round: String = "Round 1"
)

data class CreateTournamentForm(
    val name: String = "",
    val sport: String = "",
    val venue: String = "",
    val startDateText: String = "",
    val endDateText: String = "",
    val maxTeams: String = "16",
    val prizePool: String = "",
    val eligibilityText: String = "All GNITS students"
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val repository: SportFlowRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    private val _matchForm = MutableStateFlow(CreateMatchForm())
    val matchForm: StateFlow<CreateMatchForm> = _matchForm.asStateFlow()

    private val _tournamentMatchForm = MutableStateFlow(CreateTournamentMatchForm())
    val tournamentMatchForm: StateFlow<CreateTournamentMatchForm> = _tournamentMatchForm.asStateFlow()

    private val _tournamentForm = MutableStateFlow(CreateTournamentForm())
    val tournamentForm: StateFlow<CreateTournamentForm> = _tournamentForm.asStateFlow()

    /** Fixture engine configuration form state */
    private val _fixtureConfig = MutableStateFlow(FixtureConfig())
    val fixtureConfig: StateFlow<FixtureConfig> = _fixtureConfig.asStateFlow()

    init {
        loadFromFirebase()
    }

    private fun loadFromFirebase() {
        viewModelScope.launch {
            try {
                repository.getPendingPayments().collect { payments ->
                    _uiState.update { it.copy(pendingPayments = payments, isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
        viewModelScope.launch {
            try {
                repository.getTournaments().collect { tournaments ->
                    _uiState.update { it.copy(tournaments = tournaments) }
                }
            } catch (_: Exception) {}
        }
        viewModelScope.launch {
            try {
                repository.getAllMatches().collect { matches ->
                    _uiState.update { state ->
                        // Auto-update selected match if it changed
                        val updatedSelected = state.selectedMatch?.let { sel ->
                            matches.find { it.id == sel.id }
                        }
                        state.copy(
                            allMatches = matches,
                            selectedMatch = updatedSelected ?: state.selectedMatch
                        )
                    }
                }
            } catch (_: Exception) {}
        }
        // Listen for registrations (Admin Data Bridge)
        viewModelScope.launch {
            try {
                repository.observeAllRegistrations().collect { regs ->
                    _uiState.update { it.copy(registrations = regs) }
                }
            } catch (_: Exception) {}
        }
        // Listen for new (unseen) registration count
        viewModelScope.launch {
            try {
                repository.observeNewRegistrationCount().collect { count ->
                    _uiState.update { it.copy(newRegistrationCount = count) }
                }
            } catch (_: Exception) {}
        }
    }

    // ── Match Form ──────────────────────────────────────────────────────────

    fun updateMatchForm(form: CreateMatchForm) {
        _matchForm.update { form }
    }

    /** Create a new match from admin form data */
    fun createMatch() {
        _uiState.update { it.copy(error = "Admins can create tournaments only. Generate matches after approving tournament registrations.") }
    }

    fun updateTournamentForm(form: CreateTournamentForm) {
        _tournamentForm.update { form }
    }

    fun createTournament() {
        val form = _tournamentForm.value
        if (form.name.isBlank() || form.sport.isBlank() || form.venue.isBlank() ||
            form.startDateText.isBlank() || form.endDateText.isBlank()) {
            _uiState.update { it.copy(error = "Please enter tournament name, sport, venue, start date, and end date") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingMatch = true) }
            try {
                val startDate = parseAdminDateTime(form.startDateText)
                val endDate = parseAdminDateTime(form.endDateText)
                if (startDate == null || endDate == null) {
                    _uiState.update {
                        it.copy(
                            isCreatingMatch = false,
                            error = "Use date format dd/MM/yyyy HH:mm for start and end"
                        )
                    }
                    return@launch
                }
                if (endDate.seconds < startDate.seconds) {
                    _uiState.update {
                        it.copy(
                            isCreatingMatch = false,
                            error = "End date/time must be after the start date/time"
                        )
                    }
                    return@launch
                }
                repository.createTournament(
                    Tournament(
                        name = form.name.trim(),
                        sport = form.sport,
                        venue = form.venue,
                        maxTeams = form.maxTeams.toIntOrNull()?.coerceAtLeast(2) ?: 16,
                        prizePool = form.prizePool,
                        eligibilityText = form.eligibilityText.ifBlank { "All GNITS students" },
                        status = TournamentStatus.REGISTRATION,
                        startDate = startDate,
                        endDate = endDate
                    )
                )
                _tournamentForm.update { CreateTournamentForm() }
                _uiState.update {
                    it.copy(
                        isCreatingMatch = false,
                        successMessage = "Tournament created: ${form.name}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isCreatingMatch = false, error = e.message)
                }
            }
        }
    }

    fun updateTournamentMatchForm(form: CreateTournamentMatchForm) {
        _tournamentMatchForm.update { form }
    }

    /**
     * Create a new match under an existing tournament with approved teams.
     * The match will have status SCHEDULED and appear in Tab 3 (Manual Editor).
     */
    fun createTournamentMatch() {
        val form = _tournamentMatchForm.value
        if (form.selectedTournamentId.isBlank()) {
            _uiState.update { it.copy(error = "Please select a tournament") }
            return
        }
        if (form.teamA.isBlank() || form.teamB.isBlank()) {
            _uiState.update { it.copy(error = "Please select both teams") }
            return
        }
        if (form.teamA == form.teamB) {
            _uiState.update { it.copy(error = "Teams must be different") }
            return
        }
        if (form.venue.isBlank()) {
            _uiState.update { it.copy(error = "Please select a venue") }
            return
        }
        if (form.scheduledDateText.isBlank()) {
            _uiState.update { it.copy(error = "Please enter scheduled date and time") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingMatch = true) }
            try {
                val scheduledTime = parseAdminDateTime(form.scheduledDateText)
                if (scheduledTime == null) {
                    _uiState.update {
                        it.copy(
                            isCreatingMatch = false,
                            error = "Use date format dd/MM/yyyy HH:mm for scheduled time"
                        )
                    }
                    return@launch
                }

                // Find the tournament to get its details
                val tournament = _uiState.value.tournaments.find { it.id == form.selectedTournamentId }
                if (tournament == null) {
                    _uiState.update {
                        it.copy(
                            isCreatingMatch = false,
                            error = "Tournament not found"
                        )
                    }
                    return@launch
                }

                // Check for duplicate match (same teams in same round)
                val existingMatch = _uiState.value.allMatches.find { match ->
                    match.tournamentId == tournament.id &&
                    match.round == form.round.ifBlank { "Round 1" } &&
                    ((match.teamA == form.teamA.trim() && match.teamB == form.teamB.trim()) ||
                     (match.teamA == form.teamB.trim() && match.teamB == form.teamA.trim()))
                }

                if (existingMatch != null) {
                    _uiState.update {
                        it.copy(
                            isCreatingMatch = false,
                            error = "Match already exists: ${form.teamA} vs ${form.teamB} in ${form.round.ifBlank { "Round 1" }}"
                        )
                    }
                    return@launch
                }

                // Create the match
                val match = Match(
                    sportType = tournament.sport,
                    teamA = form.teamA.trim(),
                    teamB = form.teamB.trim(),
                    venue = form.venue,
                    scheduledTime = scheduledTime,
                    status = MatchStatus.SCHEDULED,
                    tournamentId = tournament.id,
                    tournamentName = tournament.name,
                    round = form.round.ifBlank { "Round 1" }
                )

                repository.createMatch(match)
                
                // Reset form
                _tournamentMatchForm.update { CreateTournamentMatchForm() }
                _uiState.update {
                    it.copy(
                        isCreatingMatch = false,
                        successMessage = "Match created: ${form.teamA} vs ${form.teamB}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isCreatingMatch = false, error = e.message)
                }
            }
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // AI FIXTURE ENGINE — Dual-Mode (Single Elimination + Round Robin)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    fun updateFixtureConfig(config: FixtureConfig) {
        _fixtureConfig.update { config }
    }

    /**
     * AI Mode: Generate all fixture match documents using a deterministic algorithm.
     * Supports both Single Elimination and Round Robin based on [FixtureConfig.tournamentType].
     * All matches are written to Firestore with proper timestamps and venue allocations.
     */
    fun generateAIFixtures() {
        val config = _fixtureConfig.value
        if (config.teams.size < 2) {
            _uiState.update { it.copy(error = "Need at least 2 teams to generate fixtures") }
            return
        }
        if (config.tournamentName.isBlank()) {
            _uiState.update { it.copy(error = "Please enter a tournament name") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingBracket = true) }
            try {
                val generator = FixtureGenerator()
                val startTime = config.startTime ?: Timestamp.now()

                val fixtures = when (config.tournamentType) {
                    TournamentType.SINGLE_ELIMINATION -> generator.generateSingleElimination(
                        teams          = config.teams,
                        matchType      = config.sportType.ifBlank { "Football" },
                        startTime      = startTime,
                        tournamentId   = config.tournamentId,
                        tournamentName = config.tournamentName,
                        venueName      = config.venue,
                        eligibilityText = config.eligibilityText,
                        intervalMinutes = config.intervalMinutes
                    )
                    TournamentType.ROUND_ROBIN -> generator.generateRoundRobin(
                        teams          = config.teams,
                        matchType      = config.sportType.ifBlank { "Football" },
                        startTime      = startTime,
                        tournamentId   = config.tournamentId,
                        tournamentName = config.tournamentName,
                        venueName      = config.venue,
                        eligibilityText = config.eligibilityText,
                        intervalMinutes = config.intervalMinutes
                    )
                }

                // Persist each fixture document to Firestore
                fixtures.forEach { match -> repository.createMatch(match) }

                _fixtureConfig.update { FixtureConfig() } // Reset config
                _uiState.update {
                    it.copy(
                        isGeneratingBracket = false,
                        successMessage = "🤖 AI Engine: ${fixtures.size} fixtures generated (${config.tournamentType.displayName})!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isGeneratingBracket = false, error = e.message)
                }
            }
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // MANUAL FIXTURE OVERRIDE — Admin can edit timings, venues, team swaps
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Apply a manual fixture edit. Triggers an immediate Firestore update.
     * Since getAllMatches() is a live snapshot, the student Home Screen
     * re-sorts "Upcoming Matches" instantly without a refresh.
     */
    fun applyManualEdit(edit: ManualFixtureEdit) {
        viewModelScope.launch {
            try {
                repository.applyManualFixtureEdit(edit)
                _uiState.update {
                    it.copy(successMessage = "✏️ Fixture updated successfully")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // REFEREE PANEL — Sport-Specific Player Scorecard Management
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** Load all scorecards for the currently selected match */
    private fun loadScorecardsForSelectedMatch(matchId: String) {
        viewModelScope.launch {
            repository.getScorecardsForMatch(matchId).collect { scorecards ->
                _uiState.update { it.copy(matchScorecards = scorecards) }
            }
        }
    }

    /**
     * Initialize blank scorecards for all registered players.
     * Called automatically when Admin starts a match, or manually from Referee Panel.
     */
    fun initializeScorecards() {
        val match = _uiState.value.selectedMatch ?: return
        viewModelScope.launch {
            try {
                repository.initializeScorecardsForMatch(match.id, match.sportType)
                _uiState.update { it.copy(successMessage = "📋 Scorecards initialized for all registered players") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Increment a specific stat for a player in the Referee Panel.
     * e.g. runsScored +1 for a Cricket player, aces +1 for a Badminton player.
     */
    fun incrementPlayerStat(playerId: String, statKey: String, delta: Int = 1) {
        val matchId = _uiState.value.selectedMatch?.id ?: return
        viewModelScope.launch {
            try {
                repository.incrementScorecardStat(matchId, playerId, statKey, delta)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // ── Sport-Specific Live Scoring ─────────────────────────────────────────

    fun selectMatchForScoring(match: Match) {
        val scheduled = match.scheduledTime
        val now = Timestamp.now()
        if (match.status == MatchStatus.SCHEDULED && scheduled != null && scheduled.seconds > now.seconds) {
            _uiState.update {
                it.copy(error = "Live scoring opens only when the scheduled time is reached or after the match is started")
            }
            return
        }
        _uiState.update { it.copy(selectedMatch = match) }
        loadScorecardsForSelectedMatch(match.id)
    }

    fun deselectMatch() {
        _uiState.update { it.copy(selectedMatch = null, matchScorecards = emptyList()) }
    }

    /** Universal dispatcher for any sport scoring action */
    fun applyScoringAction(action: com.sportflow.app.data.model.ScoringAction) {
        val matchId = _uiState.value.selectedMatch?.id ?: return
        viewModelScope.launch {
            try {
                repository.applyScoringAction(matchId, action)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /** Set-based sports (Badminton/Volleyball/TT): wrap up current set */
    fun completeSet(winnerTeam: String) {
        val matchId = _uiState.value.selectedMatch?.id ?: return
        viewModelScope.launch {
            try {
                repository.completeSet(matchId, winnerTeam)
                _uiState.update { it.copy(successMessage = "Set completed! Starting next set.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /** Cricket: advance the over counter by one ball */
    fun advanceOver() {
        val match = _uiState.value.selectedMatch ?: return
        viewModelScope.launch {
            try {
                repository.advanceOver(match.id, match.currentInning)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /** Cricket: switch from 1st to 2nd innings */
    fun startSecondInning() {
        val matchId = _uiState.value.selectedMatch?.id ?: return
        viewModelScope.launch {
            try {
                repository.startSecondInning(matchId)
                _uiState.update { it.copy(successMessage = "2nd Innings started!") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /** Basketball: advance to next quarter */
    fun nextQuarter() {
        val matchId = _uiState.value.selectedMatch?.id ?: return
        viewModelScope.launch {
            try {
                repository.nextQuarter(matchId)
                _uiState.update { it.copy(successMessage = "Next quarter started!") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /** Undo: decrement score by 1 */
    fun undoScore(team: String) {
        val matchId = _uiState.value.selectedMatch?.id ?: return
        viewModelScope.launch {
            try {
                repository.decrementScore(matchId, team)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /** Add a timeline highlight to the match */
    fun addHighlight(text: String) {
        val matchId = _uiState.value.selectedMatch?.id ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            try {
                repository.addHighlight(matchId, text)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }


    // ── Match Lifecycle ─────────────────────────────────────────────────────

    /** Scheduled → Live (also auto-initializes scorecards) */
    fun startMatch() {
        val match = _uiState.value.selectedMatch ?: return
        viewModelScope.launch {
            try {
                repository.startMatch(match.id)
                // Auto-initialize scorecards for all registered players
                repository.initializeScorecardsForMatch(match.id, match.sportType)
                _uiState.update { it.copy(successMessage = "Match started! Scorecards initialized.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /** Live → Halftime */
    fun halfTime() {
        val matchId = _uiState.value.selectedMatch?.id ?: return
        viewModelScope.launch {
            try {
                repository.halfTimeMatch(matchId)
                _uiState.update { it.copy(successMessage = "Half time!") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /** Halftime → Live (resume) */
    fun resumeMatch() {
        val matchId = _uiState.value.selectedMatch?.id ?: return
        viewModelScope.launch {
            try {
                repository.resumeMatch(matchId)
                _uiState.update { it.copy(successMessage = "Match resumed!") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /** Live/Halftime → Completed */
    fun completeMatch() {
        val matchId = _uiState.value.selectedMatch?.id ?: return
        viewModelScope.launch {
            try {
                repository.completeMatch(matchId)
                _uiState.update {
                    it.copy(
                        successMessage = "Match completed! Winner advanced.",
                        selectedMatch = null,
                        matchScorecards = emptyList()
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /** EMERGENCY FIX: Recalculate setsWonA/B from completedSets for a completed match */
    fun fixCompletedMatchScores() {
        val matchId = _uiState.value.selectedMatch?.id ?: return
        viewModelScope.launch {
            try {
                repository.fixCompletedMatchScores(matchId)
                _uiState.update { it.copy(successMessage = "Match scores fixed! Check My Matches.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Fix failed: ${e.message}") }
            }
        }
    }

    // ── Payments ─────────────────────────────────────────────────────────────

    fun verifyPayment(paymentId: String) {
        viewModelScope.launch {
            try {
                repository.verifyPayment(paymentId)
                _uiState.update { it.copy(successMessage = "Payment verified") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun rejectPayment(paymentId: String) {
        viewModelScope.launch {
            try {
                repository.rejectPayment(paymentId)
                _uiState.update { it.copy(successMessage = "Payment rejected") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun generateBracket(tournamentId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingBracket = true) }
            try {
                repository.generateBracket(tournamentId)
                _uiState.update {
                    it.copy(
                        isGeneratingBracket = false,
                        successMessage = "Tournament generated successfully!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isGeneratingBracket = false, error = e.message)
                }
            }
        }
    }

    /**
     * Generates a complete single-elimination fixture list using [FixtureGenerator]
     * for a given tournament and persists all match documents to Firestore.
     *
     * @param tournamentId   Target tournament document ID
     * @param teams          Ordered or pre-seeded team list (will be shuffled internally)
     * @param matchType      Sport name, e.g. "Cricket"
     * @param venueName      GNITS venue display name
     * @param eligibilityText Department restriction, e.g. "IT students only"
     */
    fun generateFixtures(
        tournamentId: String,
        tournamentName: String,
        teams: List<String>,
        matchType: String,
        venueName: String = GnitsVenue.INDOOR_SPORTS_ROOM.displayName,
        eligibilityText: String = "All GNITS students"
    ) {
        if (teams.size < 2) {
            _uiState.update { it.copy(error = "Need at least 2 teams to generate fixtures") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingBracket = true) }
            try {
                val generator = FixtureGenerator()
                val fixtures  = generator.generateSingleElimination(
                    teams          = teams,
                    matchType      = matchType,
                    startTime      = Timestamp.now(),
                    tournamentId   = tournamentId,
                    tournamentName = tournamentName,
                    venueName      = venueName,
                    eligibilityText = eligibilityText
                )
                // Persist each fixture document
                fixtures.forEach { match -> repository.createMatch(match) }
                _uiState.update {
                    it.copy(
                        isGeneratingBracket = false,
                        successMessage = "⚽ ${fixtures.size} fixtures generated for $tournamentName!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isGeneratingBracket = false, error = e.message)
                }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(successMessage = null, error = null) }
    }

    // ── Manual Broadcast Updates ─────────────────────────────────────────────────

    /**
     * Send a manual broadcast update to players.
     * @param title Update title
     * @param body Update message body
     * @param targetSport Empty string = All Users, otherwise specific sport name
     */
    fun sendBroadcastUpdate(title: String, body: String, targetSport: String = "") {
        viewModelScope.launch {
            try {
                if (title.isBlank() || body.isBlank()) {
                    _uiState.update { it.copy(error = "Title and body cannot be empty") }
                    return@launch
                }
                
                repository.createBroadcastUpdate(title, body, targetSport)
                
                val targetText = if (targetSport.isBlank()) "All Users" else "$targetSport players"
                _uiState.update { 
                    it.copy(successMessage = "Broadcast sent to $targetText successfully!") 
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to send broadcast: ${e.message}") }
            }
        }
    }

    // ── Match Management ─────────────────────────────────────────────────────

    fun deleteMatch(matchId: String) {
        viewModelScope.launch {
            try {
                repository.deleteMatch(matchId)
                _uiState.update { it.copy(successMessage = "Match deleted successfully") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to delete match") }
            }
        }
    }

    // ── Admin Registration Detail View ────────────────────────────────────

    fun selectRegistration(registration: Registration) {
        _uiState.update { it.copy(selectedRegistration = registration) }
        // Mark as seen when admin clicks on it
        viewModelScope.launch {
            repository.markRegistrationSeen(registration.id)
        }
    }

    fun deselectRegistration() {
        _uiState.update { it.copy(selectedRegistration = null) }
    }

    fun markAllRegistrationsSeen() {
        viewModelScope.launch {
            repository.markAllRegistrationsSeen()
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // ADMIN APPROVAL SYSTEM
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    fun acceptRegistration(registrationId: String) {
        viewModelScope.launch {
            try {
                repository.acceptRegistration(registrationId)
                _uiState.update { it.copy(successMessage = "✅ Registration accepted") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun denyRegistration(registrationId: String, reason: String) {
        viewModelScope.launch {
            try {
                repository.denyRegistration(registrationId, reason)
                _uiState.update { it.copy(successMessage = "❌ Registration denied") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun generateFixturesFromApprovedRegistrations(matchId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingBracket = true, error = null) }
            try {
                val created = repository.generateFixturesFromApprovedRegistrations(matchId)
                _uiState.update {
                    it.copy(
                        isGeneratingBracket = false,
                        successMessage = "$created fixture(s) generated from approved registrations"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isGeneratingBracket = false, error = e.message ?: "Fixture generation failed")
                }
            }
        }
    }

    fun generateFixturesFromApprovedTournamentRegistrations(tournamentId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingBracket = true, error = null) }
            try {
                val created = repository.generateFixturesFromApprovedTournamentRegistrations(tournamentId)
                _uiState.update {
                    it.copy(
                        isGeneratingBracket = false,
                        successMessage = "$created fixture(s) generated from approved tournament registrations"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isGeneratingBracket = false, error = e.message ?: "Fixture generation failed")
                }
            }
        }
    }

    fun markRegistrationAsSeen(registrationId: String) {
        viewModelScope.launch {
            try {
                repository.markRegistrationAsSeen(registrationId)
            } catch (_: Exception) {
                // Non-critical
            }
        }
    }

    // ── Create Fixtures Between Users ────────────────────────────────────────

    fun createFixturesBetweenUsers(
        tournamentId: String,
        sportType: String,
        venue: String,
        startTime: Timestamp,
        intervalMinutes: Long
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingBracket = true, error = null) }
            try {
                val createdMatchIds = repository.createFixturesBetweenRegisteredUsers(
                    tournamentId = tournamentId,
                    sportType = sportType,
                    venue = venue,
                    startTime = startTime,
                    intervalMinutes = intervalMinutes
                )
                _uiState.update {
                    it.copy(
                        isGeneratingBracket = false,
                        successMessage = "✅ Created ${createdMatchIds.size} fixtures between registered users! Both users will see their matches in My Matches."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGeneratingBracket = false,
                        error = e.message ?: "Failed to create fixtures"
                    )
                }
            }
        }
    }
}

private fun parseAdminDateTime(raw: String): Timestamp? {
    return try {
        val formatter = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
        formatter.isLenient = false
        val parsed = formatter.parse(raw.trim()) ?: return null
        Timestamp(parsed)
    } catch (_: Exception) {
        null
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// MY MATCHES VIEWMODEL — Player’s registered events, filtered by their UID
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

data class MyMatchesUiState(
    val myMatches: List<Match> = emptyList(),
    val myRegistrations: Map<String, RegistrationStatus> = emptyMap(), // matchId -> status
    val isLoading: Boolean = true,
    val error: String? = null,
    val emptyMessage: String = "You haven't registered for any events yet"
)

@HiltViewModel
class MyMatchesViewModel @Inject constructor(
    private val repository: SportFlowRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyMatchesUiState())
    val uiState: StateFlow<MyMatchesUiState> = _uiState.asStateFlow()

    init {
        loadMyMatches()
        loadMyRegistrations()
        // Auto-link matches to user based on name matching
        autoLinkMatches()
    }

    private fun loadMyMatches() {
        viewModelScope.launch {
            try {
                repository.observeMyRegisteredMatchesRealtime().collect { matches ->
                    android.util.Log.d("MyMatchesViewModel", "Received ${matches.size} matches from repository")
                    matches.forEach { match ->
                        android.util.Log.d("MyMatchesViewModel", "  - ${match.status}: ${match.teamA} vs ${match.teamB} | Score: ${match.scoreA}-${match.scoreB}")
                    }
                    
                    val scheduled = matches.count { it.status == MatchStatus.SCHEDULED }
                    val live = matches.count { it.status == MatchStatus.LIVE || it.status == MatchStatus.HALFTIME }
                    val completed = matches.count { it.status == MatchStatus.COMPLETED }
                    
                    android.util.Log.d("MyMatchesViewModel", "Tab counts - Scheduled: $scheduled, Live: $live, Completed: $completed")
                    
                    _uiState.update {
                        it.copy(
                            myMatches  = matches,
                            isLoading  = false,
                            emptyMessage = if (repository.getCurrentUser() == null)
                                "Sign in to see your registered events"
                            else
                                "You haven't registered for any events yet"
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MyMatchesViewModel", "Error loading matches", e)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun loadMyRegistrations() {
        viewModelScope.launch {
            try {
                repository.observeMyRegistrationStatuses().collect { statusMap ->
                    _uiState.update { it.copy(myRegistrations = statusMap) }
                }
            } catch (_: Exception) {
                // Non-critical
            }
        }
    }

    private fun autoLinkMatches() {
        viewModelScope.launch {
            try {
                repository.autoLinkMatchesToUsers()
            } catch (_: Exception) {
                // Non-critical - just for fixing existing data
            }
        }
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        loadMyMatches()
        autoLinkMatches()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// REGISTRATION VIEWMODEL — Single source of truth via Firestore live snapshots
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

data class RegistrationUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    /**
     * Live set of matchIds for which the current user has an active registration.
     * Backed by a real-time Firestore collectionGroup snapshot — any external
     * deletion (e.g. admin or cancellation) reflects immediately across ALL screens.
     */
    val registeredMatchIds: Set<String> = emptySet(),
    val registeredTournamentIds: Set<String> = emptySet(),
    /** matchId → actively processing (button shows spinner) */
    val loadingMatchIds: Set<String> = emptySet(),
    /** Current user profile for pre-filling registration forms */
    val currentUser: com.sportflow.app.data.model.SportUser? = null
)

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// SCORECARD VIEWMODEL — Player's personal performance across matches
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

data class ScorecardUiState(
    val selectedMatchId: String? = null,
    val scorecard: PlayerScorecard? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ScorecardViewModel @Inject constructor(
    private val repository: SportFlowRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScorecardUiState())
    val uiState: StateFlow<ScorecardUiState> = _uiState.asStateFlow()

    /**
     * Load the current player's personal scorecard for the given match.
     * This is called when the user taps "View My Performance" on a completed or live match.
     */
    fun loadScorecard(matchId: String) {
        if (matchId == _uiState.value.selectedMatchId) return
        _uiState.update { it.copy(selectedMatchId = matchId, isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                repository.getMyScorecard(matchId).collect { scorecard ->
                    _uiState.update {
                        it.copy(
                            scorecard = scorecard,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun clearScorecard() {
        _uiState.update { ScorecardUiState() }
    }
}


// NOTE: NotificationUiState is defined in NotificationViewModel.kt.
// It is not re-declared here to avoid duplicate class compilation errors.

data class UpdatesUiState(
    val announcements: List<Announcement> = emptyList(),
    val broadcastUpdates: List<com.sportflow.app.data.model.Update> = emptyList(),
    val lastOpenedAt: Timestamp? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class UpdatesViewModel @Inject constructor(
    private val repository: SportFlowRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(UpdatesUiState())
    val uiState: StateFlow<UpdatesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAnnouncements().collect { announcements ->
                _uiState.update { it.copy(announcements = announcements, isLoading = false) }
            }
        }
        viewModelScope.launch {
            repository.observePlayerUpdates().collect { updates ->
                _uiState.update { it.copy(broadcastUpdates = updates) }
            }
        }
        viewModelScope.launch {
            repository.observeLastUpdatesOpenedAt().collect { ts ->
                _uiState.update { it.copy(lastOpenedAt = ts) }
            }
        }
    }

    fun markOpened() {
        viewModelScope.launch {
            repository.markUpdatesOpened()
        }
    }
}
