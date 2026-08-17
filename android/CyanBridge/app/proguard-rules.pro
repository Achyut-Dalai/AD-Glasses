# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Shizuku creates this Binder user service by its class name outside the normal Android manifest.
-keep class com.fersaiyan.cyanbridge.localagent.shizuku.LocalAgentShizukuUserService { <init>(...); *; }

# The first React Native migration bridge intentionally reuses MainActivity's proven
# dashboard dispatcher through reflection. Keep these two private entry points stable
# until that runtime is extracted into a dedicated service/controller.
-keepclassmembers class com.fersaiyan.cyanbridge.MainActivity {
    private void handleDashboardAction(com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardAction);
    private com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardUiState getDashboardState();
}
