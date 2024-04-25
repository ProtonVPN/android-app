# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# disable logcat logs in release builds
-assumenosideeffects class android.util.Log {
    v(...);
    d(...);
    i(...);
    w(...);
    e(...);
    wtf(...);
    println(...);
}