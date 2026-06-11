# 1. Jetpack Compose Safeguards
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# 2. Android Architecture & Lifecycle Keep Rules
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# 3. Suppress benign warnings from dependencies (keeps build logs clean)
-dontwarn dontwarn class **