# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# =============================================================================
# Aroha Astrology — project-specific R8 keep rules
# Written when enabling minifyEnabled/shrinkResources for release builds.
# =============================================================================

# Capacitor core bridge — duplicate of the consumer rules already bundled in
# capacitor-android's own AAR, kept explicit so bridge protection doesn't
# silently depend on that mechanism alone.
-keep @com.getcapacitor.annotation.CapacitorPlugin public class * {
    @com.getcapacitor.annotation.PermissionCallback <methods>;
    @com.getcapacitor.annotation.ActivityCallback <methods>;
    @com.getcapacitor.annotation.Permission <methods>;
    @com.getcapacitor.PluginMethod public <methods>;
}
-keep public class * extends com.getcapacitor.Plugin { *; }

# ...and Capacitor's own runtime, which the rules above do NOT cover: they
# protect plugin SUBCLASSES, while the bridge machinery that drives them lives
# in com.getcapacitor.* and was left exposed to the optimizer.
#
# That crashed the app on every launch for logged-in users. R8's usage.txt
# showed it deleting PluginHandle.pluginAnnotation and getPluginAnnotation() —
# reachable only by reflection, so the optimizer sees them as dead. Bridge
# .getPermissionStates() then does
#
#     CapacitorPlugin annotation = plugin.getPluginHandle().getPluginAnnotation();
#     for (Permission perm : annotation.permissions())   // no null check
#
# and NPEs on the CapacitorPlugins thread, which kills the process. Only
# logged-in users hit it, because checkPermissions() is called from
# PushNotificationListener (gated on userId) and PermissionsPrompt (gated on
# profileCompletedAt) and nowhere else.
#
# Narrow keeps just move the crash — usage.txt shows PluginMethodHandle
# .getMethod() and PluginResult.getWrappedResult() stripped too, and the whole
# bridge is reflection-driven. Keep the package.
#
# Regression check: after any proguard or AGP change, confirm
#   android/app/build/outputs/mapping/release/usage.txt
# has no "com.getcapacitor.PluginHandle:" block.
-keep class com.getcapacitor.** { *; }

# This app's own native Capacitor plugins (PlayBillingPlugin, TtsPlugin).
-keep class com.aroha.astrology.** { *; }

# capacitor-firebase-messaging's FCM service isn't a com.getcapacitor.Plugin
# subclass, so the rule above doesn't cover it.
-keep class io.capawesome.capacitorjs.plugins.firebase.messaging.MessagingService

# This app only enables the "phone" FirebaseAuthentication provider
# (capacitor.config.ts), so play-services-auth / facebook-login are
# compileOnly in capacitor-firebase-authentication and not on the runtime
# classpath, even though its Google/Facebook provider handler classes
# reference them. Silence the resulting R8 missing-class warnings instead of
# keeping (keeping would force-package unused SDKs).
-dontwarn com.google.android.gms.auth.**
-dontwarn com.facebook.**

# Google Play Billing Library — narrow restatement of the public API surface
# PlayBillingPlugin.java calls directly, on top of the AAR's own bundled
# consumer rules.
-keep class com.android.billingclient.api.** { *; }
-keep class com.android.vending.billing.** { *; }

# Deliberately NOT keeping com.google.firebase.** or com.google.android.gms.**
# broadly — firebase-auth and firebase-messaging ship their own consumer
# ProGuard rules and are documented as R8-compatible without extra rules for
# standard usage. A blanket keep here would defeat most of the shrink/
# obfuscation win this file exists for. If verification turns up a genuinely
# missing class, add a rule scoped to that exact class, not the package.
