package com.auto.odo.presentation.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auto.odo.presentation.viewmodel.BackupViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    viewModel: BackupViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backupList by viewModel.backupList.collectAsStateWithLifecycle()
    val isLoadingList by viewModel.isLoadingList.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.appdata"))
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }
    var showBackupDialog by remember { mutableStateOf(false) }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            if (task.isSuccessful) {
                when (pendingAction) {
                    PendingAction.BACKUP -> viewModel.triggerManualBackup()
                    PendingAction.RESTORE -> viewModel.triggerRestore()
                    PendingAction.LIST_AND_RESTORE -> {
                        viewModel.listBackups()
                        showBackupDialog = true
                    }
                    null -> { /* no-op */ }
                }
            }
        }
        pendingAction = null
    }

    LaunchedEffect(uiState.backupMessage) {
        uiState.backupMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    // ── Backup selection dialog ──
if (showBackupDialog) {
    AlertDialog(
        onDismissRequest = { showBackupDialog = false },
        title = { Text("Select a Backup") },
        text = {
            if (isLoadingList) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (backupList.isEmpty()) {
                Text("No backups found.")
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    LazyColumn(modifier = Modifier.padding(8.dp)) {
                        items(backupList) { backup ->
                            ListItem(
                                headlineContent = { Text(backup.displayName, style = MaterialTheme.typography.bodyLarge) },
                                supportingContent = { Text(backup.formattedDate, style = MaterialTheme.typography.bodySmall) },
                                modifier = Modifier.clickable {
                                    viewModel.triggerRestore(backup.id)
                                    showBackupDialog = false
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { showBackupDialog = false }) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Backup & Sync", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CloudUpload,
                contentDescription = "Cloud Backup",
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Google Drive Backup",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Securely backup your database to a hidden, app-specific folder in your Google Drive. Your data remains private and will be restored if you reinstall the app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Last backup card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Last Backup", style = MaterialTheme.typography.labelMedium)
                    Text(uiState.lastBackupTime, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Backup Button ──
            Button(
                onClick = {
                    val account = GoogleSignIn.getLastSignedInAccount(context)
                    if (account != null && GoogleSignIn.hasPermissions(account, Scope("https://www.googleapis.com/auth/drive.appdata"))) {
                        viewModel.triggerManualBackup()
                    } else {
                        pendingAction = PendingAction.BACKUP
                        signInLauncher.launch(googleSignInClient.signInIntent)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !uiState.isBackingUp
            ) {
                if (uiState.isBackingUp) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Syncing...")
                } else {
                    Text("Backup Now", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Restore Latest Button ──
            Button(
                onClick = {
                    val account = GoogleSignIn.getLastSignedInAccount(context)
                    if (account != null && GoogleSignIn.hasPermissions(account, Scope("https://www.googleapis.com/auth/drive.appdata"))) {
                        viewModel.triggerRestore()
                    } else {
                        pendingAction = PendingAction.RESTORE
                        signInLauncher.launch(googleSignInClient.signInIntent)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                enabled = !uiState.isBackingUp
            ) {
                Text("Restore Latest Backup", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Restore from Backup... Button (opens list dialog) ──
            Button(
                onClick = {
                    val account = GoogleSignIn.getLastSignedInAccount(context)
                    if (account != null && GoogleSignIn.hasPermissions(account, Scope("https://www.googleapis.com/auth/drive.appdata"))) {
                        viewModel.listBackups()
                        showBackupDialog = true
                    } else {
                        pendingAction = PendingAction.LIST_AND_RESTORE
                        signInLauncher.launch(googleSignInClient.signInIntent)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                enabled = !uiState.isBackingUp
            ) {
                Text("Restore from Backup…", fontWeight = FontWeight.Bold)
            }
        }
    }
}

enum class PendingAction { BACKUP, RESTORE, LIST_AND_RESTORE }