# Retrofit/kotlinx.serialization models are annotated; keep them.
-keepattributes *Annotation*, InnerClasses, Signature
-dontwarn kotlinx.serialization.**
-keepclassmembers class com.lingualoop.android.data.api.dto.** {
    *** Companion;
}
-keepclasseswithmembers class com.lingualoop.android.data.api.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}
