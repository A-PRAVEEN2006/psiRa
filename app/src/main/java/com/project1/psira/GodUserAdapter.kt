package com.project1.psira

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.FirebaseDatabase

class GodUserAdapter(private val userList: List<User>) : RecyclerView.Adapter<GodUserAdapter.GodUserViewHolder>() {

    class GodUserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvGodUserName)
        val tvEmail: TextView = itemView.findViewById(R.id.tvGodUserEmail)
        val tvId: TextView = itemView.findViewById(R.id.tvGodUserId)
        val btnBan: Button = itemView.findViewById(R.id.btnBan)
        val btnImpersonate: Button = itemView.findViewById(R.id.btnImpersonate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GodUserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_god_user, parent, false)
        return GodUserViewHolder(view)
    }

    override fun onBindViewHolder(holder: GodUserViewHolder, position: Int) {
        val user = userList[position]
        holder.tvName.text = user.name ?: "Unknown Agent"
        holder.tvEmail.text = user.email ?: "No Email"
        holder.tvId.text = "ID: #${user.agentId ?: "NONE"}"

        if (user.banned) {
            holder.btnBan.text = "BANNED"
            holder.btnBan.setBackgroundColor(android.graphics.Color.DKGRAY)
            holder.btnBan.isEnabled = false
        } else {
            holder.btnBan.text = "BAN"
            holder.btnBan.setBackgroundColor(android.graphics.Color.RED)
            holder.btnBan.isEnabled = true
        }

        holder.btnBan.setOnClickListener {
            val context = holder.itemView.context
            AlertDialog.Builder(context)
                .setTitle("PERMANENT BAN")
                .setMessage("Are you sure you want to completely ban ${user.name}? They will be locked out of the app forever.")
                .setPositiveButton("EXECUTE") { _, _ ->
                    if (user.uid != null) {
                        FirebaseDatabase.getInstance().getReference("users").child(user.uid).child("banned").setValue(true)
                    }
                }
                .setNegativeButton("CANCEL", null)
                .show()
        }

        holder.btnImpersonate.setOnClickListener {
            val context = holder.itemView.context
            AlertDialog.Builder(context)
                .setTitle("IDENTITY THEFT")
                .setMessage("Hijack the identity of ${user.name}? All messages you send will appear under their name and Agent ID across all enclaves.")
                .setPositiveButton("HIJACK") { _, _ ->
                    val sharedPref = context.getSharedPreferences("PsiRaPrefs", android.content.Context.MODE_PRIVATE)
                    sharedPref.edit()
                        .putString("IMPERSONATING_NAME", user.name)
                        .putString("IMPERSONATING_ID", user.agentId)
                        .apply()
                    android.widget.Toast.makeText(context, "Identity adopted: ${user.name}", android.widget.Toast.LENGTH_LONG).show()
                }
                .setNegativeButton("CANCEL", null)
                .show()
        }
    }

    override fun getItemCount() = userList.size
}
