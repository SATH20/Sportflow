@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sportflow.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.sportflow.app.data.model.UserRole
import com.sportflow.app.ui.screens.admin.AdminDashboardScreen
import com.sportflow.app.ui.screens.auth.LoginScreen
import com.sportflow.app.ui.screens.bracket.BracketViewScreen
import com.sportflow.app.ui.screens.events.EventsScreen
import com.sportflow.app.ui.screens.live.LiveMatchCenterScreen
import com.sportflow.app.ui.screens.profile.ProfileScreen
import com.sportflow.app.ui.screens.updates.UpdatesScreen
import com.sportflow.app.ui.theme.*
import com.sportflow.app.ui.viewmodel.AuthViewModel

sealed class Screen(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Login : Screen("login", "Login", Icons.Filled.Login, Icons.Outlined.Login)
    data object Home : Screen("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    data object Events : Screen("events", "Events", Icons.Filled.Event, Icons.Outlined.Event)
    data object Updates : Screen("updates", "Updates", Icons.Filled.Campaign, Icons.Outlined.Campaign)
    data object MyMatches : Screen("my_matches", "My Matches", Icons.Filled.SportsSoccer, Icons.Outlined.SportsSoccer)
    data object Profile : Screen("profile", "Profile", Icons.Filled.Person, Icons.Outlined.Person)

    // Detail screens (no bottom nav)
    data object LiveMatch : Screen("live", "Live", Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle)
    data object Bracket : Screen("bracket?tournamentId={tournamentId}", "Bracket", Icons.Filled.AccountTree, Icons.Outlined.AccountTree)
    data object Admin : Screen("admin", "Admin", Icons.Filled.AdminPanelSettings, Icons.Outlined.AdminPanelSettings)
}

// ─────────────────────────────────────────────────────────────────────────────
// Per-role bottom nav definitions
//   PLAYER : Home | Events | Updates | My Matches | Profile
//   ADMIN  : Home | Events | Admin              | Profile
// ─────────────────────────────────────────────────────────────────────────────
private val playerNavItems = listOf(
    Screen.Home,
    Screen.Events,
    Screen.Updates,
    Screen.MyMatches,
    Screen.Profile
)

private val adminNavItems = listOf(
    Screen.Home,
    Screen.Events,
    Screen.Admin,
    Screen.Profile
)

@Composable
fun SportFlowNavHost() {
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isAdmin = authState.userRole == UserRole.ADMIN

    // Role-aware bottom nav items — completely different per role
    val bottomNavItems = if (isAdmin) adminNavItems else playerNavItems

    val showBottomBar = bottomNavItems.any { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    }

    // Determine start destination based on auth state
    val startDestination = if (authState.isLoggedIn) Screen.Home.route else Screen.Login.route

    Scaffold(
        containerColor = PureWhite,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = PureWhite,
                    contentColor = TextPrimary,
                    tonalElevation = 0.dp,
                    modifier = Modifier
                ) {
                    bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.label,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = screen.label,
                                    style = SportFlowTypography().labelSmall,
                                    maxLines = 1
                                )
                            },
                            selected = selected,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = GnitsOrange,
                                selectedTextColor = GnitsOrange,
                                unselectedIconColor = NavInactive,
                                unselectedTextColor = NavInactive,
                                indicatorColor = GnitsOrangeLight
                            ),
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
                HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(200)) }
        ) {
            // ── Auth ─────────────────────────────────────────────────────
            composable(Screen.Login.route) {
                LoginScreen(
                    navController = navController,
                    viewModel = authViewModel
                )
            }

            // ── Home (both roles) ─────────────────────────────────────────
            composable(Screen.Home.route) {
                com.sportflow.app.ui.screens.home.HomeFeedScreenComplete(
                    navController = navController,
                    currentUser   = authState.currentUser,
                    isAdmin       = isAdmin
                )
            }

            // ── Events (both roles) ───────────────────────────────────────
            composable(Screen.Events.route) {
                EventsScreen(
                    navController = navController,
                    isAdmin = isAdmin
                )
            }

            // ── Updates — PLAYER ONLY ─────────────────────────────────────
            // Always registered to prevent navigation crashes.
            // Access gating is handled by only showing the nav item for players.
            composable(Screen.Updates.route) {
                UpdatesScreen(navController = navController)
            }

            // ── My Matches — PLAYER ONLY ──────────────────────────────────
            // Always registered to prevent navigation crashes.
            // Access gating is handled by only showing the nav item for players.
            composable(Screen.MyMatches.route) {
                com.sportflow.app.ui.screens.home.MyMatchesScreenComplete(
                    navController   = navController,
                    currentUserRole = authState.userRole ?: UserRole.PLAYER
                )
            }

            // ── Profile (both roles) ──────────────────────────────────────
            composable(Screen.Profile.route) {
                ProfileScreen(
                    navController = navController,
                    authViewModel = authViewModel
                )
            }

            // ── Live Match (both roles — view only) ───────────────────────
            composable(Screen.LiveMatch.route) {
                LiveMatchCenterScreen(navController = navController)
            }

            // ── Bracket (both roles) ──────────────────────────────────────
            composable(
                route = Screen.Bracket.route,
                arguments = listOf(
                    androidx.navigation.navArgument("tournamentId") {
                        defaultValue = ""
                        type = androidx.navigation.NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val tournamentId = backStackEntry.arguments?.getString("tournamentId") ?: ""
                BracketViewScreen(
                    tournamentId = tournamentId,
                    navController = navController
                )
            }

            // ── Admin Dashboard — ADMIN ONLY ──────────────────────────────
            // Always registered to prevent navigation crashes.
            // Access gating is handled by only showing the nav item for admins.
            composable(Screen.Admin.route) {
                AdminDashboardScreen(navController = navController)
            }

            // Admin Approval Screen
            composable("admin/approvals") {
                com.sportflow.app.ui.screens.admin.AdminApprovalScreen()
            }
        }
    }
}
