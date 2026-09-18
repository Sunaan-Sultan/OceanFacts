package com.pixel.oceanfacts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The surface language the quiz, the reward prompt and the streak cards all share.
 *
 * These live together rather than inside whichever screen needed them first, because four
 * copies of a hairline border drift apart quietly and the difference only shows up side by side.
 */

/** The abyss, one step off black, so panels and the accent glow have something to sit on. */
internal val Ink = Color(0xFF03121C)
internal val CardFill = Color.White.copy(alpha = 0.045f)
internal val Hairline = Color.White.copy(alpha = 0.08f)

internal val Right = Color(0xFF4FE0A8)
internal val Wrong = Color(0xFFFF6B5A)

/** Bioluminescent cyan: the quiz, the sparkle glyph, anything the app wants you to tap. */
internal val QuizAccent = Color(0xFF3FE0D8)

/** A warm vent orange for the streak — the one thing down here that is not blue. */
internal val StreakAccent = Color(0xFFFF9E5C)

internal val CardShape = RoundedCornerShape(22.dp)
internal val ButtonShape = RoundedCornerShape(16.dp)

/** The filled, accent-coloured call to action. One per screen. */
@Composable
internal fun PrimaryButton(
    label: String,
    accent: Color = QuizAccent,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(ButtonShape)
            .background(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.82f))))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = ts(15.5f, FontWeight.Bold, Color.Black.copy(alpha = 0.88f)))
    }
}

/** The quieter twin, for the choice the user is less likely to want. */
@Composable
internal fun SecondaryButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(ButtonShape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Hairline, ButtonShape)
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = ts(15.5f, FontWeight.SemiBold, Color.White))
    }
}

/**
 * The last seven days, oldest first and today last.
 *
 * Today's dot is ringed rather than filled while the day is still open, so an unfinished day
 * reads as an invitation rather than as a miss — the difference between "you have something to
 * do" and "you already failed" is most of the feeling this whole loop trades on.
 */
@Composable
internal fun WeekDots(
    days: List<Boolean>,
    accent: Color = StreakAccent,
    dot: Dp = 7.dp,
    gap: Dp = 6.dp,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
        days.forEachIndexed { i, active ->
            val isToday = i == days.lastIndex
            Box(
                Modifier
                    .size(dot)
                    .clip(CircleShape)
                    .background(if (active) accent else Color.White.copy(alpha = 0.12f))
                    .then(
                        if (isToday && !active) {
                            Modifier.border(1.dp, accent.copy(alpha = 0.55f), CircleShape)
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}
