package com.example.app

import android.net.Uri
import androidx.compose.runtime.Composable
import com.example.registry.ToolMeta
import com.example.services.storage.BenchmarkScoreEntity
import com.example.services.storage.DownloadJobEntity
import com.example.services.storage.ExpenseEntity
import com.example.services.storage.HealthLogEntity
import com.example.services.storage.NoteEntity
import com.example.services.storage.TallyCounterEntity
import com.example.services.storage.VaultEntryEntity
import com.example.tools.benchmark.BenchmarkExtraScreen
import com.example.tools.benchmark.BenchmarkMoreScreen
import com.example.tools.benchmark.HumanBenchmarkScreens
import com.example.tools.calc.CalcAndChartsScreen
import com.example.tools.calc.EmiFinanceTool
import com.example.tools.calc.SciCalcTool
import com.example.tools.calc.UnitConverterTool
import com.example.tools.common_suite.CommonToolsExtendedScreen
import com.example.tools.daily.ColorContrastTool
import com.example.tools.daily.NotesChecklistTool
import com.example.tools.dev.DevExtendedScreen
import com.example.tools.dev.DevExtraToolsScreen
import com.example.tools.dev.DevMoreScreen
import com.example.tools.dev.hash.HashGeneratorTool
import com.example.tools.dev.json.JsonJwtTool
import com.example.tools.dev.passqr.PasswordQrTool
import com.example.tools.device.DeviceInfoScreen
import com.example.tools.downloader.MediaDownloaderTool
import com.example.tools.essentials.EssentialsExtendedScreen
import com.example.tools.finance.FinanceExtendedScreen
import com.example.tools.games.GamesArcadeMoreScreen
import com.example.tools.games.GamesArcadeScreen
import com.example.tools.games.GamesBoardScreen
import com.example.tools.games.GamesCardLogicScreen
import com.example.tools.games.GamesExtraScreen
import com.example.tools.health.HealthExtraScreen
import com.example.tools.health.HealthMoreScreen
import com.example.tools.health.HealthScreens
import com.example.tools.image.ImageStudioCoreScreen
import com.example.tools.image.ImageStudioExtrasScreen
import com.example.tools.measure.MeasureBasicScreen
import com.example.tools.measure.MeasureSensorsScreen
import com.example.tools.media.AudioVideoExtraScreen
import com.example.tools.media.AudioVideoMediaScreen
import com.example.tools.pdf.PdfLabWorkbench
import com.example.tools.server.LocalHttpServerScreen
import com.example.tools.social.SocialExtraScreen
import com.example.tools.social.SocialMoreScreen
import com.example.tools.social.SocialWebScreens
import com.example.tools.text.TextExtendedScreen
import com.example.tools.text.TextExtraToolsScreen
import com.example.tools.time.TimeExtendedScreen
import com.example.tools.time.tally.TallyCounterTool
import com.example.tools.time.watch.StopwatchTimerTool
import com.example.tools.time.world.WorldClockDateTool
import com.example.tools.urlcheck.UrlSafetyScreen
import com.example.tools.words.caseconv.CaseConverterTool
import com.example.tools.words.diff.TextDiffAnalyzerTool
import com.example.tools.words.morse.MorseEncoderTool

@Composable
fun OmniDeckToolRouter(
    meta: ToolMeta,
    vm: DotkitViewModel,
    incomingPayload: String,
    incomingUri: Uri?,
    pinnedToolIds: List<String>,
    counters: List<TallyCounterEntity>,
    notes: List<NoteEntity>,
    expenses: List<ExpenseEntity>,
    vaultEntries: List<VaultEntryEntity>,
    healthLogs: List<HealthLogEntity>,
    benchmarkScores: List<BenchmarkScoreEntity>,
    downloadJobs: List<DownloadJobEntity>,
    companionUrl: String,
    companionToken: String,
    lastCaseStyle: String,
    lastHashAlgorithm: String
) {
    val pinned = meta.id in pinnedToolIds
    val onTogglePin = { vm.togglePinTool(meta.id) }
    val onBack = { vm.closeTool() }
    val onSave = { name: String, kind: com.example.registry.DataKind, content: String ->
        vm.saveOutputToFiles(name, kind, meta.id, content)
    }
    val onSend = { targetId: String, payload: String ->
        vm.openTool(targetId, payload)
    }
    val onToast = { msg: String -> vm.showToast(msg) }
    val onNetAudit = { id: String, host: String, outB: Long, inB: Long, note: String ->
        vm.logNetworkAudit(id, host, outB, inB, note)
    }

    when (meta.id) {
        // 1. Media & Music Downloader (Seal / spotDL)
        "dl-video", "dl-music" -> MediaDownloaderTool(
            meta = meta,
            isMusicMode = meta.id == "dl-music",
            initialUrl = incomingPayload,
            companionUrl = companionUrl,
            companionToken = companionToken,
            jobs = downloadJobs,
            onInsertJobSync = { vm.insertDownloadJobSync(it) },
            onUpdateJob = { vm.updateDownloadJob(it) },
            onDeleteJob = { vm.deleteDownloadJob(it) },
            onLogNetworkAudit = onNetAudit,
            isPinned = pinned,
            onTogglePin = onTogglePin,
            onBack = onBack,
            onSaveToFiles = onSave,
            onSendToTool = onSend,
            onStatusToast = onToast
        )

        // 2. URL Safety & Unshortener
        "url-safety" -> UrlSafetyScreen(
            meta = meta,
            initialUrl = incomingPayload,
            onLogNetworkAudit = onNetAudit,
            isPinned = pinned,
            onTogglePin = onTogglePin,
            onBack = onBack,
            onSaveToFiles = onSave,
            onSendToTool = onSend,
            onStatusToast = onToast
        )

        // 3. Local HTTP / HTTPS Server (shttps)
        "srv-shttps" -> LocalHttpServerScreen(
            meta = meta,
            isPinned = pinned,
            onTogglePin = onTogglePin,
            onBack = onBack,
            onSaveToFiles = onSave,
            onSendToTool = onSend,
            onStatusToast = onToast
        )

        // 4. Image Studio Core & Extras (ImageToolbox parity)
        "img-filters", "img-crop", "img-resize", "img-compress", "img-draw", "img-bg", "img-collage" -> ImageStudioCoreScreen(
            meta = meta,
            initialUri = incomingUri,
            initialPayload = incomingPayload,
            isPinned = pinned,
            onTogglePin = onTogglePin,
            onBack = onBack,
            onSaveToFiles = onSave,
            onSendToTool = onSend,
            onStatusToast = onToast
        )
        "img-gif", "img-watermark", "img-exif", "img-ocr", "img-textcard", "img-sig", "img-colorpick" -> ImageStudioExtrasScreen(
            meta = meta,
            initialUri = incomingUri,
            initialPayload = incomingPayload,
            isPinned = pinned,
            onTogglePin = onTogglePin,
            onBack = onBack,
            onSaveToFiles = onSave,
            onSendToTool = onSend,
            onStatusToast = onToast
        )

        // 5. PDF Lab (PaperKnife parity)
        "pdf-merge", "pdf-split", "pdf-compress", "pdf-convert", "pdf-annotate", "pdf-text" -> PdfLabWorkbench(
            meta = meta,
            initialUri = incomingUri,
            isPinned = pinned,
            onTogglePin = onTogglePin,
            onBack = onBack,
            onSaveToFiles = onSave,
            onSendToTool = onSend,
            onStatusToast = onToast
        )

        // 6. Date & Time
        "time-analog", "time-calendar", "time-pomodoro", "time-age" -> TimeExtendedScreen(
            meta = meta,
            isPinned = pinned,
            onTogglePin = onTogglePin,
            onBack = onBack,
            onSaveToFiles = onSave,
            onSendToTool = onSend,
            onStatusToast = onToast
        )
        "time-world" -> WorldClockDateTool(
            meta = meta, isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )
        "time-watch" -> StopwatchTimerTool(
            meta = meta, isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )
        "time-tally" -> TallyCounterTool(
            meta = meta, counters = counters, isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onCreateCounter = { t, s, g -> vm.createCounter(t, s, g) },
            onUpdateCounter = { vm.updateCounter(it) },
            onDeleteCounter = { vm.deleteCounter(it) },
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )

        // 7. Calculators & Charts
        "calc-basic", "calc-sci" -> SciCalcTool(
            meta = meta, initialExpression = incomingPayload, isBasicMode = meta.id == "calc-basic",
            isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )
        "calc-bmi", "calc-pct", "calc-auto", "calc-elec", "calc-linechart", "calc-piechart" -> CalcAndChartsScreen(
            meta = meta, isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )

        // 8. Finance
        "fin-emi" -> EmiFinanceTool(
            meta = meta, isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )
        "fin-invoice", "fin-interest", "fin-mf", "fin-gst", "fin-fdrd", "fin-expense" -> FinanceExtendedScreen(
            meta = meta, expenses = expenses,
            onAddExpense = { t, a, c, k, n -> vm.addExpense(t, a, c, k, n) },
            onDeleteExpense = { vm.deleteExpense(it) },
            isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )

        // 9. Essentials
        "ess-flash", "ess-qrscan", "ess-dict", "ess-translate" -> EssentialsExtendedScreen(
            meta = meta, initialText = incomingPayload, onLogNetworkAudit = onNetAudit,
            isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )
        "ess-todo", "ess-notes", "ess-shop" -> NotesChecklistTool(
            meta = meta,
            initialText = incomingPayload,
            notes = notes,
            filterNoteType = when (meta.id) {
                "ess-todo" -> "TODO"
                "ess-shop" -> "SHOPPING"
                else -> "NOTE"
            },
            isPinned = pinned,
            onTogglePin = onTogglePin,
            onBack = onBack,
            onCreateNote = { t, b, c, nt, mt -> vm.createNote(t, b, c, nt, mt) },
            onUpdateNote = { vm.updateNote(it) },
            onDeleteNote = { vm.deleteNote(it) },
            onSaveToFiles = onSave,
            onSendToTool = onSend,
            onStatusToast = onToast
        )

        // 10. Measurement
        "meas-ruler", "meas-protractor", "meas-level", "meas-pendulum" -> MeasureBasicScreen(
            meta = meta, isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )
        "meas-vibro", "meas-pedometer", "meas-speedtest", "meas-sound", "meas-light" -> MeasureSensorsScreen(
            meta = meta, onLogNetworkAudit = onNetAudit,
            isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )

        // 11. Common Suite
        "com-units" -> UnitConverterTool(
            meta = meta, isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )
        "com-mirror", "com-compass", "com-currency", "com-freq", "com-metal", "com-gpscam", "com-magnifier", "com-speedo" -> CommonToolsExtendedScreen(
            meta = meta, onLogNetworkAudit = onNetAudit,
            isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )

        // 12. Text Tools
        "txt-case" -> CaseConverterTool(
            meta = meta, initialText = incomingPayload, rememberedStyleName = lastCaseStyle,
            isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onRememberStyle = { vm.rememberCaseStyle(it) },
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )
        "txt-diff" -> TextDiffAnalyzerTool(
            meta = meta, initialText = incomingPayload, isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )
        "txt-morse" -> MorseEncoderTool(
            meta = meta, initialText = incomingPayload, isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )
        "txt-aes", "txt-vault", "txt-repeat", "txt-lorem", "txt-markdown", "txt-tts" -> TextExtendedScreen(
            meta = meta, initialText = incomingPayload, vaultEntries = vaultEntries,
            onAddVaultEntry = { l, u, p, iv, s -> vm.addVaultEntry(l, u, p, iv, s) },
            onDeleteVaultEntry = { vm.deleteVaultEntry(it) },
            isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )
        "txt-cipher", "txt-stego", "txt-slug", "txt-lines", "txt-phonetic", "txt-csvjson" -> TextExtraToolsScreen(
            meta = meta, initialText = incomingPayload,
            isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )

        // 13. Media (Audio / Video)
        "med-audiorec", "med-audiotrim", "med-vid2aud", "med-vidcomp" -> AudioVideoMediaScreen(
            meta = meta, isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )
        "med-tuner", "med-metronome", "med-noise", "med-dtmf", "med-wavsynth", "med-framegrab" -> AudioVideoExtraScreen(
            meta = meta, isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )

        // 14. Device Info
        "devinfo-sys", "devinfo-bat", "devinfo-sensors", "devinfo-net", "devinfo-bt", "devinfo-screen", "devinfo-speaker" -> DeviceInfoScreen(
            meta = meta, onLogNetworkAudit = onNetAudit,
            isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )

        // 15. Games
        "game-2048", "game-ttt", "game-rps", "game-tower", "game-cricket", "game-dice", "game-brain", "game-tug" -> GamesArcadeScreen(
            meta = meta,
            onRecordScore = { id, lbl, v, hib -> vm.recordBenchmarkScore(id, lbl, v, hib) },
            isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )
        "game-wheel", "game-quiz", "game-mines", "game-sudoku", "game-memory", "game-snake", "game-hangman", "game-chess" -> GamesBoardScreen(
            meta = meta,
            onRecordScore = { id, lbl, v, hib -> vm.recordBenchmarkScore(id, lbl, v, hib) },
            isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )
        "game-c4", "game-wordle", "game-bricks", "game-lights", "game-checkers" -> GamesExtraScreen(
            meta = meta,
            onRecordScore = { id, lbl, v, hib -> vm.recordBenchmarkScore(id, lbl, v, hib) },
            isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )
        "game-solitaire", "game-blackjack", "game-fifteen", "game-hanoi", "game-reversi", "game-dots" -> GamesCardLogicScreen(
            meta = meta,
            onRecordScore = { id, lbl, v, hib -> vm.recordBenchmarkScore(id, lbl, v, hib) },
            isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )
        "game-nonogram", "game-maze", "game-flappy", "game-whack", "game-scramble", "game-party" -> GamesArcadeMoreScreen(
            meta = meta,
            onRecordScore = { id, lbl, v, hib -> vm.recordBenchmarkScore(id, lbl, v, hib) },
            isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )

        // 16. Health
        "hlth-breathe", "hlth-water", "hlth-meds", "hlth-hr", "hlth-calorie", "hlth-sleep", "hlth-eye", "hlth-bp" -> HealthScreens(
            meta = meta, healthLogs = healthLogs,
            onAddHealthLog = { t, p, s, l, n, c -> vm.addHealthLog(t, p, s, l, n, c) },
            onUpdateHealthLog = { vm.updateHealthLog(it) },
            onDeleteHealthLog = { vm.deleteHealthLog(it) },
            isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )
        "hlth-fast", "hlth-bodyfat", "hlth-hiit", "hlth-caffeine" -> HealthExtraScreen(
            meta = meta, healthLogs = healthLogs,
            onAddHealthLog = { t, p, s, l, n, c -> vm.addHealthLog(t, p, s, l, n, c) },
            onDeleteHealthLog = { vm.deleteHealthLog(it) },
            isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )
        "hlth-cycle", "hlth-workout", "hlth-posture" -> HealthMoreScreen(
            meta = meta, healthLogs = healthLogs,
            onAddHealthLog = { t, p, s, l, n, c -> vm.addHealthLog(t, p, s, l, n, c) },
            onDeleteHealthLog = { vm.deleteHealthLog(it) },
            isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )

        // 17. Human Benchmark
        "bench-reaction", "bench-sequence", "bench-aim", "bench-number", "bench-verbal", "bench-chimp", "bench-visual", "bench-typing" -> HumanBenchmarkScreens(
            meta = meta, benchmarkScores = benchmarkScores,
            onRecordScore = { id, lbl, v, hib -> vm.recordBenchmarkScore(id, lbl, v, hib) },
            isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )
        "bench-color", "bench-stroop", "bench-audio", "bench-cps" -> BenchmarkExtraScreen(
            meta = meta, benchmarkScores = benchmarkScores,
            onRecordScore = { id, lbl, v, hib -> vm.recordBenchmarkScore(id, lbl, v, hib) },
            isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )
        "bench-matrix", "bench-readwpm", "bench-gonogo" -> BenchmarkMoreScreen(
            meta = meta, benchmarkScores = benchmarkScores,
            onRecordScore = { id, lbl, v, hib -> vm.recordBenchmarkScore(id, lbl, v, hib) },
            isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )

        // 18. Social & Web
        "soc-wa", "soc-caption", "soc-ytdesc", "soc-status", "soc-wifiqr", "soc-pfp" -> SocialWebScreens(
            meta = meta, initialText = incomingPayload, isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )
        "soc-utm", "soc-og", "soc-vcard", "soc-curl", "soc-deeplink", "soc-biopage" -> SocialExtraScreen(
            meta = meta, initialText = incomingPayload, onLogNetworkAudit = onNetAudit,
            isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )
        "soc-thread", "soc-giveaway", "soc-serp" -> SocialMoreScreen(
            meta = meta, initialText = incomingPayload,
            isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )

        // 19. Developer
        "dev-hash" -> HashGeneratorTool(
            meta = meta, initialText = incomingPayload, rememberedAlgName = lastHashAlgorithm,
            isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onRememberAlgorithm = { vm.rememberHashAlgorithm(it) },
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )
        "dev-json" -> JsonJwtTool(
            meta = meta, initialText = incomingPayload, isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )
        "dev-pass" -> PasswordQrTool(
            meta = meta, initialText = incomingPayload, isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )
        "dev-color" -> ColorContrastTool(
            meta = meta, initialHex = incomingPayload, isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )
        "dev-regex", "dev-cron", "dev-dns", "dev-sandbox" -> DevExtendedScreen(
            meta = meta, initialText = incomingPayload, onLogNetworkAudit = onNetAudit,
            isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )
        "dev-hmacsig", "dev-batchid" -> DevExtraToolsScreen(
            meta = meta, initialText = incomingPayload,
            isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )
        "dev-sqlfmt", "dev-cert", "dev-httpref" -> DevMoreScreen(
            meta = meta, initialText = incomingPayload,
            isPinned = pinned, onTogglePin = onTogglePin, onBack = onBack,
            onSaveToFiles = onSave, onSendToTool = onSend, onStatusToast = onToast
        )
    }
}
