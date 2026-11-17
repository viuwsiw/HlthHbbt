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

class HlthApp : Application(), coil.ImageLoaderFactory {
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this)
        .components {
            if (android.os.Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory())
            else add(coil.decode.GifDecoder.Factory())
        }
        .crossfade(true)
        .build()

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
    val auth: AuthRepository = AuthRepository(db.userDao(), SessionStore(context), habits, { messenger }, { cloud })
    val history = HistoryRepository(users, habits, water, notes)
    val backup = BackupRepository(context, db)
    val health = HealthRepository(context, habits)
    val reminders = ReminderScheduler(context)
    val updates = com.tshk.hlthhbbty.update.UpdateRepository(context)
    val updater = com.tshk.hlthhbbty.update.UpdateController(updates, scope) { settings.current().betaChannel }
    val bedtime = com.tshk.hlthhbbty.bedtime.BedtimeChecker(context, habits)
    val walks = com.tshk.hlthhbbty.walk.WalkTracker(context, db.walkDao(), habits)
    /** Запрос «открыть экран прогулки» (нажатие на уведомление). */
    val walkOpenRequest = kotlinx.coroutines.flow.MutableStateFlow(false)
    /** Экран, который просит открыть виджет: focus / walk / habits. */
    val widgetRoute = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val messenger: com.tshk.hlthhbbty.messenger.MessengerRepository = com.tshk.hlthhbbty.messenger.MessengerRepository(
        context, scope, db.chatDao(),
        localUser = { auth.currentUserId.first()?.let { db.userDao().findById(it) } },
        history = { history.once(it) },
    )
    val cloud: com.tshk.hlthhbbty.cloud.CloudBackup = com.tshk.hlthhbbty.cloud.CloudBackup(context, db, settings, backup) { messenger }
    private var cloudJob: kotlinx.coroutines.Job? = null

    /** Облачная копия через 15 секунд после последнего изменения. */
    fun backupSoon() {
        cloudJob?.cancel()
        cloudJob = scope.launch {
            kotlinx.coroutines.delay(15_000)
            auth.currentUserId.first()?.let { runCatching { cloud.backupNow(it) } }
        }
    }

    val onboarding = com.tshk.hlthhbbty.onboarding.OnboardingStore(context)
    val focusTimer = com.tshk.hlthhbbty.focus.FocusTimer(context)
    val media = com.tshk.hlthhbbty.media.MediaRepository(context)
    val spotify = com.tshk.hlthhbbty.spotify.SpotifyRepository(context, scope)

    private var profilePush: kotlinx.coroutines.Job? = null

    fun start() {
        // Мессенджер и аккаунты: перенос локальных аккаунтов на сервер, запуск, уведомления, фоновая синхронизация.
        messenger.start()
        messenger.onPrivacyChanged = { backupSoon() }
        scope.launch { runCatching { auth.migrateLocalAccounts(auth.currentUserId.first()) } }
        com.tshk.hlthhbbty.messenger.MessengerSyncWorker.schedule(context)
        scope.launch {
            messenger.incoming.collect { ev -> if (ev != null) Notifications.showChat(context, ev.peer, ev.text) }
        }
        com.tshk.hlthhbbty.update.UpdateCheckWorker.schedule(context)
        bedtime.schedule()
        scope.launch {
            // Разовое обновление стандартных привычек (1.0.1): «Хобби», автоотбой, прогулка с картой.
            val prefs = context.getSharedPreferences("migrations", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("defaults_101", false)) {
                runCatching { habits.migrateDefaults() }
                prefs.edit().putBoolean("defaults_101", true).apply()
            }
            // 1.0.3: кнопка багтрекера по умолчанию только у бета-тестеров — интерфейс чище.
            if (!prefs.getBoolean("ux_103", false)) {
                settings.update { it.copy(bugButton = it.betaChannel) }
                prefs.edit().putBoolean("ux_103", true).apply()
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
                if (id == null && messenger.store.account.value != null) messenger.unlink()
                if (id == null) reminders.cancelAll()
                else reminders.rescheduleAll(habits.activeOnce(id), settings.current())
                HabitsWidget.refresh(context)
            }
        }
        // Любая смена настроек — в облачную копию.
        scope.launch { settings.settings.distinctUntilChanged().drop(1).collect { backupSoon() } }
        // Состояние копии для экрана настроек
        scope.launch {
            kotlinx.coroutines.flow.combine(auth.currentUserId, messenger.store.account) { id, _ -> id }.collect { id ->
                cloud.refreshState(id?.let { db.userDao().findById(it)?.username })
            }
        }
        // Что сейчас играет — собеседникам (если разрешено конфиденциальностью)
        scope.launch {
            kotlinx.coroutines.flow.combine(media.state, messenger.store.privacy, messenger.store.account) { st, p, acc -> Triple(st, p, acc) }
                .collect { (st, p, acc) ->
                    if (acc == null) return@collect
                    val track = st?.takeIf { it.isPlaying && p.nowPlaying != com.tshk.hlthhbbty.messenger.Visibility.NOBODY }
                    messenger.shareNowPlaying(track?.title.orEmpty(), track?.artist.orEmpty(), track?.appName.orEmpty(), p.nowPlaying.name)
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
        backupSoon()
        // Профиль в мессенджере — не чаще раза в 20 секунд.
        profilePush?.cancel()
        profilePush = scope.launch {
            kotlinx.coroutines.delay(20_000)
            runCatching { messenger.pushProfile() }
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
