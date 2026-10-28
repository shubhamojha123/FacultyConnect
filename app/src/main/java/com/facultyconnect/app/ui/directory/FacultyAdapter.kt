package com.facultyconnect.app.ui.directory

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.facultyconnect.app.databinding.ItemFacultyBinding
import com.facultyconnect.app.model.FacultyUser
import com.facultyconnect.app.util.Avatar
import com.facultyconnect.app.util.show

class FacultyAdapter(
    private val myUid: String,
    private val onMessage: (FacultyUser) -> Unit,
    private val onOpen: (FacultyUser) -> Unit
) : ListAdapter<FacultyUser, FacultyAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<FacultyUser>() {
        override fun areItemsTheSame(a: FacultyUser, b: FacultyUser) = a.uid == b.uid
        override fun areContentsTheSame(a: FacultyUser, b: FacultyUser) = a == b
    }

    inner class VH(val b: ItemFacultyBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemFacultyBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val u = getItem(position)
        Avatar.bind(h.b.ivAvatar, u.fullName, u.photoUrl)
        h.b.tvName.text = u.fullName
        h.b.tvRole.text = "${u.designation}  |  ${u.department}"
        h.b.tvSubjects.text = if (u.subjects.isEmpty()) "" else "Subjects: " + u.subjects.joinToString(", ")
        h.b.btnMessage.show(u.uid != myUid)
        h.b.btnMessage.setOnClickListener { onMessage(u) }
        h.b.root.setOnClickListener { onOpen(u) }
    }
}
