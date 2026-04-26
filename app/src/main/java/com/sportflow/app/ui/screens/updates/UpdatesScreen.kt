package com.sportflow.app.ui.screens.updates

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material.icons.outlined.Feed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.google.firebase.Timestamp
import com.sportflow.app.data.model.Announcement
import com.sportflow.app.data.model.AnnouncementCategory
import com.sportflow.app.ui.components.SportCard
import com.sportflow.app.ui.theme.*
import com.sportflow.app.ui.viewmodel.UpdatesViewModel

@Composable
fun UpdatesScreen(
    navController: NavHostController,
    viewModel: UpdatesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var sessionLastOpenedAt by remember { mutableStateOf<Timestamp?>(null) }

    LaunchedEffect(uiState.lastOpenedAt) {
        if (sessionLastOpenedAt == null) {
            sessionLastOpenedAt = uiState.lastOpenedAt
            viewModel.markOpened()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PureWhite)
                    .padding(20.dp)
            ) {
                Text(
                    text = "Updates",
                    style = SportFlowTheme.typography.displayLarge,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tournament announcements and fixture changes",
                    style = SportFlowTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        if (uiState.isLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = GnitsOrange)
                }
            }
        } else if (uiState.announcements.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Outlined.Feed, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No updates yet", style = SportFlowTheme.typography.headlineMedium, color = TextSecondary)
                }
            }
        } else {
            items(uiState.announcements, key = { it.id }) { announcement ->
                TimelineAnnouncementCard(
                    announcement = announcement,
                    isNew = announcement.createdAt.isAfter(sessionLastOpenedAt),
                    onClick = {
                        when {
                            announcement.matchId.isNotBlank() -> navController.navigate("live")
                            announcement.tournamentId.isNotBlank() -> navController.navigate("tournament?tournamentId=${announcement.tournamentId}")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun TimelineAnnouncementCard(
    announcement: Announcement,
    isNew: Boolean,
    onClick: () -> Unit
) {
    val icon = announcement.icon()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(announcement.tint()),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
            }
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(72.dp)
                    .background(GnitsOrange.copy(alpha = 0.32f))
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        SportCard(
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = announcement.matchId.isNotBlank() || announcement.tournamentId.isNotBlank(), onClick = onClick)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = announcement.title,
                        style = SportFlowTheme.typography.headlineSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isNew) {
                        Surface(shape = RoundedCornerShape(12.dp), color = LiveRed) {
                            Text(
                                text = "New",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = SportFlowTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = announcement.message,
                    style = SportFlowTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = announcement.category.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
                    style = SportFlowTheme.typography.labelSmall,
                    color = GnitsOrangeDark,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun Timestamp?.isAfter(other: Timestamp?): Boolean {
    if (this == null) return false
    if (other == null) return true
    return this.seconds > other.seconds || (this.seconds == other.seconds && this.nanoseconds > other.nanoseconds)
}

private fun Announcement.icon(): ImageVector = when (category) {
    AnnouncementCategory.URGENT -> Icons.Filled.ErrorOutline
    AnnouncementCategory.VENUE_SHIFT -> Icons.Filled.ErrorOutline
    AnnouncementCategory.SCHEDULE_CHANGE -> Icons.Filled.Schedule
    AnnouncementCategory.RESULT -> Icons.Filled.EmojiEvents
    AnnouncementCategory.FIXTURE_UPDATE -> Icons.Filled.SportsScore
    AnnouncementCategory.GENERAL -> Icons.Filled.Campaign
}

private fun Announcement.tint(): Color = when (category) {
    AnnouncementCategory.URGENT, AnnouncementCategory.VENUE_SHIFT -> LiveRed
    AnnouncementCategory.SCHEDULE_CHANGE, AnnouncementCategory.FIXTURE_UPDATE -> GnitsOrange
    AnnouncementCategory.RESULT -> SuccessGreen
    AnnouncementCategory.GENERAL -> InfoBlue
}
