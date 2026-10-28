package com.facultyconnect.app.ui.schedule

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.facultyconnect.app.R
import com.facultyconnect.app.databinding.ItemScheduleBinding
import com.facultyconnect.app.model.Assignment
import com.facultyconnect.app.model.PostType
import com.facultyconnect.app.util.StatusStyle
import com.facultyconnect.app.util.TimeFormat

class ScheduleAdapter(
    private val myUid: String,
    private val onClick: (Assignment) -> Unit
) : ListAdapter<Assignment, ScheduleAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<Assignment>() {
        override fun areItemsTheSame(a: Assignment, b: Assignment) = a.id == b.id
        override fun areContentsTheSame(a: Assignment, b: Assignment) = a == b
    }

    inner class VH(val b: ItemScheduleBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemScheduleBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val a = getItem(position)
        StatusStyle.bindType(h.b.tvType, PostType.from(a.type))
        StatusStyle.bindPill(h.b.tvStatus, "Confirmed", R.color.status_assigned)
        h.b.tvSubject.text = a.subject
        h.b.tvClassRoom.text = "Class: ${a.className}  |  Room: ${a.room}"
        h.b.tvWhen.text = "${TimeFormat.date(a.startAt)}  |  ${TimeFormat.range(a.startAt, a.endAt)}"
        h.b.tvRole.text = if (a.assigneeId == myUid) "You are covering for ${a.posterName}"
        else "${a.assigneeName} is covering your lecture"
        h.b.root.setOnClickListener { onClick(a) }
    }
}
