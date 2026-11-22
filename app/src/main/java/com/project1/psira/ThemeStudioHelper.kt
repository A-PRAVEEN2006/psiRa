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

        fun updateStyleButtons() {
            btnGlass.setBackgroundResource(if (activeStyle == "GLASS") R.drawable.bg_rounded_primary else R.drawable.bg_circle_dark)
            btnCyber.setBackgroundResource(if (activeStyle == "CYBER") R.drawable.bg_rounded_primary else R.drawable.bg_circle_dark)
            btnMatte.setBackgroundResource(if (activeStyle == "OLED") R.drawable.bg_rounded_primary else R.drawable.bg_circle_dark)

            btnGlass.setTextColor(if (activeStyle == "GLASS") Color.WHITE else Color.GRAY)
            btnCyber.setTextColor(if (activeStyle == "CYBER") Color.WHITE else Color.GRAY)
            btnMatte.setTextColor(if (activeStyle == "OLED") Color.WHITE else Color.GRAY)
        }
        updateStyleButtons()

        btnGlass.setOnClickListener {
            activeStyle = "GLASS"
            prefs.edit { putString("UI_STYLE", activeStyle) }
            updateStyleButtons()
            Toast.makeText(activity, "Style set to: Frosted Glass", Toast.LENGTH_SHORT).show()
        }
        btnCyber.setOnClickListener {
            activeStyle = "CYBER"
            prefs.edit { putString("UI_STYLE", activeStyle) }
            updateStyleButtons()
            Toast.makeText(activity, "Style set to: Tactical Cyber", Toast.LENGTH_SHORT).show()
        }
        btnMatte.setOnClickListener {
            activeStyle = "OLED"
            prefs.edit { putString("UI_STYLE", activeStyle) }
            updateStyleButtons()
            Toast.makeText(activity, "Style set to: OLED Stealth", Toast.LENGTH_SHORT).show()
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
