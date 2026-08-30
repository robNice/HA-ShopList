package de.robnice.homeshoplist.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.robnice.homeshoplist.R
import de.robnice.homeshoplist.data.backup.BackupSection
import de.robnice.homeshoplist.data.backup.SettingsBackupPayload
import de.robnice.homeshoplist.data.backup.SettingsBackupService
import de.robnice.homeshoplist.ui.util.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class BackupDialog { EXPORT_SELECTION, EXPORT_PASSWORD, IMPORT_PASSWORD, IMPORT_SELECTION }

@Composable
fun SettingsBackupSection(
    context: Context,
    onImported: (SettingsBackupPayload, Set<BackupSection>) -> Unit
) {
    val service = remember(context) { SettingsBackupService(context) }
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<BackupDialog?>(null) }
    var selected by remember { mutableStateOf(BackupSection.entries.toSet()) }
    var pendingPassword by remember { mutableStateOf<CharArray?>(null) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var importPayload by remember { mutableStateOf<SettingsBackupPayload?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(SettingsBackupService.MIME_TYPE)
    ) { uri ->
        val password = pendingPassword
        val sections = selected
        pendingPassword = null
        if (uri != null && password != null) {
            scope.launch {
                busy = true
                status = null
                try {
                    withContext(Dispatchers.IO) { service.export(uri, password, sections) }
                    status = context.getString(R.string.settings_backup_export_success)
                } catch (e: Exception) {
                    val message = context.getString(R.string.settings_backup_export_error, e.message.orEmpty())
                    status = message
                } finally {
                    password.fill('\u0000')
                    busy = false
                }
            }
        } else {
            password?.fill('\u0000')
        }
    }

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importUri = uri
            dialog = BackupDialog.IMPORT_PASSWORD
        }
    }

    Text(text = t(R.string.settings_backup_title), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        text = t(R.string.settings_backup_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(12.dp))
    Row {
        Button(
            onClick = {
                selected = BackupSection.entries.toSet()
                status = null
                errorMessage = null
                dialog = BackupDialog.EXPORT_SELECTION
            },
            enabled = !busy
        ) { Text(t(R.string.settings_backup_export)) }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(
            onClick = {
                status = null
                errorMessage = null
                openDocument.launch(arrayOf(SettingsBackupService.MIME_TYPE, "application/*", "*/*"))
            },
            enabled = !busy
        ) { Text(t(R.string.settings_backup_import)) }
    }
    status?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(16.dp))
    HorizontalDivider()

    when (dialog) {
        BackupDialog.EXPORT_SELECTION -> BackupSectionSelectionDialog(
            title = t(R.string.settings_backup_export_selection_title),
            available = BackupSection.entries.toSet(),
            selected = selected,
            onSelected = { selected = it },
            onDismiss = { dialog = null },
            onConfirm = { dialog = BackupDialog.EXPORT_PASSWORD }
        )
        BackupDialog.EXPORT_PASSWORD -> BackupPasswordDialog(
            title = t(R.string.settings_backup_export_password_title),
            confirmPassword = true,
            onDismiss = { dialog = null },
            onConfirm = { password ->
                pendingPassword = password
                dialog = null
                val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ROOT).format(Date())
                createDocument.launch("HomeShoppingList_$stamp${SettingsBackupService.FILE_EXTENSION}")
            }
        )
        BackupDialog.IMPORT_PASSWORD -> BackupPasswordDialog(
            title = t(R.string.settings_backup_import_password_title),
            confirmPassword = false,
            onDismiss = { dialog = null; importUri = null },
            onConfirm = { password ->
                val uri = importUri ?: return@BackupPasswordDialog
                dialog = null
                scope.launch {
                    busy = true
                    try {
                        val payload = withContext(Dispatchers.IO) { service.decrypt(uri, password) }
                        importPayload = payload
                        selected = payload.sections
                        dialog = BackupDialog.IMPORT_SELECTION
                    } catch (e: Exception) {
                        val message = context.getString(R.string.settings_backup_unlock_error)
                        status = message
                        errorMessage = message
                        dialog = null
                    } finally {
                        password.fill('\u0000')
                        busy = false
                    }
                }
            }
        )
        BackupDialog.IMPORT_SELECTION -> BackupSectionSelectionDialog(
            title = t(R.string.settings_backup_import_selection_title),
            available = importPayload?.sections.orEmpty(),
            selected = selected,
            onSelected = { selected = it },
            onDismiss = { dialog = null; importPayload = null; importUri = null },
            onConfirm = {
                val payload = importPayload ?: return@BackupSectionSelectionDialog
                val sectionsToImport = selected
                dialog = null
                scope.launch {
                    busy = true
                    try {
                        withContext(Dispatchers.IO) { service.import(payload, sectionsToImport) }
                        onImported(payload, sectionsToImport)
                        status = context.getString(R.string.settings_backup_import_success)
                    } catch (e: Exception) {
                        val message = context.getString(
                            R.string.settings_backup_import_error,
                            e.message.orEmpty()
                        )
                        status = message
                        errorMessage = message
                    } finally {
                        importPayload = null
                        importUri = null
                        busy = false
                    }
                }
            }
        )
        null -> Unit
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text(t(R.string.settings_backup_import)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text(t(R.string.close))
                }
            }
        )
    }
}

@Composable
private fun BackupSectionSelectionDialog(
    title: String,
    available: Set<BackupSection>,
    selected: Set<BackupSection>,
    onSelected: (Set<BackupSection>) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                BackupSection.entries.filter { it in available }.forEach { section ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = section in selected,
                            onCheckedChange = { checked ->
                                onSelected(if (checked) selected + section else selected - section)
                            }
                        )
                        Text(sectionLabel(section))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = selected.isNotEmpty()) { Text(t(R.string.next)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t(R.string.clear_completed_confirm_btn_cancel)) } }
    )
}

@Composable
private fun BackupPasswordDialog(
    title: String,
    confirmPassword: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val valid = password.isNotEmpty() && (!confirmPassword || password == confirmation)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(t(R.string.settings_backup_password_hint), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(t(R.string.settings_backup_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                if (confirmPassword) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = { Text(t(R.string.settings_backup_password_confirm)) },
                        visualTransformation = PasswordVisualTransformation(),
                        isError = confirmation.isNotEmpty() && password != confirmation,
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password.toCharArray()) }, enabled = valid) { Text(t(R.string.next)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t(R.string.clear_completed_confirm_btn_cancel)) } }
    )
}

@Composable
private fun sectionLabel(section: BackupSection): String = when (section) {
    BackupSection.CONNECTION -> t(R.string.settings_backup_section_connection)
    BackupSection.CATEGORIES -> t(R.string.settings_backup_section_categories)
    BackupSection.PRODUCT_HISTORY -> t(R.string.settings_backup_section_history)
}
