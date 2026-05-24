package com.project1.psira

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog

object PsiRaDialogs {

    fun showDeleteSheet(
        context: Context,
        title: String,
        message: String,
        confirmText: String = "CONFIRM",
        optionalView: View? = null,
        isCancelable: Boolean = true,
        onConfirm: () -> Unit
    ): BottomSheetDialog {
        val dialog = BottomSheetDialog(context, R.style.CustomBottomSheetDialog)
        dialog.setCancelable(isCancelable)
        val view = LayoutInflater.from(context).inflate(R.layout.layout_delete_bottom_sheet, null)
        
        view.findViewById<TextView>(R.id.tvSheetTitle).text = title
        view.findViewById<TextView>(R.id.tvSheetMessage).text = message

        if (optionalView != null) {
            val container = view.findViewById<android.widget.FrameLayout>(R.id.sheetInputContainer)
            container.visibility = View.VISIBLE
            container.addView(optionalView)
        }
        
        val btnConfirm = view.findViewById<android.widget.Button>(R.id.btnConfirm)
        btnConfirm.text = confirmText
        val isDangerous = confirmText.contains("WIPE", true) || 
                          confirmText.contains("DELETE", true) || 
                          confirmText.contains("DESTROY", true) || 
                          confirmText.contains("SHRED", true) || 
                          confirmText.contains("REMOVE", true)
        val bgRes = if (isDangerous) R.drawable.bg_rounded_danger else R.drawable.bg_rounded_primary
        btnConfirm.setBackgroundResource(bgRes)
        
        btnConfirm.setOnClickListener {
            onConfirm()
            dialog.dismiss()
        }
        
        val btnCancel = view.findViewById<android.widget.Button>(R.id.btnCancel)
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.setContentView(view)
        dialog.show()
        return dialog
    }

    /**
     * Shows a list of options in a premium Bottom Sheet.
     */
    fun showOptionsSheet(
        context: Context,
        title: String,
        options: List<String>,
        onOptionSelected: (Int) -> Unit
    ): BottomSheetDialog {
        val dialog = BottomSheetDialog(context, R.style.CustomBottomSheetDialog)
        val view = LayoutInflater.from(context).inflate(R.layout.layout_options_bottom_sheet, null)
        
        view.findViewById<TextView>(R.id.tvOptionsTitle).text = title
        val container = view.findViewById<LinearLayout>(R.id.optionsContainer)
        
        options.forEachIndexed { index, option ->
            val itemView = LayoutInflater.from(context).inflate(R.layout.item_sheet_option, container, false)
            val tvOption = itemView.findViewById<TextView>(R.id.tvOptionText)
            tvOption.text = option
            
            // Highlight dangerous options like "Delete" or "Destroy"
            if (option.contains("Delete", true) || option.contains("Destroy", true) || option.contains("Wipe", true) || option.contains("Shred", true)) {
                tvOption.setTextColor(android.graphics.Color.RED)
            }
            
            itemView.setOnClickListener {
                onOptionSelected(index)
                dialog.dismiss()
            }
            container.addView(itemView)
        }
        
        dialog.setContentView(view)
        dialog.show()
        return dialog
    }
}
