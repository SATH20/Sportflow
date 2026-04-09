package com.sportflow.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportflow.app.data.model.*
import com.sportflow.app.data.repository.SportFlowRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// HOME VIEWMODEL
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

data class HomeUiState(
    val upcomingMatches: List<Match> = emptyList(),
    val liveMatches: List<Match> = emptyList(),
    val tournaments: List<Tournament> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: SportFlowRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        if (repository.isFirebaseAvailable) {
            loadFromFirebase()
        } else {
            loadDemoData()
        }
    }

    private fun loadFromFirebase() {
        viewModelScope.launch {
            try {
                repository.getUpcomingMatches().collect { matches ->
                    _uiState.update { it.copy(upcomingMatches = matches, isLoading = false) }
                }
            } catch (e: Exception) {
                loadDemoData()
            }
        }
        viewModelScope.launch {
            try {
                repository.getLiveMatches().collect { matches ->
                    _uiState.update { it.copy(liveMatches = matches) }
                }
            } catch (_: Exception) {}
        }
        viewModelScope.launch {
            try {
                repository.getTournaments().collect { tournaments ->
                    _uiState.update { it.copy(tournaments = tournaments) }
                }
            } catch (_: Exception) {}
        }
    }

    fun loadDemoData() {
        _uiState.update {
            it.copy(
                isLoading = false,
                liveMatches = listOf(
                    Match(
                        id = "live1",
                        sportType = "Football",
                        teamA = "Thunder FC",
                        teamB = "Storm United",
                        scoreA = 2,
                        scoreB = 1,
                        status = MatchStatus.LIVE,
                        venue = "National Stadium",
                        currentPeriod = "2nd Half",
                        elapsedTime = "67'",
                        tournamentName = "Premier League"
                    ),
                    Match(
                        id = "live2",
                        sportType = "Basketball",
                        teamA = "Night Hawks",
                        teamB = "Solar Bears",
                        scoreA = 78,
                        scoreB = 82,
                        status = MatchStatus.LIVE,
                        venue = "City Arena",
                        currentPeriod = "Q3",
                        elapsedTime = "04:22",
                        tournamentName = "National Cup"
                    )
                ),
                upcomingMatches = listOf(
                    Match(
                        id = "up1",
                        sportType = "Cricket",
                        teamA = "Royal Kings",
                        teamB = "Super Giants",
                        status = MatchStatus.UPCOMING,
                        venue = "Eden Gardens",
                        tournamentName = "Champions Trophy"
                    ),
                    Match(
                        id = "up2",
                        sportType = "Football",
                        teamA = "Phoenix Rising",
                        teamB = "Arctic Wolves",
                        status = MatchStatus.UPCOMING,
                        venue = "Olympic Park",
                        tournamentName = "Premier League"
                    ),
                    Match(
                        id = "up3",
                        sportType = "Badminton",
                        teamA = "Shuttle Stars",
                        teamB = "Net Blazers",
                        status = MatchStatus.UPCOMING,
                        venue = "Indoor Complex",
                        tournamentName = "Open Championship"
                    )
                ),
                tournaments = listOf(
                    Tournament(
                        id = "t1",
                        name = "Premier League 2026",
                        sport = "Football",
                        status = TournamentStatus.IN_PROGRESS,
                        maxTeams = 16,
                        prizePool = "₹5,00,000",
                        venue = "National Stadium"
                    ),
                    Tournament(
                        id = "t2",
                        name = "Champions Trophy",
                        sport = "Cricket",
                        status = TournamentStatus.REGISTRATION,
                        maxTeams = 8,
                        prizePool = "₹2,50,000",
                        venue = "Eden Gardens"
                    )
                )
            )
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// LIVE MATCH VIEWMODEL
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
        if (repository.isFirebaseAvailable) {
            loadFromFirebase()
        } else {
            loadDemoData()
        }
    }

    private fun loadFromFirebase() {
        viewModelScope.launch {
            try {
                repository.getLiveMatches().collect { matches ->
                    _uiState.update {
                        it.copy(
                            liveMatches = matches,
                            selectedMatch = it.selectedMatch ?: matches.firstOrNull(),
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                loadDemoData()
            }
        }
    }

    fun selectMatch(match: Match) {
        _uiState.update { it.copy(selectedMatch = match) }
    }

    fun loadDemoData() {
        val demoMatches = listOf(
            Match(
                id = "live1",
                sportType = "Football",
                teamA = "Thunder FC",
                teamB = "Storm United",
                scoreA = 2,
                scoreB = 1,
                status = MatchStatus.LIVE,
                venue = "National Stadium",
                currentPeriod = "2nd Half",
                elapsedTime = "72'",
                tournamentName = "Premier League",
                round = "Quarter Final",
                highlights = listOf(
                    "⚽ 12' - Thunder FC scores! Header by R. Kumar",
                    "⚽ 34' - Storm United equalizes! Long-range shot by S. Patel",
                    "🟨 45' - Yellow card to A. Singh (Storm United)",
                    "⚽ 58' - Thunder FC takes the lead! Penalty converted by V. Sharma"
                )
            ),
            Match(
                id = "live2",
                sportType = "Basketball",
                teamA = "Night Hawks",
                teamB = "Solar Bears",
                scoreA = 78,
                scoreB = 82,
                status = MatchStatus.LIVE,
                venue = "City Arena",
                currentPeriod = "Q3",
                elapsedTime = "04:22",
                tournamentName = "National Cup",
                round = "Semi Final",
                highlights = listOf(
                    "🏀 Q1 - Solar Bears lead 22-18",
                    "🏀 Q2 - Night Hawks rally back, tied at 44",
                    "🔥 3-pointer streak by J. Williams (Night Hawks)",
                    "🏀 Q3 - Solar Bears pull ahead with fast breaks"
                )
            ),
            Match(
                id = "live3",
                sportType = "Cricket",
                teamA = "Royal Kings",
                teamB = "Super Giants",
                scoreA = 184,
                scoreB = 156,
                status = MatchStatus.LIVE,
                venue = "Eden Gardens",
                currentPeriod = "2nd Innings",
                elapsedTime = "Over 16.4",
                tournamentName = "Champions Trophy",
                round = "Final",
                highlights = listOf(
                    "🏏 Royal Kings set target of 185",
                    "💥 Six by M. Kohli! 32 off 18 balls",
                    "🎯 Wicket! Clean bowled by R. Ashwin",
                    "🏏 Super Giants need 29 off 20 balls"
                )
            )
        )
        _uiState.update {
            it.copy(
                liveMatches = demoMatches,
                selectedMatch = demoMatches.first(),
                isLoading = false
            )
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// BRACKET VIEWMODEL
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
        if (repository.isFirebaseAvailable) {
            loadFromFirebase()
        } else {
            loadDemoData()
        }
    }

    private fun loadFromFirebase() {
        viewModelScope.launch {
            try {
                repository.getTournaments().collect { tournaments ->
                    _uiState.update {
                        it.copy(
                            tournaments = tournaments,
                            selectedTournament = it.selectedTournament ?: tournaments.firstOrNull(),
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                loadDemoData()
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

    fun loadDemoData() {
        val demoTournament = Tournament(
            id = "t1",
            name = "Premier League 2026",
            sport = "Football",
            status = TournamentStatus.IN_PROGRESS,
            maxTeams = 8,
            bracketGenerated = true
        )
        val demoBracket = listOf(
            // Quarter Finals (Round 1)
            BracketNode("qf1", 1, 0, "Thunder FC", "Phoenix Rising", 3, 1, "Thunder FC"),
            BracketNode("qf2", 1, 1, "Storm United", "Arctic Wolves", 2, 2, null),
            BracketNode("qf3", 1, 2, "Night Hawks", "Solar Bears", 4, 2, "Night Hawks"),
            BracketNode("qf4", 1, 3, "Royal Kings", "Super Giants", 1, 3, "Super Giants"),
            // Semi Finals (Round 2)
            BracketNode("sf1", 2, 0, "Thunder FC", null, null, null, null),
            BracketNode("sf2", 2, 1, "Night Hawks", "Super Giants", null, null, null),
            // Final (Round 3)
            BracketNode("f1", 3, 0, null, null, null, null, null)
        )
        _uiState.update {
            it.copy(
                tournaments = listOf(demoTournament),
                selectedTournament = demoTournament,
                bracketNodes = demoBracket,
                isLoading = false
            )
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// ADMIN VIEWMODEL
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

data class AdminUiState(
    val pendingPayments: List<Payment> = emptyList(),
    val tournaments: List<Tournament> = emptyList(),
    val allMatches: List<Match> = emptyList(),
    val isLoading: Boolean = true,
    val isGeneratingBracket: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val repository: SportFlowRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        if (repository.isFirebaseAvailable) {
            loadFromFirebase()
        } else {
            loadDemoData()
        }
    }

    private fun loadFromFirebase() {
        viewModelScope.launch {
            try {
                repository.getPendingPayments().collect { payments ->
                    _uiState.update { it.copy(pendingPayments = payments, isLoading = false) }
                }
            } catch (e: Exception) {
                loadDemoData()
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
                    _uiState.update { it.copy(allMatches = matches) }
                }
            } catch (_: Exception) {}
        }
    }

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
                        successMessage = "Bracket generated successfully!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGeneratingBracket = false,
                        error = e.message
                    )
                }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(successMessage = null, error = null) }
    }

    fun loadDemoData() {
        _uiState.update {
            it.copy(
                isLoading = false,
                pendingPayments = listOf(
                    Payment("p1", "team1", "Thunder FC", "t1", 5000.0, PaymentStatus.PENDING, transactionId = "TXN001234"),
                    Payment("p2", "team2", "Storm United", "t1", 5000.0, PaymentStatus.PENDING, transactionId = "TXN001235"),
                    Payment("p3", "team3", "Night Hawks", "t1", 5000.0, PaymentStatus.PENDING, transactionId = "TXN001236")
                ),
                tournaments = listOf(
                    Tournament("t1", "Premier League 2026", "Football", TournamentStatus.REGISTRATION, maxTeams = 16, prizePool = "₹5,00,000"),
                    Tournament("t2", "Champions Trophy", "Cricket", TournamentStatus.IN_PROGRESS, maxTeams = 8, prizePool = "₹2,50,000", bracketGenerated = true)
                ),
                allMatches = listOf(
                    Match("m1", "Football", "Thunder FC", "Storm United", scoreA = 2, scoreB = 1, status = MatchStatus.COMPLETED, tournamentName = "Premier League"),
                    Match("m2", "Cricket", "Royal Kings", "Super Giants", scoreA = 184, scoreB = 156, status = MatchStatus.LIVE, tournamentName = "Champions Trophy")
                )
            )
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// AUTH VIEWMODEL
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

data class AuthUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: SportFlowRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        _uiState.update {
            it.copy(isLoggedIn = try {
                repository.getCurrentUser() != null
            } catch (_: Exception) {
                false
            })
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                repository.signIn(email, password)
                _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun signUp(email: String, password: String, name: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                repository.signUp(email, password, name)
                _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun signOut() {
        try {
            repository.signOut()
        } catch (_: Exception) {}
        _uiState.update { AuthUiState() }
    }
}
