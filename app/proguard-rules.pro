# Add project-specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep class names of the app package
-keep class com.example.voicetranslator.** { *; }

# Keep okhttp / kotlinx / androidX classes if needed
-keep class okhttp3.** { *; }
-keep class org.json.** { *; }

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you do not need to use reflection, keep this turned off.
#-dontwarn org.slf4j.**
