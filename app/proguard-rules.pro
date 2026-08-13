# Keep the BackupWorker so it can be called by WorkManager
-keep class com.auto.odo.core.background.BackupWorker { *; }

# Keep Google API Client classes
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod
-dontwarn com.google.api.client.**

# Apache HttpClient (transitively pulled in by google-api-client) references
# javax.naming.* / org.ietf.jgss.* which don't exist on Android. These code
# paths are never exercised at runtime — safe to silence.
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
-dontwarn org.apache.http.**