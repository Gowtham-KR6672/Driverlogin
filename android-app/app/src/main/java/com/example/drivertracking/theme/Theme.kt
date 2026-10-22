package com.example.drivertracking.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = BrandBlue,
    secondary = BrandBlue,
    tertiary = BrandBlue,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = LightBackground,
    onSurface = LightBackground,
    error = ErrorRed,
    outline = TextGray
)

private val LightColorScheme = lightColorScheme(
    primary = BrandBlue,
    secondary = BrandBlue,
    tertiary = BrandBlue,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = TextDark,
    onSurface = TextDark,
    error = ErrorRed,
    outline = TextGray
)

@Composable
fun DriverTrackingTheme(
  // The screens use fixed light colours (LightBackground, white cards), so following the
  // phone's dark mode mixed light-on-light text into them. Stay light until the screens
  // are reworked to use theme colours.
  darkTheme: Boolean = false,
  // Disable dynamic color by default to enforce our premium brand palette
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
