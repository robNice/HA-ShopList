# Wear OS ProGuard rules
-keep class de.robnice.homeshoplist.wear.** { *; }
-keep class com.google.android.gms.wearable.** { *; }

# Room creates the generated *_Impl database classes via reflection (also WorkManager's
# internal WorkDatabase). Room's own consumer rule did not cover them in our release build,
# which crashed the app on start with NoSuchMethodException (WorkDatabase_Impl.<init>).
-keep class * extends androidx.room.RoomDatabase { <init>(); }
