# yt.js bridge (hidden WebView) talks to Kotlin through @JavascriptInterface methods.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Optional okhttp / okio platform classes
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---- NewPipeExtractor (reflection, Rhino JS engine, protobuf, jsoup) ----
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-keep class com.grack.nanojson.** { *; }
-keep class org.jsoup.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { *; }
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-dontwarn org.mozilla.javascript.engine.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn java.beans.**
-dontwarn com.google.re2j.**
-dontwarn org.jspecify.annotations.**
-dontwarn edu.umd.cs.findbugs.annotations.**
-dontwarn javax.annotation.**
