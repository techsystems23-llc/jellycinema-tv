-dontwarn org.commonmark.ext.gfm.strikethrough.Strikethrough

# Jelly Cinema: NewPipe Extractor (YouTube trailers) and the Rhino JavaScript engine it uses
-dontwarn java.beans.**
-dontwarn javax.script.**
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.mozilla.javascript.**
