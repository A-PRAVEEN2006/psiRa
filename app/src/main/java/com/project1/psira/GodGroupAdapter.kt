package com.project1.psira

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class GodGroupAdapter(private val groupList: List<Group>) : RecyclerView.Adapter<GodGroupAdapter.GodGroupViewHolder>() {

    class GodGroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvGodGroupName)
        val tvDetails: TextView = itemView.findViewById(R.id.tvGodGroupDetails)
        val btnSpy: Button = itemView.findViewById(R.id.btnSpy)
        val btnWiretap: Button = itemView.findViewById(R.id.btnWiretap)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GodGroupViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_god_group, parent, false)
        return GodGroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: GodGroupViewHolder, position: Int) {
        val group = groupList[position]
        holder.tvName.text = group.name ?: "Unnamed Enclave"
        holder.tvDetails.text = "ID: #${group.id ?: "NONE"} | Members: ${group.memberCount}/50"

        holder.btnSpy.setOnClickListener {
            val context = holder.itemView.context
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null && group.id != null) {
                PsiRaDialogs.showDeleteSheet(
                    context,
                    "INJECT GOD MODE?",
                    "Inject administrative backdoors into ${group.name}? You will be granted instant Admin privileges and added to the enclave.",
                    "INJECT"
                ) {
                    val db = FirebaseDatabase.getInstance()
                    db.getReference("groups").child(group.id!!).child("adminUids").child(currentUser.uid).setValue(true)
                    db.getReference("groups").child(group.id!!).child("memberCount").setValue(group.memberCount + 1)
                    db.getReference("user_groups").child(currentUser.uid).child(group.id!!).setValue(true)
                    Toast.makeText(context, "Injection Successful. Group appears in your Dashboard.", Toast.LENGTH_LONG).show()
                }
            }
        }

        holder.btnWiretap.setOnClickListener {
            val context = holder.itemView.context
            PsiRaDialogs.showDeleteSheet(
                context,
                "GHOST WIRETAP?",
                "Wiretap ${group.name}? You will view the live chat feed invisibly. Signal theft is non-traceable.",
                "HACK FEED"
            ) {
                val sharedPref = context.getSharedPreferences("PsiRaPrefs", android.content.Context.MODE_PRIVATE)
                sharedPref.edit().putString("SECURE_CHANNEL", "group_${group.id}").apply()
                val intent = android.content.Intent(context, ChatActivity::class.java)
                intent.putExtra("IS_GHOST_MODE", true)
                context.startActivity(intent)
            }
        }
    }

    override fun getItemCount() = groupList.size
}
