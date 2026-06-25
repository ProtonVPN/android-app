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

# WireGuard
-keep class com.wireguard.android.backend.** { *; }

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

# dnsjava
-dontwarn com.sun.jna.platform.win32.Guid$GUID
-dontwarn com.sun.jna.platform.win32.Win32Exception
-dontwarn java.awt.Component
-dontwarn java.awt.GraphicsEnvironment
-dontwarn java.awt.HeadlessException
-dontwarn java.awt.Window
-dontwarn javax.naming.**
-dontwarn lombok.Generated
-dontwarn org.xbill.DNS.config.JndiContextResolverConfigProvider
-dontwarn org.xbill.DNS.spi.DnsjavaInetAddressResolverProvider
-dontwarn sun.net.spi.nameservice.NameServiceDescriptor
