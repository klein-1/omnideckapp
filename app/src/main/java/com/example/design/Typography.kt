package com.example.design

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.R

val SpaceGroteskFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.Bold)
)

val DotoFamily = FontFamily(
    Font(R.font.doto, FontWeight.Normal),
    Font(R.font.doto, FontWeight.Bold)
)

val AtkinsonFamily = FontFamily(
    Font(R.font.atkinson_hyperlegible, FontWeight.Normal),
    Font(R.font.atkinson_hyperlegible, FontWeight.Bold)
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

val NewsreaderFamily = FontFamily(
    Font(R.font.newsreader, FontWeight.Normal),
    Font(R.font.newsreader, FontWeight.Normal, FontStyle.Italic)
)

object DotkitType {
    @Composable
    fun displayPixel(sizeSp: Int = 28): TextStyle {
        val look = LocalDotkitLook.current
        val family = if (look.dyslexiaFriendly) AtkinsonFamily else SpaceGroteskFamily
        return TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = (sizeSp * look.uiScale).sp,
            lineHeight = ((sizeSp + 4) * look.uiScale).sp,
            letterSpacing = (-0.5).sp,
            fontFeatureSettings = "tnum"
        )
    }

    @Composable
    fun readoutDoto(sizeSp: Int = 36): TextStyle {
        val scale = LocalDotkitLook.current.uiScale
        return TextStyle(
            fontFamily = DotoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (sizeSp * scale).sp,
            lineHeight = ((sizeSp + 4) * scale).sp,
            letterSpacing = 0.sp,
            fontFeatureSettings = "tnum"
        )
    }

    @Composable
    fun editorialHeading(sizeSp: Int = 18): TextStyle {
        val look = LocalDotkitLook.current
        val family = if (look.dyslexiaFriendly) AtkinsonFamily else SpaceGroteskFamily
        return TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = (sizeSp * look.uiScale).sp,
            lineHeight = ((sizeSp + 5) * look.uiScale).sp,
            letterSpacing = (-0.2).sp,
            fontFeatureSettings = "tnum"
        )
    }

    @Composable
    fun bodyUi(sizeSp: Int = 14, bold: Boolean = false): TextStyle {
        val look = LocalDotkitLook.current
        val family = if (look.dyslexiaFriendly) AtkinsonFamily else SpaceGroteskFamily
        return TextStyle(
            fontFamily = family,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontSize = (sizeSp * look.uiScale).sp,
            lineHeight = ((sizeSp + 6) * look.uiScale).sp,
            fontFeatureSettings = "tnum"
        )
    }

    @Composable
    fun monoLabel(sizeSp: Int = 11): TextStyle {
        val scale = LocalDotkitLook.current.uiScale
        return TextStyle(
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (sizeSp * scale).sp,
            lineHeight = ((sizeSp + 4) * scale).sp,
            letterSpacing = 0.6.sp,
            fontFeatureSettings = "tnum"
        )
    }

    @Composable
    fun monoData(sizeSp: Int = 13): TextStyle {
        val scale = LocalDotkitLook.current.uiScale
        return TextStyle(
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (sizeSp * scale).sp,
            lineHeight = ((sizeSp + 6) * scale).sp,
            letterSpacing = 0.sp,
            fontFeatureSettings = "tnum"
        )
    }

    @Composable
    fun serifNote(sizeSp: Int = 14, italic: Boolean = false): TextStyle {
        val look = LocalDotkitLook.current
        val family = if (look.dyslexiaFriendly) AtkinsonFamily else SpaceGroteskFamily
        return TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            fontSize = (sizeSp * look.uiScale).sp,
            lineHeight = ((sizeSp + 6) * look.uiScale).sp
        )
    }
}

fun buildMaterialTypography(dyslexiaFriendly: Boolean, scale: Float): Typography {
    val uiFamily = if (dyslexiaFriendly) AtkinsonFamily else SpaceGroteskFamily
    fun s(v: Int): TextUnit = (v * scale).sp
    return Typography(
        displayLarge = TextStyle(fontFamily = uiFamily, fontWeight = FontWeight.Bold, fontSize = s(44), lineHeight = s(48), letterSpacing = (-0.8).sp, fontFeatureSettings = "tnum"),
        displayMedium = TextStyle(fontFamily = uiFamily, fontWeight = FontWeight.Bold, fontSize = s(34), lineHeight = s(38), letterSpacing = (-0.5).sp, fontFeatureSettings = "tnum"),
        displaySmall = TextStyle(fontFamily = uiFamily, fontWeight = FontWeight.Bold, fontSize = s(28), lineHeight = s(32), letterSpacing = (-0.4).sp, fontFeatureSettings = "tnum"),
        headlineLarge = TextStyle(fontFamily = uiFamily, fontWeight = FontWeight.Bold, fontSize = s(22), lineHeight = s(28), fontFeatureSettings = "tnum"),
        headlineMedium = TextStyle(fontFamily = uiFamily, fontWeight = FontWeight.Bold, fontSize = s(18), lineHeight = s(24), fontFeatureSettings = "tnum"),
        titleLarge = TextStyle(fontFamily = uiFamily, fontWeight = FontWeight.Bold, fontSize = s(17), lineHeight = s(22), fontFeatureSettings = "tnum"),
        titleMedium = TextStyle(fontFamily = uiFamily, fontWeight = FontWeight.Medium, fontSize = s(15), lineHeight = s(20), fontFeatureSettings = "tnum"),
        bodyLarge = TextStyle(fontFamily = uiFamily, fontWeight = FontWeight.Normal, fontSize = s(15), lineHeight = s(22), fontFeatureSettings = "tnum"),
        bodyMedium = TextStyle(fontFamily = uiFamily, fontWeight = FontWeight.Normal, fontSize = s(14), lineHeight = s(20), fontFeatureSettings = "tnum"),
        labelLarge = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold, fontSize = s(12), lineHeight = s(16), letterSpacing = 0.5.sp, fontFeatureSettings = "tnum"),
        labelMedium = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Normal, fontSize = s(11), lineHeight = s(16), letterSpacing = 0.5.sp, fontFeatureSettings = "tnum"),
        labelSmall = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Normal, fontSize = s(10), lineHeight = s(12), letterSpacing = 0.5.sp, fontFeatureSettings = "tnum")
    )
}
