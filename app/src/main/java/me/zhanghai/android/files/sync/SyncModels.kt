package me.zhanghai.android.files.sync

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import java8.nio.file.Path
import me.zhanghai.android.files.R

enum class SyncDirection(
    val icon: ImageVector,
    @StringRes val label: Int,
    @StringRes val description: Int,
) {
    Push(
        Icons.AutoMirrored.Filled.ArrowForward,
        R.string.sync_direction_push,
        R.string.sync_direction_push_desc,
    ),
    TwoWay(
        Icons.Default.SwapHoriz,
        R.string.sync_direction_two_way,
        R.string.sync_direction_two_way_desc,
    ),
    Pull(
        Icons.AutoMirrored.Filled.ArrowBack,
        R.string.sync_direction_pull,
        R.string.sync_direction_pull_desc,
    );

    val defaultPolicy: ConflictPolicy
        get() = when (this) {
            Push -> ConflictPolicy.OverwriteTarget
            Pull -> ConflictPolicy.OverwriteSource
            TwoWay -> ConflictPolicy.KeepNewer
        }

    fun supports(policy: ConflictPolicy): Boolean = when (this) {
        Push -> policy != ConflictPolicy.OverwriteSource
        Pull -> policy != ConflictPolicy.OverwriteTarget
        TwoWay -> true
    }
}

enum class ConflictPolicy(
    @StringRes val label: Int,
    @StringRes val description: Int,
) {
    OverwriteTarget(
        R.string.sync_conflict_overwrite_target, R.string.sync_conflict_overwrite_target_desc
    ),
    OverwriteSource(
        R.string.sync_conflict_overwrite_source, R.string.sync_conflict_overwrite_source_desc
    ),
    KeepNewer(
        R.string.sync_conflict_keep_newer, R.string.sync_conflict_keep_newer_desc
    ),
    Skip(R.string.sync_conflict_skip, R.string.sync_conflict_skip_desc),
}

data class SyncOptions(
    val direction: SyncDirection = SyncDirection.Push,
    val conflictPolicy: ConflictPolicy = ConflictPolicy.OverwriteTarget,
    val deleteExtraneous: Boolean = false,
    val preserveTimestamps: Boolean = true,
) {
    fun withDirection(direction: SyncDirection): SyncOptions = copy(
        direction = direction,
        conflictPolicy = if (direction.supports(conflictPolicy)) {
            conflictPolicy
        } else {
            direction.defaultPolicy
        },
    )
}

data class SyncSourceItem(
    val path: Path,
    val isDirectory: Boolean,
)

data class SyncGroup(
    val id: String,
    val displayName: String? = null,
    val sourceItems: List<SyncSourceItem> = emptyList(),
    val targetDirectory: Path? = null,
    val options: SyncOptions = SyncOptions(),
)

enum class SyncOperation {
    Push,
    Pull,
    DeleteSource,
    DeleteTarget,
}

data class SyncSummary(
    val pushCount: Int = 0,
    val pullCount: Int = 0,
    val deleteCount: Int = 0,
)

sealed interface SyncAction {
    val path: Path

    val operation: SyncOperation

    data class CreateDirectory(
        override val path: Path,
        override val operation: SyncOperation,
    ) : SyncAction

    data class CopyFile(
        val source: Path,
        override val path: Path,
        override val operation: SyncOperation,
    ) : SyncAction

    data class Delete(
        override val path: Path,
        override val operation: SyncOperation,
    ) : SyncAction
}

data class SyncMissingPair(val source: Path, val target: Path)

enum class SyncMissingAction {
    Copy,
    Delete,
    Skip,
}

data class SyncMissingResolution(
    val missingOnTarget: SyncMissingAction = SyncMissingAction.Skip,
    val missingOnSource: SyncMissingAction = SyncMissingAction.Skip,
)

data class SyncPlan(
    val actions: List<SyncAction>,
    val missingOnTarget: List<SyncMissingPair> = emptyList(),
    val missingOnSource: List<SyncMissingPair> = emptyList(),
) {
    val summary: SyncSummary = SyncSummary(
        pushCount = actions.count {
            it is SyncAction.CopyFile && it.operation == SyncOperation.Push
        },
        pullCount = actions.count {
            it is SyncAction.CopyFile && it.operation == SyncOperation.Pull
        },
        deleteCount = actions.count { it is SyncAction.Delete },
    )

    val hasMissing: Boolean
        get() = missingOnTarget.isNotEmpty() || missingOnSource.isNotEmpty()

    fun resolved(resolution: SyncMissingResolution): SyncPlan =
        SyncEngine.resolveMissing(this, resolution)
}

sealed interface SyncState {
    data object Idle : SyncState
    data object Planning : SyncState
    data class NeedsResolution(val plan: SyncPlan, val previewOnly: Boolean) : SyncState
    data class Preview(val plan: SyncPlan) : SyncState
    data class Running(
        val completed: Int,
        val total: Int,
        val currentAction: SyncAction?,
    ) : SyncState

    data class Done(val summary: SyncSummary) : SyncState
    data class Failed(val failedCount: Int) : SyncState
}
