package texteditor.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import texteditor.SoraEditorViewModel
import texteditor.data.PRELOAD_BG_DARK
import texteditor.data.registry.FontRegistry
import texteditor.data.registry.ThemeRegistry
import ui.SimpleShadow
import java.io.File

@Composable
fun EditorSettingsScreen(
    viewModel: SoraEditorViewModel,
    onNavigateUp: () -> Unit,
) {
    BackHandler(onBack = onNavigateUp)
    val context = LocalContext.current
    val themeDisplayName =
        ThemeRegistry.availableThemes.find { it.id == viewModel.selectedTheme }?.displayName
            ?: viewModel.selectedTheme
    val fontDisplayName =
        viewModel.fontOptions.find { it.id == viewModel.selectedFont }?.displayName
            ?: viewModel.systemFontOptions.find { it.id == viewModel.selectedFont }?.displayName
            ?: File(viewModel.selectedFont).name
    val fontFamily = remember(viewModel.selectedFont) {
        FontFamily(FontRegistry.loadTypeface(context, viewModel.selectedFont))
    }
    Surface(
        modifier = Modifier.fillMaxSize(), color = Color(PRELOAD_BG_DARK),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp),
                ) {
                    SectionHeader("Appearance")
                    SettingsRow(
                        label = "Theme",
                        description = "Coming soon",
                        enabled = false,
                        onClick = { viewModel.openThemeDialog() },
                        trailing = { ValueWithChevron(themeDisplayName) },
                    )
                    SettingsRow(
                        label = "Font",
                        description = "Change the editors font",
                        onClick = { viewModel.openFontDialog() },
                        trailing = { ValueWithChevron(fontDisplayName, fontFamily) },
                    )
                    HorizontalDivider(thickness = 0.4.dp, color = DividerDefaults.color.copy(0.8f))
                    SectionHeader("Editor")
                    SettingsRow(
                        label = "Autofocus editor",
                        description = "Focus the editor automatically",
                        onClick = { viewModel.toggleAutofocus() },
                        trailing = {
                            Switch(
                                checked = viewModel.autoFocus, onCheckedChange = null
                            )
                        })
                    SettingsRow(
                        label = "Syntax highlighting",
                        description = "Show language colors",
                        onClick = { viewModel.toggleSyntaxHighlight() },
                        trailing = {
                            Switch(
                                checked = viewModel.syntaxHighlight, onCheckedChange = null
                            )
                        },
                    )
                    SettingsRow(
                        label = "Line numbers",
                        description = "Show number line",
                        onClick = { viewModel.toggleLineNumbers() },
                        trailing = {
                            Switch(
                                checked = viewModel.lineNumbers, onCheckedChange = null
                            )
                        },
                    )
                    SettingsRow(
                        label = "Minimap",
                        description = "Show minimap on end",
                        onClick = { viewModel.toggleMinimap() },
                        trailing = { Switch(checked = viewModel.miniMap, onCheckedChange = null) },
                    )
                    SettingsRow(
                        label = "Minimap blocks",
                        description = "Show blocks in minimap instead of text",
                        enabled = viewModel.miniMap,
                        onClick = { viewModel.toggleMinimapBlocks() },
                        trailing = {
                            Switch(
                                checked = viewModel.miniMapBlocks,
                                onCheckedChange = null,
                                enabled = viewModel.miniMap
                            )
                        },
                    )
                    SettingsRow(
                        label = "Symbol bar",
                        description = "Show bar on bottom",
                        onClick = { viewModel.toggleSymbolBar() },
                        trailing = {
                            Switch(
                                checked = viewModel.symbolBar, onCheckedChange = null
                            )
                        },
                    )
                    SettingsRow(
                        label = "Invisible characters",
                        description = "Show hinted glyphs",
                        onClick = { viewModel.toggleInvisibleChars() },
                        trailing = {
                            Switch(
                                checked = viewModel.invisibleChars, onCheckedChange = null
                            )
                        },
                    )
                    SettingsRow(
                        label = "Tabs",
                        description = "Show tabs at the top for opened files",
                        onClick = { viewModel.toggleTabs() },
                        trailing = {
                            Switch(
                                checked = viewModel.documentTabs, onCheckedChange = null
                            )
                        })
                }
                SimpleShadow()
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsRow(
    label: String,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.38f
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
        ),
        headlineContent = { Text(label) },
        supportingContent = { Text(description) },
        trailingContent = trailing,
    )
}

@Composable
private fun ValueWithChevron(value: String, fontFamily: FontFamily? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = fontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(20.dp),
        )
    }
}
