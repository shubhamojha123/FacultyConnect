package com.facultyconnect.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facultyconnect.app.data.AssignmentRepository
import com.facultyconnect.app.data.ChatRepository
import com.facultyconnect.app.data.ErrorMapper
import com.facultyconnect.app.data.PostRepository
import com.facultyconnect.app.data.Resource
import com.facultyconnect.app.data.RuleViolation
import com.facultyconnect.app.data.Session
import com.facultyconnect.app.data.UserRepository
import com.facultyconnect.app.model.ChatMessage
import com.facultyconnect.app.model.Conversation
import com.facultyconnect.app.model.LecturePost
import com.facultyconnect.app.model.PostStatus
import com.facultyconnect.app.model.SendState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUi(
    val loading: Boolean = false,
    val error: String? = null,
    val messages: List<ChatMessage> = emptyList(),   // newest first
    val canLoadMore: Boolean = false,
    val fromCache: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel : ViewModel() {
    private val repo = ChatRepository()
    private val users = UserRepository()
    private val posts = PostRepository()
    private val assignments = AssignmentRepository()
    private val me get() = Session.me!!

    private val pageSize = 30L
    private val limit = MutableStateFlow(pageSize)
    private val failed = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val marking = HashSet<String>()
    private var started = false

    val conversation = MutableStateFlow<Resource<Conversation>>(Resource.Loading)
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events
    private val _confirming = MutableStateFlow(false)
    val confirming: StateFlow<Boolean> = _confirming

    val ui: StateFlow<ChatUi> = conversation.flatMapLatest { cr ->
        when (cr) {
            is Resource.Loading -> flowOf(ChatUi(loading = true))
            is Resource.Error -> flowOf(ChatUi(error = cr.message))
            is Resource.Success -> limit.flatMapLatest { lim ->
                repo.observeMessages(cr.data.id, lim).map { r -> r to lim }
            }.combine(failed) { pair, f ->
                val (r, lim) = pair
                when (r) {
                    is Resource.Loading -> ChatUi(loading = true)
                    is Resource.Error -> ChatUi(error = r.message)
                    is Resource.Success -> {
                        val ids = r.data.map { it.id }.toSet()
                        ChatUi(messages = f.filter { it.id !in ids } + r.data, canLoadMore = r.data.size >= lim, fromCache = r.fromCache)
                    }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUi(loading = true))

    /** Lecture post linked to this conversation (live, so the banner and Confirm button stay current). */
    val post: StateFlow<LecturePost?> = conversation.flatMapLatest { cr ->
        val pid = (cr as? Resource.Success)?.data?.postId
        if (pid == null) flowOf<LecturePost?>(null)
        else posts.observePost(pid).map { (it as? Resource.Success)?.data }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun start(convId: String, otherUid: String, postId: String?, requestToTake: Boolean = false) {
        if (started) return
        started = true
        load(convId, otherUid, postId, requestToTake)
    }

    fun reload(convId: String, otherUid: String, postId: String?) = load(convId, otherUid, postId)

    private fun load(convId: String, otherUid: String, postId: String?, requestToTake: Boolean = false) {
        conversation.value = Resource.Loading
        viewModelScope.launch {
            try {
                val existing = repo.getConversation(convId)
                val conv = if (existing != null) existing else {
                    val other = users.getUser(otherUid) ?: throw RuleViolation("That faculty member could not be found")
                    val post = postId?.let { posts.getPost(it) }
                    repo.getOrCreate(convId, me, other, post)
                }
                conversation.value = Resource.Success(conv)
                if (requestToTake && postId != null) {
                    val currentPost = posts.getPost(postId)
                    if (currentPost == null || currentPost.postStatus != PostStatus.AVAILABLE || currentPost.posterId == me.uid) {
                        _events.emit("This lecture is no longer available to request.")
                    } else {
                        val requestMessageId = "take_${currentPost.id}_${me.uid}"
                        if (repo.hasMessage(conv.id, requestMessageId)) {
                            _events.emit("You have already requested this lecture. Continue the discussion here.")
                        } else {
                            repo.send(
                                conv, me, requestMessageId,
                                "I'd like to request this lecture. Could you consider assigning it to me? We can discuss the details here."
                            )
                            _events.emit("Request sent. The poster can review it in Messages and decide whether to assign the lecture.")
                        }
                    }
                }
                // Clear my unread counter on open.
                runCatching { repo.markRead(conv.id, me.uid, emptyList()) }
            } catch (e: Exception) {
                conversation.value = Resource.Error(ErrorMapper.message(e))
            }
        }
    }

    fun loadMore() {
        val s = ui.value
        if (!s.loading && s.canLoadMore) limit.update { it + pageSize }
    }

    fun send(text: String) {
        val t = text.trim()
        val c = (conversation.value as? Resource.Success)?.data ?: return
        if (t.isEmpty()) return
        dispatch(c, repo.newMessageId(c.id), t)
    }

    fun retry(m: ChatMessage) {
        val c = (conversation.value as? Resource.Success)?.data ?: return
        failed.update { list -> list.filter { it.id != m.id } }
        dispatch(c, m.id, m.text)
    }

    private fun dispatch(c: Conversation, id: String, text: String) {
        viewModelScope.launch {
            try {
                repo.send(c, me, id, text)
            } catch (e: Exception) {
                failed.update { listOf(ChatMessage(id, me.uid, text, null, listOf(me.uid), SendState.FAILED)) + it }
                _events.emit("Message not sent: " + ErrorMapper.message(e))
            }
        }
    }

    /** Called by the screen (only while visible) with the current list so incoming messages get marked read. */
    fun onMessagesShown(msgs: List<ChatMessage>) {
        val c = (conversation.value as? Resource.Success)?.data ?: return
        val ids = msgs.filter { it.senderId != me.uid && me.uid !in it.readBy && it.state == SendState.SENT && it.id !in marking }
            .map { it.id }
        if (ids.isEmpty()) return
        marking.addAll(ids)
        viewModelScope.launch {
            try { repo.markRead(c.id, me.uid, ids) } catch (_: Exception) { marking.removeAll(ids.toSet()) }
        }
    }

    /** Poster confirms the other participant for the linked lecture (one Firestore transaction). */
    fun confirmAssignment() {
        val c = (conversation.value as? Resource.Success)?.data ?: return
        val p = post.value ?: return
        if (_confirming.value) return
        val otherUid = c.otherUid(me.uid)
        val otherName = c.participantNames[otherUid] ?: "Faculty"
        _confirming.value = true
        viewModelScope.launch {
            try {
                assignments.confirm(p.id, otherUid, otherName, me)
                _events.emit("Assigned to $otherName. It now appears in both schedules.")
            } catch (e: Exception) {
                _events.emit(ErrorMapper.message(e))
            } finally { _confirming.value = false }
        }
    }
}
