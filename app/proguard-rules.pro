# Multica Android ProGuard rules

# Keep Retrofit interfaces (uses reflection on method/parameter annotations)
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Keep our API interface
-keep class ai.multica.android.core.network.MulticaApi { *; }

# kotlinx.serialization — keep @Serializable classes' fields
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class ai.multica.android.**$$serializer { *; }
-keepclassmembers class ai.multica.android.** {
    *** Companion;
}
-keepclasseswithmembers class ai.multica.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp

# Compose
-keep class androidx.compose.** { *; }
