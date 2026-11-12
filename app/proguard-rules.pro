-dontwarn org.jsoup.**
-keep class org.jsoup.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keepclassmembers class com.notflix.app.data.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
