package com.auto.odo.presentation.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.auto.odo.core.background.BackupWorker
import com.auto.odo.data.AppDatabase
import com.auto.odo.domain.model.BackupFileInfo
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.cancel
import java.io.FileOutputStream
import java.io.FileInputStream
import java.io.File
import java.util.*
import javax.inject.Inject
import android.content.Context
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter



data class BackupUiState(
    val isBackingUp: Boolean = false,
    val lastBackupTime: String = "Never",
    val backupMessage: String? = null
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private val _backupList = MutableStateFlow<List<BackupFileInfo>>(emptyList())
    val backupList: StateFlow<List<BackupFileInfo>> = _backupList.asStateFlow()

    private val _isLoadingList = MutableStateFlow(false)
    val isLoadingList: StateFlow<Boolean> = _isLoadingList.asStateFlow()

    private val workManager = WorkManager.getInstance(application)

private val prefs = application.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)

init {
    // Load persisted last backup time
    val savedMillis = prefs.getLong("last_backup_millis", 0L)
    if (savedMillis > 0) {
        _uiState.update { it.copy(lastBackupTime = formatTimestamp(savedMillis)) }
    }
}

// Extracts epoch millis from a filename like "odo_backup_1749999999999.zip"
private fun extractTimestampFromFileName(name: String): Long? {
    val regex = Regex("odo_backup_(\\d{13})\\.zip")
    val match = regex.find(name)
    return match?.groupValues?.get(1)?.toLongOrNull()
}

// Overloaded formatTimestamp for Long
private fun formatTimestamp(millis: Long): String {
    val instant = Instant.ofEpochMilli(millis)
    val now = Instant.now()
    return if (Duration.between(instant, now).toMinutes() < 1) {
        "Just now"
    } else {
        val formatter = DateTimeFormatter
            .ofPattern("d MMM yyyy, HH:mm")
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    }
}

private fun saveLastBackupTime() {
    val now = System.currentTimeMillis()
    prefs.edit().putLong("last_backup_millis", now).apply()
    _uiState.update { it.copy(lastBackupTime = formatTimestamp(now)) }
}

    // ---------- Backup ----------
    fun triggerManualBackup() {
        _uiState.update { it.copy(isBackingUp = true, backupMessage = "Starting backup...") }

        val backupRequest = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()

        workManager.enqueueUniqueWork("DriveManualBackup", ExistingWorkPolicy.REPLACE, backupRequest)

        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(backupRequest.id)
                .collect { workInfo ->
                    when (workInfo?.state) {
                        WorkInfo.State.SUCCEEDED -> {
    _uiState.update { it.copy(isBackingUp = false, backupMessage = "Backup successful!") }
    saveLastBackupTime()   // persist & display formatted time
    cleanupOldBackups()
    currentCoroutineContext().cancel()
}
                        WorkInfo.State.FAILED -> {
                            _uiState.update { it.copy(isBackingUp = false, backupMessage = "Backup failed.") }
                            currentCoroutineContext().cancel()
                        }
                        WorkInfo.State.CANCELLED -> {
                            _uiState.update { it.copy(isBackingUp = false, backupMessage = "Backup cancelled.") }
                            currentCoroutineContext().cancel()
                        }
                        else -> {}
                    }
                }
        }
    }

    // ---------- List backups ----------
    fun listBackups() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingList.value = true
            try {
                val account = GoogleSignIn.getLastSignedInAccount(application)
                if (account == null) {
                    _backupList.value = emptyList()
                    return@launch
                }

                val credential = GoogleAccountCredential.usingOAuth2(
                    application,
                    Collections.singleton("https://www.googleapis.com/auth/drive.appdata")
                ).setSelectedAccount(account.account)

                val driveService = Drive.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("OdoAuto").build()

                val result = driveService.files().list()
                    .setSpaces("appDataFolder")
                    .setOrderBy("createdTime desc")
                    .execute()

_backupList.value = result.files.map { file ->
    val rawName = file.name ?: "Unnamed backup"
    // Try to parse date from Drive's modifiedTime first, fallback to extracting from filename
    val dateStr = file.modifiedTime?.toStringRfc3339()?.let { formatModifiedTime(it) }
        ?: extractTimestampFromFileName(rawName)?.let { formatTimestamp(it) }
        ?: "Unknown date"
    BackupFileInfo(
        id = file.id,
        displayName = "Backup – $dateStr",
        formattedDate = dateStr
    )
}
            } catch (e: Exception) {
                _backupList.value = emptyList()
                _uiState.update { it.copy(backupMessage = "Failed to fetch backups: ${e.localizedMessage}") }
            } finally {
                _isLoadingList.value = false
            }
        }
    }

    // ---------- Restore (with optional fileId) ----------
    fun triggerRestore(backupFileId: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBackingUp = true, backupMessage = "Restoring...") }

            val account = GoogleSignIn.getLastSignedInAccount(application)
            if (account == null) {
                _uiState.update { it.copy(isBackingUp = false, backupMessage = "Please sign in first.") }
                return@launch
            }

            val credential = GoogleAccountCredential.usingOAuth2(
                application,
                Collections.singleton("https://www.googleapis.com/auth/drive.appdata")
            ).setSelectedAccount(account.account)

            val driveService = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("OdoAuto").build()

            try {
                val fileIdToRestore = backupFileId ?: run {
                    val files = driveService.files().list()
                        .setSpaces("appDataFolder")
                        .setOrderBy("createdTime desc")
                        .setPageSize(1)
                        .execute()
                    files.files.firstOrNull()?.id ?: throw Exception("No backup found")
                }

                val zipFile = File(application.cacheDir, "restore.zip")
                driveService.files().get(fileIdToRestore)
                    .executeMediaAndDownloadTo(FileOutputStream(zipFile))

                AppDatabase.getDatabase(application).close()
                AppDatabase.INSTANCE = null

                val dbDir = application.getDatabasePath("odo_database").parentFile!!
                listOf("odo_database", "odo_database-shm", "odo_database-wal").forEach { name ->
                    val f = File(dbDir, name)
                    if (f.exists()) f.delete()
                }

                java.util.zip.ZipInputStream(java.io.FileInputStream(zipFile)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        FileOutputStream(File(dbDir, entry.name)).use { zis.copyTo(it) }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                zipFile.delete()

                withContext(Dispatchers.Main) {
                    val restartIntent = application.packageManager.getLaunchIntentForPackage(application.packageName)
                    restartIntent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    application.startActivity(restartIntent)
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isBackingUp = false, backupMessage = "Restore failed: ${e.localizedMessage}") }
            }
        }
    }

    // ---------- Delete a single backup ----------
    fun deleteBackup(fileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val account = GoogleSignIn.getLastSignedInAccount(application) ?: return@launch
                val credential = GoogleAccountCredential.usingOAuth2(application,
                    Collections.singleton("https://www.googleapis.com/auth/drive.appdata")
                ).setSelectedAccount(account.account)

                val driveService = Drive.Builder(
                    NetHttpTransport(), GsonFactory.getDefaultInstance(), credential
                ).setApplicationName("OdoAuto").build()

                driveService.files().delete(fileId).execute()
                listBackups()
            } catch (e: Exception) {
                _uiState.update { it.copy(backupMessage = "Failed to delete backup: ${e.localizedMessage}") }
            }
        }
    }

    // ---------- Keep only last N backups ----------
    fun cleanupOldBackups(keepCount: Int = 5) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val account = GoogleSignIn.getLastSignedInAccount(application) ?: return@launch
                val credential = GoogleAccountCredential.usingOAuth2(application,
                    Collections.singleton("https://www.googleapis.com/auth/drive.appdata")
                ).setSelectedAccount(account.account)

                val driveService = Drive.Builder(
                    NetHttpTransport(), GsonFactory.getDefaultInstance(), credential
                ).setApplicationName("OdoAuto").build()

                val result = driveService.files().list()
                    .setSpaces("appDataFolder")
                    .setOrderBy("createdTime desc")
                    .execute()
                result.files.drop(keepCount).forEach { file ->
                    driveService.files().delete(file.id).execute()
                }
            } catch (_: Exception) { }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(backupMessage = null) }
    }

    // ---------- Helper for date formatting ----------
    private fun formatModifiedTime(rfc3339: String?): String {
        if (rfc3339.isNullOrBlank()) return "Unknown"
        return try {
            val instant = java.time.Instant.parse(rfc3339)
            val formatter = java.time.format.DateTimeFormatter
                .ofPattern("d MMM yyyy, HH:mm")
                .withZone(java.time.ZoneId.systemDefault())
            formatter.format(instant)
        } catch (e: Exception) {
            "Unknown"
        }
    }
}