# SportFlow ProGuard Rules
-keep class com.sportflow.app.data.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Firebase
-keep class com.google.firebase.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
