package com.project1.psira

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog

data class PsiRaThemeModel(
    val id: String,
    val name: String,
    val hexColor: String,
    val description: String
)

object ThemeStudioHelper {

    val THEMES = listOf(
        PsiRaThemeModel("MIDNIGHT", "Midnight Amethyst", "#B388FF", "Royal Cryptography & Purple Glow (Default)"),
        PsiRaThemeModel("EMERALD",  "Emerald Shadow",    "#00E676", "Matrix Cyber Green & Tactical HUD"),
        PsiRaThemeModel("RUBY",     "Ruby Protocol",     "#FF1744", "High-Threat Red Alert & Warning Glow"),
        PsiRaThemeModel("COBALT",   "Cobalt Sky",        "#2979FF", "Deep-Sea Navy & Cyber Defense Blue"),
        PsiRaThemeModel("OBSIDIAN", "Obsidian Cyan",     "#00FFFF", "High-Contrast Pure Neon Cyan"),
        PsiRaThemeModel("DESERT",   "Desert Amber",      "#FFD600", "Military Tactical Gold & Sand Grid"),
        PsiRaThemeModel("DEEPSEA",  "Deep Sea Oceanic",  "#00B8D4", "Submarine Stealth Oceanic Teal"),
        PsiRaThemeModel("NOVA",     "Nova Flare",        "#FF6D00", "Sunset Warning Orange & Hazard Grid"),
        PsiRaThemeModel("COSMOS",   "Deep Cosmos (JWST)","#00E5FF", "Deep Space Telemetry & Infrared Gold (#FFB300)"),
        PsiRaThemeModel("GHOST",    "Ghost Monolith",    "#9E9E9E", "Minimalist Matte Monochrome Gray")
    )

    fun showStudio(activity: Activity, onThemeChanged: () -> Unit) {
        val dialog = BottomSheetDialog(activity, R.style.CustomBottomSheetDialog)
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_theme_studio, null)
        dialog.setContentView(view)

        val prefs = activity.getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        var activeTheme = prefs.getString("APP_THEME", "MIDNIGHT") ?: "MIDNIGHT"
        var activeStyle = prefs.getString("UI_STYLE", "GLASS") ?: "GLASS"

        val btnGlass = view.findViewById<Button>(R.id.btnStyleGlass)
        val btnCyber = view.findViewById<Button>(R.id.btnStyleCyber)
        val btnMatte = view.findViewById<Button>(R.id.btnStyleMatte)
        val btnMercury = view.findViewById<Button>(R.id.btnStyleMercury)
        val btnCosmic = view.findViewById<Button>(R.id.btnStyleCosmic)

        fun updateStyleButtons() {
            btnGlass?.setBackgroundResource(if (activeStyle == "LIQUID_GLASS" || activeStyle == "GLASS") R.drawable.bg_rounded_primary else R.drawable.bg_circle_dark)
            btnCyber?.setBackgroundResource(if (activeStyle == "CYBER") R.drawable.bg_rounded_primary else R.drawable.bg_circle_dark)
            btnMatte?.setBackgroundResource(if (activeStyle == "OLED") R.drawable.bg_rounded_primary else R.drawable.bg_circle_dark)
            btnMercury?.setBackgroundResource(if (activeStyle == "MERCURY") R.drawable.bg_rounded_primary else R.drawable.bg_circle_dark)
            btnCosmic?.setBackgroundResource(if (activeStyle == "COSMOS" || activeStyle == "DEEP_COSMOS") R.drawable.bg_rounded_primary else R.drawable.bg_circle_dark)

            btnGlass?.setTextColor(if (activeStyle == "LIQUID_GLASS" || activeStyle == "GLASS") Color.WHITE else Color.GRAY)
            btnCyber?.setTextColor(if (activeStyle == "CYBER") Color.WHITE else Color.GRAY)
            btnMatte?.setTextColor(if (activeStyle == "OLED") Color.WHITE else Color.GRAY)
            btnMercury?.setTextColor(if (activeStyle == "MERCURY") Color.WHITE else Color.GRAY)
            btnCosmic?.setTextColor(if (activeStyle == "COSMOS" || activeStyle == "DEEP_COSMOS") Color.WHITE else Color.GRAY)
        }
        updateStyleButtons()

        fun applyStyleAndRecreate(newStyle: String, name: String) {
            activeStyle = newStyle
            prefs.edit { putString("UI_STYLE", activeStyle) }
            updateStyleButtons()
            Toast.makeText(activity, "UI Style applied: $name", Toast.LENGTH_SHORT).show()
            onThemeChanged()
            dialog.dismiss()
            activity.recreate()
        }

        btnGlass?.setOnClickListener { applyStyleAndRecreate("LIQUID_GLASS", "Liquid Glass") }
        btnCyber?.setOnClickListener { applyStyleAndRecreate("CYBER", "Tactical Cyber") }
        btnMatte?.setOnClickListener { applyStyleAndRecreate("OLED", "OLED Stealth") }
        btnMercury?.setOnClickListener { applyStyleAndRecreate("MERCURY", "Mercury Chrome") }
        btnCosmic?.setOnClickListener { applyStyleAndRecreate("COSMOS", "Deep Cosmos") }

        // Custom Color Wheel Button Setup
        val customHex = prefs.getString("CUSTOM_ACCENT_HEX", "#00E5FF") ?: "#00E5FF"
        val viewDot = view.findViewById<View>(R.id.viewCustomColorDot)
        val tvCustomSub = view.findViewById<TextView>(R.id.tvCustomColorSub)
        try {
            val dotColor = Color.parseColor(customHex)
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(dotColor)
            }
            viewDot?.background = shape
            tvCustomSub?.text = "Current: $customHex • Tap for infinite 360° wheel"
        } catch (_: Exception) {}

        view.findViewById<View>(R.id.btnOpenColorWheel)?.setOnClickListener {
            showColorWheelStudio(activity, onThemeChanged, dialog)
        }

        val rv = view.findViewById<RecyclerView>(R.id.rvThemes)
        rv.layoutManager = LinearLayoutManager(activity)

        val adapter = ThemeAdapter(THEMES, activeTheme) { selected ->
            activeTheme = selected.id
            prefs.edit {
                putString("APP_THEME", selected.id)
                putBoolean("IS_DARK_MODE", true)
            }
            Toast.makeText(activity, "Theme activated: ${selected.name}", Toast.LENGTH_SHORT).show()
            onThemeChanged()
            dialog.dismiss()
            activity.recreate()
        }
        rv.adapter = adapter

        view.findViewById<View>(R.id.btnCloseThemeStudio).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    fun showColorWheelStudio(activity: Activity, onThemeChanged: () -> Unit, parentDialog: BottomSheetDialog? = null) {
        val wheelDialog = BottomSheetDialog(activity, R.style.CustomBottomSheetDialog)
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_color_wheel, null)
        wheelDialog.setContentView(view)

        val prefs = activity.getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        val savedHex = prefs.getString("CUSTOM_ACCENT_HEX", "#00E5FF") ?: "#00E5FF"

        val colorWheelView = view.findViewById<ColorWheelView>(R.id.colorWheelView)
        val viewPreview = view.findViewById<View>(R.id.viewColorPreview)
        val tvHex = view.findViewById<TextView>(R.id.tvColorHex)
        val tvRgb = view.findViewById<TextView>(R.id.tvColorRgb)
        val seekBrightness = view.findViewById<android.widget.SeekBar>(R.id.seekBrightness)
        val etManualHex = view.findViewById<android.widget.EditText>(R.id.etManualHex)
        val btnApplyManualHex = view.findViewById<Button>(R.id.btnApplyManualHex)
        val btnApplyCustom = view.findViewById<Button>(R.id.btnApplyCustomColor)

        fun updateColorDisplay(color: Int, hex: String) {
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            tvHex?.text = hex
            tvRgb?.text = "RGB($r, $g, $b)"
            val previewShape = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            viewPreview?.background = previewShape
        }

        var currentSelectedColor = Color.parseColor("#00E5FF")
        try {
            currentSelectedColor = Color.parseColor(savedHex)
        } catch (_: Exception) {}

        colorWheelView.setColor(currentSelectedColor)
        updateColorDisplay(currentSelectedColor, savedHex)

        colorWheelView.onColorChangedListener = { color, hex ->
            currentSelectedColor = color
            updateColorDisplay(color, hex)
        }

        seekBrightness?.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    colorWheelView.currentBrightness = progress / 100f
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        // Space Science Preset Buttons
        fun setPreset(hexStr: String) {
            try {
                val c = Color.parseColor(hexStr)
                currentSelectedColor = c
                colorWheelView.setColor(c)
                updateColorDisplay(c, hexStr)
            } catch (_: Exception) {}
        }

        view.findViewById<Button>(R.id.btnPresetPulsar)?.setOnClickListener { setPreset("#00E5FF") }
        view.findViewById<Button>(R.id.btnPresetJwst)?.setOnClickListener { setPreset("#FFB300") }
        view.findViewById<Button>(R.id.btnPresetNebula)?.setOnClickListener { setPreset("#7C4DFF") }
        view.findViewById<Button>(R.id.btnPresetSupernova)?.setOnClickListener { setPreset("#FF6D00") }
        view.findViewById<Button>(R.id.btnPresetAurora)?.setOnClickListener { setPreset("#00E676") }
        view.findViewById<Button>(R.id.btnPresetMars)?.setOnClickListener { setPreset("#FF1744") }

        btnApplyManualHex?.setOnClickListener {
            var raw = etManualHex?.text?.toString()?.trim() ?: ""
            if (!raw.startsWith("#")) raw = "#$raw"
            try {
                val parsed = Color.parseColor(raw)
                currentSelectedColor = parsed
                colorWheelView.setColor(parsed)
                updateColorDisplay(parsed, raw)
            } catch (_: Exception) {
                Toast.makeText(activity, "Invalid Hex Color (e.g. #00E5FF)", Toast.LENGTH_SHORT).show()
            }
        }

        btnApplyCustom?.setOnClickListener {
            val hex = String.format("#%06X", (0xFFFFFF and currentSelectedColor))
            prefs.edit {
                putString("CUSTOM_ACCENT_HEX", hex)
                putString("APP_THEME", "CUSTOM")
                putBoolean("IS_DARK_MODE", true)
            }
            Toast.makeText(activity, "Custom accent activated: $hex", Toast.LENGTH_SHORT).show()
            onThemeChanged()
            wheelDialog.dismiss()
            parentDialog?.dismiss()
            activity.recreate()
        }

        wheelDialog.show()
    }

    private class ThemeAdapter(
        private val list: List<PsiRaThemeModel>,
        private var selectedId: String,
        private val onSelect: (PsiRaThemeModel) -> Unit
    ) : RecyclerView.Adapter<ThemeAdapter.ViewHolder>() {

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val card: CardView = v.findViewById(R.id.cardTheme)
            val dot: View = v.findViewById(R.id.viewColorDot)
            val title: TextView = v.findViewById(R.id.tvThemeTitle)
            val desc: TextView = v.findViewById(R.id.tvThemeDesc)
            val badge: TextView = v.findViewById(R.id.tvThemeSelectedBadge)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_theme_card, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            holder.title.text = item.name
            holder.desc.text = "${item.hexColor} • ${item.description}"

            val color = Color.parseColor(item.hexColor)
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            holder.dot.background = shape

            val isSelected = item.id.equals(selectedId, ignoreCase = true)
            if (isSelected) {
                holder.badge.visibility = View.VISIBLE
                holder.card.setCardBackgroundColor(Color.parseColor("#1B2A1E"))
            } else {
                holder.badge.visibility = View.GONE
                holder.card.setCardBackgroundColor(Color.parseColor("#1E1E1E"))
            }

            holder.card.setOnClickListener {
                selectedId = item.id
                notifyDataSetChanged()
                onSelect(item)
            }
        }

        override fun getItemCount(): Int = list.size
    }
}
