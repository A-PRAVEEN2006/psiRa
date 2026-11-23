# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# Preserve Firebase data models and reflection mapping
-keepattributes *Annotation*
-keepattributes Signature
-keepclassmembers class * {
    @com.google.firebase.database.IgnoreExtraProperties *;
    @com.google.firebase.database.PropertyName *;
}
-keep class com.project1.psira.** { *; }
-keep class com.google.firebase.** { *; }