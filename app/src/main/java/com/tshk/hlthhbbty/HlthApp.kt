package com.tshk.hlthhbbty

import android.app.Application
import android.content.Context
import com.tshk.hlthhbbty.data.Achievements
import com.tshk.hlthhbbty.data.SettingsStore
import com.tshk.hlthhbbty.data.auth.AuthRepository
import com.tshk.hlthhbbty.data.auth.SessionStore
import com.tshk.hlthhbbty.data.db.AppDatabase
import com.tshk.hlthhbbty.data.db.HabitEntity
import com.tshk.hlthhbbty.data.repo.BackupRepository
import com.tshk.hlthhbbty.data.repo.HabitChangeListener
import com.tshk.hlthhbbty.data.repo.HabitRepository
import com.tshk.hlthhbbty.data.repo.HistoryRepository
import com.tshk.hlthhbbty.data.repo.NoteRepository
import com.tshk.hlthhbbty.data.repo.UserRepository
import com.tshk.hlthhbbty.data.repo.WaterRepository
import com.tshk.hlthhbbty.health.HealthRepository
import com.tshk.hlthhbbty.reminders.Notifications
import com.tshk.hlthhbbty.reminders.ReminderScheduler
import com.tshk.hlthhbbty.widget.HabitsWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HlthApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        container = AppContainer(this)
        container.start()
    }
}

/** Простой DI-контейнер: всё создаётся один раз на процесс. */
class AppContainer(private val context: Context) {
    val appContext: Context get() = context
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val db = AppDatabase.build(context)

    val settings = SettingsStore(context)
    val habits = HabitRepository(db.habitDao())
    val water = WaterRepository(db.waterDao())
    val notes = NoteRepository(db.noteDao())
    val users = UserRepository(context, db.userDao())
    val auth = AuthRepository(db.userDao(), SessionStore(context), habits)
    val history = HistoryRepository(users, habits, water, notes)
    val backup = BackupRepository(context, db)
    val health = HealthRepository(context, habits)
    val reminders = ReminderScheduler(context)
    val updates = com.tshk.hlthhbbty.update.UpdateRepository(context)
    val updater = com.tshk.hlthhbbty.update.UpdateController(updates, scope)
    val bedtime = com.tshk.hlthhbbty.bedtime.BedtimeChecker(context, habits)
    val walks = com.tshk.hlthhbbty.walk.WalkTracker(context, db.walkDao(), habits)
    /** Запрос «открыть экран прогулки» (нажатие на уведомление). */
    val walkOpenRequest = kotlinx.coroutines.flow.MutableStateFlow(false)
    /** Экран, который просит открыть виджет: focus / walk / habits. */
    val widgetRoute = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val focusTimer = com.tshk.hlthhbbty.focus.FocusTimer(context)
    val media = com.tshk.hlthhbbty.media.MediaRepository(context)
    val spotify = com.tshk.hlthhbbty.spotify.SpotifyRepository(context, scope)

    fun start() {
        com.tshk.hlthhbbty.update.UpdateCheckWorker.schedule(context)
        bedtime.schedule()
        scope.launch {
            // Разовое обновление стандартных привычек (1.0.1): «Хобби», автоотбой, прогулка с картой.
            val prefs = context.getSharedPreferences("migrations", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("defaults_101", false)) {
                runCatching { habits.migrateDefaults() }
                prefs.edit().putBoolean("defaults_101", true).apply()
            }
        }
        // Разблокировка, пока приложение живо — сразу пересчитываем «отбой».
        val unlockFilter = android.content.IntentFilter(android.content.Intent.ACTION_USER_PRESENT)
        androidx.core.content.ContextCompat.registerReceiver(context, object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, i: android.content.Intent) {
                scope.launch { auth.currentUserId.first()?.let { runCatching { bedtime.evaluate(it) } } }
            }
        }, unlockFilter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        habits.listener = object : HabitChangeListener {
            override suspend fun onHabitSaved(habit: HabitEntity) = reminders.scheduleHabit(habit)
            override suspend fun onHabitDeleted(habitId: Long) {
                reminders.cancelHabit(habitId)
                Notifications.cancel(context, Notifications.habitId(habitId))
            }
            override suspend fun onProgressChanged() = dataChanged()
        }
        water.onChanged = { dataChanged() }

        // Вход/выход: перепланировать или снять все напоминания.
        scope.launch {
            auth.currentUserId.distinctUntilChanged().collect { id ->
                if (id == null) reminders.cancelAll()
                else reminders.rescheduleAll(habits.activeOnce(id), settings.current())
                HabitsWidget.refresh(context)
            }
        }
        // Виджет перекрашивается вместе с темой и акцентом.
        scope.launch {
            settings.settings.distinctUntilChanged { a, b -> a.themeMode == b.themeMode && a.accentIndex == b.accentIndex }
                .drop(1)
                .collect { HabitsWidget.refresh(context) }
        }
        scope.launch {
            settings.settings.distinctUntilChanged { a, b -> a.waterReminders == b.waterReminders && a.waterIntervalHours == b.waterIntervalHours }
                .drop(1)
                .collect { reminders.scheduleWater(it) }
        }
    }

    private fun dataChanged() {
        scope.launch {
            HabitsWidget.refresh(context)
            checkAchievements()
        }
    }

    private suspend fun checkAchievements() {
        val userId = auth.currentUserId.first() ?: return
        val unlocked = Achievements.compute(history.once(userId)).filter { it.unlocked }
        val seen = settings.seenAchievements()
        val fresh = unlocked.filter { "$userId:${it.id}" !in seen }
        fresh.forEachIndexed { i, a -> Notifications.showAchievement(context, i + a.id.hashCode() % 1000, a.title, a.emoji) }
        if (fresh.isNotEmpty()) settings.markSeen(fresh.map { "$userId:${it.id}" }.toSet())
    }
}
