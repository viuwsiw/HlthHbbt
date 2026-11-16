# Код приложения не обфусцируем и не вырезаем: он небольшой, а так надёжнее
# (Room, WorkManager, Glance, сервисы и ресиверы из манифеста). Библиотеки сжимаются как обычно.
-keep class com.tshk.hlthhbbty.** { *; }
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn org.slf4j.**
