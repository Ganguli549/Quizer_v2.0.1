package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40, secondary = PurpleGrey40, tertiary = Pink40
)

private val OceanDarkColorScheme = darkColorScheme(
    primary = OceanPrimaryDark, onPrimary = OceanOnPrimaryDark,
    secondary = OceanSecondaryDark, onSecondary = OceanOnSecondaryDark,
    tertiary = OceanTertiaryDark,
    background = OceanBackgroundDark, surface = OceanSurfaceDark, onSurface = OceanOnSurfaceDark,
    surfaceVariant = OceanSurfaceVariantDark, onSurfaceVariant = OceanOnSurfaceVariantDark,
    primaryContainer = OceanPrimaryContainerDark, onPrimaryContainer = OceanOnPrimaryContainerDark,
    secondaryContainer = OceanSecondaryContainerDark, onSecondaryContainer = OceanOnSecondaryContainerDark
)
private val OceanLightColorScheme = lightColorScheme(
    primary = OceanPrimaryLight, onPrimary = OceanOnPrimaryLight,
    secondary = OceanSecondaryLight, onSecondary = OceanOnSecondaryLight,
    tertiary = OceanTertiaryLight,
    background = OceanBackgroundLight, surface = OceanSurfaceLight, onSurface = OceanOnSurfaceLight,
    surfaceVariant = OceanSurfaceVariantLight, onSurfaceVariant = OceanOnSurfaceVariantLight,
    primaryContainer = OceanPrimaryContainerLight, onPrimaryContainer = OceanOnPrimaryContainerLight,
    secondaryContainer = OceanSecondaryContainerLight, onSecondaryContainer = OceanOnSecondaryContainerLight
)

private val ForestDarkColorScheme = darkColorScheme(
    primary = ForestPrimaryDark, onPrimary = ForestOnPrimaryDark,
    secondary = ForestSecondaryDark, onSecondary = ForestOnSecondaryDark,
    tertiary = ForestTertiaryDark,
    background = ForestBackgroundDark, surface = ForestSurfaceDark, onSurface = ForestOnSurfaceDark,
    surfaceVariant = ForestSurfaceVariantDark, onSurfaceVariant = ForestOnSurfaceVariantDark,
    primaryContainer = ForestPrimaryContainerDark, onPrimaryContainer = ForestOnPrimaryContainerDark,
    secondaryContainer = ForestSecondaryContainerDark, onSecondaryContainer = ForestOnSecondaryContainerDark
)
private val ForestLightColorScheme = lightColorScheme(
    primary = ForestPrimaryLight, onPrimary = ForestOnPrimaryLight,
    secondary = ForestSecondaryLight, onSecondary = ForestOnSecondaryLight,
    tertiary = ForestTertiaryLight,
    background = ForestBackgroundLight, surface = ForestSurfaceLight, onSurface = ForestOnSurfaceLight,
    surfaceVariant = ForestSurfaceVariantLight, onSurfaceVariant = ForestOnSurfaceVariantLight,
    primaryContainer = ForestPrimaryContainerLight, onPrimaryContainer = ForestOnPrimaryContainerLight,
    secondaryContainer = ForestSecondaryContainerLight, onSecondaryContainer = ForestOnSecondaryContainerLight
)

private val SunsetDarkColorScheme = darkColorScheme(
    primary = SunsetPrimaryDark, onPrimary = SunsetOnPrimaryDark,
    secondary = SunsetSecondaryDark, onSecondary = SunsetOnSecondaryDark,
    tertiary = SunsetTertiaryDark,
    background = SunsetBackgroundDark, surface = SunsetSurfaceDark, onSurface = SunsetOnSurfaceDark,
    surfaceVariant = SunsetSurfaceVariantDark, onSurfaceVariant = SunsetOnSurfaceVariantDark,
    primaryContainer = SunsetPrimaryContainerDark, onPrimaryContainer = SunsetOnPrimaryContainerDark,
    secondaryContainer = SunsetSecondaryContainerDark, onSecondaryContainer = SunsetOnSecondaryContainerDark
)
private val SunsetLightColorScheme = lightColorScheme(
    primary = SunsetPrimaryLight, onPrimary = SunsetOnPrimaryLight,
    secondary = SunsetSecondaryLight, onSecondary = SunsetOnSecondaryLight,
    tertiary = SunsetTertiaryLight,
    background = SunsetBackgroundLight, surface = SunsetSurfaceLight, onSurface = SunsetOnSurfaceLight,
    surfaceVariant = SunsetSurfaceVariantLight, onSurfaceVariant = SunsetOnSurfaceVariantLight,
    primaryContainer = SunsetPrimaryContainerLight, onPrimaryContainer = SunsetOnPrimaryContainerLight,
    secondaryContainer = SunsetSecondaryContainerLight, onSecondaryContainer = SunsetOnSecondaryContainerLight
)

private val LavenderDarkColorScheme = darkColorScheme(
    primary = LavenderPrimaryDark, onPrimary = LavenderOnPrimaryDark,
    secondary = LavenderSecondaryDark, onSecondary = LavenderOnSecondaryDark,
    tertiary = LavenderTertiaryDark,
    background = LavenderBackgroundDark, surface = LavenderSurfaceDark, onSurface = LavenderOnSurfaceDark,
    surfaceVariant = LavenderSurfaceVariantDark, onSurfaceVariant = LavenderOnSurfaceVariantDark,
    primaryContainer = LavenderPrimaryContainerDark, onPrimaryContainer = LavenderOnPrimaryContainerDark,
    secondaryContainer = LavenderSecondaryContainerDark, onSecondaryContainer = LavenderOnSecondaryContainerDark
)
private val LavenderLightColorScheme = lightColorScheme(
    primary = LavenderPrimaryLight, onPrimary = LavenderOnPrimaryLight,
    secondary = LavenderSecondaryLight, onSecondary = LavenderOnSecondaryLight,
    tertiary = LavenderTertiaryLight,
    background = LavenderBackgroundLight, surface = LavenderSurfaceLight, onSurface = LavenderOnSurfaceLight,
    surfaceVariant = LavenderSurfaceVariantLight, onSurfaceVariant = LavenderOnSurfaceVariantLight,
    primaryContainer = LavenderPrimaryContainerLight, onPrimaryContainer = LavenderOnPrimaryContainerLight,
    secondaryContainer = LavenderSecondaryContainerLight, onSecondaryContainer = LavenderOnSecondaryContainerLight
)

private val GoldDarkColorScheme = darkColorScheme(
    primary = GoldPrimaryDark, onPrimary = GoldOnPrimaryDark,
    secondary = GoldSecondaryDark, onSecondary = GoldOnSecondaryDark,
    tertiary = GoldTertiaryDark,
    background = GoldBackgroundDark, surface = GoldSurfaceDark, onSurface = GoldOnSurfaceDark,
    surfaceVariant = GoldSurfaceVariantDark, onSurfaceVariant = GoldOnSurfaceVariantDark,
    primaryContainer = GoldPrimaryContainerDark, onPrimaryContainer = GoldOnPrimaryContainerDark,
    secondaryContainer = GoldSecondaryContainerDark, onSecondaryContainer = GoldOnSecondaryContainerDark
)
private val GoldLightColorScheme = lightColorScheme(
    primary = GoldPrimaryLight, onPrimary = GoldOnPrimaryLight,
    secondary = GoldSecondaryLight, onSecondary = GoldOnSecondaryLight,
    tertiary = GoldTertiaryLight,
    background = GoldBackgroundLight, surface = GoldSurfaceLight, onSurface = GoldOnSurfaceLight,
    surfaceVariant = GoldSurfaceVariantLight, onSurfaceVariant = GoldOnSurfaceVariantLight,
    primaryContainer = GoldPrimaryContainerLight, onPrimaryContainer = GoldOnPrimaryContainerLight,
    secondaryContainer = GoldSecondaryContainerLight, onSecondaryContainer = GoldOnSecondaryContainerLight
)

private val AmoledColorScheme = darkColorScheme(
    primary = AmoledPrimary, onPrimary = AmoledOnPrimary,
    secondary = AmoledSecondary, onSecondary = AmoledOnSecondary,
    tertiary = AmoledTertiary,
    background = AmoledBackground, surface = AmoledSurface,
    primaryContainer = AmoledPrimaryContainer, onPrimaryContainer = AmoledOnPrimaryContainer
)

val ColorfulLightColorScheme = lightColorScheme(
    primary = ColorfulPrimaryLight, onPrimary = ColorfulOnPrimaryLight,
    secondary = ColorfulSecondaryLight, onSecondary = ColorfulOnSecondaryLight,
    tertiary = ColorfulTertiaryLight,
    background = ColorfulBackgroundLight, surface = ColorfulSurfaceLight, onSurface = ColorfulOnSurfaceLight,
    surfaceVariant = ColorfulSurfaceVariantLight, onSurfaceVariant = ColorfulOnSurfaceVariantLight,
    primaryContainer = ColorfulPrimaryContainerLight, onPrimaryContainer = ColorfulOnPrimaryContainerLight,
    secondaryContainer = ColorfulSecondaryContainerLight, onSecondaryContainer = ColorfulOnSecondaryContainerLight
)

val ColorfulDarkColorScheme = darkColorScheme(
    primary = ColorfulPrimaryDark, onPrimary = ColorfulOnPrimaryDark,
    secondary = ColorfulSecondaryDark, onSecondary = ColorfulOnSecondaryDark,
    tertiary = ColorfulTertiaryDark,
    background = ColorfulBackgroundDark, surface = ColorfulSurfaceDark, onSurface = ColorfulOnSurfaceDark,
    surfaceVariant = ColorfulSurfaceVariantDark, onSurfaceVariant = ColorfulOnSurfaceVariantDark,
    primaryContainer = ColorfulPrimaryContainerDark, onPrimaryContainer = ColorfulOnPrimaryContainerDark,
    secondaryContainer = ColorfulSecondaryContainerDark, onSecondaryContainer = ColorfulOnSecondaryContainerDark
)
@Composable
fun getAppColorScheme(darkTheme: Boolean, dynamicColor: Boolean, appTheme: String): androidx.compose.material3.ColorScheme {
    return when (appTheme) {
        "ocean" -> if (darkTheme) OceanDarkColorScheme else OceanLightColorScheme
        "forest" -> if (darkTheme) ForestDarkColorScheme else ForestLightColorScheme
        "sunset" -> if (darkTheme) SunsetDarkColorScheme else SunsetLightColorScheme
        "lavender" -> if (darkTheme) LavenderDarkColorScheme else LavenderLightColorScheme
        "gold" -> if (darkTheme) GoldDarkColorScheme else GoldLightColorScheme
        "colorful" -> if (darkTheme) ColorfulDarkColorScheme else ColorfulLightColorScheme
        "amoled" -> AmoledColorScheme
        else -> {
            when {
                dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    val context = LocalContext.current
                    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
                darkTheme -> DarkColorScheme
                else -> LightColorScheme
            }
        }
    }
}

@Composable
fun MyApplicationTheme(
  themeMode: String = "system",
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  appTheme: String = "dynamic",
  content: @Composable () -> Unit,
) {
  val darkTheme = when (themeMode) {
      "light" -> false
      "dark" -> true
      else -> isSystemInDarkTheme()
  }

  val colorScheme = getAppColorScheme(darkTheme = darkTheme, dynamicColor = dynamicColor, appTheme = appTheme)

  val view = androidx.compose.ui.platform.LocalView.current
  if (!view.isInEditMode) {
      androidx.compose.runtime.SideEffect {
          val window = (view.context as android.app.Activity).window
          @Suppress("DEPRECATION")
          window.statusBarColor = android.graphics.Color.TRANSPARENT
          @Suppress("DEPRECATION")
          window.navigationBarColor = android.graphics.Color.TRANSPARENT
          androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme && appTheme != "amoled"
          androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme && appTheme != "amoled"
      }
  }

  val shapes = androidx.compose.material3.Shapes(
      extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
      small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
      medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
      large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
      extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
  )

  MaterialTheme(colorScheme = colorScheme, typography = Typography, shapes = shapes, content = content)
}
