# NewPipeExtractor uses Rhino for YouTube's JavaScript player support.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**

# Keep line numbers useful in crash reports while still allowing shrinking.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
