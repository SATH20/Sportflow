package com.sportflow.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.sportflow.app.data.model.Match
import com.sportflow.app.data.model.MatchStatus
import com.sportflow.app.data.model.NotificationItem
import com.sportflow.app.data.model.Registration
import com.sportflow.app.data.model.RegistrationStatus
import com.sportflow.app.data.model.SportUser
import com.sportflow.app.ui.components.PillButton
import com.sportflow.app.ui.components.SectionHeader
import com.sportflow.app.ui.components.SportCard
import com.sportflow.app.ui.theme.GnitsOrange
import com.sportflow.app.ui.theme.GnitsOrangeDark
import com.sportflow.app.ui.theme.GnitsOrangeLight
import com.sportflow.app.ui.theme.InfoBlue
import com.sportflow.app.ui.theme.LiveRed
import com.sportflow.app.ui.theme.OffWhite
import com.sportflow.app.ui.theme.PureWhite
import com.sportflow.app.ui.theme.ScreenBg
import com.sportflow.app.ui.theme.SuccessGreen
import com.sportflow.app.ui.theme.SportFlowTheme
import com.sportflow.app.ui.theme.TextPrimary
import com.sportflow.app.ui.theme.TextSecondary
import com.sportflow.app.ui.theme.TextTertiary
import com.sportflow.app.ui.theme.WarningAmber
import com.sportflow.app.ui.viewmodel.AdminViewModel
import com.sportflow.app.ui.viewmodel.HomeViewModel
import com.sportflow.app.ui.viewmodel.NotificationViewModel
import com.sportflow.app.ui.viewmodel.RoleHomeViewModel

@Composable
fun HomeFeedScreenComplete(
    navController: NavHostController,
    currentUser: SportUser?,
    isAdmin: Boolean,
    homeViewModel: HomeViewModel = hiltViewModel(),
    roleHomeViewModel: RoleHomeViewModel = hiltViewModel(),
    notificationViewModel: NotificationViewModel = hiltViewModel(),
    adminViewModel: AdminViewModel = hiltViewModel()
) {
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val roleState by roleHomeViewModel.uiState.collectAsStateWithLifecycle()
    val notificationState by notificationViewModel.uiState.collectAsStateWithLifecycle()
    val adminState by adminViewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showBroadcastDialog by rememberSaveable { mutableStateOf(false) }
    var announcementTitle by rememberSaveable { mutableStateOf("") }
    var announcementMessage by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(adminState.successMessage, adminState.error) {
        val message = adminState.successMessage ?: adminState.error
        if (message != null) {
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
            adminViewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = ScreenBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isAdmin) "Command Center" else "My Journey",
                            style = SportFlowTheme.typography.headlineLarge,
                            color = Color.White
                        )
                        Text(
                            text = currentUser?.displayName ?: "GNITS Sports",
                            style = SportFlowTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                    }
                },
                actions = {
                    if (isAdmin) {
                        IconButton(onClick = { navController.navigate("admin") }) {
                            Icon(Icons.Filled.AdminPanelSettings, contentDescription = "Admin", tint = Color.White)
                        }
                    }
                    IconButton(onClick = { notificationViewModel.openNotificationCenter() }) {
                        BadgedBox(
                            badge = {
                                if (notificationState.unseenCount > 0) {
                                    Badge(containerColor = LiveRed, contentColor = Color.White) {
                                        Text("${minOf(notificationState.unseenCount, 9)}${if (notificationState.unseenCount > 9) "+" else ""}")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (notificationState.unseenCount > 0) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                                contentDescription = "Notifications",
                                tint = Color.White
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = GnitsOrange,
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(
                    onClick = { showBroadcastDialog = true },
                    containerColor = GnitsOrange,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Filled.Campaign, contentDescription = "Broadcast")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                DashboardHeaderCard(
                    isAdmin = isAdmin,
                    currentUser = currentUser,
                    pendingCount = roleState.pendingRegistrations.size,
                    unseenNotificationCount = notificationState.unseenCount
                )
            }

            if (isAdmin) {
                val todayMatches = homeState.liveMatches + homeState.upcomingMatches
                if (roleState.pendingRegistrations.isNotEmpty()) {
                    item { SectionHeader(title = "Review Required") }
                    item {
                        ReviewRequiredCard(
                            pendingCount = roleState.pendingRegistrations.size,
                            latest = roleState.pendingRegistrations.first(),
                            onClick = { navController.navigate("admin") }
                        )
                    }
                }
                item { SectionHeader(title = "Today's Schedule") }
                if (todayMatches.isEmpty()) {
                    item { EmptyHomeCard("No matches scheduled today") }
                } else {
                    items(todayMatches.sortedBy { it.scheduledTime }) { match ->
                        TimelineMatchCard(match = match)
                    }
                }
            } else {
                item { SectionHeader(title = "Registered Matches") }
                if (roleState.myRegistrations.isEmpty()) {
                    item { EmptyHomeCard("No registrations yet. Join a tournament to start your journey.") }
                } else {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(roleState.myRegistrations, key = { it.id }) { registration ->
                                JourneyRegistrationCard(
                                    registration = registration,
                                    relatedMatch = findRelatedMatch(registration, roleState.allMatches)
                                )
                            }
                        }
                    }
                }
            }

            item { SectionHeader(title = "Recent Notifications") }
            if (notificationState.notifications.isEmpty()) {
                item { EmptyHomeCard("No recent notifications") }
            } else {
                items(notificationState.notifications.take(6), key = { it.id }) { notification ->
                    RecentNotificationRow(
                        notification = notification,
                        onClick = { notificationViewModel.markSeen(notification.id) }
                    )
                }
            }
        }
    }

    if (notificationState.showDialog) {
        NotificationCenterDialog(
            notifications = notificationState.notifications,
            onDismiss = { notificationViewModel.closeNotificationCenter() },
            onMarkAllSeen = { notificationViewModel.markAllSeen() },
            onNotificationClick = { notificationViewModel.markSeen(it.id) }
        )
    }

    if (showBroadcastDialog) {
        AlertDialog(
            onDismissRequest = { showBroadcastDialog = false },
            title = { Text("Broadcast Update") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = announcementTitle,
                        onValueChange = { announcementTitle = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = announcementMessage,
                        onValueChange = { announcementMessage = it },
                        label = { Text("Message") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.postAnnouncement(
                            announcementTitle,
                            announcementMessage,
                            com.sportflow.app.data.model.AnnouncementCategory.GENERAL
                        )
                        announcementTitle = ""
                        announcementMessage = ""
                        showBroadcastDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GnitsOrange)
                ) {
                    Text("Post")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBroadcastDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DashboardHeaderCard(
    isAdmin: Boolean,
    currentUser: SportUser?,
    pendingCount: Int,
    unseenNotificationCount: Int
) {
    SportCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = if (isAdmin) "Physical Directress Dashboard" else "Track approvals, fixtures, and updates in one place",
                style = SportFlowTheme.typography.headlineSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isAdmin) {
                    "$pendingCount registrations need review right now."
                } else {
                    "${currentUser?.department?.ifBlank { "GNITS" } ?: "GNITS"} notifications: $unseenNotificationCount new."
                },
                style = SportFlowTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun JourneyRegistrationCard(
    registration: Registration,
    relatedMatch: Match?
) {
    val completed = relatedMatch?.status == MatchStatus.COMPLETED
    val badgeText = when {
        completed -> "Match Completed"
        registration.status == RegistrationStatus.CONFIRMED -> "Confirmed"
        else -> "Pending Approval"
    }
    val badgeColor = when {
        completed -> InfoBlue
        registration.status == RegistrationStatus.CONFIRMED -> SuccessGreen
        else -> WarningAmber
    }

    SportCard(
        modifier = Modifier.width(280.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = registration.matchName.ifBlank { registration.fixtureUnitName.ifBlank { "Tournament Entry" } },
                    style = SportFlowTheme.typography.headlineSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                StatusPill(text = badgeText, color = badgeColor)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = registration.fixtureUnitName.ifBlank { registration.teamName.ifBlank { registration.userName } },
                style = SportFlowTheme.typography.bodyMedium,
                color = TextSecondary
            )
            if (registration.tournamentId.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tournament registration",
                    style = SportFlowTheme.typography.labelMedium,
                    color = GnitsOrangeDark
                )
            }
        }
    }
}

@Composable
private fun ReviewRequiredCard(
    pendingCount: Int,
    latest: Registration,
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
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$pendingCount registration${if (pendingCount == 1) "" else "s"} waiting",
                    style = SportFlowTheme.typography.headlineSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = latest.fixtureUnitName.ifBlank { latest.teamName.ifBlank { latest.userName } },
                    style = SportFlowTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = GnitsOrange)
        }
    }
}

@Composable
private fun TimelineMatchCard(match: Match) {
    SportCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(GnitsOrangeLight, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = GnitsOrange)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${match.teamA} vs ${match.teamB}",
                    style = SportFlowTheme.typography.headlineSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${match.venue} • ${match.scheduledTime?.toDate()?.toString() ?: "TBD"}",
                    style = SportFlowTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            StatusPill(
                text = match.status.name.replace("_", " "),
                color = when (match.status) {
                    MatchStatus.COMPLETED -> InfoBlue
                    MatchStatus.LIVE -> LiveRed
                    else -> GnitsOrange
                }
            )
        }
    }
}

@Composable
private fun RecentNotificationRow(
    notification: NotificationItem,
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
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(if (notification.seen) OffWhite else GnitsOrangeLight, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (notification.seen) Icons.Outlined.Notifications else Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = if (notification.seen) TextTertiary else GnitsOrange
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.title,
                    style = SportFlowTheme.typography.labelLarge,
                    color = TextPrimary,
                    fontWeight = if (notification.seen) FontWeight.Medium else FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = notification.body,
                    style = SportFlowTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!notification.seen) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(GnitsOrange, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun EmptyHomeCard(message: String) {
    SportCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                style = SportFlowTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.14f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = SportFlowTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun findRelatedMatch(registration: Registration, matches: List<Match>): Match? {
    return matches.firstOrNull { it.id == registration.matchId }
}

@Composable
private fun NotificationCenterDialog(
    notifications: List<NotificationItem>,
    onDismiss: () -> Unit,
    onMarkAllSeen: () -> Unit,
    onNotificationClick: (NotificationItem) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Notifications",
                    style = SportFlowTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                TextButton(onClick = onMarkAllSeen) {
                    Text("Mark all read", color = GnitsOrange)
                }
            }
        },
        text = {
            if (notifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.NotificationsOff, contentDescription = null, tint = TextTertiary)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No notifications yet", color = TextSecondary)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notifications, key = { it.id }) { notification ->
                        RecentNotificationRow(notification = notification) {
                            onNotificationClick(notification)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
