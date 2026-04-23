package com.sportflow.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.sportflow.app.data.model.*
import com.sportflow.app.data.repository.SportFlowRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// ADVANCED REGISTRATION VIEWMODEL WITH FIRESTORE TRANSACTIONS
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

data class AdvancedRegistrationUiState(
    /** matchId → whether user is registered */
    val registeredMatchIds: Set<String> = emptySet(),
    /** matchId → actively processing (button loading) */
    val loadingMatchIds: Set<String> = emptySet(),
    /** matchId → current squad count (real-time) */
    val squadCountMap: Map<String, Int> = emptyMap(),
    /** matchId → max squads allowed */
    val maxSquadsMap: Map<String, Int> = emptyMap(),
    val successMessage: String? = null,
    val error: String? = null,
    val showBottomSheet: Boolean = false,
    val selectedMatch: Match? = null
)

@HiltViewModel
class AdvancedRegistrationViewModel @Inject constructor(
    private val repository: SportFlowRepository,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdvancedRegistrationUiState())
    val uiState: StateFlow<AdvancedRegistrationUiState> = _uiState.asStateFlow()

    /**
     * Check and cache whether the current user is already registered for [matchId].
     * Also fetches real-time squad count.
     */
    fun checkRegistration(matchId: String) {
        viewModelScope.launch {
            try {
                val reg = repository.getRegistrationStatus(matchId)
                if (reg != null) {
                    _uiState.update { it.copy(registeredMatchIds = it.registeredMatchIds + matchId) }
                }
                
                // Fetch real-time squad count
                fetchSquadCount(matchId)
            } catch (_: Exception) {}
        }
    }

    /**
     * Fetch real-time squad count from Firestore
     */
    private suspend fun fetchSquadCount(matchId: String) {
        try {
            val matchDoc = firestore.collection("gnits_matches")
                .document(matchId)
                .get()
                .await()
            
            val currentCount = matchDoc.getLong("currentSquadCount")?.toInt() ?: 0
            val maxSquads = matchDoc.getLong("maxSquadSize")?.toInt() ?: 0
            
            _uiState.update {
                it.copy(
                    squadCountMap = it.squadCountMap + (matchId to currentCount),
                    maxSquadsMap = it.maxSquadsMap + (matchId to maxSquads)
                )
            }
        } catch (_: Exception) {}
    }

    /**
     * Show registration bottom sheet for a match
     */
    fun showRegistrationSheet(match: Match) {
        _uiState.update {
            it.copy(
                showBottomSheet = true,
                selectedMatch = match
            )
        }
    }

    /**
     * Hide registration bottom sheet
     */
    fun hideRegistrationSheet() {
        _uiState.update {
            it.copy(
                showBottomSheet = false,
                selectedMatch = null
            )
        }
    }

    /**
     * Perform a transactional registration with squad details.
     * Uses Firestore Transaction to ensure atomic updates of:
     * - Registration document creation
     * - Match.currentSquadCount increment
     * - Match.registrationCount increment
     * 
     * This prevents race conditions and ensures data consistency.
     */
    fun registerWithSquad(
        match: Match,
        squadName: String,
        captainName: String,
        captainPhone: String,
        squadSize: Int
    ) {
        val matchId = match.id
        val currentUser = repository.getCurrentUser()
        
        if (currentUser == null) {
            _uiState.update { it.copy(error = "You must be signed in to register") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(loadingMatchIds = it.loadingMatchIds + matchId, error = null) }
            
            try {
                // Fetch current user profile for department and year
                var userProfile: SportUser? = null
                repository.getUserProfile(currentUser.uid).first { profile ->
                    userProfile = profile
                    true
                }

                if (userProfile == null) {
                    throw Exception("User profile not found. Please complete your profile.")
                }

                // Perform Firestore Transaction
                firestore.runTransaction { transaction ->
                    val matchRef = firestore.collection("gnits_matches").document(matchId)
                    val matchSnapshot = transaction.get(matchRef)

                    // Check current squad count
                    val currentSquadCount = matchSnapshot.getLong("currentSquadCount")?.toInt() ?: 0
                    val maxSquads = matchSnapshot.getLong("maxSquadSize")?.toInt() ?: 0

                    // Validate capacity
                    if (maxSquads > 0 && currentSquadCount >= maxSquads) {
                        throw Exception("Squad capacity is full. Registration closed.")
                    }

                    // Create registration document
                    val registrationId = "${currentUser.uid}_${System.currentTimeMillis()}"
                    val registrationRef = matchRef
                        .collection("registrations")
                        .document(registrationId)

                    val registration = Registration(
                        id = registrationId,
                        uid = currentUser.uid,
                        matchId = matchId,
                        tournamentId = match.tournamentId,
                        userName = userProfile!!.displayName,
                        department = userProfile!!.department,
                        yearOfStudy = userProfile!!.yearOfStudy,
                        rollNumber = userProfile!!.rollNumber,
                        status = RegistrationStatus.CONFIRMED,
                        registeredAt = Timestamp.now(),
                        squadName = squadName,
                        captainName = captainName,
                        captainPhone = captainPhone,
                        squadSize = squadSize,
                        sportRole = userProfile!!.preferredSportRole,
                        sportType = match.sportType,
                        matchName = "${match.teamA} vs ${match.teamB}"
                    )

                    // Atomic operations
                    transaction.set(registrationRef, registration)
                    transaction.update(
                        matchRef,
                        mapOf(
                            "currentSquadCount" to FieldValue.increment(1),
                            "registrationCount" to FieldValue.increment(1)
                        )
                    )
                }.await()

                // Write to top-level gnits_registrations for Admin Data Bridge
                try {
                    val bridgeDoc = hashMapOf(
                        "uid"          to currentUser.uid,
                        "matchId"      to matchId,
                        "tournamentId" to match.tournamentId,
                        "userName"     to userProfile!!.displayName,
                        "department"   to userProfile!!.department,
                        "yearOfStudy"  to userProfile!!.yearOfStudy,
                        "rollNumber"   to userProfile!!.rollNumber,
                        "sportRole"    to userProfile!!.preferredSportRole,
                        "sportType"    to match.sportType,
                        "matchName"    to "${match.teamA} vs ${match.teamB}",
                        "status"       to RegistrationStatus.CONFIRMED.name,
                        "registeredAt" to Timestamp.now(),
                        "seen"         to false
                    )
                    firestore.collection("gnits_registrations")
                        .document("${currentUser.uid}_${matchId}")
                        .set(bridgeDoc)
                        .await()
                } catch (_: Exception) {
                    // Non-critical — admin bridge failure should not block player registration
                }

                // Success - update UI state
                _uiState.update {
                    it.copy(
                        registeredMatchIds = it.registeredMatchIds + matchId,
                        loadingMatchIds = it.loadingMatchIds - matchId,
                        squadCountMap = it.squadCountMap + (matchId to (it.squadCountMap[matchId] ?: 0) + 1),
                        successMessage = "🎉 Successfully registered for ${match.teamA} vs ${match.teamB}!",
                        showBottomSheet = false,
                        selectedMatch = null
                    )
                }

                // Refresh squad count
                fetchSquadCount(matchId)

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        loadingMatchIds = it.loadingMatchIds - matchId,
                        error = e.message ?: "Registration failed. Please try again.",
                        showBottomSheet = false
                    )
                }
            }
        }
    }

    /**
     * Cancel an existing registration using Firestore Transaction.
     * Atomically:
     * - Deletes registration document
     * - Decrements Match.currentSquadCount
     * - Decrements Match.registrationCount
     */
    fun cancelRegistration(matchId: String) {
        val currentUser = repository.getCurrentUser()
        
        if (currentUser == null) {
            _uiState.update { it.copy(error = "You must be signed in") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(loadingMatchIds = it.loadingMatchIds + matchId, error = null) }
            
            try {
                // Find user's registration document
                val matchRef = firestore.collection("gnits_matches").document(matchId)
                val registrationsSnapshot = matchRef
                    .collection("registrations")
                    .whereEqualTo("uid", currentUser.uid)
                    .get()
                    .await()

                if (registrationsSnapshot.isEmpty) {
                    throw Exception("No registration found")
                }

                val registrationDoc = registrationsSnapshot.documents.first()

                // Perform Firestore Transaction
                firestore.runTransaction { transaction ->
                    val matchSnapshot = transaction.get(matchRef)
                    val currentSquadCount = matchSnapshot.getLong("currentSquadCount")?.toInt() ?: 0

                    // Prevent negative counts
                    if (currentSquadCount <= 0) {
                        throw Exception("Cannot cancel: squad count is already zero")
                    }

                    // Atomic operations
                    transaction.delete(registrationDoc.reference)
                    transaction.update(
                        matchRef,
                        mapOf(
                            "currentSquadCount" to FieldValue.increment(-1),
                            "registrationCount" to FieldValue.increment(-1)
                        )
                    )
                }.await()

                // Success - update UI state
                _uiState.update {
                    it.copy(
                        registeredMatchIds = it.registeredMatchIds - matchId,
                        loadingMatchIds = it.loadingMatchIds - matchId,
                        squadCountMap = it.squadCountMap + (matchId to maxOf(0, (it.squadCountMap[matchId] ?: 1) - 1)),
                        successMessage = "Registration cancelled successfully"
                    )
                }

                // Refresh squad count
                fetchSquadCount(matchId)

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        loadingMatchIds = it.loadingMatchIds - matchId,
                        error = e.message ?: "Failed to cancel registration"
                    )
                }
            }
        }
    }

    /**
     * Subscribe to real-time squad count updates for a match
     */
    fun subscribeToSquadUpdates(matchId: String) {
        viewModelScope.launch {
            firestore.collection("gnits_matches")
                .document(matchId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    
                    val currentCount = snapshot.getLong("currentSquadCount")?.toInt() ?: 0
                    val maxSquads = snapshot.getLong("maxSquadSize")?.toInt() ?: 0
                    
                    _uiState.update {
                        it.copy(
                            squadCountMap = it.squadCountMap + (matchId to currentCount),
                            maxSquadsMap = it.maxSquadsMap + (matchId to maxSquads)
                        )
                    }
                }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(successMessage = null, error = null) }
    }
}
