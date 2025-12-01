# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-dontobfuscate
-verbose
-keepattributes SourceFile,LineNumberTable,Exceptions,InnerClasses,Signature,Deprecated,*Annotation*,EnclosingMethod


# Keep kotlinx serializable classes.
-keep @kotlinx.serialization.Serializable public class * {
    *;
}
# Keep GSON serializable classes
# https://r8.googlesource.com/r8/+/refs/heads/main/compatibility-faq.md#troubleshooting-gson
-keepclassmembers,allowobfuscation class * {
 @com.google.gson.annotations.SerializedName <fields>;
}
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,RuntimeVisibleParameterAnnotations
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Keep no-args constructor of classes which can be used with @JsonAdapter
# By default their no-args constructor is invoked to create an adapter instance
-keepclassmembers class * extends com.google.gson.TypeAdapter {
  <init>();
}
-keepclassmembers class * implements com.google.gson.TypeAdapterFactory {
  <init>();
}
-keepclassmembers class * implements com.google.gson.JsonSerializer {
  <init>();
}
-keepclassmembers class * implements com.google.gson.JsonDeserializer {
  <init>();
}

# serializable classes
-keep class com.protonvpn.** implements java.io.Serializable {
    *;
}

# WireGuard
-keep class com.wireguard.android.backend.** { *; }

# Retrofit
# https://r8.googlesource.com/r8/+/refs/heads/main/compatibility-faq.md#kotlin-suspend-functions-and-generic-signatures
-keep class kotlin.coroutines.Continuation
# https://github.com/square/retrofit/pull/3886
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>
# With R8 full mode generic signatures are stripped for classes that are not kept.
-keep,allowobfuscation,allowshrinking class retrofit2.Response
# Retain service method parameters when optimizing.
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
# With R8 full mode, it sees no subtypes of Retrofit interfaces since they are created with a Proxy
# and replaces all potential values with null. Explicitly keeping the interfaces prevents this.
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
# Keep inherited services.
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface * extends <1>

# Logback
# (we only use a small part, the rules can be further optimized)
-keep class ch.qos.logback.** { *; }

# JNA (java-native-access)
# https://github.com/java-native-access/jna/blob/master/www/FrequentlyAskedQuestions.md#jna-on-android
-dontwarn java.awt.*
-keep class com.sun.jna.* { *; }
-keep class * extends com.sun.jna.* { *; }
-keepclassmembers class * extends com.sun.jna.* { public *; }

# Missing classes from dependencies.
# This is generated automatically by the Android Gradle plugin.
# (but copied&pasted manually :) )
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn javax.lang.model.element.Modifier
-dontwarn javax.mail.Address
-dontwarn javax.mail.Authenticator
-dontwarn javax.mail.BodyPart
-dontwarn javax.mail.Message$RecipientType
-dontwarn javax.mail.Message
-dontwarn javax.mail.Multipart
-dontwarn javax.mail.PasswordAuthentication
-dontwarn javax.mail.Session
-dontwarn javax.mail.Transport
-dontwarn javax.mail.internet.AddressException
-dontwarn javax.mail.internet.InternetAddress
-dontwarn javax.mail.internet.MimeBodyPart
-dontwarn javax.mail.internet.MimeMessage
-dontwarn javax.mail.internet.MimeMultipart
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE

# Proton Environment Configuration
-keep class me.proton.core.configuration.** { *; }

-keep class com.appmattus.certificatetransparency.internal.verifier.CertificateTransparencyTrustManagerExtended { *; }

# dnsjava
-dontwarn com.sun.jna.platform.win32.Guid$GUID
-dontwarn com.sun.jna.platform.win32.Win32Exception
-dontwarn java.awt.Component
-dontwarn java.awt.GraphicsEnvironment
-dontwarn java.awt.HeadlessException
-dontwarn java.awt.Window
-dontwarn lombok.Generated
-dontwarn org.xbill.DNS.spi.DnsjavaInetAddressResolverProvider
-dontwarn sun.net.spi.nameservice.NameServiceDescriptor