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
            PsiRaDialogs.showDeleteSheet(
                context,
                "PERMANENT BAN?",
                "Eradicate the identity of ${user.name}? They will be locked out of the matrix forever.",
                "EXECUTE"
            ) {
                if (user.uid != null) {
                    FirebaseDatabase.getInstance().getReference("users").child(user.uid).child("banned").setValue(true)
                }
            }
        }

        holder.btnImpersonate.setOnClickListener {
            val context = holder.itemView.context
            PsiRaDialogs.showDeleteSheet(
                context,
                "IDENTITY THEFT?",
                "Hijack the identity of ${user.name}? You will walk the matrix in their name.",
                "HIJACK"
            ) {
                val sharedPref = context.getSharedPreferences("PsiRaPrefs", android.content.Context.MODE_PRIVATE)
                sharedPref.edit()
                    .putString("IMPERSONATING_NAME", user.name)
                    .putString("IMPERSONATING_ID", user.agentId)
                    .apply()
                android.widget.Toast.makeText(context, "Identity adopted: ${user.name}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun getItemCount() = userList.size
}
