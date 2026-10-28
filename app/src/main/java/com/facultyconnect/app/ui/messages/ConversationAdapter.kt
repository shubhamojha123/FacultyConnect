package com.facultyconnect.app.ui.messages

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.facultyconnect.app.databinding.ItemConversationBinding
import com.facultyconnect.app.model.Conversation
import com.facultyconnect.app.util.Avatar
import com.facultyconnect.app.util.TimeFormat
import com.facultyconnect.app.util.show

class ConversationAdapter(
    private val myUid: String,
    private val onClick: (Conversation) -> Unit
) : ListAdapter<Conversation, ConversationAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<Conversation>() {
        override fun areItemsTheSame(a: Conversation, b: Conversation) = a.id == b.id
        override fun areContentsTheSame(a: Conversation, b: Conversation) = a == b
    }

    inner class VH(val b: ItemConversationBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemConversationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val c = getItem(position)
        val other = c.otherUid(myUid)
        val name = c.participantNames[other] ?: "Faculty"
        Avatar.bind(h.b.ivAvatar, name, c.participantPhotos[other])
        h.b.tvName.text = name
        h.b.tvPost.show(c.postSubject != null)
        h.b.tvPost.text = "Re: ${c.postSubject}"
        val prefix = if (c.lastSenderId == myUid) "You: " else ""
        h.b.tvPreview.text = prefix + c.lastMessage
        h.b.tvTime.text = TimeFormat.listTime(c.lastMessageAt)
        val unread = c.unread[myUid] ?: 0L
        h.b.tvBadge.show(unread > 0)
        h.b.tvBadge.text = if (unread > 99) "99+" else unread.toString()
        h.b.root.setOnClickListener { onClick(c) }
    }
}
