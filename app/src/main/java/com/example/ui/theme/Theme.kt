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

private val CosmicPurpleColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    background = Color(0xFF121314),
    surface = Color(0xFF2A2D31),
    onBackground = Color(0xFFE2E2E6),
    onSurface = Color(0xFFE2E2E6),
    secondary = Color(0xFFCCC2DC)
)

private val EmeraldOceanColorScheme = darkColorScheme(
    primary = Color(0xFF2DD4BF),
    onPrimary = Color(0xFF0F3E38),
    background = Color(0xFF0D1520),
    surface = Color(0xFF1A2332),
    onBackground = Color(0xFFE2E8F0),
    onSurface = Color(0xFFE2E8F0),
    secondary = Color(0xFF10B981)
)

private val CrimsonShadowColorScheme = darkColorScheme(
    primary = Color(0xFFF43F5E),
    onPrimary = Color(0xFF4C0519),
    background = Color(0xFF0A0506),
    surface = Color(0xFF1B0F11),
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
    secondary = Color(0xFFE11D48)
)

private val SapphireStarColorScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF1E3A8A),
    background = Color(0xFF090E1A),
    surface = Color(0xFF131D31),
    onBackground = Color(0xFFE2E8F0),
    onSurface = Color(0xFFE2E8F0),
    secondary = Color(0xFF3B82F6)
)

private val SunsetGoldColorScheme = darkColorScheme(
    primary = Color(0xFFFBBF24),
    onPrimary = Color(0xFF451A03),
    background = Color(0xFF0E0D0B),
    surface = Color(0xFF1E1A13),
    onBackground = Color(0xFFEFEAE2),
    onSurface = Color(0xFFEFEAE2),
    secondary = Color(0xFFF59E0B)
)

@Composable
fun MyApplicationTheme(
  themeSelection: String = "Cosmic Purple",
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val colorScheme = when (themeSelection) {
      "Dynamic Color" -> {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
              dynamicDarkColorScheme(context)
          } else {
              CosmicPurpleColorScheme
          }
      }
      "Emerald Ocean" -> EmeraldOceanColorScheme
      "Crimson Shadow" -> CrimsonShadowColorScheme
      "Sapphire Star" -> SapphireStarColorScheme
      "Sunset Gold" -> SunsetGoldColorScheme
      else -> CosmicPurpleColorScheme
  }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
