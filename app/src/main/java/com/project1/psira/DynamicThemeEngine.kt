package com.project1.psira

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * Central dynamic theming engine that combines:
 * - UI Design Style (Liquid Glass, Tactical Cyber, OLED Stealth, Mercury Chrome, Deep Cosmos)
 * - Accent Color Palette (Midnight, Emerald, Ruby, Cobalt, Obsidian, Desert, DeepSea, Nova, Cosmos, Ghost, or Custom 360° Hex)
 * - Typography (System Sans, Monospace Terminal, Minimal)
 * - Background Style (Cosmos Nebula, Pure OLED Black, Tactical Grid)
 * - Glow & Effects
 */
object DynamicThemeEngine {

    data class ThemePalette(
        val accentColor: Int,
        val backgroundColor: Int,
        val surfaceColor: Int,
        val strokeColor: Int,
        val onSurfaceColor: Int,
        val secondaryTextColor: Int,
        val fontTypeface: Typeface,
        val uiStyle: String,
        val themeName: String,
        val customHex: String,
        val glowEnabled: Boolean,
        val bgStyle: String
    )

    fun getThemeSignature(context: Context): String {
        val prefs = context.getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        val uiStyle = prefs.getString("UI_STYLE", "LIQUID_GLASS") ?: "LIQUID_GLASS"
        val themeName = prefs.getString("APP_THEME", "MIDNIGHT") ?: "MIDNIGHT"
        val customHex = prefs.getString("CUSTOM_ACCENT_HEX", "#FFB300") ?: "#FFB300"
        val fontStyle = prefs.getString("APP_FONT_STYLE", "SYSTEM_SANS") ?: "SYSTEM_SANS"
        val bgStyle = prefs.getString("APP_BACKGROUND_STYLE", "COSMOS_NEBULA") ?: "COSMOS_NEBULA"
        val glow = prefs.getBoolean("CYBER_GLOW_ENABLED", true)
        val isDark = prefs.getBoolean("IS_DARK_MODE", true)
        return "$uiStyle|$themeName|$customHex|$fontStyle|$bgStyle|$glow|$isDark"
    }

    fun getAccentColor(context: Context): Int {
        val prefs = context.getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        val themeName = (prefs.getString("APP_THEME", "MIDNIGHT") ?: "MIDNIGHT").uppercase()
        val customHex = prefs.getString("CUSTOM_ACCENT_HEX", "#FFB300") ?: "#FFB300"
        return when (themeName) {
            "CUSTOM"   -> parseColorSafe(customHex, "#00E5FF")
            "EMERALD"  -> Color.parseColor("#00E676")
            "RUBY"     -> Color.parseColor("#FF1744")
            "COBALT"   -> Color.parseColor("#2979FF")
            "OBSIDIAN" -> Color.parseColor("#00FFFF")
            "DESERT"   -> Color.parseColor("#FFD600")
            "DEEPSEA"  -> Color.parseColor("#00B8D4")
            "NOVA"     -> Color.parseColor("#FF6D00")
            "COSMOS", "DEEP_COSMOS" -> Color.parseColor("#00E5FF")
            "GHOST"    -> Color.parseColor("#9E9E9E")
            else       -> Color.parseColor("#B388FF") // MIDNIGHT default
        }
    }

    fun getPalette(context: Context): ThemePalette {
        val prefs = context.getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        val uiStyle = (prefs.getString("UI_STYLE", "LIQUID_GLASS") ?: "LIQUID_GLASS").uppercase()
        val themeName = (prefs.getString("APP_THEME", "MIDNIGHT") ?: "MIDNIGHT").uppercase()
        val customHex = prefs.getString("CUSTOM_ACCENT_HEX", "#FFB300") ?: "#FFB300"
        val fontStyle = (prefs.getString("APP_FONT_STYLE", "SYSTEM_SANS") ?: "SYSTEM_SANS").uppercase()
        val bgStyle = (prefs.getString("APP_BACKGROUND_STYLE", "COSMOS_NEBULA") ?: "COSMOS_NEBULA").uppercase()
        val glowEnabled = prefs.getBoolean("CYBER_GLOW_ENABLED", true)
        val isDark = prefs.getBoolean("IS_DARK_MODE", true)

        val accent = getAccentColor(context)

        val (styleBg, styleSurface) = if (!isDark) {
            Color.parseColor("#F5F7FA") to Color.parseColor("#FFFFFF")
        } else {
            when (uiStyle) {
                "OLED", "OLED_STEALTH" -> Color.parseColor("#000000") to Color.parseColor("#0A0A0A")
                "CYBER", "CYBER_TACTICAL" -> Color.parseColor("#05080E") to Color.parseColor("#0B1420")
                "MERCURY", "MERCURY_CHROME" -> Color.parseColor("#0D1219") to Color.parseColor("#18202C")
                "COSMOS", "DEEP_COSMOS" -> Color.parseColor("#030712") to Color.parseColor("#0A1128")
                else -> Color.parseColor("#0B111E") to Color.parseColor("#162033") // LIQUID_GLASS
            }
        }

        val finalBg = if (!isDark) {
            styleBg
        } else if (uiStyle == "OLED" || uiStyle == "OLED_STEALTH" || bgStyle == "OLED_BLACK") {
            if (uiStyle == "OLED" || uiStyle == "OLED_STEALTH") Color.parseColor("#000000") else styleBg
        } else {
            styleBg
        }

        val strokeAlpha = if (glowEnabled) 150 else 70
        val strokeColor = Color.argb(strokeAlpha, Color.red(accent), Color.green(accent), Color.blue(accent))

        val typeface = when (fontStyle) {
            "MONOSPACE" -> Typeface.MONOSPACE
            "MINIMAL"   -> Typeface.SANS_SERIF
            else        -> Typeface.DEFAULT
        }

        val onSurface = if (isDark) Color.WHITE else Color.parseColor("#111827")
        val secondary = if (isDark) Color.parseColor("#8899A6") else Color.parseColor("#4B5563")

        return ThemePalette(
            accentColor = accent,
            backgroundColor = finalBg,
            surfaceColor = styleSurface,
            strokeColor = strokeColor,
            onSurfaceColor = onSurface,
            secondaryTextColor = secondary,
            fontTypeface = typeface,
            uiStyle = uiStyle,
            themeName = themeName,
            customHex = customHex,
            glowEnabled = glowEnabled,
            bgStyle = bgStyle
        )
    }

    fun getThemeResId(context: Context): Int {
        val prefs = context.getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        val themeName = (prefs.getString("APP_THEME", "MIDNIGHT") ?: "MIDNIGHT").uppercase()
        val uiStyle = (prefs.getString("UI_STYLE", "LIQUID_GLASS") ?: "LIQUID_GLASS").uppercase()

        return when (themeName) {
            "EMERALD"  -> R.style.Theme_PsiRa_Emerald
            "RUBY"     -> R.style.Theme_PsiRa_Ruby
            "COBALT"   -> R.style.Theme_PsiRa_Cobalt
            "OBSIDIAN" -> R.style.Theme_PsiRa_Obsidian
            "DESERT"   -> R.style.Theme_PsiRa_Desert
            "GHOST"    -> R.style.Theme_PsiRa_Ghost
            "DEEPSEA"  -> R.style.Theme_PsiRa_DeepSea
            "NOVA"     -> R.style.Theme_PsiRa_Nova
            "COSMOS", "DEEP_COSMOS" -> R.style.Theme_PsiRa_DeepCosmos
            "CUSTOM"   -> R.style.Theme_PsiRa_Custom
            "MIDNIGHT" -> {
                if (uiStyle == "OLED" || uiStyle == "OLED_STEALTH") R.style.Theme_PsiRa_OledStealth
                else R.style.Theme_PsiRa_Midnight
            }
            else -> R.style.Theme_PsiRa_Midnight
        }
    }

    fun applyToActivity(activity: Activity) {
        // Never overwrite cloak/disguise screens
        if (activity is DisguiseActivity || activity is MainActivity || activity is SplashActivity) {
            return
        }

        val palette = getPalette(activity)

        try {
            activity.window.statusBarColor = palette.backgroundColor
            activity.window.navigationBarColor = palette.backgroundColor
        } catch (_: Exception) {}

        val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)?.getChildAt(0) ?: return

        // Apply root background (with subtle accent nebula glow if enabled and not pure OLED)
        if (activity !is VisualThemesActivity) {
            if (palette.bgStyle == "COSMOS_NEBULA" && palette.uiStyle != "OLED" && palette.uiStyle != "OLED_STEALTH") {
                val topGlow = blendColors(palette.backgroundColor, palette.accentColor, if (palette.glowEnabled) 0.16f else 0.08f)
                val bgDrawable = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(topGlow, palette.backgroundColor, palette.backgroundColor)
                )
                contentRoot.background = bgDrawable
            } else if (palette.bgStyle == "TACTICAL_GRID" && palette.uiStyle != "OLED" && palette.uiStyle != "OLED_STEALTH") {
                val edgeTint = blendColors(palette.backgroundColor, palette.accentColor, 0.10f)
                val bgDrawable = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(edgeTint, palette.backgroundColor)
                )
                contentRoot.background = bgDrawable
            } else {
                contentRoot.setBackgroundColor(palette.backgroundColor)
            }
        }

        // Recursively apply surface, accent, and typography styles
        applyToViewTree(contentRoot, palette, activity is VisualThemesActivity)

        // Apply dynamic accent to known header/title views across Chats & Dashboard
        val accentViews = intArrayOf(R.id.tvChatsHeader, R.id.nexusTitle, R.id.tvAgentId)
        for (id in accentViews) {
            activity.findViewById<TextView>(id)?.setTextColor(palette.accentColor)
        }
        activity.findViewById<View>(R.id.layoutChatsHeader)?.setBackgroundColor(palette.backgroundColor)
        activity.findViewById<View>(R.id.layoutTerminalSubHeader)?.setBackgroundColor(palette.surfaceColor)
    }

    private fun applyToViewTree(view: View, palette: ThemePalette, isVisualThemesScreen: Boolean) {
        // Apply Typography
        if (view is TextView) {
            val currentStyle = view.typeface?.style ?: Typeface.NORMAL
            view.setTypeface(palette.fontTypeface, currentStyle)
        }

        if (!isVisualThemesScreen) {
            when (view) {
                is CardView -> {
                    view.setCardBackgroundColor(palette.surfaceColor)
                }
                is BottomNavigationView -> {
                    view.setBackgroundColor(palette.surfaceColor)
                    val states = arrayOf(
                        intArrayOf(android.R.attr.state_checked),
                        intArrayOf(-android.R.attr.state_checked)
                    )
                    val colors = intArrayOf(palette.accentColor, palette.secondaryTextColor)
                    val csl = ColorStateList(states, colors)
                    view.itemIconTintList = csl
                    view.itemTextColor = csl
                }
                is FloatingActionButton -> {
                    view.backgroundTintList = ColorStateList.valueOf(palette.accentColor)
                }
            }
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyToViewTree(view.getChildAt(i), palette, isVisualThemesScreen)
            }
        }
    }

    fun blendColors(color1: Int, color2: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio
        val r = (Color.red(color1) * inverseRatio + Color.red(color2) * ratio).toInt()
        val g = (Color.green(color1) * inverseRatio + Color.green(color2) * ratio).toInt()
        val b = (Color.blue(color1) * inverseRatio + Color.blue(color2) * ratio).toInt()
        return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    private fun parseColorSafe(hex: String, fallback: String): Int {
        return try {
            Color.parseColor(hex)
        } catch (_: Exception) {
            Color.parseColor(fallback)
        }
    }
}
