# osmdroid keeps tile source and configuration classes reachable by name.
-keep class org.osmdroid.** { *; }

# kotlinx.serialization keeps generated serializers for the Overpass DTOs.
-keepclassmembers class pt.vcc.parking.** {
    *** Companion;
}
-keepclasseswithmembers class pt.vcc.parking.** {
    kotlinx.serialization.KSerializer serializer(...);
}
