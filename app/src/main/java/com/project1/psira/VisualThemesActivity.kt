package com.project1.psira

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.core.content.edit
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetDialog

class VisualThemesActivity : BaseActivity() {

    private lateinit var nestedScrollView: NestedScrollView

    // Style Cards & Badges
    private lateinit var cardStyleGlass: RelativeLayout
    private lateinit var cardStyleCyber: RelativeLayout
    private lateinit var cardStyleOled: RelativeLayout
    private lateinit var cardStyleMercury: RelativeLayout
    private lateinit var cardStyleCosmos: RelativeLayout

    private lateinit var badgeStyleGlass: TextView
    private lateinit var badgeStyleCyber: TextView
    private lateinit var badgeStyleOled: TextView
    private lateinit var badgeStyleMercury: TextView
    private lateinit var badgeStyleCosmos: TextView

    // Custom Color Wheel
    private lateinit var cardCustomColorWheel: RelativeLayout
    private lateinit var viewCustomColorDot: View
    private lateinit var tvCustomColorHex: TextView
    private lateinit var btnCustomizeWheel: TextView

    // Palette Items
    private lateinit var cardThemeMidnight: RelativeLayout
    private lateinit var cardThemeEmerald: RelativeLayout
    private lateinit var cardThemeRuby: RelativeLayout
    private lateinit var cardThemeCobalt: RelativeLayout
    private lateinit var cardThemeObsidian: RelativeLayout
    private lateinit var cardThemeDesert: RelativeLayout

    private lateinit var rbThemeMidnight: RadioButton
    private lateinit var rbThemeEmerald: RadioButton
    private lateinit var rbThemeRuby: RadioButton
    private lateinit var rbThemeCobalt: RadioButton
    private lateinit var rbThemeObsidian: RadioButton
    private lateinit var rbThemeDesert: RadioButton

    // Floating Bottom Bar Tabs
    private lateinit var tabThemes: LinearLayout
    private lateinit var tabColors: LinearLayout
    private lateinit var tabEffects: LinearLayout
    private lateinit var tabTypography: LinearLayout
    private lateinit var tabBackgrounds: LinearLayout

    private var activeStyle = "LIQUID_GLASS"
    private var activeTheme = "MIDNIGHT"
    private var customHex = "#00E5FF"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ScreenSecurityHelper.apply(this)
        setContentView(R.layout.activity_visual_themes)

        loadPreferences()
        initViews()
        setupListeners()
        refreshUI()
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        activeStyle = prefs.getString("UI_STYLE", "LIQUID_GLASS") ?: "LIQUID_GLASS"
        activeTheme = prefs.getString("APP_THEME", "MIDNIGHT") ?: "MIDNIGHT"
        customHex   = prefs.getString("CUSTOM_ACCENT_HEX", "#FFB300") ?: "#FFB300"
    }

    private fun initViews() {
        nestedScrollView = findViewById(R.id.nestedScrollView)

        // Top App Bar
        findViewById<View>(R.id.btnBack).setOnClickListener {
            setResult(Activity.RESULT_OK)
            finish()
        }
        findViewById<View>(R.id.btnSettingsGear).setOnClickListener {
            finish()
        }

        // Live Preview Button
        findViewById<View>(R.id.btnLivePreview).setOnClickListener {
            showLivePreviewSheet()
        }

        // Style Cards
        cardStyleGlass   = findViewById(R.id.cardStyleGlass)
        cardStyleCyber   = findViewById(R.id.cardStyleCyber)
        cardStyleOled    = findViewById(R.id.cardStyleOled)
        cardStyleMercury = findViewById(R.id.cardStyleMercury)
        cardStyleCosmos  = findViewById(R.id.cardStyleCosmos)

        badgeStyleGlass   = findViewById(R.id.badgeStyleGlass)
        badgeStyleCyber   = findViewById(R.id.badgeStyleCyber)
        badgeStyleOled    = findViewById(R.id.badgeStyleOled)
        badgeStyleMercury = findViewById(R.id.badgeStyleMercury)
        badgeStyleCosmos  = findViewById(R.id.badgeStyleCosmos)

        // Color Wheel
        cardCustomColorWheel = findViewById(R.id.cardCustomColorWheel)
        viewCustomColorDot   = findViewById(R.id.viewCustomColorDot)
        tvCustomColorHex     = findViewById(R.id.tvCustomColorHex)
        btnCustomizeWheel    = findViewById(R.id.btnCustomizeWheel)

        // Palette Cards & Radios
        cardThemeMidnight = findViewById(R.id.cardThemeMidnight)
        cardThemeEmerald  = findViewById(R.id.cardThemeEmerald)
        cardThemeRuby     = findViewById(R.id.cardThemeRuby)
        cardThemeCobalt   = findViewById(R.id.cardThemeCobalt)
        cardThemeObsidian = findViewById(R.id.cardThemeObsidian)
        cardThemeDesert   = findViewById(R.id.cardThemeDesert)

        rbThemeMidnight = findViewById(R.id.rbThemeMidnight)
        rbThemeEmerald  = findViewById(R.id.rbThemeEmerald)
        rbThemeRuby     = findViewById(R.id.rbThemeRuby)
        rbThemeCobalt   = findViewById(R.id.rbThemeCobalt)
        rbThemeObsidian = findViewById(R.id.rbThemeObsidian)
        rbThemeDesert   = findViewById(R.id.rbThemeDesert)

        // Bottom Bar Tabs
        tabThemes      = findViewById(R.id.tabThemes)
        tabColors      = findViewById(R.id.tabColors)
        tabEffects     = findViewById(R.id.tabEffects)
        tabTypography  = findViewById(R.id.tabTypography)
        tabBackgrounds = findViewById(R.id.tabBackgrounds)
    }

    private fun setupListeners() {
        // UI Style Card Listeners
        cardStyleGlass.setOnClickListener {
            applyUiStyle("LIQUID_GLASS", "Liquid Glass")
        }
        cardStyleCyber.setOnClickListener {
            applyUiStyle("CYBER", "Tactical Cyber")
        }
        cardStyleOled.setOnClickListener {
            applyUiStyle("OLED", "OLED Stealth")
        }
        cardStyleMercury.setOnClickListener {
            applyUiStyle("MERCURY", "Mercury Chrome")
        }
        cardStyleCosmos.setOnClickListener {
            applyUiStyle("COSMOS", "Deep Cosmos (Space Science Telemetry)")
        }

        // Custom Color Wheel Listener
        val openWheelAction = View.OnClickListener {
            ThemeStudioHelper.showColorWheelStudio(this, {
                loadPreferences()
                refreshUI()
            })
        }
        cardCustomColorWheel.setOnClickListener(openWheelAction)
        btnCustomizeWheel.setOnClickListener(openWheelAction)

        // Palette Card Listeners
        cardThemeMidnight.setOnClickListener { applyAccentTheme("MIDNIGHT", "Midnight Amethyst") }
        cardThemeEmerald.setOnClickListener  { applyAccentTheme("EMERALD",  "Emerald Shadow") }
        cardThemeRuby.setOnClickListener     { applyAccentTheme("RUBY",     "Ruby Protocol") }
        cardThemeCobalt.setOnClickListener   { applyAccentTheme("COBALT",   "Cobalt Sky") }
        cardThemeObsidian.setOnClickListener { applyAccentTheme("OBSIDIAN", "Obsidian Cyan") }
        cardThemeDesert.setOnClickListener   { applyAccentTheme("DESERT",   "Desert Amber") }

        // Bottom Floating Bar Tabs
        tabThemes.setOnClickListener {
            highlightTab(tabThemes)
            nestedScrollView.smoothScrollTo(0, 0)
        }
        tabColors.setOnClickListener {
            highlightTab(tabColors)
            findViewById<View>(R.id.headerAccentPalette)?.let {
                nestedScrollView.smoothScrollTo(0, it.top)
            }
        }
        tabEffects.setOnClickListener {
            highlightTab(tabEffects)
            showEffectsDialog()
        }
        tabTypography.setOnClickListener {
            highlightTab(tabTypography)
            showTypographyDialog()
        }
        tabBackgrounds.setOnClickListener {
            highlightTab(tabBackgrounds)
            showBackgroundsDialog()
        }
    }

    private fun applyUiStyle(styleId: String, styleName: String) {
        activeStyle = styleId
        getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE).edit {
            putString("UI_STYLE", styleId)
        }
        refreshUI()
        Toast.makeText(this, "UI Design Style: $styleName applied", Toast.LENGTH_SHORT).show()
    }

    private fun applyAccentTheme(themeId: String, themeName: String) {
        activeTheme = themeId
        getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE).edit {
            putString("APP_THEME", themeId)
            putBoolean("IS_DARK_MODE", true)
        }
        refreshUI()
        Toast.makeText(this, "Accent Palette: $themeName activated", Toast.LENGTH_SHORT).show()
    }

    private fun refreshUI() {
        // 1. Update Styles
        cardStyleGlass.setBackgroundResource(if (activeStyle == "LIQUID_GLASS" || activeStyle == "GLASS") R.drawable.bg_card_visual_style_selected else R.drawable.bg_card_visual_style)
        badgeStyleGlass.visibility = if (activeStyle == "LIQUID_GLASS" || activeStyle == "GLASS") View.VISIBLE else View.GONE

        cardStyleCyber.setBackgroundResource(if (activeStyle == "CYBER" || activeStyle == "CYBER_TACTICAL") R.drawable.bg_card_visual_style_selected else R.drawable.bg_card_visual_style)
        badgeStyleCyber.visibility = if (activeStyle == "CYBER" || activeStyle == "CYBER_TACTICAL") View.VISIBLE else View.GONE

        cardStyleOled.setBackgroundResource(if (activeStyle == "OLED" || activeStyle == "OLED_STEALTH") R.drawable.bg_card_visual_style_selected else R.drawable.bg_card_visual_style)
        badgeStyleOled.visibility = if (activeStyle == "OLED" || activeStyle == "OLED_STEALTH") View.VISIBLE else View.GONE

        cardStyleMercury.setBackgroundResource(if (activeStyle == "MERCURY" || activeStyle == "MERCURY_CHROME") R.drawable.bg_card_visual_style_selected else R.drawable.bg_card_visual_style)
        badgeStyleMercury.visibility = if (activeStyle == "MERCURY" || activeStyle == "MERCURY_CHROME") View.VISIBLE else View.GONE

        cardStyleCosmos.setBackgroundResource(if (activeStyle == "COSMOS" || activeStyle == "DEEP_COSMOS") R.drawable.bg_card_cosmos else R.drawable.bg_card_visual_style)
        badgeStyleCosmos.visibility = if (activeStyle == "COSMOS" || activeStyle == "DEEP_COSMOS") View.VISIBLE else View.GONE

        // 2. Update Custom Color Dot & Hex
        tvCustomColorHex.text = "Current: $customHex"
        try {
            val color = Color.parseColor(customHex)
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            viewCustomColorDot.background = shape
        } catch (_: Exception) {}

        // 3. Update Palette Radios
        rbThemeMidnight.isChecked = activeTheme.equals("MIDNIGHT", ignoreCase = true)
        rbThemeEmerald.isChecked  = activeTheme.equals("EMERALD", ignoreCase = true)
        rbThemeRuby.isChecked     = activeTheme.equals("RUBY", ignoreCase = true)
        rbThemeCobalt.isChecked   = activeTheme.equals("COBALT", ignoreCase = true)
        rbThemeObsidian.isChecked = activeTheme.equals("OBSIDIAN", ignoreCase = true)
        rbThemeDesert.isChecked   = activeTheme.equals("DESERT", ignoreCase = true)

        cardThemeMidnight.setBackgroundResource(if (rbThemeMidnight.isChecked) R.drawable.bg_card_visual_style_selected else R.drawable.bg_card_visual_style)
        cardThemeEmerald.setBackgroundResource(if (rbThemeEmerald.isChecked) R.drawable.bg_card_visual_style_selected else R.drawable.bg_card_visual_style)
        cardThemeRuby.setBackgroundResource(if (rbThemeRuby.isChecked) R.drawable.bg_card_visual_style_selected else R.drawable.bg_card_visual_style)
        cardThemeCobalt.setBackgroundResource(if (rbThemeCobalt.isChecked) R.drawable.bg_card_visual_style_selected else R.drawable.bg_card_visual_style)
        cardThemeObsidian.setBackgroundResource(if (rbThemeObsidian.isChecked) R.drawable.bg_card_visual_style_selected else R.drawable.bg_card_visual_style)
        cardThemeDesert.setBackgroundResource(if (rbThemeDesert.isChecked) R.drawable.bg_card_visual_style_selected else R.drawable.bg_card_visual_style)
    }

    private fun highlightTab(selectedTab: LinearLayout) {
        val tabs = listOf(tabThemes, tabColors, tabEffects, tabTypography, tabBackgrounds)
        for (tab in tabs) {
            if (tab == selectedTab) {
                tab.setBackgroundResource(R.drawable.bg_tab_active_pill)
            } else {
                tab.background = null
            }
        }
    }

    private fun showLivePreviewSheet() {
        val dialog = BottomSheetDialog(this, R.style.CustomBottomSheetDialog)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_live_preview, null)
        dialog.setContentView(view)

        val tvSubtitle = view.findViewById<TextView>(R.id.tvPreviewSubtitle)
        val btnAction = view.findViewById<Button>(R.id.btnPreviewAction)
        val btnClose = view.findViewById<Button>(R.id.btnClosePreview)

        tvSubtitle?.text = "Active: $activeStyle • $activeTheme ($customHex)"

        // Color action button with active accent
        val accentHex = when (activeTheme.uppercase()) {
            "EMERALD" -> "#00E676"
            "RUBY" -> "#FF1744"
            "COBALT" -> "#2979FF"
            "OBSIDIAN" -> "#00FFFF"
            "DESERT" -> "#FFD600"
            "COSMOS" -> "#00E5FF"
            "CUSTOM" -> customHex
            else -> "#B388FF"
        }
        try {
            val parsedColor = Color.parseColor(accentHex)
            val btnShape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24f
                setColor(parsedColor)
            }
            btnAction?.background = btnShape
        } catch (_: Exception) {}

        btnClose?.setOnClickListener { dialog.dismiss() }
        btnAction?.setOnClickListener {
            Toast.makeText(this, "Interactive UI elements validated", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showEffectsDialog() {
        val prefs = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        val isHaptic = prefs.getBoolean("HAPTIC_FEEDBACK_ENABLED", true)
        val isGlow = prefs.getBoolean("CYBER_GLOW_ENABLED", true)

        val items = arrayOf(
            "Tactical Haptics: ${if (isHaptic) "ON ✓" else "OFF"}",
            "Cyber Glow Effects: ${if (isGlow) "HIGH GLOW ✓" else "SUBTLE"}"
        )

        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("🎛 Visual Effects & Haptics")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        prefs.edit { putBoolean("HAPTIC_FEEDBACK_ENABLED", !isHaptic) }
                        Toast.makeText(this, "Haptics: ${if (!isHaptic) "ENABLED" else "DISABLED"}", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        prefs.edit { putBoolean("CYBER_GLOW_ENABLED", !isGlow) }
                        Toast.makeText(this, "Cyber Glow: ${if (!isGlow) "HIGH GLOW" else "SUBTLE"}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setPositiveButton("CLOSE", null)
            .show()
    }

    private fun showTypographyDialog() {
        val prefs = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        val currentFont = prefs.getString("APP_FONT_STYLE", "SYSTEM_SANS") ?: "SYSTEM_SANS"

        val fonts = arrayOf("Modern Geometric Sans (Default)", "Hacker Terminal Monospace", "Minimal Clean")
        val values = arrayOf("SYSTEM_SANS", "MONOSPACE", "MINIMAL")

        var selectedIndex = values.indexOf(currentFont).takeIf { it >= 0 } ?: 0

        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Aa App Typography Style")
            .setSingleChoiceItems(fonts, selectedIndex) { d, which ->
                val chosen = values[which]
                prefs.edit { putString("APP_FONT_STYLE", chosen) }
                Toast.makeText(this, "Typography updated: ${fonts[which]}", Toast.LENGTH_SHORT).show()
                d.dismiss()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun showBackgroundsDialog() {
        val prefs = getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        val currentBg = prefs.getString("APP_BACKGROUND_STYLE", "COSMOS_NEBULA") ?: "COSMOS_NEBULA"

        val bgs = arrayOf("Deep Cosmos Space Nebula", "Pure OLED Deep Black", "Tactical Mesh Cyber Grid")
        val values = arrayOf("COSMOS_NEBULA", "OLED_BLACK", "TACTICAL_GRID")

        var selectedIndex = values.indexOf(currentBg).takeIf { it >= 0 } ?: 0

        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("🖼 App Background Style")
            .setSingleChoiceItems(bgs, selectedIndex) { d, which ->
                val chosen = values[which]
                prefs.edit { putString("APP_BACKGROUND_STYLE", chosen) }
                Toast.makeText(this, "Background updated: ${bgs[which]}", Toast.LENGTH_SHORT).show()
                d.dismiss()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }
}
