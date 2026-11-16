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
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.tshk.hlthhbbty.data.db.HabitKind
import com.tshk.hlthhbbty.ui.achievements.AchievementsScreen
import com.tshk.hlthhbbty.ui.focus.FocusScreen
import com.tshk.hlthhbbty.ui.habits.HabitEditorScreen
import com.tshk.hlthhbbty.ui.settings.SettingsScreen
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
        UpdateHost()
        val context = androidx.compose.ui.platform.LocalContext.current
        val settings by (context.applicationContext as com.tshk.hlthhbbty.HlthApp).container.settings.settings
            .collectAsStateWithLifecycle(com.tshk.hlthhbbty.data.AppSettings())
        if (settings.bugButton) BugTrackerButton()
        AskNotificationsOnce()
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
    const val ACHIEVEMENTS = "achievements"
    const val PLAYER = "spotify/player"
    const val SPOTIFY_PLAYLIST = "spotify/playlist/{playlistId}"
    const val SETTINGS = "settings"
    const val HABIT_EDIT = "habit/edit?id={id}&kind={kind}"
    const val FOCUS = "focus/{id}"
    fun habitEdit(id: Long? = null, kind: String = HabitKind.GOOD) = "habit/edit?id=${id ?: -1}&kind=$kind"
    fun focus(id: Long) = "focus/$id"
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
                    onFocus = { nav.navigate(Routes.focus(it.id)) },
                    onOpenPlayer = { nav.navigate(Routes.PLAYER) },
                )
            }
            composable(Routes.HABITS) {
                HabitsScreen(
                    tabPadding,
                    onCreate = { kind -> nav.navigate(Routes.habitEdit(kind = kind)) },
                    onEdit = { nav.navigate(Routes.habitEdit(it.id, it.kind)) },
                    onFocus = { nav.navigate(Routes.focus(it.id)) },
                )
            }
            composable(Routes.STATS) { StatsScreen(tabPadding) }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    contentPadding = tabPadding,
                    onEditProfile = { nav.navigate(Routes.EDIT_PROFILE) },
                    onChangePassword = { nav.navigate(Routes.CHANGE_PASSWORD) },
                    onIntegrations = { nav.navigate(Routes.INTEGRATIONS) },
                    onAchievements = { nav.navigate(Routes.ACHIEVEMENTS) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.EDIT_PROFILE) { EditProfileScreen(onboarding = false, onDone = { nav.popBackStack() }) }
            composable(Routes.CHANGE_PASSWORD) { ChangePasswordScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.INTEGRATIONS) {
                IntegrationsScreen(
                    onBack = { nav.popBackStack() },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onPlayer = { nav.navigate(Routes.PLAYER) },
                    onPlaylist = { nav.navigate("spotify/playlist/$it") },
                )
            }
            composable(Routes.PLAYER) { com.tshk.hlthhbbty.ui.integrations.PlayerScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.SPOTIFY_PLAYLIST, arguments = listOf(navArgument("playlistId") { type = NavType.StringType })) {
                com.tshk.hlthhbbty.ui.integrations.SpotifyPlaylistScreen(onBack = { nav.popBackStack() }, onPlayer = { nav.navigate(Routes.PLAYER) })
            }
            composable(Routes.ACHIEVEMENTS) { AchievementsScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
            composable(
                Routes.HABIT_EDIT,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType; defaultValue = "-1" },
                    navArgument("kind") { type = NavType.StringType; defaultValue = HabitKind.GOOD },
                ),
            ) { HabitEditorScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.FOCUS, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                FocusScreen(
                    onBack = { nav.popBackStack() },
                    onIntegrations = { nav.navigate(Routes.INTEGRATIONS) },
                    onPlayer = { nav.navigate(Routes.PLAYER) },
                )
            }
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


/** Один раз просим разрешение на уведомления (Android 13+): напоминания и сообщения приходят в шторку. */
@Composable
private fun AskNotificationsOnce() {
    if (android.os.Build.VERSION.SDK_INT < 33) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) {}
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("ui_flags", android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean("asked_notifications", false) && !com.tshk.hlthhbbty.reminders.Notifications.canPost(context)) {
            prefs.edit().putBoolean("asked_notifications", true).apply()
            launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
