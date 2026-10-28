package com.facultyconnect.app.ui.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.facultyconnect.app.databinding.ItemMsgInBinding
import com.facultyconnect.app.databinding.ItemMsgOutBinding
import com.facultyconnect.app.model.ChatMessage
import com.facultyconnect.app.model.SendState
import com.facultyconnect.app.util.TimeFormat

/** List is newest-first; the RecyclerView uses reverseLayout so index 0 sits at the bottom. */
class MessageAdapter(
    private val myUid: String,
    private val otherUid: String,
    private val onRetry: (ChatMessage) -> Unit
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(a: ChatMessage, b: ChatMessage) = a.id == b.id
        override fun areContentsTheSame(a: ChatMessage, b: ChatMessage) = a == b
    }

    override fun getItemViewType(position: Int) = if (getItem(position).senderId == myUid) 1 else 0

    class InVH(val b: ItemMsgInBinding) : RecyclerView.ViewHolder(b.root)
    class OutVH(val b: ItemMsgOutBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == 1) OutVH(ItemMsgOutBinding.inflate(inf, parent, false))
        else InVH(ItemMsgInBinding.inflate(inf, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val m = getItem(position)
        when (holder) {
            is InVH -> {
                holder.b.tvMessage.text = m.text
                holder.b.tvMeta.text = TimeFormat.chatTime(m.createdAt)
            }
            is OutVH -> {
                holder.b.tvMessage.text = m.text
                val status = when (m.state) {
                    SendState.PENDING -> "Sending..."
                    SendState.FAILED -> "Not sent. Tap to retry"
                    SendState.SENT -> if (otherUid in m.readBy) "Read" else "Sent"
                }
                holder.b.tvMeta.text = if (m.state == SendState.SENT) "${TimeFormat.chatTime(m.createdAt)}  |  $status" else status
                holder.b.root.setOnClickListener { if (m.state == SendState.FAILED) onRetry(m) }
                holder.b.root.isClickable = m.state == SendState.FAILED
            }
        }
    }
}
