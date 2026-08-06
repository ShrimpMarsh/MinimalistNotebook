package com.example.minimalistnotebook.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// 1. 深色模式配色 (暂时也用复古色调映射)
private val DarkColorScheme = darkColorScheme(
    primary = RetroAmber,
    secondary = SlateGray,
    tertiary = PureWhite,
    background = InkBlack,
    surface = InkBlack,
    onPrimary = InkBlack,
    onBackground = PaperBackground,
    onSurface = PaperBackground
)

// 2. 浅色模式配色 (咱们的主打手账风)
private val LightColorScheme = lightColorScheme(
    primary = RetroAmber,
    secondary = SlateGray,
    tertiary = InkBlack,
    background = PaperBackground,
    surface = PaperBackground,
    onPrimary = InkBlack,
    onBackground = InkBlack,
    onSurface = InkBlack
)

@Composable
fun MinimalistNotebookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 关闭动态取色，强制使用我们的复古配色
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // 适配手机顶部的状态栏颜色
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // 这里的 Typography 对应你目录里的 Type.kt
        content = content
    )
}