package com.sportflow.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportflow.app.data.model.Match
import com.sportflow.app.data.model.SportUser
import com.sportflow.app.data.model.UserRole
import com.sportflow.app.data.repository.SportFlowRepository
import com.sportflow.app.ui.components.RegistrationData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// NOTE: RegistrationUiState is declared in ViewModels.kt (canonical).
// It is intentionally NOT redeclared here to avoid "Redeclaration" errors.

@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val repository: SportFlowRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegistrationUiState())
    val uiState: StateFlow<RegistrationUiState> = _uiState.asStateFlow()

    init {
        loadCurrentUser()
        observeRegistrations()
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            try {
                val user = repository.getCurrentUserProfile()
                _uiState.update { it.copy(currentUser = user) }
            } catch (_: Exception) {
                // Non-critical — user can still register with manual input
            }
        }
    }

    /**
     * Real-time listener for all matchIds the current user is registered for.
     * This is the SINGLE SOURCE OF TRUTH for button state across Home and My Matches.
     */
    private fun observeRegistrations() {
        viewModelScope.launch {
            repository.observeMyRegisteredMatchIds().collect { ids ->
                _uiState.update { it.copy(registeredMatchIds = ids) }
            }
        }
    }

    /**
     * Check if the current user is registered for a specific match.
     * Uses the in-memory set for instant lookup (no Firestore query).
     */
    fun isRegisteredFor(matchId: String): Boolean {
        return matchId in _uiState.value.registeredMatchIds
    }

    /**
     * Register for a match with advanced 3-step data.
     * Updates the user profile with the provided data before registration.
     */
    fun registerForMatch(match: Match, data: RegistrationData) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Update user profile with registration data
                val uid = repository.getCurrentUser()?.uid
                if (uid != null) {
                    repository.updateUserProfile(
                        uid = uid,
                        rollNumber = data.rollNumber,
                        department = data.department,
                        yearOfStudy = data.yearOfStudy,
                        preferredSportRole = data.sportRole
                    )
                }

                // Perform registration
                repository.registerForMatch(match)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        successMessage = "✅ Registration successful! You're confirmed for ${match.teamA} vs ${match.teamB}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Registration failed"
                    )
                }
            }
        }
    }

    /**
     * Simple register (from Home feed quick-register button).
     * Used by HomeFeedScreen which passes a UserRole.
     */
    fun register(match: Match, role: UserRole) {
        if (role == UserRole.ADMIN) {
            _uiState.update { it.copy(error = "Admins cannot register for matches") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(loadingMatchIds = it.loadingMatchIds + match.id, error = null) }
            try {
                repository.registerForMatch(match)
                _uiState.update {
                    it.copy(
                        loadingMatchIds = it.loadingMatchIds - match.id,
                        successMessage = "✅ Registered for ${match.teamA} vs ${match.teamB}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        loadingMatchIds = it.loadingMatchIds - match.id,
                        error = e.message ?: "Registration failed"
                    )
                }
            }
        }
    }

    /**
     * Cancel registration for a match.
     * Overload used by HomeFeedScreen (passes UserRole).
     */
    fun cancelRegistration(matchId: String, role: UserRole) {
        if (role == UserRole.ADMIN) {
            _uiState.update { it.copy(error = "Admins cannot cancel player registrations from here") }
            return
        }
        cancelRegistration(matchId, matchName = "")
    }

    /**
     * Cancel registration for a match — primary implementation.
     * Used by HomeFeedScreenComplete / MyMatchesScreenComplete which pass matchName.
     */
    fun cancelRegistration(matchId: String, matchName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                repository.cancelRegistration(matchId)
                val label = if (matchName.isBlank()) "match" else matchName
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        successMessage = "Registration cancelled for $label"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Cancellation failed"
                    )
                }
            }
        }
    }

    /**
     * Check eligibility for a match before showing registration form.
     * Returns null if eligible, error message if not eligible.
     */
    suspend fun checkEligibility(match: Match): String? {
        val user = repository.getCurrentUserProfile() ?: return "User profile not found"

        // Check department eligibility
        if (match.allowedDepartments.isNotEmpty() &&
            user.department.isNotBlank() &&
            match.allowedDepartments.none { it.equals(user.department, ignoreCase = true) }
        ) {
            val allowed = match.allowedDepartments.joinToString(", ")
            return "Only $allowed department(s) may register for this event"
        }

        // Check year eligibility
        if (match.allowedYears.isNotEmpty() &&
            user.yearOfStudy.isNotBlank() &&
            match.allowedYears.none { it.equals(user.yearOfStudy, ignoreCase = true) }
        ) {
            val allowed = match.allowedYears.joinToString(", ")
            return "Only $allowed student(s) may register for this event"
        }

        // Check squad capacity
        val maxSquad = if (match.maxSquadSize > 0) match.maxSquadSize else match.maxRegistrations
        val currentCount = if (match.maxSquadSize > 0) match.currentSquadCount else match.registrationCount
        if (maxSquad > 0 && currentCount >= maxSquad) {
            return "Squad is full ($currentCount/$maxSquad slots taken)"
        }

        return null // Eligible
    }

    /** Used by HomeFeedScreen and MyMatchesScreen */
    fun clearMessage() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }

    /** Alias kept for HomeFeedScreenComplete and MyMatchesScreenComplete */
    fun clearMessages() = clearMessage()
}
