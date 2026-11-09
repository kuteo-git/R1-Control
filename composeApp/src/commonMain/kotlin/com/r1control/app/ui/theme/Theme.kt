package com.r1control.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The look, read off docs/design (Google Home's dark speaker page): a
 * near-black ground, large rounded cards a step lighter, and a muted
 * blue-grey for the volume pill's fill. Material roles are seeded from these;
 * screens reach for the tokens when a role does not say it.
 */
object Tokens {
    val Ground = Color(0xFF141416)
    val Card = Color(0xFF2B2B2E)
    val CardRaised = Color(0xFF353538)
    val VolumeFill = Color(0xFF3E4557)
    val Accent = Color(0xFFAEB9D6)
    val OnGround = Color(0xFFE6E6EA)
    val OnGroundMuted = Color(0xFFA3A3AB)
    val Error = Color(0xFFF2B8B5)
    val ErrorContainer = Color(0xFF3B2A2A)
    val Scrim = Color(0xFF000000)
}

/** Corner radii: one per kind of surface, so neighbours agree. */
object Radius {
    val Card = 28.dp
    val Row = 12.dp
    val Cover = 8.dp
    val Field = 16.dp
}

/** The spacing scale. Nothing on a screen is spaced by a number that is not here. */
object Space {
    val Hair = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val XLarge = 24.dp
    val Gutter = 16.dp
}

private val R1Colors = darkColorScheme(
    primary = Tokens.Accent,
    onPrimary = Tokens.Ground,
    primaryContainer = Tokens.VolumeFill,
    onPrimaryContainer = Tokens.OnGround,
    secondaryContainer = Tokens.VolumeFill,
    onSecondaryContainer = Tokens.OnGround,
    background = Tokens.Ground,
    onBackground = Tokens.OnGround,
    surface = Tokens.Ground,
    onSurface = Tokens.OnGround,
    surfaceVariant = Tokens.Card,
    onSurfaceVariant = Tokens.OnGroundMuted,
    surfaceContainer = Tokens.Card,
    surfaceContainerHigh = Tokens.CardRaised,
    surfaceContainerHighest = Tokens.CardRaised,
    surfaceContainerLow = Tokens.Ground,
    outline = Tokens.OnGroundMuted,
    outlineVariant = Color(0xFF3F3F44),
    error = Tokens.Error,
    errorContainer = Tokens.ErrorContainer,
    onErrorContainer = Tokens.Error,
)

/** Titles like Google Home's (28 to 32sp); body text is Material's scale a notch up for a phone held at arm's length. */
private val R1Typography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Normal),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Normal),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Normal),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun R1Theme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = R1Colors, typography = R1Typography, content = content)
}
