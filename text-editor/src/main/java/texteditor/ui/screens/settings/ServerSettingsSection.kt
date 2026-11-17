package texteditor.ui.screens.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import texteditor.R
import texteditor.data.AppToasts
import texteditor.server.ServerGraphProvider
import texteditor.server.data.ServerSettings
import texteditor.server.data.ServerState
import texteditor.server.service.ServerService
import ui.dialogs.DialogAction
import ui.dialogs.TextInputDialog

private enum class ServerDialogField {
    PORT, URL_OVERRIDE,
}

@SuppressLint("InlinedApi")
@Composable
internal fun ServerSettingsSection(onChooseRootFolder: (String?) -> Unit) {
    val context = LocalContext.current
    val graph = remember(context) { ServerGraphProvider.get(context) }
    val serverState by graph.registry.serverState.collectAsState()
    val serverUrl by graph.registry.serverUrl.collectAsState()
    val settings by graph.settingsRepository.settings.collectAsState(initial = ServerSettings())

    val apiSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
    val notificationPermissionGranted =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    var dialogField by rememberSaveable { mutableStateOf<ServerDialogField?>(null) }

    val permissionDeniedMessage =
        stringResource(R.string.editor_server_notification_permission_denied)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            graph.registry.serverStarting()
            ServerService.start(context)
        } else {
            AppToasts.show(context, permissionDeniedMessage)
        }
    }

    fun toggleServer() {
        if (serverState == ServerState.RUNNING || serverState == ServerState.STOPPING) {
            graph.registry.serverStopping()
            ServerService.stop(context)
        } else if (notificationPermissionGranted) {
            graph.registry.serverStarting()
            ServerService.start(context)
        } else {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    SectionHeader("Server (experimental)")
    if (!apiSupported) {
        Text(
            text = stringResource(R.string.editor_server_requires_android_7),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    } else {
        val transitioning =
            serverState == ServerState.STARTING || serverState == ServerState.STOPPING
        val serverOn = serverState == ServerState.STARTING || serverState == ServerState.RUNNING
        val statusText = when (serverState) {
            ServerState.RUNNING -> serverUrl?.let {
                stringResource(R.string.editor_server_status_running_at, it)
            } ?: stringResource(R.string.editor_server_status_running)

            ServerState.STARTING -> stringResource(R.string.editor_server_status_starting)
            ServerState.STOPPING -> stringResource(R.string.editor_server_status_stopping)
            ServerState.STOPPED -> stringResource(R.string.editor_server_status_stopped)
        }
        val urlOverrideSummary = settings.baseUrlOverride.ifBlank {
            stringResource(R.string.editor_server_url_override_empty)
        }
        val rootSummary =
            settings.rootPath ?: stringResource(R.string.editor_server_root_folder_private)

        SettingsRow(
            label = stringResource(R.string.editor_server_title),
            description = statusText,
            enabled = !transitioning,
            onClick = ::toggleServer,
            trailing = {
                Switch(
                    checked = serverOn,
                    onCheckedChange = null,
                    enabled = !transitioning,
                )
            },
        )
        Text(
            text = stringResource(R.string.editor_server_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        SettingsRow(
            label = stringResource(R.string.editor_server_port_title),
            description = "Port the server listens on",
            onClick = { dialogField = ServerDialogField.PORT },
            trailing = { ValueWithChevron(settings.serverPort.toString()) },
        )
        SettingsRow(
            label = stringResource(R.string.editor_server_url_override_title),
            description = "Address shown to connected editors",
            onClick = { dialogField = ServerDialogField.URL_OVERRIDE },
            trailing = { ValueWithChevron(urlOverrideSummary) },
        )
        SettingsRow(
            label = stringResource(R.string.editor_server_root_folder_title),
            description = "Folder shared with the web editor",
            onClick = { onChooseRootFolder(settings.rootPath) },
            trailing = { ValueWithChevron(rootSummary) },
        )
    }

    when (dialogField) {
        ServerDialogField.PORT -> TextInputDialog(
            title = stringResource(R.string.editor_server_port_title),
            initialValue = settings.serverPort.toString(),
            onDismissRequest = { dialogField = null },
            trailingActions = listOf(
                DialogAction(text = "CANCEL", onClick = { dialogField = null }),
                DialogAction(
                    text = "SAVE",
                    onClick = { input ->
                        val port = input.toIntOrNull()?.takeIf { it in 1..65535 }
                        if (port != null) {
                            graph.settingsRepository.setPort(port)
                            dialogField = null
                        }
                    },
                ),
            ),
        )

        ServerDialogField.URL_OVERRIDE -> TextInputDialog(
            title = stringResource(R.string.editor_server_url_override_title),
            initialValue = settings.baseUrlOverride,
            onDismissRequest = { dialogField = null },
            trailingActions = listOf(
                DialogAction(text = "CANCEL", onClick = { dialogField = null }),
                DialogAction(
                    text = "SAVE",
                    onClick = { input ->
                        graph.settingsRepository.setBaseUrlOverride(input)
                        dialogField = null
                    },
                ),
            ),
        )

        null -> Unit
    }
}
