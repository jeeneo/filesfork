package me.zhanghai.android.files.sync

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java8.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.util.UUID

private const val SYNC_PREFERENCES_NAME = "sync_groups"

class SyncViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SyncGroupStore(
        application.getSharedPreferences(SYNC_PREFERENCES_NAME, Context.MODE_PRIVATE)
    )

    private val states = mutableStateMapOf<String, SyncState>()

    private val jobs = mutableMapOf<String, Job>()

    var groups by mutableStateOf<List<SyncGroup>>(emptyList())
        private set

    var editingGroupId by mutableStateOf<String?>(null)
        private set

    init {
        groups = store.getGroups()
    }

    fun group(id: String): SyncGroup? = groups.firstOrNull { it.id == id }

    fun state(id: String): SyncState = states[id] ?: SyncState.Idle

    fun editGroup(id: String?) {
        editingGroupId = id
    }

    fun addGroup() {
        val id = UUID.randomUUID().toString()
        updateGroups { it + SyncGroup(id) }
        editingGroupId = id
    }

    fun removeGroup(id: String) {
        cancel(id)
        states.remove(id)
        updateGroups { list -> list.filterNot { it.id == id } }
        if (editingGroupId == id) {
            editingGroupId = null
        }
    }

    fun updateDisplayName(id: String, displayName: String?) {
        updateGroup(id) { group -> group.copy(displayName = displayName?.takeIf { it.isNotBlank() }) }
    }

    fun addSource(id: String, path: Path, isDirectory: Boolean) {
        updateGroup(id) { group ->
            if (group.sourceItems.any { it.path == path }) {
                group
            } else {
                group.copy(sourceItems = group.sourceItems + SyncSourceItem(path, isDirectory))
            }
        }
    }

    fun removeSource(id: String, item: SyncSourceItem) {
        updateGroup(id) { group ->
            group.copy(sourceItems = group.sourceItems - item)
        }
    }

    fun updateTargetDirectory(id: String, path: Path) {
        updateGroup(id) { group -> group.copy(targetDirectory = path) }
    }

    fun updateOptions(id: String, options: SyncOptions) {
        updateGroup(id) { group -> group.copy(options = options) }
    }

    fun preview(id: String) {
        run(id, previewOnly = true)
    }

    fun sync(id: String) {
        val state = state(id)
        if (state is SyncState.Preview) {
            execute(id, state.plan)
        } else if (needsPreviewFirst(id)) {
            run(id, previewOnly = true)
        } else {
            run(id, previewOnly = false)
        }
    }

    fun cancel(id: String) {
        jobs.remove(id)?.cancel()
        states[id] = SyncState.Idle
    }

    fun resolveMissing(id: String, resolution: SyncMissingResolution) {
        val state = states[id] as? SyncState.NeedsResolution ?: return
        val plan = state.plan.resolved(resolution)
        if (state.previewOnly) {
            states[id] = SyncState.Preview(plan)
        } else {
            execute(id, plan)
        }
    }

    private fun needsPreviewFirst(id: String): Boolean {
        val group = group(id) ?: return false
        return group.options.deleteExtraneous && group.options.direction != SyncDirection.TwoWay && state(
            id
        ) !is SyncState.Preview
    }

    private fun run(id: String, previewOnly: Boolean) {
        val group = group(id) ?: return
        val targetDirectory = group.targetDirectory ?: return
        if (group.sourceItems.isEmpty()) {
            return
        }
        val state = state(id)
        if (state is SyncState.Planning || state is SyncState.Running) {
            return
        }
        val sourceItems = group.sourceItems.toList()
        val options = group.options
        jobs.remove(id)?.cancel()
        states[id] = SyncState.Planning
        jobs[id] = viewModelScope.launch {
            try {
                val plan = runInterruptible(Dispatchers.IO) {
                    SyncEngine.plan(sourceItems, targetDirectory, options)
                }
                if (plan.hasMissing) {
                    states[id] = SyncState.NeedsResolution(plan, previewOnly)
                } else if (previewOnly) {
                    states[id] = SyncState.Preview(plan)
                } else {
                    executePlan(id, plan, options)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                states[id] = SyncState.Idle
            }
        }
    }

    private fun execute(id: String, plan: SyncPlan) {
        val options = group(id)?.options ?: return
        jobs.remove(id)?.cancel()
        jobs[id] = viewModelScope.launch {
            executePlan(id, plan, options)
        }
    }

    private suspend fun executePlan(id: String, plan: SyncPlan, options: SyncOptions) {
        val actions = plan.actions
        if (actions.isEmpty()) {
            states[id] = SyncState.Done(plan.summary)
            return
        }
        var completed = 0
        var failed = 0
        states[id] = SyncState.Running(0, actions.size, null)
        for (action in actions) {
            currentCoroutineContext().ensureActive()
            try {
                runInterruptible(Dispatchers.IO) {
                    SyncEngine.execute(action, options)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                ++failed
            }
            ++completed
            currentCoroutineContext().ensureActive()
            states[id] = SyncState.Running(completed, actions.size, action)
        }
        currentCoroutineContext().ensureActive()
        states[id] = if (failed > 0) {
            SyncState.Failed(failed)
        } else {
            SyncState.Done(plan.summary)
        }
    }

    private fun updateGroup(id: String, transform: (SyncGroup) -> SyncGroup) {
        states.remove(id)
        updateGroups { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private fun updateGroups(transform: (List<SyncGroup>) -> List<SyncGroup>) {
        viewModelScope.launch {
            try {
                groups = store.update(transform)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
