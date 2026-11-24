package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val KalamoraLightColorScheme = lightColorScheme(
    primary = EspressoMahogany,
    onPrimary = ParchmentCard,
    primaryContainer = PaleGoldContainer,
    onPrimaryContainer = WarmEspresso,
    secondary = AntiqueGold,
    onSecondary = ParchmentCard,
    secondaryContainer = ParchmentContainer,
    onSecondaryContainer = CharcoalInk,
    tertiary = TerracottaAccent,
    onTertiary = ParchmentCard,
    tertiaryContainer = Color(0xFFF5DDD4),
    onTertiaryContainer = Color(0xFF451B0E),
    background = ParchmentCream,
    onBackground = CharcoalInk,
    surface = ParchmentCream,
    onSurface = CharcoalInk,
    surfaceVariant = ParchmentSurface,
    onSurfaceVariant = SepiaInk,
    surfaceContainerLowest = ParchmentCard,
    surfaceContainerLow = Color(0xFFF8F2E7),
    surfaceContainer = ParchmentSurface,
    surfaceContainerHigh = ParchmentContainer,
    outline = ParchmentOutline,
    outlineVariant = Color(0xFFE6DAC6),
    error = ErrorCrimson,
    onError = ParchmentCard,
    errorContainer = ErrorCrimsonBg,
    onErrorContainer = Color(0xFF5D1818)
)

private val KalamoraDarkColorScheme = darkColorScheme(
    primary = NightGoldPrimary,
    onPrimary = Color(0xFF281A0A),
    primaryContainer = NightEspressoContainer,
    onPrimaryContainer = Color(0xFFF6DFBA),
    secondary = SoftGold,
    onSecondary = Color(0xFF2A1C0A),
    secondaryContainer = NightInkContainer,
    onSecondaryContainer = NightParchmentText,
    tertiary = Color(0xFFE08B6F),
    onTertiary = Color(0xFF3A160A),
    background = NightInkBackground,
    onBackground = NightParchmentText,
    surface = NightInkSurface,
    onSurface = NightParchmentText,
    surfaceVariant = NightInkCard,
    onSurfaceVariant = NightMutedText,
    surfaceContainerLowest = NightInkBackground,
    surfaceContainerLow = NightInkSurface,
    surfaceContainer = NightInkCard,
    surfaceContainerHigh = NightInkContainer,
    outline = NightInkOutline,
    outlineVariant = Color(0xFF3B312A),
    error = Color(0xFFE87A7A),
    onError = Color(0xFF410E0E),
    errorContainer = Color(0xFF5C1E1E),
    onErrorContainer = Color(0xFFFFDAD6)
)

val KalamoraShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun KalamoraTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) KalamoraDarkColorScheme else KalamoraLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = KalamoraShapes,
        content = content
    )
}
