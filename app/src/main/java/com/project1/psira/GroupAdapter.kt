package com.project1.psira

import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class GroupAdapter(
    private val groupList: List<Group>,
    private val onGroupLongClick: (Group) -> Unit
) : RecyclerView.Adapter<GroupAdapter.GroupViewHolder>() {

    class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvGroupName: TextView = itemView.findViewById(R.id.tvGroupName)
        val tvGroupId: TextView = itemView.findViewById(R.id.tvGroupId)
        val ivGroupIcon: ImageView = itemView.findViewById(R.id.ivGroupIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_group, parent, false)
        return GroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = groupList[position]
        holder.tvGroupName.text = group.name
        holder.tvGroupId.text = "#${group.id} (${group.memberCount}/50)"

        if (!group.imageBase64.isNullOrEmpty()) {
            try {
                val imageBytes = Base64.decode(group.imageBase64, Base64.DEFAULT)
                val decodedImage = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                holder.ivGroupIcon.setImageBitmap(decodedImage)
                holder.ivGroupIcon.imageTintList = null // Remove tint
            } catch (e: Exception) {
                holder.ivGroupIcon.setImageResource(R.drawable.ic_group_default)
            }
        } else {
            holder.ivGroupIcon.setImageResource(R.drawable.ic_group_default)
            val colorList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#42A5F5"))
            holder.ivGroupIcon.imageTintList = colorList
        }

        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val sharedPref = context.getSharedPreferences("PsiRaPrefs", android.content.Context.MODE_PRIVATE)
            sharedPref.edit().putString("SECURE_CHANNEL", "group_${group.id}").apply()
            
            val intent = Intent(context, ChatActivity::class.java)
            context.startActivity(intent)
        }

        holder.itemView.setOnLongClickListener {
            onGroupLongClick(group)
            true
        }
    }

    override fun getItemCount(): Int {
        return groupList.size
    }
}
