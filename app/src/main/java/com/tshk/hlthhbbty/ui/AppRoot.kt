package com.tshk.hlthhbbty.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tshk.hlthhbbty.ui.auth.AuthScreen
import com.tshk.hlthhbbty.ui.glass.GlassBottomBar
import com.tshk.hlthhbbty.ui.glass.LiquidBackground
import com.tshk.hlthhbbty.ui.glass.NavItem
import com.tshk.hlthhbbty.ui.habits.HabitsScreen
import com.tshk.hlthhbbty.ui.home.HomeScreen
import com.tshk.hlthhbbty.ui.integrations.IntegrationsScreen
import com.tshk.hlthhbbty.ui.profile.ChangePasswordScreen
import com.tshk.hlthhbbty.ui.profile.EditProfileScreen
import com.tshk.hlthhbbty.ui.profile.ProfileScreen
import com.tshk.hlthhbbty.ui.stats.StatsScreen
import com.tshk.hlthhbbty.ui.theme.Liquid

@Composable
fun AppRoot(vm: RootViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    LiquidBackground {
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = { (fadeIn(tween(400)) + scaleIn(initialScale = 0.96f)) togetherWith fadeOut(tween(250)) },
            label = "root",
        ) { s ->
            when (s) {
                RootState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Liquid.palette.accent)
                }
                RootState.LoggedOut -> AuthScreen()
                is RootState.NeedsProfile -> EditProfileScreen(onboarding = true, onDone = {})
                is RootState.Ready -> MainScaffold()
            }
        }
    }
}

private object Routes {
    const val HOME = "home"
    const val HABITS = "habits"
    const val STATS = "stats"
    const val PROFILE = "profile"
    const val EDIT_PROFILE = "profile/edit"
    const val CHANGE_PASSWORD = "profile/password"
    const val INTEGRATIONS = "integrations"
}

private val tabs = listOf(Routes.HOME, Routes.HABITS, Routes.STATS, Routes.PROFILE)

private val navItems = listOf(
    NavItem("Главная", Icons.Outlined.Home, Icons.Rounded.Home),
    NavItem("Привычки", Icons.Outlined.TaskAlt, Icons.Rounded.TaskAlt),
    NavItem("Статистика", Icons.Outlined.BarChart, Icons.Rounded.BarChart),
    NavItem("Профиль", Icons.Outlined.Person, Icons.Rounded.Person),
)

@Composable
private fun MainScaffold() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val tabIndex = tabs.indexOf(route)

    val status = WindowInsets.statusBars.asPaddingValues()
    val navBars = WindowInsets.navigationBars.asPaddingValues()
    val tabPadding = PaddingValues(
        start = 20.dp,
        end = 20.dp,
        top = status.calculateTopPadding() + 16.dp,
        bottom = navBars.calculateBottomPadding() + 112.dp,
    )

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            enterTransition = { fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 24 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically(tween(250)) { it / 24 } },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    contentPadding = tabPadding,
                    onOpenProfile = { nav.switchTab(Routes.PROFILE) },
                    onOpenHabits = { nav.switchTab(Routes.HABITS) },
                    onOpenIntegrations = { nav.navigate(Routes.INTEGRATIONS) },
                )
            }
            composable(Routes.HABITS) { HabitsScreen(tabPadding) }
            composable(Routes.STATS) { StatsScreen(tabPadding) }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    contentPadding = tabPadding,
                    onEditProfile = { nav.navigate(Routes.EDIT_PROFILE) },
                    onChangePassword = { nav.navigate(Routes.CHANGE_PASSWORD) },
                    onIntegrations = { nav.navigate(Routes.INTEGRATIONS) },
                )
            }
            composable(Routes.EDIT_PROFILE) { EditProfileScreen(onboarding = false, onDone = { nav.popBackStack() }) }
            composable(Routes.CHANGE_PASSWORD) { ChangePasswordScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.INTEGRATIONS) { IntegrationsScreen(onBack = { nav.popBackStack() }) }
        }

        AnimatedVisibility(
            visible = tabIndex >= 0,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        ) {
            GlassBottomBar(
                items = navItems,
                selectedIndex = tabIndex.coerceAtLeast(0),
                onSelect = { nav.switchTab(tabs[it]) },
            )
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
