package com.pori.assistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object PoriColors {
    val BgTop = Color(0xFF1B1038)
    val BgBottom = Color(0xFF0B0A14)
    val Surface = Color(0xFF211E3A)
    val UserBubble = Color(0xFF6D4AFF)
    val Muted = Color(0xFF9A96B8)
    val Text = Color(0xFFEDEAFB)
}

private val Scheme = darkColorScheme(
    primary = Color(0xFFB794F6),
    onPrimary = Color(0xFF1A1033),
    secondary = Color(0xFFF687B3),
    background = PoriColors.BgBottom,
    surface = Color(0xFF16142A),
    onSurface = PoriColors.Text,
    onBackground = PoriColors.Text
)

@Composable
fun PoriTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
