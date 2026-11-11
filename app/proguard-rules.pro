# Glance instantiates ActionCallback implementations by class name via reflection.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }

# Keep line numbers in crash stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
