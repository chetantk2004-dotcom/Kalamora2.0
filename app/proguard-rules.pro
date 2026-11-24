# Keep Firestore domain models and annotations for release builds
-keepattributes *Annotation*
-keepclassmembers class com.example.data.model.** {
    public <init>(...);
    <fields>;
    <methods>;
}
-keep class com.example.data.model.** { *; }
-keep class com.example.data.local.** { *; }
