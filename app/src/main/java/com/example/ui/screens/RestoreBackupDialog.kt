package com.example.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun RestoreBackupDialog(
    onRestore: () -> Unit,
    onDiscard: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        icon = {
            Icon(
                imageVector = Icons.Filled.Restore,
                contentDescription = "Restore Backup",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { 
            Text(
                "Restore Backup?",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            ) 
        },
        text = { 
            Text(
                "We found a backup from a previous installation. Would you like to restore your library and progress, or start completely fresh?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ) 
        },
        confirmButton = {
            Button(onClick = onRestore) {
                Text("Restore Backup")
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) {
                Text("Discard & Start Fresh", color = MaterialTheme.colorScheme.error)
            }
        }
    )
}
