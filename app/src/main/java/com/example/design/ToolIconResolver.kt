package com.example.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.registry.ToolMeta

/**
 * Resolves a distinct Material Rounded icon per [ToolCategory] and per individual [toolId]
 * covering 100% of the 170 tools in ToolRegistry.
 */
object ToolIconResolver {

    fun categoryIcon(category: ToolCategory): ImageVector = when (category) {
        ToolCategory.ALL -> Icons.Rounded.GridView
        ToolCategory.AI -> Icons.Rounded.AutoAwesome
        ToolCategory.DOWNLOAD -> Icons.Rounded.DownloadForOffline
        ToolCategory.URL -> Icons.Rounded.Security
        ToolCategory.SERVER -> Icons.Rounded.Dns
        ToolCategory.IMAGE -> Icons.Rounded.PhotoLibrary
        ToolCategory.PDF -> Icons.Rounded.PictureAsPdf
        ToolCategory.TIME -> Icons.Rounded.Schedule
        ToolCategory.CALC -> Icons.Rounded.Calculate
        ToolCategory.FINANCE -> Icons.Rounded.AccountBalanceWallet
        ToolCategory.ESSENTIALS -> Icons.Rounded.AutoAwesomeMosaic
        ToolCategory.MEASURE -> Icons.Rounded.Straighten
        ToolCategory.COMMON -> Icons.Rounded.BuildCircle
        ToolCategory.TEXT -> Icons.Rounded.TextFields
        ToolCategory.MEDIA -> Icons.Rounded.GraphicEq
        ToolCategory.DEVICE -> Icons.Rounded.Smartphone
        ToolCategory.GAMES -> Icons.Rounded.SportsEsports
        ToolCategory.HEALTH -> Icons.Rounded.Favorite
        ToolCategory.BENCHMARK -> Icons.Rounded.Speed
        ToolCategory.SOCIAL -> Icons.Rounded.Public
        ToolCategory.DEV -> Icons.Rounded.Terminal
    }

    fun toolIcon(meta: ToolMeta): ImageVector = toolIconById(meta.id) ?: categoryIcon(meta.category)

    fun toolIconById(toolId: String): ImageVector? = when (toolId) {
        // Downloaders (2)
        "dl-video" -> Icons.Rounded.VideoLibrary
        "dl-music" -> Icons.Rounded.LibraryMusic

        // URL Safety (1)
        "url-safety" -> Icons.Rounded.VerifiedUser

        // Server (1)
        "srv-shttps" -> Icons.Rounded.CloudUpload

        // Image Studio (14)
        "img-filters" -> Icons.Rounded.AutoFixHigh
        "img-crop" -> Icons.Rounded.Crop
        "img-resize" -> Icons.Rounded.PhotoSizeSelectLarge
        "img-compress" -> Icons.Rounded.Compress
        "img-draw" -> Icons.Rounded.Brush
        "img-bg" -> Icons.Rounded.LayersClear
        "img-collage" -> Icons.AutoMirrored.Rounded.ViewQuilt
        "img-gif" -> Icons.Rounded.GifBox
        "img-watermark" -> Icons.AutoMirrored.Rounded.BrandingWatermark
        "img-exif" -> Icons.Rounded.Info
        "img-ocr" -> Icons.Rounded.DocumentScanner
        "img-textcard" -> Icons.Rounded.Title
        "img-sig" -> Icons.Rounded.Draw
        "img-colorpick" -> Icons.Rounded.Colorize

        // PDF Lab (6)
        "pdf-merge" -> Icons.AutoMirrored.Rounded.CallMerge
        "pdf-split" -> Icons.AutoMirrored.Rounded.CallSplit
        "pdf-compress" -> Icons.Rounded.FolderZip
        "pdf-convert" -> Icons.Rounded.AddPhotoAlternate
        "pdf-annotate" -> Icons.Rounded.EditNote
        "pdf-text" -> Icons.AutoMirrored.Rounded.Article

        // Date & Time (7)
        "time-analog" -> Icons.Rounded.AccessTime
        "time-world" -> Icons.Rounded.TravelExplore
        "time-calendar" -> Icons.Rounded.CalendarMonth
        "time-watch" -> Icons.Rounded.Timer
        "time-pomodoro" -> Icons.Rounded.SelfImprovement
        "time-tally" -> Icons.Rounded.ExposurePlus1
        "time-age" -> Icons.Rounded.Cake

        // Calculators & Charts (8)
        "calc-basic" -> Icons.Rounded.Calculate
        "calc-sci" -> Icons.Rounded.Functions
        "calc-bmi" -> Icons.Rounded.MonitorWeight
        "calc-pct" -> Icons.Rounded.Percent
        "calc-auto" -> Icons.Rounded.LocalGasStation
        "calc-elec" -> Icons.Rounded.ElectricBolt
        "calc-linechart" -> Icons.AutoMirrored.Rounded.ShowChart
        "calc-piechart" -> Icons.Rounded.PieChart

        // Finance (7)
        "fin-invoice" -> Icons.AutoMirrored.Rounded.ReceiptLong
        "fin-interest" -> Icons.Rounded.Savings
        "fin-mf" -> Icons.AutoMirrored.Rounded.TrendingUp
        "fin-emi" -> Icons.Rounded.CreditScore
        "fin-gst" -> Icons.Rounded.Receipt
        "fin-fdrd" -> Icons.Rounded.AccountBalance
        "fin-expense" -> Icons.Rounded.Payments

        // Essentials (7)
        "ess-flash" -> Icons.Rounded.FlashlightOn
        "ess-qrscan" -> Icons.Rounded.QrCodeScanner
        "ess-dict" -> Icons.AutoMirrored.Rounded.MenuBook
        "ess-translate" -> Icons.Rounded.Translate
        "ess-todo" -> Icons.Rounded.CheckCircleOutline
        "ess-notes" -> Icons.AutoMirrored.Rounded.StickyNote2
        "ess-shop" -> Icons.Rounded.ShoppingCart

        // Measurement (9)
        "meas-ruler" -> Icons.Rounded.Straighten
        "meas-protractor" -> Icons.Rounded.Architecture
        "meas-level" -> Icons.Rounded.ScreenRotation
        "meas-vibro" -> Icons.Rounded.Vibration
        "meas-pedometer" -> Icons.AutoMirrored.Rounded.DirectionsWalk
        "meas-speedtest" -> Icons.Rounded.NetworkCheck
        "meas-pendulum" -> Icons.Rounded.Timelapse
        "meas-sound" -> Icons.Rounded.Hearing
        "meas-light" -> Icons.Rounded.LightMode

        // Common (9)
        "com-mirror" -> Icons.Rounded.FaceRetouchingNatural
        "com-compass" -> Icons.Rounded.Explore
        "com-units" -> Icons.AutoMirrored.Rounded.CompareArrows
        "com-currency" -> Icons.Rounded.CurrencyExchange
        "com-freq" -> Icons.Rounded.SurroundSound
        "com-metal" -> Icons.Rounded.Radar
        "com-gpscam" -> Icons.Rounded.MyLocation
        "com-magnifier" -> Icons.Rounded.ZoomIn
        "com-speedo" -> Icons.Rounded.Speed

        // Text Tools (9 + 6 = 15)
        "txt-case" -> Icons.Rounded.FormatSize
        "txt-diff" -> Icons.Rounded.Difference
        "txt-morse" -> Icons.Rounded.RecordVoiceOver
        "txt-aes" -> Icons.Rounded.EnhancedEncryption
        "txt-vault" -> Icons.Rounded.Password
        "txt-repeat" -> Icons.Rounded.ControlPointDuplicate
        "txt-lorem" -> Icons.AutoMirrored.Rounded.ListAlt
        "txt-markdown" -> Icons.Rounded.Description
        "txt-tts" -> Icons.Rounded.Campaign
        "txt-cipher" -> Icons.Rounded.VpnKey
        "txt-stego" -> Icons.Rounded.VisibilityOff
        "txt-slug" -> Icons.Rounded.Link
        "txt-lines" -> Icons.AutoMirrored.Rounded.FormatListBulleted
        "txt-phonetic" -> Icons.Rounded.Spellcheck
        "txt-csvjson" -> Icons.Rounded.TableChart

        // Media & Audio (4 + 6 = 10)
        "med-audiorec" -> Icons.Rounded.SettingsVoice
        "med-audiotrim" -> Icons.Rounded.ContentCut
        "med-vid2aud" -> Icons.Rounded.AudioFile
        "med-vidcomp" -> Icons.Rounded.VideoSettings
        "med-tuner" -> Icons.Rounded.Tune
        "med-metronome" -> Icons.Rounded.AvTimer
        "med-noise" -> Icons.Rounded.Waves
        "med-dtmf" -> Icons.Rounded.Dialpad
        "med-wavsynth" -> Icons.Rounded.Equalizer
        "med-framegrab" -> Icons.Rounded.BurstMode

        // Device Info (7)
        "devinfo-sys" -> Icons.Rounded.Memory
        "devinfo-bat" -> Icons.Rounded.BatteryChargingFull
        "devinfo-sensors" -> Icons.Rounded.Sensors
        "devinfo-net" -> Icons.Rounded.WifiFind
        "devinfo-bt" -> Icons.AutoMirrored.Rounded.BluetoothSearching
        "devinfo-screen" -> Icons.Rounded.TouchApp
        "devinfo-speaker" -> Icons.Rounded.SpeakerPhone

        // Games (16 + 5 = 21)
        "game-2048" -> Icons.Rounded.Grid4x4
        "game-ttt" -> Icons.Rounded.Close
        "game-rps" -> Icons.Rounded.FrontHand
        "game-tower" -> Icons.Rounded.ViewAgenda
        "game-cricket" -> Icons.Rounded.SportsCricket
        "game-dice" -> Icons.Rounded.Casino
        "game-brain" -> Icons.Rounded.Psychology
        "game-tug" -> Icons.Rounded.SportsKabaddi
        "game-wheel" -> Icons.Rounded.DonutLarge
        "game-quiz" -> Icons.Rounded.Public
        "game-mines" -> Icons.Rounded.Flag
        "game-sudoku" -> Icons.Rounded.Apps
        "game-memory" -> Icons.Rounded.Extension
        "game-snake" -> Icons.Rounded.Gesture
        "game-hangman" -> Icons.Rounded.Abc
        "game-chess" -> Icons.Rounded.EmojiEvents
        "game-c4" -> Icons.Rounded.RadioButtonChecked
        "game-wordle" -> Icons.Rounded.TextFields
        "game-bricks" -> Icons.Rounded.SportsTennis
        "game-lights" -> Icons.Rounded.Lightbulb
        "game-checkers" -> Icons.Rounded.Dashboard

        // Health (8 + 4 = 12)
        "hlth-breathe" -> Icons.Rounded.Air
        "hlth-water" -> Icons.Rounded.WaterDrop
        "hlth-meds" -> Icons.Rounded.Medication
        "hlth-hr" -> Icons.Rounded.MonitorHeart
        "hlth-calorie" -> Icons.Rounded.LocalFireDepartment
        "hlth-sleep" -> Icons.Rounded.Bedtime
        "hlth-eye" -> Icons.Rounded.RemoveRedEye
        "hlth-bp" -> Icons.Rounded.Bloodtype
        "hlth-fast" -> Icons.Rounded.HourglassTop
        "hlth-bodyfat" -> Icons.Rounded.AccessibilityNew
        "hlth-hiit" -> Icons.Rounded.FitnessCenter
        "hlth-caffeine" -> Icons.Rounded.Coffee

        // Benchmark (8 + 4 = 12)
        "bench-reaction" -> Icons.Rounded.Bolt
        "bench-sequence" -> Icons.Rounded.Pattern
        "bench-aim" -> Icons.Rounded.GpsFixed
        "bench-number" -> Icons.Rounded.Pin
        "bench-verbal" -> Icons.Rounded.Quiz
        "bench-chimp" -> Icons.Rounded.FormatListNumbered
        "bench-visual" -> Icons.Rounded.GridOn
        "bench-typing" -> Icons.Rounded.Keyboard
        "bench-color" -> Icons.Rounded.Palette
        "bench-stroop" -> Icons.Rounded.Contrast
        "bench-audio" -> Icons.Rounded.GraphicEq
        "bench-cps" -> Icons.Rounded.AdsClick

        // Social & Web (6 + 6 = 12)
        "soc-wa" -> Icons.AutoMirrored.Rounded.Message
        "soc-caption" -> Icons.Rounded.FormatQuote
        "soc-ytdesc" -> Icons.Rounded.SmartDisplay
        "soc-status" -> Icons.Rounded.FolderOpen
        "soc-wifiqr" -> Icons.Rounded.QrCode2
        "soc-pfp" -> Icons.Rounded.AccountCircle
        "soc-utm" -> Icons.Rounded.TrackChanges
        "soc-og" -> Icons.Rounded.Share
        "soc-vcard" -> Icons.Rounded.ContactPage
        "soc-curl" -> Icons.Rounded.Http
        "soc-deeplink" -> Icons.AutoMirrored.Rounded.OpenInNew
        "soc-biopage" -> Icons.Rounded.Web

        // Developer (8 + 2 = 10)
        "dev-hash" -> Icons.Rounded.Fingerprint
        "dev-json" -> Icons.Rounded.DataObject
        "dev-pass" -> Icons.Rounded.Shield
        "dev-color" -> Icons.Rounded.ColorLens
        "dev-regex" -> Icons.AutoMirrored.Rounded.ManageSearch
        "dev-cron" -> Icons.Rounded.EventRepeat
        "dev-dns" -> Icons.Rounded.Dns
        "dev-sandbox" -> Icons.Rounded.Code
        "dev-hmacsig" -> Icons.Rounded.Verified
        "dev-batchid" -> Icons.Rounded.QrCode

        // Expansion Pack II: Games (+12), Health (+3), Benchmark (+3), Social (+3), Dev (+3)
        "game-solitaire" -> Icons.Rounded.Style
        "game-blackjack" -> Icons.Rounded.Casino
        "game-fifteen" -> Icons.Rounded.GridView
        "game-hanoi" -> Icons.Rounded.StackedBarChart
        "game-reversi" -> Icons.Rounded.Contrast
        "game-dots" -> Icons.Rounded.BorderAll
        "game-nonogram" -> Icons.Rounded.ViewModule
        "game-maze" -> Icons.Rounded.Route
        "game-flappy" -> Icons.Rounded.FlightTakeoff
        "game-whack" -> Icons.Rounded.TouchApp
        "game-scramble" -> Icons.Rounded.SortByAlpha
        "game-party" -> Icons.Rounded.Celebration

        "hlth-cycle" -> Icons.AutoMirrored.Rounded.EventNote
        "hlth-workout" -> Icons.Rounded.SportsGymnastics
        "hlth-posture" -> Icons.Rounded.Accessibility

        "bench-matrix" -> Icons.Rounded.AutoAwesomeMosaic
        "bench-readwpm" -> Icons.AutoMirrored.Rounded.MenuBook
        "bench-gonogo" -> Icons.Rounded.Traffic

        "soc-thread" -> Icons.Rounded.Forum
        "soc-giveaway" -> Icons.Rounded.CardGiftcard
        "soc-serp" -> Icons.Rounded.Search

        "dev-sqlfmt" -> Icons.Rounded.Storage
        "dev-cert" -> Icons.Rounded.Key
        "dev-httpref" -> Icons.Rounded.Http

        // Expansion Pack III: AI Studio + Web Studio Parity (+12)
        "ai-writer" -> Icons.Rounded.EditNote
        "ai-prompt" -> Icons.Rounded.AutoAwesome
        "ai-code" -> Icons.Rounded.IntegrationInstructions
        "dev-diffjson" -> Icons.Rounded.Compare
        "dev-svg" -> Icons.Rounded.Polyline
        "dev-gradient" -> Icons.Rounded.Gradient
        "net-subnet" -> Icons.Rounded.Hub
        "net-totp" -> Icons.Rounded.Pin
        "img-favicon" -> Icons.Rounded.AppShortcut
        "img-palette" -> Icons.Rounded.ColorLens
        "calc-ratio" -> Icons.Rounded.AspectRatio
        "fin-subs" -> Icons.Rounded.Autorenew

        else -> null
    }
}

@Composable
fun ToolBadgeIcon(
    icon: ImageVector,
    accentColor: Color,
    contentDescription: String? = null,
    sizeDp: Dp = 42.dp,
    iconSizeDp: Dp = 22.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(sizeDp)
            .clip(RoundedCornerShape(12.dp))
            .background(accentColor.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = accentColor,
            modifier = Modifier.size(iconSizeDp)
        )
    }
}

@Composable
fun ToolBadgeIcon(
    meta: ToolMeta,
    accentColor: Color = LocalDotkitLook.current.palette.chapterColor(meta.category.hue),
    sizeDp: Dp = 42.dp,
    iconSizeDp: Dp = 22.dp,
    modifier: Modifier = Modifier
) {
    ToolBadgeIcon(
        icon = ToolIconResolver.toolIcon(meta),
        accentColor = accentColor,
        contentDescription = meta.name,
        sizeDp = sizeDp,
        iconSizeDp = iconSizeDp,
        modifier = modifier
    )
}
