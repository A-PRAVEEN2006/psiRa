package com.project1.psira

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog

object DisguiseStudioHelper {

    fun showStudio(activity: Activity, onDisguiseChanged: () -> Unit) {
        val dialog = BottomSheetDialog(activity, R.style.CustomBottomSheetDialog)
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_disguise_studio, null)
        dialog.setContentView(view)

        val rv = view.findViewById<RecyclerView>(R.id.rvDisguises)
        rv.layoutManager = LinearLayoutManager(activity)

        val sharedPref = activity.getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE)
        val currentCloak = sharedPref.getString("CLOAK_TYPE", "CLOCK") ?: "CLOCK"

        val adapter = DisguiseAdapter(activity, StealthManager.LOGICS, currentCloak) { selectedLogic ->
            AliasManager.applyCloak(activity, selectedLogic.id)
            val msg = if (selectedLogic.id == "GHOST") {
                "👻 Ghost Mode Active: All icons hidden from home screen. Dial *#*#77472#*#* or open psira://open in browser to launch."
            } else {
                "🎭 Disguise switched to: ${selectedLogic.name}. Home screen icon will update."
            }
            Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
            onDisguiseChanged()
            dialog.dismiss()
        }

        rv.adapter = adapter

        view.findViewById<View>(R.id.btnDoneDisguiseStudio).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private class DisguiseAdapter(
        private val activity: Activity,
        private val list: List<StealthLogic>,
        private var activeId: String,
        private val onSelect: (StealthLogic) -> Unit
    ) : RecyclerView.Adapter<DisguiseAdapter.ViewHolder>() {

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val card: CardView = v.findViewById(R.id.cardDisguise)
            val icon: ImageView = v.findViewById(R.id.ivDisguiseIcon)
            val name: TextView = v.findViewById(R.id.tvDisguiseName)
            val desc: TextView = v.findViewById(R.id.tvDisguiseDesc)
            val codePreview: TextView = v.findViewById(R.id.tvDisguiseCodePreview)
            val activeBadge: TextView = v.findViewById(R.id.tvActiveBadge)
            val btnConfigure: ImageButton = v.findViewById(R.id.btnConfigureDisguise)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_disguise_card, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            holder.icon.setImageResource(item.iconRes)
            holder.name.text = item.name
            holder.desc.text = item.description

            val currentCode = StealthManager.getPass(activity, item.id)
            holder.codePreview.text = "Unlock Code: $currentCode • Tap ⚙️ to configure"

            val isSelected = item.id.equals(activeId, ignoreCase = true)
            if (isSelected) {
                holder.activeBadge.visibility = View.VISIBLE
                holder.card.setCardBackgroundColor(Color.parseColor("#1B2E20")) // subtle green tint
            } else {
                holder.activeBadge.visibility = View.GONE
                holder.card.setCardBackgroundColor(Color.parseColor("#1E1E1E"))
            }

            holder.card.setOnClickListener {
                activeId = item.id
                notifyDataSetChanged()
                onSelect(item)
            }

            holder.btnConfigure.setOnClickListener {
                showConfigDialog(activity, item) {
                    notifyItemChanged(position)
                }
            }
        }

        override fun getItemCount(): Int = list.size

        private fun showConfigDialog(context: Activity, logic: StealthLogic, onSaved: () -> Unit) {
            val input = EditText(context).apply {
                hint = "Format: ${logic.hint}"
                setText(StealthManager.getPass(context, logic.id))
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
            }

            PsiRaDialogs.showDeleteSheet(
                context,
                "CONFIGURE ${logic.name.uppercase()}",
                "Enter secret passcode/equation to unlock using ${logic.name}:",
                "SAVE CODE",
                input
            ) {
                val value = input.text.toString().trim()
                if (value.isNotEmpty()) {
                    context.getSharedPreferences("PsiRaPrefs", Context.MODE_PRIVATE).edit {
                        putString("PASS_${logic.id}", value)
                        if (logic.id == "CALCULATOR") {
                            putString("VAULT_PASS", value)
                            putBoolean("CALC_PASS_SET", true)
                        }
                    }
                    Toast.makeText(context, "${logic.name} passcode saved: $value", Toast.LENGTH_SHORT).show()
                    onSaved()
                }
            }
        }
    }
}
