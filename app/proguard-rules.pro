# --- App DTOs used by Retrofit/Moshi ---
-keep class de.robnice.homeshoplist.data.dto.** { *; }
-keep class de.robnice.homeshoplist.data.update.** { *; }

# Keep Kotlin metadata for Moshi/Kotlin reflection related lookups
-keep class kotlin.Metadata { *; }

# Keep classes annotated with @JsonClass
-keep @com.squareup.moshi.JsonClass class * { *; }

# Keep generated Moshi adapters
-keep class **JsonAdapter { *; }

# Retrofit interfaces may be proxied and can be stripped too aggressively by R8 full mode
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# Keep signatures commonly needed by Retrofit/R8 full mode
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

-dontwarn com.squareup.moshi.**
-dontwarn kotlin.Unit

# Room creates the generated *_Impl database classes via reflection (also WorkManager's
# internal WorkDatabase). Room's own consumer rule did not cover them in our release build,
# which crashed the app on start with NoSuchMethodException (WorkDatabase_Impl.<init>).
-keep class * extends androidx.room.RoomDatabase { <init>(); }
