# Paho MQTT loads its network modules and logger by reflection
-keep class org.eclipse.paho.client.mqttv3.** { *; }
-dontwarn org.eclipse.paho.client.mqttv3.**
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class mcc.**$$serializer { *; }
-keepclassmembers class mcc.** { *** Companion; }
# Glance -> WorkManager -> Room: generated database classes are created by reflection
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.** { *; }
# WorkManager creates these by reflection too (Glance runs the widget session as a worker):
# without them the widget never leaves its loading layout
-keep class * extends androidx.work.InputMerger { <init>(); }
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }
