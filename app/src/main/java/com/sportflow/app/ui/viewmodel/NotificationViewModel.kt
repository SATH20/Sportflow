package com.sportflow.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportflow.app.data.model.NotificationItem
import com.sportflow.app.data.repository.SportFlowRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationUiState(
    val notifications: List<NotificationItem> = emptyList(),
    val unseenCount: Int = 0,
    val showDialog: Boolean = false,
    val isLoading: Boolean = false
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
            repository.getNotifications(uid).collect { notifications ->
                val unseenCount = notifications.count { !it.seen }
                _uiState.update {
                    it.copy(
                        notifications = notifications,
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
