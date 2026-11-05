package com.example.fileexplorer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private val DialogShape = RoundedCornerShape(8.dp)

/** Explorer-style footer: an accent "primary" button and a plain "Cancel". */
@Composable
private fun WinButtons(
    confirm: String,
    onConfirm: () -> Unit,
    onCancel: (() -> Unit)?,
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
) {
    val c = win
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onConfirm,
            enabled = confirmEnabled,
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (destructive) Color(0xFFC42B1C) else c.accent,
                contentColor = if (destructive || !c.isDark) Color.White else Color.Black,
            ),
        ) { Text(confirm) }
        if (onCancel != null) {
            OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(4.dp)) {
                Text("Cancel", color = c.text)
            }
        }
    }
}

@Composable
fun NameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    selectBaseName: Boolean,
    requireChange: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = win
    // Like Explorer, pre-select just "photo" in "photo.jpg".
    val dot = initialName.lastIndexOf('.')
    val selEnd = if (selectBaseName && dot > 0) dot else initialName.length
    var value by remember { mutableStateOf(TextFieldValue(initialName, TextRange(0, selEnd))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val name = value.text.trim()
    val error = when {
        name.any { it in "/\\:*?\"<>|" } -> "A file name can't contain any of the following characters:  \\ / : * ? \" < > |"
        name == "." || name == ".." -> "Invalid name"
        else -> null
    }
    val valid = name.isNotEmpty() && error == null && (!requireChange || name != initialName)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = DialogShape,
        containerColor = c.chrome,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                isError = error != null,
                supportingText = if (error != null) {
                    { Text(error) }
                } else null,
                shape = RoundedCornerShape(4.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.accent,
                    unfocusedBorderColor = c.fieldBorder,
                    focusedContainerColor = c.field,
                    unfocusedContainerColor = c.field,
                    cursorColor = c.accent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm(name) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { WinButtons(confirmLabel, { onConfirm(name) }, onDismiss, confirmEnabled = valid) },
    )
}

@Composable
fun DeleteDialog(items: List<FileItem>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = win
    val single = items.singleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = DialogShape,
        containerColor = c.chrome,
        title = { Text(if (single?.isDirectory == true) "Delete Folder" else if (single != null) "Delete File" else "Delete Multiple Items") },
        text = {
            Text(
                when {
                    single?.isDirectory == true -> "Are you sure you want to permanently delete this folder and everything in it?\n\n${single?.name}"
                    single != null -> "Are you sure you want to permanently delete this file?\n\n${single?.name}"
                    else -> "Are you sure you want to permanently delete these ${items.size} items?"
                },
                color = c.text,
            )
        },
        confirmButton = { WinButtons("Delete", onConfirm, onDismiss, destructive = true) },
    )
}

@Composable
fun PropertiesDialog(item: FileItem, location: String, onDismiss: () -> Unit) {
    val c = win
    var stats by remember(item.path) { mutableStateOf<Triple<Long, Int, Int>?>(null) }
    if (item.isDirectory) {
        LaunchedEffect(item.path) {
            stats = withContext(Dispatchers.IO) {
                var bytes = 0L
                var files = 0
                var folders = 0
                for (f in item.file.walkTopDown()) {
                    ensureActive()
                    if (f == item.file) continue
                    if (f.isDirectory) folders++ else { files++; bytes += f.length() }
                }
                Triple(bytes, files, folders)
            }
        }
    }
    val modified = remember(item.lastModified) {
        DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.MEDIUM).format(Date(item.lastModified))
    }
    val size = if (item.isDirectory) stats?.first else item.size

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = DialogShape,
        containerColor = c.chrome,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ItemIcon(item, 32.dp, thumbnails = true)
                Spacer(Modifier.width(12.dp))
                Text("${item.name} Properties", style = MaterialTheme.typography.titleMedium, maxLines = 2)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PropRow("Type:", typeName(item))
                PropRow("Location:", location)
                PropRow("Size:", size?.let { "${humanSize(it)} (${exactBytes(it)})" } ?: "Calculating…")
                if (item.isDirectory) {
                    PropRow("Contains:", stats?.let { "${it.second} Files, ${it.third} Folders" } ?: "Calculating…")
                }
                HorizontalDivider(color = c.divider)
                PropRow("Modified:", modified)
                PropRow(
                    "Attributes:",
                    listOfNotNull(
                        "Read-only".takeIf { !item.file.canWrite() },
                        "Hidden".takeIf { item.file.isHidden },
                    ).joinToString().ifEmpty { "None" },
                )
            }
        },
        confirmButton = { WinButtons("OK", onDismiss, null) },
    )
}

@Composable
private fun PropRow(label: String, value: String) {
    val c = win
    Row {
        Text(label, color = c.textSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
        Text(value, color = c.text, style = MaterialTheme.typography.bodyMedium)
    }
}
