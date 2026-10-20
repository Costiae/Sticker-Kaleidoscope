package app.stickerkaleidoscope

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewSidebar
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import app.stickerkaleidoscope.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import app.stickerkaleidoscope.library.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class LibrarySection(val title: StringResource, val filter: LibraryFilter) {
    ALL(Res.string.section_all, LibraryFilter.ALL),
    FAVORITES(Res.string.section_favorites, LibraryFilter.FAVORITES),
    UNCATEGORIZED(Res.string.section_uncategorized, LibraryFilter.UNCATEGORIZED),
    TRASH(Res.string.section_trash, LibraryFilter.TRASH)
}

@Composable
fun App(controller: LibraryController, titleBar: @Composable () -> Unit = {}) {
    val library by controller.state.collectAsState()
    LaunchedEffect(controller) { controller.refresh() }
    var query by remember { mutableStateOf("") }
    var section by remember { mutableStateOf(LibrarySection.ALL) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    val items = remember(library.items, section, query) { library.items.matching(query, section.filter) }
    LaunchedEffect(items.map { it.id }) {
        selectedIds = selectedIds.intersect(items.map { it.id }.toSet())
        if (selectedId !in selectedIds) selectedId = selectedIds.firstOrNull()
    }
    val selectedMeme = items.find { it.id == selectedId }
    var leftWidth by remember { mutableStateOf(208f) }
    var rightWidth by remember { mutableStateOf(260f) }
    var showNavigation by remember { mutableStateOf(true) }
    var showLibrary by remember { mutableStateOf(true) }
    var showProperties by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var showRestartNotice by remember { mutableStateOf(false) }
    LaunchedEffect(showRestartNotice) {
        if (showRestartNotice) {
            delay(3500)
            showRestartNotice = false
        }
    }
    val visibleCount = listOf(showNavigation, showLibrary, showProperties).count { it }

    var fontChoice by remember { mutableStateOf(AppPreferences.font) }
    var fontSizeChoice by remember { mutableStateOf(AppPreferences.fontSize) }
    var fontWeightChoice by remember { mutableStateOf(AppPreferences.fontWeight) }
    val baseTypography = Typography()
    val family = if (fontChoice == "mono") FontFamily.Monospace else FontFamily.SansSerif
    val size = when (fontSizeChoice) { "small" -> 0.9f; "large" -> 1.15f; else -> 1f }
    val weight = when (fontWeightChoice) { "light" -> FontWeight.Light; "bold" -> FontWeight.Bold; else -> FontWeight.Normal }
    val typography = baseTypography.copy(
        bodyLarge = baseTypography.bodyLarge.copy(fontFamily = family, fontSize = (16 * size).sp, fontWeight = weight),
        bodyMedium = baseTypography.bodyMedium.copy(fontFamily = family, fontSize = (14 * size).sp, fontWeight = weight),
        labelLarge = baseTypography.labelLarge.copy(fontFamily = family, fontSize = (14 * size).sp, fontWeight = weight),
        titleMedium = baseTypography.titleMedium.copy(fontFamily = family, fontWeight = weight)
    )
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF9DBCA4)), typography = typography) {
        ImportDropHost(controller) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                titleBar()
                if (library.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (showRestartNotice) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Info, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.settings_language_restart), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { showRestartNotice = false }) { Icon(Icons.Outlined.Close, stringResource(Res.string.clear_search)) }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(Res.string.layout_title), style = MaterialTheme.typography.labelMedium)
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(Icons.Outlined.Settings, stringResource(Res.string.settings_title))
                    }
                    FilterChip(
                        selected = showNavigation,
                        onClick = { showNavigation = !showNavigation },
                        enabled = !showNavigation || visibleCount > 1,
                        label = { Text(stringResource(Res.string.pane_navigation)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ViewSidebar, null, Modifier.size(18.dp)) }
                    )
                    FilterChip(
                        selected = showLibrary,
                        onClick = { showLibrary = !showLibrary },
                        enabled = !showLibrary || visibleCount > 1,
                        label = { Text(stringResource(Res.string.pane_library)) },
                        leadingIcon = { Icon(Icons.Outlined.GridView, null, Modifier.size(18.dp)) }
                    )
                    FilterChip(
                        selected = showProperties,
                        onClick = { showProperties = !showProperties },
                        enabled = !showProperties || visibleCount > 1,
                        label = { Text(stringResource(Res.string.properties_title)) },
                        leadingIcon = { Icon(Icons.Outlined.Info, null, Modifier.size(18.dp)) }
                    )
                }
                HorizontalDivider()
            if (showSettings) {
                SettingsPage(onLanguageChange = { showRestartNotice = true }, onFontChange = { fontChoice = it }, onFontSizeChange = { fontSizeChoice = it }, onFontWeightChange = { fontWeightChoice = it })
            } else BoxWithConstraints(Modifier.weight(1f)) {
                // Reserve room for the content when the window shrinks.
                val budget = (maxWidth.value - (if (showLibrary) 360f else 0f) - (visibleCount - 1) * 8f).coerceAtLeast(0f)
                val sideCount = listOf(showNavigation, showProperties).count { it }.coerceAtLeast(1)
                val leftMin = if (showNavigation) minOf(168f, budget / sideCount) else 0f
                val rightMin = if (showProperties) minOf(200f, budget / sideCount) else 0f
                val left = if (showNavigation) leftWidth.coerceIn(leftMin, maxOf(leftMin, budget - rightMin)) else 0f
                val right = if (showProperties) rightWidth.coerceIn(rightMin, maxOf(rightMin, budget - left)) else 0f
                Row(Modifier.fillMaxSize()) {
                    if (showNavigation) {
                    Column(
                        (if (visibleCount == 1) Modifier.weight(1f) else Modifier.width(left.dp)).fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Collections, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.titleMedium)
                        }
                        Text(stringResource(Res.string.local_library), style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(20.dp))
                        val destinations = listOf(
                            LibrarySection.ALL to Icons.Outlined.PhotoLibrary,
                            LibrarySection.FAVORITES to Icons.Outlined.FavoriteBorder,
                            LibrarySection.UNCATEGORIZED to Icons.Outlined.FolderOpen,
                            LibrarySection.TRASH to Icons.Outlined.DeleteOutline
                        )
                        destinations.forEach { (destination, icon) ->
                            TextButton(
                                onClick = { section = destination },
                                modifier = Modifier.fillMaxWidth().semantics { selected = section == destination },
                                colors = ButtonDefaults.textButtonColors(
                                    containerColor = if (section == destination) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                    contentColor = if (section == destination) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(icon, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(stringResource(destination.title), modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = { showSettings = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = if (showSettings) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                contentColor = if (showSettings) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Outlined.Settings, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(Res.string.settings_title), modifier = Modifier.weight(1f))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CloudOff, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.sync_disconnected), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    }
                    if (showNavigation && visibleCount > 1) {
                    ResizeDivider(stringResource(Res.string.resize_navigation)) { delta ->
                        val maximum = maxOf(leftMin, minOf(400f, budget - (if (showLibrary) right else rightMin)))
                        leftWidth = (leftWidth.coerceIn(leftMin, maximum) + delta).coerceIn(leftMin, maximum)
                    }
                    }
                    if (showLibrary) {
                    Column(Modifier.weight(1f).fillMaxHeight().padding(top = 24.dp)) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(section.title), style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.weight(1f))
                            Text(pluralStringResource(Res.plurals.meme_count, items.size, items.size), style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.height(16.dp))
                        LibraryActions(controller, library)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text(stringResource(Res.string.search_placeholder)) },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Outlined.Close, stringResource(Res.string.clear_search))
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        }
                        if (items.isNotEmpty()) {
                            MemeGrid(items, selectedIds, selectedId, controller) { ids, focused ->
                                selectedIds = ids
                                selectedId = focused
                            }
                        } else Column(
                            Modifier.weight(1f).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                if (query.isBlank()) Icons.Outlined.PhotoLibrary else Icons.Outlined.SearchOff,
                                null, modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(if (query.isBlank()) Res.string.library_empty else Res.string.search_empty))
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(if (section == LibrarySection.TRASH) Res.string.trash_hint else Res.string.import_hint), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    }
                    if (showLibrary && showProperties) {
                    ResizeDivider(stringResource(Res.string.resize_properties)) { delta ->
                        val maximum = maxOf(rightMin, minOf(440f, budget - left))
                        rightWidth = (rightWidth.coerceIn(rightMin, maximum) - delta).coerceIn(rightMin, maximum)
                    }
                    }
                    if (showProperties) {
                    Column((if (showLibrary) Modifier.width(right.dp) else Modifier.weight(1f)).fillMaxHeight().padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Info, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.properties_title), style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(16.dp))
                        MemeProperties(selectedMeme, controller, library.busy)
                    }
                    }
                }
            }
            }
        }
        }
    }
}

@Composable
private fun SettingsPage(onLanguageChange: () -> Unit, onFontChange: (String) -> Unit, onFontSizeChange: (String) -> Unit, onFontWeightChange: (String) -> Unit) {
    var language by remember { mutableStateOf(AppPreferences.language) }
    var font by remember { mutableStateOf(AppPreferences.font) }
    var fontSize by remember { mutableStateOf(AppPreferences.fontSize) }
    var fontWeight by remember { mutableStateOf(AppPreferences.fontWeight) }
    var syncSource by remember { mutableStateOf(AppPreferences.syncSource) }
    var provider by remember { mutableStateOf(AppPreferences.modelProvider) }
    var providerUrl by remember { mutableStateOf(AppPreferences.providerUrl) }
    var providerModel by remember { mutableStateOf(AppPreferences.providerModel) }
    var providerKey by remember { mutableStateOf(AppPreferences.providerApiKey) }
    var providerStatus by remember { mutableStateOf<String?>(null) }
    var providerModels by remember { mutableStateOf(emptyList<String>()) }
    var modelsExpanded by remember { mutableStateOf(false) }
    val providerScope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(stringResource(Res.string.settings_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(Res.string.settings_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsSection(stringResource(Res.string.settings_appearance)) {
            SettingsDropdown(stringResource(Res.string.settings_language), language, listOf("system" to Res.string.settings_system, "zh" to Res.string.settings_chinese, "en" to Res.string.settings_english)) { language = it; AppPreferences.language = it; onLanguageChange() }
            Text(stringResource(Res.string.settings_language_restart), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SettingsDropdown(stringResource(Res.string.settings_font), font, listOf("system" to Res.string.settings_system, "sans" to Res.string.settings_font_sans, "mono" to Res.string.settings_font_mono)) { font = it; AppPreferences.font = it; onFontChange(it) }
            SettingsDropdown(stringResource(Res.string.settings_font_size), fontSize, listOf("small" to Res.string.settings_small, "medium" to Res.string.settings_medium, "large" to Res.string.settings_large)) { fontSize = it; AppPreferences.fontSize = it; onFontSizeChange(it) }
            SettingsDropdown(stringResource(Res.string.settings_font_weight), fontWeight, listOf("light" to Res.string.settings_light, "normal" to Res.string.settings_normal, "bold" to Res.string.settings_bold)) { fontWeight = it; AppPreferences.fontWeight = it; onFontWeightChange(it) }
        }
        SettingsSection(stringResource(Res.string.settings_sync)) {
            OutlinedTextField(syncSource, { syncSource = it; AppPreferences.syncSource = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.settings_sync_source)) }, placeholder = { Text(stringResource(Res.string.settings_sync_source_hint)) }, singleLine = true)
            Text(stringResource(Res.string.settings_not_connected), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsSection(stringResource(Res.string.settings_ai)) {
            OutlinedTextField(providerUrl, { providerUrl = it; AppPreferences.providerUrl = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.settings_provider_url)) }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(providerModel, { providerModel = it; AppPreferences.providerModel = it }, Modifier.weight(1f), label = { Text(stringResource(Res.string.settings_provider_model)) }, singleLine = true)
                Spacer(Modifier.width(8.dp))
                Button(onClick = { providerScope.launch { providerStatus = null; AiProvider.models(AiProviderConfig(providerUrl, providerModel, providerKey)).fold({ providerModels = it; modelsExpanded = it.isNotEmpty(); providerStatus = "${it.size}" }, { providerStatus = it.message ?: "Failed" }) } }) { Text(stringResource(Res.string.settings_fetch_models)) }
            }
            DropdownMenu(expanded = modelsExpanded, onDismissRequest = { modelsExpanded = false }) {
                providerModels.forEach { model -> DropdownMenuItem(text = { Text(model) }, onClick = { providerModel = model; AppPreferences.providerModel = model; modelsExpanded = false }) }
            }
            OutlinedTextField(providerKey, { providerKey = it; AppPreferences.providerApiKey = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.settings_provider_key)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { providerScope.launch { providerStatus = AiProvider.test(AiProviderConfig(providerUrl, providerModel, providerKey)).fold({ it }, { it.message ?: "Failed" }) } }) { Text(stringResource(Res.string.settings_test_provider)) }
                if (providerStatus != null) { Spacer(Modifier.width(12.dp)); Text(providerStatus!!, style = MaterialTheme.typography.bodySmall) }
            }
            Text(stringResource(Res.string.settings_future_feature), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun SettingsDropdown(label: String, value: String, options: List<Pair<String, StringResource>>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("$label: ${stringResource(options.first { it.first == value }.second)}")
            Icon(Icons.Outlined.ExpandMore, null)
        }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { (key, text) -> DropdownMenuItem(text = { Text(stringResource(text)) }, onClick = { onChange(key); expanded = false }) }
        }
    }
}

@Composable
private fun ResizeDivider(label: String, onResize: (Float) -> Unit) {
    val density = LocalDensity.current.density
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val dragged by interactions.collectIsDraggedAsState()
    val active = hovered || dragged
    Box(
        Modifier.width(8.dp).fillMaxHeight().resizeCursor().hoverable(interactions)
            .draggable(
                state = rememberDraggableState { pixels -> onResize(pixels / density) },
                orientation = Orientation.Horizontal,
                interactionSource = interactions
            )
            .semantics { contentDescription = label }
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.width(if (active) 2.dp else 1.dp).fillMaxHeight()
                .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
        )
        if (active) Box(Modifier.width(4.dp).height(32.dp).background(MaterialTheme.colorScheme.primary))
    }
}
