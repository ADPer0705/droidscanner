# Keep MediaPipe Tasks (native graph + JNI bindings) intact under R8 release shrinking.
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**
-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**

# Keep WorkManager CoroutineWorker subclasses instantiated via reflection.
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.idiots3.riskscanner.worker.** { *; }
