# Код приложения не обфусцируем и не вырезаем: он небольшой, а так надёжнее
# (Room, WorkManager, Glance, сервисы и ресиверы из манифеста). Библиотеки сжимаются как обычно.
-keep class com.tshk.hlthhbbty.** { *; }
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn org.slf4j.**
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Tink (E2E-шифрование) и OkHttp
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
