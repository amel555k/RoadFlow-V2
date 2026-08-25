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
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

-keep class com.amko.roadflow.data.** { *; }
-keepclassmembers class com.amko.roadflow.data.** { *; }

-keepattributes *Annotation*,Signature,InnerClasses
-keepclassmembers class * {
    @com.google.firebase.database.PropertyName <fields>;
}

# --- Coroutines ---
-keepclassmembers class * extends kotlinx.coroutines.AbstractCoroutine { *; }

-keep class * extends androidx.glance.appwidget.action.ActionCallback {
    <init>();
    *;
}

-keep class com.amko.roadflow.presentation.widget.** { *; }