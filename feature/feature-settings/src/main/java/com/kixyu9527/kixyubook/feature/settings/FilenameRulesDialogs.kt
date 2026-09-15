package com.kixyu9527.kixyubook.feature.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.withContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kixyu9527.kixyubook.core.common.model.FilenameRuleSpec
import com.kixyu9527.kixyubook.core.common.model.FilenameSegmentRole
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuActionDialog
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPopupBackdropEffect
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuDivider
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSwitch
import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols
import com.kixyu9527.kixyubook.core.reader.engine.LocalMetadata
import java.util.UUID

/** Reads only the display name of a picked document; the file itself is never opened here. */
internal fun Context.documentDisplayName(uri: Uri): String? = runCatching {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()

/**
 * Rules produced from a real file name (with edit and enable/disable) plus the legacy regex rules
 * in their own section. The default list never shows a regex.
 */
@Composable
internal fun FilenameRulesManagerDialog(
    specs: List<FilenameRuleSpec>,
    advancedRules: List<String>,
    onAdd: () -> Unit,
    onEditSpec: (FilenameRuleSpec) -> Unit,
    onSetSpecEnabled: (String, Boolean) -> Unit,
    onDeleteSpec: (String) -> Unit,
    onAddAdvanced: () -> Unit,
    onDeleteAdvanced: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    KixyuActionDialog(
        show = true,
        title = stringResource(R.string.settings_filename_rules),
        onDismissRequest = onDismiss,
        confirmLabel = stringResource(R.string.settings_ok),
        onConfirm = onDismiss,
        dismissLabel = null,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        backdropEffect = KixyuPopupBackdropEffect.SURFACE_ONLY,
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
        ) {
            if (specs.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_filename_rules_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                specs.forEach { spec ->
                    val match = remember(spec) { LocalMetadata.applyFilenameRuleSpec(spec, spec.sample) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(spec.sample, style = MaterialTheme.typography.bodyLarge)
                            if (match != null) {
                                Text(
                                    text = stringResource(
                                        if (spec.enabled) R.string.settings_filename_rules_preview
                                        else R.string.settings_filename_rules_disabled,
                                        match.title,
                                        match.author,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (spec.enabled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                        KixyuSwitch(
                            checked = spec.enabled,
                            onCheckedChange = { enabled -> onSetSpecEnabled(spec.id, enabled) },
                        )
                        KixyuIconButton(onClick = { onEditSpec(spec) }) {
                            Icon(KixyuSymbols.Edit, stringResource(R.string.settings_filename_rules_edit))
                        }
                        KixyuIconButton(onClick = { onDeleteSpec(spec.id) }) {
                            Icon(KixyuSymbols.DeleteOutline, stringResource(R.string.settings_filename_rules_remove))
                        }
                    }
                    KixyuDivider()
                }
            }
            TextButton(onClick = onAdd) {
                Icon(KixyuSymbols.Add, null)
                Text(stringResource(R.string.settings_filename_rules_add), Modifier.padding(start = KixyuSpacing.small))
            }
            HorizontalDivider()
            Text(
                text = stringResource(R.string.settings_filename_rules_advanced_title),
                style = MaterialTheme.typography.titleSmall,
            )
            advancedRules.forEach { rule ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(LocalMetadata.describeFilenameRule(rule), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = rule,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                    KixyuIconButton(onClick = { onDeleteAdvanced(rule) }) {
                        Icon(KixyuSymbols.DeleteOutline, stringResource(R.string.settings_filename_rules_remove))
                    }
                }
            }
            TextButton(onClick = onAddAdvanced) {
                Text(stringResource(R.string.settings_filename_rules_add_advanced))
            }
        }
    }
}

/**
 * "Pick a file, see the result, confirm": the sample is tokenized and auto-labelled, the table
 * shows what a real import would extract, presets fill common formats, extra samples can verify
 * the rule, and only a wrong result opens the per-segment adjuster.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilenameRuleEditorDialog(
    pickedSample: String?,
    initialSpec: FilenameRuleSpec? = null,
    onPickFile: () -> Unit,
    onSave: (FilenameRuleSpec) -> Unit,
    onDismiss: () -> Unit,
) {
    var sample by rememberSaveable { mutableStateOf(initialSpec?.sample.orEmpty()) }
    var separator by rememberSaveable { mutableStateOf(initialSpec?.separator) }
    var roleNames by rememberSaveable {
        mutableStateOf(initialSpec?.roles?.map { it.name }.orEmpty())
    }
    var adjusting by rememberSaveable { mutableStateOf(false) }
    var inferRoles by rememberSaveable { mutableStateOf(initialSpec == null) }
    var samples by rememberSaveable { mutableStateOf(listOf<String>()) }
    var sampleInput by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(pickedSample) {
        if (!pickedSample.isNullOrBlank()) {
            inferRoles = true
            sample = pickedSample
            separator = null
            adjusting = false
        }
    }
    val tokens = remember(sample, separator) { LocalMetadata.tokenizeFilenameSample(sample, separator) }
    LaunchedEffect(tokens, inferRoles) {
        // Preset selection and edit-mode restore carry explicit roles; only free typing infers.
        if (!inferRoles) return@LaunchedEffect
        val auto = LocalMetadata.autoAssignRoles(tokens)
        roleNames = auto.map { it?.name ?: UNASSIGNED }
    }
    val effectiveSeparator = remember(sample, separator) {
        LocalMetadata.splitFilenameSample(sample, separator)?.separator
    }
    val roles = remember(roleNames) {
        roleNames.map { name -> FilenameSegmentRole.entries.firstOrNull { it.name == name } }
    }
    val spec = remember(sample, effectiveSeparator, roleNames) {
        if (tokens.isEmpty() || roles.size != tokens.size || roles.any { it == null }) {
            null
        } else {
            FilenameRuleSpec(
                id = initialSpec?.id ?: UUID.randomUUID().toString(),
                sample = sample.trim(),
                separator = effectiveSeparator,
                roles = roles.filterNotNull(),
                version = initialSpec?.version ?: 1,
                enabled = initialSpec?.enabled ?: true,
            )
        }
    }
    val match = remember(spec, sample) { spec?.let { LocalMetadata.applyFilenameRuleSpec(it, sample) } }
    val ignored = tokens.mapIndexedNotNull { index, token ->
        val role = roles.getOrNull(index)
        token.text.takeIf { role == FilenameSegmentRole.IGNORE || role == FilenameSegmentRole.STATUS }
    }
    val sampleStats = remember(spec, samples) {
        spec?.let { LocalMetadata.countSpecMatches(it, samples) }
    }
    val hasTitle = roles.contains(FilenameSegmentRole.TITLE)
    val hasAuthor = roles.contains(FilenameSegmentRole.AUTHOR)

    KixyuActionDialog(
        show = true,
        title = stringResource(
            if (initialSpec != null) R.string.settings_filename_rules_edit
            else R.string.settings_filename_rules_pick_title,
        ),
        onDismissRequest = onDismiss,
        confirmLabel = stringResource(R.string.settings_filename_rules_confirm),
        confirmEnabled = spec != null && match != null,
        onConfirm = { spec?.let(onSave) },
        dismissLabel = stringResource(R.string.settings_cancel),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        backdropEffect = KixyuPopupBackdropEffect.SURFACE_ONLY,
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
        ) {
            Text(
                text = stringResource(R.string.settings_filename_rules_preset_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
                LocalMetadata.FilenameRulePresets.forEach { preset ->
                    FilterChip(
                        selected = sample == preset.sample,
                        onClick = {
                            inferRoles = false
                            sample = preset.sample
                            separator = preset.separator
                            roleNames = preset.roles.map { it.name }
                            adjusting = false
                        },
                        label = { Text(stringResource(presetLabelRes(preset.id))) },
                    )
                }
            }
            TextButton(onClick = onPickFile) { Text(stringResource(R.string.settings_filename_rules_pick_file)) }
            OutlinedTextField(
                value = sample,
                onValueChange = {
                    inferRoles = true
                    sample = it
                    separator = null
                    adjusting = false
                },
                label = { Text(stringResource(R.string.settings_filename_rules_example_label)) },
                placeholder = { Text(stringResource(R.string.settings_filename_rules_example_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (tokens.isEmpty()) {
                if (sample.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.settings_filename_rules_no_split),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.settings_filename_rules_result_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (match != null) {
                    Text(
                        text = stringResource(R.string.settings_filename_rules_preview, match.title, match.author),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (ignored.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.settings_filename_rules_result_ignored,
                                ignored.joinToString(", "),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    val fileExtension = sample.substringAfterLast('.', "").trim()
                    if (fileExtension.isNotEmpty() && fileExtension != sample) {
                        Text(
                            text = stringResource(
                                R.string.settings_filename_rules_result_type,
                                fileExtension.uppercase(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else {
                    Text(
                        text = stringResource(
                            if (!hasTitle || !hasAuthor) R.string.settings_filename_rules_incomplete
                            else R.string.settings_filename_rules_guidance,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = { adjusting = !adjusting }) {
                    Text(stringResource(R.string.settings_filename_rules_adjust))
                }
                if (adjusting) {
                    Text(
                        text = stringResource(R.string.settings_filename_rules_segment_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
                        tokens.forEachIndexed { index, token ->
                            FilterChip(
                                selected = roles.getOrNull(index) != null,
                                onClick = { roleNames = roleNames.cycleRole(index) },
                                label = { Text("${token.text}·${roleLabel(roleNames.getOrNull(index))}") },
                            )
                        }
                    }
                    if (effectiveSeparator != null) {
                        Text(
                            text = stringResource(R.string.settings_filename_rules_separator_pick),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
                            LocalMetadata.availableSampleSeparators(sample).forEach { candidate ->
                                FilterChip(
                                    selected = effectiveSeparator == candidate,
                                    onClick = {
                                        separator = candidate
                                        adjusting = true
                                    },
                                    label = {
                                        Text(
                                            if (candidate == " ") stringResource(R.string.settings_filename_rules_separator_space)
                                            else candidate,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = sampleInput,
                        onValueChange = { sampleInput = it },
                        label = { Text(stringResource(R.string.settings_filename_rules_verify_label)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = sampleInput.isNotBlank(),
                        onClick = {
                            if (sampleInput.isNotBlank()) samples = samples + sampleInput.trim()
                            sampleInput = ""
                        },
                    ) { Text(stringResource(R.string.settings_filename_rules_add_sample)) }
                }
                if (samples.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
                        samples.forEachIndexed { index, extra ->
                            FilterChip(
                                selected = false,
                                onClick = { samples = samples.filterIndexed { i, _ -> i != index } },
                                label = {
                                    val ok = remember(spec, extra) {
                                        spec?.let { LocalMetadata.applyFilenameRuleSpec(it, extra) } != null
                                    }
                                    Text((if (ok) "✓ " else "✕ ") + extra)
                                },
                            )
                        }
                    }
                    sampleStats?.let { (matched, missed) ->
                        Text(
                            text = stringResource(R.string.settings_filename_rules_samples_summary, matched, missed),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (missed == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/** Keeps the raw-regex escape hatch for names the point-and-tap flow cannot express. */
@Composable
internal fun AdvancedRegexRuleDialog(
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pattern by rememberSaveable { mutableStateOf("") }
    var sample by rememberSaveable { mutableStateOf("") }
    // Validation runs off the composition thread and inputs are length-bounded: a pathological
    // pattern must not freeze a frame (a running Java match itself cannot be interrupted).
    var validation by remember { mutableStateOf<LocalMetadata.FilenameRuleValidation?>(null) }
    LaunchedEffect(pattern, sample) {
        validation = if (pattern.isBlank() || pattern.length > MAX_REGEX_PATTERN_CHARS || sample.length > MAX_REGEX_SAMPLE_CHARS) {
            null
        } else {
            withContext(kotlinx.coroutines.Dispatchers.Default) {
                LocalMetadata.validateFilenameRule(pattern, sample)
            }
        }
    }
    KixyuActionDialog(
        show = true,
        title = stringResource(R.string.settings_filename_rules_add_advanced),
        onDismissRequest = onDismiss,
        confirmLabel = stringResource(R.string.settings_filename_rules_add),
        confirmEnabled = validation is LocalMetadata.FilenameRuleValidation.Valid,
        onConfirm = { onSave(pattern.trim()) },
        dismissLabel = stringResource(R.string.settings_cancel),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        backdropEffect = KixyuPopupBackdropEffect.SURFACE_ONLY,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
            Text(
                text = stringResource(R.string.settings_filename_rules_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = pattern,
                onValueChange = { pattern = it },
                label = { Text(stringResource(R.string.settings_filename_rules_custom_label)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = sample,
                onValueChange = { sample = it },
                label = { Text(stringResource(R.string.settings_filename_rules_example_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            validation?.let { result ->
                val (message, isError) = when (result) {
                    is LocalMetadata.FilenameRuleValidation.Valid -> stringResource(
                        R.string.settings_filename_rules_preview,
                        result.title,
                        result.author,
                    ) to false
                    is LocalMetadata.FilenameRuleValidation.Invalid -> stringResource(
                        when (result.reason) {
                            LocalMetadata.FilenameRuleError.SYNTAX -> R.string.settings_filename_rules_error_syntax
                            LocalMetadata.FilenameRuleError.MISSING_GROUPS -> R.string.settings_filename_rules_error_groups
                            LocalMetadata.FilenameRuleError.NO_MATCH -> R.string.settings_filename_rules_error_no_match
                            LocalMetadata.FilenameRuleError.EMPTY_TITLE -> R.string.settings_filename_rules_error_empty_title
                            LocalMetadata.FilenameRuleError.EMPTY_AUTHOR -> R.string.settings_filename_rules_error_empty_author
                        },
                    ) to true
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private const val UNASSIGNED = "UNASSIGNED"
private const val MAX_REGEX_PATTERN_CHARS = 300
private const val MAX_REGEX_SAMPLE_CHARS = 300

private fun presetLabelRes(id: String): Int = when (id) {
    "title_dash_author" -> R.string.settings_filename_rules_preset_title_author
    "author_dash_title" -> R.string.settings_filename_rules_preset_author_title
    "by" -> R.string.settings_filename_rules_preset_by
    "bracketed_author" -> R.string.settings_filename_rules_preset_bracketed
    else -> R.string.settings_filename_rules_preset_markers
}

private fun List<String>.cycleRole(index: Int): List<String> {
    val order = listOf(
        UNASSIGNED,
        FilenameSegmentRole.TITLE.name,
        FilenameSegmentRole.AUTHOR.name,
        FilenameSegmentRole.STATUS.name,
        FilenameSegmentRole.IGNORE.name,
    )
    var cursor = order.indexOf(getOrElse(index) { UNASSIGNED }).takeIf { it >= 0 } ?: 0
    repeat(order.size) {
        cursor = (cursor + 1) % order.size
        val next = order[cursor]
        val takenElsewhere = (next == FilenameSegmentRole.AUTHOR.name ||
            next == FilenameSegmentRole.STATUS.name) &&
            indices.any { it != index && getOrNull(it) == next }
        if (!takenElsewhere) return toMutableList().also { it[index] = next }
    }
    return this
}

@Composable
private fun roleLabel(name: String?): String = when (name) {
    FilenameSegmentRole.TITLE.name -> stringResource(R.string.settings_filename_rules_role_title)
    FilenameSegmentRole.AUTHOR.name -> stringResource(R.string.settings_filename_rules_role_author)
    FilenameSegmentRole.STATUS.name -> stringResource(R.string.settings_filename_rules_role_status)
    FilenameSegmentRole.IGNORE.name -> stringResource(R.string.settings_filename_rules_role_ignore)
    else -> stringResource(R.string.settings_filename_rules_role_unassigned)
}
