package me.zhanghai.android.files.sync

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import java8.nio.file.Path
import kotlinx.coroutines.launch
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.filelist.FileListActivity
import me.zhanghai.android.files.filelist.toUserFriendlyString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(viewModel: SyncViewModel) {
    val editingGroupId = viewModel.editingGroupId

    val sourceFileLauncher = rememberLauncherForActivityResult(
        FileListActivity.OpenFileContract()
    ) { path ->
        if (path != null) {
            editingGroupId?.let { viewModel.addSource(it, path, false) }
        }
    }
    val sourceFolderLauncher = rememberLauncherForActivityResult(
        FileListActivity.OpenDirectoryContract()
    ) { path ->
        if (path != null) {
            editingGroupId?.let { viewModel.addSource(it, path, true) }
        }
    }
    val targetFolderLauncher = rememberLauncherForActivityResult(
        FileListActivity.OpenDirectoryContract()
    ) { path ->
        if (path != null) {
            editingGroupId?.let { viewModel.updateTargetDirectory(it, path) }
        }
    }

    val urgentGroupId = viewModel.groups.firstOrNull {
        val state = viewModel.state(it.id)
        state is SyncState.NeedsResolution || state is SyncState.Failed
    }?.id
    var previewGroupId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_title)) },
                actions = {
                    IconButton(onClick = viewModel::addGroup) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.sync_group_add),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (viewModel.groups.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.sync_groups_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(viewModel.groups, key = { it.id }) { group ->
                    SyncGroupCard(
                        group = group,
                        syncState = viewModel.state(group.id),
                        onEdit = { viewModel.editGroup(group.id) },
                        onPreview = {
                            previewGroupId = group.id
                            viewModel.preview(group.id)
                        },
                        onSync = {
                            previewGroupId = group.id
                            viewModel.sync(group.id)
                        },
                        onCancel = { viewModel.cancel(group.id) },
                    )
                }
            }
        }
    }

    val group = editingGroupId?.let(viewModel::group)
    if (group != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        val dismiss = {
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                if (!sheetState.isVisible) {
                    viewModel.editGroup(null)
                }
            }
            Unit
        }
        var displayNameGroupId by remember { mutableStateOf<String?>(null) }
        var deleteGroupId by remember { mutableStateOf<String?>(null) }
        ModalBottomSheet(
            onDismissRequest = dismiss,
            sheetState = sheetState,
            // The form scrolls, so the sheet must not also consume vertical drags: doing both
            // made the sheet slide away mid-scroll. Closing is explicit or via back instead.
            sheetGesturesEnabled = false,
            // A drag handle would be a dead affordance without gestures.
            dragHandle = null,
        ) {
            SyncGroupEditor(
                group = group,
                syncState = viewModel.state(group.id),
                onDisplayNameClick = { displayNameGroupId = group.id },
                onClose = dismiss,
                onDeleteClick = { deleteGroupId = group.id },
                onAddFile = { sourceFileLauncher.launch(listOf(MimeType.ANY)) },
                onAddFolder = { sourceFolderLauncher.launch(null) },
                onRemoveSource = { viewModel.removeSource(group.id, it) },
                onTargetClick = { targetFolderLauncher.launch(group.targetDirectory) },
                onOptionsChange = { viewModel.updateOptions(group.id, it) },
                onPreview = {
                    previewGroupId = group.id
                    viewModel.preview(group.id)
                },
                onSync = {
                    previewGroupId = group.id
                    viewModel.sync(group.id)
                },
                onCancel = { viewModel.cancel(group.id) },
            )

            displayNameGroupId?.let(viewModel::group)?.let { editedGroup ->
                DisplayNameDialog(
                    displayName = editedGroup.displayName.orEmpty(),
                    onConfirm = {
                        displayNameGroupId = null
                        viewModel.updateDisplayName(editedGroup.id, it)
                    },
                    onDismiss = { displayNameGroupId = null },
                )
            }

            deleteGroupId?.let(viewModel::group)?.let { deletedGroup ->
                AlertDialog(
                    onDismissRequest = { deleteGroupId = null },
                    title = { Text(stringResource(R.string.sync_group_delete)) },
                    text = { Text(stringResource(R.string.sync_group_delete_message)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                deleteGroupId = null
                                viewModel.removeGroup(deletedGroup.id)
                            },
                        ) {
                            Text(stringResource(android.R.string.ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleteGroupId = null }) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    },
                )
            }
        }
    }

    val dialogGroup = (urgentGroupId ?: previewGroupId)?.let(viewModel::group)
    if (dialogGroup != null) {
        when (val dialogState = viewModel.state(dialogGroup.id)) {
            is SyncState.NeedsResolution -> SyncMissingDialog(
                plan = dialogState.plan,
                previewOnly = dialogState.previewOnly,
                onConfirm = { viewModel.resolveMissing(dialogGroup.id, it) },
                onDismiss = { viewModel.cancel(dialogGroup.id) },
            )

            is SyncState.Preview -> PlannedActionsDialog(
                plan = dialogState.plan,
                onSync = { viewModel.sync(dialogGroup.id) },
                onDismiss = { viewModel.cancel(dialogGroup.id) },
            )

            is SyncState.Failed -> SyncFailedDialog(
                failedCount = dialogState.failedCount,
                onDismiss = { viewModel.cancel(dialogGroup.id) },
            )

            else -> Unit
        }
    }
}

@Composable
private fun SyncGroupCard(
    group: SyncGroup,
    syncState: SyncState,
    onEdit: () -> Unit,
    onPreview: () -> Unit,
    onSync: () -> Unit,
    onCancel: () -> Unit,
) {
    val target = group.targetDirectory
    val canSync = group.sourceItems.isNotEmpty() && target != null
    Card(
        onClick = onEdit,
        colors = softCardColors(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            ListItem(
                colors = transparentListItemColors(),
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                    )
                },
                headlineContent = {
                    Text(
                        text = group.title(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = target?.toUserFriendlyString()
                                ?: stringResource(R.string.sync_target_no_folder),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(
                                R.string.sync_group_summary,
                                stringResource(group.options.direction.label),
                                group.sourceItems.joinToString { it.path.displayName() }
                                    .ifEmpty { stringResource(R.string.sync_group_no_source) },
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                    )
                },
            )
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SyncStatus(syncState)
                SyncActionButtons(
                    state = syncState,
                    canSync = canSync,
                    onPreview = onPreview,
                    onSync = onSync,
                    onCancel = onCancel,
                )
            }
        }
    }
}

@Composable
private fun SyncGroupEditor(
    group: SyncGroup,
    syncState: SyncState,
    onDisplayNameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onClose: () -> Unit,
    onAddFile: () -> Unit,
    onAddFolder: () -> Unit,
    onRemoveSource: (SyncSourceItem) -> Unit,
    onTargetClick: () -> Unit,
    onOptionsChange: (SyncOptions) -> Unit,
    onPreview: () -> Unit,
    onSync: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Keep the sheet at a fixed height so scrolling never re-measures it.
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = group.title(),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        onClickLabel = stringResource(R.string.sync_group_display_name),
                        onClick = onDisplayNameClick,
                    ),
            )
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.sync_group_delete),
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close),
                )
            }
        }
        SourceSection(
            items = group.sourceItems,
            onAddFile = onAddFile,
            onAddFolder = onAddFolder,
            onRemove = onRemoveSource,
        )
        TargetSection(
            target = group.targetDirectory,
            onClick = onTargetClick,
        )
        DirectionSection(
            direction = group.options.direction,
            onChange = { onOptionsChange(group.options.withDirection(it)) },
        )
        ConflictSection(
            direction = group.options.direction,
            selected = group.options.conflictPolicy,
            onChange = { onOptionsChange(group.options.copy(conflictPolicy = it)) },
        )
        OptionsSection(
            options = group.options,
            onChange = onOptionsChange,
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SyncStatus(syncState)
            SyncActionButtons(
                state = syncState,
                canSync = group.sourceItems.isNotEmpty() && group.targetDirectory != null,
                onPreview = onPreview,
                onSync = onSync,
                onCancel = onCancel,
            )
        }
    }
}

@Composable
private fun SyncStatus(
    state: SyncState,
    modifier: Modifier = Modifier,
) {
    when (state) {
        SyncState.Idle -> Unit

        SyncState.Planning -> LinearProgressIndicator(modifier = modifier.fillMaxWidth())

        is SyncState.NeedsResolution -> Text(
            text = stringResource(R.string.sync_status_needs_resolution),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier,
        )

        is SyncState.Preview -> Text(
            text = stringResource(
                R.string.sync_status_preview, syncSummaryText(state.plan.summary)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier,
        )

        is SyncState.Running -> {
            if (state.total > 0) {
                LinearProgressIndicator(
                    progress = { state.completed.toFloat() / state.total },
                    modifier = modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = modifier.fillMaxWidth())
            }
            state.currentAction?.let { action ->
                SyncActionRow(action)
            }
        }

        is SyncState.Done -> Text(
            text = stringResource(
                R.string.sync_status_done, syncSummaryText(state.summary)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier,
        )

        is SyncState.Failed -> Text(
            text = pluralStringResource(
                R.plurals.sync_failed_message, state.failedCount, state.failedCount
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier,
        )
    }
}

@Composable
private fun SyncActionButtons(
    state: SyncState,
    canSync: Boolean,
    onPreview: () -> Unit,
    onSync: () -> Unit,
    onCancel: () -> Unit,
) {
    if (state is SyncState.Running) {
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.sync_action_cancel))
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onPreview,
                enabled = canSync,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.sync_action_preview))
            }
            Button(
                onClick = onSync,
                enabled = canSync,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.sync_action_sync))
            }
        }
    }
}

@Composable
private fun DisplayNameDialog(
    displayName: String,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(displayName) { mutableStateOf(displayName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_group_display_name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.sync_group_display_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.sync_group_display_name_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.takeIf { it.isNotBlank() }) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun SyncMissingDialog(
    plan: SyncPlan,
    previewOnly: Boolean,
    onConfirm: (SyncMissingResolution) -> Unit,
    onDismiss: () -> Unit,
) {
    var missingOnTargetAction by remember(plan) { mutableStateOf(SyncMissingAction.Skip) }
    var missingOnSourceAction by remember(plan) { mutableStateOf(SyncMissingAction.Skip) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_missing_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.sync_missing_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (plan.missingOnTarget.isNotEmpty()) {
                    MissingActionsSection(
                        title = stringResource(
                            R.string.sync_missing_on_target, plan.missingOnTarget.size
                        ),
                        choices = listOf(
                            SyncMissingAction.Copy to R.string.sync_missing_copy_to_target,
                            SyncMissingAction.Delete to R.string.sync_missing_delete_from_source,
                            SyncMissingAction.Skip to R.string.sync_missing_skip,
                        ),
                        selected = missingOnTargetAction,
                        onSelect = { missingOnTargetAction = it },
                    )
                }
                if (plan.missingOnSource.isNotEmpty()) {
                    MissingActionsSection(
                        title = stringResource(
                            R.string.sync_missing_on_source, plan.missingOnSource.size
                        ),
                        choices = listOf(
                            SyncMissingAction.Copy to R.string.sync_missing_copy_to_source,
                            SyncMissingAction.Delete to R.string.sync_missing_delete_from_target,
                            SyncMissingAction.Skip to R.string.sync_missing_skip,
                        ),
                        selected = missingOnSourceAction,
                        onSelect = { missingOnSourceAction = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        SyncMissingResolution(
                            missingOnTarget = missingOnTargetAction,
                            missingOnSource = missingOnSourceAction,
                        )
                    )
                }
            ) {
                Text(
                    stringResource(
                        if (previewOnly) {
                            R.string.sync_action_preview
                        } else {
                            R.string.sync_action_sync
                        }
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun MissingActionsSection(
    title: String,
    choices: List<Pair<SyncMissingAction, Int>>,
    selected: SyncMissingAction,
    onSelect: (SyncMissingAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for ((action, label) in choices) {
                FilterChip(
                    selected = action == selected,
                    onClick = { onSelect(action) },
                    label = { Text(stringResource(label)) },
                )
            }
        }
    }
}

@Composable
private fun PlannedActionsDialog(
    plan: SyncPlan,
    onSync: () -> Unit,
    onDismiss: () -> Unit,
) {
    val displayedActions = plan.actions.take(MAX_DISPLAYED_SYNC_ACTIONS)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_actions_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = syncSummaryText(plan.summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                for (action in displayedActions) {
                    SyncActionRow(action)
                }
                if (plan.actions.size > displayedActions.size) {
                    Text(
                        text = stringResource(
                            R.string.sync_actions_more, plan.actions.size - displayedActions.size
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSync) {
                Text(stringResource(R.string.sync_action_sync))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.sync_action_close))
            }
        },
    )
}

@Composable
private fun SyncFailedDialog(
    failedCount: Int,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_failed_title)) },
        text = {
            Text(
                text = pluralStringResource(
                    R.plurals.sync_failed_message, failedCount, failedCount
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}

@Composable
private fun SectionTitle(@StringRes text: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

@Composable
private fun softCardColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
)

@Composable
private fun transparentListItemColors() =
    ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
private fun SourceSection(
    items: List<SyncSourceItem>,
    onAddFile: () -> Unit,
    onAddFolder: () -> Unit,
    onRemove: (SyncSourceItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(R.string.sync_source_title)
            Spacer(modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.sync_source_add),
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    containerColor = MaterialTheme.colorScheme.onSecondary,
                    properties = PopupProperties(focusable = false),
                    onDismissRequest = { expanded = false },
                ) {
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                contentDescription = null,
                            )
                        },
                        text = { Text(stringResource(R.string.sync_source_add_file)) },
                        onClick = { expanded = false; onAddFile() },
                    )
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                            )
                        },
                        text = { Text(stringResource(R.string.sync_source_add_folder)) },
                        onClick = { expanded = false; onAddFolder() },
                    )
                }
            }
        }

        if (items.isEmpty()) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.sync_source_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                )
            }
        } else {
            items.forEach { item ->
                SourceItem(item = item, onRemove = { onRemove(item) })
            }
        }
    }
}

@Composable
private fun SourceItem(
    item: SyncSourceItem,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = softCardColors()) {
        ListItem(
            colors = transparentListItemColors(),
            leadingContent = {
                Icon(
                    imageVector = if (item.isDirectory) {
                        Icons.Default.Folder
                    } else {
                        Icons.Default.Description
                    },
                    contentDescription = null,
                )
            },
            headlineContent = {
                Text(
                    text = item.path.displayName(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Text(
                    text = item.path.toUserFriendlyString(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.sync_remove_source),
                    )
                }
            },
        )
    }
}

@Composable
private fun TargetSection(
    target: Path?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(R.string.sync_target_title)
        Card(
            onClick = onClick,
            colors = softCardColors(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ListItem(
                colors = transparentListItemColors(),
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                    )
                },
                headlineContent = {
                    Text(
                        text = target?.displayName()
                            ?: stringResource(R.string.sync_target_choose_folder),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Text(
                        text = target?.toUserFriendlyString()
                            ?: stringResource(R.string.sync_target_no_folder),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                    )
                },
            )
        }
    }
}

@Composable
private fun DirectionSection(
    direction: SyncDirection,
    onChange: (SyncDirection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val directions = SyncDirection.entries.toTypedArray()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(R.string.sync_direction_title)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            directions.forEachIndexed { index, item ->
                SegmentedButton(
                    selected = item == direction,
                    onClick = { onChange(item) },
                    shape = SegmentedButtonDefaults.itemShape(index, directions.size),
                    icon = {},
                ) {
                    Text(
                        text = stringResource(item.label),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.animateContentSize(),
        ) {
            Crossfade(targetState = direction, label = "direction icon") { current ->
                Icon(
                    imageVector = current.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = stringResource(direction.description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConflictSection(
    direction: SyncDirection,
    selected: ConflictPolicy,
    onChange: (ConflictPolicy) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle(R.string.sync_conflict_title)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ConflictPolicy.entries.filter { direction.supports(it) }.forEach { policy ->
                FilterChip(
                    selected = policy == selected,
                    onClick = { onChange(policy) },
                    label = { Text(stringResource(policy.label)) },
                )
            }
        }
        Text(
            text = stringResource(selected.description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OptionsSection(
    options: SyncOptions,
    onChange: (SyncOptions) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionTitle(R.string.sync_options_title)
        if (options.direction != SyncDirection.TwoWay) {
            SwitchRow(
                title = R.string.sync_option_delete,
                description = R.string.sync_option_delete_desc,
                checked = options.deleteExtraneous,
                warn = options.deleteExtraneous,
                onCheckedChange = { onChange(options.copy(deleteExtraneous = it)) },
            )
        }
        SwitchRow(
            title = R.string.sync_option_timestamps,
            description = R.string.sync_option_timestamps_desc,
            checked = options.preserveTimestamps,
            onCheckedChange = { onChange(options.copy(preserveTimestamps = it)) },
        )
    }
}

@Composable
private fun SwitchRow(
    @StringRes title: Int,
    @StringRes description: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    warn: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(description),
                style = MaterialTheme.typography.bodySmall,
                color = if (warn) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SyncGroup.title(): String =
    displayName ?: targetDirectory?.displayName() ?: stringResource(R.string.sync_group_untitled)

private fun Path.displayName(): String =
    fileName?.toString()?.takeIf { it.isNotEmpty() } ?: toUserFriendlyString()

private const val MAX_DISPLAYED_SYNC_ACTIONS = 100

@Composable
private fun syncSummaryText(summary: SyncSummary): String {
    val parts = mutableListOf<String>()
    if (summary.pushCount > 0) {
        parts += pluralStringResource(
            R.plurals.sync_summary_push, summary.pushCount, summary.pushCount
        )
    }
    if (summary.pullCount > 0) {
        parts += pluralStringResource(
            R.plurals.sync_summary_pull, summary.pullCount, summary.pullCount
        )
    }
    if (summary.deleteCount > 0) {
        parts += pluralStringResource(
            R.plurals.sync_summary_delete, summary.deleteCount, summary.deleteCount
        )
    }
    return parts.joinToString(", ").ifEmpty { stringResource(R.string.sync_summary_empty) }
}

private fun SyncOperation.icon(): ImageVector =
    when (this) {
        SyncOperation.Push -> Icons.AutoMirrored.Filled.ArrowForward
        SyncOperation.Pull -> Icons.AutoMirrored.Filled.ArrowBack
        SyncOperation.DeleteSource, SyncOperation.DeleteTarget -> Icons.Default.Delete
    }

private fun SyncOperation.label(): Int =
    when (this) {
        SyncOperation.Push -> R.string.sync_op_push
        SyncOperation.Pull -> R.string.sync_op_pull
        SyncOperation.DeleteSource -> R.string.sync_op_delete_source
        SyncOperation.DeleteTarget -> R.string.sync_op_delete_target
    }

@Composable
private fun SyncActionRow(action: SyncAction, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = action.operation.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(action.operation.label()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = action.path.displayName(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Composable
private fun SyncGroupCardPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        SyncGroupCard(
            group = SyncGroup(id = "preview"),
            syncState = SyncState.Idle,
            onEdit = {},
            onPreview = {},
            onSync = {},
            onCancel = {},
        )
    }
}
