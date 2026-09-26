package ru.rudra.androidos.pa.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChangeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(row: ChangeRow): Long

    @Query("SELECT EXISTS(SELECT 1 FROM changes WHERE idempotencyKey = :key OR id = :id)")
    fun exists(id: String, key: String): Boolean
}

@Dao
interface InboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(row: InboxItemRow)

    @Query("SELECT * FROM inbox_items WHERE deletedAt IS NULL ORDER BY capturedAt DESC")
    fun all(): List<InboxItemRow>

    @Query("SELECT * FROM inbox_items WHERE id = :id")
    fun byId(id: String): InboxItemRow?

    @Query("UPDATE inbox_items SET state = :state, updatedAt = :updatedAt WHERE id = :id")
    fun updateState(id: String, state: String, updatedAt: String)

    @Query("UPDATE inbox_items SET transcriptId = :transcriptId WHERE id = :id")
    fun setTranscriptId(id: String, transcriptId: String)

    @Query("UPDATE inbox_items SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    fun tombstone(id: String, deletedAt: String)
}

@Dao
interface TranscriptDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(row: TranscriptRow)

    @Query("SELECT * FROM transcripts WHERE inboxItemId = :inboxItemId AND deletedAt IS NULL")
    fun forInboxItem(inboxItemId: String): List<TranscriptRow>

    @Query("UPDATE transcripts SET text = :text, status = :status, editedAt = :editedAt WHERE id = :id")
    fun updateTextAndStatus(id: String, text: String, status: String, editedAt: String)
}

@Dao
interface EntityDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(row: EntityRow)

    @Query("SELECT * FROM entities WHERE status = 'APPROVED' AND deletedAt IS NULL")
    fun approved(): List<EntityRow>
}

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(row: ReminderRow)

    @Query("SELECT * FROM reminders WHERE state = 'ACTIVE'")
    fun active(): List<ReminderRow>
}
