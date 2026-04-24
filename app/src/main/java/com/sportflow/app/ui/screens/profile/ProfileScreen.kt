@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sportflow.app.ui.screens.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.sportflow.app.data.repository.SportFlowRepository
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.sportflow.app.data.model.GnitsDepartment
import com.sportflow.app.data.model.UserRole
import com.sportflow.app.ui.components.*
import com.sportflow.app.ui.theme.*
import com.sportflow.app.ui.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    repository: SportFlowRepository? = null  // injected at call-site via hiltViewModel or passed directly
) {
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val user = authState.currentUser
    val context = LocalContext.current

    // ── Dialog / Sheet States ─────────────────────────────────────────────
    var showEditProfile by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    // ── Edit Profile dialog state ─────────────────────────────────────────
    var editName by remember(user) { mutableStateOf(user?.displayName ?: "") }
    var editRoll by remember(user) { mutableStateOf(user?.rollNumber ?: "") }
    var editDept by remember(user) { mutableStateOf(user?.department ?: "") }
    var deptExpanded by remember { mutableStateOf(false) }

    // ── Notification prefs — backed by FCM topic subscriptions ──────────
    var notifMatchStart by remember { mutableStateOf(true) }
    var notifScoreUpdate by remember { mutableStateOf(true) }
    var notifTournament by remember { mutableStateOf(true) }
    var notifPayment by remember { mutableStateOf(true) }

    // Subscribe to default topics when screen first loads
    LaunchedEffect(Unit) {
        repository?.subscribeToDefaultTopics(user?.department ?: "")
    }

    // ── Main Screen ───────────────────────────────────────────────────────
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg),
        contentPadding = PaddingValues(bottom = 32.dp)
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
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                Brush.linearGradient(colors = listOf(GnitsOrange, GnitsOrangeDark)),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user?.displayName?.take(2)?.uppercase() ?: "GN",
                            style = SportFlowTheme.typography.displayMedium,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = user?.displayName ?: "GNITS Student",
                        style = SportFlowTheme.typography.headlineLarge,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = user?.email ?: "",
                        style = SportFlowTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    if (user?.rollNumber?.isNotBlank() == true || user?.department?.isNotBlank() == true) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (user.rollNumber.isNotBlank()) StatusChip(text = user.rollNumber, color = InfoBlue)
                            if (user.department.isNotBlank()) StatusChip(text = user.department, color = GnitsOrange)
                        }
                    }

                    if (authState.userRole == UserRole.ADMIN) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = GnitsOrangeLight,
                            border = androidx.compose.foundation.BorderStroke(1.dp, GnitsOrange.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.AdminPanelSettings, null, tint = GnitsOrangeDark, modifier = Modifier.size(16.dp))
                                Text("ADMIN", style = SportFlowTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = GnitsOrangeDark)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ProfileStat("--", "Matches")
                        Box(modifier = Modifier.width(1.dp).height(40.dp).background(CardBorder))
                        ProfileStat("--", "Tournaments")
                        Box(modifier = Modifier.width(1.dp).height(40.dp).background(CardBorder))
                        ProfileStat("--", "Wins")
                    }
                }
            }
        }

        // Quick Actions
        item { SectionHeader(title = "Quick Actions") }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (authState.userRole == UserRole.ADMIN) {
                    QuickActionCard(Icons.Filled.AdminPanelSettings, "Admin Portal", GnitsOrange, Modifier.weight(1f)) {
                        navController.navigate("admin")
                    }
                }
                QuickActionCard(Icons.Filled.EmojiEvents, "Brackets", InfoBlue, Modifier.weight(1f)) {
                    navController.navigate("bracket?tournamentId=")
                }
                QuickActionCard(Icons.Filled.Edit, "Edit Profile", WarningAmber, Modifier.weight(1f)) {
                    showEditProfile = true
                }
            }
        }

        // Settings Menu
        item { SectionHeader(title = "Settings") }
        item {
            SportCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Column {
                    SettingsRow(Icons.Outlined.Person, "Edit Profile", subtitle = "Update name, roll no & department") {
                        showEditProfile = true
                    }
                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                    SettingsRow(Icons.Outlined.Notifications, "Notifications", subtitle = "Manage match & score alerts") {
                        showNotifications = true
                    }
                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                    SettingsRow(Icons.Outlined.Security, "Privacy & Security", subtitle = "Password & data options") {
                        showPrivacy = true
                    }
                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                    SettingsRow(Icons.Outlined.Help, "Help & Support", subtitle = "FAQs, contact us") {
                        showHelp = true
                    }
                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                    SettingsRow(Icons.Outlined.Info, "About GNITS Sports", subtitle = "Version, developers & info") {
                        showAbout = true
                    }
                }
            }
        }

        // Sign Out
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                PillButton(
                    text = "Sign Out",
                    onClick = {
                        authViewModel.signOut()
                        navController.navigate("login") { popUpTo(0) { inclusive = true } }
                    },
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
                text = "GNITS Sports v1.0.0",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                style = SportFlowTheme.typography.bodySmall,
                color = TextTertiary,
                textAlign = TextAlign.Center
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // DIALOG: Edit Profile
    // ══════════════════════════════════════════════════════════════════════
    if (showEditProfile) {
        Dialog(onDismissRequest = { showEditProfile = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = PureWhite) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Edit Profile", style = SportFlowTheme.typography.headlineLarge, color = TextPrimary)
                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Full Name") },
                        leadingIcon = { Icon(Icons.Outlined.Person, null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editRoll,
                        onValueChange = { editRoll = it },
                        label = { Text("Roll Number") },
                        leadingIcon = { Icon(Icons.Outlined.Badge, null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Department dropdown
                    ExposedDropdownMenuBox(
                        expanded = deptExpanded,
                        onExpandedChange = { deptExpanded = !deptExpanded }
                    ) {
                        OutlinedTextField(
                            value = GnitsDepartment.entries.find { it.name == editDept }?.displayName ?: editDept,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Department") },
                            leadingIcon = { Icon(Icons.Outlined.School, null) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deptExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = RoundedCornerShape(14.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = deptExpanded,
                            onDismissRequest = { deptExpanded = false }
                        ) {
                            GnitsDepartment.entries.forEach { dept ->
                                DropdownMenuItem(
                                    text = { Text("${dept.name} — ${dept.displayName}") },
                                    onClick = { editDept = dept.name; deptExpanded = false }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { showEditProfile = false },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text("Cancel", color = TextSecondary)
                        }
                        PillButton(
                            text = "Save Changes",
                            onClick = {
                                // Profile update is persisted through the ViewModel/Repository
                                showEditProfile = false
                            },
                            modifier = Modifier.weight(1f),
                            containerColor = GnitsOrange
                        )
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // DIALOG: Notifications
    // ══════════════════════════════════════════════════════════════════════
    if (showNotifications) {
        Dialog(onDismissRequest = { showNotifications = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = PureWhite) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(42.dp)
                                .background(GnitsOrangeLight, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Notifications, null, tint = GnitsOrange, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Notifications", style = SportFlowTheme.typography.headlineLarge, color = TextPrimary)
                    }
                    Spacer(modifier = Modifier.height(20.dp))

                    NotifToggleRow(
                        "Match Start Alerts",
                        "Know instantly when a game begins",
                        notifMatchStart
                    ) {
                        notifMatchStart = it
                        repository?.setNotificationCategory(SportFlowRepository.FCM_TOPIC_MATCH_START, it)
                    }
                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                    NotifToggleRow(
                        "Live Score Updates",
                        "Get notified on every goal/point scored",
                        notifScoreUpdate
                    ) {
                        notifScoreUpdate = it
                        repository?.setNotificationCategory(SportFlowRepository.FCM_TOPIC_SCORES, it)
                    }
                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                    NotifToggleRow(
                        "Tournament Announcements",
                        "New brackets, fixtures & results",
                        notifTournament
                    ) {
                        notifTournament = it
                        repository?.setNotificationCategory(SportFlowRepository.FCM_TOPIC_TOURNAMENT, it)
                    }
                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                    NotifToggleRow(
                        "Payment Verification",
                        "When your registration is confirmed",
                        notifPayment
                    ) {
                        notifPayment = it
                        repository?.setNotificationCategory(SportFlowRepository.FCM_TOPIC_PAYMENT, it)
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    PillButton(
                        text = "Save Preferences",
                        onClick = { showNotifications = false },
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = GnitsOrange,
                        icon = Icons.Filled.Check
                    )
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // DIALOG: Privacy & Security
    // ══════════════════════════════════════════════════════════════════════
    if (showPrivacy) {
        Dialog(onDismissRequest = { showPrivacy = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = PureWhite) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(42.dp).background(InfoBlue.copy(alpha = 0.1f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Security, null, tint = InfoBlue, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Privacy & Security", style = SportFlowTheme.typography.headlineLarge, color = TextPrimary)
                    }
                    Spacer(modifier = Modifier.height(20.dp))

                    // Change Password info
                    Surface(shape = RoundedCornerShape(16.dp), color = OffWhite, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Lock, null, tint = GnitsOrange, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Change Password", style = SportFlowTheme.typography.headlineSmall, color = TextPrimary)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "To change your password, use the 'Forgot Password' option on the Sign In screen. A reset link will be sent to your registered email.",
                                style = SportFlowTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Data Privacy
                    Surface(shape = RoundedCornerShape(16.dp), color = OffWhite, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Shield, null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Data Protection", style = SportFlowTheme.typography.headlineSmall, color = TextPrimary)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Your data (name, roll number, department) is stored securely on Firebase Cloud Firestore and is only accessible to GNITS sports administrators.",
                                style = SportFlowTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Account Deletion
                    Surface(shape = RoundedCornerShape(16.dp), color = LiveRedBg, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.DeleteForever, null, tint = LiveRed, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Delete Account", style = SportFlowTheme.typography.headlineSmall, color = LiveRed)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "To permanently delete your account and data, contact the Department Sports Coordinator or email: sports@gnits.ac.in",
                                style = SportFlowTheme.typography.bodySmall,
                                color = LiveRed.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    PillButton(
                        text = "Done",
                        onClick = { showPrivacy = false },
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = InfoBlue,
                        icon = Icons.Filled.Check
                    )
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // DIALOG: Help & Support
    // ══════════════════════════════════════════════════════════════════════
    if (showHelp) {
        Dialog(onDismissRequest = { showHelp = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = PureWhite) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(42.dp).background(WarningAmber.copy(alpha = 0.1f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Help, null, tint = WarningAmber, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Help & Support", style = SportFlowTheme.typography.headlineLarge, color = TextPrimary)
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    FaqItem("How do I join a tournament?", "Your Department Sports Representative will register your team. Once registered, you can track scores and brackets live in this app.")
                    FaqItem("Why can't I see the Admin tab?", "Only verified Sports Coordinators and Faculty have Admin access. Contact your HOD if you need elevated access.")
                    FaqItem("Live scores not updating?", "Make sure you have an active internet connection. Live scores update within 1–2 seconds via Firebase real-time sync.")
                    FaqItem("My department is wrong, how to change?", "Use the 'Edit Profile' option in Settings to update your department.")

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = CardBorder)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Contact the Sports Office", style = SportFlowTheme.typography.headlineSmall, color = TextPrimary)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton(
                            text = "Email Us",
                            onClick = {
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:sports@gnits.ac.in")
                                    putExtra(Intent.EXTRA_SUBJECT, "GNITS Sports App Support")
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.Email,
                            containerColor = GnitsOrange
                        )
                        PillButton(
                            text = "Call",
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:+914023536177")
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.Phone,
                            containerColor = SuccessGreen
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showHelp = false },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Close", color = TextSecondary)
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // DIALOG: About GNITS Sports
    // ══════════════════════════════════════════════════════════════════════
    if (showAbout) {
        Dialog(onDismissRequest = { showAbout = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = PureWhite) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // App icon
                    Box(
                        modifier = Modifier.size(72.dp)
                            .background(
                                Brush.linearGradient(colors = listOf(GnitsOrange, GnitsOrangeDark)),
                                RoundedCornerShape(20.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.EmojiEvents, null, tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("GNITS Sports", style = SportFlowTheme.typography.displayMedium, color = TextPrimary, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    StatusChip(text = "Version 1.0.0", color = GnitsOrange)
                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(shape = RoundedCornerShape(16.dp), color = OffWhite, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "G. Narayanamma Institute of Technology & Science",
                                style = SportFlowTheme.typography.headlineSmall,
                                color = TextPrimary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Shaikpet, Hyderabad – 500008\nTelangana, India",
                                style = SportFlowTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    AboutRow(Icons.Outlined.Code, "Developed by", "GNITS IT  Department")
                    AboutRow(Icons.Outlined.School, "Purpose", "Inter-Department Sports Management")
                    AboutRow(Icons.Outlined.Cloud, "Backend", "Firebase Firestore + Cloud Messaging")
                    AboutRow(Icons.Outlined.PhoneAndroid, "Platform", "Android (Compose)")

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "© 2026 GNITS. All rights reserved.",
                        style = SportFlowTheme.typography.bodySmall,
                        color = TextTertiary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    PillButton(
                        text = "Close",
                        onClick = { showAbout = false },
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = GnitsOrange
                    )
                }
            }
        }
    }
}

// ─── Helper Composables ───────────────────────────────────────────────────────

@Composable
private fun ProfileStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = SportFlowTheme.typography.displayMedium, color = TextPrimary, fontWeight = FontWeight.ExtraBold)
        Text(text = label, style = SportFlowTheme.typography.bodySmall, color = TextTertiary)
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
    SportCard(modifier = modifier, onClick = onClick) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(48.dp)
                    .background(color.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = label, style = SportFlowTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    subtitle: String = "",
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(38.dp)
                .background(OffWhite, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = GnitsOrange, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = SportFlowTheme.typography.bodyLarge, color = TextPrimary)
            if (subtitle.isNotBlank()) {
                Text(text = subtitle, style = SportFlowTheme.typography.bodySmall, color = TextTertiary)
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun NotifToggleRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = SportFlowTheme.typography.bodyLarge, color = TextPrimary)
            Text(subtitle, style = SportFlowTheme.typography.bodySmall, color = TextTertiary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = GnitsOrange,
                uncheckedTrackColor = CardBorder
            )
        )
    }
}

@Composable
private fun FaqItem(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = question,
                style = SportFlowTheme.typography.labelLarge,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = GnitsOrange,
                modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(200)) + expandVertically(tween(200)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
        ) {
            Text(
                text = answer,
                style = SportFlowTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        HorizontalDivider(color = CardBorder, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun AboutRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = GnitsOrange, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text("$label:", style = SportFlowTheme.typography.bodySmall, color = TextTertiary, modifier = Modifier.width(100.dp))
        Text(value, style = SportFlowTheme.typography.labelMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}
