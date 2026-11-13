# The JS bridge is called from page JavaScript by method name — renaming
# any @JavascriptInterface method would silently break the widgets and the
# blob-download shim. Keep the whole bridge class verbatim.
-keep class com.jackbhai.omnitools.JsBridge { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface *;
}
# Manifest components are instantiated by the system by name; keep them
# (AGP adds its own keeps too — these make the standalone-R8 build in
# build-manual.sh safe as well).
-keep class com.jackbhai.omnitools.MainActivity { *; }
-keep class com.jackbhai.omnitools.*WidgetProvider { *; }
-keep class com.jackbhai.omnitools.CmdReceiver { *; }
-keep class com.jackbhai.omnitools.PlaybackService { *; }
-keep class com.jackbhai.omnitools.R { *; }
-keep class com.jackbhai.omnitools.R$* { *; }
-keep class com.jackbhai.omnitools.UpdateCheck { *; }
# androidx.webkit references annotation types that are compile-time only.
-dontwarn androidx.annotation.**
-dontwarn org.chromium.support_lib_boundary.**
