package ru.rudra.androidos.pa.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        InboxItemRow::class,
        TranscriptRow::class,
        EntityRow::class,
        ReminderRow::class,
        ChangeRow::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class PaDatabase : RoomDatabase() {
    abstract fun changeDao(): ChangeDao
    abstract fun inboxDao(): InboxDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun entityDao(): EntityDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        // v1 -> v2: change log gains logicalClock + provenance (bundleHash coverage).
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE changes ADD COLUMN logicalClock TEXT")
                db.execSQL("ALTER TABLE changes ADD COLUMN provenanceJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        @Volatile
        private var instance: PaDatabase? = null

        fun get(context: Context): PaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PaDatabase::class.java,
                    "pa.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
