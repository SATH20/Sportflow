package com.sportflow.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportflow.app.data.model.NotificationItem
import com.sportflow.app.data.model.UserRole
import com.sportflow.app.data.repository.SportFlowRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationUiState(
    val notifications: List<NotificationItem> = emptyList(),
    val unseenCount: Int = 0,
    val showDialog: Boolean = false,
    val isLoading: Boolean = false,
    val userRole: UserRole = UserRole.PLAYER
)

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val repository: SportFlowRepository
) : ViewModel() {

    private val adminOnlyTypes = setOf("admin_registration_pending", "tournament_full")
    private val playerOnlyTypes = setOf(
        "registration_success",
        "registration_accepted",
        "registration_denied",
        "match_scheduled",
        "match_reminder",
        "match_start",
        "match_end",
        "tournament_created",
        "fixture_change",
        "announcement",
        "spot_opened",
        "squad_closed"
    )

    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    init {
        loadNotifications()
    }

    private fun loadNotifications() {
        val uid = repository.getCurrentUser()?.uid ?: return

        viewModelScope.launch {
            val role = repository.getCurrentUserProfile()?.role ?: UserRole.PLAYER
            _uiState.update { it.copy(userRole = role) }
            repository.getNotifications(uid).collect { notifications ->
                val filtered = notifications.filter { notification -> isAllowedForRole(notification.type, role) }
                val unseenCount = filtered.count { !it.seen }
                _uiState.update {
                    it.copy(
                        notifications = filtered,
                        unseenCount = unseenCount
                    )
                }
            }
        }
    }

    private fun isAllowedForRole(type: String, role: UserRole): Boolean {
        return when (role) {
            UserRole.ADMIN -> type in adminOnlyTypes
            else -> type in playerOnlyTypes
        }
    }

    fun openNotificationCenter() {
        _uiState.update { it.copy(showDialog = true) }
        viewModelScope.launch {
            repository.updateLastCheckedTimestamp()
        }
    }

    fun closeNotificationCenter() {
        _uiState.update { it.copy(showDialog = false) }
    }

    fun markSeen(notificationId: String) {
        val uid = repository.getCurrentUser()?.uid ?: return
        viewModelScope.launch {
            try {
                repository.markNotificationAsSeen(uid, notificationId)
                repository.updateLastCheckedTimestamp()
            } catch (_: Exception) {
                // Non-critical
            }
        }
    }

    fun markAllSeen() {
        val uid = repository.getCurrentUser()?.uid ?: return
        viewModelScope.launch {
            try {
                repository.markAllNotificationsAsSeen(uid)
                repository.updateLastCheckedTimestamp()
            } catch (_: Exception) {
                // Non-critical
            }
        }
    }
}
