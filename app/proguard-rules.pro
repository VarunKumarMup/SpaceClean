# ── SpaceClean R8 / ProGuard rules ──────────────────────────────────────────
# Jetpack Compose, AndroidX, and Media3 ship their own consumer rules, so very
# little is needed here. The app uses no reflection, serialization, or JNI.

# Keep readable stack traces (useful even though the app is offline / no crash SDK).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Compose tooling sometimes references these; harmless to keep.
-dontwarn org.jetbrains.annotations.**
