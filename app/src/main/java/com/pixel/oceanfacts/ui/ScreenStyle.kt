package com.pixel.oceanfacts.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Shared text style plus the cool greys the chrome screens are written in. */
internal fun ts(
    size: Float,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Color.White,
    spacingEm: Float = 0f,
    lineHeight: Float = 0f,
) = TextStyle(
    fontFamily = OceanFont, fontSize = size.sp, fontWeight = weight, color = color,
    letterSpacing = spacingEm.em,
    lineHeight = if (lineHeight > 0f) lineHeight.sp else TextStyle.Default.lineHeight,
)

// Both carry a little blue rather than being neutral grey, so secondary text sits in the same
// water as everything else instead of reading as a grey UI dropped on top of it.
internal val Mute = Color(0xFF8A9BA6)
internal val Dim = Color(0xFF6E7F8A)
