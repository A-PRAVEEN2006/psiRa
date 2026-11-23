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

# Keep all PsiRa application classes & models intact
-keep class com.project1.psira.** { *; }
-keepclassmembers class com.project1.psira.** { *; }
