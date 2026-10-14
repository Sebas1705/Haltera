package es.sebas1705.ironlog.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary               = Primary,
    onPrimary             = OnPrimary,
    primaryContainer      = PrimaryContainer,
    onPrimaryContainer    = OnPrimaryContainer,
    secondary             = Secondary,
    onSecondary           = OnSecondary,
    secondaryContainer    = SecondaryContainer,
    onSecondaryContainer  = OnSecondaryContainer,
    tertiary              = Tertiary,
    onTertiary            = OnTertiary,
    tertiaryContainer     = TertiaryContainer,
    onTertiaryContainer   = OnTertiaryContainer,
    background            = Background,
    onBackground          = OnBackground,
    surface               = Surface,
    onSurface             = OnSurface,
    surfaceVariant        = SurfaceVariant,
    onSurfaceVariant      = OnSurfaceVariant,
    error                 = Error,
    onError               = OnError,
    errorContainer        = ErrorContainer,
    onErrorContainer      = OnErrorContainer,
    outline               = Outline,
    outlineVariant        = OutlineVariant,
    scrim                 = Scrim,
)

private val DarkColors = darkColorScheme(
    primary               = Color(0xFFD0BCFF),
    onPrimary             = Color(0xFF381E72),
    primaryContainer      = Color(0xFF4F378B),
    onPrimaryContainer    = Color(0xFFEADDFF),
)

@Composable
fun AppTheme(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography  = AppTypography,
        shapes      = AppShapes,
        content     = content,
    )
}
