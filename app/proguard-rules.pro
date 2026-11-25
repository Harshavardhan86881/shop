# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.slncloth.shop.**$$serializer { *; }
-keepclassmembers class com.slncloth.shop.** {
    *** Companion;
}
-keepclasseswithmembers class com.slncloth.shop.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor / coroutines
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }
-dontwarn org.slf4j.**
-keep class org.slf4j.** { *; }

# BouncyCastle (used only for local TLS certificate generation)
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }
-keep class javax.naming.** { *; }

# zxing
-dontwarn com.google.zxing.**
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }
