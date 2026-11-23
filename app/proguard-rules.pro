# ProGuard & R8 Obfuscation Rules for PsiRa

# Hide original source file names and line numbers
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# Preserve annotations and generic signatures for reflection
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Firebase Database, Auth & Messaging reflection
-keepattributes *Annotation*
-dontwarn com.google.firebase.**
-keep class com.google.firebase.** { *; }

# Keep data models used with Firebase RTDB serialization
-keepclassmembers class com.project1.psira.Message { *; }
-keepclassmembers class com.project1.psira.Group { *; }
-keepclassmembers class com.project1.psira.Note { *; }
-keepclassmembers class com.project1.psira.Report { *; }
-keepclassmembers class com.project1.psira.BluetoothMessage { *; }
-keepclassmembers class com.project1.psira.DecoyNoteItem { *; }

# Keep any fields/methods annotated with Firebase PropertyName
-keepclassmembers class * {
    @com.google.firebase.database.PropertyName <fields>;
    @com.google.firebase.database.PropertyName <methods>;
}

# WebRTC JNI native bindings (critical for voice/video calls)
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
-keepclasseswithmembernames class * {
    native <methods>;
}

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-dontwarn com.bumptech.glide.**

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Obfuscation optimizations
-repackageclasses 'com.project1.psira.internal'
-allowaccessmodification
