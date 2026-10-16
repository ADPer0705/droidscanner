# Keep MediaPipe Tasks (native graph + JNI bindings) intact if minification is ever enabled.
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**
-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**
