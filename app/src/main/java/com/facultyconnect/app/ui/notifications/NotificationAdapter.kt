package com.facultyconnect.app.ui.notifications

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.facultyconnect.app.databinding.ItemNotificationBinding
import com.facultyconnect.app.model.AppNotification
import com.facultyconnect.app.util.TimeFormat
import com.facultyconnect.app.util.show

class NotificationAdapter(private val onClick: (AppNotification) -> Unit) :
    ListAdapter<AppNotification, NotificationAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<AppNotification>() {
        override fun areItemsTheSame(a: AppNotification, b: AppNotification) = a.id == b.id
        override fun areContentsTheSame(a: AppNotification, b: AppNotification) = a == b
    }

    inner class VH(val b: ItemNotificationBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val n = getItem(position)
        h.b.viewUnread.visibility = if (n.read) android.view.View.INVISIBLE else android.view.View.VISIBLE
        h.b.tvTitle.text = n.title
        h.b.tvBody.text = n.body
        h.b.tvTime.text = TimeFormat.relative(n.createdAt) + if (n.read) "" else "  |  Unread"
        h.b.root.setOnClickListener { onClick(n) }
    }
}
