package com.auto.odo.core.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Collections
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val account = GoogleSignIn.getLastSignedInAccount(applicationContext) ?: return Result.failure()
        
        val credential = GoogleAccountCredential.usingOAuth2(
            applicationContext,
            Collections.singleton("https://www.googleapis.com/auth/drive.appdata")
        ).setSelectedAccount(account.account)

        // FIX: Swapped AndroidHttp for the built-in NetHttpTransport
        val driveService = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("OdoAuto").build()

        return try {
            val databaseName = "odo_database"
            val dbFile = applicationContext.getDatabasePath(databaseName)
            val shmFile = applicationContext.getDatabasePath("$databaseName-shm")
            val walFile = applicationContext.getDatabasePath("$databaseName-wal")

            val zipFile = java.io.File(applicationContext.cacheDir, "odo_backup.zip")
            
            // Create the ZIP archive containing the active database parts
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                listOf(dbFile, shmFile, walFile).forEach { file ->
                    if (file.exists()) {
                        zos.putNextEntry(ZipEntry(file.name))
                        FileInputStream(file).use { input -> input.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }

            // Target Google Drive's appDataFolder hidden directory
            val metadata = File().apply {
                name = "odo_backup_${System.currentTimeMillis()}.zip"
                parents = Collections.singletonList("appDataFolder")
            }

            val mediaContent = FileContent("application/zip", zipFile)
            driveService.files().create(metadata, mediaContent).execute()
            
            zipFile.delete() // Clean cache file after sync completes
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}