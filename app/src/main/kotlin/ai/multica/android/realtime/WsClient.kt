package ai.multica.android.realtime

import ai.multica.android.BuildConfig
import ai.multica.android.core.auth.ServerUrlStore
import ai.multica.android.core.auth.TokenStore
import ai.multica.android.core.network.NetworkFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.random.Random

/**
 * Tier-2 WebSocket client — mirrors `apps/mobile/data/realtime/ws-client.ts`.
 *
 * Lifecycle: `Idle → Connecting → Active ↔ Paused`.
 * Auth: first-frame `{type:"auth", payload:{token}}` → wait for
 * `auth_ack` (10s deadline). Server auto-subscribes us to
 * `workspace:{id}` and `user:{id}` scopes, so we don't send any
 * subscribe messages.
 *
 * Reconnect: exponential backoff with full jitter, 1s base, 30s cap,
 * never gives up. AppState changes (ON_STOP/ON_START) pause/resume
 * the connection via [RealtimeManager].
 */
@Singleton
class WsClient @Inject constructor(
    private val client: OkHttpClient,
    private val tokenStore: TokenStore,
    private val serverUrlStore: ServerUrlStore,
) {
    enum class State { Idle, Connecting, Active, Paused }

    private val json: Json = NetworkFactory.json
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private var webSocket: WebSocket? = null
    private var authAckDeadlineJob: Job? = null
    private var workspaceSlug: String? = null
    private var consecutiveFailures: Int = 0

    private val _state = MutableStateFlow(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _events = MutableSharedFlow<WsEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<WsEvent> = _events.asSharedFlow()

    /**
     * Start (or resume) the connection for the given workspace slug.
     * Idempotent: if already connected to the same slug, no-op.
     * If a different slug, the old connection is closed and replaced.
     */
    fun start(slug: String) {
        if (slug == workspaceSlug && _state.value == State.Active) return
        scope.launch {
            mutex.withLock {
                if (slug == workspaceSlug && _state.value == State.Active) return@withLock
                webSocket?.close(1000, "switching workspace")
                webSocket = null
                workspaceSlug = slug
                consecutiveFailures = 0
                _state.value = State.Connecting
                connect()
            }
        }
    }

    fun pause() {
        scope.launch {
            mutex.withLock {
                if (_state.value == State.Idle) return@withLock
                webSocket?.close(1000, "app paused")
                webSocket = null
                _state.value = State.Paused
            }
        }
    }

    fun resume() {
        val slug = workspaceSlug ?: return
        if (_state.value == State.Active) return
        start(slug)
    }

    fun stop() {
        scope.launch {
            mutex.withLock {
                webSocket?.close(1000, "client stopped")
                webSocket = null
                workspaceSlug = null
                _state.value = State.Idle
            }
        }
    }

    fun shutdown() {
        stop()
        scope.cancel()
    }

    private suspend fun connect() {
        val slug = workspaceSlug ?: run { _state.value = State.Idle; return }
        val baseUrl = serverUrlStore.getActiveUrl()
        val wsBase = baseUrl
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
            .trimEnd('/')
        val token = tokenStore.getToken()
        if (token == null) {
            _state.value = State.Idle
            return
        }

        val url = buildString {
            append(wsBase)
            append("/ws")
            append("?workspace_slug=").append(slug)
            append("&client_platform=").append(BuildConfig.CLIENT_PLATFORM)
            append("&client_version=").append(BuildConfig.VERSION_NAME)
            append("&client_os=").append(BuildConfig.CLIENT_OS)
        }

        val request = Request.Builder().url(url).build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Send auth frame as the very first message.
                val authFrame = """{"type":"auth","payload":{"token":"$token"}}"""
                webSocket.send(authFrame)
                // Start ack deadline.
                authAckDeadlineJob?.cancel()
                authAckDeadlineJob = scope.launch {
                    delay(AUTH_ACK_TIMEOUT_MS)
                    // If we never got auth_ack, close and let the
                    // exponential backoff reconnect logic kick in.
                    webSocket.close(4001, "auth_ack timeout")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onConnectionClosed()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onConnectionClosed()
            }
        }

        webSocket = client.newWebSocket(request, listener)
    }

    private fun handleMessage(text: String) {
        try {
            val envelope = json.parseToJsonElement(text) as? JsonObject ?: return
            val type = (envelope["type"] as? JsonPrimitive)?.contentOrNull ?: return
            val payload = envelope["payload"] as? JsonObject ?: JsonObject(emptyMap())
            val actorId = (envelope["actor_id"] as? JsonPrimitive)?.contentOrNull
            val actorType = (envelope["actor_type"] as? JsonPrimitive)?.contentOrNull

            when (type) {
                "auth_ack" -> {
                    authAckDeadlineJob?.cancel()
                    authAckDeadlineJob = null
                    _state.value = State.Active
                    _events.tryEmit(WsEvent.AuthAck(envelope))
                }
                "inbox:new" -> {
                    val itemEl = payload["item"] ?: return
                    val item = runCatching {
                        json.decodeFromJsonElement(InboxItemSerializer, itemEl)
                    }.getOrNull() ?: return
                    _events.tryEmit(WsEvent.InboxNew(item, actorId, actorType))
                }
                "inbox:read" -> {
                    val itemId = (payload["item_id"] as? JsonPrimitive)?.contentOrNull ?: return
                    val recipientId = (payload["recipient_id"] as? JsonPrimitive)?.contentOrNull ?: return
                    _events.tryEmit(WsEvent.InboxRead(itemId, recipientId, actorId, actorType))
                }
                "inbox:archived" -> {
                    val itemId = (payload["item_id"] as? JsonPrimitive)?.contentOrNull ?: return
                    val issueId = (payload["issue_id"] as? JsonPrimitive)?.contentOrNull
                    val recipientId = (payload["recipient_id"] as? JsonPrimitive)?.contentOrNull ?: return
                    _events.tryEmit(WsEvent.InboxArchived(itemId, issueId, recipientId, actorId, actorType))
                }
                "inbox:batch-read" -> {
                    val recipientId = (payload["recipient_id"] as? JsonPrimitive)?.contentOrNull ?: return
                    val count = (payload["count"] as? JsonPrimitive)?.intOrNull?.toLong() ?: 0L
                    _events.tryEmit(WsEvent.InboxBatchRead(recipientId, count, actorId, actorType))
                }
                "inbox:batch-archived" -> {
                    val recipientId = (payload["recipient_id"] as? JsonPrimitive)?.contentOrNull ?: return
                    val count = (payload["count"] as? JsonPrimitive)?.intOrNull?.toLong() ?: 0L
                    _events.tryEmit(WsEvent.InboxBatchArchived(recipientId, count, actorId, actorType))
                }
                "comment:created", "comment:updated", "comment:resolved", "comment:unresolved" -> {
                    val comment = payload["comment"] ?: return
                    val issueId = (comment.jsonObject["issue_id"] as? JsonPrimitive)?.contentOrNull ?: return
                    val raw = comment.jsonObject
                    val ev = when (type) {
                        "comment:created" -> WsEvent.CommentCreated(issueId, raw, actorId, actorType)
                        "comment:updated" -> WsEvent.CommentUpdated(issueId, raw, actorId, actorType)
                        "comment:resolved" -> WsEvent.CommentResolved(issueId, raw, actorId, actorType)
                        "comment:unresolved" -> WsEvent.CommentUnresolved(issueId, raw, actorId, actorType)
                        else -> return
                    }
                    _events.tryEmit(ev)
                }
                "comment:deleted" -> {
                    val issueId = (payload["issue_id"] as? JsonPrimitive)?.contentOrNull ?: return
                    val commentId = (payload["comment_id"] as? JsonPrimitive)?.contentOrNull ?: return
                    _events.tryEmit(WsEvent.CommentDeleted(issueId, commentId, actorId, actorType))
                }
                "issue:updated" -> {
                    val issue = payload["issue"] ?: return
                    val issueId = (issue.jsonObject["id"] as? JsonPrimitive)?.contentOrNull ?: return
                    _events.tryEmit(WsEvent.IssueUpdated(issueId, issue.jsonObject, actorId, actorType))
                }
                // ---- Entity lifecycle: best-effort id extraction.
                // The list VMs only need a refresh signal; the id is decorative.
                // Web payloads: agent events carry {agent:{id}}, project events
                // {project:{id}} or {project_id}, member events {member:{user_id}}
                // or {user_id}. squad/label/pin are `unknown` in the web type
                // map so we try common shapes and fall back to "".
                "agent:status", "agent:created", "agent:archived", "agent:restored" -> {
                    val id = extractEntityId(payload, "agent") ?: extractFlatId(payload, "agent_id")
                    _events.tryEmit(WsEvent.AgentChanged(id ?: "", actorId, actorType))
                }
                "squad:created", "squad:updated", "squad:deleted" -> {
                    val id = extractEntityId(payload, "squad") ?: extractFlatId(payload, "squad_id")
                    _events.tryEmit(WsEvent.SquadChanged(id ?: "", actorId, actorType))
                }
                "project:created", "project:updated", "project:deleted" -> {
                    val id = extractEntityId(payload, "project") ?: extractFlatId(payload, "project_id")
                    _events.tryEmit(WsEvent.ProjectChanged(id ?: "", actorId, actorType))
                }
                "label:created", "label:updated", "label:deleted" -> {
                    val id = extractEntityId(payload, "label") ?: extractFlatId(payload, "label_id")
                    _events.tryEmit(WsEvent.LabelChanged(id ?: "", actorId, actorType))
                }
                "member:added", "member:updated", "member:removed" -> {
                    val id = extractEntityId(payload, "member") ?: extractFlatId(payload, "user_id") ?: extractFlatId(payload, "member_id")
                    _events.tryEmit(WsEvent.MemberChanged(id ?: "", actorId, actorType))
                }
                "pin:created", "pin:deleted", "pin:reordered" -> {
                    _events.tryEmit(WsEvent.PinChanged(actorId, actorType))
                }
                else -> {
                    _events.tryEmit(WsEvent.Unknown(type, envelope, actorId, actorType))
                }
            }
        } catch (e: Throwable) {
            // Swallow malformed frames; the server has its own dedup
            // and we don't want a single bad payload to crash the loop.
        }
    }

    /** Pull `id` (or `user_id` for members) out of a nested object payload like {agent:{id}}. */
    private fun extractEntityId(payload: JsonObject, key: String): String? {
        val obj = payload[key] as? JsonObject ?: return null
        val idField = if (key == "member") "user_id" else "id"
        return (obj[idField] as? JsonPrimitive)?.contentOrNull
    }

    /** Pull a flat id like {agent_id}/{project_id}/{user_id} from the payload. */
    private fun extractFlatId(payload: JsonObject, key: String): String? =
        (payload[key] as? JsonPrimitive)?.contentOrNull

    private fun onConnectionClosed() {
        val current = _state.value
        if (current == State.Paused || current == State.Idle) return
        // Schedule reconnect with exponential backoff, but cap total
        // attempts at 5 to avoid hammering the server (and exhausting
        // OkHttp's dispatcher pool) when the server is genuinely
        // unavailable. Pause and resume (e.g. ON_STOP/ON_START or
        // workspace switch) resets the counter via [start].
        if (consecutiveFailures >= MAX_FAILURES) {
            _state.value = State.Paused
            return
        }
        consecutiveFailures++
        scope.launch {
            var attempt = 0
            while (isActive && _state.value != State.Idle && _state.value != State.Paused) {
                val delayMs = nextBackoffMs(attempt)
                delay(delayMs)
                if (_state.value == State.Paused || _state.value == State.Idle) return@launch
                connect()
                if (_state.value == State.Active) {
                    consecutiveFailures = 0
                    return@launch
                }
                attempt++
            }
        }
    }

    private fun nextBackoffMs(attempt: Int): Long {
        val base = 1000L
        val cap = 30_000L
        val exp = min(cap, base * (1L shl min(attempt, 16)))
        // Full jitter: random in [0, exp]
        return Random.nextLong(0, exp + 1)
    }

    companion object {
        private const val AUTH_ACK_TIMEOUT_MS = 10_000L
        private const val MAX_FAILURES = 5
    }
}

/**
 * Reuse the InboxItem serializer rather than redefining it.
 * `json.decodeFromJsonElement<InboxItem>(...)` would also work; this
 * shim keeps the import in one place.
 */
private val InboxItemSerializer = ai.multica.android.data.model.InboxItem.serializer()
