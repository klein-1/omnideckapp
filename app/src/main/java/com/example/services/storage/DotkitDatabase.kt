package com.example.services.storage

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tally_counters")
data class TallyCounterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val count: Int = 0,
    val step: Int = 1,
    val goal: Int = 100,
    val lapsLog: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_files")
data class SavedFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val mimeType: String,
    val dataKindId: String,
    val sizeBytes: Long,
    val originToolId: String,
    val originToolName: String,
    val contentText: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tool_history")
data class ToolHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val toolId: String,
    val summary: String,
    val payload: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "network_ledger")
data class NetworkLedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val toolId: String,
    val host: String,
    val bytesOut: Long,
    val bytesIn: Long,
    val statusNote: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val pinned: Boolean = false,
    val isChecklist: Boolean = false,
    val noteType: String = "NOTE", // NOTE, TODO, SHOPPING
    val metaTag: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Double,
    val category: String,
    val kind: String = "EXPENSE", // EXPENSE or INCOME
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "vault_entries")
data class VaultEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val username: String,
    val encryptedPayload: String,
    val ivBase64: String,
    val saltBase64: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "health_logs")
data class HealthLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val logType: String, // WATER_ML, SLEEP_MIN, BP, HR_BPM, MED_REMINDER, STEPS
    val primaryVal: Double,
    val secondaryVal: Double = 0.0,
    val label: String = "",
    val note: String = "",
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "benchmark_scores")
data class BenchmarkScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val testId: String,
    val scoreLabel: String,
    val numericScore: Double,
    val higherIsBetter: Boolean,
    val recordedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "download_jobs")
data class DownloadJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val platform: String,
    val formatSpec: String,
    val status: String, // QUEUED, RUNNING, DONE, ERROR
    val progress: Int = 0,
    val outputPath: String = "",
    val errorMsg: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface DotkitDao {
    // Tally Counters
    @Query("SELECT * FROM tally_counters ORDER BY id ASC")
    fun observeCounters(): Flow<List<TallyCounterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCounter(counter: TallyCounterEntity): Long

    @Update
    suspend fun updateCounter(counter: TallyCounterEntity)

    @Query("DELETE FROM tally_counters WHERE id = :id")
    suspend fun deleteCounterById(id: Long)

    @Query("DELETE FROM tally_counters")
    suspend fun clearAllCounters()

    // Notes / Todos / Shopping
    @Query("SELECT * FROM notes ORDER BY pinned DESC, updatedAt DESC")
    fun observeNotes(): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long)

    @Query("DELETE FROM notes")
    suspend fun clearAllNotes()

    // Expenses
    @Query("SELECT * FROM expenses ORDER BY createdAt DESC")
    fun observeExpenses(): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(item: ExpenseEntity): Long

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpenseById(id: Long)

    @Query("DELETE FROM expenses")
    suspend fun clearAllExpenses()

    // Vault
    @Query("SELECT * FROM vault_entries ORDER BY updatedAt DESC")
    fun observeVaultEntries(): Flow<List<VaultEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaultEntry(entry: VaultEntryEntity): Long

    @Query("DELETE FROM vault_entries WHERE id = :id")
    suspend fun deleteVaultEntryById(id: Long)

    @Query("DELETE FROM vault_entries")
    suspend fun clearAllVaultEntries()

    // Health Logs
    @Query("SELECT * FROM health_logs ORDER BY createdAt DESC")
    fun observeHealthLogs(): Flow<List<HealthLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHealthLog(entry: HealthLogEntity): Long

    @Update
    suspend fun updateHealthLog(entry: HealthLogEntity)

    @Query("DELETE FROM health_logs WHERE id = :id")
    suspend fun deleteHealthLogById(id: Long)

    @Query("DELETE FROM health_logs")
    suspend fun clearAllHealthLogs()

    // Benchmark & Game Scores
    @Query("SELECT * FROM benchmark_scores ORDER BY recordedAt DESC")
    fun observeBenchmarkScores(): Flow<List<BenchmarkScoreEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBenchmarkScore(entry: BenchmarkScoreEntity): Long

    @Query("DELETE FROM benchmark_scores")
    suspend fun clearAllBenchmarkScores()

    // Download Jobs
    @Query("SELECT * FROM download_jobs ORDER BY createdAt DESC")
    fun observeDownloadJobs(): Flow<List<DownloadJobEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloadJob(job: DownloadJobEntity): Long

    @Update
    suspend fun updateDownloadJob(job: DownloadJobEntity)

    @Query("DELETE FROM download_jobs WHERE id = :id")
    suspend fun deleteDownloadJobById(id: Long)

    @Query("DELETE FROM download_jobs")
    suspend fun clearAllDownloadJobs()

    // Saved Files
    @Query("SELECT * FROM saved_files ORDER BY createdAt DESC")
    fun observeSavedFiles(): Flow<List<SavedFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedFile(file: SavedFileEntity): Long

    @Query("DELETE FROM saved_files WHERE id = :id")
    suspend fun deleteSavedFileById(id: Long)

    @Query("DELETE FROM saved_files")
    suspend fun clearAllSavedFiles()

    // Tool History
    @Query("SELECT * FROM tool_history ORDER BY createdAt DESC LIMIT 100")
    fun observeHistory(): Flow<List<ToolHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: ToolHistoryEntity)

    @Query("DELETE FROM tool_history")
    suspend fun clearAllHistory()

    // Network Ledger
    @Query("SELECT * FROM network_ledger ORDER BY createdAt DESC LIMIT 100")
    fun observeNetworkLedger(): Flow<List<NetworkLedgerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNetworkEntry(entry: NetworkLedgerEntity)

    @Query("DELETE FROM network_ledger")
    suspend fun clearNetworkLedger()
}

@Database(
    entities = [
        TallyCounterEntity::class,
        SavedFileEntity::class,
        ToolHistoryEntity::class,
        NetworkLedgerEntity::class,
        NoteEntity::class,
        ExpenseEntity::class,
        VaultEntryEntity::class,
        HealthLogEntity::class,
        BenchmarkScoreEntity::class,
        DownloadJobEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class DotkitDatabase : RoomDatabase() {
    abstract fun dao(): DotkitDao

    companion object {
        @Volatile
        private var INSTANCE: DotkitDatabase? = null

        fun getInstance(context: Context): DotkitDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DotkitDatabase::class.java,
                    "omni_deck_studio.db"
                ).fallbackToDestructiveMigration(true).build().also { INSTANCE = it }
            }
        }
    }
}
