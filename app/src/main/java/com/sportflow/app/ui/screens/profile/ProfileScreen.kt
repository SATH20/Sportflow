package com.sportflow.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.sportflow.app.ui.components.*
import com.sportflow.app.ui.theme.*

@Composable
fun ProfileScreen(
    navController: NavHostController
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Profile Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PureWhite)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(PlayoGreen, PlayoGreenDark)
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "SF",
                            style = SportFlowTheme.typography.displayMedium,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "SportFlow Player",
                        style = SportFlowTheme.typography.headlineLarge,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "player@sportflow.com",
                        style = SportFlowTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Stats Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ProfileStat(value = "12", label = "Matches")
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(40.dp)
                                .background(CardBorder)
                        )
                        ProfileStat(value = "3", label = "Tournaments")
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(40.dp)
                                .background(CardBorder)
                        )
                        ProfileStat(value = "8", label = "Wins")
                    }
                }
            }
        }

        // Quick Actions
        item {
            SectionHeader(title = "Quick Actions")
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    icon = Icons.Filled.AdminPanelSettings,
                    label = "Admin Portal",
                    color = InfoBlue,
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate("admin") }
                )
                QuickActionCard(
                    icon = Icons.Filled.EmojiEvents,
                    label = "Brackets",
                    color = PlayoGreen,
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate("bracket?tournamentId=") }
                )
                QuickActionCard(
                    icon = Icons.Filled.History,
                    label = "History",
                    color = WarningAmber,
                    modifier = Modifier.weight(1f),
                    onClick = { }
                )
            }
        }

        // Settings Menu
        item {
            SectionHeader(title = "Settings")
        }

        item {
            SportCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Column {
                    SettingsRow(icon = Icons.Outlined.Person, label = "Edit Profile")
                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                    SettingsRow(icon = Icons.Outlined.Notifications, label = "Notifications")
                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                    SettingsRow(icon = Icons.Outlined.Security, label = "Privacy & Security")
                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                    SettingsRow(icon = Icons.Outlined.Help, label = "Help & Support")
                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                    SettingsRow(icon = Icons.Outlined.Info, label = "About SportFlow")
                }
            }
        }

        // Sign Out
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                PillButton(
                    text = "Sign Out",
                    onClick = { },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Logout,
                    containerColor = LiveRedBg,
                    contentColor = LiveRed
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "SportFlow v1.0.0",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                style = SportFlowTheme.typography.bodySmall,
                color = TextTertiary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun ProfileStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = SportFlowTheme.typography.displayMedium,
            color = TextPrimary,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = label,
            style = SportFlowTheme.typography.bodySmall,
            color = TextTertiary
        )
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    SportCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = label,
                style = SportFlowTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = SportFlowTheme.typography.bodyLarge,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(20.dp)
        )
    }
}
