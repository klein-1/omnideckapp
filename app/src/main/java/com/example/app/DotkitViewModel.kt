package com.example.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.design.BlueprintPalette
import com.example.design.ChapterHue
import com.example.design.DotkitLookState
import com.example.design.DotkitPalette
import com.example.design.HighContrastPalette
import com.example.design.InkPalette
import com.example.design.MotionMode
import com.example.design.PaperPalette
import com.example.design.RegionFormat
import com.example.design.SepiaPalette
import com.example.design.TerminalPalette
import com.example.design.ThemeMode
import com.example.design.ToolCategory
import com.example.design.UiDensity
import com.example.registry.DataKind
import com.example.registry.ToolRegistry
import com.example.services.capabilities.CapabilitiesService
import com.example.services.capabilities.DeviceCapabilitySnapshot
import com.example.services.companion.CompanionProbeResult
import com.example.services.companion.CompanionService
import com.example.services.storage.BenchmarkScoreEntity
import com.example.services.storage.DotkitRepository
import com.example.services.storage.DownloadJobEntity
import com.example.services.storage.ExpenseEntity
import com.example.services.storage.HealthLogEntity
import com.example.services.storage.NetworkLedgerEntity
import com.example.services.storage.NoteEntity
import com.example.services.storage.SavedFileEntity
import com.example.services.storage.TallyCounterEntity
import com.example.services.storage.VaultEntryEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ShellTab(val id: String, val label: String) {
    HOME("home", "Home"),
    TOOLS("tools", "Tools"),
    FILES("files", "Files"),
    SETTINGS("settings", "Settings")
}

class DotkitViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = DotkitRepository(application)

    private var lastSystemIsDark: Boolean = false

    private val _lookState = MutableStateFlow(loadInitialLookState())
    val lookState: StateFlow<DotkitLookState> = _lookState.asStateFlow()

    private val _activeTab = MutableStateFlow(ShellTab.HOME)
    val activeTab: StateFlow<ShellTab> = _activeTab.asStateFlow()

    private val _selectedCategory = MutableStateFlow<ToolCategory?>(null)
    val selectedCategory: StateFlow<ToolCategory?> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeToolId = MutableStateFlow<String?>(null)
    val activeToolId: StateFlow<String?> = _activeToolId.asStateFlow()

    private val _incomingPayload = MutableStateFlow("")
    val incomingPayload: StateFlow<String> = _incomingPayload.asStateFlow()

    private val _incomingUri = MutableStateFlow<Uri?>(null)
    val incomingUri: StateFlow<Uri?> = _incomingUri.asStateFlow()

    private val _pinnedToolIds = MutableStateFlow(repo.getPinnedToolIds())
    val pinnedToolIds: StateFlow<List<String>> = _pinnedToolIds.asStateFlow()

    private val _recentToolIds = MutableStateFlow(repo.getRecentToolIds())
    val recentToolIds: StateFlow<List<String>> = _recentToolIds.asStateFlow()

    private val _companionUrl = MutableStateFlow(repo.getCompanionUrl())
    val companionUrl: StateFlow<String> = _companionUrl.asStateFlow()

    private val _companionToken = MutableStateFlow(repo.getCompanionToken())
    val companionToken: StateFlow<String> = _companionToken.asStateFlow()

    private val _companionProbe = MutableStateFlow<CompanionProbeResult?>(null)
    val companionProbe: StateFlow<CompanionProbeResult?> = _companionProbe.asStateFlow()

    private val _geminiKeyOverride = MutableStateFlow(repo.getGeminiKeyOverride())
    val geminiKeyOverride: StateFlow<String> = _geminiKeyOverride.asStateFlow()

    private val _lastCaseStyle = MutableStateFlow(repo.getLastCaseStyle())
    val lastCaseStyle: StateFlow<String> = _lastCaseStyle.asStateFlow()

    private val _lastHashAlgorithm = MutableStateFlow(repo.getLastHashAlgorithm())
    val lastHashAlgorithm: StateFlow<String> = _lastHashAlgorithm.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _lastDeletedFile = MutableStateFlow<SavedFileEntity?>(null)
    val lastDeletedFile: StateFlow<SavedFileEntity?> = _lastDeletedFile.asStateFlow()

    private val _capabilities = MutableStateFlow(probeDeviceCapabilities(false))
    val capabilities: StateFlow<DeviceCapabilitySnapshot> = _capabilities.asStateFlow()

    val counters: StateFlow<List<TallyCounterEntity>> = repo.counters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<NoteEntity>> = repo.notes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenses: StateFlow<List<ExpenseEntity>> = repo.expenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vaultEntries: StateFlow<List<VaultEntryEntity>> = repo.vaultEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val healthLogs: StateFlow<List<HealthLogEntity>> = repo.healthLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val benchmarkScores: StateFlow<List<BenchmarkScoreEntity>> = repo.benchmarkScores
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadJobs: StateFlow<List<DownloadJobEntity>> = repo.downloadJobs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedFiles: StateFlow<List<SavedFileEntity>> = repo.savedFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val networkLedger: StateFlow<List<NetworkLedgerEntity>> = repo.networkLedger
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun loadInitialLookState(): DotkitLookState {
        val theme = repo.getThemeMode()
        val accent = repo.getAccentHue()
        return DotkitLookState(
            themeMode = theme,
            palette = resolvePalette(theme, accent, lastSystemIsDark),
            accentHue = accent,
            motionMode = repo.getMotionMode(),
            uiScale = repo.getUiScale(),
            density = repo.getDensity(),
            dyslexiaFriendly = repo.isDyslexiaFriendly(),
            soundEnabled = repo.isSoundEnabled(),
            hapticsEnabled = repo.isHapticsEnabled(),
            confettiEnabled = repo.isConfettiEnabled(),
            regionFormat = repo.getRegionFormat(),
            offlineOnlyAi = repo.isOfflineOnlyAi()
        )
    }

    fun syncSystemDarkTheme(systemIsDark: Boolean) {
        lastSystemIsDark = systemIsDark
        val current = _lookState.value
        if (current.themeMode == ThemeMode.AUTO) {
            _lookState.value = current.copy(
                palette = resolvePalette(ThemeMode.AUTO, current.accentHue, systemIsDark)
            )
        }
    }

    private fun resolvePalette(mode: ThemeMode, accent: ChapterHue, systemIsDark: Boolean): DotkitPalette =
        when (mode) {
            ThemeMode.PAPER -> PaperPalette.copy(accent = accent)
            ThemeMode.INK -> InkPalette.copy(accent = accent)
            ThemeMode.BLUEPRINT -> BlueprintPalette.copy(accent = accent)
            ThemeMode.TERMINAL -> TerminalPalette.copy(accent = accent)
            ThemeMode.SEPIA -> SepiaPalette.copy(accent = accent)
            ThemeMode.HIGH_CONTRAST -> HighContrastPalette.copy(accent = accent)
            ThemeMode.AUTO -> if (systemIsDark) InkPalette.copy(accent = accent) else PaperPalette.copy(accent = accent)
        }

    fun selectTab(tab: ShellTab) {
        _activeToolId.value = null
        _activeTab.value = tab
    }

    fun selectCategory(category: ToolCategory?) {
        _selectedCategory.value = category
    }

    fun browseCategory(category: ToolCategory) {
        _activeToolId.value = null
        _selectedCategory.value = category
        _activeTab.value = ShellTab.TOOLS
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun openTool(toolId: String, payload: String = "", uri: Uri? = null) {
        val meta = ToolRegistry.getToolById(toolId) ?: return
        _incomingPayload.value = payload
        _incomingUri.value = uri
        _activeToolId.value = meta.id
        repo.pushRecentToolId(meta.id)
        _recentToolIds.value = repo.getRecentToolIds()
    }

    fun closeTool() {
        _activeToolId.value = null
        _incomingPayload.value = ""
        _incomingUri.value = null
    }

    fun togglePinTool(toolId: String) {
        val current = _pinnedToolIds.value
        val updated = if (toolId in current) current - toolId else current + toolId
        repo.setPinnedToolIds(updated)
        _pinnedToolIds.value = updated
        showToast(if (toolId in updated) "Pinned to Home." else "Unpinned from Home.")
    }

    fun updateThemeMode(mode: ThemeMode) {
        repo.setThemeMode(mode)
        val curr = _lookState.value
        val nextAccent = when (mode) {
            ThemeMode.BLUEPRINT -> ChapterHue.SKY
            ThemeMode.TERMINAL -> ChapterHue.MINT
            ThemeMode.SEPIA -> ChapterHue.CLAY
            else -> curr.accentHue
        }
        if (nextAccent != curr.accentHue) {
            repo.setAccentHue(nextAccent)
        }
        _lookState.value = curr.copy(
            themeMode = mode,
            accentHue = nextAccent,
            palette = resolvePalette(mode, nextAccent, lastSystemIsDark)
        )
    }

    fun updateAccentHue(hue: ChapterHue) {
        repo.setAccentHue(hue)
        val curr = _lookState.value
        _lookState.value = curr.copy(
            accentHue = hue,
            palette = resolvePalette(curr.themeMode, hue, lastSystemIsDark)
        )
    }

    fun updateMotionMode(mode: MotionMode) {
        repo.setMotionMode(mode)
        _lookState.value = _lookState.value.copy(motionMode = mode)
    }

    fun updateUiScale(scale: Float) {
        repo.setUiScale(scale)
        _lookState.value = _lookState.value.copy(uiScale = scale)
    }

    fun updateDensity(density: UiDensity) {
        repo.setDensity(density)
        _lookState.value = _lookState.value.copy(density = density)
    }

    fun updateDyslexiaFriendly(enabled: Boolean) {
        repo.setDyslexiaFriendly(enabled)
        _lookState.value = _lookState.value.copy(dyslexiaFriendly = enabled)
    }

    fun updateSoundEnabled(enabled: Boolean) {
        repo.setSoundEnabled(enabled)
        _lookState.value = _lookState.value.copy(soundEnabled = enabled)
    }

    fun updateHapticsEnabled(enabled: Boolean) {
        repo.setHapticsEnabled(enabled)
        _lookState.value = _lookState.value.copy(hapticsEnabled = enabled)
    }

    fun updateConfettiEnabled(enabled: Boolean) {
        repo.setConfettiEnabled(enabled)
        _lookState.value = _lookState.value.copy(confettiEnabled = enabled)
    }

    fun updateRegionFormat(region: RegionFormat) {
        repo.setRegionFormat(region)
        _lookState.value = _lookState.value.copy(regionFormat = region)
    }

    fun updateOfflineOnlyAi(offlineOnly: Boolean) {
        repo.setOfflineOnlyAi(offlineOnly)
        _lookState.value = _lookState.value.copy(offlineOnlyAi = offlineOnly)
        refreshCapabilities()
    }

    fun updateCompanionConfig(url: String, token: String) {
        repo.setCompanionUrl(url)
        repo.setCompanionToken(token)
        _companionUrl.value = url
        _companionToken.value = token
    }

    fun testCompanionConnection() {
        viewModelScope.launch {
            val res = CompanionService.testConnection(_companionUrl.value, _companionToken.value)
            _companionProbe.value = res
            repo.logNetworkCall(
                toolId = "settings-companion",
                host = _companionUrl.value,
                bytesOut = 64L,
                bytesIn = res.bytesTransferred,
                note = res.statusMessage
            )
            _capabilities.value = probeDeviceCapabilities(res.reachable)
            showToast(res.statusMessage)
        }
    }

    fun logNetworkAudit(toolId: String, host: String, bytesOut: Long, bytesIn: Long, note: String) {
        viewModelScope.launch {
            repo.logNetworkCall(toolId, host, bytesOut, bytesIn, note)
        }
    }

    fun updateGeminiKeyOverride(key: String) {
        repo.setGeminiKeyOverride(key)
        _geminiKeyOverride.value = key
        refreshCapabilities()
        showToast("Updated configuration.")
    }

    fun rememberCaseStyle(styleName: String) {
        repo.setLastCaseStyle(styleName)
        _lastCaseStyle.value = styleName
    }

    fun rememberHashAlgorithm(algName: String) {
        repo.setLastHashAlgorithm(algName)
        _lastHashAlgorithm.value = algName
    }

    // Tally Counter operations
    fun createCounter(title: String, step: Int, goal: Int) {
        viewModelScope.launch { repo.insertCounter(title, step, goal) }
    }

    fun updateCounter(counter: TallyCounterEntity) {
        viewModelScope.launch { repo.updateCounter(counter) }
    }

    fun deleteCounter(id: Long) {
        viewModelScope.launch { repo.deleteCounter(id) }
    }

    // Notes / Todo / Shopping operations
    fun createNote(title: String, body: String, isChecklist: Boolean, noteType: String = "NOTE", metaTag: String = "") {
        viewModelScope.launch { repo.insertNote(title, body, isChecklist, noteType, metaTag) }
    }

    fun updateNote(note: NoteEntity) {
        viewModelScope.launch { repo.updateNote(note) }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch { repo.deleteNote(id) }
    }

    // Expense Ledger operations
    fun addExpense(title: String, amount: Double, category: String, kind: String, note: String = "") {
        viewModelScope.launch { repo.insertExpense(title, amount, category, kind, note) }
    }

    fun deleteExpense(id: Long) {
        viewModelScope.launch { repo.deleteExpense(id) }
    }

    // Vault operations
    fun addVaultEntry(label: String, username: String, encryptedPayload: String, ivBase64: String, saltBase64: String) {
        viewModelScope.launch { repo.insertVaultEntry(label, username, encryptedPayload, ivBase64, saltBase64) }
    }

    fun deleteVaultEntry(id: Long) {
        viewModelScope.launch { repo.deleteVaultEntry(id) }
    }

    // Health operations
    fun addHealthLog(logType: String, primaryVal: Double, secondaryVal: Double = 0.0, label: String = "", note: String = "", completed: Boolean = false) {
        viewModelScope.launch { repo.insertHealthLog(logType, primaryVal, secondaryVal, label, note, completed) }
    }

    fun updateHealthLog(entry: HealthLogEntity) {
        viewModelScope.launch { repo.updateHealthLog(entry) }
    }

    fun deleteHealthLog(id: Long) {
        viewModelScope.launch { repo.deleteHealthLog(id) }
    }

    // Benchmark & Game Score operations
    fun recordBenchmarkScore(testId: String, scoreLabel: String, numericScore: Double, higherIsBetter: Boolean) {
        viewModelScope.launch { repo.recordBenchmarkScore(testId, scoreLabel, numericScore, higherIsBetter) }
    }

    // Download Queue operations
    suspend fun insertDownloadJobSync(job: DownloadJobEntity): Long = repo.insertDownloadJob(job)
    fun updateDownloadJob(job: DownloadJobEntity) {
        viewModelScope.launch { repo.updateDownloadJob(job) }
    }
    fun deleteDownloadJob(id: Long) {
        viewModelScope.launch { repo.deleteDownloadJob(id) }
    }

    // Files operations
    fun saveOutputToFiles(fileName: String, kind: DataKind, originToolId: String, content: String) {
        val toolName = ToolRegistry.getToolById(originToolId)?.name ?: originToolId
        viewModelScope.launch {
            repo.saveOutputToFile(fileName, kind, originToolId, toolName, content)
            repo.logToolHistory(originToolId, "Saved $fileName", content.take(160))
        }
    }

    fun deleteSavedFileWithUndo(file: SavedFileEntity) {
        viewModelScope.launch {
            _lastDeletedFile.value = file
            repo.deleteSavedFile(file.id)
            showToast("Deleted ${file.fileName}.")
        }
    }

    fun undoDeleteSavedFile() {
        val file = _lastDeletedFile.value ?: return
        viewModelScope.launch {
            repo.restoreSavedFile(file)
            _lastDeletedFile.value = null
            showToast("Restored ${file.fileName}.")
        }
    }

    fun panicWipeAllData() {
        viewModelScope.launch {
            repo.panicWipeAllData()
            _lookState.value = loadInitialLookState()
            _pinnedToolIds.value = repo.getPinnedToolIds()
            _recentToolIds.value = repo.getRecentToolIds()
            _companionProbe.value = null
            showToast("All local Omni Deck data cleared and reset.")
        }
    }

    fun refreshCapabilities() {
        _capabilities.value = probeDeviceCapabilities(_companionProbe.value?.reachable == true)
    }

    private fun probeDeviceCapabilities(companionOk: Boolean): DeviceCapabilitySnapshot {
        return CapabilitiesService.probe(
            context = getApplication(),
            companionReachable = companionOk,
            geminiConfigured = _geminiKeyOverride.value.isNotBlank(),
            offlineOnlyAi = _lookState.value.offlineOnlyAi
        )
    }

    fun showToast(msg: String) {
        viewModelScope.launch {
            _toastMessage.value = msg
            delay(2600L)
            if (_toastMessage.value == msg) {
                _toastMessage.value = null
            }
        }
    }
}
