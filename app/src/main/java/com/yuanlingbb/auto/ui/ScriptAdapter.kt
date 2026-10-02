package com.yuanlingbb.auto.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yuanlingbb.auto.R
import com.yuanlingbb.auto.data.Script
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScriptAdapter(
    private val items: List<Script>,
    private val onOpen: (Script) -> Unit,
    private val onRun: (Script) -> Unit,
    private val onDelete: (Script) -> Unit
) : RecyclerView.Adapter<ScriptAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tvName)
        val tvMeta: TextView = v.findViewById(R.id.tvMeta)
        val btnRun: ImageButton = v.findViewById(R.id.btnRun)
        val btnDelete: ImageButton = v.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_script, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(h: VH, i: Int) {
        val s = items[i]
        h.tvName.text = s.name
        val time = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(s.updated))
        h.tvMeta.text = "更新于 $time · ${s.code.lines().size} 行"
        h.itemView.setOnClickListener { onOpen(s) }
        h.btnRun.setOnClickListener { onRun(s) }
        h.btnDelete.setOnClickListener { onDelete(s) }
    }
}
