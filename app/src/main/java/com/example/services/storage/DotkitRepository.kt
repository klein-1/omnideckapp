package com.example.services.storage

import android.content.Context
import android.content.SharedPreferences
import com.example.design.ChapterHue
import com.example.design.MotionMode
import com.example.design.RegionFormat
import com.example.design.ThemeMode
import com.example.design.UiDensity
import com.example.registry.DataKind
import kotlinx.coroutines.flow.Flow

class DotkitRepository(context: Context) {
    private val db = DotkitDatabase.getInstance(context)
    private val dao = db.dao()
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("omni_deck_prefs", Context.MODE_PRIVATE)

    val counters: Flow<List<TallyCounterEntity>> = dao.observeCounters()
    val notes: Flow<List<NoteEntity>> = dao.observeNotes()
    val expenses: Flow<List<ExpenseEntity>> = dao.observeExpenses()
    val vaultEntries: Flow<List<VaultEntryEntity>> = dao.observeVaultEntries()
    val healthLogs: Flow<List<HealthLogEntity>> = dao.observeHealthLogs()
    val benchmarkScores: Flow<List<BenchmarkScoreEntity>> = dao.observeBenchmarkScores()
    val downloadJobs: Flow<List<DownloadJobEntity>> = dao.observeDownloadJobs()
    val savedFiles: Flow<List<SavedFileEntity>> = dao.observeSavedFiles()
    val history: Flow<List<ToolHistoryEntity>> = dao.observeHistory()
    val networkLedger: Flow<List<NetworkLedgerEntity>> = dao.observeNetworkLedger()

    suspend fun insertCounter(title: String, step: Int, goal: Int): Long =
        dao.insertCounter(TallyCounterEntity(title = title, count = 0, step = step, goal = goal))

    suspend fun updateCounter(entity: TallyCounterEntity) = dao.updateCounter(entity)
    suspend fun deleteCounter(id: Long) = dao.deleteCounterById(id)

    suspend fun insertNote(
        title: String,
        body: String,
        isChecklist: Boolean,
        noteType: String = "NOTE",
        metaTag: String = ""
    ): Long = dao.insertNote(
        NoteEntity(
            title = title,
            body = body,
            isChecklist = isChecklist,
            noteType = noteType,
            metaTag = metaTag
        )
    )

    suspend fun updateNote(note: NoteEntity) = dao.updateNote(note)
    suspend fun deleteNote(id: Long) = dao.deleteNoteById(id)

    suspend fun insertExpense(title: String, amount: Double, category: String, kind: String, note: String = ""): Long =
        dao.insertExpense(ExpenseEntity(title = title, amount = amount, category = category, kind = kind, note = note))

    suspend fun deleteExpense(id: Long) = dao.deleteExpenseById(id)

    suspend fun insertVaultEntry(label: String, username: String, encryptedPayload: String, ivBase64: String, saltBase64: String): Long =
        dao.insertVaultEntry(
            VaultEntryEntity(
                label = label,
                username = username,
                encryptedPayload = encryptedPayload,
                ivBase64 = ivBase64,
                saltBase64 = saltBase64
            )
        )

    suspend fun deleteVaultEntry(id: Long) = dao.deleteVaultEntryById(id)

    suspend fun insertHealthLog(
        logType: String,
        primaryVal: Double,
        secondaryVal: Double = 0.0,
        label: String = "",
        note: String = "",
        completed: Boolean = false
    ): Long = dao.insertHealthLog(
        HealthLogEntity(
            logType = logType,
            primaryVal = primaryVal,
            secondaryVal = secondaryVal,
            label = label,
            note = note,
            completed = completed
        )
    )

    suspend fun updateHealthLog(entry: HealthLogEntity) = dao.updateHealthLog(entry)
    suspend fun deleteHealthLog(id: Long) = dao.deleteHealthLogById(id)

    suspend fun recordBenchmarkScore(testId: String, scoreLabel: String, numericScore: Double, higherIsBetter: Boolean): Long =
        dao.insertBenchmarkScore(
            BenchmarkScoreEntity(
                testId = testId,
                scoreLabel = scoreLabel,
                numericScore = numericScore,
                higherIsBetter = higherIsBetter
            )
        )

    suspend fun insertDownloadJob(job: DownloadJobEntity): Long = dao.insertDownloadJob(job)
    suspend fun updateDownloadJob(job: DownloadJobEntity) = dao.updateDownloadJob(job)
    suspend fun deleteDownloadJob(id: Long) = dao.deleteDownloadJobById(id)

    suspend fun saveOutputToFile(
        fileName: String,
        kind: DataKind,
        originToolId: String,
        originToolName: String,
        contentText: String
    ): Long {
        val bytes = contentText.encodeToByteArray().size.toLong()
        return dao.insertSavedFile(
            SavedFileEntity(
                fileName = fileName,
                mimeType = kind.mime,
                dataKindId = kind.name,
                sizeBytes = bytes,
                originToolId = originToolId,
                originToolName = originToolName,
                contentText = contentText
            )
        )
    }

    suspend fun restoreSavedFile(file: SavedFileEntity): Long = dao.insertSavedFile(file)
    suspend fun deleteSavedFile(id: Long) = dao.deleteSavedFileById(id)

    suspend fun logToolHistory(toolId: String, summary: String, payload: String) =
        dao.insertHistory(ToolHistoryEntity(toolId = toolId, summary = summary, payload = payload))

    suspend fun logNetworkCall(toolId: String, host: String, bytesOut: Long, bytesIn: Long, note: String) =
        dao.insertNetworkEntry(
            NetworkLedgerEntity(
                toolId = toolId,
                host = host,
                bytesOut = bytesOut,
                bytesIn = bytesIn,
                statusNote = note
            )
        )

    suspend fun panicWipeAllData() {
        dao.clearAllCounters()
        dao.clearAllNotes()
        dao.clearAllExpenses()
        dao.clearAllVaultEntries()
        dao.clearAllHealthLogs()
        dao.clearAllBenchmarkScores()
        dao.clearAllDownloadJobs()
        dao.clearAllSavedFiles()
        dao.clearAllHistory()
        dao.clearNetworkLedger()
        prefs.edit().clear().apply()
    }

    // Preferences
    fun getThemeMode(): ThemeMode =
        runCatching { ThemeMode.valueOf(prefs.getString("theme", ThemeMode.PAPER.name)!!) }.getOrDefault(ThemeMode.PAPER)

    fun setThemeMode(mode: ThemeMode) = prefs.edit().putString("theme", mode.name).apply()

    fun getAccentHue(): ChapterHue =
        runCatching { ChapterHue.valueOf(prefs.getString("accent", ChapterHue.VERMILION.name)!!) }.getOrDefault(ChapterHue.VERMILION)

    fun setAccentHue(hue: ChapterHue) = prefs.edit().putString("accent", hue.name).apply()

    fun getMotionMode(): MotionMode =
        runCatching { MotionMode.valueOf(prefs.getString("motion", MotionMode.FULL.name)!!) }.getOrDefault(MotionMode.FULL)

    fun setMotionMode(mode: MotionMode) = prefs.edit().putString("motion", mode.name).apply()

    fun getUiScale(): Float = prefs.getFloat("ui_scale", 1.0f).coerceIn(0.85f, 1.4f)
    fun setUiScale(scale: Float) = prefs.edit().putFloat("ui_scale", scale).apply()

    fun getDensity(): UiDensity =
        runCatching { UiDensity.valueOf(prefs.getString("density", UiDensity.COZY.name)!!) }.getOrDefault(UiDensity.COZY)

    fun setDensity(density: UiDensity) = prefs.edit().putString("density", density.name).apply()

    fun isDyslexiaFriendly(): Boolean = prefs.getBoolean("dyslexia", false)
    fun setDyslexiaFriendly(v: Boolean) = prefs.edit().putBoolean("dyslexia", v).apply()

    fun isSoundEnabled(): Boolean = prefs.getBoolean("sound", false)
    fun setSoundEnabled(v: Boolean) = prefs.edit().putBoolean("sound", v).apply()

    fun isHapticsEnabled(): Boolean = prefs.getBoolean("haptics", true)
    fun setHapticsEnabled(v: Boolean) = prefs.edit().putBoolean("haptics", v).apply()

    fun isConfettiEnabled(): Boolean = prefs.getBoolean("confetti", true)
    fun setConfettiEnabled(v: Boolean) = prefs.edit().putBoolean("confetti", v).apply()

    fun getRegionFormat(): RegionFormat =
        runCatching { RegionFormat.valueOf(prefs.getString("region", RegionFormat.EN_IN.name)!!) }.getOrDefault(RegionFormat.EN_IN)

    fun setRegionFormat(region: RegionFormat) = prefs.edit().putString("region", region.name).apply()

    fun getCompanionUrl(): String = prefs.getString("companion_url", "http://127.0.0.1:8787") ?: "http://127.0.0.1:8787"
    fun setCompanionUrl(url: String) = prefs.edit().putString("companion_url", url).apply()

    fun getCompanionToken(): String = prefs.getString("companion_token", "") ?: ""
    fun setCompanionToken(token: String) = prefs.edit().putString("companion_token", token).apply()

    fun getGeminiKeyOverride(): String = prefs.getString("gemini_key", "") ?: ""
    fun setGeminiKeyOverride(key: String) = prefs.edit().putString("gemini_key", key).apply()

    fun isOfflineOnlyAi(): Boolean = prefs.getBoolean("offline_only_ai", false)
    fun setOfflineOnlyAi(v: Boolean) = prefs.edit().putBoolean("offline_only_ai", v).apply()

    fun getPinnedToolIds(): List<String> {
        val raw = prefs.getString("pinned_tools", "") ?: ""
        return raw.split(",").filter { it.isNotBlank() }
    }

    fun setPinnedToolIds(ids: List<String>) =
        prefs.edit().putString("pinned_tools", ids.distinct().joinToString(",")).apply()

    fun getRecentToolIds(): List<String> {
        val raw = prefs.getString("recent_tools", "") ?: ""
        return raw.split(",").filter { it.isNotBlank() }
    }

    fun pushRecentToolId(id: String) {
        val updated = (listOf(id) + getRecentToolIds().filter { it != id }).take(10)
        prefs.edit().putString("recent_tools", updated.joinToString(",")).apply()
    }

    fun getLastCaseStyle(): String = prefs.getString("last_case_style", "TITLE_AP") ?: "TITLE_AP"
    fun setLastCaseStyle(styleName: String) = prefs.edit().putString("last_case_style", styleName).apply()

    fun getLastHashAlgorithm(): String = prefs.getString("last_hash_alg", "SHA256") ?: "SHA256"
    fun setLastHashAlgorithm(algName: String) = prefs.edit().putString("last_hash_alg", algName).apply()
}
