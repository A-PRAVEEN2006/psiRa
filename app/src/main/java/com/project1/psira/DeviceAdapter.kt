package com.project1.psira

import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DeviceAdapter(private val deviceList: List<BluetoothDevice>, private val onDeviceClick: (BluetoothDevice) -> Unit) :
    RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = deviceList[position]
        var name = "Unknown Signal"
        try {
            name = device.name ?: "Silent Node"
        } catch (e: SecurityException) {}

        val isVerified = name.contains("PsiRa", ignoreCase = true)
        
        if (isVerified) {
            holder.tvName.text = "✔ VERIFIED AGENT: $name \n[${device.address}]"
            holder.tvName.setTextColor(android.graphics.Color.GREEN)
        } else {
            holder.tvName.text = "░ NOISE: $name \n[${device.address}]"
            holder.tvName.setTextColor(android.graphics.Color.DKGRAY)
        }

        holder.itemView.setOnClickListener { 
            android.widget.Toast.makeText(holder.itemView.context, "ESTABLISHING SECURE LINK...", android.widget.Toast.LENGTH_SHORT).show()
            onDeviceClick(device) 
        }
    }


    override fun getItemCount(): Int = deviceList.size
}
