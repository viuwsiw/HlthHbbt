package com.tshk.hlthhbbty

import android.app.Application
import android.content.Context
import com.tshk.hlthhbbty.data.auth.AuthRepository
import com.tshk.hlthhbbty.data.auth.SessionStore
import com.tshk.hlthhbbty.data.db.AppDatabase
import com.tshk.hlthhbbty.data.repo.HabitRepository
import com.tshk.hlthhbbty.data.repo.UserRepository
import com.tshk.hlthhbbty.data.repo.WaterRepository

class HlthApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Простой DI-контейнер: всё создаётся один раз на процесс. */
class AppContainer(context: Context) {
    private val db = AppDatabase.build(context)
    val habits = HabitRepository(db.habitDao())
    val water = WaterRepository(db.waterDao())
    val users = UserRepository(context, db.userDao())
    val auth = AuthRepository(db.userDao(), SessionStore(context), habits)
}
