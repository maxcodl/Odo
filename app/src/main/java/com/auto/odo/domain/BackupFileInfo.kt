// com/auto/odo/domain/model/BackupFileInfo.kt
package com.auto.odo.domain.model

data class BackupFileInfo(
    val id: String,
    val displayName: String,   // e.g. "Backup – 15 Jun 2026, 18:22"
    val formattedDate: String   // e.g. "15 Jun 2026, 18:22"
)