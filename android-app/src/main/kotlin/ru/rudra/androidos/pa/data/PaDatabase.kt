package ru.rudra.androidos.pa.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        InboxItemRow::class,
        TranscriptRow::class,
        EntityRow::class,
        ReminderRow::class,
        ChangeRow::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class PaDatabase : RoomDatabase() {
    abstract fun changeDao(): ChangeDao
    abstract fun inboxDao(): InboxDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun entityDao(): EntityDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile
        private var instance: PaDatabase? = null

        fun get(context: Context): PaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PaDatabase::class.java,
                    "pa.db",
                ).build().also { instance = it }
            }
    }
}
