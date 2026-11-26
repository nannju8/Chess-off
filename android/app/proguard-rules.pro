# Règles ProGuard/R8 conservant les classes nécessaires
-keepattributes *Annotation*
-keep class com.chessoffline.core.** { *; }
-keep class com.chessoffline.engine.** { *; }
