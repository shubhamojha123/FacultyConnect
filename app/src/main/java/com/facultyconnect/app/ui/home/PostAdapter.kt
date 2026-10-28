package com.facultyconnect.app.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.facultyconnect.app.databinding.ItemPostBinding
import com.facultyconnect.app.model.LecturePost
import com.facultyconnect.app.model.PostStatus
import com.facultyconnect.app.util.Avatar
import com.facultyconnect.app.util.StatusStyle
import com.facultyconnect.app.util.TimeFormat
import com.facultyconnect.app.util.show

/** Post cards separate a take request, direct chat, and the poster's management action. */
class PostAdapter(
    private val myUid: String,
    private val onRequest: (LecturePost) -> Unit,
    private val onMessage: (LecturePost) -> Unit,
    private val onManage: (LecturePost) -> Unit,
    private val onTeams: (LecturePost) -> Unit,
    private val onOpen: (LecturePost) -> Unit
) : ListAdapter<LecturePost, PostAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<LecturePost>() {
        override fun areItemsTheSame(a: LecturePost, b: LecturePost) = a.id == b.id
        override fun areContentsTheSame(a: LecturePost, b: LecturePost) = a == b
    }

    inner class VH(val b: ItemPostBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemPostBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val p = getItem(position)
        val b = h.b
        Avatar.bind(b.ivAvatar, p.posterName, p.posterPhotoUrl)
        b.tvName.text = p.posterName
        b.tvDept.text = p.posterDepartment
        StatusStyle.bindStatus(b.tvStatus, p.postStatus)
        StatusStyle.bindType(b.tvType, p.postType)
        b.tvPosted.text = "Posted " + TimeFormat.relative(p.createdAt)
        b.tvSubject.text = p.subject
        b.tvClass.text = "Class: ${p.className}"
        b.tvAssigned.show(p.postStatus == PostStatus.ASSIGNED)
        b.tvAssigned.text = "Assigned to ${p.assignedToName?.takeIf(String::isNotBlank) ?: "Faculty"}"
        b.tvWhen.text = "${TimeFormat.date(p.startAt)}  |  ${TimeFormat.range(p.startAt, p.endAt)}"
        b.tvRoom.text = "Room: ${p.room}"
        b.tvReason.text = p.reason
        val mine = p.posterId == myUid
        b.rowPostActions.show(!mine)
        b.btnManagePost.show(mine)
        b.btnManagePost.setOnClickListener { onManage(p) }
        b.btnRequestTake.show(!mine && p.postStatus == PostStatus.AVAILABLE)
        b.btnRequestTake.setOnClickListener { onRequest(p) }
        b.btnMessage.setOnClickListener { onMessage(p) }
        b.btnTeams.show(!mine && p.teamsLink.startsWith("https://"))
        b.btnTeams.setOnClickListener { onTeams(p) }
        b.root.setOnClickListener { onOpen(p) }
    }
}
