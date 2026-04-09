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
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.sportflow.app.ui.screens.admin.AdminDashboardScreen
import com.sportflow.app.ui.screens.bracket.BracketViewScreen
import com.sportflow.app.ui.screens.events.EventsScreen
import com.sportflow.app.ui.screens.home.HomeFeedScreen
import com.sportflow.app.ui.screens.live.LiveMatchCenterScreen
import com.sportflow.app.ui.screens.profile.ProfileScreen
import com.sportflow.app.ui.theme.*

sealed class Screen(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : Screen("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    data object Events : Screen("events", "Events", Icons.Filled.Event, Icons.Outlined.Event)
    data object MyMatches : Screen("my_matches", "My Matches", Icons.Filled.SportsSoccer, Icons.Outlined.SportsSoccer)
    data object Profile : Screen("profile", "Profile", Icons.Filled.Person, Icons.Outlined.Person)

    // Detail screens (no bottom nav)
    data object LiveMatch : Screen("live", "Live", Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle)
    data object Bracket : Screen("bracket?tournamentId={tournamentId}", "Bracket", Icons.Filled.AccountTree, Icons.Outlined.AccountTree)
    data object Admin : Screen("admin", "Admin", Icons.Filled.AdminPanelSettings, Icons.Outlined.AdminPanelSettings)
}

@Composable
fun SportFlowNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val bottomNavItems = listOf(
        Screen.Home,
        Screen.Events,
        Screen.MyMatches,
        Screen.Profile
    )

    val showBottomBar = bottomNavItems.any { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    }

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
                                selectedIconColor = PlayoGreen,
                                selectedTextColor = PlayoGreen,
                                unselectedIconColor = NavInactive,
                                unselectedTextColor = NavInactive,
                                indicatorColor = PlayoGreenLight
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
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(200)) }
        ) {
            composable(Screen.Home.route) {
                HomeFeedScreen(navController = navController)
            }
            composable(Screen.Events.route) {
                EventsScreen(navController = navController)
            }
            composable(Screen.MyMatches.route) {
                LiveMatchCenterScreen(navController = navController)
            }
            composable(Screen.Profile.route) {
                ProfileScreen(navController = navController)
            }
            composable(Screen.LiveMatch.route) {
                LiveMatchCenterScreen(navController = navController)
            }
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
            composable(Screen.Admin.route) {
                AdminDashboardScreen(navController = navController)
            }
        }
    }
}
