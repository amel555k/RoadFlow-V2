-keepattributes Signature,AnnotationDefault,EnclosingMethod,InnerClasses,SourceFile,LineNumberTable

-keep class com.amko.roadflow.domain.model.** { *; }
-keep class com.amko.roadflow.data.local.Secrets { *; }
-keep class com.amko.roadflow.BuildConfig { *; }

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