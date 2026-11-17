package texteditor.server.data

// SPDX-License-Identifier: GPL-3.0-or-later

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ServerState { STOPPED, STARTING, RUNNING, STOPPING }
enum class RefreshType { DIRS, FILE }
enum class RefreshSource { APP, WEB_UI }

data class RefreshSignal(
    val type: RefreshType,
    val path: String = "",
    val sourceId: RefreshSource,
    val clientId: String? = null,
)

class Registry {
    private val _serverState = MutableStateFlow(ServerState.STOPPED)
    val serverState: StateFlow<ServerState> = _serverState.asStateFlow()

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()

    private val _refresh = MutableSharedFlow<RefreshSignal>(extraBufferCapacity = 64)
    val refresh: SharedFlow<RefreshSignal> = _refresh.asSharedFlow()

    fun serverStarting() {
        _serverUrl.value = null
        _serverState.update { ServerState.STARTING }
    }

    fun serverStarted(url: String) {
        _serverUrl.value = url
        _serverState.update { ServerState.RUNNING }
    }

    fun serverStopping() = _serverState.update { ServerState.STOPPING }

    fun serverStopped() {
        _serverUrl.value = null
        _serverState.update { ServerState.STOPPED }
    }

    fun setServerUrl(url: String) {
        _serverUrl.value = url
    }

    fun emitRefresh(
        type: RefreshType,
        path: String,
        sourceId: RefreshSource,
        clientId: String? = null,
    ) {
        _refresh.tryEmit(RefreshSignal(type, path, sourceId, clientId))
    }
}
