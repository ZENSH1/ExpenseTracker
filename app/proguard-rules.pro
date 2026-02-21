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
# ---------------- Firestore ----------------
-keep class com.xs.expensetracker.data.** { *; }
-keepclassmembers class * { public <init>(); }
-keepattributes *Annotation*

# ---------------- Firebase ----------------
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ---------------- Koin ----------------
-keep class org.koin.** { *; }

# ---------------- Kotlinx Serialization ----------------
-keep @kotlinx.serialization.Serializable class * { *; }

# ---------------- Google Ads ----------------
-keep class com.google.android.gms.ads.** { *; }

# ---------------- Google Identity ----------------
-keep class com.google.android.libraries.identity.** { *; }
# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile