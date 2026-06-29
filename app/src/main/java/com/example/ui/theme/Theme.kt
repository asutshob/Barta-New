package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = XBlue,
    secondary = XSubTextDark,
    tertiary = XRepostGreen,
    background = XDarkBg,
    surface = XDarkSurface,
    onBackground = Color.White,
    onSurface = Color.White,
    onPrimary = Color.White,
    outline = XDarkBorder
  )

private val LightColorScheme =
  lightColorScheme(
    primary = XBlue,
    secondary = XSubTextLight,
    tertiary = XRepostGreen,
    background = XLightBg,
    surface = XLightSurface,
    onBackground = Color(0xFF0F1419),
    onSurface = Color(0xFF0F1419),
    onPrimary = Color.White,
    outline = XLightBorder
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic color to maintain the custom Twitter/X Barta branding across all devices
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

