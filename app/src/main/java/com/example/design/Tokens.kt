package com.example.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

const val APP_NAME = "Omni Deck"

enum class ThemeMode(val label: String, val subtitle: String) {
    INK("Obsidian", "True OLED dark & electric cobalt"),
    PAPER("Minimal Light", "Crisp white cards & subtle hairlines"),
    BLUEPRINT("Nordic Navy", "Deep oceanic slate & sky"),
    TERMINAL("Emerald", "Carbon dark & phosphor mint"),
    SEPIA("Sandstone", "Soft parchment & terracotta"),
    HIGH_CONTRAST("Contrast", "Ultra-legible monochrome"),
    AUTO("System", "Follow device dark mode")
}

enum class MotionMode(val label: String) {
    FULL("120Hz"),
    CALM("Gentle"),
    OFF("Instant")
}

enum class UiDensity(val label: String, val padMultiplier: Float) {
    COZY("Comfortable", 1.0f),
    COMPACT("Compact", 0.80f)
}

enum class RegionFormat(val label: String, val code: String, val currencySymbol: String) {
    EN_IN("India (12,34,567 · ₹)", "en-IN", "₹"),
    EN_US("International (1,234,567 · $)", "en-US", "$")
}

enum class ToolCategory(val id: String, val label: String, val hue: ChapterHue, val isFlagship: Boolean = false) {
    ALL("all", "All", ChapterHue.COBALT),
    AI("ai", "AI Studio", ChapterHue.SKY, isFlagship = true),
    DOWNLOAD("download", "Downloader", ChapterHue.VERMILION, isFlagship = true),
    URL("url", "URL Safety", ChapterHue.TEAL, isFlagship = true),
    SERVER("server", "Local Server", ChapterHue.FOREST, isFlagship = true),
    IMAGE("image", "Image Studio", ChapterHue.COBALT, isFlagship = true),
    PDF("pdf", "PDF Lab", ChapterHue.MARIGOLD, isFlagship = true),
    TIME("time", "Date & Time", ChapterHue.SKY),
    CALC("calc", "Calculators", ChapterHue.COBALT),
    FINANCE("finance", "Finance", ChapterHue.LIME),
    ESSENTIALS("essentials", "Essentials", ChapterHue.TANGERINE),
    MEASURE("measure", "Measurement", ChapterHue.MINT),
    COMMON("common", "Common", ChapterHue.SAND),
    TEXT("text", "Text Tools", ChapterHue.VERMILION),
    MEDIA("media", "Media & Audio", ChapterHue.TEAL),
    DEVICE("device", "Device Info", ChapterHue.COBALT),
    GAMES("games", "Games", ChapterHue.ROSE),
    HEALTH("health", "Health", ChapterHue.MINT),
    BENCHMARK("benchmark", "Benchmark", ChapterHue.MARIGOLD),
    SOCIAL("social", "Social & Web", ChapterHue.TANGERINE),
    DEV("dev", "Developer", ChapterHue.FOREST)
}

enum class ChapterHue(val id: String, val label: String, val paperColor: Color, val inkColor: Color, val onColor: Color) {
    VERMILION("vermilion", "Coral Orange", Color(0xFFE85325), Color(0xFFFF6B4A), Color(0xFFFFFFFF)),
    TANGERINE("tangerine", "Warm Amber", Color(0xFFD97706), Color(0xFFFBBF24), Color(0xFF141413)),
    MARIGOLD("marigold", "Saffron", Color(0xFFCA8A04), Color(0xFFFACC15), Color(0xFF141413)),
    LIME("lime", "Meadow", Color(0xFF4D7C0F), Color(0xFF84CC16), Color(0xFF141413)),
    MINT("mint", "Emerald", Color(0xFF059669), Color(0xFF10B981), Color(0xFF06130E)),
    TEAL("teal", "Teal Cyan", Color(0xFF0891B2), Color(0xFF06B6D4), Color(0xFFFFFFFF)),
    SKY("sky", "Azure Sky", Color(0xFF0284C7), Color(0xFF38BDF8), Color(0xFFFFFFFF)),
    COBALT("cobalt", "Electric Blue", Color(0xFF2563EB), Color(0xFF3B82F6), Color(0xFFFFFFFF)),
    ROSE("rose", "Crimson", Color(0xFFE11D48), Color(0xFFF43F5E), Color(0xFFFFFFFF)),
    CLAY("clay", "Terracotta", Color(0xFFB45309), Color(0xFFF59E0B), Color(0xFFFFFFFF)),
    FOREST("forest", "Sage", Color(0xFF0D9488), Color(0xFF14B8A6), Color(0xFFFFFFFF)),
    SAND("sand", "Bronze", Color(0xFF92602D), Color(0xFFD6A46E), Color(0xFFFFFFFF));

    fun resolve(isDark: Boolean, isHighContrast: Boolean): Color {
        if (isHighContrast) return if (isDark) Color(0xFFFFD600) else Color(0xFF0038FF)
        return if (isDark) inkColor else paperColor
    }
}

enum class DitherPattern(val id: String, val label: String) {
    DIAG("diag", "Diagonal"),
    WAVES("waves", "Waves"),
    GRID("grid", "Matrix"),
    BRICK("brick", "Brick"),
    DOTS("dots", "Dots"),
    LINES("lines", "Lines"),
    RINGS("rings", "Rings"),
    GRAPH("graph", "Graph"),
    COINS("coins", "Ledger"),
    CHECK("check", "Checker"),
    TICKS("ticks", "Ticks"),
    WEAVE("weave", "Weave"),
    STRIPES("stripes", "Stripes"),
    FILM("film", "Film"),
    SCAN("scan", "Scan"),
    MAZE("maze", "Maze"),
    PULSE("pulse", "Pulse"),
    CHAIN("chain", "Chain"),
    BRACES("braces", "Braces"),
    SPARK("spark", "Spark"),
    NODES("nodes", "Nodes"),
    LOCK("lock", "Lock")
}

@Immutable
data class ChapterSpec(
    val number: Int,
    val code: String,
    val name: String,
    val signatureNote: String,
    val hue: ChapterHue,
    val pattern: DitherPattern,
    val glyphKey: String
)

val ALL_CHAPTERS: List<ChapterSpec> = listOf(
    ChapterSpec(1, "01", "Downloader", "Video, audio & playlists", ChapterHue.VERMILION, DitherPattern.DIAG, "ch_fetch"),
    ChapterSpec(2, "02", "Tunes", "Music & audio streams", ChapterHue.ROSE, DitherPattern.WAVES, "ch_tunes"),
    ChapterSpec(3, "03", "URL Safety", "Redirects, TLS & trackers", ChapterHue.TEAL, DitherPattern.GRID, "ch_link"),
    ChapterSpec(4, "04", "Local Server", "HTTP/HTTPS LAN hosting", ChapterHue.FOREST, DitherPattern.BRICK, "ch_serve"),
    ChapterSpec(5, "05", "Image Studio", "Filters, crop, GIF & EXIF", ChapterHue.COBALT, DitherPattern.DOTS, "ch_image"),
    ChapterSpec(6, "06", "PDF Lab", "Merge, split, sign & redact", ChapterHue.MARIGOLD, DitherPattern.LINES, "ch_pdf"),
    ChapterSpec(7, "07", "Date & Time", "Clocks, timers & tallies", ChapterHue.SKY, DitherPattern.RINGS, "ch_time"),
    ChapterSpec(8, "08", "Calculators", "Scientific, BMI & charts", ChapterHue.COBALT, DitherPattern.GRAPH, "ch_calc"),
    ChapterSpec(9, "09", "Finance", "Invoices, EMI, SIP & ledger", ChapterHue.LIME, DitherPattern.COINS, "ch_money"),
    ChapterSpec(10, "10", "Essentials", "Torch, QR, notes & lists", ChapterHue.TANGERINE, DitherPattern.CHECK, "ch_essentials"),
    ChapterSpec(11, "11", "Measurement", "Ruler, level, mic & speed", ChapterHue.MINT, DitherPattern.TICKS, "ch_measure"),
    ChapterSpec(12, "12", "Common", "Compass, mirror & units", ChapterHue.SAND, DitherPattern.WEAVE, "ch_common"),
    ChapterSpec(13, "13", "Text Tools", "AES, cipher, stego & vault", ChapterHue.VERMILION, DitherPattern.STRIPES, "ch_words"),
    ChapterSpec(14, "14", "Media & Audio", "Tuner, metronome & video", ChapterHue.TEAL, DitherPattern.FILM, "ch_studio"),
    ChapterSpec(15, "15", "Device Info", "CPU, GPU, storage & sensors", ChapterHue.COBALT, DitherPattern.SCAN, "ch_device"),
    ChapterSpec(16, "16", "Games", "Chess, Connect 4, Wordle & more", ChapterHue.ROSE, DitherPattern.MAZE, "ch_play"),
    ChapterSpec(17, "17", "Health", "Fasting, HIIT, caffeine & HR", ChapterHue.MINT, DitherPattern.PULSE, "ch_body"),
    ChapterSpec(18, "18", "Social & Web", "UTM, vCard, cURL & links", ChapterHue.TANGERINE, DitherPattern.CHAIN, "ch_social"),
    ChapterSpec(19, "19", "Developer", "Hashes, JSON, ECDSA & regex", ChapterHue.FOREST, DitherPattern.BRACES, "ch_dev"),
    ChapterSpec(20, "20", "Benchmark", "Reaction, Stroop, CPS & memory", ChapterHue.MARIGOLD, DitherPattern.SPARK, "ch_mind"),
    ChapterSpec(21, "21", "AI Studio", "Prompt & writing helpers", ChapterHue.SKY, DitherPattern.NODES, "ch_ai"),
    ChapterSpec(22, "22", "Vault & Files", "Saved files & encryption", ChapterHue.CLAY, DitherPattern.LOCK, "ch_vault")
)

fun chapterById(number: Int): ChapterSpec =
    ALL_CHAPTERS.firstOrNull { it.number == number } ?: ALL_CHAPTERS.first()

@Immutable
data class DotkitPalette(
    val isDark: Boolean,
    val isHighContrast: Boolean,
    val paper: Color,
    val paper2: Color,
    val paper3: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val focus: Color,
    val ok: Color,
    val warn: Color,
    val err: Color,
    val info: Color,
    val accent: ChapterHue
) {
    val accentColor: Color get() = accent.resolve(isDark, isHighContrast)
    val hairline: Color
        get() = when {
            isHighContrast -> ink.copy(alpha = 0.72f)
            isDark -> Color(0xFF23252E)
            else -> Color(0xFFE4E6EC)
        }
    fun chapterColor(hue: ChapterHue): Color = hue.resolve(isDark, isHighContrast)
}

val PaperPalette = DotkitPalette(
    isDark = false,
    isHighContrast = false,
    paper = Color(0xFFF7F8FA),
    paper2 = Color(0xFFFFFFFF),
    paper3 = Color(0xFFEEF0F4),
    ink = Color(0xFF09090B),
    ink2 = Color(0xFF52525B),
    ink3 = Color(0xFF8B8E99),
    focus = Color(0xFF2563EB),
    ok = Color(0xFF059669),
    warn = Color(0xFFD97706),
    err = Color(0xFFE11D48),
    info = Color(0xFF0891B2),
    accent = ChapterHue.COBALT
)

val InkPalette = DotkitPalette(
    isDark = true,
    isHighContrast = false,
    paper = Color(0xFF070709),
    paper2 = Color(0xFF111216),
    paper3 = Color(0xFF1A1C23),
    ink = Color(0xFFFAFAFA),
    ink2 = Color(0xFFA1A1AA),
    ink3 = Color(0xFF6E717C),
    focus = Color(0xFF3B82F6),
    ok = Color(0xFF10B981),
    warn = Color(0xFFFBBF24),
    err = Color(0xFFF43F5E),
    info = Color(0xFF06B6D4),
    accent = ChapterHue.COBALT
)

val BlueprintPalette = DotkitPalette(
    isDark = true,
    isHighContrast = false,
    paper = Color(0xFF0B1420),
    paper2 = Color(0xFF121F30),
    paper3 = Color(0xFF1A2B42),
    ink = Color(0xFFEDF5FF),
    ink2 = Color(0xFF9BB6D6),
    ink3 = Color(0xFF6482A8),
    focus = Color(0xFF38BDF8),
    ok = Color(0xFF2DD4BF),
    warn = Color(0xFFFBBF24),
    err = Color(0xFFFB7185),
    info = Color(0xFF38BDF8),
    accent = ChapterHue.SKY
)

val TerminalPalette = DotkitPalette(
    isDark = true,
    isHighContrast = false,
    paper = Color(0xFF0A120E),
    paper2 = Color(0xFF111D17),
    paper3 = Color(0xFF192A22),
    ink = Color(0xFFE6F7EE),
    ink2 = Color(0xFF93C5AA),
    ink3 = Color(0xFF5A8A70),
    focus = Color(0xFF34D399),
    ok = Color(0xFF34D399),
    warn = Color(0xFFFBBF24),
    err = Color(0xFFFB7185),
    info = Color(0xFF22D3EE),
    accent = ChapterHue.MINT
)

val SepiaPalette = DotkitPalette(
    isDark = false,
    isHighContrast = false,
    paper = Color(0xFFF5EFE4),
    paper2 = Color(0xFFFDFAF4),
    paper3 = Color(0xFFE9DEC9),
    ink = Color(0xFF2C221B),
    ink2 = Color(0xFF695546),
    ink3 = Color(0xFF9A8370),
    focus = Color(0xFFB45309),
    ok = Color(0xFF15803D),
    warn = Color(0xFFD97706),
    err = Color(0xFFDC2626),
    info = Color(0xFF0891B2),
    accent = ChapterHue.CLAY
)

val HighContrastPalette = DotkitPalette(
    isDark = false,
    isHighContrast = true,
    paper = Color(0xFFFFFFFF),
    paper2 = Color(0xFFF7F7F7),
    paper3 = Color(0xFFE5E5E5),
    ink = Color(0xFF000000),
    ink2 = Color(0xFF222222),
    ink3 = Color(0xFF555555),
    focus = Color(0xFF0038FF),
    ok = Color(0xFF006629),
    warn = Color(0xFFAD6200),
    err = Color(0xFFB80000),
    info = Color(0xFF0038FF),
    accent = ChapterHue.COBALT
)

@Immutable
data class DotkitLookState(
    val themeMode: ThemeMode = ThemeMode.INK,
    val palette: DotkitPalette = InkPalette,
    val accentHue: ChapterHue = ChapterHue.COBALT,
    val motionMode: MotionMode = MotionMode.FULL,
    val uiScale: Float = 1.0f,
    val density: UiDensity = UiDensity.COZY,
    val dyslexiaFriendly: Boolean = false,
    val soundEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val confettiEnabled: Boolean = true,
    val regionFormat: RegionFormat = RegionFormat.EN_IN,
    val offlineOnlyAi: Boolean = false
)

val LocalDotkitLook = staticCompositionLocalOf { DotkitLookState() }
