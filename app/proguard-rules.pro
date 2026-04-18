# Suppress warnings for optional/compile-time dependencies not present at runtime
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn javax.annotation.concurrent.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**

# Keep libsignal JNI entry points
-keep class org.signal.libsignal.** { *; }

# Keep Tink classes used by libsignal
-keep class com.google.crypto.tink.** { *; }

# Keep tor-android native bindings
-keep class info.guardianproject.** { *; }
-keep class net.freehaven.tor.** { *; }

# Keep ZXing used for QR encoding/decoding
-keep class com.google.zxing.** { *; }

# Keep Apache Commons Codec used for Base32 onion address encoding
-keep class org.apache.commons.codec.** { *; }
