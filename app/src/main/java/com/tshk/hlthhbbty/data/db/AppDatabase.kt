package com.tshk.hlthhbbty.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserEntity::class, HabitEntity::class, HabitCheckEntity::class, WaterLogEntity::class,
        FreezeEntity::class, DayNoteEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun habitDao(): HabitDao
    abstract fun waterDao(): WaterDao
    abstract fun noteDao(): NoteDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN scheduleMask INTEGER NOT NULL DEFAULT 127")
                db.execSQL("ALTER TABLE habits ADD COLUMN reminderMinutes INTEGER")
                db.execSQL("ALTER TABLE habits ADD COLUMN kind TEXT NOT NULL DEFAULT 'GOOD'")
                db.execSQL("ALTER TABLE habits ADD COLUMN quitSince INTEGER")
                db.execSQL("ALTER TABLE habits ADD COLUMN healthMetric TEXT")
                db.execSQL("ALTER TABLE habits ADD COLUMN healthTarget INTEGER")
                db.execSQL("ALTER TABLE habits ADD COLUMN focusMinutes INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `habit_freezes` (`habitId` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`habitId`, `epochDay`), FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `day_notes` (`userId` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL, `mood` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL, PRIMARY KEY(`userId`, `epochDay`), FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "hlth_hbbty.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
