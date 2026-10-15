# ===== WxDexInspector ProGuard / R8 rules =====

# Keep DexKit (reflection + native linkage)
-keep class org.luckypray.dexkit.** { *; }
-keepclassmembers class org.luckypray.dexkit.** { *; }
-dontwarn org.luckypray.dexkit.**

# Keep Shizuku
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# Keep all app classes (small app; simplifies Gson reflection + keeps model fields)
-keep class com.johnny.wxdexinspector.** { *; }
-keepclassmembers class com.johnny.wxdexinspector.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-dontwarn com.google.gson.**

# Shizuku / hidden API usage safety
-dontwarn android.os.**
-dontwarn java.lang.reflect.**

# Kotlin reflection / stdlib
-dontwarn kotlin.**
