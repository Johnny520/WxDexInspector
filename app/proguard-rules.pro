# ===== WxDexInspector ProGuard / R8 rules =====

# Keep DexKit (reflection + native linkage)
-keep class org.luckypray.dexkit.** { *; }
-keepclassmembers class org.luckypray.dexkit.** { *; }
-dontwarn org.luckypray.dexkit.**

# Keep Shizuku
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# Gson model classes (serialized by field name)
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.johnny.wxdexinspector.data.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Keep data/snapshot models referenced by Gson reflection
-keep class com.johnny.wxdexinspector.data.model.** { *; }
