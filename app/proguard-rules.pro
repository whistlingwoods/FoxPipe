# https://developer.android.com/build/shrink-code

## Helps debug release versions
-dontobfuscate

## Rules for NewPipeExtractor
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
## Rules for Rhino and Rhino Engine
-keep class org.mozilla.javascript.* { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.javascript.engine.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-keep class javax.script.** { *; }
-dontwarn javax.script.**
-keep class jdk.dynalink.** { *; }
-dontwarn jdk.dynalink.**
-dontwarn com.google.protobuf.GeneratedMessageLite$Builder
-dontwarn com.google.protobuf.GeneratedMessageLite
-dontwarn com.google.protobuf.MessageLiteOrBuilder
-dontwarn org.mozilla.javascript.Context
-dontwarn org.mozilla.javascript.Function
-dontwarn org.mozilla.javascript.Kit
-dontwarn org.mozilla.javascript.Script
-dontwarn org.mozilla.javascript.ScriptRuntime
-dontwarn org.mozilla.javascript.Scriptable
-dontwarn org.mozilla.javascript.ScriptableObject

## Rules for ExoPlayer
-keep class com.google.android.exoplayer2.** { *; }

## Rules for OkHttp. Copy pasted from https://github.com/square/okhttp
-dontwarn okhttp3.**
-dontwarn okio.**

## See https://github.com/TeamNewPipe/NewPipe/pull/1441
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
}

## For some reason NotificationModeConfigFragment wasn't kept (only referenced in a preference xml)
-keep class org.schabi.newpipe.settings.notifications.** { *; }
