# kotlinx.serialization keeps its own metadata; this is the standard rule set.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class com.juni.app.**$$serializer { *; }
-keepclassmembers class com.juni.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.juni.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp / Okio reflection.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
