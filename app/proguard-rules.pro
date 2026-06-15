# Keep the BackupWorker so it can be called by WorkManager
-keep class com.auto.odo.core.background.BackupWorker { *; }

# Keep Google API Client classes
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod
-dontwarn com.google.api.client.**