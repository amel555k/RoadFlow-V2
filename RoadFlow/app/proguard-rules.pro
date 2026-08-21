-keepattributes Signature,AnnotationDefault,EnclosingMethod,InnerClasses,SourceFile,LineNumberTable

-keep class com.amko.roadflow.domain.model.** { *; }


-keep class org.json.** { *; }

-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

-keep class org.maplibre.android.** { *; }
-dontwarn org.maplibre.android.**

-keep class androidx.glance.** { *; }
-dontwarn androidx.glance.**

-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}