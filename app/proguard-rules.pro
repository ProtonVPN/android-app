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

-verbose
-keepattributes SourceFile,LineNumberTable,Exceptions,InnerClasses,Signature,Deprecated,*Annotation*,EnclosingMethod

# Legacy Storage
# Keep names of classes used in legacy Storage - class names are used as keys.
-keepnames class com.protonvpn.android.appconfig.AppConfigResponse
-keepnames class com.protonvpn.android.models.vpn.ConnectionParams
-keepnames class com.protonvpn.android.models.profiles.SavedProfilesV3
-keepnames class com.protonvpn.android.utils.ServerManager
-keepnames class com.protonvpn.android.vpn.RecentsManager
-keepnames class com.protonvpn.android.vpn.VpnState$*

# R8 has been observed to optimize the body of Hilt_XxxActivity.inject()
# down to a no-op for some @AndroidEntryPoint siblings in a class hierarchy
# (verified in a release build: Hilt_CarouselUpgradeDialogActivity.inject()
# and Hilt_PlusOnlyUpgradeDialogActivity.inject() compiled to `return-void`,
# dropping members-injection of fields declared on a shared non-entry-point
# base class, e.g. BaseActivityV2.isTv). Keeping just the class isn't enough;
# the method bodies themselves must be kept from being optimized/inlined.
# It's most likely this issue: https://issuetracker.google.com/issues/379034741
# but we're using a version that should be new enough to include the fix...
# Try to remove this rule when updating AGP to 9+ (with its associated R8 version).
-keep,allowobfuscation,allowshrinking class * implements dagger.hilt.internal.GeneratedComponentManagerHolder {
    *;
}

# ProcessLifecycleOwner
# release_tests crash when ProcessLifecycleOwner is not excluded from obfuscation.
# Perhaps it's because we have some dependencies that were built with old lifecycle library (esp. Sentry).
# Can be re-evaluated later.
-keep class androidx.lifecycle.ProcessLifecycleOwner {
    *;
}
-keep class androidx.lifecycle.ProcessLifecycleOwner$*

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
-dontwarn java.net.spi.InetAddressResolverProvider
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
