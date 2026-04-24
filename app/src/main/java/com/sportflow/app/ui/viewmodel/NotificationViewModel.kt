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
                val filtered = notifications.filter { notification ->
                    when (role) {
                        UserRole.ADMIN -> notification.type != "registration_success"
                        else -> notification.type != "admin_registration_pending"
                    }
                }
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

    fun openNotificationCenter() {
        _uiState.update { it.copy(showDialog = true) }
    }

    fun closeNotificationCenter() {
        _uiState.update { it.copy(showDialog = false) }
    }

    fun markSeen(notificationId: String) {
        val uid = repository.getCurrentUser()?.uid ?: return
        viewModelScope.launch {
            try {
                repository.markNotificationAsSeen(uid, notificationId)
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
            } catch (_: Exception) {
                // Non-critical
            }
        }
    }
}
