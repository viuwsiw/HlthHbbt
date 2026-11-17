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
        FreezeEntity::class, DayNoteEntity::class, WalkEntity::class, WalkPointEntity::class,
        ChatMessageEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun habitDao(): HabitDao
    abstract fun waterDao(): WaterDao
    abstract fun noteDao(): NoteDao
    abstract fun walkDao(): WalkDao
    abstract fun chatDao(): ChatDao

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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN activity TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `walks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` INTEGER NOT NULL, " +
                        "`habitId` INTEGER, `startedAt` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `distanceM` REAL NOT NULL, " +
                        "`steps` INTEGER NOT NULL, FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_walks_userId` ON `walks` (`userId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `walk_points` (`walkId` INTEGER NOT NULL, `idx` INTEGER NOT NULL, `lat` REAL NOT NULL, " +
                        "`lon` REAL NOT NULL, `time` INTEGER NOT NULL, PRIMARY KEY(`walkId`, `idx`), " +
                        "FOREIGN KEY(`walkId`) REFERENCES `walks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_messages` (`clientId` TEXT NOT NULL, `owner` TEXT NOT NULL, `peer` TEXT NOT NULL, " +
                        "`outgoing` INTEGER NOT NULL, `text` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `serverId` INTEGER NOT NULL, " +
                        "`readable` INTEGER NOT NULL, PRIMARY KEY(`clientId`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_owner_peer_serverId` ON `chat_messages` (`owner`, `peer`, `serverId`)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN kind TEXT NOT NULL DEFAULT 'text'")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN media TEXT")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "hlth_hbbty.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
