# 灵动豹豹 — 默认混淆规则（release 未开启 minify，留作后续加固/混淆使用）
-keepattributes *Annotation*
-keep class org.luaj.** { *; }
-keep class com.yuanlingbb.auto.** { *; }
